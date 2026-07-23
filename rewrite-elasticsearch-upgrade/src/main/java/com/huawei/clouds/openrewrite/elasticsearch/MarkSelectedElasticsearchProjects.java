package com.huawei.clouds.openrewrite.elasticsearch;

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

/**
 * Captures exact source-version ownership before any dependency is edited.
 *
 * <p>A root is selected only when every encountered Elasticsearch identity at
 * that root agrees on the sole approved source coordinate. A target, future,
 * off-list, unresolved, variant, or Elasticsearch Server declaration turns a
 * mixed root into a conflict and blocks all automatic work.</p>
 */
public final class MarkSelectedElasticsearchProjects
        extends ScanningRecipe<MarkSelectedElasticsearchProjects.Projects> {

    enum State {
        SELECTED,
        OTHER,
        CONFLICT
    }

    static final class Projects {
        private final Set<Path> roots = new HashSet<>();
        private final Map<Path, State> states = new HashMap<>();

        void record(Path sourcePath, State state) {
            Path root = root(sourcePath);
            roots.add(root);
            State existing = states.get(root);
            states.put(root, existing == null || existing == state
                    ? state : State.CONFLICT);
        }

        State nearest(Path sourcePath) {
            Path nearest = null;
            for (Path root : roots) {
                if ((root.toString().isEmpty() || sourcePath.startsWith(root)) &&
                    (nearest == null || depth(root) > depth(nearest))) {
                    nearest = root;
                }
            }
            return nearest == null ? null : states.getOrDefault(nearest, State.OTHER);
        }

        private static Path root(Path sourcePath) {
            Path parent = sourcePath.getParent();
            return parent == null ? Path.of("") : parent;
        }

        private static int depth(Path path) {
            return path.toString().isEmpty() ? 0 : path.getNameCount();
        }
    }

    @Override
    public String getDisplayName() {
        return "Mark workbook-selected Testcontainers Elasticsearch projects";
    }

    @Override
    public String getDescription() {
        return "Scan the nearest Maven or Gradle build root before dependency edits and select it only when " +
               "it exclusively owns org.testcontainers:elasticsearch:1.17.6 without an identity conflict.";
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
                    ElasticsearchUpgradeSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                State state = null;
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    state = mavenState(document, ctx);
                } else if (tree instanceof G.CompilationUnit groovy && "build.gradle".equals(file)) {
                    state = groovyState(groovy, ctx);
                } else if (tree instanceof K.CompilationUnit kotlin &&
                           "build.gradle.kts".equals(file)) {
                    state = kotlinState(kotlin, ctx);
                }
                if (state != null) {
                    projects.record(source.getSourcePath(), state);
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
                    ElasticsearchUpgradeSupport.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(ElasticsearchProjectMarker.class).isPresent() ||
                    projects.nearest(source.getSourcePath()) != State.SELECTED) {
                    return tree;
                }
                return source.withMarkers(source.getMarkers().add(
                        new ElasticsearchProjectMarker(UUID.randomUUID(),
                                ElasticsearchUpgradeSupport.SOURCE)));
            }
        };
    }

    private static State mavenState(Xml.Document source, ExecutionContext ctx) {
        MavenProperties properties = MavenProperties.analyze(source, ctx);
        Eligibility eligibility = new Eligibility();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                boolean testcontainersCoordinate = coordinate(
                        visited, ElasticsearchUpgradeSupport.GROUP,
                        ElasticsearchUpgradeSupport.ARTIFACT);
                boolean serverCoordinate = coordinate(
                        visited, ElasticsearchUpgradeSupport.SERVER_GROUP,
                        ElasticsearchUpgradeSupport.SERVER_ARTIFACT);
                if (testcontainersCoordinate &&
                    !ElasticsearchUpgradeSupport.isTestcontainersDependency(
                            getCursor(), visited)) {
                    eligibility.recordOther();
                } else if (testcontainersCoordinate) {
                    if (!ElasticsearchUpgradeSupport.standardJar(visited)) {
                        eligibility.recordOther();
                    } else {
                        String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                        eligibility.recordTestcontainers(
                                properties.resolveOwnedVersion(raw, getCursor()));
                    }
                } else if (serverCoordinate) {
                    eligibility.recordOther();
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return eligibility.state();
    }

    private static State groovyState(G.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = ElasticsearchUpgradeSupport.isGradleDependencyInvocation(
                        getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                String group = ElasticsearchUpgradeSupport.mapValue(visited, "group");
                String name = ElasticsearchUpgradeSupport.mapValue(visited, "name");
                if (ElasticsearchUpgradeSupport.GROUP.equals(group) &&
                    ElasticsearchUpgradeSupport.ARTIFACT.equals(name)) {
                    if (!direct || ElasticsearchUpgradeSupport.hasVariant(visited)) {
                        eligibility.recordOther();
                    } else {
                        eligibility.recordTestcontainers(
                                ElasticsearchUpgradeSupport.mapValue(visited, "version"));
                    }
                } else if (ElasticsearchUpgradeSupport.SERVER_GROUP.equals(group) &&
                           ElasticsearchUpgradeSupport.SERVER_ARTIFACT.equals(name)) {
                    eligibility.recordOther();
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = ElasticsearchUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, eligibility, direct);
                return visited;
            }
        }.visitNonNull(source, ctx);
        if (unresolvedOwner(source.printAll())) {
            eligibility.recordOther();
        }
        return eligibility.state();
    }

    private static State kotlinState(K.CompilationUnit source, ExecutionContext ctx) {
        Eligibility eligibility = new Eligibility();
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = ElasticsearchUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                recordCoordinate(visited, eligibility, direct);
                return visited;
            }
        }.visitNonNull(source, ctx);
        if (unresolvedOwner(source.printAll())) {
            eligibility.recordOther();
        }
        return eligibility.state();
    }

    private static void recordCoordinate(J.Literal literal, Eligibility eligibility,
                                         boolean direct) {
        if (!(literal.getValue() instanceof String coordinate)) {
            return;
        }
        int at = coordinate.indexOf('@');
        String plain = at < 0 ? coordinate : coordinate.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2) {
            return;
        }
        if (ElasticsearchUpgradeSupport.GROUP.equals(parts[0]) &&
            ElasticsearchUpgradeSupport.ARTIFACT.equals(parts[1])) {
            if (!direct || parts.length != 3 || at >= 0) {
                eligibility.recordOther();
            } else {
                eligibility.recordTestcontainers(parts[2]);
            }
        } else if (ElasticsearchUpgradeSupport.SERVER_GROUP.equals(parts[0]) &&
                   ElasticsearchUpgradeSupport.SERVER_ARTIFACT.equals(parts[1])) {
            eligibility.recordOther();
        }
    }

    private static boolean coordinate(Xml.Tag tag, String group, String artifact) {
        return "dependency".equals(tag.getName()) &&
               group.equals(tag.getChildValue("groupId").map(String::trim).orElse(null)) &&
               artifact.equals(tag.getChildValue("artifactId").map(String::trim).orElse(null));
    }

    private static boolean unresolvedOwner(String source) {
        return source.matches("(?s).*\\blibs(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.elasticsearch\\b.*") ||
               source.contains("org.testcontainers:elasticsearch:$") ||
               source.contains("org.testcontainers:elasticsearch:${") ||
               source.matches("(?s).*org\\.testcontainers:elasticsearch:[+\\[].*") ||
               source.contains("platform(") && source.contains("org.testcontainers");
    }

    private static final class Eligibility {
        private boolean selected;
        private boolean other;

        void recordTestcontainers(String version) {
            if (ElasticsearchUpgradeSupport.SOURCE.equals(version)) {
                selected = true;
            } else {
                other = true;
            }
        }

        void recordOther() {
            other = true;
        }

        State state() {
            if (selected && !other) {
                return State.SELECTED;
            }
            return selected ? State.CONFLICT : State.OTHER;
        }
    }
}
