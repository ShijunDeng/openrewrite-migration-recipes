package com.huawei.clouds.openrewrite.guava;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;

class OfficialGuavaReuseTest implements RewriteTest {
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.guava.MigrateGuavaTo33_5_0Jre";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(UpgradeGuavaTest.environment().activateRecipes(MIGRATE))
                .parser(Guava21Parser.parser());
    }

    @Test
    void compositionPinsOnlyTheReviewedOfficialGuavaLeaves() {
        List<Recipe> sourceLeaves = effectiveChildren(activate(
                "com.huawei.clouds.openrewrite.guava.MigrateSelectedGuavaSources"));
        assertEquals(List.of(
                        MigrateCharMatcherConstants.class.getName(),
                        AddGuavaDirectExecutor.class.getName(),
                        "org.openrewrite.java.migrate.guava.NoGuavaDirectExecutor",
                        "org.openrewrite.java.migrate.guava.NoGuavaCreateTempDir"),
                sourceLeaves.stream().map(Recipe::getName).toList());

        List<Recipe> inlineGate = effectiveChildren(activate(
                "com.huawei.clouds.openrewrite.guava.InlineSelectedGuavaMethodsOnJava11"));
        assertEquals(List.of("com.google.guava.InlineGuavaMethods"),
                inlineGate.stream().map(Recipe::getName).toList());

        List<Recipe> generatedInlineLeaves =
                effectiveChildren(activate("com.google.guava.InlineGuavaMethods"));
        assertEquals(65, generatedInlineLeaves.size());
        assertTrue(generatedInlineLeaves.stream()
                .allMatch(recipe -> "org.openrewrite.java.InlineMethodCalls".equals(recipe.getName())));
        assertFalse(allRecipeNames(activate(MIGRATE))
                .contains("org.openrewrite.java.migrate.guava.NoGuava"));
    }

    @Test
    void inlinesOfficialGuavaMethodsOnlyForSelectedJava11Projects() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import com.google.common.base.Strings;

                        class Banner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        """
                        class Banner {
                            String repeat(int count) {
                                return "*".repeat(count);
                            }
                        }
                        """,
                        source -> source.markers(javaVersion(11))
                ),
                selectedPom()
        );
    }

    @Test
    void doesNotInlineJava11ApisIntoJava8Targets() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class Java8Banner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.markers(javaVersion(8))
                ),
                selectedPom()
        );
    }

    @Test
    void doesNotRunOfficialLeavesWithoutAnExactSelectedBuildOwner() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class UnownedBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.markers(javaVersion(11))
                )
        );
    }

    @Test
    void offListAndTargetProjectsRemainOutsideSourceAutomation() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class OffListBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.path("off-list/src/main/java/OffListBanner.java")
                                .markers(javaVersion(11))
                ),
                pomXml(pom("28.2-jre"),
                        source -> source.path("off-list/pom.xml")),
                java(
                        """
                        import com.google.common.base.Strings;

                        class TargetBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.path("target-version/src/main/java/TargetBanner.java")
                                .markers(javaVersion(11))
                ),
                pomXml(pom("33.5.0-jre"),
                        source -> source.path("target-version/pom.xml"))
        );
    }

    @Test
    void mixedVersionsBlockOfficialSourceAutomation() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class MixedBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.path("mixed/src/main/java/MixedBanner.java")
                                .markers(javaVersion(11))
                ),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>mixed</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>31.1-jre</version></dependency>
                            <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>28.2-jre</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>mixed</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>33.5.0-jre</version></dependency>
                            <dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>28.2-jre</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.path("mixed/pom.xml")
                )
        );
    }

    @Test
    void reusesOfficialCreateTempDirectoryInsideIOExceptionBoundary() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import com.google.common.io.Files;
                        import java.io.File;
                        import java.io.IOException;

                        class TempDirectories {
                            File create() throws IOException {
                                return Files.createTempDir();
                            }
                        }
                        """,
                        """
                        import java.io.File;
                        import java.io.IOException;
                        import java.nio.file.Files;

                        class TempDirectories {
                            File create() throws IOException {
                                return Files.createTempDirectory(null).toFile();
                            }
                        }
                        """
                ),
                selectedPom()
        );
    }

    @Test
    void preservesMarkerFallbackWhenCreateTempDirCannotBeChangedSafely() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import com.google.common.io.Files;
                        import java.io.File;

                        class UnsafeTempDirectory {
                            File create() {
                                return Files.createTempDir();
                            }
                        }
                        """,
                        """
                        import com.google.common.io.Files;
                        import java.io.File;

                        class UnsafeTempDirectory {
                            File create() {
                                return /*~~(Files.createTempDir is deprecated and changed security/error behavior; migrate with explicit IOException and permissions handling)~~>*/Files.createTempDir();
                            }
                        }
                        """
                ),
                selectedPom()
        );
    }

    @Test
    void customOverloadBridgeRunsBeforeOfficialDirectExecutorRecipe() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import com.google.common.util.concurrent.FutureCallback;
                        import com.google.common.util.concurrent.Futures;
                        import com.google.common.util.concurrent.ListenableFuture;

                        class Callback {
                            void register(ListenableFuture<String> future, FutureCallback<String> callback) {
                                Futures.addCallback(future, callback);
                            }
                        }
                        """,
                        """
                        import com.google.common.util.concurrent.FutureCallback;
                        import com.google.common.util.concurrent.Futures;
                        import com.google.common.util.concurrent.ListenableFuture;

                        class Callback {
                            void register(ListenableFuture<String> future, FutureCallback<String> callback) {
                                Futures.addCallback(future, callback, Runnable::run);
                            }
                        }
                        """
                ),
                selectedPom()
        );
    }

    @Test
    void selectedProjectGateStillExcludesGeneratedSources() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class GeneratedBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.path("target/generated-sources/GeneratedBanner.java")
                                .markers(javaVersion(11))
                ),
                selectedPom()
        );
    }

    @Test
    void localDependencyManagementSelectsVersionlessConsumers() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import com.google.common.base.Strings;

                        class ManagedBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        """
                        class ManagedBanner {
                            String repeat(int count) {
                                return "*".repeat(count);
                            }
                        }
                        """,
                        source -> source.markers(javaVersion(11))
                ),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>31.1-jre</version>
                          </dependency></dependencies></dependencyManagement>
                          <dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId>
                          </dependency></dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>33.5.0-jre</version>
                          </dependency></dependencies></dependencyManagement>
                          <dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId>
                          </dependency></dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void versionlessConsumerWithoutLocalManagementBlocksSourceAutomation() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class UnknownManagedBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.markers(javaVersion(11))
                ),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>unknown-managed</artifactId><version>1</version>
                          <dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>31.1-jre</version>
                          </dependency></dependencies>
                          <profiles><profile><id>unknown</id><dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId>
                          </dependency></dependencies></profile></profiles>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>unknown-managed</artifactId><version>1</version>
                          <dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>33.5.0-jre</version>
                          </dependency></dependencies>
                          <profiles><profile><id>unknown</id><dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId>
                          </dependency></dependencies></profile></profiles>
                        </project>
                        """
                )
        );
    }

    @Test
    void sharedMavenPropertyBlocksOfficialSourceAutomation() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class SharedPropertyBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.markers(javaVersion(11))
                ),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>shared</artifactId><version>${shared.version}</version>
                          <properties><shared.version>31.1-jre</shared.version></properties>
                          <dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>${shared.version}</version>
                          </dependency></dependencies>
                        </project>
                        """)
        );
    }

    @Test
    void selectedGradleBuildMarksItsJavaSources() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import com.google.common.base.Strings;

                        class GradleBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        """
                        class GradleBanner {
                            String repeat(int count) {
                                return "*".repeat(count);
                            }
                        }
                        """,
                        source -> source.path("gradle-app/src/main/java/GradleBanner.java")
                                .markers(javaVersion(11))
                ),
                buildGradle(
                        "plugins { id 'java' }\ndependencies { implementation 'com.google.guava:guava:31.1-jre' }",
                        "plugins { id 'java' }\ndependencies { implementation 'com.google.guava:guava:33.5.0-jre' }",
                        source -> source.path("gradle-app/build.gradle")
                )
        );
    }

    @Test
    void gradleInterpolationBlocksOfficialSourceAutomation() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class InterpolatedBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.path("interpolated/src/main/java/InterpolatedBanner.java")
                                .markers(javaVersion(11))
                ),
                buildGradle(
                        """
                        plugins { id 'java' }
                        def guavaVersion = '31.1-jre'
                        dependencies { implementation "com.google.guava:guava:$guavaVersion" }
                        """,
                        source -> source.path("interpolated/build.gradle")
                )
        );
    }

    @Test
    void mixedGroovyLiteralAndInterpolationBlockOfficialSourceAutomation() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class MixedGroovyBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.path("mixed-groovy/src/main/java/MixedGroovyBanner.java")
                                .markers(javaVersion(11))
                ),
                buildGradle(
                        """
                        plugins { id 'java' }
                        def guavaVersion = '28.2-jre'
                        dependencies {
                            implementation 'com.google.guava:guava:31.1-jre'
                            testImplementation "com.google.guava:guava:$guavaVersion"
                        }
                        """,
                        """
                        plugins { id 'java' }
                        def guavaVersion = '28.2-jre'
                        dependencies {
                            implementation 'com.google.guava:guava:33.5.0-jre'
                            testImplementation "com.google.guava:guava:$guavaVersion"
                        }
                        """,
                        source -> source.path("mixed-groovy/build.gradle")
                )
        );
    }

    @Test
    void mixedKotlinLiteralAndInterpolationBlockOfficialSourceAutomation() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class MixedKotlinBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.path("mixed-kotlin/src/main/java/MixedKotlinBanner.java")
                                .markers(javaVersion(11))
                ),
                buildGradleKts(
                        """
                        plugins { java }
                        val guavaVersion = "28.2-jre"
                        dependencies {
                            implementation("com.google.guava:guava:31.1-jre")
                            testImplementation("com.google.guava:guava:$guavaVersion")
                        }
                        """,
                        """
                        plugins { java }
                        val guavaVersion = "28.2-jre"
                        dependencies {
                            implementation("com.google.guava:guava:33.5.0-jre")
                            testImplementation("com.google.guava:guava:$guavaVersion")
                        }
                        """,
                        source -> source.path("mixed-kotlin/build.gradle.kts")
                )
        );
    }

    @Test
    void classifiedArtifactBlocksOfficialSourceAutomation() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Strings;

                        class ClassifiedBanner {
                            String repeat(int count) {
                                return Strings.repeat("*", count);
                            }
                        }
                        """,
                        source -> source.markers(javaVersion(11))
                ),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>classified</artifactId><version>1</version>
                          <dependencies><dependency>
                            <groupId>com.google.guava</groupId><artifactId>guava</artifactId>
                            <version>31.1-jre</version><classifier>tests</classifier>
                          </dependency></dependencies>
                        </project>
                        """)
        );
    }

    private static JavaVersion javaVersion(int target) {
        String version = Integer.toString(target);
        return new JavaVersion(Tree.randomId(), "OpenJDK", "Eclipse Adoptium",
                version, version);
    }

    private static org.openrewrite.test.SourceSpecs selectedPom() {
        return pomXml(pom("31.1-jre"), pom("33.5.0-jre"));
    }

    private static String pom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>guava-app</artifactId><version>1</version>
                 <dependencies>
                   <dependency>
                     <groupId>com.google.guava</groupId>
                     <artifactId>guava</artifactId>
                     <version>%s</version>
                   </dependency>
                 </dependencies>
               </project>
               """.formatted(version);
    }

    private static Recipe activate(String name) {
        return unwrap(UpgradeGuavaTest.environment().activateRecipes(name));
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        List<Recipe> children = new ArrayList<>();
        for (Recipe child : recipe.getRecipeList()) {
            Recipe unwrapped = unwrap(child);
            if (!unwrapped.getClass().getName().endsWith("PreconditionBellwether")) {
                children.add(unwrapped);
            }
        }
        return children;
    }

    private static List<String> allRecipeNames(Recipe recipe) {
        List<String> names = new ArrayList<>();
        Recipe unwrapped = unwrap(recipe);
        names.add(unwrapped.getName());
        for (Recipe child : effectiveChildren(unwrapped)) {
            names.addAll(allRecipeNames(child));
        }
        return names;
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) {
            current = delegating.getDelegate();
        }
        return current;
    }
}
