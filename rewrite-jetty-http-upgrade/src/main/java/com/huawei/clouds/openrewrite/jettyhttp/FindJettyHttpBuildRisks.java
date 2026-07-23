package com.huawei.clouds.openrewrite.jettyhttp;

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
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Locate Jetty dependency ownership, branch, Java, alignment and packaging decisions. */
public final class FindJettyHttpBuildRisks extends Recipe {
    private static final Pattern PROPERTY = Pattern.compile("^\\$\\{([^}]+)}$");
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern JAVA_NUMBER = Pattern.compile("^(?:1\\.)?(\\d+)(?:\\..*)?$");
    static final String OWNER =
            "Jetty HTTP 版本由缺失版本、父 POM、Jetty BOM/platform、共享属性、version catalog、动态表达式或插件控制；" +
            "请修改真实 owner，并确认最终解析为 12.0.34";
    static final String OUTSIDE =
            "该固定 Jetty HTTP 版本不在十个 AUTO 源版本、目标 12.0.34 或禁止降级版本中；" +
            "没有经审计的迁移路径，配方保持原值";
    static final String VARIANT =
            "该 Jetty HTTP 声明含 classifier、非 JAR type 或 Gradle variant；请验证制品形状、OSGi/JPMS 元数据和运行时类路径";
    static final String JAVA_BASELINE =
            "Jetty 12.0 要求 Java 17；请统一 Maven/Gradle 编译、测试、运行时、容器、jlink/native-image 和 CI 基线";
    static final String FAMILY_ALIGNMENT =
            "Jetty 家族依赖没有与 jetty-http 12.0.34 对齐；请使用同一 Jetty BOM/版本线，并验证 server、io、util、client、HTTP2/3 和 ALPN 的收敛";
    static final String EE_LINEAGE =
            "Jetty 9/11 的 servlet/webapp/websocket artifact 在 Jetty 12 按 ee8/ee9/ee10 重组；" +
            "选择目标 EE 环境后迁移整个部署栈，不能把官方宽聚合无条件塞进单一 jetty-http 升级";
    static final String PACKAGING =
            "Jetty 被 exclusions、shade/relocation、assembly、OSGi fragment 或自定义 module-path 打包；" +
            "请验证服务加载、Automatic-Module-Name、重复类、签名和最终运行时版本";

    @Override
    public String getDisplayName() {
        return "Find Jetty HTTP 12.0.34 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact no-downgrade conflicts, unsupported versions, unresolved owners, variants, Java 17, " +
               "Jetty family/EE alignment and packaging boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JettyHttpSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        MavenProperties properties = properties(source, ctx);
        if (!hasPrimary(source, ctx)) return source;
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (JettyHttpSupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    javaProperty(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(FindJettyHttpBuildRisks::belowJava17).isPresent()) {
                    return JettyHttpSupport.mark(visited, JAVA_BASELINE);
                }
                if (compilerLevel(getCursor(), visited) &&
                    visited.getValue().map(String::trim).filter(FindJettyHttpBuildRisks::belowJava17).isPresent()) {
                    return JettyHttpSupport.mark(visited, JAVA_BASELINE);
                }
                if (projectPackagingPlugin(visited) && mentionsJetty(visited)) {
                    return JettyHttpSupport.mark(visited, PACKAGING);
                }
                if (!JettyHttpSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").map(String::trim).orElse("");
                String artifact = visited.getChildValue("artifactId").map(String::trim).orElse("");
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                String version = resolve(raw, getCursor(), properties);
                if (JettyHttpSupport.GROUP.equals(group) && JettyHttpSupport.ARTIFACT.equals(artifact)) {
                    if (!JettyHttpSupport.standardJar(visited)) return JettyHttpSupport.mark(visited, VARIANT);
                    if (visited.getChild("exclusions").isPresent()) {
                        visited = JettyHttpSupport.mark(visited, PACKAGING);
                    }
                    String message = primaryMessage(version);
                    return message == null ? visited : markVersion(visited, message);
                }
                String message = companionMessage(group, artifact, version);
                return message == null ? visited : markVersion(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!mentionsPrimary(source.printAll())) return source;
        G.CompilationUnit result = (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = JettyHttpSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct) return visited;
                String group = JettyHttpSupport.mapValue(visited, "group");
                String artifact = JettyHttpSupport.mapValue(visited, "name");
                String version = JettyHttpSupport.mapValue(visited, "version");
                if (JettyHttpSupport.GROUP.equals(group) && JettyHttpSupport.ARTIFACT.equals(artifact)) {
                    if (JettyHttpSupport.hasVariant(visited)) return JettyHttpSupport.mark(visited, VARIANT);
                    String message = primaryMessage(version);
                    return message == null ? visited : JettyHttpSupport.mark(visited, message);
                }
                String companion = companionMessage(group, artifact, version);
                if (companion != null) return JettyHttpSupport.mark(visited, companion);
                if (dynamicPrimary(visited)) return JettyHttpSupport.mark(visited, OWNER);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = JettyHttpSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : JettyHttpSupport.mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        String text = source.printAll();
        if (belowJava17Build(text)) result = JettyHttpSupport.mark(result, JAVA_BASELINE);
        if (text.contains("libs.jetty.http") || text.contains("platform(")) {
            result = JettyHttpSupport.mark(result, OWNER);
        }
        if (text.contains("exclude group: 'org.eclipse.jetty'") || text.contains("shadowJar") ||
            text.contains("relocate 'org.eclipse.jetty")) result = JettyHttpSupport.mark(result, PACKAGING);
        return result;
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!mentionsPrimary(source.printAll())) return source;
        K.CompilationUnit result = (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = JettyHttpSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                return direct && dynamicPrimary(visited) ? JettyHttpSupport.mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = JettyHttpSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : JettyHttpSupport.mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        String text = source.printAll();
        if (belowJava17Build(text)) result = JettyHttpSupport.mark(result, JAVA_BASELINE);
        if (text.contains("libs.jetty.http") || text.contains("platform(")) {
            result = JettyHttpSupport.mark(result, OWNER);
        }
        if (text.contains("exclude(group = \"org.eclipse.jetty") || text.contains("shadowJar") ||
            text.contains("relocate(\"org.eclipse.jetty")) result = JettyHttpSupport.mark(result, PACKAGING);
        return result;
    }

    static String primaryMessage(String version) {
        if (version == null || version.isBlank()) return OWNER;
        if (JettyHttpSupport.AUTO_SOURCES.contains(version) || JettyHttpSupport.TARGET.equals(version)) return null;
        if (JettyHttpSupport.targetConflict(version)) return JettyHttpSupport.TARGET_CONFLICT;
        return JettyHttpSupport.fixedVersion(version) ? OUTSIDE : OWNER;
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        int at = coordinate.indexOf('@');
        String plain = at < 0 ? coordinate : coordinate.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2) return null;
        if (JettyHttpSupport.GROUP.equals(parts[0]) && JettyHttpSupport.ARTIFACT.equals(parts[1])) {
            if (parts.length != 3 || at >= 0) return at >= 0 ? VARIANT : OWNER;
            return primaryMessage(parts[2]);
        }
        if (parts.length == 3) return companionMessage(parts[0], parts[1], parts[2]);
        return null;
    }

    private static String companionMessage(String group, String artifact, String version) {
        if (group == null || !group.startsWith("org.eclipse.jetty") || artifact == null ||
            JettyHttpSupport.ARTIFACT.equals(artifact)) return null;
        if (legacyEeArtifact(group, artifact)) return EE_LINEAGE;
        if (version == null || !JettyHttpSupport.TARGET.equals(version)) return FAMILY_ALIGNMENT;
        return null;
    }

    private static boolean legacyEeArtifact(String group, String artifact) {
        return "org.eclipse.jetty.websocket".equals(group) ||
               "org.eclipse.jetty".equals(group) && Set.of(
                       "jetty-servlet", "jetty-servlets", "jetty-webapp", "jetty-security",
                       "jetty-annotations", "jetty-plus", "jetty-jndi", "apache-jsp").contains(artifact);
    }

    private static boolean hasPrimary(Xml.Document source, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (JettyHttpSupport.isJettyHttpDependency(getCursor(), visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static MavenProperties properties(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profilePropertyNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (JettyHttpSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    counts.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profilePropertyNames.add(key.name());
                }
                return visited;
            }
        }.visitNonNull(source, ctx);

        Map<PropertyKey, Integer> references = new HashMap<>();
        Map<PropertyKey, Integer> ownedReferences = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                collectReferences(visited.getText(), getCursor(), counts, references,
                        targetVersionReference(getCursor(), visited.getText()) ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), counts, references, null);
                return visited;
            }
        }.visitNonNull(source, ctx);

        Set<PropertyKey> safe = ownedReferences.keySet().stream()
                .filter(owner -> counts.getOrDefault(owner, 0) == 1)
                .filter(owner -> references.getOrDefault(owner, 0)
                        .equals(ownedReferences.getOrDefault(owner, 0)))
                .filter(owner -> !"ROOT".equals(owner.scope()) ||
                                 !profilePropertyNames.contains(owner.name()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new MavenProperties(counts, values, safe);
    }

    private static String resolve(String raw, Cursor cursor, MavenProperties properties) {
        if (raw == null || raw.isBlank()) return null;
        Matcher matcher = PROPERTY.matcher(raw);
        if (!matcher.matches()) return raw;
        PropertyKey local = new PropertyKey(scope(cursor), matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = properties.counts().getOrDefault(local, 0) == 1 ? local : root;
        return properties.safe().contains(owner) ? properties.values().get(owner) : null;
    }

    private static boolean targetVersionReference(Cursor cursor, String text) {
        if (!PROPERTY.matcher(text.trim()).matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) ||
            !"version".equals(version.getName())) return false;
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        return dependencyCursor.getValue() instanceof Xml.Tag dependency &&
               JettyHttpSupport.isJettyHttpDependency(dependencyCursor, dependency) &&
               JettyHttpSupport.standardJar(dependency);
    }

    private static void collectReferences(
            String text, Cursor cursor, Map<PropertyKey, Integer> counts,
            Map<PropertyKey, Integer> references, Map<PropertyKey, Integer> ownedReferences) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            PropertyKey local = new PropertyKey(scope(cursor), name);
            PropertyKey owner = counts.containsKey(local)
                    ? local
                    : new PropertyKey("ROOT", name);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(owner, 1, Integer::sum);
        }
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

    private static boolean javaProperty(String name) {
        return Set.of("maven.compiler.release", "maven.compiler.source", "maven.compiler.target",
                      "java.version", "jdk.version").contains(name);
    }

    private static boolean compilerLevel(Cursor cursor, Xml.Tag tag) {
        if (!Set.of("release", "source", "target").contains(tag.getName())) return false;
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag owner && "plugin".equals(owner.getName()) &&
                "maven-compiler-plugin".equals(owner.getChildValue("artifactId").orElse(""))) return true;
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static boolean belowJava17(String value) {
        Matcher matcher = JAVA_NUMBER.matcher(value);
        return matcher.matches() && new BigInteger(matcher.group(1)).compareTo(BigInteger.valueOf(17)) < 0;
    }

    private static boolean belowJava17Build(String text) {
        return text.matches("(?s).*sourceCompatibility\\s*=\\s*['\"](?:1\\.)?(?:8|9|10|11|12|13|14|15|16)['\"].*") ||
               text.matches("(?s).*targetCompatibility\\s*=\\s*['\"](?:1\\.)?(?:8|9|10|11|12|13|14|15|16)['\"].*") ||
               text.matches("(?s).*JavaVersion\\.VERSION_(?:1_8|9|10|11|12|13|14|15|16).*") ||
               text.matches("(?s).*JavaLanguageVersion\\.of\\((?:8|9|10|11|12|13|14|15|16)\\).*");
    }

    private static boolean mentionsPrimary(String text) {
        return text.contains(JettyHttpSupport.GROUP + ":" + JettyHttpSupport.ARTIFACT) ||
               text.contains("libs.jetty.http");
    }

    private static boolean dynamicPrimary(J.MethodInvocation method) {
        return method.getArguments().stream().anyMatch(argument ->
                !(argument instanceof J.Literal) && argument.toString().contains(JettyHttpSupport.ARTIFACT));
    }

    private static boolean projectPackagingPlugin(Xml.Tag tag) {
        if (!"plugin".equals(tag.getName())) return false;
        String artifact = tag.getChildValue("artifactId").orElse("");
        return "maven-shade-plugin".equals(artifact) || "maven-assembly-plugin".equals(artifact) ||
               "bnd-maven-plugin".equals(artifact) || "maven-bundle-plugin".equals(artifact);
    }

    private static boolean mentionsJetty(Xml.Tag tag) {
        if (tag.getValue().map(String::trim)
                .filter(value -> value.contains("org.eclipse.jetty") || value.contains("jetty-http")).isPresent()) {
            return true;
        }
        if (tag.getAttributes().stream().map(Xml.Attribute::getValueAsString)
                .anyMatch(value -> value.contains("org.eclipse.jetty") || value.contains("jetty-http"))) return true;
        return tag.getChildren().stream().anyMatch(FindJettyHttpBuildRisks::mentionsJetty);
    }

    private static Xml.Tag markVersion(Xml.Tag dependency, String message) {
        return dependency.getChild("version").map(version -> {
            Xml.Tag marked = JettyHttpSupport.mark(version, message);
            return marked == version ? dependency : dependency.withContent(dependency.getContent().stream()
                    .map(content -> content == version ? marked : content).toList());
        }).orElseGet(() -> JettyHttpSupport.mark(dependency, message));
    }

    private record PropertyKey(String scope, String name) {
    }

    private record MavenProperties(
            Map<PropertyKey, Integer> counts,
            Map<PropertyKey, String> values,
            Set<PropertyKey> safe) {
    }
}
