package com.huawei.clouds.openrewrite.springboot;

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

/**
 * Carries exact source-version eligibility from each nearest build root to later
 * official source and configuration recipes without printing a marker.
 */
public final class MarkSelectedSpringBootProjects
        extends ScanningRecipe<MarkSelectedSpringBootProjects.Projects> {
    enum State {
        SELECTED,
        OTHER,
        CONFLICT
    }

    static final class Projects {
        private final Set<Path> roots = new HashSet<>();
        private final Map<Path, State> states = new HashMap<>();
        private final Map<Path, String> sourceVersions = new HashMap<>();
        private final Set<Path> mavenPoms = new HashSet<>();
        private final Map<Path, MavenCoordinate> mavenCoordinates = new HashMap<>();
        private final Map<Path, MavenParent> mavenParents = new HashMap<>();

        void recordBoundary(Path sourcePath) {
            roots.add(root(sourcePath));
        }

        void recordMavenBoundary(Path sourcePath, Xml.Document document) {
            Path root = root(sourcePath);
            roots.add(root);
            mavenPoms.add(sourcePath.normalize());
            MavenCoordinate coordinate = projectCoordinate(document);
            if (coordinate != null) mavenCoordinates.put(root, coordinate);
            MavenParent parent = localParent(sourcePath, document);
            if (parent != null) mavenParents.put(root, parent);
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

        State nearest(Path sourcePath) {
            Path nearest = nearestRoot(sourcePath);
            ProjectSelection selection = selection(nearest, new HashSet<>());
            return selection == null ? null : selection.state();
        }

        String nearestSourceVersion(Path sourcePath) {
            Path nearest = nearestRoot(sourcePath);
            ProjectSelection selection = selection(nearest, new HashSet<>());
            return selection == null || selection.state() != State.SELECTED
                    ? null : selection.sourceVersion();
        }

        private ProjectSelection selection(Path root, Set<Path> seen) {
            if (root == null || !seen.add(root)) return null;
            State direct = states.get(root);
            if (direct != null) {
                return new ProjectSelection(direct,
                        direct == State.SELECTED ? sourceVersions.get(root) : null);
            }
            MavenParent parent = mavenParents.get(root);
            if (parent == null || !mavenPoms.contains(parent.pom())) {
                return new ProjectSelection(State.OTHER, null);
            }
            Path parentRoot = root(parent.pom());
            MavenCoordinate actual = mavenCoordinates.get(parentRoot);
            if (actual == null || !parent.coordinate().equals(actual)) {
                return new ProjectSelection(State.OTHER, null);
            }
            ProjectSelection inherited = selection(parentRoot, seen);
            return inherited == null
                    ? new ProjectSelection(State.OTHER, null) : inherited;
        }

        private Path nearestRoot(Path sourcePath) {
            Path nearest = null;
            for (Path root : roots) {
                if ((root.toString().isEmpty() || sourcePath.startsWith(root)) &&
                    (nearest == null || depth(root) > depth(nearest))) {
                    nearest = root;
                }
            }
            return nearest;
        }

        private static int depth(Path path) {
            return path.toString().isEmpty() ? 0 : path.getNameCount();
        }

        private static Path root(Path sourcePath) {
            Path parent = sourcePath.getParent();
            return parent == null ? Path.of("") : parent;
        }

        private static MavenCoordinate projectCoordinate(Xml.Document document) {
            Xml.Tag project = document.getRoot();
            if (!"project".equals(project.getName())) return null;
            Xml.Tag parent = project.getChild("parent").orElse(null);
            String group = value(project, "groupId");
            String version = value(project, "version");
            if (group == null && parent != null) group = value(parent, "groupId");
            if (version == null && parent != null) version = value(parent, "version");
            String artifact = value(project, "artifactId");
            return group == null || artifact == null || version == null
                    ? null : new MavenCoordinate(group, artifact, version);
        }

        private static MavenParent localParent(
                Path sourcePath, Xml.Document document) {
            Xml.Tag parent = document.getRoot().getChild("parent").orElse(null);
            if (parent == null) return null;
            String group = value(parent, "groupId");
            String artifact = value(parent, "artifactId");
            String version = value(parent, "version");
            if (group == null || artifact == null || version == null) return null;

            Xml.Tag relativeTag = parent.getChild("relativePath").orElse(null);
            String relative = relativeTag == null
                    ? "../pom.xml"
                    : relativeTag.getValue().map(String::trim).orElse("");
            if (relative.isBlank()) return null;
            Path sourceRoot = root(sourcePath);
            Path parentPom = sourceRoot.resolve(relative).normalize();
            return new MavenParent(parentPom,
                    new MavenCoordinate(group, artifact, version));
        }

        private static String value(Xml.Tag tag, String child) {
            return tag.getChildValue(child).map(String::trim)
                    .filter(value -> !value.isEmpty()).orElse(null);
        }
    }

    record MavenCoordinate(String group, String artifact, String version) {
    }

    record MavenParent(Path pom, MavenCoordinate coordinate) {
    }

    record ProjectSelection(State state, String sourceVersion) {
    }

    @Override
    public String getDisplayName() {
        return "Mark workbook-selected Spring Boot projects";
    }

    @Override
    public String getDescription() {
        return "Find the nearest Maven or Gradle project that directly owns one of the 19 exact " +
               "Spring Boot source versions, so later official migrations cannot run on target, " +
               "higher, off-whitelist, conflicting, or unrelated projects.";
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
                    SpringBootSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                ProjectSelection selection = null;
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    projects.recordMavenBoundary(source.getSourcePath(), document);
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
                    SpringBootSupport.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(SpringBootProjectMarker.class).isPresent()) {
                    return tree;
                }
                State state = projects.nearest(source.getSourcePath());
                if (state != State.SELECTED) return tree;
                String sourceVersion = projects.nearestSourceVersion(source.getSourcePath());
                if (sourceVersion == null) return tree;
                return source.withMarkers(source.getMarkers().add(
                        new SpringBootProjectMarker(UUID.randomUUID(), sourceVersion)));
            }
        };
    }

    private static ProjectSelection mavenState(Xml.Document document, ExecutionContext ctx) {
        SpringBootSupport.PomProperties properties =
                SpringBootSupport.analyzeProperties(document, ctx);
        Eligibility eligibility = new Eligibility();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                if (!SpringBootSupport.isBootOwner(getCursor(), visited)) return visited;
                if (SpringBootSupport.isBootDependency(getCursor(), visited) &&
                    !SpringBootSupport.isStandardArtifact(visited)) {
                    eligibility.other = true;
                    return visited;
                }
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                if (raw == null) return visited;
                if (!properties.safeReference(raw, getCursor())) {
                    eligibility.other = true;
                    return visited;
                }
                eligibility.record(properties.resolveUnique(raw, getCursor()));
                return visited;
            }
        }.visitNonNull(document, ctx);
        return eligibility.selection();
    }

    private static ProjectSelection groovyState(G.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                recordPluginVersion(getCursor(), visited, eligibility);
                recordDependencyMap(getCursor(), visited, eligibility);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = SpringBootSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isRootPlatformLiteral(getCursor());
                boolean buildscriptPlugin =
                        SpringBootSupport.isBuildscriptClasspathLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                if (buildscriptPlugin) {
                    recordBuildscriptPluginCoordinate(visited, eligibility);
                } else if (direct || platform) {
                    recordCoordinate(visited, platform, eligibility);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    private static ProjectSelection kotlinState(K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                recordPluginVersion(getCursor(), visited, eligibility);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                boolean direct = SpringBootSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isRootPlatformLiteral(getCursor());
                boolean buildscriptPlugin =
                        SpringBootSupport.isBuildscriptClasspathLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, executionContext);
                if (buildscriptPlugin) {
                    recordBuildscriptPluginCoordinate(visited, eligibility);
                } else if (direct || platform) {
                    recordCoordinate(visited, platform, eligibility);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    private static void recordPluginVersion(
            Cursor cursor, J.MethodInvocation invocation, Eligibility eligibility) {
        if (!SpringBootSupport.isBootGradlePluginVersion(cursor, invocation) ||
            invocation.getArguments().size() != 1 ||
            !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String version)) return;
        eligibility.record(version);
    }

    private static void recordDependencyMap(
            Cursor cursor, J.MethodInvocation invocation, Eligibility eligibility) {
        if (!SpringBootSupport.isRootGradleDependency(cursor, invocation)) return;
        String group = SpringBootSupport.mapValue(invocation, "group");
        String name = SpringBootSupport.mapValue(invocation, "name");
        if (!SpringBootSupport.GROUP.equals(group) ||
            !SpringBootSupport.ARTIFACT.equals(name)) return;
        if (SpringBootSupport.hasVariant(invocation)) {
            eligibility.other = true;
            return;
        }
        eligibility.record(SpringBootSupport.mapValue(invocation, "version"));
    }

    private static void recordCoordinate(
            J.Literal literal, boolean allowBom, Eligibility eligibility) {
        if (!(literal.getValue() instanceof String coordinate)) return;
        String version = SpringBootSupport.coordinateVersion(
                coordinate, SpringBootSupport.ARTIFACT);
        if (version != null) {
            eligibility.record(version);
            return;
        }
        if (allowBom) {
            version = SpringBootSupport.coordinateVersion(
                    coordinate, SpringBootSupport.BOM);
            if (version != null) eligibility.record(version);
        }
    }

    private static void recordBuildscriptPluginCoordinate(
            J.Literal literal, Eligibility eligibility) {
        if (!(literal.getValue() instanceof String coordinate)) return;
        String version = SpringBootSupport.coordinateVersion(
                coordinate, SpringBootSupport.GRADLE_PLUGIN_ARTIFACT);
        if (version != null) eligibility.record(version);
    }

    private static boolean isRootPlatformLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof J.MethodInvocation platform) ||
            !("platform".equals(platform.getSimpleName()) ||
              "enforcedPlatform".equals(platform.getSimpleName()))) return false;
        Cursor dependency = parent.getParentTreeCursor();
        return dependency.getValue() instanceof J.MethodInvocation invocation &&
               SpringBootSupport.isRootGradleDependency(dependency, invocation);
    }

    private static final class Eligibility {
        private final Set<String> selectedVersions = new HashSet<>();
        private boolean other;

        void record(String version) {
            if (version == null || version.isBlank()) {
                other = true;
            } else if (SpringBootSupport.SOURCE_VERSIONS.contains(version)) {
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
            return new ProjectSelection(
                    State.SELECTED, selectedVersions.iterator().next());
        }
    }
}
