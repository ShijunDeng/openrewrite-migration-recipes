package com.huawei.clouds.openrewrite.elasticsearch;

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

/** Locate coordinate identity, dependency ownership, version and variant decisions. */
public final class FindElasticsearchBuildRisks extends Recipe {
    private static final Pattern PROPERTY = Pattern.compile("^\\$\\{([^}]+)}$");
    static final String OWNER =
            "Testcontainers Elasticsearch 版本由缺失版本、父 POM、BOM/platform、共享属性、version catalog、" +
            "动态表达式或插件控制；请修改真实 owner，并确认最终解析为 org.testcontainers:elasticsearch:1.21.4";
    static final String OUTSIDE =
            "该固定 org.testcontainers:elasticsearch 版本不是自动白名单 1.17.6 或目标 1.21.4；" +
            "配方保持原值，需先提供该版本到 1.21.4 的升级证据";
    static final String VARIANT =
            "该 org.testcontainers:elasticsearch 声明含 classifier、非 JAR type 或 Gradle variant；" +
            "请人工确认制品形状";
    static final String IDENTITY_CONFLICT =
            "组件身份冲突（禁止跨组件改写）：org.elasticsearch:elasticsearch 7.9.3/7.10.2 是 " +
            "Elasticsearch Server；目标 1.21.4 属于 org.testcontainers:elasticsearch，配方保持原值";
    static final String SERVER_IDENTITY_BOUNDARY =
            "组件身份边界：org.elasticsearch:elasticsearch 是 Elasticsearch Server，" +
            "org.testcontainers:elasticsearch:1.21.4 不是它的升级版本；配方保持原值";

    @Override
    public String getDisplayName() {
        return "Find Testcontainers Elasticsearch 1.21.4 build risks";
    }

    @Override
    public String getDescription() {
        return "Mark component identity conflicts, no-downgrade conflicts, unsupported versions, unresolved " +
               "owners and artifact variants without changing their source text.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    ElasticsearchUpgradeSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && file.endsWith(".gradle")) return groovy(groovy, ctx);
                if (tree instanceof K.CompilationUnit kotlin && file.endsWith(".gradle.kts")) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        BuildMavenProperties properties = properties(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                boolean testcontainers =
                        ElasticsearchUpgradeSupport.isTestcontainersDependency(getCursor(), visited);
                boolean server =
                        ElasticsearchUpgradeSupport.isElasticsearchServerDependency(getCursor(), visited);
                if (!testcontainers && !server) return visited;
                if (testcontainers && !ElasticsearchUpgradeSupport.standardJar(visited)) {
                    return mark(visited, VARIANT);
                }
                String raw = visited.getChildValue("version").map(String::trim).orElse(null);
                String version = resolve(raw, getCursor(), properties);
                String message = testcontainers ? primaryMessage(version) : serverMessage(version);
                return message == null ? visited : markVersion(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        G.CompilationUnit result = (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = ElasticsearchUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct) return visited;
                String group = ElasticsearchUpgradeSupport.mapValue(visited, "group");
                String artifact = ElasticsearchUpgradeSupport.mapValue(visited, "name");
                String version = ElasticsearchUpgradeSupport.mapValue(visited, "version");
                if (coordinate(group, artifact, ElasticsearchUpgradeSupport.GROUP,
                        ElasticsearchUpgradeSupport.ARTIFACT)) {
                    if (ElasticsearchUpgradeSupport.hasVariant(visited)) return mark(visited, VARIANT);
                    String message = primaryMessage(version);
                    return message == null ? visited : mark(visited, message);
                }
                if (coordinate(group, artifact, ElasticsearchUpgradeSupport.SERVER_GROUP,
                        ElasticsearchUpgradeSupport.SERVER_ARTIFACT)) {
                    return mark(visited, serverMessage(version));
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = ElasticsearchUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        return unresolvedGradleOwner(source.printAll()) ? mark(result, OWNER) : result;
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        K.CompilationUnit result = (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = ElasticsearchUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                String message = direct ? coordinateMessage(visited.getValue()) : null;
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
        return unresolvedGradleOwner(source.printAll()) ? mark(result, OWNER) : result;
    }

    static String primaryMessage(String version) {
        if (version == null || version.isBlank()) return OWNER;
        if (ElasticsearchUpgradeSupport.SOURCE.equals(version) ||
            ElasticsearchUpgradeSupport.TARGET.equals(version)) return null;
        if (ElasticsearchUpgradeSupport.targetConflict(version)) {
            return ElasticsearchUpgradeSupport.TARGET_CONFLICT;
        }
        return ElasticsearchUpgradeSupport.fixedVersion(version) ? OUTSIDE : OWNER;
    }

    static String serverMessage(String version) {
        return ElasticsearchUpgradeSupport.SERVER_WORKBOOK_VERSIONS.contains(version)
                ? IDENTITY_CONFLICT : SERVER_IDENTITY_BOUNDARY;
    }

    private static String coordinateMessage(Object value) {
        if (!(value instanceof String coordinate)) return null;
        int at = coordinate.indexOf('@');
        String plain = at < 0 ? coordinate : coordinate.substring(0, at);
        String[] parts = plain.split(":", -1);
        if (parts.length < 2) return null;
        if (coordinate(parts[0], parts[1], ElasticsearchUpgradeSupport.GROUP,
                ElasticsearchUpgradeSupport.ARTIFACT)) {
            if (parts.length != 3 || at >= 0) return at >= 0 ? VARIANT : OWNER;
            return primaryMessage(parts[2]);
        }
        if (coordinate(parts[0], parts[1], ElasticsearchUpgradeSupport.SERVER_GROUP,
                ElasticsearchUpgradeSupport.SERVER_ARTIFACT)) {
            return serverMessage(parts.length == 3 ? parts[2] : null);
        }
        return null;
    }

    private static boolean coordinate(String group, String artifact, String expectedGroup,
                                      String expectedArtifact) {
        return expectedGroup.equals(group) && expectedArtifact.equals(artifact);
    }

    private static boolean unresolvedGradleOwner(String text) {
        return text.contains("libs.testcontainers.elasticsearch") ||
               text.contains("org.testcontainers:elasticsearch:$") ||
               text.contains("org.testcontainers:elasticsearch:${") ||
               text.matches("(?s).*org\\.testcontainers:elasticsearch:[+\\[].*") ||
               text.contains("platform(") && text.contains("org.testcontainers");
    }

    private static BuildMavenProperties properties(Xml.Document source, ExecutionContext ctx) {
        Map<PropertyKey, Integer> counts = new HashMap<>();
        Map<PropertyKey, String> values = new HashMap<>();
        Set<String> profilePropertyNames = new HashSet<>();
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (ElasticsearchUpgradeSupport.isMavenPropertyDefinition(getCursor(), visited)) {
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
                        elasticsearchVersionReference(getCursor(), visited.getText())
                                ? ownedReferences : null);
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                collectReferences(visited.getValueAsString(), getCursor(), counts, references, null);
                return visited;
            }
        }.visitNonNull(source, ctx);
        return new BuildMavenProperties(
                counts, values, references, ownedReferences, profilePropertyNames);
    }

    private static String resolve(String raw, Cursor cursor,
                                  BuildMavenProperties properties) {
        if (raw == null || raw.isBlank()) return null;
        Matcher matcher = PROPERTY.matcher(raw);
        if (!matcher.matches()) return raw;
        PropertyKey owner = resolvedOwner(cursor, matcher.group(1), properties.counts());
        boolean exclusive = properties.counts().getOrDefault(owner, 0) == 1 &&
                            properties.references().getOrDefault(owner, 0)
                                    .equals(properties.ownedReferences().getOrDefault(owner, 0)) &&
                            (!"ROOT".equals(owner.scope()) ||
                             !properties.profilePropertyNames().contains(owner.name()));
        return exclusive ? properties.values().get(owner) : null;
    }

    private static boolean elasticsearchVersionReference(Cursor cursor, String text) {
        if (!PROPERTY.matcher(text.trim()).matches()) return false;
        Cursor versionCursor = cursor.getParentTreeCursor();
        if (!(versionCursor.getValue() instanceof Xml.Tag version) || !"version".equals(version.getName())) {
            return false;
        }
        Cursor dependencyCursor = versionCursor.getParentTreeCursor();
        if (!(dependencyCursor.getValue() instanceof Xml.Tag dependency)) return false;
        return ElasticsearchUpgradeSupport.isElasticsearchServerDependency(
                       dependencyCursor, dependency) ||
               ElasticsearchUpgradeSupport.isTestcontainersDependency(
                       dependencyCursor, dependency) &&
               ElasticsearchUpgradeSupport.standardJar(dependency);
    }

    private static void collectReferences(String text, Cursor cursor, Map<PropertyKey, Integer> definitions,
                                          Map<PropertyKey, Integer> references,
                                          Map<PropertyKey, Integer> ownedReferences) {
        Matcher matcher = Pattern.compile("\\$\\{([^}]+)}").matcher(text);
        while (matcher.find()) {
            PropertyKey owner = resolvedOwner(cursor, matcher.group(1), definitions);
            references.merge(owner, 1, Integer::sum);
            if (ownedReferences != null) ownedReferences.merge(owner, 1, Integer::sum);
        }
    }

    private static PropertyKey resolvedOwner(Cursor cursor, String name,
                                             Map<PropertyKey, Integer> definitions) {
        PropertyKey local = new PropertyKey(scope(cursor), name);
        return !"ROOT".equals(local.scope()) && definitions.containsKey(local)
                ? local : new PropertyKey("ROOT", name);
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

    private record BuildMavenProperties(
            Map<PropertyKey, Integer> counts,
            Map<PropertyKey, String> values,
            Map<PropertyKey, Integer> references,
            Map<PropertyKey, Integer> ownedReferences,
            Set<String> profilePropertyNames) {
    }
}
