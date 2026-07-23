package com.huawei.clouds.openrewrite.springexpression;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.UpdateJavaCompatibility;
import org.openrewrite.groovy.GroovyIsoVisitor;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.java.tree.J;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.maven.AddProperty;
import org.openrewrite.maven.UpdateMavenProjectPropertyJavaVersion;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.List;

/**
 * Apply the narrowly reusable official OpenRewrite build recipes only when this
 * project locally owns a selected or target Spring Expression declaration.
 */
public final class ConfigureSpringExpressionBuild extends Recipe {
    @Override
    public String getDisplayName() {
        return "Configure Java 17 and parameter metadata for Spring Expression 6.2";
    }

    @Override
    public String getDescription() {
        return "Conditionally compose official OpenRewrite Maven/Gradle Java compatibility and Maven property " +
               "recipes for locally owned Spring Expression declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringExpressionSupport.generated(source.getSourcePath())) return tree;
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document pom && "pom.xml".equals(fileName) &&
                    ownsSelectedMavenDeclaration(pom, ctx)) {
                    Tree updated = pom;
                    for (Recipe official : officialMavenRecipes()) {
                        updated = official.getVisitor().visitNonNull(updated, ctx);
                    }
                    return updated;
                }
                if (tree instanceof G.CompilationUnit gradle && fileName.endsWith(".gradle") &&
                    ownsSelectedGradleDeclaration(gradle, ctx)) {
                    return updateGradleJava(gradle, ctx);
                }
                if (tree instanceof K.CompilationUnit gradleKts && fileName.endsWith(".gradle.kts") &&
                    ownsSelectedGradleDeclaration(gradleKts, ctx)) {
                    return updateGradleJava(gradleKts, ctx);
                }
                return tree;
            }
        };
    }

    private static Tree updateGradleJava(Tree source, ExecutionContext ctx) {
        Tree updated = source;
        for (Recipe official : officialGradleRecipes()) {
            updated = official.getVisitor().visitNonNull(updated, ctx);
        }
        return updated;
    }

    static List<Recipe> officialMavenRecipes() {
        return List.of(
                new UpdateMavenProjectPropertyJavaVersion(17),
                new AddProperty("maven.compiler.parameters", "true", true, false));
    }

    static List<Recipe> officialGradleRecipes() {
        return List.of(
                new UpdateJavaCompatibility(
                        17, UpdateJavaCompatibility.CompatibilityType.source, null, false, false),
                new UpdateJavaCompatibility(
                        17, UpdateJavaCompatibility.CompatibilityType.target, null, false, false));
    }

    private static boolean ownsSelectedMavenDeclaration(Xml.Document document, ExecutionContext ctx) {
        SpringExpressionSupport.PomProperties properties =
                SpringExpressionSupport.analyzeProperties(document, ctx);
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                if (SpringExpressionSupport.isStandardTargetDependency(getCursor(), t)) {
                    String version = properties.resolveSafePrimary(
                            t.getChildValue("version").orElse(""), getCursor());
                    if (selected(version)) found[0] = true;
                }
                return t;
            }
        }.visitNonNull(document, ctx);
        return found[0];
    }

    private static boolean ownsSelectedGradleDeclaration(G.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new GroovyIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringExpressionSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && selectedGradleInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean ownsSelectedGradleDeclaration(K.CompilationUnit source, ExecutionContext ctx) {
        boolean[] found = {false};
        new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                boolean direct = SpringExpressionSupport.isRootGradleDependency(getCursor(), method);
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (direct && selectedGradleInvocation(m)) found[0] = true;
                return m;
            }
        }.visitNonNull(source, ctx);
        return found[0];
    }

    private static boolean selectedGradleInvocation(J.MethodInvocation invocation) {
        if (SpringExpressionSupport.hasVariant(invocation)) return false;
        String group = SpringExpressionSupport.mapValue(invocation, "group");
        String artifact = SpringExpressionSupport.mapValue(invocation, "name");
        String version = SpringExpressionSupport.mapValue(invocation, "version");
        if (SpringExpressionSupport.GROUP.equals(group) &&
            SpringExpressionSupport.ARTIFACT.equals(artifact) &&
            selected(version)) return true;
        for (J argument : invocation.getArguments()) {
            if (argument instanceof J.Literal literal &&
                literal.getValue() instanceof String coordinate) {
                String[] parts = coordinate.split(":", -1);
                if (parts.length == 3 &&
                    SpringExpressionSupport.GROUP.equals(parts[0]) &&
                    SpringExpressionSupport.ARTIFACT.equals(parts[1]) &&
                    selected(parts[2])) return true;
            }
            if (argument instanceof G.MapLiteral map &&
                !SpringExpressionSupport.hasVariant(map) &&
                SpringExpressionSupport.GROUP.equals(SpringExpressionSupport.mapValue(map, "group")) &&
                SpringExpressionSupport.ARTIFACT.equals(SpringExpressionSupport.mapValue(map, "name")) &&
                selected(SpringExpressionSupport.mapValue(map, "version"))) return true;
        }
        return false;
    }

    private static boolean selected(String version) {
        return version != null && (SpringExpressionSupport.SOURCE_VERSIONS.contains(version) ||
               SpringExpressionSupport.TARGET.equals(version));
    }
}
