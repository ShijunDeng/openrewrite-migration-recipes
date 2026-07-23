package com.huawei.clouds.openrewrite.springretry;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SpringRetryOfficialMigrationTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springretry.MigrateDeterministicSpringRetry20Java";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(SpringRetryTestSupport.recipe(RECIPE)).parser(SpringRetryTestSupport.parser());
    }

    @Test
    void compositionUsesAllSevenFixedOfficialCoreRecipes() {
        Recipe recipe = SpringRetryTestSupport.recipe(RECIPE);
        List<Recipe> guardedChildren = executableChildren(recipe);
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.springretry.ProtectSpringRetryAnnotationAliasConflicts",
                "com.huawei.clouds.openrewrite.springretry.MigrateOfficialSpringRetry20JavaLeaves",
                "com.huawei.clouds.openrewrite.springretry.RestoreSpringRetryAnnotationAliasConflicts"),
                guardedChildren.stream().map(Recipe::getName).toList());

        List<String> names = guardedChildren.get(1).getRecipeList().stream()
                .map(Recipe::getName)
                .toList();
        assertEquals(6, names.stream()
                .filter("org.openrewrite.java.ChangeAnnotationAttributeName"::equals).count(), names.toString());
        assertEquals(1, names.stream().filter("org.openrewrite.java.ChangeMethodName"::equals).count(),
                names.toString());
        assertFalse(names.contains("org.openrewrite.java.ChangeType"), names.toString());
        assertFalse(names.stream().anyMatch(name -> name.contains("UpgradeDependencyVersion")), names.toString());
    }

    private static List<Recipe> executableChildren(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .filter(child -> !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether"
                        .equals(child.getName()))
                .toList();
    }

    @Test
    void fixedRuntimeCatalogHasNoDedicatedOfficialSpringRetryMajorMigration() {
        List<String> runtimeRecipes = SpringRetryTestSupport.environment().listRecipes().stream()
                .map(Recipe::getName)
                .toList();
        assertTrue(runtimeRecipes.stream().anyMatch(
                name -> name.startsWith("org.openrewrite.java.spring.")),
                "rewrite-spring runtime catalog was not scanned");
        List<String> officialRetryRecipes = runtimeRecipes.stream()
                .filter(name -> name.startsWith("org.openrewrite."))
                .filter(name -> {
                    String lower = name.toLowerCase();
                    return lower.contains("springretry") || lower.contains("spring.retry");
                })
                .sorted()
                .toList();
        assertTrue(officialRetryRecipes.isEmpty(), officialRetryRecipes.toString());
    }

    @Test
    void migratesAllRetryableDeprecatedExceptionAliases() {
        rewriteRun(java("""
                import java.io.IOException;
                import org.springframework.retry.annotation.Retryable;
                class Client {
                    @Retryable(value = IOException.class)
                    void byValue() {}
                    @Retryable(include = IOException.class)
                    void byInclude() {}
                    @Retryable(exclude = IllegalArgumentException.class)
                    void byExclude() {}
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertEquals(2, SpringRetryTestSupport.occurrences(printed, "retryFor = IOException.class"), printed);
            assertTrue(printed.contains("noRetryFor = IllegalArgumentException.class"), printed);
            assertFalse(printed.contains("value ="), printed);
            assertFalse(printed.contains("include ="), printed);
            assertFalse(printed.contains("exclude ="), printed);
        })));
    }

    @Test
    void migratesAllCircuitBreakerDeprecatedExceptionAliases() {
        rewriteRun(java("""
                import java.io.IOException;
                import org.springframework.retry.annotation.CircuitBreaker;
                class Client {
                    @CircuitBreaker(value = IOException.class)
                    void byValue() {}
                    @CircuitBreaker(include = IOException.class)
                    void byInclude() {}
                    @CircuitBreaker(exclude = IllegalArgumentException.class)
                    void byExclude() {}
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertEquals(2, SpringRetryTestSupport.occurrences(printed, "retryFor = IOException.class"), printed);
            assertTrue(printed.contains("noRetryFor = IllegalArgumentException.class"), printed);
            assertFalse(printed.contains("value ="), printed);
            assertFalse(printed.contains("include ="), printed);
            assertFalse(printed.contains("exclude ="), printed);
        })));
    }

    @Test
    void migratesRetryTemplateBuilderWithinMillis() {
        rewriteRun(java("""
                import org.springframework.retry.support.RetryTemplate;
                class Timeouts {
                    RetryTemplate template() {
                        return RetryTemplate.builder().withinMillis(1000L).build();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(".withTimeout(1000L)"), printed);
            assertFalse(printed.contains("withinMillis"), printed);
        })));
    }

    @Test
    void doesNotRenameUnrelatedSameNamedMethodsOrAnnotationAttributes() {
        rewriteRun(java("""
                @interface Retryable { Class<?>[] include() default {}; }
                class LocalBuilder {
                    @Retryable(include = String.class)
                    void call() {}
                    LocalBuilder withinMillis(long value) { return this; }
                    void use() { withinMillis(1); }
                }
                """));
    }

    @Test
    void generatedJavaIsExcludedFromEveryOfficialTransformation() {
        rewriteRun(
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        class Generated {
                            @Retryable(include = IOException.class)
                            void call() {}
                        }
                        """, source -> source.path("build/generated/sources/Generated.java")),
                java("""
                        import org.springframework.retry.support.RetryTemplate;
                        class GeneratedTimeout {
                            Object call() { return RetryTemplate.builder().withinMillis(1).build(); }
                        }
                        """, source -> source.path("target/generated-sources/GeneratedTimeout.java")));
    }

    @Test
    void officialCompositionIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        import org.springframework.retry.support.RetryTemplate;
                        class Client {
                            @Retryable(include = IOException.class)
                            void call() {
                                RetryTemplate.builder().withinMillis(100).build();
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("retryFor = IOException.class"), printed);
                    assertTrue(printed.contains("withTimeout(100)"), printed);
                })));
    }

    @Test
    void processesNetflixGenieFixture() throws Exception {
        rewriteRun(java(fixture("netflix-genie-retryable.java"),
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("retryFor = { JobDirectoryManifestNotFoundException.class }"),
                            printed);
                    assertTrue(printed.contains("maxAttemptsExpression"), printed);
                    assertFalse(printed.contains("include ="), printed);
                })));
    }

    @Test
    void processesSpringRetryUpstreamBuilderFixture() throws Exception {
        rewriteRun(java(fixture("spring-retry-builder.java"),
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertTrue(after.printAll().contains("withTimeout(10000)"), after.printAll());
                    assertFalse(after.printAll().contains("withinMillis"), after.printAll());
                })));
    }

    private static String fixture(String name) throws IOException, URISyntaxException {
        return Files.readString(Path.of(SpringRetryOfficialMigrationTest.class
                .getResource("/fixtures/real/" + name).toURI()));
    }
}
