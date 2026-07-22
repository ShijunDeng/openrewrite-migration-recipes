package com.huawei.clouds.openrewrite.jedis;

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

/** Marks exact build declarations that must be aligned with Jedis 7.2.1. */
public final class FindJedis7BuildRisks extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target"
    );
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public String getDisplayName() {
        return "Find exact Jedis 7 build and platform risks";
    }

    @Override
    public String getDescription() {
        return "Marks exact non-target or centrally managed Jedis declarations, Java levels below 8, explicit " +
               "SLF4J/Commons Pool owners, and Spring Redis platform integration boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedJedisDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    return new MavenRisks(localProperties(document, ctx), hasProjectJedis(document, ctx))
                            .visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return new GroovyRisks(hasGradleJedis(groovy, ctx)).visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return new KotlinRisks(hasGradleJedis(kotlin, ctx)).visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static final class MavenRisks extends XmlIsoVisitor<ExecutionContext> {
        private final Map<String, String> properties;
        private final boolean hasProjectJedis;

        private MavenRisks(Map<String, String> properties, boolean hasProjectJedis) {
            this.properties = properties;
            this.hasProjectJedis = hasProjectJedis;
        }

        @Override
        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
            Xml.Tag visited = super.visitTag(tag, ctx);
            if (hasProjectJedis && UpgradeSelectedJedisDependency.isMavenPropertyDefinition(getCursor(), visited) &&
                JAVA_PROPERTIES.contains(visited.getName()) &&
                visited.getValue().filter(FindJedis7BuildRisks::belowJava8).isPresent()) {
                return mark(visited, "Jedis 7.2.1 requires Java 8 or newer; raise this exact compiler/runtime baseline and verify production, tests, annotation processors, CI, container images, TLS providers, and dependency bytecode");
            }
            if (UpgradeSelectedJedisDependency.isJedisCoordinate(visited)) {
                if (UpgradeSelectedJedisDependency.isPluginDependency(getCursor(), visited)) {
                    return mark(visited, "This plugin-owned Jedis declaration is outside the application runtime classpath; decide its target at the plugin owner and verify plugin execution, isolation, packaging, and generated output");
                }
                if (!UpgradeSelectedJedisDependency.isProjectDependency(getCursor(), visited)) return visited;
                if (!UpgradeSelectedJedisDependency.isSupportedJedisDependency(getCursor(), visited)) {
                    return mark(visited, "This classifier or non-jar Jedis variant is outside deterministic runtime upgrade scope; decide its target artifact/version at the owning classpath and verify variant publication, packaging, fixtures, and runtime linkage");
                }
                String declared = visited.getChildValue("version").orElse("");
                if (declared.isEmpty()) {
                    return mark(visited, "Versionless Jedis is owned by a BOM/parent; upgrade the central Spring/platform owner to a Jedis 7-compatible generation and inspect the resolved graph instead of injecting a leaf override");
                }
                String resolved = resolve(declared);
                if (!UpgradeSelectedJedisDependency.TARGET.equals(resolved)) {
                    return markChild(visited, "version", "Jedis remains on an unselected, ranged, property-managed, patched, or non-target declaration; choose 7.2.1 explicitly or migrate its central owner, then verify dependency convergence and runtime linkage");
                }
                return visited;
            }
            if (!hasProjectJedis || (!UpgradeSelectedJedisDependency.isProjectDependency(getCursor(), visited) &&
                !isProjectParent(getCursor(), visited))) return visited;
            String group = visited.getChildValue("groupId").orElse("");
            String artifact = visited.getChildValue("artifactId").orElse("");
            if ("org.slf4j".equals(group) && "slf4j-api".equals(artifact)) {
                return markChild(visited, "version", "Jedis and the application both expose SLF4J at this boundary; verify the resolved API/provider generation, bindings, bridges, duplicate providers, logging initialization, and dependency convergence without forcing an unrelated application logging upgrade");
            }
            if ("org.apache.commons".equals(group) && "commons-pool2".equals(artifact)) {
                return markChild(visited, "version", "Jedis pools share this explicit Commons Pool owner; align a supported version through the platform and verify eviction, validation, abandoned resources, exhaustion, metrics, JMX, and shutdown under load");
            }
            if (("org.springframework.data".equals(group) && "spring-data-redis".equals(artifact)) ||
                ("org.springframework.boot".equals(group) &&
                 Set.of("spring-boot-dependencies", "spring-boot-starter-data-redis").contains(artifact))) {
                return markChild(visited, "version", "Spring owns the Redis client integration at this boundary; select a Spring Data/Boot generation explicitly compatible with Jedis 7, then verify client selection, pooling, serialization, transactions, Sentinel/Cluster, observability, and native/AOT behavior");
            }
            return visited;
        }

        private String resolve(String version) {
            Matcher matcher = PROPERTY.matcher(version);
            return matcher.matches() ? properties.getOrDefault(matcher.group(1), version) : version;
        }
    }

    private static final class GroovyRisks extends GroovyIsoVisitor<ExecutionContext> {
        private final boolean hasJedis;

        private GroovyRisks(boolean hasJedis) {
            this.hasJedis = hasJedis;
        }

        @Override
        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
            boolean direct = UpgradeSelectedJedisDependency.isDirectGradleDependencyLiteral(getCursor());
            J.Literal visited = super.visitLiteral(literal, ctx);
            return direct ? markCoordinate(visited) : visited;
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
            J.Assignment visited = super.visitAssignment(assignment, ctx);
            return hasJedis ? baselineAssignment(visited, getCursor()) : visited;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
            if (hasJedis && legacyToolchain(visited, getCursor())) return mark(visited,
                    "Jedis 7.2.1 requires Java 8 or newer; raise this exact Gradle toolchain and align compilation, tests, CI, runtime images, TLS providers, and dependency bytecode");
            if (isDynamicJedisInvocation(visited, getCursor())) return mark(visited,
                    "This interpolated Jedis dependency has no provable workbook source version; move it to an explicit 7.2.1 literal or upgrade its version/catalog owner, then inspect the resolved graph and dependency convergence");
            return markMapCoordinate(visited, getCursor());
        }
    }

    private static final class KotlinRisks extends KotlinIsoVisitor<ExecutionContext> {
        private final boolean hasJedis;

        private KotlinRisks(boolean hasJedis) {
            this.hasJedis = hasJedis;
        }

        @Override
        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
            boolean direct = UpgradeSelectedJedisDependency.isDirectGradleDependencyLiteral(getCursor());
            J.Literal visited = super.visitLiteral(literal, ctx);
            return direct ? markCoordinate(visited) : visited;
        }

        @Override
        public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
            J.Assignment visited = super.visitAssignment(assignment, ctx);
            return hasJedis ? baselineAssignment(visited, getCursor()) : visited;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
            if (hasJedis && legacyToolchain(visited, getCursor())) return mark(visited,
                    "Jedis 7.2.1 requires Java 8 or newer; raise this exact Gradle toolchain and align compilation, tests, CI, runtime images, TLS providers, and dependency bytecode");
            return isDynamicJedisInvocation(visited, getCursor()) ? mark(visited,
                    "This interpolated Jedis dependency has no provable workbook source version; move it to an explicit 7.2.1 literal or upgrade its version/catalog owner, then inspect the resolved graph and dependency convergence") : visited;
        }
    }

    private static J.Literal markCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length < 2) return literal;
        String group = parts[0];
        String artifact = parts[1];
        String version = parts.length == 3 ? parts[2] : "";
        if (UpgradeSelectedJedisDependency.GROUP.equals(group) &&
            UpgradeSelectedJedisDependency.ARTIFACT.equals(artifact)) {
            if (parts.length != 3) {
                return mark(literal, "This versionless or classifier-qualified Jedis coordinate is outside deterministic upgrade scope; choose the exact runtime variant and 7.2.1 owner, then verify the resolved artifact, fixtures, packaging, and linkage");
            }
            if (!UpgradeSelectedJedisDependency.TARGET.equals(version)) {
                return mark(literal, "Jedis remains on an unselected, dynamic, variable, ranged, or non-target declaration; choose 7.2.1 explicitly or migrate the catalog/platform owner and verify the resolved graph");
            }
            return literal;
        }
        if ("org.slf4j".equals(group) && "slf4j-api".equals(artifact)) {
            return mark(literal, "Verify the resolved SLF4J API/provider generation, bindings, bridges, duplicate providers, initialization, and convergence at the Jedis logging boundary");
        }
        if ("org.apache.commons".equals(group) && "commons-pool2".equals(artifact)) {
            return mark(literal, "Align the Commons Pool owner used with Jedis and test eviction, validation, exhaustion, abandoned resources, metrics, JMX, and shutdown under load");
        }
        if (("org.springframework.data".equals(group) && "spring-data-redis".equals(artifact)) ||
            ("org.springframework.boot".equals(group) &&
             Set.of("spring-boot-dependencies", "spring-boot-starter-data-redis").contains(artifact))) {
            return mark(literal, "Select a Spring Data/Boot generation compatible with Jedis 7 and verify client selection, pooling, serialization, transactions, topology, observability, and native/AOT behavior");
        }
        return literal;
    }

    private static J.MethodInvocation markMapCoordinate(J.MethodInvocation invocation, Cursor cursor) {
        if (!UpgradeSelectedJedisDependency.isGradleDependencyInvocation(cursor, invocation)) return invocation;
        String group = mapValue(invocation, "group");
        String artifact = mapValue(invocation, "name");
        String version = mapValue(invocation, "version");
        if (UpgradeSelectedJedisDependency.GROUP.equals(group) &&
            UpgradeSelectedJedisDependency.ARTIFACT.equals(artifact)) {
            if (hasMapVariant(invocation)) {
                return mark(invocation, "This classifier, ext, or type-qualified Jedis map is outside deterministic upgrade scope; choose its exact target artifact/version and verify variant publication, fixtures, packaging, and runtime linkage");
            }
            if (!UpgradeSelectedJedisDependency.TARGET.equals(version)) {
                return mark(invocation, "Jedis remains on an unselected, dynamic, variable, ranged, or non-target map declaration; choose 7.2.1 explicitly or migrate the catalog/platform owner and verify the resolved graph");
            }
            return invocation;
        }
        if ("org.slf4j".equals(group) && "slf4j-api".equals(artifact)) {
            return mark(invocation, "Verify the resolved SLF4J API/provider generation, bindings, bridges, duplicate providers, initialization, and convergence at the Jedis logging boundary");
        }
        if ("org.apache.commons".equals(group) && "commons-pool2".equals(artifact)) {
            return mark(invocation, "Align the Commons Pool owner used with Jedis and test eviction, validation, exhaustion, abandoned resources, metrics, JMX, and shutdown under load");
        }
        if (("org.springframework.data".equals(group) && "spring-data-redis".equals(artifact)) ||
            ("org.springframework.boot".equals(group) &&
             Set.of("spring-boot-dependencies", "spring-boot-starter-data-redis").contains(artifact))) {
            return mark(invocation, "Select a Spring Data/Boot generation compatible with Jedis 7 and verify client selection, pooling, serialization, transactions, topology, observability, and native/AOT behavior");
        }
        return invocation;
    }

    private static String mapValue(J.MethodInvocation invocation, String key) {
        String direct = invocation.getArguments().stream().filter(G.MapEntry.class::isInstance).map(G.MapEntry.class::cast)
                .filter(entry -> key.equals(mapKey(entry))).map(G.MapEntry::getValue)
                .filter(J.Literal.class::isInstance).map(J.Literal.class::cast).map(J.Literal::getValue)
                .filter(String.class::isInstance).map(String.class::cast).findFirst().orElse(null);
        if (direct != null) return direct;
        return invocation.getArguments().stream().filter(G.MapLiteral.class::isInstance).map(G.MapLiteral.class::cast)
                .flatMap(map -> map.getElements().stream()).filter(entry -> key.equals(mapKey(entry)))
                .map(G.MapEntry::getValue).filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .findFirst().orElse(null);
    }

    private static boolean hasMapVariant(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().anyMatch(argument -> {
            if (argument instanceof G.MapEntry entry) {
                return Set.of("classifier", "ext", "type").contains(mapKey(entry));
            }
            return argument instanceof G.MapLiteral map && map.getElements().stream()
                    .anyMatch(entry -> Set.of("classifier", "ext", "type").contains(mapKey(entry)));
        });
    }

    private static boolean isDynamicJedisInvocation(J.MethodInvocation invocation, Cursor cursor) {
        if (!UpgradeSelectedJedisDependency.isGradleDependencyInvocation(cursor, invocation)) return false;
        return invocation.getArguments().stream().anyMatch(argument ->
                (argument instanceof G.GString || argument instanceof K.StringTemplate) &&
                argument.printTrimmed(cursor).contains(UpgradeSelectedJedisDependency.GROUP + ":" +
                                                       UpgradeSelectedJedisDependency.ARTIFACT + ":"));
    }

    private static boolean methodDeclaresJedis(J.MethodInvocation invocation, Cursor cursor) {
        if (!UpgradeSelectedJedisDependency.isGradleDependencyInvocation(cursor, invocation)) return false;
        if (UpgradeSelectedJedisDependency.GROUP.equals(mapValue(invocation, "group")) &&
            UpgradeSelectedJedisDependency.ARTIFACT.equals(mapValue(invocation, "name"))) return true;
        return invocation.getArguments().stream().anyMatch(argument -> {
            if (argument instanceof J.Literal literal && literal.getValue() instanceof String value) {
                return value.equals(UpgradeSelectedJedisDependency.GROUP + ":" +
                                    UpgradeSelectedJedisDependency.ARTIFACT) ||
                       value.startsWith(UpgradeSelectedJedisDependency.GROUP + ":" +
                                        UpgradeSelectedJedisDependency.ARTIFACT + ":");
            }
            return (argument instanceof G.GString || argument instanceof K.StringTemplate) &&
                   argument.printTrimmed(cursor).contains(UpgradeSelectedJedisDependency.GROUP + ":" +
                                                          UpgradeSelectedJedisDependency.ARTIFACT + ":");
        });
    }

    private static boolean hasGradleJedis(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (methodDeclaresJedis(visited, getCursor())) found[0] = true;
                return visited;
            }
        }.visit(compilationUnit, ctx);
        return found[0];
    }

    private static boolean hasGradleJedis(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, executionContext);
                if (methodDeclaresJedis(visited, getCursor())) found[0] = true;
                return visited;
            }
        }.visit(compilationUnit, ctx);
        return found[0];
    }

    private static String mapKey(G.MapEntry entry) {
        if (entry.getKey() instanceof J.Literal literal && literal.getValue() instanceof String key) return key;
        return entry.getKey() instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    private static J.Assignment baselineAssignment(J.Assignment assignment, Cursor cursor) {
        String variable = assignment.getVariable().printTrimmed(cursor);
        if (!variable.endsWith("sourceCompatibility") && !variable.endsWith("targetCompatibility")) return assignment;
        return belowJava8(assignment.getAssignment().printTrimmed(cursor))
                ? mark(assignment, "Jedis 7.2.1 requires Java 8 or newer; raise this exact Gradle baseline and align compilation, tests, CI, runtime images, TLS providers, and dependency bytecode")
                : assignment;
    }

    private static boolean legacyToolchain(J.MethodInvocation method, Cursor cursor) {
        if (!"of".equals(method.getSimpleName()) || method.getArguments().size() != 1 ||
            !(method.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof Number number) || number.intValue() >= 8 ||
            method.getSelect() == null) return false;
        return method.getSelect().printTrimmed(cursor).endsWith("JavaLanguageVersion");
    }

    private static Xml.Tag markChild(Xml.Tag owner, String childName, String message) {
        return owner.getChild(childName).map(child -> {
            Xml.Tag marked = mark(child, message);
            if (marked == child) return owner;
            return owner.withContent(owner.getContent().stream()
                    .map(content -> content == child ? marked : content).toList());
        }).orElseGet(() -> mark(owner, message));
    }

    private static Map<String, String> localProperties(Xml.Document document, ExecutionContext ctx) {
        Map<String, String> properties = new HashMap<>();
        Map<String, Integer> definitions = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (UpgradeSelectedJedisDependency.isMavenPropertyDefinition(getCursor(), tag)) {
                    definitions.merge(tag.getName(), 1, Integer::sum);
                    tag.getValue().ifPresent(value -> properties.put(tag.getName(), value.trim()));
                }
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        definitions.entrySet().stream().filter(entry -> entry.getValue() != 1)
                .map(Map.Entry::getKey).toList().forEach(properties::remove);
        return properties;
    }

    private static boolean hasProjectJedis(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (UpgradeSelectedJedisDependency.isJedisCoordinate(tag) &&
                    UpgradeSelectedJedisDependency.isProjectDependency(getCursor(), tag)) found[0] = true;
                return super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return found[0];
    }

    private static boolean isProjectParent(Cursor cursor, Xml.Tag tag) {
        if (!"parent".equals(tag.getName())) return false;
        Cursor owner = cursor.getParentTreeCursor();
        return owner != null && UpgradeSelectedJedisDependency.isProjectOwner(owner);
    }

    private static boolean belowJava8(String value) {
        Matcher matcher = Pattern.compile("(?:1\\.)?(\\d+)").matcher(value);
        if (!matcher.find()) return false;
        try {
            return Integer.parseInt(matcher.group(1)) < 8;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static <T extends Tree> T mark(T tree, String message) {
        boolean present = tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()));
        return present ? tree : SearchResult.found(tree, message);
    }
}
