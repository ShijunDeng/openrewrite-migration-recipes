package com.huawei.clouds.openrewrite.jakartael;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedJakartaElMigrationTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.jakartael.MigrateJakartaElApiTo6_0_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECOMMENDED))
                .parser(JavaParser.fromJavaVersion().dependsOn(JakartaElTestApi.legacySources()));
    }

    @Test
    void migratesAWholeProjectInProductionOrder() {
        rewriteRun(
                xml(UpgradeJakartaElApiDependencyTest.pom("3.0.3"),
                        UpgradeJakartaElApiDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                java(
                        """
                        import javax.el.MethodExpression;
                        class Expressions { boolean parameters(MethodExpression expression) {
                            return expression.isParmetersProvided();
                        } }
                        """,
                        """
                        import jakarta.el.MethodExpression;
                        class Expressions { boolean parameters(MethodExpression expression) {
                            return expression.isParametersProvided();
                        } }
                        """),
                text("javax.el.ExpressionFactory\n", "jakarta.el.ExpressionFactory\n", source -> source
                        .path("src/main/resources/META-INF/services/javax.el.ExpressionFactory")
                        .afterRecipe(after -> assertEquals(
                                "src/main/resources/META-INF/services/jakarta.el.ExpressionFactory",
                                after.getSourcePath().toString()))),
                text("module example.app { requires java.el; }",
                        "module example.app { requires jakarta.el; }",
                        source -> source.path("src/main/java/module-info.java")));
    }

    @Test
    void targetProjectIsNoop() {
        rewriteRun(
                xml(UpgradeJakartaElApiDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                text("module example.app { requires jakarta.el; }",
                        source -> source.path("src/main/java/module-info.java")));
    }

    @Test
    void publicRecipeOrderKeepsAutomaticChangesBeforeMarkers() {
        Recipe recipe = environment().activateRecipes(RECOMMENDED);
        List<String> names = recipe.getRecipeList().stream().map(Recipe::getName).toList();
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.jakartael.UpgradeJakartaElApiTo6_0_1",
                "com.huawei.clouds.openrewrite.jakartael.MigrateDeterministicJakartaElJava",
                "com.huawei.clouds.openrewrite.jakartael.MigrateJavaxElPackage",
                "com.huawei.clouds.openrewrite.jakartael.MigrateJavaxElReferences",
                "com.huawei.clouds.openrewrite.jakartael.MigrateJakartaElModuleName",
                "com.huawei.clouds.openrewrite.jakartael.FindJakartaEl6BuildRisks",
                "com.huawei.clouds.openrewrite.jakartael.FindJakartaEl6SourceRisks"), names);
    }

    @Test
    void wholeRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeJakartaElApiDependencyTest.pom("5.0.1"),
                        UpgradeJakartaElApiDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                text("module example { requires javax.el; }", "module example { requires jakarta.el; }",
                        source -> source.path("module-info.java")));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.jakartael").build();
    }
}
