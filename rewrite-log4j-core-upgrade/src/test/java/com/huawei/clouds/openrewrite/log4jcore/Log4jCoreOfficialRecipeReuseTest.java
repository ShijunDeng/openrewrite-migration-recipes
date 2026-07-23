package com.huawei.clouds.openrewrite.log4jcore;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class Log4jCoreOfficialRecipeReuseTest implements RewriteTest {
    private static final String LOCAL_RECOMMENDED =
            "com.huawei.clouds.openrewrite.log4jcore.MigrateLog4jCoreTo2_25_5";
    private static final String OFFICIAL_UPGRADE =
            "org.openrewrite.java.logging.log4j.UpgradeLog4J2DependencyVersion";
    private static final String OFFICIAL_LOG4J1_CLASS_RECIPE =
            "org.openrewrite.java.logging.log4j.LoggerSetLevelToConfiguratorRecipe";

    @Test
    void builderMigrationUsesExactOfficialCoreDelegates() {
        List<Recipe> delegates = MigrateLoggerConfigFilterBuilders.officialCoreRecipes();
        assertEquals(3, delegates.size());
        assertEquals(List.of(
                "org.apache.logging.log4j.core.config.LoggerConfig.Builder " +
                "withtFilter(org.apache.logging.log4j.core.Filter)",
                "org.apache.logging.log4j.core.config.LoggerConfig.Builder " +
                "withFilter(org.apache.logging.log4j.core.Filter)",
                "org.apache.logging.log4j.core.config.LoggerConfig.RootLogger.Builder " +
                "withtFilter(org.apache.logging.log4j.core.Filter)"
        ), delegates.stream()
                .map(ChangeMethodName.class::cast)
                .map(ChangeMethodName::getMethodPattern)
                .toList());
        for (Recipe delegate : delegates) {
            ChangeMethodName rename = assertInstanceOf(ChangeMethodName.class, delegate);
            assertEquals("setFilter", rename.getNewMethodName());
            assertEquals(Boolean.FALSE, rename.getMatchOverrides());
            assertEquals(Boolean.TRUE, rename.getIgnoreDefinition());
        }
    }

    @Test
    void auditedOfficialClassAndCompositionActivateButAreExcludedFromThePatchRecipe() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();

        Recipe officialUpgrade = environment.activateRecipes(OFFICIAL_UPGRADE);
        assertEquals(OFFICIAL_UPGRADE, officialUpgrade.getName());
        UpgradeDependencyVersion broadUpgrade = flatten(officialUpgrade)
                .map(Log4jCoreOfficialRecipeReuseTest::unwrap)
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("org.apache.logging.log4j", broadUpgrade.getGroupId());
        assertEquals("*", broadUpgrade.getArtifactId());
        assertEquals("2.x", broadUpgrade.getNewVersion());
        assertEquals(Boolean.TRUE, broadUpgrade.getOverrideManagedVersion());

        Recipe officialClassRecipe = environment.activateRecipes(OFFICIAL_LOG4J1_CLASS_RECIPE);
        assertEquals(OFFICIAL_LOG4J1_CLASS_RECIPE, officialClassRecipe.getName());

        Recipe local = environment.activateRecipes(LOCAL_RECOMMENDED);
        List<String> localChildren = local.getRecipeList().stream().map(Recipe::getName).toList();
        assertFalse(localChildren.contains(OFFICIAL_UPGRADE), localChildren.toString());
        assertFalse(localChildren.contains(OFFICIAL_LOG4J1_CLASS_RECIPE), localChildren.toString());
        assertTrue(localChildren.contains(
                "com.huawei.clouds.openrewrite.log4jcore.MigrateLog4jCore25"));
    }

    @Test
    void officialCoreRenamesAndTheNarrowNoLookupsGapRunTogether() {
        rewriteRun(spec -> spec.recipe(new MigrateLog4jCore25())
                        .parser(JavaParser.fromJavaVersion().dependsOn(Log4jCoreTestApi.legacySources())),
                java("""
                        import org.apache.logging.log4j.core.Filter;
                        import org.apache.logging.log4j.core.config.LoggerConfig;
                        class Logging {
                            String pattern = "%d %m{nolookups}%n";
                            void configure(LoggerConfig.Builder logger,
                                           LoggerConfig.RootLogger.Builder root,
                                           Filter filter) {
                                logger.withtFilter(filter);
                                logger.withFilter(filter);
                                root.withtFilter(filter);
                            }
                        }
                        """, """
                        import org.apache.logging.log4j.core.Filter;
                        import org.apache.logging.log4j.core.config.LoggerConfig;
                        class Logging {
                            String pattern = "%d %m%n";
                            void configure(LoggerConfig.Builder logger,
                                           LoggerConfig.RootLogger.Builder root,
                                           Filter filter) {
                                logger.setFilter(filter);
                                logger.setFilter(filter);
                                root.setFilter(filter);
                            }
                        }
                        """));
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        return Stream.concat(
                Stream.of(recipe),
                recipe.getRecipeList().stream().flatMap(Log4jCoreOfficialRecipeReuseTest::flatten));
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe unwrapped = recipe;
        while (unwrapped instanceof Recipe.DelegatingRecipe delegating) {
            unwrapped = delegating.getDelegate();
        }
        return unwrapped;
    }
}
