package com.huawei.clouds.openrewrite.springexpression;

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

/** Upgrade only the 17 exact Spring Expression source versions selected by the workbook. */
public final class UpgradeSelectedSpringExpressionDependency extends Recipe {
    private static final String PREFIX =
            SpringExpressionSupport.GROUP + ":" + SpringExpressionSupport.ARTIFACT + ":";

    @Override
    public String getDisplayName() {
        return "Upgrade selected Spring Expression dependencies to 6.2.19";
    }

    @Override
    public String getDescription() {
        return "Upgrade only the 17 exact workbook-selected org.springframework:spring-expression source " +
               "versions in locally owned standard Maven or root Gradle declarations; never downgrade other versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringExpressionSupport.generated(source.getSourcePath())) return tree;
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
        SpringExpressionSupport.PomProperties properties =
                SpringExpressionSupport.analyzeProperties(document, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringExpressionSupport.isMavenPropertyDefinition(getCursor(), t)) {
                    SpringExpressionSupport.PropertyKey key =
                            SpringExpressionSupport.propertyKey(getCursor(), t.getName());
                    if (properties.safe().contains(key) &&
                        t.getValue().map(String::trim)
                                .filter(SpringExpressionSupport.SOURCE_VERSIONS::contains).isPresent()) {
                        return t.withValue(SpringExpressionSupport.TARGET);
                    }
                }
                if (SpringExpressionSupport.isStandardTargetDependency(getCursor(), t) &&
                    t.getChildValue("version").map(String::trim)
                            .filter(SpringExpressionSupport.SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", SpringExpressionSupport.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringExpressionSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (!dependency || SpringExpressionSupport.hasVariant(m)) return m;
                if (SpringExpressionSupport.GROUP.equals(SpringExpressionSupport.mapValue(m, "group")) &&
                    SpringExpressionSupport.ARTIFACT.equals(SpringExpressionSupport.mapValue(m, "name")) &&
                    SpringExpressionSupport.SOURCE_VERSIONS.contains(
                            SpringExpressionSupport.mapValue(m, "version"))) {
                    return m.withArguments(m.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry &&
                            "version".equals(SpringExpressionSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal) {
                            return entry.withValue(SpringExpressionSupport.replaceLiteral(
                                    literal, SpringExpressionSupport.TARGET));
                        }
                        if (argument instanceof G.MapLiteral map && upgradeableMap(map)) return upgradeMap(map);
                        return argument;
                    }).toList());
                }
                return m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringExpressionSupport.isDirectDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringExpressionSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (!dependency || SpringExpressionSupport.hasVariant(m) ||
                    !SpringExpressionSupport.GROUP.equals(SpringExpressionSupport.mapValue(m, "group")) ||
                    !SpringExpressionSupport.ARTIFACT.equals(SpringExpressionSupport.mapValue(m, "name")) ||
                    !SpringExpressionSupport.SOURCE_VERSIONS.contains(
                            SpringExpressionSupport.mapValue(m, "version"))) return m;
                return m.withArguments(m.getArguments().stream().map(argument -> {
                    if (argument instanceof J.Assignment assignment &&
                        "version".equals(SpringExpressionSupport.assignmentKey(assignment)) &&
                        assignment.getAssignment() instanceof J.Literal literal) {
                        return assignment.withAssignment(SpringExpressionSupport.replaceLiteral(
                                literal, SpringExpressionSupport.TARGET));
                    }
                    return argument;
                }).toList());
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringExpressionSupport.isDirectDependencyLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                return direct ? upgradeCoordinate(l) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean upgradeableMap(G.MapLiteral map) {
        return !SpringExpressionSupport.hasVariant(map) &&
               SpringExpressionSupport.GROUP.equals(SpringExpressionSupport.mapValue(map, "group")) &&
               SpringExpressionSupport.ARTIFACT.equals(SpringExpressionSupport.mapValue(map, "name")) &&
               SpringExpressionSupport.SOURCE_VERSIONS.contains(
                       SpringExpressionSupport.mapValue(map, "version"));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(SpringExpressionSupport.mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(SpringExpressionSupport.replaceLiteral(
                                literal, SpringExpressionSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 ||
            !SpringExpressionSupport.GROUP.equals(parts[0]) ||
            !SpringExpressionSupport.ARTIFACT.equals(parts[1]) ||
            !SpringExpressionSupport.SOURCE_VERSIONS.contains(parts[2])) return literal;
        return SpringExpressionSupport.replaceLiteral(
                literal, PREFIX + SpringExpressionSupport.TARGET);
    }
}
