package com.huawei.clouds.openrewrite.commonscli;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class CommonsCliRecommendedRecipeTest implements RewriteTest {
    private static final String RECIPE = "com.huawei.clouds.openrewrite.commonscli.MigrateCommonsCliTo1_9_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.commonscli")
                        .build().activateRecipes(RECIPE))
                .parser(JavaParser.fromJavaVersion().classpath("commons-cli"));
    }

    @Test
    void performsDependencySourceAndModuleAutoMigrationsTogether() {
        rewriteRun(
                xml(UpgradeCommonsCliDependencyTest.pom("1.5.0"),
                        UpgradeCommonsCliDependencyTest.pom("1.9.0"), source -> source.path("pom.xml")),
                java(
                        """
                        import org.apache.commons.cli.Option;
                        import org.apache.commons.cli.OptionBuilder;
                        class Cli { Option verbose() { return OptionBuilder.withLongOpt("verbose").create("v"); } }
                        """,
                        """
                        import org.apache.commons.cli.Option;
                        class Cli { Option verbose() { return Option.builder("v").longOpt("verbose").build(); } }
                        """),
                text(
                        """
                        module example.cli {
                            requires commons.cli;
                            requires static java.sql;
                        }
                        """,
                        """
                        module example.cli {
                            requires org.apache.commons.cli;
                            requires static java.sql;
                        }
                        """, source -> source.path("src/main/java/module-info.java")));
    }

    @Test
    void moduleRewritePreservesStaticAndTransitiveModifiersAndSimilarNames() {
        rewriteRun(text(
                """
                module example.cli {
                    requires static commons.cli;
                    requires transitive commons.cli;
                    requires example.commons.cli;
                    requires commons.cli.extra;
                }
                """,
                """
                module example.cli {
                    requires static org.apache.commons.cli;
                    requires transitive org.apache.commons.cli;
                    requires example.commons.cli;
                    requires commons.cli.extra;
                }
                """, source -> source.path("src/main/java/module-info.java")));
    }

    @Test
    void moduleRewriteIgnoresLineBlockCommentsAndOrdinaryLiterals() {
        String source = """
                module example.cli {
                    // requires commons.cli;
                    /*
                    requires commons.cli;
                    */
                    // ordinary documentation: requires commons.cli;
                    provides example.Service with example.ServiceImpl;
                }
                String fixture = "requires commons.cli;";
                """;
        rewriteRun(text(source, spec -> spec.path("src/main/java/module-info.java")));
    }

    @Test
    void moduleRewriteSkipsGeneratedParents() {
        rewriteRun(text("module example { requires commons.cli; }",
                source -> source.path("build/generated/module-info.java")));
    }

    @Test
    void publicUpgradeDoesNotRunSourceOrModuleMigration() {
        var strict = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.commonscli")
                .build().activateRecipes("com.huawei.clouds.openrewrite.commonscli.UpgradeCommonsCliTo1_9_0");
        rewriteRun(spec -> spec.recipe(strict),
                java(
                        """
                        import org.apache.commons.cli.Option;
                        import org.apache.commons.cli.OptionBuilder;
                        class Cli { Option o() { return OptionBuilder.hasArg().create("x"); } }
                        """),
                text("module example { requires commons.cli; }", source -> source.path("src/main/java/module-info.java")));
    }

    @Test
    void recommendedRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeCommonsCliDependencyTest.pom("1.5.0"), UpgradeCommonsCliDependencyTest.pom("1.9.0"),
                        source -> source.path("pom.xml")),
                text("module example { requires commons.cli; }",
                        "module example { requires org.apache.commons.cli; }",
                        source -> source.path("src/main/java/module-info.java")));
    }

    @Test
    void declarationIsDiscoverableAndUsesSafetyOrderedComponents() {
        var recipe = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.commonscli")
                .build().activateRecipes(RECIPE);
        assertEquals(5, recipe.getRecipeList().size());
        assertEquals("com.huawei.clouds.openrewrite.commonscli.UpgradeCommonsCliTo1_9_0", recipe.getRecipeList().get(0).getName());
        assertEquals(MigrateOptionBuilderChains.class, recipe.getRecipeList().get(1).getClass());
        assertEquals(MigrateCommonsCliModuleName.class, recipe.getRecipeList().get(2).getClass());
        assertEquals(FindCommonsCliJavaRisks.class, recipe.getRecipeList().get(3).getClass());
        assertEquals(FindCommonsCliBuildRisks.class, recipe.getRecipeList().get(4).getClass());
    }
}
