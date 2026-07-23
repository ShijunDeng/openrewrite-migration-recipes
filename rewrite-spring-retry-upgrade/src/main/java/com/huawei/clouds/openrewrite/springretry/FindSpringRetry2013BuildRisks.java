package com.huawei.clouds.openrewrite.springretry;

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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Locate dependency ownership, baseline and integration decisions the strict upgrade cannot infer. */
public final class FindSpringRetry2013BuildRisks extends Recipe {
    private static final Pattern PROPERTY = Pattern.compile("^\\$\\{([^}]+)}$");
    private static final Pattern JAVA_NUMBER = Pattern.compile("^(?:1\\.)?(\\d+)(?:\\..*)?$");
    static final String OWNER =
            "Spring Retry 版本由缺失版本、父 POM、BOM/platform、共享属性、version catalog、动态表达式或插件控制；" +
            "请修改真实 owner，并确认最终解析为 2.0.13";
    static final String OUTSIDE =
            "该固定 Spring Retry 版本不是自动白名单 1.3.4 或目标 2.0.13；未获得升级路径证据，配方保持原值";
    static final String VARIANT =
            "该 Spring Retry 声明含 classifier、非 JAR type 或 Gradle variant；请人工确认制品形状";
    static final String DECLARATION_CONFLICT =
            "同一构建根同时包含 Spring Retry 1.3.4 与目标版 2.0.13；已阻断全部自动迁移，请统一真实版本 owner";
    static final String JAVA_BASELINE =
            "Spring Retry 2.0.13 字节码为 Java 17（class major 61）；请统一编译、测试、运行时、容器和 CI 的 Java 基线";
    static final String SPRING_BASELINE =
            "Spring Retry 2.0.13 发布 POM使用可选 Spring Context 6.2.19；请对齐 Spring Framework/Boot BOM，避免 5.x/6.x 混装";
    static final String MICROMETER_ALIGNMENT =
            "Spring Retry 2.0.13 发布 POM使用可选 Micrometer Core 1.15.12；使用 MetricsRetryListener 时请对齐并验证 meter 名称、标签和生命周期";
    static final String PROXY_STACK =
            "@EnableRetry/@Retryable 依赖 Spring AOP/AspectJ 代理；请验证代理模式、final/private/self-invocation、advisor 顺序和事务边界";
    static final String PACKAGING =
            "Spring Retry 被排除、重定位、shade 或以非标准方式打包；请检查运行时类路径、原生镜像反射配置和重复版本";

    @Override
    public String getDisplayName() {
        return "Find Spring Retry 2.0.13 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark no-downgrade conflicts, unsupported versions, unresolved owners, variants, Java 17, Spring " +
               "6.2.19, Micrometer 1.15.12, proxy and packaging boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || SpringRetrySupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                boolean selected = source.getMarkers().findFirst(SpringRetryProjectMarker.class).isPresent();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    return maven(document, ctx, selected);
                }
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) {
                    return groovy(groovy, ctx, selected);
                }
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) {
                    return kotlin(kotlin, ctx, selected);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx, boolean selected) {
        MavenProperties properties = properties(source, ctx);
        if (!hasPrimary(source, ctx)) return source;
        if (hasTargetConflict(source, ctx, properties)) {
            return markOnlyTargetConflicts(source, ctx, properties);
        }
        if (hasSourceTargetConflict(source, ctx, properties)) {
            return markOnlySourceTargetConflicts(source, ctx, properties);
        }
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (selected &&
                    SpringRetrySupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    javaProperty(visited.getName()) &&
                    visited.getValue().map(String::trim).filter(FindSpringRetry2013BuildRisks::belowJava17)
                            .isPresent()) {
                    return mark(visited, JAVA_BASELINE);
                }
                if (selected && compilerLevel(getCursor(), visited) &&
                    visited.getValue().map(String::trim).filter(FindSpringRetry2013BuildRisks::belowJava17)
                            .isPresent()) {
                    return mark(visited, JAVA_BASELINE);
                }
                if (selected && projectShadePlugin(getCursor(), visited) && mentionsSpringRetry(visited)) {
                    return mark(visited, PACKAGING);
                }
                if (!SpringRetrySupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").map(String::trim).orElse("");
                String artifact = visited.getChildValue("artifactId").map(String::trim).orElse("");
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                String version = resolve(raw, getCursor(), properties);
                if (SpringRetrySupport.GROUP.equals(group) && SpringRetrySupport.ARTIFACT.equals(artifact)) {
                    if (!SpringRetrySupport.standardJar(visited)) return mark(visited, VARIANT);
                    if (selected && visited.getChild("exclusions").isPresent()) {
                        return mark(visited, PACKAGING);
                    }
                    String message = primaryMessage(version);
                    return message == null ? visited : markVersion(visited, message);
                }
                if (!selected) return visited;
                String message = companionMessage(group, artifact, version);
                return message == null ? visited : markVersion(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(
            G.CompilationUnit source, ExecutionContext ctx, boolean selected) {
        if (!mentionsPrimary(source.printAll())) return source;
        if (hasGroovyTargetConflict(source, ctx)) return markOnlyGroovyTargetConflicts(source, ctx);
        if (hasGroovySourceTargetConflict(source, ctx)) {
            return markOnlyGroovySourceTargetConflicts(source, ctx);
        }
        G.CompilationUnit result = (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct) return visited;
                String group = SpringRetrySupport.mapValue(visited, "group");
                String artifact = SpringRetrySupport.mapValue(visited, "name");
                String version = SpringRetrySupport.mapValue(visited, "version");
                if (SpringRetrySupport.GROUP.equals(group) && SpringRetrySupport.ARTIFACT.equals(artifact)) {
                    if (SpringRetrySupport.hasVariant(visited)) return mark(visited, VARIANT);
                    String message = primaryMessage(version);
                    return message == null ? visited : mark(visited, message);
                }
                if (dynamicPrimary(visited)) return mark(visited, OWNER);
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        String text = source.printAll();
        if (selected && belowJava17Build(text)) result = mark(result, JAVA_BASELINE);
        if (text.contains("libs.spring.retry") || platformOwnsPrimary(text)) result = mark(result, OWNER);
        if (selected &&
            (text.contains("exclude group: 'org.springframework.retry'") || text.contains("shadowJar") ||
             text.contains("relocate 'org.springframework.retry"))) result = mark(result, PACKAGING);
        return result;
    }

    private static K.CompilationUnit kotlin(
            K.CompilationUnit source, ExecutionContext ctx, boolean selected) {
        if (!mentionsPrimary(source.printAll())) return source;
        if (hasKotlinTargetConflict(source, ctx)) return markOnlyKotlinTargetConflicts(source, ctx);
        if (hasKotlinSourceTargetConflict(source, ctx)) {
            return markOnlyKotlinSourceTargetConflicts(source, ctx);
        }
        K.CompilationUnit result = (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                return direct && dynamicPrimary(visited) ? mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        String text = source.printAll();
        if (selected && belowJava17Build(text)) result = mark(result, JAVA_BASELINE);
        if (text.contains("libs.spring.retry") || platformOwnsPrimary(text)) result = mark(result, OWNER);
        if (selected &&
            (text.contains("exclude(group = \"org.springframework.retry") || text.contains("shadowJar") ||
             text.contains("relocate(\"org.springframework.retry"))) result = mark(result, PACKAGING);
        return result;
    }

    private static boolean hasTargetConflict(
            Xml.Document source, ExecutionContext ctx, MavenProperties properties) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringRetrySupport.isSpringRetryDependency(getCursor(), visited)) {
                    String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                    if (SpringRetrySupport.targetConflict(resolve(raw, getCursor(), properties))) {
                        found[0] = true;
                    }
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static Xml.Document markOnlyTargetConflicts(
            Xml.Document source, ExecutionContext ctx, MavenProperties properties) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!SpringRetrySupport.isSpringRetryDependency(getCursor(), visited)) return visited;
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                return SpringRetrySupport.targetConflict(resolve(raw, getCursor(), properties))
                        ? markVersion(visited, SpringRetrySupport.TARGET_CONFLICT) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasSourceTargetConflict(
            Xml.Document source, ExecutionContext ctx, MavenProperties properties) {
        boolean[] versions = {false, false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringRetrySupport.isSpringRetryDependency(getCursor(), visited)) {
                    String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                    recordSourceAndTarget(resolve(raw, getCursor(), properties), versions);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return versions[0] && versions[1];
    }

    private static Xml.Document markOnlySourceTargetConflicts(
            Xml.Document source, ExecutionContext ctx, MavenProperties properties) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (!SpringRetrySupport.isSpringRetryDependency(getCursor(), visited)) return visited;
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                String version = resolve(raw, getCursor(), properties);
                return sourceOrTarget(version) ? markVersion(visited, DECLARATION_CONFLICT) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasGroovyTargetConflict(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct && SpringRetrySupport.GROUP.equals(SpringRetrySupport.mapValue(visited, "group")) &&
                    SpringRetrySupport.ARTIFACT.equals(SpringRetrySupport.mapValue(visited, "name")) &&
                    SpringRetrySupport.targetConflict(SpringRetrySupport.mapValue(visited, "version"))) {
                    found[0] = true;
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct && SpringRetrySupport.targetConflict(coordinateVersion(visited.getValue()))) {
                    found[0] = true;
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static G.CompilationUnit markOnlyGroovyTargetConflicts(
            G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                return direct &&
                       SpringRetrySupport.GROUP.equals(SpringRetrySupport.mapValue(visited, "group")) &&
                       SpringRetrySupport.ARTIFACT.equals(SpringRetrySupport.mapValue(visited, "name")) &&
                       SpringRetrySupport.targetConflict(SpringRetrySupport.mapValue(visited, "version"))
                        ? mark(visited, SpringRetrySupport.TARGET_CONFLICT) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct && SpringRetrySupport.targetConflict(coordinateVersion(visited.getValue()))
                        ? mark(visited, SpringRetrySupport.TARGET_CONFLICT) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasGroovySourceTargetConflict(
            G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] versions = {false, false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (direct &&
                    SpringRetrySupport.GROUP.equals(SpringRetrySupport.mapValue(visited, "group")) &&
                    SpringRetrySupport.ARTIFACT.equals(SpringRetrySupport.mapValue(visited, "name"))) {
                    recordSourceAndTarget(SpringRetrySupport.mapValue(visited, "version"), versions);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct) recordSourceAndTarget(coordinateVersion(visited.getValue()), versions);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return versions[0] && versions[1];
    }

    private static G.CompilationUnit markOnlyGroovySourceTargetConflicts(
            G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                String version = SpringRetrySupport.mapValue(visited, "version");
                return direct &&
                       SpringRetrySupport.GROUP.equals(SpringRetrySupport.mapValue(visited, "group")) &&
                       SpringRetrySupport.ARTIFACT.equals(SpringRetrySupport.mapValue(visited, "name")) &&
                       sourceOrTarget(version) ? mark(visited, DECLARATION_CONFLICT) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct && sourceOrTarget(coordinateVersion(visited.getValue()))
                        ? mark(visited, DECLARATION_CONFLICT) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasKotlinTargetConflict(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct && SpringRetrySupport.targetConflict(coordinateVersion(visited.getValue()))) {
                    found[0] = true;
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static K.CompilationUnit markOnlyKotlinTargetConflicts(
            K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct && SpringRetrySupport.targetConflict(coordinateVersion(visited.getValue()))
                        ? mark(visited, SpringRetrySupport.TARGET_CONFLICT) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean hasKotlinSourceTargetConflict(
            K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] versions = {false, false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                if (direct) recordSourceAndTarget(coordinateVersion(visited.getValue()), versions);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return versions[0] && versions[1];
    }

    private static K.CompilationUnit markOnlyKotlinSourceTargetConflicts(
            K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringRetrySupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct && sourceOrTarget(coordinateVersion(visited.getValue()))
                        ? mark(visited, DECLARATION_CONFLICT) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static void recordSourceAndTarget(String version, boolean[] versions) {
        if (SpringRetrySupport.SOURCE.equals(version)) versions[0] = true;
        if (SpringRetrySupport.TARGET.equals(version)) versions[1] = true;
    }

    private static boolean sourceOrTarget(String version) {
        return SpringRetrySupport.SOURCE.equals(version) || SpringRetrySupport.TARGET.equals(version);
    }

    private static String coordinateVersion(Object value) {
        if (!(value instanceof String coordinate)) return null;
        String prefix = SpringRetrySupport.GROUP + ":" + SpringRetrySupport.ARTIFACT + ":";
        if (!coordinate.startsWith(prefix)) return null;
        String version = coordinate.substring(prefix.length());
        int colon = version.indexOf(':');
        int at = version.indexOf('@');
        int end = colon < 0 ? at : at < 0 ? colon : Math.min(colon, at);
        return end < 0 ? version : version.substring(0, end);
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        int at = coordinate.indexOf('@');
        String plain = at < 0 ? coordinate : coordinate.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2 || !SpringRetrySupport.GROUP.equals(parts[0]) ||
            !SpringRetrySupport.ARTIFACT.equals(parts[1])) return null;
        if (parts.length != 3 || at >= 0) return at >= 0 ? VARIANT : OWNER;
        return primaryMessage(parts[2]);
    }

    static String primaryMessage(String version) {
        if (version == null || version.isBlank()) return OWNER;
        if (SpringRetrySupport.SOURCE.equals(version) || SpringRetrySupport.TARGET.equals(version)) return null;
        if (SpringRetrySupport.targetConflict(version)) return SpringRetrySupport.TARGET_CONFLICT;
        return SpringRetrySupport.fixedVersion(version) ? OUTSIDE : OWNER;
    }

    private static String companionMessage(String group, String artifact, String version) {
        if ("org.springframework".equals(group) && artifact != null && artifact.startsWith("spring-") &&
            !"6.2.19".equals(version)) return SPRING_BASELINE;
        if ("org.springframework.boot".equals(group) && artifact != null && artifact.startsWith("spring-boot")) {
            return SPRING_BASELINE;
        }
        if ("io.micrometer".equals(group) && "micrometer-core".equals(artifact) &&
            !"1.15.12".equals(version)) return MICROMETER_ALIGNMENT;
        if ("org.aspectj".equals(group) && "aspectjweaver".equals(artifact) ||
            "org.springframework".equals(group) && "spring-aop".equals(artifact)) return PROXY_STACK;
        return null;
    }

    private static boolean hasPrimary(Xml.Document source, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringRetrySupport.isSpringRetryDependency(getCursor(), visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static MavenProperties properties(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (SpringRetrySupport.isMavenPropertyDefinition(getCursor(), visited)) {
                    PropertyKey key = new PropertyKey(scope(getCursor()), visited.getName());
                    counts.merge(key, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(key, value.trim()));
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return new MavenProperties(counts, values);
    }

    private static String resolve(String raw, Cursor cursor, MavenProperties properties) {
        if (raw == null || raw.isBlank()) return null;
        Matcher matcher = PROPERTY.matcher(raw);
        if (!matcher.matches()) return raw;
        PropertyKey local = new PropertyKey(scope(cursor), matcher.group(1));
        PropertyKey root = new PropertyKey("ROOT", matcher.group(1));
        PropertyKey owner = properties.counts().getOrDefault(local, 0) == 1 ? local : root;
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
        return text.contains(SpringRetrySupport.GROUP + ":" + SpringRetrySupport.ARTIFACT) ||
               text.contains("libs.spring.retry");
    }

    private static boolean platformOwnsPrimary(String text) {
        return text.matches("(?s).*(?:platform|enforcedPlatform)\\s*\\(\\s*['\"]" +
                            Pattern.quote(SpringRetrySupport.GROUP + ":" + SpringRetrySupport.ARTIFACT) +
                            "(?::[^'\"]*)?['\"].*");
    }

    private static boolean dynamicPrimary(J.MethodInvocation method) {
        return method.getArguments().stream().anyMatch(argument ->
                !(argument instanceof J.Literal) && argument.toString().contains(SpringRetrySupport.ARTIFACT));
    }

    private static boolean projectShadePlugin(Cursor cursor, Xml.Tag tag) {
        if (!"plugin".equals(tag.getName())) return false;
        String artifact = tag.getChildValue("artifactId").orElse("");
        return "maven-shade-plugin".equals(artifact) || "maven-assembly-plugin".equals(artifact);
    }

    private static boolean mentionsSpringRetry(Xml.Tag tag) {
        if (tag.getValue().map(String::trim)
                .filter(value -> value.contains("org.springframework.retry") ||
                                 value.contains("spring-retry")).isPresent()) return true;
        if (tag.getAttributes().stream().map(Xml.Attribute::getValueAsString)
                .anyMatch(value -> value.contains("org.springframework.retry") ||
                                   value.contains("spring-retry"))) return true;
        return tag.getChildren().stream().anyMatch(FindSpringRetry2013BuildRisks::mentionsSpringRetry);
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

    private record MavenProperties(Map<PropertyKey, Integer> counts, Map<PropertyKey, String> values) {
    }
}
