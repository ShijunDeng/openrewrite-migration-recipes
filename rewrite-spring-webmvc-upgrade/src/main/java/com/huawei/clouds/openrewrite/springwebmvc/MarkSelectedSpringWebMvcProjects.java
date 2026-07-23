package com.huawei.clouds.openrewrite.springwebmvc;

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
 * Captures exact source-version ownership before the dependency recipe edits a
 * build file. Later source/configuration recipes consume the non-printing marker.
 */
public final class MarkSelectedSpringWebMvcProjects
        extends ScanningRecipe<MarkSelectedSpringWebMvcProjects.Projects> {
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
        return "Mark workbook-selected Spring Web MVC projects";
    }

    @Override
    public String getDescription() {
        return "Scan the nearest Maven or Gradle build root before dependency edits and carry " +
               "eligibility only when it owns one of the 24 exact source versions without conflict.";
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
                    UpgradeSelectedSpringWebMvcDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                ProjectSelection selection = null;
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = mavenState(document, ctx);
                } else if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    projects.recordBoundary(source.getSourcePath());
                    selection = groovyState(groovy, ctx);
                } else if (tree instanceof K.CompilationUnit kotlin &&
                           file.endsWith(".gradle.kts")) {
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
                    UpgradeSelectedSpringWebMvcDependency.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(SpringWebMvcProjectMarker.class).isPresent()) {
                    return tree;
                }
                ProjectSelection selection = projects.nearest(source.getSourcePath());
                if (selection == null || selection.state() != State.SELECTED ||
                    selection.sourceVersion() == null) {
                    return tree;
                }
                return source.withMarkers(source.getMarkers().add(
                        new SpringWebMvcProjectMarker(UUID.randomUUID(),
                                selection.sourceVersion())));
            }
        };
    }

    private static ProjectSelection mavenState(
            Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> definitions = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedSpringWebMvcDependency.isMavenPropertyDefinition(
                        getCursor(), visited)) {
                    PropertyKey key = propertyKey(getCursor(), visited.getName());
                    definitions.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value ->
                            values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profileNames.add(key.name());
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<PropertyKey, Integer> references = new HashMap<>();
        Map<PropertyKey, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(
                    Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                Matcher matcher = PROPERTY.matcher(visited.getText());
                while (matcher.find()) {
                    PropertyKey key = resolveOwner(
                            getCursor(), matcher.group(1), definitions);
                    references.merge(key, 1, Integer::sum);
                    if (ownedVersionReference(getCursor(), visited.getText())) {
                        ownedReferences.merge(key, 1, Integer::sum);
                    }
                }
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(
                    Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                Matcher matcher = PROPERTY.matcher(visited.getValueAsString());
                while (matcher.find()) {
                    references.merge(resolveOwner(
                            getCursor(), matcher.group(1), definitions), 1,
                            Integer::sum);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Set<PropertyKey> safe = new HashSet<>();
        ownedReferences.forEach((key, count) -> {
            if (definitions.getOrDefault(key, 0) == 1 &&
                UpgradeSelectedSpringWebMvcDependency.SOURCE_VERSIONS.contains(
                        values.get(key)) &&
                count > 0 && count.equals(references.get(key)) &&
                !("ROOT".equals(key.scope()) && profileNames.contains(key.name()))) {
                safe.add(key);
            }
        });

        Eligibility eligibility = new Eligibility();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!UpgradeSelectedSpringWebMvcDependency.isTargetDependency(
                        getCursor(), visited)) {
                    return visited;
                }
                if (!UpgradeSelectedSpringWebMvcDependency.isStandardArtifact(visited)) {
                    eligibility.other = true;
                    return visited;
                }
                String raw = visited.getChildValue("version")
                        .map(String::trim).orElse(null);
                if (raw == null) {
                    eligibility.other = true;
                    return visited;
                }
                Matcher matcher = PROPERTY.matcher(raw);
                if (matcher.matches()) {
                    PropertyKey key = resolveOwner(
                            getCursor(), matcher.group(1), definitions);
                    eligibility.record(safe.contains(key) ? values.get(key) : null);
                } else {
                    eligibility.record(raw);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        return eligibility.selection();
    }

    private static ProjectSelection groovyState(
            G.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency =
                        UpgradeSelectedSpringWebMvcDependency.isGradleDependencyInvocation(
                                getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!dependency) return visited;
                String group = UpgradeSelectedSpringWebMvcDependency.mapValue(
                        visited, "group");
                String name = UpgradeSelectedSpringWebMvcDependency.mapValue(
                        visited, "name");
                if (UpgradeSelectedSpringWebMvcDependency.GROUP.equals(group) &&
                    UpgradeSelectedSpringWebMvcDependency.ARTIFACT.equals(name)) {
                    if (UpgradeSelectedSpringWebMvcDependency.hasVariant(visited)) {
                        eligibility.other = true;
                    } else {
                        eligibility.record(
                                UpgradeSelectedSpringWebMvcDependency.mapValue(
                                        visited, "version"));
                    }
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct =
                        UpgradeSelectedSpringWebMvcDependency.isDirectDependencyLiteral(
                                getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct) recordCoordinate(visited, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    private static ProjectSelection kotlinState(
            K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct =
                        UpgradeSelectedSpringWebMvcDependency.isDirectDependencyLiteral(
                                getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct) recordCoordinate(visited, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    private static void recordCoordinate(
            J.Literal literal, Eligibility eligibility) {
        if (!(literal.getValue() instanceof String coordinate)) return;
        String[] parts = coordinate.split(":", -1);
        if (parts.length >= 2 &&
            UpgradeSelectedSpringWebMvcDependency.GROUP.equals(parts[0]) &&
            UpgradeSelectedSpringWebMvcDependency.ARTIFACT.equals(parts[1])) {
            eligibility.record(parts.length == 3 ? parts[2] : null);
        }
    }

    private static boolean ownedVersionReference(Cursor cursor, String text) {
        if (!PROPERTY.matcher(text.trim()).matches()) return false;
        Cursor version = cursor.getParentTreeCursor();
        if (!(version.getValue() instanceof Xml.Tag versionTag) ||
            !"version".equals(versionTag.getName())) {
            return false;
        }
        Cursor dependency = version.getParentTreeCursor();
        return dependency.getValue() instanceof Xml.Tag dependencyTag &&
               UpgradeSelectedSpringWebMvcDependency.isStandardTargetDependency(
                       dependency, dependencyTag);
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
            if (current.getValue() instanceof Xml.Tag tag &&
                "profile".equals(tag.getName())) {
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
            } else if (UpgradeSelectedSpringWebMvcDependency.SOURCE_VERSIONS
                    .contains(version)) {
                selectedVersions.add(version);
            } else {
                other = true;
            }
        }

        ProjectSelection selection() {
            if (selectedVersions.isEmpty()) {
                return other ? new ProjectSelection(State.OTHER, null) : null;
            }
            if (other || selectedVersions.size() != 1) {
                return new ProjectSelection(State.CONFLICT, null);
            }
            return new ProjectSelection(State.SELECTED,
                    selectedVersions.iterator().next());
        }
    }
}
