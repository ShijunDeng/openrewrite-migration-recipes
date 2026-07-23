package com.huawei.clouds.openrewrite.springretry;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringRetryRecommendedRecipeTest implements RewriteTest {
    private static final String BASELINE =
            "com.huawei.clouds.openrewrite.springretry.UpgradeSpringRetryBuildToJava17";
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springretry.UpgradeSpringRetryTo2_0_13";
    private static final String SELECTED_MIGRATION =
            "com.huawei.clouds.openrewrite.springretry.MigrateSelectedSpringRetry20Java";
    private static final String SELECTED_SOURCE_RISKS =
            "com.huawei.clouds.openrewrite.springretry.FindSelectedSpringRetry2_0SourceRisks";
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springretry.MigrateSpringRetryTo2_0_13";

    @Test
    void recommendedRecipeHasAuditedOrderAndOfficialChildren() {
        Recipe recipe = SpringRetryTestSupport.recipe(RECIPE);
        List<String> names = recipe.getRecipeList().stream().map(Recipe::getName).toList();
        assertEquals(List.of(
                BASELINE,
                UPGRADE,
                SELECTED_MIGRATION,
                "com.huawei.clouds.openrewrite.springretry.FindSpringRetry2_0_13BuildRisks",
                SELECTED_SOURCE_RISKS), names);

        Recipe baseline = SpringRetryTestSupport.recipe(BASELINE);
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.springretry.MarkSelectedSpringRetryProjects",
                "com.huawei.clouds.openrewrite.springretry.UpgradeMarkedSpringRetryBuildToJava17"),
                baseline.getRecipeList().stream().map(Recipe::getName).toList());
        Recipe javaBaseline = baseline.getRecipeList().get(1);
        assertEquals(1, executableChildren(javaBaseline).stream()
                .filter(child -> "org.openrewrite.java.migrate.UpgradeJavaVersion"
                        .equals(child.getName())).count());

        Recipe selectedUpgrade = recipe.getRecipeList().get(1);
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.springretry.MarkSelectedSpringRetryProjects",
                "com.huawei.clouds.openrewrite.springretry.UpgradeMarkedSpringRetryDependencyTo2_0_13"),
                selectedUpgrade.getRecipeList().stream().map(Recipe::getName).toList());
        Recipe markedUpgrade = selectedUpgrade.getRecipeList().get(1);
        assertEquals(List.of(
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion",
                "com.huawei.clouds.openrewrite.springretry.UpgradeSelectedSpringRetryDependency"),
                executableChildren(markedUpgrade).stream().map(Recipe::getName).toList());
        List<Recipe> selectedMigrationChildren = executableChildren(recipe.getRecipeList().get(2));
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.springretry.MigrateDeterministicSpringRetry20Java"),
                selectedMigrationChildren.stream().map(Recipe::getName).toList());
        List<Recipe> guardedMigrationChildren = executableChildren(selectedMigrationChildren.get(0));
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.springretry.ProtectSpringRetryAnnotationAliasConflicts",
                "com.huawei.clouds.openrewrite.springretry.MigrateOfficialSpringRetry20JavaLeaves",
                "com.huawei.clouds.openrewrite.springretry.RestoreSpringRetryAnnotationAliasConflicts"),
                guardedMigrationChildren.stream().map(Recipe::getName).toList());
        Recipe javaMigration = guardedMigrationChildren.get(1);
        assertTrue(javaMigration.getRecipeList().stream().map(Recipe::getName)
                .anyMatch("org.openrewrite.java.ChangeAnnotationAttributeName"::equals));
        assertTrue(javaMigration.getRecipeList().stream().map(Recipe::getName)
                .anyMatch("org.openrewrite.java.ChangeMethodName"::equals));
        assertTrue(javaMigration.getRecipeList().stream()
                .allMatch(child -> child.getClass().getProtectionDomain().getCodeSource()
                        .getLocation().toString().contains("/8.87.5/")),
                javaMigration.getRecipeList().toString());
        Recipe selectedRisks = recipe.getRecipeList().get(4);
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.springretry.FindSpringRetry2_0SourceRisks"),
                executableChildren(selectedRisks).stream().map(Recipe::getName).toList());
    }

    private static List<Recipe> executableChildren(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .filter(child -> !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether"
                        .equals(child.getName()))
                .toList();
    }

    @Test
    void officialJava17RecipeActuallyActivatesForExactMaven134() {
        String source = SpringRetryTestSupport.project(
                "<properties><maven.compiler.source>8</maven.compiler.source>" +
                "<maven.compiler.target>8</maven.compiler.target></properties><dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4", "") + "</dependencies>");
        rewriteRun(spec -> spec.recipe(SpringRetryTestSupport.recipe(BASELINE)),
                pomXml(source, pom -> pom.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(">17<"), printed);
                    assertFalse(printed.contains("<maven.compiler.source>8</maven.compiler.source>"), printed);
                    assertFalse(printed.contains("<maven.compiler.target>8</maven.compiler.target>"), printed);
                    assertTrue(printed.contains("<version>1.3.4</version>"), printed);
                })));
    }

    @Test
    void officialJava17RecipeActuallyActivatesForExactGradle134() {
        rewriteRun(spec -> spec.recipe(SpringRetryTestSupport.recipe(BASELINE)),
                buildGradle("""
                        sourceCompatibility = '1.8'
                        targetCompatibility = '1.8'
                        dependencies {
                          implementation 'org.springframework.retry:spring-retry:1.3.4'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("VERSION_17") || printed.contains("'17'") ||
                               printed.contains("\"17\""), printed);
                    assertFalse(printed.contains("'1.8'"), printed);
                    assertTrue(printed.contains("spring-retry:1.3.4"), printed);
                })));
    }

    @Test
    void baselineRecipeDoesNotActivateForUnapprovedMavenVersion() {
        String source = SpringRetryTestSupport.project(
                "<properties><maven.compiler.release>8</maven.compiler.release></properties><dependencies>" +
                SpringRetryTestSupport.dependency("1.3.3", "") + "</dependencies>");
        rewriteRun(spec -> spec.recipe(SpringRetryTestSupport.recipe(BASELINE)),
                pomXml(source));
    }

    @Test
    void baselineRecipeDoesNotActivateForTargetOrHigherVersion() {
        rewriteRun(spec -> spec.recipe(SpringRetryTestSupport.recipe(BASELINE)),
                pomXml(SpringRetryTestSupport.project(
                        "<properties><java.version>8</java.version></properties><dependencies>" +
                        SpringRetryTestSupport.dependency("2.0.13", "") + "</dependencies>"),
                        source -> source.path("target/pom.xml")),
                xml(SpringRetryTestSupport.project(
                        "<properties><java.version>8</java.version></properties><dependencies>" +
                        SpringRetryTestSupport.dependency("3.0.0", "") + "</dependencies>"),
                        source -> source.path("higher/pom.xml")));
    }

    @Test
    void fullRecipeUpgradesJavaDependencyAndOfficialApisInOneRun() {
        Recipe recipe = SpringRetryTestSupport.recipe(RECIPE);
        String source = SpringRetryTestSupport.project(
                "<properties><maven.compiler.release>8</maven.compiler.release></properties><dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4", "") + "</dependencies>");
        rewriteRun(spec -> spec.recipe(recipe).parser(SpringRetryTestSupport.parser()),
                pomXml(source, pom -> pom.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>2.0.13</version>"), printed);
                    assertTrue(printed.contains(">17<"), printed);
                    assertFalse(printed.contains(FindSpringRetry2013BuildRisks.OUTSIDE), printed);
                    assertFalse(printed.contains(SpringRetrySupport.TARGET_CONFLICT), printed);
                })),
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        import org.springframework.retry.support.RetryTemplate;
                        class Client {
                            @Retryable(include = IOException.class,
                                       maxAttemptsExpression = "#{${client.attempts:3}}")
                            void call() {
                                RetryTemplate.builder().withinMillis(1000).build();
                            }
                        }
                        """, sourceSpec -> sourceSpec.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("retryFor = IOException.class"), printed);
                    assertTrue(printed.contains("withTimeout(1000)"), printed);
                    assertTrue(printed.contains(FindSpringRetry20SourceRisks.PROXY_RECOVERY), printed);
                    assertTrue(printed.contains(FindSpringRetry20SourceRisks.EXPRESSION), printed);
                    assertTrue(printed.contains(FindSpringRetry20SourceRisks.BACKOFF_POLICY), printed);
                })));
    }

    @Test
    void fullRecipeNeverDowngradesHigherVersionsAndAddsExactConflictMarker() {
        String source = SpringRetryTestSupport.project(
                "<properties><maven.compiler.release>21</maven.compiler.release></properties><dependencies>" +
                SpringRetryTestSupport.dependency("3.0.0", "") + "</dependencies>");
        rewriteRun(spec -> spec.recipe(SpringRetryTestSupport.recipe(RECIPE)),
                xml(source, pom -> pom.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>3.0.0</version>"), printed);
                    assertTrue(printed.contains(SpringRetrySupport.TARGET_CONFLICT), printed);
                    assertFalse(printed.contains("<version>2.0.13</version>"), printed);
                })));
    }

    @Test
    void fullRecipeLeavesOtherLowerVersionsAndExplainsUnsupportedPath() {
        String source = SpringRetryTestSupport.project("<dependencies>" +
                SpringRetryTestSupport.dependency("2.0.12", "") + "</dependencies>");
        rewriteRun(spec -> spec.recipe(SpringRetryTestSupport.recipe(RECIPE)),
                xml(source, pom -> pom.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>2.0.12</version>"), printed);
                    assertTrue(printed.contains(FindSpringRetry2013BuildRisks.OUTSIDE), printed);
                    assertFalse(printed.contains("<version>2.0.13</version>"), printed);
                })));
    }

    @Test
    void fullCompositionIsIdempotent() {
        Recipe recipe = SpringRetryTestSupport.recipe(RECIPE);
        rewriteRun(spec -> spec.recipe(recipe).parser(SpringRetryTestSupport.parser())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                xml(SpringRetryTestSupport.pom("1.3.4"), SpringRetryTestSupport.pom("2.0.13"),
                        source -> source.path("pom.xml")),
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        import org.springframework.retry.support.RetryTemplate;
                        class Client {
                            @Retryable(include = IOException.class)
                            void call() { RetryTemplate.builder().withinMillis(10).build(); }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(1, SpringRetryTestSupport.occurrences(printed, "retryFor ="));
                    assertEquals(1, SpringRetryTestSupport.occurrences(printed, "withTimeout(10)"));
                })));
    }

    @Test
    void generatedBuildAndJavaFilesRemainUntouchedInFullComposition() {
        rewriteRun(spec -> spec.recipe(SpringRetryTestSupport.recipe(RECIPE))
                        .parser(SpringRetryTestSupport.parser()),
                xml(SpringRetryTestSupport.project(
                        "<properties><maven.compiler.release>8</maven.compiler.release></properties><dependencies>" +
                        SpringRetryTestSupport.dependency("1.3.4", "") + "</dependencies>"),
                        source -> source.path("target/generated/pom.xml")),
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        class Generated {
                            @Retryable(include = IOException.class)
                            void call() {}
                        }
                        """, source -> source.path("build/generated/sources/Generated.java")));
    }
}
