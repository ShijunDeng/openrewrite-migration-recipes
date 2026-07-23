package com.huawei.clouds.openrewrite.springretry;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class SpringRetryAnnotationConflictGateTest implements RewriteTest {
    private static final String SELECTED =
            "com.huawei.clouds.openrewrite.springretry.MigrateSelectedSpringRetry20Java";
    private static final String DETERMINISTIC =
            "com.huawei.clouds.openrewrite.springretry.MigrateDeterministicSpringRetry20Java";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.springretry.MigrateSpringRetryTo2_0_13";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(SpringRetryTestSupport.recipe(SELECTED))
                .parser(SpringRetryTestSupport.parser());
    }

    @Test
    void guardedRecipeTreeDoesNotRequestAdditionalCycles() {
        List<String> causing = new ArrayList<>();
        collectCycleRecipes(SpringRetryTestSupport.recipe(SELECTED), causing);
        assertTrue(causing.isEmpty(), causing.toString());
    }

    @Test
    void marksRetryableValueAndIncludeAndPreservesEveryAliasOnThatAnnotation() {
        rewriteRun(java("""
                import java.io.IOException;
                import org.springframework.retry.annotation.Retryable;
                class Client {
                    @Retryable(value = IOException.class, include = IllegalStateException.class,
                               exclude = IllegalArgumentException.class)
                    void call() {}
                }
                """, source -> selected(source).after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertConflictCount(printed, 1);
            assertTrue(printed.contains("value = IOException.class"), printed);
            assertTrue(printed.contains("include = IllegalStateException.class"), printed);
            assertTrue(printed.contains("exclude = IllegalArgumentException.class"), printed);
            assertFalse(printed.contains("retryFor ="), printed);
            assertFalse(printed.contains("noRetryFor ="), printed);
            assertNoProtectionResidue(printed);
        })));
    }

    @Test
    void marksRetryableOldAndTargetPositiveAliases() {
        rewriteRun(java("""
                import java.io.IOException;
                import org.springframework.retry.annotation.Retryable;
                class Client {
                    @Retryable(value = IOException.class, retryFor = IllegalStateException.class)
                    void byValue() {}
                    @Retryable(include = IOException.class, retryFor = IllegalStateException.class)
                    void byInclude() {}
                }
                """, source -> selected(source).after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertConflictCount(printed, 2);
            assertTrue(printed.contains("value = IOException.class, retryFor = IllegalStateException.class"),
                    printed);
            assertTrue(printed.contains("include = IOException.class, retryFor = IllegalStateException.class"),
                    printed);
            assertEquals(2, SpringRetryTestSupport.occurrences(
                    printed, "retryFor = IllegalStateException.class"), printed);
            assertNoProtectionResidue(printed);
        })));
    }

    @Test
    void marksRetryableExcludeAndNoRetryFor() {
        rewriteRun(java("""
                import org.springframework.retry.annotation.Retryable;
                class Client {
                    @Retryable(exclude = IllegalArgumentException.class,
                               noRetryFor = IllegalStateException.class)
                    void call() {}
                }
                """, source -> selected(source).after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertConflictCount(printed, 1);
            assertTrue(printed.contains("exclude = IllegalArgumentException.class"), printed);
            assertTrue(printed.contains("noRetryFor = IllegalStateException.class"), printed);
            assertNoProtectionResidue(printed);
        })));
    }

    @Test
    void appliesTheSameThreeConflictRulesToCircuitBreaker() {
        rewriteRun(java("""
                import java.io.IOException;
                import org.springframework.retry.annotation.CircuitBreaker;
                class Client {
                    @CircuitBreaker(value = IOException.class, include = RuntimeException.class)
                    void duplicateOldAliases() {}
                    @CircuitBreaker(include = IOException.class, retryFor = RuntimeException.class)
                    void oldAndTarget() {}
                    @CircuitBreaker(exclude = IllegalArgumentException.class,
                                    noRetryFor = IllegalStateException.class)
                    void negativeAliases() {}
                }
                """, source -> selected(source).after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertConflictCount(printed, 3);
            assertTrue(printed.contains("value = IOException.class, include = RuntimeException.class"), printed);
            assertTrue(printed.contains("include = IOException.class, retryFor = RuntimeException.class"), printed);
            assertTrue(printed.contains("exclude = IllegalArgumentException.class"), printed);
            assertTrue(printed.contains("noRetryFor = IllegalStateException.class"), printed);
            assertNoProtectionResidue(printed);
        })));
    }

    @Test
    void safeAnnotationsInTheSameFileStillUseTheOfficialRenameLeaves() {
        rewriteRun(java("""
                import java.io.IOException;
                import org.springframework.retry.annotation.Retryable;
                import org.springframework.retry.annotation.CircuitBreaker;
                class Client {
                    @Retryable(value = IOException.class, include = RuntimeException.class)
                    void conflict() {}
                    @Retryable(include = IOException.class)
                    void safeRetryable() {}
                    @CircuitBreaker(exclude = IllegalArgumentException.class)
                    void safeCircuitBreaker() {}
                }
                """, source -> selected(source).after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertConflictCount(printed, 1);
            assertTrue(printed.contains("value = IOException.class, include = RuntimeException.class"), printed);
            assertTrue(printed.contains("@Retryable(retryFor = IOException.class)"), printed);
            assertTrue(printed.contains(
                    "@CircuitBreaker(noRetryFor = IllegalArgumentException.class)"), printed);
            assertNoProtectionResidue(printed);
        })));
    }

    @Test
    void directlyActivatedDeterministicRecipeProtectsConflictsAndMigratesSafeAnnotations() {
        rewriteRun(spec -> spec.recipe(SpringRetryTestSupport.recipe(DETERMINISTIC)),
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        import org.springframework.retry.annotation.CircuitBreaker;
                        class Client {
                            @Retryable(value = IOException.class, include = RuntimeException.class)
                            void conflict() {}
                            @Retryable(include = IOException.class)
                            void safeRetryable() {}
                            @CircuitBreaker(exclude = IllegalArgumentException.class)
                            void safeCircuitBreaker() {}
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertConflictCount(printed, 1);
                    assertTrue(printed.contains(
                            "@Retryable(value = IOException.class, include = RuntimeException.class)"),
                            printed);
                    assertTrue(printed.contains("@Retryable(retryFor = IOException.class)"), printed);
                    assertTrue(printed.contains(
                            "@CircuitBreaker(noRetryFor = IllegalArgumentException.class)"), printed);
                    assertNoProtectionResidue(printed);
                })));
    }

    @Test
    void protectsAndRestoresImplicitValueFormattingWhenASecondAliasIsPresent() {
        rewriteRun(java("""
                import java.io.IOException;
                import org.springframework.retry.annotation.Retryable;
                class Client {
                    @Retryable(IOException.class, include = IllegalStateException.class)
                    void call() {}
                }
                """, source -> selected(source).after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertConflictCount(printed, 1);
            assertTrue(printed.contains(
                    "@Retryable(IOException.class, include = IllegalStateException.class)"), printed);
            assertFalse(printed.contains("retryFor = IOException.class"), printed);
            assertNoProtectionResidue(printed);
        })));
    }

    @Test
    void userAuthoredSentinelLikeAttributeIsNeverRestoredWithoutTheProtectionMarker() {
        rewriteRun(java("""
                import org.springframework.retry.annotation.Retryable;
                class Client {
                    @Retryable(value = RuntimeException.class, include = IllegalStateException.class,
                               __openrewrite_spring_retry_protected_exclude = IllegalArgumentException.class)
                    void call() {}
                }
                """, source -> selected(source).after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertConflictCount(printed, 1);
            assertTrue(printed.contains(
                    "__openrewrite_spring_retry_protected_exclude = IllegalArgumentException.class"), printed);
            assertFalse(Pattern.compile("(?<![A-Za-z0-9_])exclude\\s*=")
                    .matcher(printed).find(), printed);
        })));
    }

    @Test
    void selectedAndAuthoredSourceGatesExcludeUnselectedAndGeneratedFiles() {
        rewriteRun(
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        class Unselected {
                            @Retryable(value = IOException.class, include = RuntimeException.class)
                            void call() {}
                        }
                        """),
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        class Generated {
                            @Retryable(value = IOException.class, include = RuntimeException.class)
                            void call() {}
                        }
                        """, source -> selected(source).path("build/generated/sources/Generated.java")));
    }

    @Test
    void recommendedRecipeOnlyMarksTheExactPreUpgradeProject() {
        rewriteRun(spec -> spec.recipe(SpringRetryTestSupport.recipe(RECOMMENDED))
                        .parser(SpringRetryTestSupport.parser()),
                pomXml(SpringRetryTestSupport.pom("1.3.4"),
                        source -> source.path("selected/pom.xml").after(actual -> actual)),
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        class Selected {
                            @Retryable(value = IOException.class, include = RuntimeException.class)
                            void conflict() {}
                            @Retryable(include = IOException.class)
                            void safe() {}
                        }
                        """, source -> source.path("selected/src/main/java/Selected.java")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertConflictCount(printed, 1);
                                    assertTrue(printed.contains(
                                            "@Retryable(retryFor = IOException.class)"), printed);
                                })),
                pomXml(SpringRetryTestSupport.pom("2.0.13"),
                        source -> source.path("already-upgraded/pom.xml")),
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        class AlreadyUpgraded {
                            @Retryable(value = IOException.class, include = RuntimeException.class)
                            void call() {}
                        }
                        """, source -> source.path(
                                "already-upgraded/src/main/java/AlreadyUpgraded.java")));
    }

    @Test
    void conflictProtectionAndSafeMigrationAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import java.io.IOException;
                        import org.springframework.retry.annotation.Retryable;
                        class Client {
                            @Retryable(value = IOException.class, include = RuntimeException.class)
                            void conflict() {}
                            @Retryable(include = IOException.class)
                            void safe() {}
                        }
                        """, source -> selected(source).after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertConflictCount(printed, 1);
                    assertTrue(printed.contains(
                            "@Retryable(retryFor = IOException.class)"), printed);
                    assertNoProtectionResidue(printed);
                })));
    }

    private static <T extends org.openrewrite.SourceFile> org.openrewrite.test.SourceSpec<T> selected(
            org.openrewrite.test.SourceSpec<T> source) {
        return source.markers(new SpringRetryProjectMarker(UUID.randomUUID()));
    }

    private static void assertConflictCount(String printed, int expected) {
        assertEquals(expected, SpringRetryTestSupport.occurrences(
                printed, ProtectSpringRetryAnnotationAliasConflicts.CONFLICT), printed);
    }

    private static void assertNoProtectionResidue(String printed) {
        assertFalse(printed.contains("__openrewrite_spring_retry_protected_"), printed);
    }

    private static void collectCycleRecipes(Recipe recipe, List<String> causing) {
        if (recipe.causesAnotherCycle()) causing.add(recipe.getName());
        recipe.getRecipeList().forEach(child -> collectCycleRecipes(child, causing));
    }
}
