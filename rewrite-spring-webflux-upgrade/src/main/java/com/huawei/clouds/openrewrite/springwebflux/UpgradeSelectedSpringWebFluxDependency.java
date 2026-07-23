package com.huawei.clouds.openrewrite.springwebflux;

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

/** Upgrade only the exact Spring WebFlux source versions selected by the workbook. */
public final class UpgradeSelectedSpringWebFluxDependency extends Recipe {
    private static final String PREFIX = SpringWebFluxSupport.GROUP + ":" +
                                         SpringWebFluxSupport.ARTIFACT + ":";

    @Override
    public String getDisplayName() {
        return "Upgrade selected Spring WebFlux dependencies to 6.2.19";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact workbook-selected org.springframework:spring-webflux source versions in " +
               "locally owned standard Maven or root Gradle declarations; never downgrade future or outside versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringWebFluxSupport.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && "pom.xml".equals(fileName)) return maven(xml, ctx);
                if (tree instanceof G.CompilationUnit groovy && fileName.endsWith(".gradle")) {
                    return groovy(groovy, ctx);
                }
                if (tree instanceof K.CompilationUnit kotlin && fileName.endsWith(".gradle.kts")) {
                    return kotlin(kotlin, ctx);
                }
                return tree;
            }
        };
    }

    private static Xml.Document maven(Xml.Document document, ExecutionContext ctx) {
        SpringWebFluxSupport.PomProperties properties = SpringWebFluxSupport.analyzeProperties(document, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringWebFluxSupport.isMavenPropertyDefinition(getCursor(), t)) {
                    SpringWebFluxSupport.PropertyKey key =
                            SpringWebFluxSupport.propertyKey(getCursor(), t.getName());
                    if (properties.safe().contains(key) &&
                        t.getValue().map(String::trim)
                                .filter(SpringWebFluxSupport.SOURCE_VERSIONS::contains).isPresent()) {
                        return t.withValue(SpringWebFluxSupport.TARGET);
                    }
                }
                if (SpringWebFluxSupport.isStandardTargetDependency(getCursor(), t) &&
                    t.getChildValue("version").map(String::trim)
                            .filter(SpringWebFluxSupport.SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", SpringWebFluxSupport.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringWebFluxSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (!dependency || SpringWebFluxSupport.hasVariant(m)) return m;
                if (SpringWebFluxSupport.GROUP.equals(SpringWebFluxSupport.mapValue(m, "group")) &&
                    SpringWebFluxSupport.ARTIFACT.equals(SpringWebFluxSupport.mapValue(m, "name")) &&
                    SpringWebFluxSupport.SOURCE_VERSIONS.contains(
                            SpringWebFluxSupport.mapValue(m, "version"))) {
                    return m.withArguments(m.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry &&
                            "version".equals(SpringWebFluxSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal) {
                            return entry.withValue(SpringWebFluxSupport.replaceLiteral(
                                    literal, SpringWebFluxSupport.TARGET));
                        }
                        if (argument instanceof G.MapLiteral map && upgradeableMap(map)) {
                            return upgradeMap(map);
                        }
                        return argument;
                    }).toList());
                }
                return m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringWebFluxSupport.isDirectDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringWebFluxSupport.isDirectDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean upgradeableMap(G.MapLiteral map) {
        return !SpringWebFluxSupport.hasVariant(map) &&
               SpringWebFluxSupport.GROUP.equals(SpringWebFluxSupport.mapValue(map, "group")) &&
               SpringWebFluxSupport.ARTIFACT.equals(SpringWebFluxSupport.mapValue(map, "name")) &&
               SpringWebFluxSupport.SOURCE_VERSIONS.contains(
                       SpringWebFluxSupport.mapValue(map, "version"));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(SpringWebFluxSupport.mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(SpringWebFluxSupport.replaceLiteral(
                                literal, SpringWebFluxSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 ||
            !SpringWebFluxSupport.GROUP.equals(parts[0]) ||
            !SpringWebFluxSupport.ARTIFACT.equals(parts[1]) ||
            !SpringWebFluxSupport.SOURCE_VERSIONS.contains(parts[2])) return literal;
        return SpringWebFluxSupport.replaceLiteral(
                literal, PREFIX + SpringWebFluxSupport.TARGET);
    }
}
