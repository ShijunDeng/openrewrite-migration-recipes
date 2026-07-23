package com.huawei.clouds.openrewrite.springweb;

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

/** Upgrade only exact Spring Web source versions selected by the workbook. */
public final class UpgradeSelectedSpringWebDependency extends Recipe {
    private static final String PREFIX = SpringWebSupport.GROUP + ":" + SpringWebSupport.ARTIFACT + ":";

    @Override
    public String getDisplayName() {
        return "Upgrade selected Spring Web dependencies to 6.2.19";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact workbook-selected org.springframework:spring-web source versions in " +
               "locally owned standard Maven or root Gradle declarations; never downgrade future or outside versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || SpringWebSupport.generated(source.getSourcePath())) {
                    return tree;
                }
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
        SpringWebSupport.PomProperties properties = SpringWebSupport.analyzeProperties(document, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringWebSupport.isMavenPropertyDefinition(getCursor(), t)) {
                    SpringWebSupport.PropertyKey key = SpringWebSupport.propertyKey(getCursor(), t.getName());
                    if (properties.safe().contains(key) &&
                        t.getValue().map(String::trim).filter(SpringWebSupport.SOURCE_VERSIONS::contains).isPresent()) {
                        return t.withValue(SpringWebSupport.TARGET);
                    }
                }
                if (SpringWebSupport.isStandardTargetDependency(getCursor(), t) &&
                    t.getChildValue("version").map(String::trim)
                            .filter(SpringWebSupport.SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", SpringWebSupport.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringWebSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (!dependency || SpringWebSupport.hasVariant(m)) return m;
                if (SpringWebSupport.GROUP.equals(SpringWebSupport.mapValue(m, "group")) &&
                    SpringWebSupport.ARTIFACT.equals(SpringWebSupport.mapValue(m, "name")) &&
                    SpringWebSupport.SOURCE_VERSIONS.contains(SpringWebSupport.mapValue(m, "version"))) {
                    return m.withArguments(m.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry &&
                            "version".equals(SpringWebSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal) {
                            return entry.withValue(SpringWebSupport.replaceLiteral(literal, SpringWebSupport.TARGET));
                        }
                        if (argument instanceof G.MapLiteral map && upgradeableMap(map)) return upgradeMap(map);
                        return argument;
                    }).toList());
                }
                return m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringWebSupport.isDirectDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringWebSupport.isDirectDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean upgradeableMap(G.MapLiteral map) {
        return !SpringWebSupport.hasVariant(map) &&
               SpringWebSupport.GROUP.equals(SpringWebSupport.mapValue(map, "group")) &&
               SpringWebSupport.ARTIFACT.equals(SpringWebSupport.mapValue(map, "name")) &&
               SpringWebSupport.SOURCE_VERSIONS.contains(SpringWebSupport.mapValue(map, "version"));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(SpringWebSupport.mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(SpringWebSupport.replaceLiteral(literal, SpringWebSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !SpringWebSupport.GROUP.equals(parts[0]) ||
            !SpringWebSupport.ARTIFACT.equals(parts[1]) ||
            !SpringWebSupport.SOURCE_VERSIONS.contains(parts[2])) return literal;
        return SpringWebSupport.replaceLiteral(literal, PREFIX + SpringWebSupport.TARGET);
    }
}
