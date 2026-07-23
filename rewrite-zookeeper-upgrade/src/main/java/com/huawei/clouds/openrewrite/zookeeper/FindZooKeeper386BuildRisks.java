package com.huawei.clouds.openrewrite.zookeeper;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reports dependency ownership and runtime graph decisions that cannot be changed safely from syntax alone. */
public final class FindZooKeeper386BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("^\\$\\{([^}]+)}$");

    static final String NO_DOWNGRADE_PREFIX = "目标版本冲突（禁止降级）";
    static final String OWNER =
            "This ZooKeeper version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, shared or " +
            "externally owned; migrate the real owner and prove that 3.8.6 resolves without widening the whitelist";
    static final String OUTSIDE =
            "This fixed ZooKeeper version is outside the approved source set and target; choose its forward support " +
            "path explicitly instead of widening automatic migration";
    static final String VARIANT =
            "This classified or non-JAR ZooKeeper artifact is outside deterministic scope; verify its 3.8.6 artifact " +
            "shape, generated protocol classes and packaging before changing it";
    static final String JUTE =
            "ZooKeeper and zookeeper-jute contain coupled generated protocol classes; align both to 3.8.6 and verify " +
            "that dependency exclusions, shading and generated classes do not leave a mixed runtime";
    static final String LOGGING =
            "ZooKeeper 3.8.5+ moved its runtime to SLF4J 2 and Logback 1.3; inspect direct bindings/bridges, remove " +
            "mixed SLF4J 1.7 providers, and smoke-test startup logging, audit logging and redaction";
    static final String TRANSPORT =
            "This direct Netty or Jetty override can replace ZooKeeper 3.8.6's tested transport graph; align it using " +
            "an approved security target and test TLS, four-letter-word/admin endpoints and connection lifecycle";
    static final String PACKAGING =
            "Shading or relocating org.apache.zookeeper can break service loading, Jute classes, audit logging and " +
            "server resources; verify relocation rules and the final packaged client/server runtime";

    private static final Set<String> LOGGING_ARTIFACTS = Set.of(
            "slf4j-api", "slf4j-simple", "slf4j-reload4j", "log4j-slf4j-impl", "log4j-slf4j2-impl",
            "jul-to-slf4j", "jcl-over-slf4j", "logback-classic");

    @Override
    public String getDisplayName() {
        return "Find ZooKeeper 3.8.6 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Preserve and mark every would-be downgrade, unresolved/outside version, variant, zookeeper-jute " +
               "skew, dependency exclusion, logging-provider boundary, transport override and shading decision.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    ZooKeeperSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Map<String, String> rootProperties = new HashMap<>();
        Map<String, Map<String, String>> profileProperties = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (ZooKeeperSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    String profile = profileId(getCursor());
                    Map<String, String> target = profile == null ? rootProperties :
                            profileProperties.computeIfAbsent(profile, ignored -> new HashMap<>());
                    visited.getValue().ifPresent(value -> target.put(visited.getName(), value.trim()));
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if ("plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) &&
                    "maven-shade-plugin".equals(visited.getChildValue("artifactId").orElse("")) &&
                    mentionsZooKeeper(visited)) return mark(visited, PACKAGING);
                if (!ZooKeeperSupport.isProjectDependency(getCursor(), visited)) return visited;

                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String raw = visited.getChildValue("version").map(String::trim).orElse("");
                String version = resolve(raw, profileId(getCursor()), rootProperties, profileProperties);
                if (ZooKeeperSupport.GROUP.equals(group) && ZooKeeperSupport.ARTIFACT.equals(artifact)) {
                    if (!ZooKeeperSupport.standardJar(visited)) return mark(visited, VARIANT);
                    String message = ownerMessage(version);
                    if (message != null) return markVersion(visited, message);
                    if (visited.getChild("exclusions").isPresent()) return mark(visited, JUTE);
                    return visited;
                }
                if (ZooKeeperSupport.GROUP.equals(group) && ZooKeeperSupport.JUTE.equals(artifact) &&
                    !ZooKeeperSupport.TARGET.equals(version)) return markVersion(visited, JUTE);
                if (loggingDependency(group, artifact)) return mark(visited, LOGGING);
                if (transportDependency(group, artifact)) return mark(visited, TRANSPORT);
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = ZooKeeperSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if ("relocate".equals(visited.getSimpleName()) &&
                    visited.getArguments().stream().anyMatch(FindZooKeeper386BuildRisks::mentionsZooKeeperLiteral)) {
                    return mark(visited, PACKAGING);
                }
                if (!dependency) return visited;
                String group = ZooKeeperSupport.mapValue(visited, "group");
                String artifact = ZooKeeperSupport.mapValue(visited, "name");
                String version = ZooKeeperSupport.mapValue(visited, "version");
                boolean variant = ZooKeeperSupport.hasVariant(visited);
                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                if (map != null) {
                    group = ZooKeeperSupport.mapValue(map, "group");
                    artifact = ZooKeeperSupport.mapValue(map, "name");
                    version = ZooKeeperSupport.mapValue(map, "version");
                    variant = ZooKeeperSupport.hasVariant(map);
                }
                String message = dependencyMessage(group, artifact, version, variant);
                if (message != null) return mark(visited, message);
                return visited.getArguments().stream().anyMatch(FindZooKeeper386BuildRisks::dynamicZooKeeper)
                        ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = ZooKeeperSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = ZooKeeperSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                return dependency && visited.getArguments().stream().anyMatch(FindZooKeeper386BuildRisks::dynamicZooKeeper)
                        ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = ZooKeeperSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (ZooKeeperSupport.GROUP.equals(group) && ZooKeeperSupport.ARTIFACT.equals(artifact)) {
            return variant ? VARIANT : ownerMessage(version);
        }
        if (ZooKeeperSupport.GROUP.equals(group) && ZooKeeperSupport.JUTE.equals(artifact) &&
            !ZooKeeperSupport.TARGET.equals(version)) return JUTE;
        if (loggingDependency(group, artifact)) return LOGGING;
        if (transportDependency(group, artifact)) return TRANSPORT;
        return null;
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return null;
        String group = parts[0];
        String artifact = parts[1];
        String version = parts.length == 3 ? parts[2] : null;
        if (ZooKeeperSupport.GROUP.equals(group) && ZooKeeperSupport.ARTIFACT.equals(artifact)) {
            if (parts.length > 3 || version != null && version.contains("@")) return VARIANT;
            return ownerMessage(version);
        }
        if (ZooKeeperSupport.GROUP.equals(group) && ZooKeeperSupport.JUTE.equals(artifact) &&
            !ZooKeeperSupport.TARGET.equals(version)) return JUTE;
        if (loggingDependency(group, artifact)) return LOGGING;
        if (transportDependency(group, artifact)) return TRANSPORT;
        return null;
    }

    private static String ownerMessage(String version) {
        if (ZooKeeperSupport.TARGET.equals(version) ||
            version != null && ZooKeeperSupport.AUTO_SOURCES.contains(version)) return null;
        if (ZooKeeperSupport.higherThanTarget(version)) return targetConflictMessage(version);
        return version == null || !FIXED.matcher(version).matches() ? OWNER : OUTSIDE;
    }

    static String targetConflictMessage(String version) {
        return NO_DOWNGRADE_PREFIX + ": " + version +
               " is newer than the requested 3.8.6 target or belongs to a newer ZooKeeper branch; this declaration " +
               "is intentionally unchanged and requires an approved forward target";
    }

    private static String resolve(String raw, String profile, Map<String, String> root,
                                  Map<String, Map<String, String>> profiles) {
        java.util.regex.Matcher matcher = PROPERTY.matcher(raw);
        if (!matcher.matches()) return raw.isBlank() ? null : raw;
        String name = matcher.group(1);
        if (profile != null && profiles.getOrDefault(profile, Map.of()).containsKey(name)) {
            return profiles.get(profile).get(name);
        }
        return root.get(name);
    }

    private static String profileId(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return null;
    }

    private static boolean loggingDependency(String group, String artifact) {
        return ("org.slf4j".equals(group) || "ch.qos.logback".equals(group) ||
                "org.apache.logging.log4j".equals(group)) && LOGGING_ARTIFACTS.contains(artifact);
    }

    private static boolean transportDependency(String group, String artifact) {
        return ("io.netty".equals(group) && artifact != null && artifact.startsWith("netty-")) ||
               ("org.eclipse.jetty".equals(group) && artifact != null && artifact.startsWith("jetty-"));
    }

    private static boolean dynamicZooKeeper(Object expression) {
        String text = expression.toString();
        return text.contains("org.apache.zookeeper") && text.contains("zookeeper");
    }

    private static boolean mentionsZooKeeperLiteral(Object expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value &&
               (value.equals("org.apache.zookeeper") || value.startsWith("org.apache.zookeeper."));
    }

    private static boolean mentionsZooKeeper(Xml.Tag tag) {
        if (tag.getValue().map(String::trim)
                .filter(value -> value.contains("org.apache.zookeeper")).isPresent()) return true;
        if (tag.getAttributes().stream().map(Xml.Attribute::getValueAsString)
                .anyMatch(value -> value.contains("org.apache.zookeeper"))) return true;
        return tag.getChildren().stream().anyMatch(FindZooKeeper386BuildRisks::mentionsZooKeeper);
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag p) || !"plugins".equals(p.getName())) return false;
        for (Cursor current = plugins; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "project".equals(tag.getName())) return true;
        }
        return false;
    }

    private static Xml.Tag markVersion(Xml.Tag dependency, String message) {
        return dependency.getChild("version").map(version -> {
            Xml.Tag marked = mark(version, message);
            return marked == version ? dependency : dependency.withContent(dependency.getContent().stream()
                    .map(content -> content == version ? marked : content).toList());
        }).orElseGet(() -> mark(dependency, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree :
                SearchResult.found(tree, message);
    }
}
