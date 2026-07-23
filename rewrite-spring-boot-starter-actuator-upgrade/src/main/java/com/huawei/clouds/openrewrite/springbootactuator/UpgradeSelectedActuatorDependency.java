package com.huawei.clouds.openrewrite.springbootactuator;

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

/** Upgrade only workbook-selected Actuator starter versions and their unambiguous local Boot owner. */
public final class UpgradeSelectedActuatorDependency extends Recipe {
    private static final String STARTER_PREFIX = SpringBootActuatorSupport.GROUP + ":" +
                                                 SpringBootActuatorSupport.ARTIFACT + ":";
    private static final String BOM_PREFIX = SpringBootActuatorSupport.GROUP + ":" +
                                             SpringBootActuatorSupport.BOM + ":";

    @Override
    public String getDisplayName() {
        return "Upgrade selected Spring Boot Actuator starter declarations to 3.5.15";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact workbook-selected starter versions and locally owned Boot parent/BOM versions; " +
               "preserve variants, shared properties, external owners, ranges, catalogs, and newer release lines.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringBootActuatorSupport.generated(source.getSourcePath())) return tree;
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
        boolean hasStarter = SpringBootActuatorSupport.containsTargetStarter(document, ctx);
        SpringBootActuatorSupport.PomProperties properties =
                SpringBootActuatorSupport.analyzeProperties(document, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringBootActuatorSupport.isMavenPropertyDefinition(getCursor(), t)) {
                    SpringBootActuatorSupport.PropertyKey key =
                            SpringBootActuatorSupport.propertyKey(getCursor(), t.getName());
                    if (properties.safe().contains(key) &&
                        t.getValue().map(String::trim)
                                .filter(SpringBootActuatorSupport.SOURCE_VERSIONS::contains).isPresent()) {
                        return t.withValue(SpringBootActuatorSupport.TARGET);
                    }
                }
                if (SpringBootActuatorSupport.isTargetDependency(getCursor(), t) &&
                    SpringBootActuatorSupport.isStandardArtifact(t) &&
                    t.getChildValue("version").map(String::trim)
                            .filter(SpringBootActuatorSupport.SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", SpringBootActuatorSupport.TARGET);
                }
                if (hasStarter &&
                    (SpringBootActuatorSupport.isBootParent(getCursor(), t) ||
                     SpringBootActuatorSupport.isBootBom(getCursor(), t)) &&
                    t.getChildValue("version").map(String::trim)
                            .filter(SpringBootActuatorSupport.SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", SpringBootActuatorSupport.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        boolean hasStarter = source.printAll().contains(
                SpringBootActuatorSupport.GROUP + ":" + SpringBootActuatorSupport.ARTIFACT);
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringBootActuatorSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (!dependency || SpringBootActuatorSupport.hasVariant(m)) return m;
                if (SpringBootActuatorSupport.GROUP.equals(SpringBootActuatorSupport.mapValue(m, "group")) &&
                    SpringBootActuatorSupport.ARTIFACT.equals(SpringBootActuatorSupport.mapValue(m, "name")) &&
                    SpringBootActuatorSupport.SOURCE_VERSIONS.contains(
                            SpringBootActuatorSupport.mapValue(m, "version"))) {
                    return m.withArguments(m.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry &&
                            "version".equals(SpringBootActuatorSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal) {
                            return entry.withValue(SpringBootActuatorSupport.replaceLiteral(
                                    literal, SpringBootActuatorSupport.TARGET));
                        }
                        if (argument instanceof G.MapLiteral map && upgradeableMap(map)) return upgradeMap(map);
                        return argument;
                    }).toList());
                }
                return m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringBootActuatorSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = hasStarter && isRootPlatformLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                return direct || platform ? upgradeCoordinate(l, platform) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean hasStarter = source.printAll().contains(
                SpringBootActuatorSupport.GROUP + ":" + SpringBootActuatorSupport.ARTIFACT);
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringBootActuatorSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = hasStarter && isRootPlatformLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                return direct || platform ? upgradeCoordinate(l, platform) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean isRootPlatformLiteral(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        if (!(parent.getValue() instanceof J.MethodInvocation platform) ||
            !("platform".equals(platform.getSimpleName()) ||
              "enforcedPlatform".equals(platform.getSimpleName()))) return false;
        Cursor dependency = parent.getParentTreeCursor();
        return dependency.getValue() instanceof J.MethodInvocation invocation &&
               SpringBootActuatorSupport.isRootGradleDependency(dependency, invocation);
    }

    private static boolean upgradeableMap(G.MapLiteral map) {
        return !SpringBootActuatorSupport.hasVariant(map) &&
               SpringBootActuatorSupport.GROUP.equals(SpringBootActuatorSupport.mapValue(map, "group")) &&
               SpringBootActuatorSupport.ARTIFACT.equals(SpringBootActuatorSupport.mapValue(map, "name")) &&
               SpringBootActuatorSupport.SOURCE_VERSIONS.contains(
                       SpringBootActuatorSupport.mapValue(map, "version"));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(SpringBootActuatorSupport.mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(SpringBootActuatorSupport.replaceLiteral(
                                literal, SpringBootActuatorSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal, boolean allowBom) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !SpringBootActuatorSupport.GROUP.equals(parts[0]) ||
            !SpringBootActuatorSupport.SOURCE_VERSIONS.contains(parts[2])) return literal;
        if (SpringBootActuatorSupport.ARTIFACT.equals(parts[1])) {
            return SpringBootActuatorSupport.replaceLiteral(
                    literal, STARTER_PREFIX + SpringBootActuatorSupport.TARGET);
        }
        if (allowBom && SpringBootActuatorSupport.BOM.equals(parts[1])) {
            return SpringBootActuatorSupport.replaceLiteral(
                    literal, BOM_PREFIX + SpringBootActuatorSupport.TARGET);
        }
        return literal;
    }
}
