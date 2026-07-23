package com.huawei.clouds.openrewrite.junrar;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark dependency ownership, no-downgrade, variant, SLF4J and packaging decisions. */
public final class FindJunrar7510BuildRisks extends Recipe {
    private static final Pattern PROPERTY = Pattern.compile("^\\$\\{([^}]+)}$");

    static final String OWNER =
            "Junrar 版本由缺失版本、父 POM、BOM/platform、共享属性、version catalog、范围、" +
            "动态表达式或插件控制；请修改真实 owner，并确认最终只解析到 7.5.10";
    static final String DOWNGRADE_FORBIDDEN = JunrarSupport.TARGET_CONFLICT;
    static final String VARIANT =
            "该 Junrar 声明含 classifier、非 JAR type、Gradle ext/variant 或四段坐标；" +
            "请确认目标制品形状，配方不会猜测";
    static final String SLF4J =
            "Junrar 7.5.5 的运行时依赖是 slf4j-api 1.7.36，而 7.5.8/7.5.10 是 2.0.17；" +
            "请对齐 API、provider/binding、桥接器和日志初始化，并检查无 provider、重复 provider 与滚动升级";
    static final String PACKAGING =
            "Junrar 被排除、shade/relocate、打入 fat JAR 或作为自动模块使用；" +
            "请在最终制品验证唯一版本、资源、异常类型、automatic module 名称 junrar 和回滚类路径";

    @Override
    public String getDisplayName() {
        return "Find Junrar 7.5.10 build migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved owners, source-version variants, exact no-downgrade conflicts, " +
               "and selected-project SLF4J 1.7/2.0 or packaging boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    JunrarSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) {
                    return maven(document, ctx);
                }
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
        MavenProperties properties = properties(source, ctx);
        if (!hasPrimary(source, ctx)) return source;
        boolean selected = JunrarSupport.selected(source);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (selected && projectPlugin(getCursor(), visited) && packagingPlugin(visited) &&
                    mentionsJunrar(visited, getCursor())) return mark(visited, PACKAGING);
                if (!JunrarSupport.isProjectDependency(getCursor(), visited)) return visited;
                String group = visited.getChildValue("groupId").map(String::trim).orElse("");
                String artifact = visited.getChildValue("artifactId").map(String::trim).orElse("");
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                if (JunrarSupport.GROUP.equals(group) && JunrarSupport.ARTIFACT.equals(artifact)) {
                    String message = primaryMessage(raw, getCursor(), properties,
                            !JunrarSupport.standardArtifact(visited));
                    if (message != null) return markVersion(visited, message);
                    if (selected && visited.getChild("exclusions").isPresent()) {
                        return mark(visited, PACKAGING);
                    }
                    return visited;
                }
                if (selected &&
                    slf4jCompanion(group, artifact, resolve(raw, getCursor(), properties))) {
                    return markVersion(visited, SLF4J);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        if (!mentionsPrimary(source.printAll())) return source;
        boolean selected = JunrarSupport.selected(source);
        G.CompilationUnit result = (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = JunrarSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (selected && "relocate".equals(visited.getSimpleName()) &&
                    visited.printTrimmed(getCursor()).contains("com.github.junrar")) {
                    return mark(visited, PACKAGING);
                }
                if (!direct) return visited;
                String group = JunrarSupport.mapValue(visited, "group");
                String artifact = JunrarSupport.mapValue(visited, "name");
                String version = JunrarSupport.mapValue(visited, "version");
                if (JunrarSupport.GROUP.equals(group) && JunrarSupport.ARTIFACT.equals(artifact)) {
                    String message = primaryMessage(version, JunrarSupport.hasVariant(visited));
                    return message == null ? visited : mark(visited, message);
                }
                if (selected && slf4jCompanion(group, artifact, version)) {
                    return mark(visited, SLF4J);
                }
                String printed = visited.getArguments().isEmpty() ? "" :
                        visited.getArguments().get(0).printTrimmed(getCursor());
                if (printed.contains("libs.") || printed.contains("platform(") ||
                    printed.contains("com.github.junrar") && !literalArguments(visited)) {
                    return mark(visited, OWNER);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = JunrarSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue(), selected) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        String text = source.printAll();
        if (text.contains("libs.junrar") || text.matches("(?s).*com\\.github\\.junrar:junrar:\\$.*")) {
            result = mark(result, OWNER);
        }
        return result;
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        if (!mentionsPrimary(source.printAll())) return source;
        boolean selected = JunrarSupport.selected(source);
        K.CompilationUnit result = (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = JunrarSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct) return visited;
                String printed = visited.printTrimmed(getCursor());
                return printed.contains("libs.") || printed.contains("platform(") ?
                        mark(visited, OWNER) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = JunrarSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue(), selected) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        String text = source.printAll();
        if (text.contains("libs.junrar") || text.matches("(?s).*com\\.github\\.junrar:junrar:\\$.*")) {
            result = mark(result, OWNER);
        }
        return result;
    }

    static String primaryMessage(String version, boolean variant) {
        if (version == null || version.isBlank() || !JunrarSupport.FIXED.matcher(version).matches()) {
            return OWNER;
        }
        if (JunrarSupport.higherThanTarget(version)) return DOWNGRADE_FORBIDDEN;
        if (JunrarSupport.SOURCES.contains(version)) return variant ? VARIANT : null;
        return null;
    }

    private static String primaryMessage(
            String raw, Cursor cursor, MavenProperties properties, boolean variant) {
        if (raw == null || raw.isBlank()) return OWNER;
        Matcher property = PROPERTY.matcher(raw);
        if (!property.matches()) return primaryMessage(raw, variant);
        PropertyOwner owner = resolveOwner(cursor, property.group(1), properties);
        if (owner == null || properties.definitions().getOrDefault(owner, 0) != 1) {
            return OWNER;
        }
        String resolved = properties.values().get(owner);
        if (JunrarSupport.SOURCES.contains(resolved) &&
            (properties.allReferences().getOrDefault(owner, 0)
                    .intValue() != properties.targetReferences().getOrDefault(owner, 0) ||
             "ROOT".equals(owner.scope()) && properties.profileNames().contains(owner.name()))) {
            return OWNER;
        }
        return primaryMessage(resolved, variant);
    }

    private static String coordinateMessage(Object value, boolean selected) {
        if (!(value instanceof String coordinate)) return null;
        int at = coordinate.indexOf('@');
        String plain = at < 0 ? coordinate : coordinate.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2) return null;
        if (JunrarSupport.GROUP.equals(parts[0]) && JunrarSupport.ARTIFACT.equals(parts[1])) {
            if (parts.length < 3) return OWNER;
            return primaryMessage(parts[2], parts.length != 3 || at >= 0);
        }
        if (selected && parts.length == 3 &&
            slf4jCompanion(parts[0], parts[1], parts[2])) return SLF4J;
        return null;
    }

    private static boolean slf4jCompanion(String group, String artifact, String version) {
        if (group == null || artifact == null) return false;
        if ("org.slf4j".equals(group)) {
            if ("slf4j-api".equals(artifact)) return version == null || !version.startsWith("2.0.");
            return version == null || version.startsWith("1.") || artifact.endsWith("12") ||
                   artifact.contains("log4j12") || artifact.contains("jdk14");
        }
        return "ch.qos.logback".equals(group) && "logback-classic".equals(artifact) &&
               (version == null || version.startsWith("1.0.") || version.startsWith("1.1.") ||
                version.startsWith("1.2."));
    }

    private static boolean literalArguments(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().allMatch(argument ->
                argument instanceof J.Literal || argument instanceof G.MapEntry ||
                argument instanceof G.MapLiteral);
    }

    private static boolean mentionsPrimary(String text) {
        return text.contains(JunrarSupport.GROUP + ":" + JunrarSupport.ARTIFACT) ||
               text.contains("libs.junrar");
    }

    private static boolean hasPrimary(Xml.Document source, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (JunrarSupport.isTargetDependency(getCursor(), visited)) found[0] = true;
                return visited;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean projectPlugin(Cursor cursor, Xml.Tag tag) {
        if (!"plugin".equals(tag.getName())) return false;
        Cursor plugins = cursor.getParentTreeCursor();
        if (!(plugins.getValue() instanceof Xml.Tag p) || !"plugins".equals(p.getName())) return false;
        for (Cursor current = plugins; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag owner &&
                ("project".equals(owner.getName()) || "profile".equals(owner.getName()))) return true;
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static boolean packagingPlugin(Xml.Tag plugin) {
        String artifact = plugin.getChildValue("artifactId").orElse("");
        return Set.of("maven-shade-plugin", "maven-assembly-plugin", "bnd-maven-plugin",
                "spring-boot-maven-plugin").contains(artifact);
    }

    private static boolean mentionsJunrar(Xml.Tag tag, Cursor cursor) {
        String printed = tag.printTrimmed(cursor);
        return printed.contains("com.github.junrar") || printed.contains("junrar");
    }

    private static MavenProperties properties(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyOwner, Integer> definitions = new HashMap<>();
        Map<PropertyOwner, String> values = new HashMap<>();
        Set<String> profileNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (JunrarSupport.isPropertyDefinition(getCursor(), visited)) {
                    PropertyOwner owner = propertyOwner(getCursor(), visited.getName());
                    definitions.merge(owner, 1, Integer::sum);
                    visited.getValue().ifPresent(value -> values.put(owner, value.trim()));
                    if (!"ROOT".equals(owner.scope())) profileNames.add(owner.name());
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        Map<PropertyOwner, Integer> all = new HashMap<>();
        Map<PropertyOwner, Integer> target = new HashMap<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData data, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(data, ec);
                Matcher matcher = Pattern.compile("\\$\\{([^}]+)}").matcher(visited.getText());
                while (matcher.find()) {
                    PropertyOwner owner = resolveOwner(getCursor(), matcher.group(1),
                            new MavenProperties(definitions, values, Map.of(), Map.of(), profileNames));
                    if (owner != null) {
                        all.merge(owner, 1, Integer::sum);
                        Cursor parent = getCursor().getParentTreeCursor();
                        if (parent.getValue() instanceof Xml.Tag version && "version".equals(version.getName())) {
                            Cursor dependency = parent.getParentTreeCursor();
                            if (dependency.getValue() instanceof Xml.Tag dep &&
                                JunrarSupport.isTargetDependency(dependency, dep) &&
                                JunrarSupport.standardArtifact(dep)) target.merge(owner, 1, Integer::sum);
                        }
                    }
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
        return new MavenProperties(definitions, values, all, target, profileNames);
    }

    private static String resolve(String raw, Cursor cursor, MavenProperties properties) {
        if (raw == null) return null;
        Matcher matcher = PROPERTY.matcher(raw);
        if (!matcher.matches()) return raw;
        PropertyOwner owner = resolveOwner(cursor, matcher.group(1), properties);
        return owner != null && properties.definitions().getOrDefault(owner, 0) == 1 ?
                properties.values().get(owner) : null;
    }

    private static PropertyOwner resolveOwner(Cursor cursor, String name, MavenProperties properties) {
        String profile = profileId(cursor);
        PropertyOwner local = profile == null ? null : new PropertyOwner(profile, name);
        if (local != null && properties.definitions().containsKey(local)) return local;
        PropertyOwner root = new PropertyOwner("ROOT", name);
        return properties.definitions().containsKey(root) ? root : null;
    }

    private static PropertyOwner propertyOwner(Cursor cursor, String name) {
        String profile = profileId(cursor);
        return new PropertyOwner(profile == null ? "ROOT" : profile, name);
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

    private static Xml.Tag markVersion(Xml.Tag dependency, String message) {
        return dependency.getChild("version").map(version -> {
            Xml.Tag marked = mark(version, message);
            return marked == version ? dependency : dependency.withContent(dependency.getContent().stream()
                    .map(content -> content == version ? marked : content).toList());
        }).orElseGet(() -> mark(dependency, message));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        if (tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))) return tree;
        return SearchResult.found(tree, message);
    }

    private record PropertyOwner(String scope, String name) {
    }

    private record MavenProperties(
            Map<PropertyOwner, Integer> definitions,
            Map<PropertyOwner, String> values,
            Map<PropertyOwner, Integer> allReferences,
            Map<PropertyOwner, Integer> targetReferences,
            Set<String> profileNames) {
    }
}
