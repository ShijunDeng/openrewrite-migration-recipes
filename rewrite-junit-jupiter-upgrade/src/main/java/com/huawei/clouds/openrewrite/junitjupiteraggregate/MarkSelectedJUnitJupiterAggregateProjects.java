package com.huawei.clouds.openrewrite.junitjupiteraggregate;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Captures exact aggregate dependency ownership before dependency edits.
 *
 * <p>Only {@code pom.xml}, {@code build.gradle}, and {@code build.gradle.kts}
 * establish project boundaries. Auxiliary Gradle scripts contribute dependency
 * evidence to their nearest real build root, so an off-list or mixed declaration
 * in an applied script blocks source migration for that project.</p>
 */
public final class MarkSelectedJUnitJupiterAggregateProjects
        extends ScanningRecipe<MarkSelectedJUnitJupiterAggregateProjects.Projects> {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    enum State {
        SELECTED,
        OTHER,
        CONFLICT
    }

    record ProjectSelection(State state, String sourceVersion) {
    }

    static final class Projects {
        private final Set<Path> roots = ConcurrentHashMap.newKeySet();
        private final Map<Path, ProjectSelection> observations = new ConcurrentHashMap<>();
        private volatile Map<Path, ProjectSelection> resolved;

        void recordBoundary(Path sourcePath) {
            roots.add(parent(sourcePath));
            resolved = null;
        }

        void recordObservation(Path sourcePath, ProjectSelection observation) {
            if (observation != null) {
                observations.put(sourcePath.normalize(), observation);
                resolved = null;
            }
        }

        ProjectSelection selection(Path sourcePath) {
            Path root = nearestRoot(sourcePath.normalize());
            if (root == null) return null;
            if (resolved == null) resolved = resolve();
            return resolved.get(root);
        }

        private Map<Path, ProjectSelection> resolve() {
            Map<Path, Eligibility> byRoot = new HashMap<>();
            observations.forEach((path, observation) -> {
                Path root = nearestRoot(path);
                if (root != null) {
                    byRoot.computeIfAbsent(root, ignored -> new Eligibility()).merge(observation);
                }
            });
            Map<Path, ProjectSelection> selections = new HashMap<>();
            byRoot.forEach((root, eligibility) -> {
                ProjectSelection selection = eligibility.selection();
                if (selection != null) selections.put(root, selection);
            });
            return selections;
        }

        private Path nearestRoot(Path sourcePath) {
            Path nearest = null;
            for (Path root : roots) {
                if (contains(root, sourcePath) &&
                    (nearest == null || depth(root) > depth(nearest))) {
                    nearest = root;
                }
            }
            return nearest;
        }

        private static Path parent(Path sourcePath) {
            Path parent = sourcePath.normalize().getParent();
            return parent == null ? Path.of("") : parent;
        }

        private static boolean contains(Path root, Path path) {
            return root.toString().isEmpty() || path.startsWith(root);
        }

        private static int depth(Path path) {
            return path.toString().isEmpty() ? 0 : path.getNameCount();
        }
    }

    @Override
    public String getDisplayName() {
        return "Mark workbook-selected JUnit Jupiter aggregate projects";
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
                    UpgradeSelectedJUnitJupiterDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    projects.recordObservation(source.getSourcePath(), mavenState(document, ctx));
                } else if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    if ("build.gradle".equals(file)) projects.recordBoundary(source.getSourcePath());
                    projects.recordObservation(source.getSourcePath(), groovyState(groovy, ctx));
                } else if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    if ("build.gradle.kts".equals(file)) projects.recordBoundary(source.getSourcePath());
                    projects.recordObservation(source.getSourcePath(), kotlinState(kotlin, ctx));
                }
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
                    UpgradeSelectedJUnitJupiterDependency.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(JUnitJupiterAggregateProjectMarker.class).isPresent()) {
                    return tree;
                }
                ProjectSelection selection = projects.selection(source.getSourcePath());
                if (selection == null || selection.state() != State.SELECTED ||
                    selection.sourceVersion() == null) {
                    return tree;
                }
                return source.withMarkers(source.getMarkers().add(
                        new JUnitJupiterAggregateProjectMarker(UUID.randomUUID(),
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
                if (UpgradeSelectedJUnitJupiterDependency.isMavenPropertyDefinition(
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
                UpgradeSelectedJUnitJupiterDependency.SOURCE_VERSIONS.contains(values.get(key)) &&
                count > 0 && count.equals(allReferences.get(key))) {
                safe.add(key);
            }
        });

        Eligibility eligibility = new Eligibility();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!"dependency".equals(visited.getName()) ||
                    !UpgradeSelectedJUnitJupiterDependency.GROUP.equals(
                            visited.getChildValue("groupId").map(String::trim).orElse(null)) ||
                    !UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(
                            visited.getChildValue("artifactId").map(String::trim).orElse(null))) {
                    return visited;
                }
                if (!UpgradeSelectedJUnitJupiterDependency.isProjectDependency(getCursor(), visited) ||
                    visited.getChild("classifier").isPresent() ||
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
                G.GString visited = super.visitGString(gString, ec);
                if (mentionsCoordinate(visited.printTrimmed(getCursor()))) {
                    eligibility.recordOther();
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedJUnitJupiterDependency
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                String group = UpgradeSelectedJUnitJupiterDependency.mapValue(visited, "group");
                String artifact = UpgradeSelectedJUnitJupiterDependency.mapValue(visited, "name");
                if (UpgradeSelectedJUnitJupiterDependency.GROUP.equals(group) &&
                    UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(artifact)) {
                    if (!direct || UpgradeSelectedJUnitJupiterDependency.hasVariant(visited)) {
                        eligibility.recordOther();
                    } else {
                        eligibility.record(UpgradeSelectedJUnitJupiterDependency
                                .mapValue(visited, "version"));
                    }
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedJUnitJupiterDependency
                        .isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, eligibility, direct);
                return visited;
            }
        }.visitNonNull(source, ctx);
        if (!eligibility.seen() && mentionsCoordinate(source.printAll())) eligibility.recordOther();
        return eligibility.selection();
    }

    private static ProjectSelection kotlinState(K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public K.StringTemplate visitStringTemplate(
                    K.StringTemplate template, ExecutionContext ec) {
                K.StringTemplate visited = super.visitStringTemplate(template, ec);
                if (mentionsCoordinate(visited.printTrimmed(getCursor()))) {
                    eligibility.recordOther();
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedJUnitJupiterDependency
                        .isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, eligibility, direct);
                return visited;
            }
        }.visitNonNull(source, ctx);
        if (!eligibility.seen() && mentionsCoordinate(source.printAll())) eligibility.recordOther();
        return eligibility.selection();
    }

    private static boolean mentionsCoordinate(String source) {
        return source.contains(UpgradeSelectedJUnitJupiterDependency.GROUP) &&
               source.contains(UpgradeSelectedJUnitJupiterDependency.ARTIFACT);
    }

    private static void recordCoordinate(J.Literal literal, Eligibility eligibility, boolean direct) {
        if (!(literal.getValue() instanceof String coordinate)) return;
        int variant = coordinate.indexOf('@');
        String plain = variant < 0 ? coordinate : coordinate.substring(0, variant);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2 ||
            !UpgradeSelectedJUnitJupiterDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedJUnitJupiterDependency.ARTIFACT.equals(parts[1])) {
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
               UpgradeSelectedJUnitJupiterDependency.isJUnitJupiterDependency(
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
        private boolean seen;

        boolean seen() {
            return seen;
        }

        void record(String version) {
            seen = true;
            if (version == null || version.isBlank()) {
                other = true;
            } else if (UpgradeSelectedJUnitJupiterDependency.SOURCE_VERSIONS.contains(version)) {
                selectedVersions.add(version);
            } else {
                other = true;
            }
        }

        void recordOther() {
            seen = true;
            other = true;
        }

        void merge(ProjectSelection selection) {
            if (selection == null) return;
            seen = true;
            if (selection.state() != State.SELECTED || selection.sourceVersion() == null) {
                other = true;
            } else {
                selectedVersions.add(selection.sourceVersion());
            }
        }

        ProjectSelection selection() {
            if (!seen) return null;
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
