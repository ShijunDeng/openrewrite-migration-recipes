package com.huawei.clouds.openrewrite.log4jcore;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class Log4jCoreRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.log4jcore.MigrateLog4jCoreTo2_25_5";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECOMMENDED));
    }

    @Test
    void publicRecipeHasSafetyOrderedStages() {
        assertEquals(List.of(
                        "com.huawei.clouds.openrewrite.log4jcore.UpgradeLog4jCoreTo2_25_5",
                        "com.huawei.clouds.openrewrite.log4jcore.MigrateLog4jCore25",
                        "com.huawei.clouds.openrewrite.log4jcore.FindLog4jCore25BuildRisks",
                        "com.huawei.clouds.openrewrite.log4jcore.FindLog4jCore25SourceRisks",
                        "com.huawei.clouds.openrewrite.log4jcore.FindLog4jCore25ConfigurationRisks"),
                environment().activateRecipes(RECOMMENDED).getRecipeList().stream()
                        .map(recipe -> recipe.getName()).toList());
    }

    @Test
    void recipeDescriptorIsDiscoverableFromRuntimeClasspath() {
        assertEquals(RECOMMENDED, environment().activateRecipes(RECOMMENDED).getName());
    }

    @Test
    void recommendedRecipeUpgradesAutoMigratesThenMarksRuntimeDecision() {
        rewriteRun(
                xml(UpgradeLog4jCoreDependencyTest.pom("2.13.3"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("<version>2.25.5</version>")))),
                xml("<Configuration><PatternLayout pattern=\"%d %m{nolookups}%n\"/></Configuration>",
                        source -> source.path("src/main/resources/log4j2.xml").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("pattern=\"%d %m%n\""), printed);
                                    assertTrue(printed.contains(FindLog4jCore25ConfigurationRisks.EXCEPTION), printed);
                                })));
    }

    @Test
    void sourceAutoMigrationAndRiskMarkerCompose() {
        rewriteRun(spec -> spec.parser(org.openrewrite.java.JavaParser.fromJavaVersion()
                        .dependsOn(Log4jCoreTestApi.legacySources())),
                java("""
                        import org.apache.logging.log4j.core.*;
                        import org.apache.logging.log4j.core.config.LoggerConfig;
                        import org.apache.logging.log4j.core.config.plugins.Plugin;
                        @Plugin(name="Audit", category="Core") class Audit {
                            String pattern="%m{nolookups}";
                            void configure(LoggerConfig.Builder builder, Filter filter){ builder.withtFilter(filter); }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("%m\""), printed);
                    assertTrue(printed.contains("builder.setFilter(filter)"), printed);
                    assertTrue(printed.contains(FindLog4jCore25SourceRisks.PLUGIN_DISCOVERY), printed);
                })));
    }

    @Test
    void recommendedRecipeNeverDowngradesAHigherFixedVersion() {
        rewriteRun(xml(
                UpgradeLog4jCoreDependencyTest.pom("2.26.0"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>2.26.0</version>"), printed);
                    assertTrue(printed.contains(
                            FindLog4jCore25BuildRisks.targetConflictMessage("2.26.0")), printed);
                })));
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml("<Configuration status=\"WARN\"><PatternLayout pattern=\"%m{nolookups}\"/></Configuration>",
                        source -> source.path("src/main/resources/log4j2.xml").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertEquals(1, occurrences(printed, FindLog4jCore25ConfigurationRisks.STATUS));
                                    assertEquals(1, occurrences(printed, FindLog4jCore25ConfigurationRisks.EXCEPTION));
                                })));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.log4jcore").build();
    }

    private static int occurrences(String value, String token) {
        int found = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) found++;
        return found;
    }
}
