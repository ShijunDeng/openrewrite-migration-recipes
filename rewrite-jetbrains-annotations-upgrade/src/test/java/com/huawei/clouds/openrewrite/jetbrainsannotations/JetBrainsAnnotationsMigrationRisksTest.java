package com.huawei.clouds.openrewrite.jetbrainsannotations;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class JetBrainsAnnotationsMigrationRisksTest implements RewriteTest {
    private static final String FIND =
            "com.huawei.clouds.openrewrite.jetbrainsannotations.FindJetBrainsAnnotations26MigrationRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(FIND))
                .parser(JavaParser.fromJavaVersion().classpath("annotations"));
    }

    @Test
    void marksMavenJavaBaselineAndLegacyOrParallelDependencies() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy</artifactId><version>1</version>
                  <properties><java.version>1.7</java.version></properties>
                  <dependencies>
                    <dependency><groupId>org.jetbrains</groupId><artifactId>annotations-java5</artifactId><version>24.1.0</version></dependency>
                    <dependency><groupId>org.jspecify</groupId><artifactId>jspecify</artifactId><version>1.0.0</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy</artifactId><version>1</version>
                  <properties><!--~~(org.jetbrains:annotations 26 requires JDK 8 or newer; raise the build baseline or retain annotations-java5 24.1.0)~~>--><java.version>1.7</java.version></properties>
                  <dependencies>
                    <!--~~(annotations-java5 stopped at 24.1.0; keep it only for JDK 5-7 or raise the build to JDK 8+ before using annotations 26)~~>--><dependency><groupId>org.jetbrains</groupId><artifactId>annotations-java5</artifactId><version>24.1.0</version></dependency>
                    <!--~~(parallel nullability annotation dependency detected; define analyzer precedence and check duplicate or conflicting contracts)~~>--><dependency><groupId>org.jspecify</groupId><artifactId>jspecify</artifactId><version>1.0.0</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void marksGradleJavaBaselineLegacyArtifactAndKmpResolution() {
        rewriteRun(
                buildGradle(
                        """
                        plugins { id 'java' }
                        sourceCompatibility = '1.7'
                        dependencies { compileOnly 'com.intellij:annotations:12.0' }
                        """,
                        """
                        plugins { id 'java' }
                        sourceCompatibility = /*~~(org.jetbrains:annotations 26 requires JDK 8 or newer; raise source/target compatibility first)~~>*/'1.7'
                        dependencies { compileOnly /*~~(legacy com.intellij annotations can duplicate org.jetbrains classes; remove or isolate it after checking the resolved classpath)~~>*/'com.intellij:annotations:12.0' }
                        """
                ),
                buildGradleKts(
                        """
                        plugins { kotlin("multiplatform") version "2.2.0" }
                        dependencies { compileOnly("org.jetbrains:annotations:24.0.1") }
                        """,
                        """
                        plugins { kotlin(/*~~(JetBrains Annotations 25+ publishes Kotlin Multiplatform variants; resolve and test every declared target plus lock/verification metadata)~~>*/"multiplatform") version "2.2.0" }
                        dependencies { compileOnly("org.jetbrains:annotations:24.0.1") }
                        """
                )
        );
    }

    @Test
    void marksExperimentalDefaultAndMixedNullabilityContract() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package androidx.annotation;
                        public @interface Nullable {}
                        """,
                        """
                        package org.jetbrains.annotations;
                        public @interface NotNullByDefault {}
                        """
                )),
                java(
                """
                package example;

                import androidx.annotation.Nullable;
                import org.jetbrains.annotations.NotNullByDefault;

                @NotNullByDefault
                class Service {
                    @Nullable String external() {
                        return null;
                    }
                }
                """,
                """
                package example;

                /*~~(mixed nullability models detected; define compiler/analyzer precedence and reconcile conflicting contracts)~~>*/import androidx.annotation.Nullable;
                import org.jetbrains.annotations.NotNullByDefault;

                /*~~(@NotNullByDefault is experimental and recursively changes generic, array, field, parameter, return, and override contracts; review this boundary explicitly)~~>*/@NotNullByDefault
                class Service {
                    @Nullable String external() {
                        return null;
                    }
                }
                """
        ));
    }

    @Test
    void recommendedRecipeUpgradesAndMarksInOneRun() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(
                        "com.huawei.clouds.openrewrite.jetbrainsannotations.MigrateJetBrainsAnnotationsTo26_0_2_1")),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><java.version>1.7</java.version></properties>
                          <dependencies><dependency><groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>23.0.0</version></dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><!--~~(org.jetbrains:annotations 26 requires JDK 8 or newer; raise the build baseline or retain annotations-java5 24.1.0)~~>--><java.version>1.7</java.version></properties>
                          <dependencies><dependency><groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>26.0.2-1</version></dependency></dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void modernSingleModelProjectIsNoOp() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>modern</artifactId><version>1</version>
                          <properties><maven.compiler.release>17</maven.compiler.release></properties>
                          <dependencies><dependency><groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>26.0.2-1</version></dependency></dependencies>
                        </project>
                        """
                ),
                java(
                        """
                        package example;
                        import org.jetbrains.annotations.NotNull;
                        class Service { @NotNull String value() { return "ok"; } }
                        """
                )
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jetbrainsannotations")
                .scanYamlResources()
                .build();
    }
}
