package com.huawei.clouds.openrewrite.jakartael;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;

class MigrateJakartaElJavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new MigrateDeterministicJakartaElJava(), new MigrateJavaxElPackage())
                .parser(JavaParser.fromJavaVersion().dependsOn(JakartaElTestApi.legacySources()));
    }

    @Test
    void correctsHistoricMethodSpellingAndMigratesPackage() {
        rewriteRun(java(
                """
                import javax.el.MethodExpression;
                class UsesExpression {
                    boolean parameters(MethodExpression expression) {
                        return expression.isParmetersProvided();
                    }
                }
                """,
                """
                import jakarta.el.MethodExpression;
                class UsesExpression {
                    boolean parameters(MethodExpression expression) {
                        return expression.isParametersProvided();
                    }
                }
                """));
    }

    @Test
    void correctsOverrideDefinitionFromRealZkPattern() {
        rewriteRun(java(
                """
                import javax.el.MethodExpression;
                class MethodExpressionImpl extends MethodExpression {
                    @Override public boolean isParmetersProvided() { return true; }
                }
                """,
                """
                import jakarta.el.MethodExpression;
                class MethodExpressionImpl extends MethodExpression {
                    @Override public boolean isParametersProvided() { return true; }
                }
                """));
    }

    @Test
    void migratesFullyQualifiedAndNestedTypeReferences() {
        rewriteRun(java(
                "class ResolverHolder { javax.el.ELResolver resolver; Class<?> type = javax.el.ExpressionFactory.class; }",
                "class ResolverHolder { jakarta.el.ELResolver resolver; Class<?> type = jakarta.el.ExpressionFactory.class; }"));
    }

    @Test
    void doesNotRenameSameSpellingOnApplicationType() {
        rewriteRun(java("class OwnExpression { boolean isParmetersProvided(){ return false; } void use(){ isParmetersProvided(); } }"));
    }

    @Test
    void targetPackageAndCorrectSpellingAreNoop() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(JakartaElTestApi.targetSources())),
                java("import jakarta.el.MethodExpression; class C { boolean f(MethodExpression e){ return e.isParametersProvided(); } }"));
    }

    @Test
    void generatedSourceIsNoop() {
        rewriteRun(java("import javax.el.MethodExpression; class C { boolean f(MethodExpression e){ return e.isParmetersProvided(); } }",
                source -> source.path("target/generated-sources/C.java")));
    }

    @Test
    void compositionOrderIsStable() {
        MigrateDeterministicJakartaElJava recipe = new MigrateDeterministicJakartaElJava();
        assertEquals(1, recipe.getRecipeList().size());
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import javax.el.MethodExpression; class C { boolean f(MethodExpression e){ return e.isParmetersProvided(); } }",
                "import jakarta.el.MethodExpression; class C { boolean f(MethodExpression e){ return e.isParametersProvided(); } }"));
    }
}
