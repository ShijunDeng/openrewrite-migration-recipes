package com.huawei.clouds.openrewrite.springsecuritycore;

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

import java.util.Set;
import java.util.regex.Pattern;

/** Locate version ownership, no-downgrade, Java baseline, and dependency-family risks. */
public final class FindSpringSecurityCore6511BuildRisks extends Recipe {
    static final String OWNER =
            "Spring Security Core 版本由缺失版本、共享属性、父 POM、BOM/platform、version catalog、" +
            "动态表达式或外部插件控制；请修改真实 owner，并确认最终解析为 6.5.11";
    static final String OUTSIDE =
            "该固定 spring-security-core 版本不在工作簿自动升级白名单或目标 6.5.11 中；" +
            "配方不会扩大升级范围，请显式选择支持路径";
    static final String VARIANT =
            "该 spring-security-core 声明包含 classifier、非 JAR type 或 Gradle variant；" +
            "其制品形状不在确定性升级范围内";
    static final String JAVA_BASELINE =
            "Spring Security 6.5.11 要求 Java 17；请统一编译、测试、运行时、容器镜像与 CI";
    static final String PARAMETERS =
            "Spring Framework 6.1+ 的方法授权参数名解析需要 -parameters；" +
            "请启用编译参数并回归所有含 #参数名 的授权表达式";
    static final String SECURITY_ALIGNMENT =
            "Spring Security 组件、BOM 与 test 必须与 spring-security-core 6.5.11 对齐；" +
            "混装会造成认证、授权和密码编码的二进制/行为不一致";
    static final String SPRING_ALIGNMENT =
            "spring-security-core 6.5.11 发布 POM 使用 Spring Framework 6.2.19；" +
            "请升级真实 BOM owner，避免 Spring 5/6 或不同 6.2 patch 混装";
    static final String MICROMETER_ALIGNMENT =
            "spring-security-core 6.5.11 发布 POM 使用 micrometer-observation 1.15.12；" +
            "请统一 observation 依赖并回归认证/授权观测链路";

    private static final Pattern JAVA_NUMBER = Pattern.compile("^(?:1\\.)?(\\d+)(?:\\..*)?$");

    @Override
    public String getDisplayName() {
        return "Find Spring Security Core 6.5.11 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact no-downgrade conflicts, unresolved owners, variants, Java 17/-parameters and " +
               "Spring Security, Spring Framework and Micrometer alignment decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringSecurityCoreSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return kotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        boolean[] primary = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringSecurityCoreSupport.isTargetDependency(getCursor(), visited)) primary[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        if (!primary[0]) return source;
        SpringSecurityCoreSupport.PomProperties properties =
                SpringSecurityCoreSupport.analyzeProperties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringSecurityCoreSupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    String value = visited.getValue().map(String::trim).orElse("");
                    if (javaProperty(visited.getName()) && belowJava17(value)) {
                        return SpringSecurityCoreSupport.mark(visited, JAVA_BASELINE);
                    }
                    if ("maven.compiler.parameters".equals(visited.getName()) &&
                        "false".equalsIgnoreCase(value)) {
                        return SpringSecurityCoreSupport.mark(visited, PARAMETERS);
                    }
                    SpringSecurityCoreSupport.PropertyKey key =
                            SpringSecurityCoreSupport.propertyKey(getCursor(), visited.getName());
                    if (properties.safe().contains(key)) {
                        String message = primaryMessage(value, false);
                        if (message != null) return SpringSecurityCoreSupport.mark(visited, message);
                    }
                }
                if (!SpringSecurityCoreSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").map(String::trim).orElse("");
                String artifact = visited.getChildValue("artifactId").map(String::trim).orElse("");
                String raw = visited.getChildValue("version").map(String::trim).orElse("");
                String version = properties.resolve(raw, getCursor());
                if (SpringSecurityCoreSupport.isTargetDependency(getCursor(), visited)) {
                    if (SpringSecurityCoreSupport.targetConflict(version)) {
                        return markVersion(visited, SpringSecurityCoreSupport.TARGET_CONFLICT);
                    }
                    String safeVersion = properties.resolveSafe(raw, getCursor());
                    if (safeVersion == null) return markVersion(visited, OWNER);
                    if (SpringSecurityCoreSupport.PROPERTY_REFERENCE.matcher(raw).matches() &&
                        SpringSecurityCoreSupport.standardJar(visited)) {
                        return visited;
                    }
                }
                String message = dependencyMessage(group, artifact, version,
                        SpringSecurityCoreSupport.isTargetDependency(getCursor(), visited) &&
                        !SpringSecurityCoreSupport.standardJar(visited));
                return message == null ? visited : markVersion(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!mentionsTarget(source.printAll())) return source;
        G.CompilationUnit result = (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct) return visited;
                String message = dependencyMessage(
                        SpringSecurityCoreSupport.mapValue(visited, "group"),
                        SpringSecurityCoreSupport.mapValue(visited, "name"),
                        SpringSecurityCoreSupport.mapValue(visited, "version"),
                        SpringSecurityCoreSupport.hasVariant(visited));
                if (message != null) return SpringSecurityCoreSupport.mark(visited, message);
                return dynamicTarget(visited) ? SpringSecurityCoreSupport.mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : SpringSecurityCoreSupport.mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        if (belowJava17Build(source.printAll())) result = SpringSecurityCoreSupport.mark(result, JAVA_BASELINE);
        if (parametersDisabled(source.printAll())) result = SpringSecurityCoreSupport.mark(result, PARAMETERS);
        return result;
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!mentionsTarget(source.printAll())) return source;
        K.CompilationUnit result = (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct) return visited;
                String message = dependencyMessage(
                        SpringSecurityCoreSupport.mapValue(visited, "group"),
                        SpringSecurityCoreSupport.mapValue(visited, "name"),
                        SpringSecurityCoreSupport.mapValue(visited, "version"),
                        SpringSecurityCoreSupport.hasVariant(visited));
                if (message != null) return SpringSecurityCoreSupport.mark(visited, message);
                return dynamicTarget(visited) ? SpringSecurityCoreSupport.mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringSecurityCoreSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : SpringSecurityCoreSupport.mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        if (belowJava17Build(source.printAll())) result = SpringSecurityCoreSupport.mark(result, JAVA_BASELINE);
        if (parametersDisabled(source.printAll())) result = SpringSecurityCoreSupport.mark(result, PARAMETERS);
        return result;
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String plain = coordinate;
        int at = plain.indexOf('@');
        if (at >= 0) plain = plain.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2) return null;
        return dependencyMessage(parts[0], parts[1], parts.length == 3 ? parts[2] : null, at >= 0);
    }

    private static boolean mentionsTarget(String source) {
        return source.contains(SpringSecurityCoreSupport.GROUP + ":" + SpringSecurityCoreSupport.ARTIFACT) ||
               source.contains(SpringSecurityCoreSupport.GROUP) &&
               source.contains(SpringSecurityCoreSupport.ARTIFACT);
    }

    private static boolean dynamicTarget(J.MethodInvocation method) {
        return method.getArguments().stream().anyMatch(FindSpringSecurityCore6511BuildRisks::dynamicTarget);
    }

    private static boolean dynamicTarget(J expression) {
        java.util.List<J> parts;
        if (expression instanceof G.GString string) parts = string.getStrings();
        else if (expression instanceof K.StringTemplate string) parts = string.getStrings();
        else return false;
        return parts.stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> value.stripLeading().startsWith(
                        SpringSecurityCoreSupport.GROUP + ":" + SpringSecurityCoreSupport.ARTIFACT + ":"));
    }

    private static String dependencyMessage(String group, String artifact, String version, boolean variant) {
        if (SpringSecurityCoreSupport.GROUP.equals(group) &&
            SpringSecurityCoreSupport.ARTIFACT.equals(artifact)) {
            return primaryMessage(version, variant);
        }
        if (group == null || artifact == null) return null;
        if (SpringSecurityCoreSupport.GROUP.equals(group) && artifact.startsWith("spring-security-") &&
            !SpringSecurityCoreSupport.TARGET.equals(version)) {
            return SpringSecurityCoreSupport.targetConflict(version)
                    ? SpringSecurityCoreSupport.TARGET_CONFLICT : SECURITY_ALIGNMENT;
        }
        if ("org.springframework".equals(group) && artifact.startsWith("spring-") &&
            !"6.2.19".equals(version)) {
            return SpringSecurityCoreSupport.versionGreaterThan(version, "6.2.19")
                    ? SpringSecurityCoreSupport.TARGET_CONFLICT : SPRING_ALIGNMENT;
        }
        if ("io.micrometer".equals(group) && artifact.startsWith("micrometer-") &&
            !"1.15.12".equals(version)) {
            return SpringSecurityCoreSupport.versionGreaterThan(version, "1.15.12")
                    ? SpringSecurityCoreSupport.TARGET_CONFLICT : MICROMETER_ALIGNMENT;
        }
        return null;
    }

    private static String primaryMessage(String version, boolean variant) {
        if (SpringSecurityCoreSupport.targetConflict(version)) {
            return SpringSecurityCoreSupport.TARGET_CONFLICT;
        }
        if (variant) return VARIANT;
        if (version == null || version.isBlank()) return OWNER;
        if (SpringSecurityCoreSupport.TARGET.equals(version) ||
            SpringSecurityCoreSupport.SOURCE_VERSIONS.contains(version)) return null;
        return SpringSecurityCoreSupport.FIXED_VERSION.matcher(version).matches() ? OUTSIDE : OWNER;
    }

    private static Xml.Tag markVersion(Xml.Tag dependency, String message) {
        return dependency.getChild("version").map(version -> {
            Xml.Tag marked = SpringSecurityCoreSupport.mark(version, message);
            return marked == version ? dependency : dependency.withContent(dependency.getContent().stream()
                    .map(content -> content == version ? marked : content).toList());
        }).orElseGet(() -> SpringSecurityCoreSupport.mark(dependency, message));
    }

    private static boolean javaProperty(String name) {
        return Set.of("maven.compiler.release", "maven.compiler.source", "maven.compiler.target",
                      "java.version", "jdk.version").contains(name);
    }

    private static boolean belowJava17(String value) {
        java.util.regex.Matcher matcher = JAVA_NUMBER.matcher(value);
        return matcher.matches() &&
               new java.math.BigInteger(matcher.group(1))
                       .compareTo(java.math.BigInteger.valueOf(17)) < 0;
    }

    private static boolean belowJava17Build(String source) {
        return Pattern.compile("(?m)(?:sourceCompatibility|targetCompatibility|jvmToolchain\\s*\\(|jvmTarget\\s*=)" +
                               "[^\\n]*(?:VERSION_1_[1-9]|JavaVersion\\.VERSION_1_[1-9]|[\"'](?:1\\.)?(?:[1-9]|1[0-6])[\"'])")
                .matcher(source).find();
    }

    private static boolean parametersDisabled(String source) {
        return Pattern.compile("(?m)(?:parameters\\s*=\\s*false|-parameters['\"]?\\s*:\\s*false)")
                .matcher(source).find();
    }
}
