package com.huawei.clouds.openrewrite.junitjupiter;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures exact dependency ownership before dependency edits.
 *
 * <p>A build root is selected only when all visible declarations of
 * {@code org.junit.jupiter:junit-jupiter-api} are standard artifacts owned by
 * that root and resolve to one workbook source version. Missing, target,
 * off-list, variable, ranged, variant, or mixed declarations block source and
 * configuration automation for that root.</p>
 */
public final class MarkSelectedJUnitJupiterProjects
        extends ScanningRecipe<MarkSelectedJUnitJupiterProjects.Projects> {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    enum State {
        SELECTED,
        OTHER,
        CONFLICT
    }

    static final class Projects {
        private final Set<Path> roots = new HashSet<>();
        private final Map<Path, State> states = new HashMap<>();
        private final Map<Path, String> sourceVersions = new HashMap<>();

        void recordBoundary(Path sourcePath) {
            roots.add(root(sourcePath));
        }

        void record(Path sourcePath, ProjectSelection selection) {
            Path root = root(sourcePath);
            roots.add(root);
            State existing = states.get(root);
            if (existing == null) {
                states.put(root, selection.state());
                if (selection.sourceVersion() != null) {
                    sourceVersions.put(root, selection.sourceVersion());
                }
            } else if (existing != selection.state() ||
                       existing == State.SELECTED &&
                       !Objects.equals(sourceVersions.get(root), selection.sourceVersion())) {
                states.put(root, State.CONFLICT);
                sourceVersions.remove(root);
            }
        }

        ProjectSelection nearest(Path sourcePath) {
            Path nearest = null;
            for (Path root : roots) {
                if ((root.toString().isEmpty() || sourcePath.startsWith(root)) &&
                    (nearest == null || depth(root) > depth(nearest))) {
                    nearest = root;
                }
            }
            if (nearest == null) return null;
            State state = states.getOrDefault(nearest, State.OTHER);
            return new ProjectSelection(state,
                    state == State.SELECTED ? sourceVersions.get(nearest) : null);
        }

        private static Path root(Path sourcePath) {
            Path parent = sourcePath.getParent();
            return parent == null ? Path.of("") : parent;
        }

        private static int depth(Path path) {
            return path.toString().isEmpty() ? 0 : path.getNameCount();
        }
    }

    record ProjectSelection(State state, String sourceVersion) {
    }

    @Override
    public String getDisplayName() {
        return "Mark workbook-selected JUnit Jupiter API projects";
    }

    @Override
    public String getDescription() {
        return "Scan the nearest Maven or Gradle build root before dependency edits and carry eligibility " +
               "only when it owns one exact, non-conflicting workbook source version.";
    }

    @Override
    public Projects getInitialValue(ExecutionContext ctx) {
        return new Projects();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Projects projects) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedJUnitJupiterApiDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                ProjectSelection selection = null;
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = mavenState(document, ctx);
                } else if (tree instanceof G.CompilationUnit groovy && "build.gradle".equals(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = groovyState(groovy, ctx);
                } else if (tree instanceof K.CompilationUnit kotlin && "build.gradle.kts".equals(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = kotlinState(kotlin, ctx);
                }
                if (selection != null) projects.record(source.getSourcePath(), selection);
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Projects projects) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedJUnitJupiterApiDependency.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(JUnitJupiterProjectMarker.class).isPresent()) {
                    return tree;
                }
                ProjectSelection selection = projects.nearest(source.getSourcePath());
                if (selection == null || selection.state() != State.SELECTED ||
                    selection.sourceVersion() == null) {
                    return tree;
                }
                return source.withMarkers(source.getMarkers().add(
                        new JUnitJupiterProjectMarker(UUID.randomUUID(),
                                selection.sourceVersion())));
            }
        };
    }

    private static ProjectSelection mavenState(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedJUnitJupiterApiDependency.isMavenPropertyDefinition(
                        getCursor(), visited)) {
                    PropertyKey key = propertyKey(getCursor(), visited.getName());
                    definitions.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyKey, Integer> allReferences = new HashMap<>();
        Map<PropertyKey, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), definitions, allReferences,
                        ownedVersionReference(getCursor(), visited.getText()) ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), definitions, allReferences, null);
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyKey> safe = new HashSet<>();
        ownedReferences.forEach((key, count) -> {
            if (definitions.getOrDefault(key, 0) == 1 &&
                UpgradeSelectedJUnitJupiterApiDependency.SOURCE_VERSIONS.contains(values.get(key)) &&
                count > 0 && count.equals(allReferences.get(key))) {
                safe.add(key);
            }
        });

        Eligibility eligibility = new Eligibility();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!UpgradeSelectedJUnitJupiterApiDependency.isProjectDependency(
                        getCursor(), visited) ||
                    !UpgradeSelectedJUnitJupiterApiDependency.GROUP.equals(
                            visited.getChildValue("groupId").map(String::trim).orElse(null)) ||
                    !UpgradeSelectedJUnitJupiterApiDependency.ARTIFACT.equals(
                            visited.getChildValue("artifactId").map(String::trim).orElse(null))) {
                    return visited;
                }
                if (visited.getChild("classifier").isPresent() ||
                    !"jar".equals(visited.getChildValue("type").map(String::trim).orElse("jar"))) {
                    eligibility.recordOther();
                    return visited;
                }
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                if (raw == null) {
                    eligibility.recordOther();
                } else {
                    Matcher matcher = PROPERTY.matcher(raw);
                    if (matcher.matches()) {
                        PropertyKey key = resolveOwner(getCursor(), matcher.group(1), definitions);
                        eligibility.record(safe.contains(key) ? values.get(key) : null);
                    } else {
                        eligibility.record(raw);
                    }
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        return eligibility.selection();
    }

    private static ProjectSelection groovyState(G.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public G.GString visitGString(G.GString gString, ExecutionContext ec) {
                boolean direct = isDirectDependencyExpression(getCursor());
                G.GString visited = super.visitGString(gString, ec);
                if (direct && isTargetCoordinateExpression(visited, getCursor())) {
                    eligibility.recordOther();
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedJUnitJupiterApiDependency
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                String group = UpgradeSelectedJUnitJupiterApiDependency.mapValue(visited, "group");
                String artifact = UpgradeSelectedJUnitJupiterApiDependency.mapValue(visited, "name");
                if (UpgradeSelectedJUnitJupiterApiDependency.GROUP.equals(group) &&
                    UpgradeSelectedJUnitJupiterApiDependency.ARTIFACT.equals(artifact)) {
                    if (!direct || UpgradeSelectedJUnitJupiterApiDependency.hasVariant(visited)) {
                        eligibility.recordOther();
                    } else {
                        eligibility.record(UpgradeSelectedJUnitJupiterApiDependency
                                .mapValue(visited, "version"));
                    }
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedJUnitJupiterApiDependency
                        .isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, eligibility, direct);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    private static ProjectSelection kotlinState(K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public K.StringTemplate visitStringTemplate(K.StringTemplate template, ExecutionContext ec) {
                boolean direct = isDirectDependencyExpression(getCursor());
                K.StringTemplate visited = super.visitStringTemplate(template, ec);
                if (direct && isTargetCoordinateExpression(visited, getCursor())) {
                    eligibility.recordOther();
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedJUnitJupiterApiDependency
                        .isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, eligibility, direct);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    private static boolean isDirectDependencyExpression(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.MethodInvocation invocation &&
               UpgradeSelectedJUnitJupiterApiDependency.isGradleDependencyInvocation(parent, invocation);
    }

    private static boolean isTargetCoordinateExpression(Tree expression, Cursor cursor) {
        return expression.printTrimmed(cursor).contains(
                UpgradeSelectedJUnitJupiterApiDependency.GROUP + ":" +
                UpgradeSelectedJUnitJupiterApiDependency.ARTIFACT + ":");
    }

    private static void recordCoordinate(J.Literal literal, Eligibility eligibility, boolean direct) {
        if (!(literal.getValue() instanceof String coordinate)) return;
        int variant = coordinate.indexOf('@');
        String plain = variant < 0 ? coordinate : coordinate.substring(0, variant);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2 ||
            !UpgradeSelectedJUnitJupiterApiDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedJUnitJupiterApiDependency.ARTIFACT.equals(parts[1])) {
            return;
        }
        if (!direct || parts.length != 3 || variant >= 0) {
            eligibility.recordOther();
        } else {
            eligibility.record(parts[2]);
        }
    }

    private static boolean ownedVersionReference(Cursor cursor, String text) {
        if (!PROPERTY.matcher(text.trim()).matches()) return false;
        Cursor version = cursor.getParentTreeCursor();
        if (!(version.getValue() instanceof Xml.Tag versionTag) ||
            !"version".equals(versionTag.getName())) return false;
        Cursor dependency = version.getParentTreeCursor();
        return dependency.getValue() instanceof Xml.Tag dependencyTag &&
               UpgradeSelectedJUnitJupiterApiDependency.isJUnitJupiterApiDependency(
                       dependency, dependencyTag);
    }

    private static void collectReferences(
            String text, Cursor cursor, Map<PropertyKey, Integer> definitions,
            Map<PropertyKey, Integer> references,
            Map<PropertyKey, Integer> ownedReferences) {
        Matcher matcher = PROPERTY.matcher(text);
        while (matcher.find()) {
            PropertyKey owner = resolveOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(owner, 1, Integer::sum);
        }
    }

    private static PropertyKey propertyKey(Cursor cursor, String name) {
        return new PropertyKey(scope(cursor), name);
    }

    private static PropertyKey resolveOwner(
            Cursor cursor, String name, Map<PropertyKey, Integer> definitions) {
        String current = scope(cursor);
        PropertyKey local = new PropertyKey(current, name);
        return !"ROOT".equals(current) && definitions.containsKey(local)
                ? local : new PropertyKey("ROOT", name);
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private record PropertyKey(String scope, String name) {
    }

    private static final class Eligibility {
        private final Set<String> selectedVersions = new HashSet<>();
        private boolean other;

        void record(String version) {
            if (version == null || version.isBlank()) {
                other = true;
            } else if (UpgradeSelectedJUnitJupiterApiDependency.SOURCE_VERSIONS.contains(version)) {
                selectedVersions.add(version);
            } else {
                other = true;
            }
        }

        void recordOther() {
            other = true;
        }

        ProjectSelection selection() {
            if (selectedVersions.isEmpty()) {
                return new ProjectSelection(State.OTHER, null);
            }
            if (other || selectedVersions.size() != 1) {
                return new ProjectSelection(State.CONFLICT, null);
            }
            return new ProjectSelection(State.SELECTED, selectedVersions.iterator().next());
        }
    }
}
