package com.huawei.clouds.openrewrite.feignhttpclient;

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

/** Find build declarations that require a version owner, variant, or classpath decision. */
public final class FindFeignHttpClient13BuildRisks extends Recipe {
    private static final Pattern FIXED = Pattern.compile("[0-9]+(?:\\.[0-9]+)*(?:[-.][A-Za-z0-9]+)*");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> JAVA_KEYS = Set.of(
            "maven.compiler.release", "maven.compiler.source", "maven.compiler.target", "java.version");
    private static final String OWNER =
            "This Feign Apache HttpClient version is absent, variable, ranged, dynamic, catalog/platform/BOM-managed, or outside " +
            "the workbook selection; migrate the actual owner deliberately and verify that 13.6 resolves";
    private static final String OUTSIDE =
            "This fixed Feign Apache HttpClient version is outside the workbook source set and target; it is intentionally not " +
            "auto-upgraded, so choose its migration path explicitly";
    private static final String VARIANT =
            "This classified or non-JAR Feign Apache HttpClient artifact is outside deterministic scope; verify that 13.6 " +
            "publishes the required artifact shape before changing it";
    private static final String FEIGN_ALIGNMENT =
            "This directly declared Feign companion is not aligned to 13.6; verify dependency mediation and converge " +
            "all Feign API/runtime modules to a binary-compatible, actually published version";
    private static final String HC4_ALIGNMENT =
            "Feign Apache HttpClient 13.6 remains an HC4 adapter tested with httpclient 4.5.14/httpcore 4.4.16; this " +
            "explicit module may override that line, so converge HC4 deliberately and rerun transport/security tests";
    private static final String HC5_MIX =
            "feign-httpclient is the HC4 adapter and cannot consume Apache HC5 types; do not mix it with feign-hc5 or " +
            "org.apache.httpcomponents.client5/core5 accidentally—choose one transport and migrate its wiring explicitly";
    private static final String CODEC_ALIGNMENT =
            "Feign Apache HttpClient 13.6 manages commons-codec 1.18.0; this direct declaration may override that line, " +
            "so verify authentication, URI encoding, dependency convergence, and security fixes";
    private static final String JAVA =
            "Feign Apache HttpClient 13.6 requires Java 8 or newer; update this explicit compiler baseline before resolving it";

    @Override
    public String getDisplayName() {
        return "Find Feign Apache HttpClient 13.6 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact owned Maven and root Gradle nodes with unresolved Feign Apache HttpClient versions, nonstandard " +
               "variants, Feign-family/HC4/HC5/commons-codec alignment risks, or a pre-Java-8 baseline.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedFeignHttpClientDependency.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(fileName)) {
                    if (!containsTargetDependency(document, ctx)) return tree;
                    ScopedProperties properties = scopedProperties(document, ctx);
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag t = super.visitTag(tag, ec);
                            if (UpgradeSelectedFeignHttpClientDependency.isMavenPropertyDefinition(getCursor(), t) &&
                                JAVA_KEYS.contains(t.getName()) && t.getValue().map(String::trim)
                                        .filter(FindFeignHttpClient13BuildRisks::preJava8).isPresent()) {
                                return mark(t, JAVA);
                            }
                            if (!UpgradeSelectedFeignHttpClientDependency.isProjectDependency(getCursor(), t)) return t;
                            String group = t.getChildValue("groupId").orElse("");
                            String artifact = t.getChildValue("artifactId").orElse("");
                            String version = t.getChildValue("version").map(String::trim).orElse("");
                            if (UpgradeSelectedFeignHttpClientDependency.GROUP.equals(group) &&
                                artifact.startsWith("feign-") &&
                                !UpgradeSelectedFeignHttpClientDependency.ARTIFACT.equals(artifact)) {
                                if ("feign-hc5".equals(artifact)) return markVersionOrOwner(t, HC5_MIX);
                                return resolvesTo(version, getCursor(), properties,
                                        UpgradeSelectedFeignHttpClientDependency.TARGET) ? t :
                                        markVersionOrOwner(t, FEIGN_ALIGNMENT);
                            }
                            if (hc5Module(group)) return markVersionOrOwner(t, HC5_MIX);
                            String expectedTransport = expectedTransportVersion(group, artifact);
                            if (expectedTransport != null) {
                                String message = "commons-codec".equals(artifact) ? CODEC_ALIGNMENT : HC4_ALIGNMENT;
                                return resolvesTo(version, getCursor(), properties, expectedTransport) ? t :
                                        markVersionOrOwner(t, message);
                            }
                            if (!UpgradeSelectedFeignHttpClientDependency.GROUP.equals(group) ||
                                !UpgradeSelectedFeignHttpClientDependency.ARTIFACT.equals(artifact)) return t;
                            if (t.getChild("classifier").isPresent() ||
                                !"jar".equals(t.getChildValue("type").orElse("jar"))) return mark(t, VARIANT);
                            if (resolvesTo(version, getCursor(), properties,
                                    UpgradeSelectedFeignHttpClientDependency.TARGET)) return t;
                            if (!FIXED.matcher(version).matches()) return markVersionOrOwner(t, OWNER);
                            return UpgradeSelectedFeignHttpClientDependency.TARGET.equals(version) ? t :
                                    markVersionOrOwner(t, OUTSIDE);
                        }
                    }.visitNonNull(document, ctx);
                }
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    if (!containsTargetDependency(groovy, ctx)) return tree;
                    return new GroovyIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignHttpClientDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            if (!dependency) return m;
                            m = markDynamicTemplateArgument(m);
                            String message = mapMessage(m);
                            if (message == null) {
                                G.MapLiteral map = m.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                                message = map == null ? null : mapMessage(map);
                            }
                            return message == null ? m : mark(m, message);
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignHttpClientDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue()) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    if (!containsTargetDependency(kotlin, ctx)) return tree;
                    return new KotlinIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignHttpClientDependency
                                    .isGradleDependencyInvocation(getCursor(), method);
                            J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                            return dependency ? markDynamicTemplateArgument(m) : m;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                            boolean dependency = UpgradeSelectedFeignHttpClientDependency.isDirectDependencyLiteral(getCursor());
                            J.Literal l = super.visitLiteral(literal, ec);
                            String message = dependency ? coordinateMessage(l.getValue()) : null;
                            return message == null ? l : mark(l, message);
                        }
                    }.visitNonNull(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static String coordinateMessage(Object literal) {
        if (!(literal instanceof String value)) return null;
        String[] parts = value.split(":", -1);
        if (parts.length < 2) return null;
        if (UpgradeSelectedFeignHttpClientDependency.GROUP.equals(parts[0]) && parts[1].startsWith("feign-") &&
            !UpgradeSelectedFeignHttpClientDependency.ARTIFACT.equals(parts[1])) {
            if ("feign-hc5".equals(parts[1])) return HC5_MIX;
            return parts.length == 3 && UpgradeSelectedFeignHttpClientDependency.TARGET.equals(parts[2])
                    ? null : FEIGN_ALIGNMENT;
        }
        if (hc5Module(parts[0])) return HC5_MIX;
        String expectedTransport = expectedTransportVersion(parts[0], parts[1]);
        if (expectedTransport != null) {
            String message = "commons-codec".equals(parts[1]) ? CODEC_ALIGNMENT : HC4_ALIGNMENT;
            return parts.length == 3 && expectedTransport.equals(parts[2]) ? null : message;
        }
        if (!UpgradeSelectedFeignHttpClientDependency.GROUP.equals(parts[0]) ||
            !UpgradeSelectedFeignHttpClientDependency.ARTIFACT.equals(parts[1])) return null;
        if (parts.length > 3 || parts.length == 3 && parts[2].contains("@")) return VARIANT;
        if (parts.length != 3 || !FIXED.matcher(parts[2]).matches()) return OWNER;
        return UpgradeSelectedFeignHttpClientDependency.TARGET.equals(parts[2]) ? null : OUTSIDE;
    }

    private static boolean containsTargetDependency(Xml.Document document, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (UpgradeSelectedFeignHttpClientDependency.isProjectDependency(getCursor(), visited) &&
                    UpgradeSelectedFeignHttpClientDependency.GROUP.equals(
                            visited.getChildValue("groupId").orElse(null)) &&
                    UpgradeSelectedFeignHttpClientDependency.ARTIFACT.equals(
                            visited.getChildValue("artifactId").orElse(null))) found[0] = true;
                return visited;
            }
        }.visitNonNull(document, ctx);
        return found[0];
    }

    private static boolean containsTargetDependency(G.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedFeignHttpClientDependency
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && targetInvocation(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(compilationUnit, ctx);
        return found[0];
    }

    private static boolean containsTargetDependency(K.CompilationUnit compilationUnit, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = UpgradeSelectedFeignHttpClientDependency
                        .isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && targetInvocation(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(compilationUnit, ctx);
        return found[0];
    }

    private static boolean targetInvocation(J.MethodInvocation invocation) {
        if (UpgradeSelectedFeignHttpClientDependency.GROUP.equals(
                    UpgradeSelectedFeignHttpClientDependency.mapValue(invocation, "group")) &&
            UpgradeSelectedFeignHttpClientDependency.ARTIFACT.equals(
                    UpgradeSelectedFeignHttpClientDependency.mapValue(invocation, "name"))) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal && targetCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                UpgradeSelectedFeignHttpClientDependency.GROUP.equals(
                        UpgradeSelectedFeignHttpClientDependency.mapValue(map, "group")) &&
                UpgradeSelectedFeignHttpClientDependency.ARTIFACT.equals(
                        UpgradeSelectedFeignHttpClientDependency.mapValue(map, "name"))) return true;
            if (templateMentionsTarget(argument)) return true;
        }
        return false;
    }

    private static boolean targetCoordinate(Object value) {
        if (!(value instanceof String coordinate)) return false;
        String prefix = UpgradeSelectedFeignHttpClientDependency.GROUP + ":" +
                        UpgradeSelectedFeignHttpClientDependency.ARTIFACT;
        return prefix.equals(coordinate) || coordinate.startsWith(prefix + ":");
    }

    private static String mapMessage(J.MethodInvocation invocation) {
        return dependencyMessage(UpgradeSelectedFeignHttpClientDependency.mapValue(invocation, "group"),
                UpgradeSelectedFeignHttpClientDependency.mapValue(invocation, "name"),
                UpgradeSelectedFeignHttpClientDependency.mapValue(invocation, "version"),
                UpgradeSelectedFeignHttpClientDependency.hasVariant(invocation));
    }

    private static String mapMessage(G.MapLiteral map) {
        return dependencyMessage(UpgradeSelectedFeignHttpClientDependency.mapValue(map, "group"),
                UpgradeSelectedFeignHttpClientDependency.mapValue(map, "name"),
                UpgradeSelectedFeignHttpClientDependency.mapValue(map, "version"),
                UpgradeSelectedFeignHttpClientDependency.hasVariant(map));
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (UpgradeSelectedFeignHttpClientDependency.GROUP.equals(group) && artifact != null &&
            artifact.startsWith("feign-") && !UpgradeSelectedFeignHttpClientDependency.ARTIFACT.equals(artifact)) {
            if ("feign-hc5".equals(artifact)) return HC5_MIX;
            return UpgradeSelectedFeignHttpClientDependency.TARGET.equals(version) ? null : FEIGN_ALIGNMENT;
        }
        if (hc5Module(group)) return HC5_MIX;
        String expectedTransport = expectedTransportVersion(group, artifact);
        if (expectedTransport != null) {
            String message = "commons-codec".equals(artifact) ? CODEC_ALIGNMENT : HC4_ALIGNMENT;
            return expectedTransport.equals(version) ? null : message;
        }
        if (!UpgradeSelectedFeignHttpClientDependency.GROUP.equals(group) ||
            !UpgradeSelectedFeignHttpClientDependency.ARTIFACT.equals(artifact)) return null;
        if (variant) return VARIANT;
        if (version == null || !FIXED.matcher(version).matches()) return OWNER;
        return UpgradeSelectedFeignHttpClientDependency.TARGET.equals(version) ? null : OUTSIDE;
    }

    private static boolean hc5Module(String group) {
        return group != null && (group.equals("org.apache.httpcomponents.client5") ||
                                 group.equals("org.apache.httpcomponents.core5"));
    }

    private static String expectedTransportVersion(String group, String artifact) {
        if ("commons-codec".equals(group) && "commons-codec".equals(artifact)) return "1.18.0";
        if (!"org.apache.httpcomponents".equals(group)) return null;
        if (Set.of("httpclient", "httpmime", "fluent-hc").contains(artifact)) return "4.5.14";
        return "httpcore".equals(artifact) ? "4.4.16" : null;
    }

    private static boolean preJava8(String value) {
        return value.matches("1\\.[0-7]") || value.matches("[1-7]");
    }

    private static ScopedProperties scopedProperties(Xml.Document document, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (UpgradeSelectedFeignHttpClientDependency.isMavenPropertyDefinition(getCursor(), t)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), t.getName());
                    counts.merge(key, 1, Integer::sum);
                    t.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return new ScopedProperties(counts, values);
    }

    private static boolean resolvesTo(String version, org.openrewrite.Cursor cursor,
                                      ScopedProperties properties, String expected) {
        if (expected.equals(version)) return true;
        Matcher matcher = PROPERTY.matcher(version);
        if (!matcher.matches()) return false;
        String profile = scope(cursor);
        PropertyKey local = new PropertyKey(profile, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(profile) && properties.counts().containsKey(local) ? local : root;
        return properties.counts().getOrDefault(owner, 0) == 1 &&
               expected.equals(properties.values().get(owner));
    }

    private static String scope(org.openrewrite.Cursor cursor) {
        for (org.openrewrite.Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "profile".equals(tag.getName())) {
                return tag.getId().toString();
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return "ROOT";
    }

    private record PropertyKey(String scope, String name) {
    }

    private record ScopedProperties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values) {
    }

    private static J.MethodInvocation markDynamicTemplateArgument(J.MethodInvocation invocation) {
        return invocation.withArguments(invocation.getArguments().stream().map(argument ->
                templateMentionsTarget(argument) ? mark(argument, OWNER) : argument).toList());
    }

    private static boolean templateMentionsTarget(J argument) {
        java.util.List<J> parts;
        if (argument instanceof G.GString string) parts = string.getStrings();
        else if (argument instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> value.contains(UpgradeSelectedFeignHttpClientDependency.GROUP + ":" +
                                                  UpgradeSelectedFeignHttpClientDependency.ARTIFACT + ":"));
    }

    private static Xml.Tag markVersionOrOwner(Xml.Tag owner, String message) {
        return owner.getChild("version").map(child -> {
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
