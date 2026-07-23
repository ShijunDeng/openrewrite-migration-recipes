package com.huawei.clouds.openrewrite.springboot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;

class SpringBootLegacyGradleBuildscriptTest implements RewriteTest {

    @ParameterizedTest(name = "Legacy Gradle plugin classpath upgrades exact source {0}")
    @ValueSource(strings = {
            "2.1.3.RELEASE", "2.3.4.RELEASE", "2.6.6", "2.7.10", "2.7.12", "2.7.17", "2.7.18",
            "3.1.3", "3.1.6", "3.2.0", "3.2.9", "3.2.12", "3.4.0", "3.4.3", "3.4.5", "3.4.6",
            "3.4.9", "3.4.12", "3.5.12"
    })
    void upgradesEveryWorkbookSourceVersion(String version) {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedSpringBootVersion()),
                buildGradle(
                        legacyGroovy(version),
                        legacyGroovy(SpringBootSupport.TARGET)));
    }

    @Test
    void upgradesKotlinBuildscriptClasspath() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedSpringBootVersion()),
                buildGradleKts(
                        legacyKotlin("2.7.18"),
                        legacyKotlin(SpringBootSupport.TARGET)));
    }

    @Test
    void upgradesOnlyWhitelistCoordinatesAndNeverDowngradesFutureVersion() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedSpringBootVersion()),
                buildGradle(
                        """
                        buildscript {
                          dependencies {
                            classpath 'org.springframework.boot:spring-boot-gradle-plugin:3.4.12'
                            classpath 'org.springframework.boot:spring-boot-gradle-plugin:3.4.13'
                            classpath 'org.springframework.boot:spring-boot-gradle-plugin:4.0.0'
                          }
                        }
                        """,
                        """
                        buildscript {
                          dependencies {
                            classpath 'org.springframework.boot:spring-boot-gradle-plugin:3.5.15'
                            classpath 'org.springframework.boot:spring-boot-gradle-plugin:3.4.13'
                            classpath 'org.springframework.boot:spring-boot-gradle-plugin:4.0.0'
                          }
                        }
                        """));
    }

    @Test
    void nestedBuildscriptOwnedByAnotherDslScopeIsNotClaimed() {
        String nested = """
                allprojects {
                  buildscript {
                    dependencies {
                      classpath 'org.springframework.boot:spring-boot-gradle-plugin:2.7.18'
                    }
                  }
                }
                """;
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedSpringBootVersion()),
                buildGradle(nested));
        rewriteRun(
                spec -> spec.recipe(new MarkSelectedSpringBootProjects()),
                buildGradle(nested, source -> source.afterRecipe(after ->
                        assertFalse(after.getMarkers()
                                .findFirst(SpringBootProjectMarker.class).isPresent()))));
    }

    @Test
    void exactLegacyClasspathSelectsTheBuildRoot() {
        rewriteRun(
                spec -> spec.recipe(new MarkSelectedSpringBootProjects()),
                buildGradle(
                        legacyGroovy("2.7.18"),
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            SpringBootProjectMarker marker = after.getMarkers()
                                    .findFirst(SpringBootProjectMarker.class).orElseThrow();
                            assertEquals("2.7.18", marker.getSourceVersion());
                        })));
    }

    @Test
    void matchingLegacyAndPluginDslOwnersKeepTheRootSelected() {
        rewriteRun(
                spec -> spec.recipe(new MarkSelectedSpringBootProjects()),
                buildGradle(
                        legacyGroovy("3.4.12") + """
                                plugins {
                                  id 'org.springframework.boot' version '3.4.12'
                                }
                                """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            SpringBootProjectMarker marker = after.getMarkers()
                                    .findFirst(SpringBootProjectMarker.class).orElseThrow();
                            assertEquals("3.4.12", marker.getSourceVersion());
                        })));
    }

    @Test
    void differentOrFutureOwnersInTheSameRootBlockEligibility() {
        rewriteRun(
                spec -> spec.recipe(new MarkSelectedSpringBootProjects()),
                buildGradle(
                        legacyGroovy("2.7.18") + """
                                plugins {
                                  id 'org.springframework.boot' version '3.4.12'
                                }
                                """,
                        source -> source.path("different/build.gradle").afterRecipe(after ->
                                assertFalse(after.getMarkers()
                                        .findFirst(SpringBootProjectMarker.class).isPresent()))),
                buildGradle(
                        legacyGroovy("2.7.18") + """
                                plugins {
                                  id 'org.springframework.boot' version '4.0.0'
                                }
                                """,
                        source -> source.path("future/build.gradle").afterRecipe(after ->
                                assertFalse(after.getMarkers()
                                        .findFirst(SpringBootProjectMarker.class).isPresent()))));
    }

    @Test
    void riskRecipeMarksOutsideAndFutureClasspathVersionsButAcceptsSelectedAndTarget() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringBoot35BuildRisks()),
                buildGradle(
                        """
                        buildscript {
                          dependencies {
                            classpath 'org.springframework.boot:spring-boot-gradle-plugin:3.4.12'
                            classpath 'org.springframework.boot:spring-boot-gradle-plugin:3.5.15'
                            classpath 'org.springframework.boot:spring-boot-gradle-plugin:3.4.13'
                            classpath 'org.springframework.boot:spring-boot-gradle-plugin:4.0.0'
                          }
                        }
                        """,
                        source -> source.after(actual -> {
                            assertTrue(actual.contains(FindSpringBoot35BuildRisks.OUTSIDE), actual);
                            assertTrue(actual.contains(SpringBootSupport.TARGET_CONFLICT), actual);
                            assertEquals(1, occurrences(actual, FindSpringBoot35BuildRisks.OUTSIDE), actual);
                            assertEquals(1, occurrences(actual, SpringBootSupport.TARGET_CONFLICT), actual);
                            return actual;
                        })));
    }

    @Test
    void kotlinRiskRecipeMarksFutureClasspathWithoutChangingIt() {
        rewriteRun(
                spec -> spec.recipe(new FindSpringBoot35BuildRisks()),
                buildGradleKts(
                        legacyKotlin("4.0.0"),
                        source -> source.after(actual -> {
                            assertTrue(actual.contains(SpringBootSupport.TARGET_CONFLICT), actual);
                            assertTrue(actual.contains(
                                    "org.springframework.boot:spring-boot-gradle-plugin:4.0.0"), actual);
                            return actual;
                        })));
    }

    private static String legacyGroovy(String version) {
        return """
                buildscript {
                  dependencies {
                    classpath 'org.springframework.boot:spring-boot-gradle-plugin:%s'
                  }
                }
                """.formatted(version);
    }

    private static String legacyKotlin(String version) {
        return """
                buildscript {
                  dependencies {
                    classpath("org.springframework.boot:spring-boot-gradle-plugin:%s")
                  }
                }
                """.formatted(version);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
