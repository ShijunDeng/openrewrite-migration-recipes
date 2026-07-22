package com.huawei.clouds.openrewrite.jetbrainsannotations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeJetBrainsAnnotationsTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.jetbrainsannotations.UpgradeJetBrainsAnnotationsTo26_0_2_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME))
                .parser(JavaParser.fromJavaVersion().classpath("annotations"));
    }

    @ParameterizedTest(name = "upgrades spreadsheet version {0}")
    @ValueSource(strings = {"23.0.0", "23.1.0", "24.0.0", "24.0.1"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(pomXml(
                pomWithVersion(oldVersion),
                pomWithVersion("26.0.2-1")
        ));
    }

    @Test
    void upgradesTwitter4jGradleDependencyAndLeavesNullabilitySourceUntouched() {
        // Reduced from Twitter4J/Twitter4J at 87ccc41f:
        // https://github.com/Twitter4J/Twitter4J/blob/87ccc41fb14434e328946fa4422460990be7a2d4/twitter4j-core/build.gradle#L54-L60
        // https://github.com/Twitter4J/Twitter4J/blob/87ccc41fb14434e328946fa4422460990be7a2d4/twitter4j-core/src/v1/java/twitter4j/v1/GeoQuery.java
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies {
                            compileOnly 'org.slf4j:slf4j-api:2.0.2'
                            implementation 'org.jetbrains:annotations:23.0.0'
                            testImplementation 'org.junit.jupiter:junit-jupiter-api:5.9.1'
                        }
                        """,
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies {
                            compileOnly 'org.slf4j:slf4j-api:2.0.2'
                            implementation 'org.jetbrains:annotations:26.0.2-1'
                            testImplementation 'org.junit.jupiter:junit-jupiter-api:5.9.1'
                        }
                        """
                ),
                java(
                        """
                        package twitter4j.v1;

                        import org.jetbrains.annotations.NotNull;
                        import org.jetbrains.annotations.Nullable;

                        public final class GeoQuery {
                            @Nullable
                            private final String query;

                            private GeoQuery(@Nullable String query) {
                                this.query = query;
                            }

                            public static GeoQuery ofQuery(@NotNull String query) {
                                return new GeoQuery(query);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void upgradesRoseStackerCompileOnlyDependencyAndLeavesNotNullSourceUntouched() {
        // Reduced from Rosewood-Development/RoseStacker at e0b7f772:
        // https://github.com/Rosewood-Development/RoseStacker/blob/e0b7f772f6cab7a7cf64370d33b3e8dd17d89685/Plugin/build.gradle#L9-L22
        // https://github.com/Rosewood-Development/RoseStacker/blob/e0b7f772f6cab7a7cf64370d33b3e8dd17d89685/Plugin/src/main/java/dev/rosewood/rosestacker/event/StackEvent.java
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        dependencies {
                            compileOnly 'me.clip:placeholderapi:2.11.6'
                            compileOnly 'org.jetbrains:annotations:23.1.0'
                        }
                        """,
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        dependencies {
                            compileOnly 'me.clip:placeholderapi:2.11.6'
                            compileOnly 'org.jetbrains:annotations:26.0.2-1'
                        }
                        """
                ),
                java(
                        """
                        package dev.rosewood.rosestacker.event;

                        import org.jetbrains.annotations.NotNull;

                        public abstract class StackEvent<T> {
                            protected final T stack;

                            protected StackEvent(@NotNull T stack) {
                                this.stack = stack;
                            }

                            @NotNull
                            public T getStack() {
                                return stack;
                            }
                        }
                        """
                )
        );
    }

    @Test
    void upgradesVineflowerImplementationDependencyAndLeavesNullableSpiUntouched() {
        // Reduced from Vineflower/vineflower at b8273988:
        // https://github.com/Vineflower/vineflower/blob/b8273988af850e8cfb234ca08d129058502b032f/build.gradle#L35-L49
        // https://github.com/Vineflower/vineflower/blob/b8273988af850e8cfb234ca08d129058502b032f/src/org/jetbrains/java/decompiler/api/plugin/Plugin.java
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation 'org.jetbrains:annotations:24.0.0'
                            testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.0'
                        }
                        """,
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation 'org.jetbrains:annotations:26.0.2-1'
                            testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.0'
                        }
                        """
                ),
                java(
                        """
                        package org.jetbrains.java.decompiler.api.plugin;

                        import org.jetbrains.annotations.Nullable;

                        public interface Plugin {
                            String id();

                            @Nullable
                            default Object getPluginOptions() {
                                return null;
                            }
                        }
                        """
                )
        );
    }

    @Test
    void upgradesJetpackMvvmApiDependencyAndLeavesMixedNullabilitySourceUntouched() {
        // Reduced from KunMinX/Jetpack-MVVM-Best-Practice at 543eb865:
        // https://github.com/KunMinX/Jetpack-MVVM-Best-Practice/blob/543eb8659089d74ccad403763cb16596febc89b7/architecture/build.gradle#L34-L48
        // https://github.com/KunMinX/Jetpack-MVVM-Best-Practice/blob/543eb8659089d74ccad403763cb16596febc89b7/app/src/main/java/com/kunminx/puremusic/domain/request/AccountRequester.java
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        dependencies {
                            api 'androidx.appcompat:appcompat:1.6.1'
                            api 'org.jetbrains:annotations:24.0.1'
                        }
                        """,
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        dependencies {
                            api 'androidx.appcompat:appcompat:1.6.1'
                            api 'org.jetbrains:annotations:26.0.2-1'
                        }
                        """
                ),
                java(
                        """
                        package com.kunminx.puremusic.domain.request;

                        import org.jetbrains.annotations.NotNull;

                        public class AccountRequester {
                            public void onStop(@NotNull Object owner) {
                            }
                        }
                        """
                )
        );
    }

    @Test
    void upgradesMavenVersionProperty() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>property-app</artifactId><version>1</version>
                  <properties><jetbrains-annotations.version>23.1.0</jetbrains-annotations.version></properties>
                  <dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId>
                    <version>${jetbrains-annotations.version}</version><scope>provided</scope>
                  </dependency></dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>property-app</artifactId><version>1</version>
                  <properties><jetbrains-annotations.version>26.0.2-1</jetbrains-annotations.version></properties>
                  <dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId>
                    <version>${jetbrains-annotations.version}</version><scope>provided</scope>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesDependencyManagementVersionProperty() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>managed-parent</artifactId><version>1</version>
                  <properties><annotations.version>24.0.0</annotations.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId>
                    <version>${annotations.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>managed-parent</artifactId><version>1</version>
                  <properties><annotations.version>26.0.2-1</annotations.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId>
                    <version>${annotations.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesDirectDependencyManagementVersion() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>23.0.0</version><scope>provided</scope>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>26.0.2-1</version><scope>provided</scope>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesDependencyInsideMavenProfile() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiled</artifactId><version>1</version>
                  <profiles><profile><id>quality</id><activation><activeByDefault>true</activeByDefault></activation><dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>24.0.1</version><scope>provided</scope>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiled</artifactId><version>1</version>
                  <profiles><profile><id>quality</id><activation><activeByDefault>true</activeByDefault></activation><dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>26.0.2-1</version><scope>provided</scope>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void upgradesGradleMapNotationAndPreservesTestCompileOnlyConfiguration() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    testCompileOnly group: 'org.jetbrains', name: 'annotations', version: '24.0.0'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    testCompileOnly group: 'org.jetbrains', name: 'annotations', version: '26.0.2-1'
                }
                """
        ));
    }

    @Test
    void leavesGradleVersionVariableForReview() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        def annotationsVersion = '23.0.0'
                        dependencies { compileOnly "org.jetbrains:annotations:$annotationsVersion" }
                        """
                )
        );
    }

    @Test
    void upgradesMultipleExplicitMavenOccurrences() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>multiple</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>23.0.0</version><scope>provided</scope></dependency>
                    <dependency><groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>24.0.1</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>multiple</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>26.0.2-1</version><scope>provided</scope></dependency>
                    <dependency><groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>26.0.2-1</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesMavenScopeOptionalClassifierAndExclusions() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>details</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>23.1.0</version>
                    <classifier>sources</classifier><scope>provided</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>example</groupId><artifactId>legacy</artifactId></exclusion></exclusions>
                  </dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>details</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>26.0.2-1</version>
                    <classifier>sources</classifier><scope>provided</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>example</groupId><artifactId>legacy</artifactId></exclusion></exclusions>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesVersionOutsideSpreadsheetUntouched() {
        rewriteRun(pomXml(pomWithVersion("13.0")));
    }

    @Test
    void leavesMavenManagedVersionlessDependencyUntouched() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>managed-child</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>26.0.2-1</version>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><scope>provided</scope>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesGradleVersionlessDependencyUntouched() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { compileOnly 'org.jetbrains:annotations' }
                """
        ));
    }

    @Test
    void leavesTargetAndSemanticallyLaterVersionsUntouched() {
        rewriteRun(
                pomXml(pomWithVersion("26.0.2-1")),
                pomXml(pomWithVersion("26.0.2"), spec -> spec.path("stable-26.0.2-pom.xml")),
                pomXml(pomWithVersion("26.1.0"), spec -> spec.path("later-pom.xml"))
        );
    }

    @Test
    void upgradesLiteralKotlinGradleDependencyWithoutSemanticModel() {
        rewriteRun(buildGradleKts(
                """
                plugins { `java-library` }
                repositories { mavenCentral() }
                dependencies { compileOnly("org.jetbrains:annotations:24.0.1") }
                """,
                """
                plugins { `java-library` }
                repositories { mavenCentral() }
                dependencies { compileOnly("org.jetbrains:annotations:26.0.2-1") }
                """
        ));
    }

    @Test
    void leavesSharedMavenPropertyUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><annotations.version>23.1.0</annotations.version></properties>
                  <name>quality-${annotations.version}</name>
                  <dependencies><dependency>
                    <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>${annotations.version}</version>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesRangesDynamicVersionsAndExternalManagementUntouched() {
        rewriteRun(
                pomXml(pomWithVersion("[23.0.0,25.0.0)"), spec -> spec.path("range-pom.xml")),
                pomXml(pomWithVersion("LATEST"), spec -> spec.path("dynamic-pom.xml")),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency>
                            <groupId>org.flywaydb</groupId><artifactId>flyway-parent</artifactId><version>11.14.1</version><type>pom</type><scope>import</scope>
                          </dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>org.jetbrains</groupId><artifactId>annotations</artifactId></dependency></dependencies>
                        </project>
                        """,
                        spec -> spec.path("managed-pom.xml")
                )
        );
    }

    @Test
    void doesNotChangeSimilarOrCompanionCoordinates() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>similar</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.jetbrains</groupId><artifactId>annotations-java5</artifactId><version>24.0.1</version></dependency>
                    <dependency><groupId>org.jetbrains</groupId><artifactId>annotations-iosArm64</artifactId><version>26.0.2-1</version></dependency>
                    <dependency><groupId>com.intellij</groupId><artifactId>annotations</artifactId><version>12.0</version></dependency>
                    <dependency><groupId>org.checkerframework</groupId><artifactId>checker-qual</artifactId><version>3.42.0</version></dependency>
                    <dependency><groupId>com.github.spotbugs</groupId><artifactId>spotbugs-annotations</artifactId><version>4.8.3</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void doesNotAddOrRemoveNotNullByDefaultFromSource() {
        rewriteRun(java(
                """
                package example;

                import org.jetbrains.annotations.NotNullByDefault;
                import org.jetbrains.annotations.UnknownNullability;

                @NotNullByDefault
                public class NullMarkedService {
                    public String value() {
                        return "value";
                    }

                    public @UnknownNullability String externalValue() {
                        return null;
                    }
                }
                """
        ));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);
        Recipe migration = environment.activateRecipes(
                "com.huawei.clouds.openrewrite.jetbrainsannotations.MigrateJetBrainsAnnotationsTo26_0_2_1");
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(migration.validate().isValid(), () -> migration.validate().failures().toString());
    }

    private static String pomWithVersion(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>annotations-app</artifactId><version>1</version>
                 <dependencies><dependency>
                   <groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>%s</version><scope>provided</scope>
                 </dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jetbrainsannotations")
                .scanYamlResources()
                .build();
    }
}
