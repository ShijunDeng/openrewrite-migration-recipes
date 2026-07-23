package com.huawei.clouds.openrewrite.springboot;

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

import java.util.List;

/** Upgrade only exact workbook-selected, locally owned Spring Boot version declarations. */
public final class UpgradeSelectedSpringBootVersion extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected Spring Boot owners to 3.5.15";
    }

    @Override
    public String getDescription() {
        return "Upgrade exact workbook source versions in standard Spring Boot dependencies, parent/BOMs, " +
               "and Maven/Gradle plugins while preserving shared, inherited, dynamic, ranged, catalog, variant, " +
               "off-whitelist, target, and newer declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringBootSupport.generated(source.getSourcePath())) return tree;
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
        SpringBootSupport.PomProperties properties = SpringBootSupport.analyzeProperties(document, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringBootSupport.isMavenPropertyDefinition(getCursor(), t)) {
                    SpringBootSupport.PropertyKey key =
                            SpringBootSupport.propertyKey(getCursor(), t.getName());
                    if (properties.safe().contains(key) &&
                        t.getValue().map(String::trim)
                                .filter(SpringBootSupport.SOURCE_VERSIONS::contains).isPresent()) {
                        return t.withValue(SpringBootSupport.TARGET);
                    }
                }
                if (!SpringBootSupport.isBootOwner(getCursor(), t)) return t;
                if (SpringBootSupport.isBootDependency(getCursor(), t) &&
                    !SpringBootSupport.isStandardArtifact(t)) return t;
                if (t.getChildValue("version").map(String::trim)
                        .filter(SpringBootSupport.SOURCE_VERSIONS::contains).isPresent()) {
                    return t.withChildValue("version", SpringBootSupport.TARGET);
                }
                return t;
            }
        }.visitNonNull(document, ctx);
    }

    private static G.CompilationUnit groovy(G.CompilationUnit source, ExecutionContext ctx) {
        return (G.CompilationUnit) new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean dependency = SpringBootSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (SpringBootSupport.isBootGradlePluginVersion(getCursor(), m)) {
                    return upgradeVersionInvocation(m);
                }
                if (!dependency || SpringBootSupport.hasVariant(m)) return m;
                if (SpringBootSupport.GROUP.equals(SpringBootSupport.mapValue(m, "group")) &&
                    SpringBootSupport.ARTIFACT.equals(SpringBootSupport.mapValue(m, "name")) &&
                    SpringBootSupport.SOURCE_VERSIONS.contains(SpringBootSupport.mapValue(m, "version"))) {
                    return m.withArguments(m.getArguments().stream().map(argument -> {
                        if (argument instanceof G.MapEntry entry &&
                            "version".equals(SpringBootSupport.mapKey(entry)) &&
                            entry.getValue() instanceof J.Literal literal) {
                            return entry.withValue(SpringBootSupport.replaceLiteral(
                                    literal, SpringBootSupport.TARGET));
                        }
                        if (argument instanceof G.MapLiteral map && upgradeableMap(map)) return upgradeMap(map);
                        return argument;
                    }).toList());
                }
                return m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringBootSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isRootPlatformLiteral(getCursor());
                boolean buildscriptPlugin =
                        SpringBootSupport.isBuildscriptClasspathLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                if (buildscriptPlugin) return upgradeBuildscriptPluginCoordinate(l);
                return direct || platform ? upgradeCoordinate(l, platform) : l;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                return SpringBootSupport.isBootGradlePluginVersion(getCursor(), m)
                        ? upgradeVersionInvocation(m) : m;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                boolean direct = SpringBootSupport.isDirectDependencyLiteral(getCursor());
                boolean platform = isRootPlatformLiteral(getCursor());
                boolean buildscriptPlugin =
                        SpringBootSupport.isBuildscriptClasspathLiteral(getCursor());
                J.Literal l = super.visitLiteral(literal, ec);
                if (buildscriptPlugin) return upgradeBuildscriptPluginCoordinate(l);
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
               SpringBootSupport.isRootGradleDependency(dependency, invocation);
    }

    private static J.MethodInvocation upgradeVersionInvocation(J.MethodInvocation invocation) {
        if (invocation.getArguments().size() != 1 ||
            !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String version) ||
            !SpringBootSupport.SOURCE_VERSIONS.contains(version)) return invocation;
        return invocation.withArguments(List.of(
                SpringBootSupport.replaceLiteral(literal, SpringBootSupport.TARGET)));
    }

    private static boolean upgradeableMap(G.MapLiteral map) {
        return !SpringBootSupport.hasVariant(map) &&
               SpringBootSupport.GROUP.equals(SpringBootSupport.mapValue(map, "group")) &&
               SpringBootSupport.ARTIFACT.equals(SpringBootSupport.mapValue(map, "name")) &&
               SpringBootSupport.SOURCE_VERSIONS.contains(SpringBootSupport.mapValue(map, "version"));
    }

    private static G.MapLiteral upgradeMap(G.MapLiteral map) {
        return map.withElements(map.getElements().stream().map(entry ->
                "version".equals(SpringBootSupport.mapKey(entry)) &&
                entry.getValue() instanceof J.Literal literal
                        ? entry.withValue(SpringBootSupport.replaceLiteral(
                                literal, SpringBootSupport.TARGET))
                        : entry).toList());
    }

    private static J.Literal upgradeCoordinate(J.Literal literal, boolean allowBom) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String directVersion = SpringBootSupport.coordinateVersion(value, SpringBootSupport.ARTIFACT);
        if (directVersion != null && SpringBootSupport.SOURCE_VERSIONS.contains(directVersion)) {
            return SpringBootSupport.replaceLiteral(literal,
                    SpringBootSupport.GROUP + ":" + SpringBootSupport.ARTIFACT + ":" +
                    SpringBootSupport.TARGET);
        }
        String bomVersion = SpringBootSupport.coordinateVersion(value, SpringBootSupport.BOM);
        if (allowBom && bomVersion != null && SpringBootSupport.SOURCE_VERSIONS.contains(bomVersion)) {
            return SpringBootSupport.replaceLiteral(literal,
                    SpringBootSupport.GROUP + ":" + SpringBootSupport.BOM + ":" +
                    SpringBootSupport.TARGET);
        }
        return literal;
    }

    private static J.Literal upgradeBuildscriptPluginCoordinate(J.Literal literal) {
        if (!(literal.getValue() instanceof String value)) return literal;
        String version = SpringBootSupport.coordinateVersion(
                value, SpringBootSupport.GRADLE_PLUGIN_ARTIFACT);
        if (version == null || !SpringBootSupport.SOURCE_VERSIONS.contains(version)) return literal;
        // Official UpgradeDependencyVersion supports buildscript classpaths, but has no
        // current-version allowlist; using it here would exceed the exact 19-version policy.
        return SpringBootSupport.replaceLiteral(literal,
                SpringBootSupport.GROUP + ":" + SpringBootSupport.GRADLE_PLUGIN_ARTIFACT + ":" +
                SpringBootSupport.TARGET);
    }
}
