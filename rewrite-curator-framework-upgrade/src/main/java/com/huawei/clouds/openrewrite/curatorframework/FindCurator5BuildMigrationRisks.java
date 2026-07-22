package com.huawei.clouds.openrewrite.curatorframework;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;
import java.util.regex.Pattern;

/** Marks exact explicit Curator companion and ZooKeeper 3.4 build declarations. */
public final class FindCurator5BuildMigrationRisks extends Recipe {
    private static final Pattern ZOOKEEPER_34 = Pattern.compile("3\\.4(?:\\..+)?");
    private static final Pattern FIXED_VERSION = Pattern.compile("[0-9]+(?:\\.[0-9]+)+(?:[-.][A-Za-z0-9]+)*");
    private static final String ZOOKEEPER_MESSAGE =
            "Curator 5 no longer supports ZooKeeper 3.4.x; upgrade the owning client/BOM and ensemble plan, then verify TLS/SASL, ACLs, watches, sessions, container nodes, and rolling compatibility";
    private static final String CURATOR_MESSAGE =
            "This explicit Curator companion is not aligned to 5.7.1; align the complete Curator family and verify binary linkage, transitive ZooKeeper, recipes, test servers, exclusions, shading, and classloaders";
    private static final String FRAMEWORK_MESSAGE =
            "This explicit Curator Framework version is outside the workbook selection or target; choose the owning version deliberately and verify the resolved Curator/ZooKeeper graph instead of widening the automatic upgrade";
    private static final String EXTERNAL_CURATOR_MESSAGE =
            "This Curator version is versionless, property-managed, ranged, dynamic, or otherwise not a fixed visible value; migrate its actual parent/BOM/catalog/property owner and verify that the complete Curator family resolves to 5.7.1";
    private static final String CUSTOM_ARTIFACT_MESSAGE =
            "This classified or non-JAR Curator/ZooKeeper artifact is outside deterministic runtime upgrade scope; verify that the target release publishes the same artifact shape before migrating it";

    @Override
    public String getDisplayName() {
        return "Find Curator 5 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark only owned Maven/Gradle declarations that explicitly pin ZooKeeper 3.4.x or leave Curator " +
               "Framework/companions on a fixed non-target version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedCuratorFrameworkDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                            Xml.Tag visited = super.visitTag(tag, executionContext);
                            if (!UpgradeSelectedCuratorFrameworkDependency.isProjectDependency(getCursor(), visited)) {
                                return visited;
                            }
                            String group = visited.getChildValue("groupId").orElse("");
                            String artifact = visited.getChildValue("artifactId").orElse("");
                            String version = visited.getChildValue("version").map(String::trim).orElse("");
                            if (isMigrationCoordinate(group, artifact) &&
                                !standardProjectDependency(getCursor(), visited)) {
                                return mark(visited, CUSTOM_ARTIFACT_MESSAGE);
                            }
                            if (isCurator(group, artifact) && !FIXED_VERSION.matcher(version).matches()) {
                                return visited.getChild("version")
                                        .map(child -> markChild(visited, "version", EXTERNAL_CURATOR_MESSAGE))
                                        .orElseGet(() -> mark(visited, EXTERNAL_CURATOR_MESSAGE));
                            }
                            String message = coordinateMessage(group, artifact, version);
                            return message == null ? visited : markChild(visited, "version", message);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                        ExecutionContext executionContext) {
                            boolean direct = UpgradeSelectedCuratorFrameworkDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                            if (!direct) return visited;
                            String group = mapValue(visited, "group");
                            String artifact = mapValue(visited, "name");
                            if (isCurator(group, artifact) && hasVariant(visited)) {
                                return mark(visited, CUSTOM_ARTIFACT_MESSAGE);
                            }
                            String version = mapValue(visited, "version");
                            String message = coordinateMessage(group, artifact, version);
                            if (message == null) {
                                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                if (map != null) {
                                    group = mapValue(map, "group");
                                    artifact = mapValue(map, "name");
                                    if (isCurator(group, artifact) && hasVariant(map)) {
                                        return mark(visited, CUSTOM_ARTIFACT_MESSAGE);
                                    }
                                    version = mapValue(map, "version");
                                    message = coordinateMessage(group, artifact, version);
                                }
                            }
                            if (message == null && isCurator(group, artifact) &&
                                (version == null || !FIXED_VERSION.matcher(version).matches())) {
                                message = EXTERNAL_CURATOR_MESSAGE;
                            }
                            return message == null ? visited : mark(visited, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = UpgradeSelectedCuratorFrameworkDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? markCoordinate(visited) : visited;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                            boolean direct = UpgradeSelectedCuratorFrameworkDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, executionContext);
                            return direct ? markCoordinate(visited) : visited;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean standardProjectDependency(Cursor cursor, Xml.Tag tag) {
        return UpgradeSelectedCuratorFrameworkDependency.isProjectDependency(cursor, tag) &&
               tag.getChild("classifier").isEmpty() &&
               "jar".equals(tag.getChildValue("type").orElse("jar"));
    }

    private static String coordinateMessage(String group, String artifact, String version) {
        if (group == null || artifact == null || version == null || !FIXED_VERSION.matcher(version).matches()) {
            return null;
        }
        if ("org.apache.zookeeper".equals(group) && "zookeeper".equals(artifact) &&
            ZOOKEEPER_34.matcher(version).matches()) return ZOOKEEPER_MESSAGE;
        if (!isCurator(group, artifact)) return null;
        if ("curator-framework".equals(artifact)) {
            return UpgradeSelectedCuratorFrameworkDependency.TARGET.equals(version) ? null : FRAMEWORK_MESSAGE;
        }
        return UpgradeSelectedCuratorFrameworkDependency.TARGET.equals(version) ? null : CURATOR_MESSAGE;
    }

    private static boolean isCurator(String group, String artifact) {
        return "org.apache.curator".equals(group) && artifact != null && artifact.startsWith("curator-");
    }

    private static boolean isMigrationCoordinate(String group, String artifact) {
        return isCurator(group, artifact) ||
               "org.apache.zookeeper".equals(group) && "zookeeper".equals(artifact);
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length >= 2 && isCurator(parts[0], parts[1])) {
            if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) {
                return mark(literal, CUSTOM_ARTIFACT_MESSAGE);
            }
            if (parts.length != 3 || !FIXED_VERSION.matcher(parts[2]).matches()) {
                return mark(literal, EXTERNAL_CURATOR_MESSAGE);
            }
        }
        if (parts.length != 3) return literal;
        String message = coordinateMessage(parts[0], parts[1], parts[2]);
        return message == null ? literal : mark(literal, message);
    }

    private static Xml.Tag markChild(Xml.Tag owner, String name, String message) {
        return owner.getChild(name).map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static boolean hasVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static boolean hasVariant(G.MapLiteral map) {
        return map.getElements().stream()
                .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
