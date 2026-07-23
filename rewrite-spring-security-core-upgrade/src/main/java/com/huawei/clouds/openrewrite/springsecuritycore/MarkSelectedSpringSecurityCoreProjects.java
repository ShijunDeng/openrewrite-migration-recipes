package com.huawei.clouds.openrewrite.springsecuritycore;

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

/**
 * Captures exact source-version ownership before build-file edits. Source and
 * configuration recipes later consume the non-printing marker.
 */
public final class MarkSelectedSpringSecurityCoreProjects
        extends ScanningRecipe<MarkSelectedSpringSecurityCoreProjects.Projects> {

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
        return "Mark workbook-selected Spring Security Core projects";
    }

    @Override
    public String getDescription() {
        return "Scan the nearest Maven or Gradle build root before dependency edits and carry eligibility only " +
               "when it owns one exact, non-conflicting workbook source version.";
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
                    SpringSecurityCoreSupport.generated(source.getSourcePath())) {
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
                    SpringSecurityCoreSupport.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(SpringSecurityCoreProjectMarker.class).isPresent()) {
                    return tree;
                }
                ProjectSelection selection = projects.nearest(source.getSourcePath());
                if (selection == null || selection.state() != State.SELECTED ||
                    selection.sourceVersion() == null) {
                    return tree;
                }
                return source.withMarkers(source.getMarkers().add(
                        new SpringSecurityCoreProjectMarker(UUID.randomUUID(), selection.sourceVersion())));
            }
        };
    }

    private static ProjectSelection mavenState(Xml.Document document, ExecutionContext ctx) {
        SpringSecurityCoreSupport.PomProperties properties =
                SpringSecurityCoreSupport.analyzeProperties(document, ctx);
        Eligibility eligibility = new Eligibility();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!SpringSecurityCoreSupport.isTargetDependency(getCursor(), visited)) return visited;
                if (!SpringSecurityCoreSupport.standardJar(visited)) {
                    eligibility.other = true;
                    return visited;
                }
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                if (raw == null) {
                    eligibility.other = true;
                    return visited;
                }
                Matcher matcher = SpringSecurityCoreSupport.PROPERTY_REFERENCE.matcher(raw);
                if (matcher.matches()) {
                    SpringSecurityCoreSupport.PropertyKey key =
                            SpringSecurityCoreSupport.resolveOwner(
                                    getCursor(), matcher.group(1), properties.definitions());
                    eligibility.record(properties.safe().contains(key)
                            ? properties.values().get(key) : null);
                } else {
                    eligibility.record(raw);
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
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringSecurityCoreSupport.isRootGradleDependency(
                        getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!dependency) return visited;
                String group = SpringSecurityCoreSupport.mapValue(visited, "group");
                String name = SpringSecurityCoreSupport.mapValue(visited, "name");
                if (SpringSecurityCoreSupport.GROUP.equals(group) &&
                    SpringSecurityCoreSupport.ARTIFACT.equals(name)) {
                    if (SpringSecurityCoreSupport.hasVariant(visited)) {
                        eligibility.other = true;
                    } else {
                        eligibility.record(SpringSecurityCoreSupport.mapValue(visited, "version"));
                    }
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct) recordCoordinate(visited, eligibility);
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
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringSecurityCoreSupport.isRootGradleDependency(
                        getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!dependency) return visited;
                String group = SpringSecurityCoreSupport.mapValue(visited, "group");
                String name = SpringSecurityCoreSupport.mapValue(visited, "name");
                if (SpringSecurityCoreSupport.GROUP.equals(group) &&
                    SpringSecurityCoreSupport.ARTIFACT.equals(name)) {
                    if (SpringSecurityCoreSupport.hasVariant(visited)) {
                        eligibility.other = true;
                    } else {
                        eligibility.record(SpringSecurityCoreSupport.mapValue(visited, "version"));
                    }
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct) recordCoordinate(visited, eligibility);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.selection();
    }

    private static void recordCoordinate(J.Literal literal, Eligibility eligibility) {
        if (!(literal.getValue() instanceof String coordinate)) return;
        String plain = coordinate;
        int at = plain.indexOf('@');
        if (at >= 0) plain = plain.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length >= 2 &&
            SpringSecurityCoreSupport.GROUP.equals(parts[0]) &&
            SpringSecurityCoreSupport.ARTIFACT.equals(parts[1])) {
            if (at >= 0) eligibility.other = true;
            else eligibility.record(parts.length == 3 ? parts[2] : null);
        }
    }

    private static final class Eligibility {
        private final Set<String> selectedVersions = new HashSet<>();
        private boolean other;

        void record(String version) {
            if (version == null || version.isBlank()) {
                other = true;
            } else if (SpringSecurityCoreSupport.SOURCE_VERSIONS.contains(version)) {
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
            return new ProjectSelection(State.SELECTED, selectedVersions.iterator().next());
        }
    }
}
