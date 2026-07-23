package com.huawei.clouds.openrewrite.elasticsearch;

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

/** Upgrade only the workbook-approved Testcontainers Elasticsearch coordinate and source version. */
public final class UpgradeSelectedTestcontainersElasticsearchDependency extends Recipe {
    private static final String PREFIX =
            ElasticsearchUpgradeSupport.GROUP + ":" + ElasticsearchUpgradeSupport.ARTIFACT + ":";

    @Override
    public String getDisplayName() {
        return "Upgrade Testcontainers Elasticsearch 1.17.6 to 1.21.4";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact org.testcontainers:elasticsearch 1.17.6 in unambiguously owned Maven or " +
               "root Gradle declarations; never change org.elasticsearch:elasticsearch or any other version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    ElasticsearchUpgradeSupport.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(ElasticsearchProjectMarker.class).isEmpty()) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document document && "pom.xml".equals(file)) return maven(document, ctx);
                if (tree instanceof G.CompilationUnit groovy && "build.gradle".equals(file)) {
                    return groovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && "build.gradle.kts".equals(file)) {
                    return kotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document source, ExecutionContext ctx) {
        MavenProperties properties = MavenProperties.analyze(source, ctx);

        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (ElasticsearchUpgradeSupport.isMavenPropertyDefinition(getCursor(), visited) &&
                    properties.isSafe(MavenProperties.propertyOwner(getCursor(), visited.getName())) &&
                    visited.getValue().map(String::trim)
                            .filter(ElasticsearchUpgradeSupport.SOURCE::equals).isPresent()) {
                    return visited.withValue(ElasticsearchUpgradeSupport.TARGET);
                }
                if (ElasticsearchUpgradeSupport.isTestcontainersDependency(getCursor(), visited) &&
                    ElasticsearchUpgradeSupport.standardJar(visited) &&
                    visited.getChildValue("version").map(String::trim)
                            .filter(ElasticsearchUpgradeSupport.SOURCE::equals).isPresent()) {
                    return visited.withChildValue("version", ElasticsearchUpgradeSupport.TARGET);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = ElasticsearchUpgradeSupport.isGradleDependencyInvocation(getCursor(), method);
                J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                if (!direct || ElasticsearchUpgradeSupport.hasVariant(visited)) return visited;
                String group = ElasticsearchUpgradeSupport.mapValue(visited, "group");
                String artifact = ElasticsearchUpgradeSupport.mapValue(visited, "name");
                String version = ElasticsearchUpgradeSupport.mapValue(visited, "version");
                if (coordinate(group, artifact) && ElasticsearchUpgradeSupport.SOURCE.equals(version)) {
                    return visited.withArguments(visited.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry &&
                            "version".equals(ElasticsearchUpgradeSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal) {
                            return entry.withValue(ElasticsearchUpgradeSupport.replaceLiteral(
                                    literal, ElasticsearchUpgradeSupport.TARGET));
                        }
                        if (argument instanceof G.MapLiteral map && upgradeable(map)) return upgradeMap(map);
                        return argument;
                    }).toList());
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = ElasticsearchUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = ElasticsearchUpgradeSupport.isDirectDependencyLiteral(getCursor());
                J.Literal visited = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(visited) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean coordinate(String group, String artifact) {
        return ElasticsearchUpgradeSupport.GROUP.equals(group) &&
               ElasticsearchUpgradeSupport.ARTIFACT.equals(artifact);
    }

    private static boolean upgradeable(G.MapLiteral map) {
        return coordinate(ElasticsearchUpgradeSupport.mapValue(map, "group"),
                          ElasticsearchUpgradeSupport.mapValue(map, "name")) &&
               ElasticsearchUpgradeSupport.SOURCE.equals(
                       ElasticsearchUpgradeSupport.mapValue(map, "version")) &&
               !ElasticsearchUpgradeSupport.hasVariant(map);
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(ElasticsearchUpgradeSupport.mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(ElasticsearchUpgradeSupport.replaceLiteral(
                                literal, ElasticsearchUpgradeSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !ElasticsearchUpgradeSupport.GROUP.equals(parts[0]) ||
            !ElasticsearchUpgradeSupport.ARTIFACT.equals(parts[1]) ||
            !ElasticsearchUpgradeSupport.SOURCE.equals(parts[2])) return literal;
        return ElasticsearchUpgradeSupport.replaceLiteral(
                literal, PREFIX + ElasticsearchUpgradeSupport.TARGET);
    }

}
