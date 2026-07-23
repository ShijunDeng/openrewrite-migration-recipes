package com.huawei.clouds.openrewrite.log4jcore;

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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Find dependency ownership, Log4j family, optional runtime and packaging risks. */
public final class FindLog4jCore25BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile(
            "[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> REMOVED_MODULES = Set.of(
            "log4j-flume-ng", "log4j-kubernetes", "log4j-mongodb3");

    static final String OWNER =
            "This log4j-core version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, shared or " +
            "externally owned; migrate the real owner and verify 2.25.5 resolves";
    static final String OUTSIDE =
            "This fixed log4j-core version is outside the workbook source set and target; choose its support path " +
            "explicitly instead of widening the automatic recipe";
    static final String NO_DOWNGRADE_PREFIX = "目标版本冲突（禁止降级）";
    static final String VARIANT =
            "This classified or non-JAR log4j-core artifact is outside deterministic scope; verify that 2.25.5 " +
            "publishes the required artifact shape before changing it";
    static final String FAMILY =
            "Log4j artifacts share provider, plugin and binary contracts; align this companion to 2.25.5 with " +
            "log4j-bom and verify a single API provider/binding";
    static final String REMOVED_MODULE =
            "This Log4j module left the 2.24+ release: move Flume separately, use Fabric8 Kubernetes logging, or " +
            "replace log4j-mongodb3 with the MongoDB 4/5 module before aligning versions";
    static final String ROUTING_LOOP =
            "log4j-core together with log4j-to-slf4j can create a routing loop when an SLF4J-to-Log4j binding is also " +
            "present; select one logging backend and verify exactly one provider";
    static final String DISRUPTOR =
            "Log4j Core 2.25 async loggers support LMAX Disruptor [3.4,5); align a supported implementation and test " +
            "wait strategy, shutdown, queue saturation and class-loader discovery";
    static final String JANSI =
            "Log4j 2.25 removed its JAnsi 1.x dependency and uses native Windows ANSI support; verify why this explicit " +
            "JAnsi dependency remains and test console, redirected and non-TTY output";
    static final String PACKAGING =
            "Shading, OSGi, JPMS or native packaging must preserve/merge Log4j2Plugins.dat, provider services and " +
            "GraalVM reachability metadata; validate plugin discovery in the packaged artifact";

    @Override
    public String getDisplayName() {
        return "Find Apache Log4j Core 2.25 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved/outside core versions, Log4j family skew, removed modules, routing loops, optional runtimes and packaging integrations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    Log4jCoreSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return groovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return kotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Scopes scopes = scopes(source, ctx);
        if (scopes.empty()) return source;
        Properties properties = properties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!visible(getCursor(), scopes)) return visited;
                if ("plugin".equals(visited.getName()) && projectBuildPlugin(getCursor()) &&
                    packagingPlugin(visited) && mentionsPackaging(visited.printTrimmed(getCursor()))) {
                    return mark(visited, PACKAGING);
                }
                if (!Log4jCoreSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String version = visited.getChildValue("version").map(String::trim).orElse("");
                String resolved = resolve(version, getCursor(), properties);
                if (Log4jCoreSupport.GROUP.equals(group) &&
                    Log4jCoreSupport.ARTIFACT.equals(artifact)) {
                    if (!Log4jCoreSupport.standardJar(visited)) return mark(visited, VARIANT);
                    if (Log4jCoreSupport.TARGET.equals(resolved)) return visited;
                    if (resolved == null || Log4jCoreSupport.SOURCES.contains(resolved) ||
                        !FIXED.matcher(resolved).matches()) return markVersion(visited, OWNER);
                    return markVersion(visited, higherThanTarget(resolved)
                            ? targetConflictMessage(resolved) : OUTSIDE);
                }
                String companion = companionMessage(group, artifact, resolved);
                return companion == null ? visited : markVersion(visited, companion);
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx)) return source;
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = Log4jCoreSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if ("relocate".equals(visited.getSimpleName()) && topLevelOwner(getCursor(), "shadowJar") &&
                    visited.getArguments().stream().anyMatch(FindLog4jCore25BuildRisks::mentionsLog4jLiteral)) {
                    return mark(visited, PACKAGING);
                }
                if (!dependency) return visited;
                String group = Log4jCoreSupport.mapValue(visited, "group");
                String artifact = Log4jCoreSupport.mapValue(visited, "name");
                String version = Log4jCoreSupport.mapValue(visited, "version");
                boolean variant = Log4jCoreSupport.hasVariant(visited);
                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                if (map != null) {
                    group = Log4jCoreSupport.mapValue(map, "group");
                    artifact = Log4jCoreSupport.mapValue(map, "name");
                    version = Log4jCoreSupport.mapValue(map, "version");
                    variant = Log4jCoreSupport.hasVariant(map);
                }
                String message = dependencyMessage(group, artifact, version, variant);
                if (message != null) return mark(visited, message);
                if (visited.getArguments().stream().anyMatch(FindLog4jCore25BuildRisks::dynamicCore)) {
                    return mark(visited, OWNER);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = Log4jCoreSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx)) return source;
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = Log4jCoreSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                return dependency &&
                       visited.getArguments().stream().anyMatch(FindLog4jCore25BuildRisks::dynamicCore)
                        ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = Log4jCoreSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String dependencyMessage(
            String group, String artifact, String version, boolean variant) {
        if (Log4jCoreSupport.GROUP.equals(group) &&
            Log4jCoreSupport.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT;
            if (version == null || !FIXED.matcher(version).matches() ||
                Log4jCoreSupport.SOURCES.contains(version)) return OWNER;
            if (Log4jCoreSupport.TARGET.equals(version)) return null;
            return higherThanTarget(version) ? targetConflictMessage(version) : OUTSIDE;
        }
        return companionMessage(group, artifact, version);
    }

    private static String companionMessage(String group, String artifact, String version) {
        if (Log4jCoreSupport.GROUP.equals(group) && REMOVED_MODULES.contains(artifact)) {
            return REMOVED_MODULE;
        }
        if (Log4jCoreSupport.GROUP.equals(group) && "log4j-to-slf4j".equals(artifact)) {
            return ROUTING_LOOP;
        }
        if (Log4jCoreSupport.GROUP.equals(group) && artifact.startsWith("log4j-")) {
            return Log4jCoreSupport.TARGET.equals(version) ? null : FAMILY;
        }
        if ("com.lmax".equals(group) && "disruptor".equals(artifact) && !supportedDisruptor(version)) {
            return DISRUPTOR;
        }
        if ("org.fusesource.jansi".equals(group) && "jansi".equals(artifact)) return JANSI;
        return null;
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return null;
        String group = parts[0];
        String artifact = parts[1];
        String version = parts.length == 3 ? parts[2] : null;
        if (Log4jCoreSupport.GROUP.equals(group) &&
            Log4jCoreSupport.ARTIFACT.equals(artifact)) {
            if (parts.length > 3 || version != null && version.contains("@")) return VARIANT;
            if (version == null || !FIXED.matcher(version).matches() ||
                Log4jCoreSupport.SOURCES.contains(version)) return OWNER;
            if (Log4jCoreSupport.TARGET.equals(version)) return null;
            return higherThanTarget(version) ? targetConflictMessage(version) : OUTSIDE;
        }
        return companionMessage(group, artifact, version);
    }

    static String targetConflictMessage(String version) {
        return NO_DOWNGRADE_PREFIX + ": " + version +
               " is higher than the exact 2.25.5 target; it remains unchanged";
    }

    private static boolean higherThanTarget(String version) {
        if (version == null || !FIXED.matcher(version).matches()) return false;
        String[] candidate = version.split("[^0-9]+");
        String[] target = Log4jCoreSupport.TARGET.split("\\.");
        int length = Math.max(candidate.length, target.length);
        for (int i = 0; i < length; i++) {
            BigInteger left = new BigInteger(
                    i < candidate.length && !candidate[i].isEmpty() ? candidate[i] : "0");
            BigInteger right = new BigInteger(i < target.length ? target[i] : "0");
            int comparison = left.compareTo(right);
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }

    private static boolean supportedDisruptor(String version) {
        if (version == null || !FIXED.matcher(version).matches()) return false;
        String[] parts = version.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major == 3 && minor >= 4 || major == 4;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Scopes scopes(Xml.Document document, ExecutionContext ctx) {
        boolean[] root = {false};
        Set<String> profiles = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (Log4jCoreSupport.isCoreDependency(getCursor(), visited)) {
                    String owner = scope(getCursor());
                    if ("ROOT".equals(owner)) root[0] = true; else profiles.add(owner);
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        return new Scopes(root[0], Set.copyOf(profiles));
    }

    private static boolean visible(Cursor cursor, Scopes scopes) {
        String owner = scope(cursor);
        if ("ROOT".equals(owner)) return scopes.root() || !scopes.profiles().isEmpty();
        return scopes.root() || scopes.profiles().contains(owner);
    }

    private static Properties properties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (Log4jCoreSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    counts.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);
        return new Properties(counts, values);
    }

    private static String resolve(String version, Cursor cursor, Properties properties) {
        if (FIXED.matcher(version).matches()) return version;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return null;
        String profile = scope(cursor);
        PropertyKey local = new PropertyKey(profile, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(profile) && properties.counts().containsKey(local) ? local : root;
        return properties.counts().getOrDefault(owner, 0) == 1 ? properties.values().get(owner) : null;
    }

    private static String scope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private static boolean hasPrimary(G.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = Log4jCoreSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean hasPrimary(K.CompilationUnit unit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = Log4jCoreSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(unit, ctx);
        return found[0];
    }

    private static boolean invocationMentionsPrimary(J.MethodInvocation method) {
        if (Log4jCoreSupport.GROUP.equals(Log4jCoreSupport.mapValue(method, "group")) &&
            Log4jCoreSupport.ARTIFACT.equals(Log4jCoreSupport.mapValue(method, "name"))) return true;
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && primaryCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                Log4jCoreSupport.GROUP.equals(Log4jCoreSupport.mapValue(map, "group")) &&
                Log4jCoreSupport.ARTIFACT.equals(Log4jCoreSupport.mapValue(map, "name"))) return true;
            if (dynamicCore(argument)) return true;
        }
        return false;
    }

    private static boolean primaryCoordinate(Object value) {
        return value instanceof String coordinate &&
               (coordinate.equals(Log4jCoreSupport.GROUP + ":" + Log4jCoreSupport.ARTIFACT) ||
                coordinate.startsWith(Log4jCoreSupport.GROUP + ":" + Log4jCoreSupport.ARTIFACT + ":"));
    }

    private static boolean dynamicCore(J argument) {
        java.util.List<J> parts;
        if (argument instanceof G.GString string) parts = string.getStrings();
        else if (argument instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        String prefix = Log4jCoreSupport.GROUP + ":" + Log4jCoreSupport.ARTIFACT + ":";
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().map(value -> value.stripLeading().startsWith(prefix)).orElse(false);
    }

    private static boolean mentionsLog4jLiteral(J argument) {
        return argument instanceof J.Literal literal &&
               literal.getValue() instanceof String value &&
               (value.contains("org.apache.logging.log4j") || value.contains("Log4j2Plugins.dat"));
    }

    private static boolean mentionsPackaging(String value) {
        return value.contains("org.apache.logging.log4j") ||
               value.contains("Log4j2Plugins.dat") ||
               value.contains("META-INF/services");
    }

    private static boolean packagingPlugin(Xml.Tag plugin) {
        String artifact = plugin.getChildValue("artifactId").orElse("");
        return artifact.contains("shade") || artifact.contains("bnd") ||
               artifact.contains("bundle") || artifact.contains("native");
    }

    private static boolean projectBuildPlugin(Cursor cursor) {
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag p) || !"plugins".equals(p.getName())) return false;
        Cursor build = plugins.getParentTreeCursor();
        if (!(build.getValue() instanceof Xml.Tag b) || !"build".equals(b.getName())) return false;
        Cursor owner = build.getParentTreeCursor();
        if (owner.getValue() instanceof Xml.Tag project && "project".equals(project.getName()) &&
            owner.getParentTreeCursor().getValue() instanceof Xml.Document) return true;
        if (!(owner.getValue() instanceof Xml.Tag profile) || !"profile".equals(profile.getName())) return false;
        Cursor profiles = owner.getParentTreeCursor();
        if (!(profiles.getValue() instanceof Xml.Tag container) ||
            !"profiles".equals(container.getName())) return false;
        Cursor project = profiles.getParentTreeCursor();
        return project.getValue() instanceof Xml.Tag root && "project".equals(root.getName()) &&
               project.getParentTreeCursor().getValue() instanceof Xml.Document;
    }

    private static boolean topLevelOwner(Cursor cursor, String name) {
        int count = 0;
        String owner = null;
        for (Cursor current = cursor.getParent(); current != null; current = current.getParent()) {
            if (current.getValue() instanceof J.MethodInvocation method) {
                count++;
                owner = method.getSimpleName();
            }
        }
        return count == 1 && name.equals(owner);
    }

    private static Xml.Tag markVersion(Xml.Tag owner, String message) {
        return owner.getChild("version").map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }

    private record PropertyKey(String scope, String name) {
    }

    private record Properties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values) {
    }

    private record Scopes(boolean root, Set<String> profiles) {
        private boolean empty() {
            return !root && profiles.isEmpty();
        }
    }
}
