package com.huawei.clouds.openrewrite.springsecurityweb;

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

/** Locate exact-version, owner, Java, Jakarta and dependency-family risks. */
public final class FindSpringSecurityWeb6511BuildRisks extends Recipe {
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern JAVA_NUMBER = Pattern.compile("^(?:1\\.)?(\\d+)(?:\\..*)?$");

    static final String OWNER =
            "Spring Security Web 版本由缺失版本、共享/遮蔽属性、父 POM、BOM/platform、version catalog、" +
            "动态表达式或外部插件控制；请修改真实 owner，并确认最终解析为 6.5.11";
    static final String OUTSIDE =
            "该固定 spring-security-web 版本不在工作簿自动升级白名单或目标 6.5.11 中；" +
            "配方不会扩大升级范围，请显式选择支持路径";
    static final String VARIANT =
            "该 spring-security-web 声明包含 classifier、非 JAR type 或 Gradle variant；" +
            "其制品形状不在确定性升级范围内";
    static final String JAVA_BASELINE =
            "Spring Security 6.5.11 使用 Java 17 toolchain/release；请统一编译、测试、运行时、容器镜像与 CI";
    static final String PARAMETERS =
            "Spring Security 方法授权在 Spring Framework 6.1+ 需要 -parameters 才能稳定解析参数名；" +
            "请启用编译参数并回归所有含 #参数名 的授权表达式";
    static final String SECURITY_ALIGNMENT =
            "Spring Security 组件/BOM/test 必须与 spring-security-web 6.5.11 对齐；" +
            "混装会造成过滤器、AuthorizationManager 与二进制签名不一致";
    static final String SPRING_ALIGNMENT =
            "spring-security-web 6.5.11 发布 POM 使用 Spring Framework 6.2.19；" +
            "请统一 Spring/Boot BOM，避免 5.x/6.x 或不同 6.2 patch 混装";
    static final String JAKARTA_ALIGNMENT =
            "Spring Security 6 使用 jakarta.servlet；目标源码以 jakarta.servlet-api 6.0.0 构建。" +
            "请移除 javax.servlet，统一容器/API，并验证 Filter 与 Request/Response 包名";
    static final String MICROMETER_ALIGNMENT =
            "Spring Security 6.5.11 以 Micrometer 1.15.12 和 context-propagation 1.1.4 构建；" +
            "请对齐 observation 依赖并验证 trace/context 传播";
    static final String BOOT_OWNER =
            "Spring Boot BOM/插件通常拥有 Spring Security 与 Spring Framework 版本；" +
            "请在 Boot 兼容矩阵中升级真实 owner，不要在叶子模块强行覆盖";
    static final String AUTHORIZATION_SERVER =
            "spring-security-oauth2-authorization-server 使用独立 1.x 发布线；" +
            "请按 1.5.x 兼容矩阵单独升级，不能与 Spring Security 6.5.11 机械同版";

    @Override
    public String getDisplayName() {
        return "Find Spring Security Web 6.5.11 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark no-downgrade conflicts, unresolved owners, variants, Java 17/-parameters, Spring 6.2.19, " +
               "Security family, Jakarta Servlet and Micrometer alignment decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringSecurityWebUpgradeSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        Set<String> primaryScopes = primaryScopes(source, ctx);
        if (primaryScopes.isEmpty()) return source;
        MavenProperties properties = mavenProperties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!visible(getCursor(), primaryScopes)) return visited;
                if (SpringSecurityWebUpgradeSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    String value = visited.getValue().map(String::trim).orElse("");
                    if (javaProperty(visited.getName()) && belowJava17(value)) {
                        return mark(visited, JAVA_BASELINE);
                    }
                    if ("maven.compiler.parameters".equals(visited.getName()) && "false".equalsIgnoreCase(value)) {
                        return mark(visited, PARAMETERS);
                    }
                }
                if (compilerLevel(getCursor(), visited) &&
                    visited.getValue().map(String::trim).filter(FindSpringSecurityWeb6511BuildRisks::belowJava17)
                            .isPresent()) {
                    return mark(visited, JAVA_BASELINE);
                }
                if (compilerParameters(getCursor(), visited) &&
                    visited.getValue().map(String::trim).filter("false"::equalsIgnoreCase).isPresent()) {
                    return mark(visited, PARAMETERS);
                }
                if (!SpringSecurityWebUpgradeSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").map(String::trim).orElse("");
                String artifact = visited.getChildValue("artifactId").map(String::trim).orElse("");
                String raw = visited.getChildValue("version").map(String::trim).orElse("");
                String version = resolve(raw, getCursor(), properties);

                if (SpringSecurityWebUpgradeSupport.GROUP.equals(group) &&
                    SpringSecurityWebUpgradeSupport.ARTIFACT.equals(artifact)) {
                    if (!SpringSecurityWebUpgradeSupport.standardJar(visited)) return mark(visited, VARIANT);
                    String message = primaryMessage(version);
                    return message == null ? visited : markVersion(visited, message);
                }
                String message = companionMessage(group, artifact, version);
                return message == null ? visited : markVersion(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx) && !managedPrimary(source.printAll())) return source;
        G.CompilationUnit result = (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityWebUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct) return visited;
                String group = SpringSecurityWebUpgradeSupport.mapValue(visited, "group");
                String artifact = SpringSecurityWebUpgradeSupport.mapValue(visited, "name");
                String version = SpringSecurityWebUpgradeSupport.mapValue(visited, "version");
                boolean variant = SpringSecurityWebUpgradeSupport.hasVariant(visited);
                G.MapLiteral map = visited.getArguments().stream().filter(G.MapLiteral.class::isInstance)
                        .map(G.MapLiteral.class::cast).findFirst().orElse(null);
                if (map != null) {
                    group = SpringSecurityWebUpgradeSupport.mapValue(map, "group");
                    artifact = SpringSecurityWebUpgradeSupport.mapValue(map, "name");
                    version = SpringSecurityWebUpgradeSupport.mapValue(map, "version");
                    variant = SpringSecurityWebUpgradeSupport.hasVariant(map);
                }
                String message = dependencyMessage(group, artifact, version, variant);
                if (message != null) return mark(visited, message);
                return invocationMentionsDynamicPrimary(visited) ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityWebUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        if (belowJava17Build(source.printAll())) result = mark(result, JAVA_BASELINE);
        if (parametersDisabled(source.printAll())) result = mark(result, PARAMETERS);
        if (managedPrimary(source.printAll())) result = mark(result, OWNER);
        return result;
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!hasPrimary(source, ctx) && !managedPrimary(source.printAll())) return source;
        K.CompilationUnit result = (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityWebUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                return direct && invocationMentionsDynamicPrimary(visited) ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityWebUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        if (belowJava17Build(source.printAll())) result = mark(result, JAVA_BASELINE);
        if (parametersDisabled(source.printAll())) result = mark(result, PARAMETERS);
        if (managedPrimary(source.printAll())) result = mark(result, OWNER);
        return result;
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (SpringSecurityWebUpgradeSupport.GROUP.equals(group) &&
            SpringSecurityWebUpgradeSupport.ARTIFACT.equals(artifact)) {
            if (variant) return VARIANT;
            return primaryMessage(version);
        }
        return companionMessage(group, artifact, version);
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String plain = coordinate;
        int at = plain.indexOf('@');
        if (at >= 0) plain = plain.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2) return null;
        String version = parts.length == 3 ? parts[2] : null;
        if (SpringSecurityWebUpgradeSupport.GROUP.equals(parts[0]) &&
            SpringSecurityWebUpgradeSupport.ARTIFACT.equals(parts[1])) {
            if (parts.length != 3 || at >= 0) return at >= 0 ? VARIANT : OWNER;
            return primaryMessage(version);
        }
        return parts.length == 3 ? companionMessage(parts[0], parts[1], version) : null;
    }

    private static String primaryMessage(String version) {
        if (version == null || version.isBlank()) return OWNER;
        if (SpringSecurityWebUpgradeSupport.TARGET.equals(version) ||
            SpringSecurityWebUpgradeSupport.UPGRADE_VERSIONS.contains(version)) return null;
        if (SpringSecurityWebUpgradeSupport.targetConflict(version)) {
            return SpringSecurityWebUpgradeSupport.TARGET_CONFLICT;
        }
        return SpringSecurityWebUpgradeSupport.fixedVersion(version) ? OUTSIDE : OWNER;
    }

    private static String companionMessage(String group, String artifact, String version) {
        if (group == null || artifact == null) return null;
        if ("org.springframework.security".equals(group)) {
            if ("spring-security-oauth2-authorization-server".equals(artifact)) return AUTHORIZATION_SERVER;
            if (artifact.startsWith("spring-security-") &&
                !SpringSecurityWebUpgradeSupport.TARGET.equals(version)) return SECURITY_ALIGNMENT;
        }
        if ("org.springframework".equals(group) && artifact.startsWith("spring-") &&
            !"6.2.19".equals(version)) return SPRING_ALIGNMENT;
        if ("org.springframework.boot".equals(group) && artifact.startsWith("spring-boot")) return BOOT_OWNER;
        if ("javax.servlet".equals(group)) return JAKARTA_ALIGNMENT;
        if ("jakarta.servlet".equals(group) && artifact.contains("servlet") && !"6.0.0".equals(version)) {
            return JAKARTA_ALIGNMENT;
        }
        if ("io.micrometer".equals(group) && "context-propagation".equals(artifact) &&
            !"1.1.4".equals(version)) return MICROMETER_ALIGNMENT;
        if ("io.micrometer".equals(group) && artifact.startsWith("micrometer-") &&
            !"1.15.12".equals(version)) return MICROMETER_ALIGNMENT;
        return null;
    }

    private static boolean javaProperty(String name) {
        return Set.of("maven.compiler.release", "maven.compiler.source", "maven.compiler.target",
                      "java.version", "jdk.version").contains(name);
    }

    private static boolean compilerLevel(Cursor cursor, Xml.Tag tag) {
        if (!Set.of("release", "source", "target").contains(tag.getName())) return false;
        return insideCompilerPlugin(cursor);
    }

    private static boolean compilerParameters(Cursor cursor, Xml.Tag tag) {
        return "parameters".equals(tag.getName()) && insideCompilerPlugin(cursor);
    }

    private static boolean insideCompilerPlugin(Cursor cursor) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag owner &&
                "plugin".equals(owner.getName()) &&
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

    private static boolean parametersDisabled(String text) {
        return text.matches("(?s).*options\\.compilerArgs.*(?:remove|-=).*['\"]-parameters['\"].*") ||
               text.matches("(?s).*parameters\\s*=\\s*false.*");
    }

    private static boolean managedPrimary(String text) {
        return text.contains("libs.spring.security.web") ||
               text.matches("(?s).*platform\\s*\\(\\s*['\"]org\\.springframework\\.security:spring-security-bom.*");
    }

    private static Set<String> primaryScopes(Xml.Document source, ExecutionContext ctx) {
        Set<String> scopes = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringSecurityWebUpgradeSupport.isPrimaryDependency(getCursor(), visited)) {
                    scopes.add(scope(getCursor()));
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return Set.copyOf(scopes);
    }

    private static boolean visible(Cursor cursor, Set<String> primaryScopes) {
        String owner = scope(cursor);
        return primaryScopes.contains("ROOT") || primaryScopes.contains(owner) ||
               "ROOT".equals(owner) && !primaryScopes.isEmpty();
    }

    private static MavenProperties mavenProperties(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringSecurityWebUpgradeSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    counts.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                    if (!"ROOT".equals(key.scope())) profileNames.add(key.name());
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return new MavenProperties(counts, values, profileNames);
    }

    private static String resolve(String raw, Cursor cursor, MavenProperties properties) {
        if (SpringSecurityWebUpgradeSupport.fixedVersion(raw)) return raw;
        Matcher matcher = PROPERTY.matcher(raw);
        if (!matcher.matches()) return null;
        String currentScope = scope(cursor);
        PropertyKey local = new PropertyKey(currentScope, matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = !"ROOT".equals(currentScope) && properties.counts().containsKey(local) ? local : root;
        if (properties.counts().getOrDefault(owner, 0) != 1 ||
            "ROOT".equals(owner.scope()) && properties.profileNames().contains(owner.name())) return null;
        return properties.values().get(owner);
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

    private static boolean hasPrimary(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityWebUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean hasPrimary(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityWebUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && invocationMentionsPrimary(visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean invocationMentionsPrimary(J.MethodInvocation method) {
        if (SpringSecurityWebUpgradeSupport.GROUP.equals(SpringSecurityWebUpgradeSupport.mapValue(method, "group")) &&
            SpringSecurityWebUpgradeSupport.ARTIFACT.equals(SpringSecurityWebUpgradeSupport.mapValue(method, "name"))) {
            return true;
        }
        for (J argument : method.getArguments()) {
            if (argument instanceof J.Literal literal && primaryCoordinate(literal.getValue())) return true;
            if (argument instanceof G.MapLiteral map &&
                SpringSecurityWebUpgradeSupport.GROUP.equals(SpringSecurityWebUpgradeSupport.mapValue(map, "group")) &&
                SpringSecurityWebUpgradeSupport.ARTIFACT.equals(SpringSecurityWebUpgradeSupport.mapValue(map, "name"))) {
                return true;
            }
            if (dynamicPrimary(argument)) return true;
        }
        return false;
    }

    private static boolean primaryCoordinate(Object value) {
        return value instanceof String coordinate &&
               (coordinate.equals(SpringSecurityWebUpgradeSupport.GROUP + ":" +
                                  SpringSecurityWebUpgradeSupport.ARTIFACT) ||
                coordinate.startsWith(SpringSecurityWebUpgradeSupport.GROUP + ":" +
                                      SpringSecurityWebUpgradeSupport.ARTIFACT + ":"));
    }

    private static boolean invocationMentionsDynamicPrimary(J.MethodInvocation method) {
        return method.getArguments().stream().anyMatch(FindSpringSecurityWeb6511BuildRisks::dynamicPrimary);
    }

    private static boolean dynamicPrimary(J expression) {
        java.util.List<J> parts;
        if (expression instanceof G.GString string) parts = string.getStrings();
        else if (expression instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> value.stripLeading().startsWith(
                        SpringSecurityWebUpgradeSupport.GROUP + ":" +
                        SpringSecurityWebUpgradeSupport.ARTIFACT + ":"));
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
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }

    private record PropertyKey(String scope, String name) {
    }

    private record MavenProperties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values,
                                   Set<String> profileNames) {
    }
}
