package com.huawei.clouds.openrewrite.log4joverslf4j;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark build graph states that cannot be safely resolved by a low-level version replacement. */
public final class FindLog4jOverSlf4jBuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)+(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> SLF4J_PROVIDERS = Set.of(
            "slf4j-simple", "slf4j-nop", "slf4j-jdk14", "slf4j-reload4j", "slf4j-log4j12"
    );
    private static final Set<String> SLF4J_COMPANIONS = Set.of(
            "slf4j-api", "slf4j-bom", "jcl-over-slf4j", "jul-to-slf4j", "slf4j-jdk-platform-logging"
    );
    private static final String EXTERNAL =
            "This log4j-over-slf4j version is versionless, property/BOM/catalog-managed, ranged, dynamic, or otherwise externally owned; migrate the actual owner and verify the resolved bridge and SLF4J API/provider graph";
    private static final String OUTSIDE =
            "This fixed log4j-over-slf4j version is outside the workbook source selection; choose it deliberately rather than widening the automatic 1.7.30/1.7.32/1.7.36 migration";
    private static final String VARIANT =
            "This classified or non-JAR log4j-over-slf4j artifact is outside deterministic runtime scope; verify that 2.0.17 publishes the same shape and test module/classloader packaging";
    private static final String RECURSION =
            "log4j-over-slf4j must not coexist with slf4j-reload4j/slf4j-log4j12 or a Log4j 1/reload4j runtime: Log4j calls bridge to SLF4J and SLF4J routes back to Log4j, causing an endless recursion; select a non-Log4j-1 provider and remove duplicate org.apache.log4j classes";
    private static final String LEGACY_PROVIDER =
            "SLF4J 2 ignores 1.7 StaticLoggerBinder bindings; upgrade the owning provider to its SLF4J 2 ServiceLoader generation, align the 2.0 major/minor family, and verify exactly one provider at runtime";
    private static final String PROVIDER =
            "SLF4J 2 selects providers through ServiceLoader; verify this is the only runtime provider, matches slf4j-api 2.0.x, has a visible META-INF/services descriptor, and is not exported transitively by a library";
    private static final String FAMILY =
            "This SLF4J companion is not in the 2.0 family; align slf4j-api, bridges, and the chosen provider, then verify dependency convergence and startup diagnostics";
    private static final String FAMILY_EXTERNAL =
            "This SLF4J companion/provider version is versionless, property/BOM-managed, ranged, or otherwise unresolved in this POM; migrate the actual owner, align the 2.0 family, and verify dependency convergence and startup diagnostics";
    private static final String JAVA =
            "SLF4J 2.0 requires Java 8 or newer; raise the owning compiler/toolchain and verify every runtime, CI image, test launcher, plugin host, and packaged deployment";

    @Override
    public String getDisplayName() {
        return "Find log4j-over-slf4j 2.0 build risks";
    }

    @Override
    public String getDescription() {
        return "Mark recursive Log4j bridges, legacy/multiple-provider boundaries, non-2.0 SLF4J companions, " +
               "external/managed/variant versions, and Maven Java levels below 8.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedLog4jOverSlf4jDependency.excluded(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return markPom(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedLog4jOverSlf4jDependency.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                            if (!direct) return visited;
                            if (catalogAlias(visited, getCursor())) return mark(visited, EXTERNAL);
                            String platformMessage = platformMessage(visited);
                            if (platformMessage != null) return mark(visited, platformMessage);
                            String group = mapValue(visited, "group");
                            String artifact = mapValue(visited, "name");
                            String version = mapValue(visited, "version");
                            String message = mapMessage(group, artifact, version,
                                    UpgradeSelectedLog4jOverSlf4jDependency.hasVariant(visited));
                            if (message == null) {
                                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                if (map != null) message = mapMessage(mapValue(map, "group"), mapValue(map, "name"),
                                        mapValue(map, "version"), UpgradeSelectedLog4jOverSlf4jDependency.hasVariant(map));
                            }
                            return message == null ? visited : mark(visited, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedLog4jOverSlf4jDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, ec);
                            return direct ? markCoordinate(visited) : visited;
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedLog4jOverSlf4jDependency.isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                            if (!direct) return visited;
                            if (catalogAlias(visited, getCursor())) return mark(visited, EXTERNAL);
                            String platformMessage = platformMessage(visited);
                            return platformMessage == null ? visited : mark(visited, platformMessage);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean direct = UpgradeSelectedLog4jOverSlf4jDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal visited = super.visitLiteral(literal, ec);
                            return direct ? markCoordinate(visited) : visited;
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document markPom(Xml.Document document, ExecutionContext ctx) {
        Map<String, Integer> definitions = new HashMap<>();
        Map<String, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedLog4jOverSlf4jDependency.isPropertyDefinition(getCursor(), visited)) {
                    definitions.merge(visited.getName(), 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(visited.getName(), value.trim()));
                }
                return visited;
            }
        }.visitNonNull(document, ctx);

        Map<String, Map<String, String>> management = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!isDependencyManagementEntry(getCursor()) ||
                    !UpgradeSelectedLog4jOverSlf4jDependency.isOwnedDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String scope = dependencyScope(getCursor());
                if (scope == null || group.isEmpty() || artifact.isEmpty()) return visited;
                String declared = visited.getChildValue("version").map(String::trim).orElse("");
                String visible = resolveVisibleVersion(declared, definitions, values);
                String candidate = visible == null ? "<unknown>" : visible;
                management.computeIfAbsent(group + ":" + artifact, ignored -> new HashMap<>())
                        .merge(scope, candidate, (left, right) -> left.equals(right) ? left : "<unknown>");
                return visited;
            }
        }.visitNonNull(document, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (oldJavaProperty(getCursor(), visited)) return mark(visited, JAVA);
                if (!UpgradeSelectedLog4jOverSlf4jDependency.isOwnedDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").orElse("");
                String artifact = visited.getChildValue("artifactId").orElse("");
                String declared = visited.getChildValue("version").map(String::trim).orElse("");
                if (isBridge(group, artifact) && (visited.getChild("classifier").isPresent() ||
                    !"jar".equals(visited.getChildValue("type").orElse("jar")))) return mark(visited, VARIANT);
                String visible = resolveVisibleVersion(declared, definitions, values);
                if (visible == null && declared.isEmpty() && !isDependencyManagementEntry(getCursor())) {
                    visible = managedVersion(getCursor(), group + ":" + artifact, management);
                }
                String message = coordinateMessage(group, artifact, visible);
                if (message == null && isBridge(group, artifact) && visible == null) message = EXTERNAL;
                if (message == null && isSlf4jFamily(group, artifact) && visible == null) message = FAMILY_EXTERNAL;
                if (message == null) return visited;
                return visited.getChild("version").isPresent()
                        ? markChild(visited, "version", message) : mark(visited, message);
            }
        }.visitNonNull(document, ctx);
    }

    private static String resolveVisibleVersion(String declared, Map<String, Integer> definitions,
                                                Map<String, String> values) {
        if (FIXED.matcher(declared).matches()) return declared;
        Matcher matcher = PROPERTY_REFERENCE.matcher(declared);
        return matcher.matches() && definitions.getOrDefault(matcher.group(1), 0) == 1
                ? values.get(matcher.group(1)) : null;
    }

    private static boolean isDependencyManagementEntry(Cursor cursor) {
        Cursor dependencies = cursor.getParentTreeCursor();
        Cursor owner = dependencies == null ? null : dependencies.getParentTreeCursor();
        return dependencies != null && dependencies.getValue() instanceof Xml.Tag dependenciesTag &&
               "dependencies".equals(dependenciesTag.getName()) && owner != null &&
               owner.getValue() instanceof Xml.Tag ownerTag && "dependencyManagement".equals(ownerTag.getName());
    }

    private static String dependencyScope(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (!(current.getValue() instanceof Xml.Tag tag)) continue;
            if ("profile".equals(tag.getName())) return tag.getId().toString();
            if ("project".equals(tag.getName())) return "<root>";
        }
        return null;
    }

    private static String managedVersion(Cursor cursor, String coordinate,
                                         Map<String, Map<String, String>> management) {
        Map<String, String> scopes = management.get(coordinate);
        if (scopes == null) return null;
        String scope = dependencyScope(cursor);
        String candidate = scope != null && !"<root>".equals(scope) && scopes.containsKey(scope)
                ? scopes.get(scope) : scopes.get("<root>");
        return candidate == null || "<unknown>".equals(candidate) ? null : candidate;
    }

    private static String mapMessage(String group, String artifact, String version, boolean variant) {
        if (isBridge(group, artifact)) {
            if (variant) return VARIANT;
            if (version == null || !FIXED.matcher(version).matches()) return EXTERNAL;
        }
        return coordinateMessage(group, artifact, version);
    }

    private static boolean catalogAlias(J.MethodInvocation invocation, Cursor cursor) {
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Empty || argument instanceof J.Literal || argument instanceof G.MapEntry ||
                argument instanceof G.MapLiteral) continue;
            String printed = argument.printTrimmed(cursor).replace("`", "");
            if (printed.matches("libs\\.(?:log4j\\.over\\.slf4j|log4jOverSlf4j|log4jOverSlf4jLibrary)")) return true;
        }
        return false;
    }

    private static String platformMessage(J.MethodInvocation invocation) {
        for (J argument : invocation.getArguments()) {
            if (!(argument instanceof J.MethodInvocation platform) ||
                !("platform".equals(platform.getSimpleName()) || "enforcedPlatform".equals(platform.getSimpleName())) ||
                platform.getArguments().size() != 1 || !(platform.getArguments().get(0) instanceof J.Literal literal) ||
                !(literal.getValue() instanceof String coordinate)) continue;
            String[] parts = coordinate.split(":", -1);
            if (parts.length == 3 && UpgradeSelectedLog4jOverSlf4jDependency.GROUP.equals(parts[0]) &&
                "slf4j-bom".equals(parts[1])) {
                return parts[2].startsWith("2.0.") ? null : FAMILY;
            }
        }
        return null;
    }

    private static String coordinateMessage(String group, String artifact, String version) {
        if (isRecursive(group, artifact)) return RECURSION;
        if (isBridge(group, artifact)) {
            return UpgradeSelectedLog4jOverSlf4jDependency.TARGET.equals(version) ? null : OUTSIDE;
        }
        if ("org.apache.logging.log4j".equals(group) && "log4j-slf4j-impl".equals(artifact)) return LEGACY_PROVIDER;
        if ("org.apache.logging.log4j".equals(group) && "log4j-slf4j2-impl".equals(artifact)) return PROVIDER;
        if ("ch.qos.logback".equals(group) && "logback-classic".equals(artifact)) {
            return version != null && version.startsWith("1.2.") ? LEGACY_PROVIDER : PROVIDER;
        }
        if (UpgradeSelectedLog4jOverSlf4jDependency.GROUP.equals(group) && SLF4J_PROVIDERS.contains(artifact)) {
            if ("slf4j-reload4j".equals(artifact) || "slf4j-log4j12".equals(artifact)) return RECURSION;
            return version != null && version.startsWith("2.0.") ? PROVIDER : LEGACY_PROVIDER;
        }
        if (UpgradeSelectedLog4jOverSlf4jDependency.GROUP.equals(group) && SLF4J_COMPANIONS.contains(artifact) &&
            version != null && FIXED.matcher(version).matches() && !version.startsWith("2.0.")) return FAMILY;
        return null;
    }

    private static boolean isSlf4jFamily(String group, String artifact) {
        return UpgradeSelectedLog4jOverSlf4jDependency.GROUP.equals(group) &&
               (SLF4J_PROVIDERS.contains(artifact) || SLF4J_COMPANIONS.contains(artifact));
    }

    private static boolean isBridge(String group, String artifact) {
        return UpgradeSelectedLog4jOverSlf4jDependency.GROUP.equals(group) &&
               UpgradeSelectedLog4jOverSlf4jDependency.ARTIFACT.equals(artifact);
    }

    private static boolean isRecursive(String group, String artifact) {
        return UpgradeSelectedLog4jOverSlf4jDependency.GROUP.equals(group) &&
               ("slf4j-reload4j".equals(artifact) || "slf4j-log4j12".equals(artifact)) ||
               "log4j".equals(group) && "log4j".equals(artifact) ||
               "ch.qos.reload4j".equals(group) && "reload4j".equals(artifact);
    }

    private static boolean oldJavaProperty(Cursor cursor, Xml.Tag tag) {
        if (!Set.of("maven.compiler.source", "maven.compiler.target", "maven.compiler.release").contains(tag.getName())) {
            return false;
        }
        if (!UpgradeSelectedLog4jOverSlf4jDependency.isPropertyDefinition(cursor, tag)) return false;
        try {
            String raw = tag.getValue().orElse("").trim();
            double parsed = Double.parseDouble(raw);
            return raw.startsWith("1.") ? parsed < 1.8 : parsed < 8;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String coordinate)) return literal;
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 2) return literal;
        if (isBridge(parts[0], parts[1])) {
            if (parts.length > 3) return mark(literal, VARIANT);
            if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return mark(literal, EXTERNAL);
        }
        if (parts.length != 3) return literal;
        String message = coordinateMessage(parts[0], parts[1], parts[2]);
        return message == null ? literal : mark(literal, message);
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        return invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(UpgradeSelectedLog4jOverSlf4jDependency.mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static String mapValue(G.MapLiteral map, String key) {
        return map.getElements().stream().filter(entry -> key.equals(UpgradeSelectedLog4jOverSlf4jDependency.mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static Xml.Tag markChild(Xml.Tag owner, String childName, String message) {
        return owner.getChild(childName).map(child -> {
            Xml.Tag marked = mark(child, message);
            return marked == child ? owner : owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
