package com.huawei.clouds.openrewrite.guava;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.Expression;
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
 * Captures exact Guava dependency ownership before dependency edits.
 *
 * <p>A build root is selected only when every visible project-owned Guava
 * declaration is compatible with one workbook source version. Off-list,
 * target, ranged, variable, classified, mixed-version, and malformed
 * declarations conservatively block source automation.</p>
 */
public final class MarkSelectedGuavaProjects
        extends ScanningRecipe<MarkSelectedGuavaProjects.Projects> {
    private static final String GROUP = "com.google.guava";
    private static final String ARTIFACT = "guava";
    private static final String PREFIX = GROUP + ":" + ARTIFACT + ":";
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
                return;
            }
            if (existing != selection.state() ||
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
        return "Mark workbook-selected Guava projects";
    }

    @Override
    public String getDescription() {
        return "Scan the nearest Maven or Gradle build root before dependency edits and carry eligibility " +
               "only when it owns one exact, non-conflicting workbook Guava source version.";
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
                    !UpgradeSelectedGuavaDependency.isProjectPath(source.getSourcePath())) {
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
                    !UpgradeSelectedGuavaDependency.isProjectPath(source.getSourcePath()) ||
                    source.getMarkers().findFirst(GuavaProjectMarker.class).isPresent()) {
                    return tree;
                }
                ProjectSelection selection = projects.nearest(source.getSourcePath());
                if (selection == null || selection.state() != State.SELECTED ||
                    selection.sourceVersion() == null) {
                    return tree;
                }
                return source.withMarkers(source.getMarkers().add(
                        new GuavaProjectMarker(UUID.randomUUID(), selection.sourceVersion())));
            }
        };
    }

    private static ProjectSelection mavenState(Xml.Document document, ExecutionContext ctx) {
        Map<String, Integer> definitions = new HashMap<>();
        Map<String, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (isMavenPropertyDefinition(getCursor(), visited)) {
                    definitions.merge(visited.getName(), 1, Integer::sum);
                    visited.getValue().ifPresent(value ->
                            values.put(visited.getName(), value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<String, Integer> references = new HashMap<>();
        Map<String, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), references,
                        ownedVersionReference(getCursor(), visited.getText())
                                ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), references, null);
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<String> safe = new HashSet<>();
        ownedReferences.forEach((name, count) -> {
            if (definitions.getOrDefault(name, 0) == 1 &&
                UpgradeSelectedGuavaDependency.SOURCE_VERSIONS.contains(values.get(name)) &&
                count > 0 && count.equals(references.get(name))) {
                safe.add(name);
            }
        });

        Eligibility eligibility = new Eligibility();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!isOwnedGuavaDependency(getCursor(), visited)) return visited;
                eligibility.sawDeclaration();
                if (!UpgradeSelectedGuavaDependency.isSupportedGuavaDependency(
                        getCursor(), visited)) {
                    eligibility.recordOther();
                    return visited;
                }
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                if (raw == null) {
                    if (isManagedDependency(getCursor())) {
                        eligibility.recordOther();
                    } else {
                        eligibility.recordVersionlessConsumer();
                    }
                    return visited;
                }
                boolean managed = isManagedDependency(getCursor());
                Matcher matcher = PROPERTY.matcher(raw);
                if (matcher.matches()) {
                    eligibility.record(safe.contains(matcher.group(1))
                            ? values.get(matcher.group(1)) : null, managed);
                } else {
                    eligibility.record(raw, managed);
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
                if (visited.printTrimmed(getCursor()).contains(PREFIX)) {
                    eligibility.sawDeclaration();
                    eligibility.recordOther();
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedGuavaDependency
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                recordInvocationMaps(visited, direct, eligibility);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedGuavaDependency
                        .isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, direct, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    private static ProjectSelection kotlinState(K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public K.StringTemplate visitStringTemplate(
                    K.StringTemplate template, ExecutionContext ec) {
                K.StringTemplate visited = super.visitStringTemplate(template, ec);
                if (visited.printTrimmed(getCursor()).contains(PREFIX)) {
                    eligibility.sawDeclaration();
                    eligibility.recordOther();
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = UpgradeSelectedGuavaDependency
                        .isDirectGradleDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, direct, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    private static boolean isManagedDependency(Cursor dependencyCursor) {
        Cursor dependenciesCursor = dependencyCursor.getParentTreeCursor();
        if (!(dependenciesCursor.getValue() instanceof Xml.Tag dependencies) ||
            !"dependencies".equals(dependencies.getName())) {
            return false;
        }
        Cursor ownerCursor = dependenciesCursor.getParentTreeCursor();
        return ownerCursor.getValue() instanceof Xml.Tag owner &&
               "dependencyManagement".equals(owner.getName());
    }

    private static void recordInvocationMaps(
            J.MethodInvocation invocation, boolean direct, Eligibility eligibility) {
        String group = invocationMapValue(invocation, "group");
        String artifact = invocationMapValue(invocation, "name");
        if (GROUP.equals(group) && ARTIFACT.equals(artifact)) {
            eligibility.sawDeclaration();
            if (!direct || hasVariant(invocation)) {
                eligibility.recordOther();
            } else {
                eligibility.record(invocationMapValue(invocation, "version"));
            }
        }
        for (Expression argument : invocation.getArguments()) {
            if (!(argument instanceof G.MapLiteral map)) continue;
            group = mapValue(map, "group");
            artifact = mapValue(map, "name");
            if (!GROUP.equals(group) || !ARTIFACT.equals(artifact)) continue;
            eligibility.sawDeclaration();
            if (!direct || hasVariant(map)) {
                eligibility.recordOther();
            } else {
                eligibility.record(mapValue(map, "version"));
            }
        }
    }

    private static void recordCoordinate(
            J.Literal literal, boolean direct, Eligibility eligibility) {
        if (!(literal.getValue() instanceof String coordinate)) return;
        int variant = coordinate.indexOf('@');
        String plain = variant < 0 ? coordinate : coordinate.substring(0, variant);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2 || !GROUP.equals(parts[0]) || !ARTIFACT.equals(parts[1])) {
            return;
        }
        eligibility.sawDeclaration();
        if (!direct || parts.length != 3 || variant >= 0) {
            eligibility.recordOther();
        } else {
            eligibility.record(parts[2]);
        }
    }

    private static boolean isOwnedGuavaDependency(Cursor cursor, Xml.Tag tag) {
        if (!UpgradeSelectedGuavaDependency.isGuava(tag)) return false;
        Cursor containerCursor = cursor.getParentTreeCursor();
        if (!(containerCursor.getValue() instanceof Xml.Tag container) ||
            !"dependencies".equals(container.getName())) return false;
        Cursor ownerCursor = containerCursor.getParentTreeCursor();
        if (!(ownerCursor.getValue() instanceof Xml.Tag owner)) return false;
        if ("project".equals(owner.getName()) || "profile".equals(owner.getName())) return true;
        if (!"dependencyManagement".equals(owner.getName())) return false;
        Cursor actualOwnerCursor = ownerCursor.getParentTreeCursor();
        return actualOwnerCursor.getValue() instanceof Xml.Tag actualOwner &&
               ("project".equals(actualOwner.getName()) ||
                "profile".equals(actualOwner.getName()));
    }

    private static boolean isMavenPropertyDefinition(Cursor cursor, Xml.Tag tag) {
        Cursor parentCursor = cursor.getParentTreeCursor();
        if (!(parentCursor.getValue() instanceof Xml.Tag parent) ||
            !"properties".equals(parent.getName()) ||
            "properties".equals(tag.getName())) return false;
        Cursor ownerCursor = parentCursor.getParentTreeCursor();
        return ownerCursor.getValue() instanceof Xml.Tag owner &&
               ("project".equals(owner.getName()) || "profile".equals(owner.getName()));
    }

    private static boolean ownedVersionReference(Cursor cursor, String text) {
        if (!PROPERTY.matcher(text.trim()).matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) ||
            !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               isOwnedGuavaDependency(dependencyCursor, dependency) &&
               UpgradeSelectedGuavaDependency.isSupportedGuavaDependency(
                       dependencyCursor, dependency);
    }

    private static void collectReferences(
            String text, Map<String, Integer> references,
            Map<String, Integer> ownedReferences) {
        Matcher matcher = PROPERTY.matcher(text);
        while (matcher.find()) {
            references.merge(matcher.group(1), 1, Integer::sum);
            if (ownedReferences != null) {
                ownedReferences.merge(matcher.group(1), 1, Integer::sum);
            }
        }
    }

    private static String invocationMapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream()
                .filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance)
                .map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance)
                .map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal &&
            literal.getValue() instanceof String key) {
            return key;
        }
        return entry.getKey() instanceof J.Identifier identifier
                ? identifier.getSimpleName() : null;
    }

    private static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream()
                .filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type")
                        .contains(mapKey(entry)));
    }

    private static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream().anyMatch(entry ->
                Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static final class Eligibility {
        private final Set<String> selectedVersions = new HashSet<>();
        private final Set<String> managedSelectedVersions = new HashSet<>();
        private boolean declaration;
        private boolean other;
        private boolean versionlessConsumer;

        void sawDeclaration() {
            declaration = true;
        }

        void recordOther() {
            other = true;
        }

        void record(String version) {
            record(version, false);
        }

        void record(String version, boolean managed) {
            if (version == null || version.isBlank()) {
                other = true;
            } else if (UpgradeSelectedGuavaDependency.SOURCE_VERSIONS.contains(version)) {
                selectedVersions.add(version);
                if (managed) {
                    managedSelectedVersions.add(version);
                }
            } else {
                other = true;
            }
        }

        void recordVersionlessConsumer() {
            versionlessConsumer = true;
        }

        ProjectSelection selection() {
            if (!declaration) return null;
            if (selectedVersions.isEmpty()) {
                return new ProjectSelection(State.OTHER, null);
            }
            if (versionlessConsumer &&
                !managedSelectedVersions.containsAll(selectedVersions)) {
                other = true;
            }
            if (other || selectedVersions.size() != 1) {
                return new ProjectSelection(State.CONFLICT, null);
            }
            return new ProjectSelection(State.SELECTED,
                    selectedVersions.iterator().next());
        }
    }
}
