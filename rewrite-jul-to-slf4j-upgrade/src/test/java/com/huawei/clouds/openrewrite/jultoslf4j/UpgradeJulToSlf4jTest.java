package com.huawei.clouds.openrewrite.jultoslf4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.maven.tree.MavenRepositoryMirror;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeJulToSlf4jTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.jultoslf4j.MigrateJulToSlf4jTo2_0_17";
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.jultoslf4j.UpgradeJulToSlf4jDependencyTo2_0_17";

    @Override
    public void defaults(RecipeSpec spec) {
        InMemoryExecutionContext context = new InMemoryExecutionContext(Throwable::printStackTrace);
        MavenExecutionContextView.view(context)
                .setRepositories(List.of(MavenRepository.MAVEN_CENTRAL))
                .setMirrors(List.of(new MavenRepositoryMirror(
                        "central-only", "https://repo.maven.apache.org/maven2", "external:*", true, false, null)));
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
        spec.executionContext(context);
        spec.parser(JavaParser.fromJavaVersion().dependsOn(
                """
                package org.slf4j;
                public interface ILoggerFactory {}
                """,
                """
                package org.slf4j;
                public final class LoggerFactory {
                    public static ILoggerFactory getILoggerFactory() { return null; }
                }
                """,
                """
                package org.slf4j;
                public final class MDC {
                    public static org.slf4j.spi.MDCAdapter getMDCAdapter() { return null; }
                }
                """,
                """
                package org.slf4j;
                public final class MarkerFactory {
                    public static IMarkerFactory getIMarkerFactory() { return null; }
                }
                """,
                """
                package org.slf4j;
                public interface IMarkerFactory {}
                """,
                """
                package org.slf4j.spi;
                public interface MDCAdapter {}
                """,
                """
                package org.slf4j.impl;
                public final class StaticLoggerBinder {
                    public static final String REQUESTED_API_VERSION = "1.7.36";
                    public static StaticLoggerBinder getSingleton() { return null; }
                    public org.slf4j.ILoggerFactory getLoggerFactory() { return null; }
                    public String getLoggerFactoryClassStr() { return null; }
                }
                """,
                """
                package org.slf4j.impl;
                public final class StaticMDCBinder {
                    public static StaticMDCBinder getSingleton() { return null; }
                    public org.slf4j.spi.MDCAdapter getMDCA() { return null; }
                }
                """,
                """
                package org.slf4j.impl;
                public final class StaticMarkerBinder {
                    public static StaticMarkerBinder getSingleton() { return null; }
                    public org.slf4j.IMarkerFactory getMarkerFactory() { return null; }
                }
                """,
                """
                package org.slf4j.bridge;
                public final class SLF4JBridgeHandler {
                    public static void removeHandlersForRootLogger() {}
                    public static void install() {}
                    public static void uninstall() {}
                }
                """
        ));
    }

    @ParameterizedTest(name = "Maven upgrades spreadsheet version {0}")
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesEverySpreadsheetVersionInMaven(String oldVersion) {
        rewriteRun(pomXml(directPom(oldVersion), directPom("2.0.17")));
    }

    @ParameterizedTest(name = "Gradle Groovy upgrades spreadsheet version {0}")
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesEverySpreadsheetVersionInGradle(String oldVersion) {
        rewriteRun(buildGradle(
                gradleBuild(oldVersion, "runtimeOnly"),
                gradleBuild("2.0.17", "runtimeOnly")
        ));
    }

    @ParameterizedTest(name = "Gradle Kotlin upgrades spreadsheet version {0}")
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesEverySpreadsheetVersionInGradleKotlin(String oldVersion) {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradleKts(
                        kotlinGradleBuild(oldVersion),
                        kotlinGradleBuild("2.0.17")
                )
        );
    }

    @ParameterizedTest(name = "Maven property upgrades spreadsheet version {0}")
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesMavenVersionProperty(String oldVersion) {
        rewriteRun(pomXml(
                propertyPom(oldVersion, false),
                propertyPom("2.0.17", false)
        ));
    }

    @ParameterizedTest(name = "managed Maven dependency upgrades spreadsheet version {0}")
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesDirectDependencyManagementEntry(String oldVersion) {
        rewriteRun(pomXml(
                managedPom(oldVersion, false),
                managedPom("2.0.17", false)
        ));
    }

    @ParameterizedTest(name = "managed Maven property upgrades spreadsheet version {0}")
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesDependencyManagementProperty(String oldVersion) {
        rewriteRun(pomXml(
                propertyPom(oldVersion, true),
                propertyPom("2.0.17", true)
        ));
    }

    @ParameterizedTest(name = "active Maven profile upgrades spreadsheet version {0}")
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesDependencyInsideActiveMavenProfile(String oldVersion) {
        rewriteRun(pomXml(
                profilePom(oldVersion),
                profilePom("2.0.17")
        ));
    }

    @ParameterizedTest(name = "preserves Gradle configuration {0}")
    @ValueSource(strings = {"api", "implementation", "runtimeOnly", "testImplementation"})
    void preservesGradleConfiguration(String configuration) {
        rewriteRun(buildGradle(
                gradleBuild("1.7.30", configuration),
                gradleBuild("2.0.17", configuration)
        ));
    }

    @ParameterizedTest(name = "upgrades Gradle map notation {0}")
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesGradleMapNotation(String oldVersion) {
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly group: 'org.slf4j', name: 'jul-to-slf4j', version: '%s'
                }
                """.formatted(oldVersion),
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly group: 'org.slf4j', name: 'jul-to-slf4j', version: '2.0.17'
                }
                """
        ));
    }

    @ParameterizedTest(name = "preserves Gradle variable {0} for strict fail-safe semantics")
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void preservesGradleVersionVariableForManualReview(String oldVersion) {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        def julToSlf4jVersion = '%s'
                        dependencies {
                            runtimeOnly "org.slf4j:jul-to-slf4j:$julToSlf4jVersion"
                        }
                        """.formatted(oldVersion)
                )
        );
    }

    @Test
    void upgradesDirectGradleDependencyFromAuctionApiGateway() {
        // Reduced from sba-indoles/auction-apiGateway-server at ced191a76d65ad83231735892c2c737211148588:
        // https://github.com/sba-indoles/auction-apiGateway-server/blob/ced191a76d65ad83231735892c2c737211148588/build.gradle#L39-L42
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:2.0.0'
                    implementation 'org.slf4j:jcl-over-slf4j:1.7.30'
                    implementation 'org.slf4j:jul-to-slf4j:1.7.30'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:2.0.17'
                    implementation 'org.slf4j:jcl-over-slf4j:2.0.17'
                    implementation 'org.slf4j:jul-to-slf4j:2.0.17'
                }
                """
        ));
    }

    @Test
    void upgradesBridgeAndMarksOldLog4jBindingFromIonosDim() {
        // Reduced from IONOS-Core/dim at 2bbdd1f74731f4400465ae548142a78871922152:
        // https://github.com/IONOS-Core/dim/blob/2bbdd1f74731f4400465ae548142a78871922152/pdns-output/pdns-output/build.gradle#L35-L38
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.apache.logging.log4j:log4j-core:2.17.1'
                    implementation 'org.apache.logging.log4j:log4j-slf4j-impl:2.17.1'
                    implementation 'org.slf4j:jul-to-slf4j:1.7.32'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.apache.logging.log4j:log4j-core:2.17.1'
                    /*~~>*/implementation 'org.apache.logging.log4j:log4j-slf4j-impl:2.17.1'
                    implementation 'org.slf4j:jul-to-slf4j:2.0.17'
                }
                """
        ));
    }

    @Test
    void selectsLog4jSlf4j2ProviderInMavenAndPreservesVersion() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>log4j-provider</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>1.7.30</version></dependency>
                    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-slf4j-impl</artifactId><version>2.20.0</version><scope>runtime</scope></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>log4j-provider</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>2.0.17</version></dependency>
                    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-slf4j2-impl</artifactId><version>2.20.0</version><scope>runtime</scope></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void selectsLog4jSlf4j2ProviderInGradleKotlin() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradleKts(
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies {
                            runtimeOnly("org.slf4j:jul-to-slf4j:1.7.36")
                            runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
                        }
                        """,
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies {
                            runtimeOnly("org.slf4j:jul-to-slf4j:2.0.17")
                            runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
                        }
                        """
                )
        );
    }

    @Test
    void changesOnlyGradleDependencyCallsAndLeavesCoordinateDocumentationUntouched() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def migrationNote = 'org.apache.logging.log4j:log4j-slf4j-impl:2.20.0'
                dependencies {
                    runtimeOnly 'org.slf4j:jul-to-slf4j:1.7.36'
                    runtimeOnly 'org.apache.logging.log4j:log4j-slf4j-impl:2.20.0'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def migrationNote = 'org.apache.logging.log4j:log4j-slf4j-impl:2.20.0'
                dependencies {
                    runtimeOnly 'org.slf4j:jul-to-slf4j:2.0.17'
                    runtimeOnly 'org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0'
                }
                """
        ));
    }

    @Test
    void preservesLog4jBindingWhenJulBridgeVersionIsUnlisted() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly 'org.slf4j:jul-to-slf4j:1.7.35'
                    runtimeOnly 'org.apache.logging.log4j:log4j-slf4j-impl:2.17.2'
                }
                """
        ));
    }

    @Test
    void upgradesKotlinDependencyFromKCatan() {
        // Reduced from croissant676/KCatan at fda582f221ad30301565cb9f5c8522d76c327508:
        // https://github.com/croissant676/KCatan/blob/fda582f221ad30301565cb9f5c8522d76c327508/build.gradle.kts#L27-L30
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradleKts(
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation("ch.qos.logback:logback-classic:1.2.11")
                            implementation("org.slf4j:jul-to-slf4j:1.7.36")
                        }
                        """,
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies {
                            /*~~>*/implementation("ch.qos.logback:logback-classic:1.2.11")
                            implementation("org.slf4j:jul-to-slf4j:2.0.17")
                        }
                        """
                )
        );
    }

    @Test
    void upgradesSharedManagedPropertyFromApacheShardingSphere() {
        // Reduced from apache/shardingsphere at 1668c9378b84b2ad8b27c7535daaf99cff120b34:
        // https://github.com/apache/shardingsphere/blob/1668c9378b84b2ad8b27c7535daaf99cff120b34/pom.xml#L90-L93
        // https://github.com/apache/shardingsphere/blob/1668c9378b84b2ad8b27c7535daaf99cff120b34/pom.xml#L423-L436
        rewriteRun(pomXml(
                sharedSlf4jPropertyPom("1.7.36", "shardingsphere"),
                sharedSlf4jPropertyPom("2.0.17", "shardingsphere")
        ));
    }

    @Test
    void upgradesSharedManagedPropertyFromPmdMigration() {
        // Reduced from pmd/pmd immediately before b45cd3919e2613afc363f67eb58271c433db10b7:
        // https://github.com/pmd/pmd/blob/2214f3405b8ed00c1c8db714977aa5d4e8fcb703/pom.xml#L106-L109
        // https://github.com/pmd/pmd/blob/2214f3405b8ed00c1c8db714977aa5d4e8fcb703/pom.xml#L891-L903
        // The real next commit upgraded the same shared property to this recipe's exact 2.0.17 target.
        rewriteRun(pomXml(
                sharedSlf4jPropertyPom("1.7.36", "pmd"),
                sharedSlf4jPropertyPom("2.0.17", "pmd")
        ));
    }

    @Test
    void preservesMavenDependencyMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>metadata</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>1.7.32</version>
                    <type>jar</type><classifier>sources</classifier><scope>runtime</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>
                  </dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>metadata</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>2.0.17</version>
                    <type>jar</type><classifier>sources</classifier><scope>runtime</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesMultipleExplicitDeclarationsInOneBuild() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly 'org.slf4j:jul-to-slf4j:1.7.30'
                    testRuntimeOnly('org.slf4j:jul-to-slf4j:1.7.30') { transitive = false }
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly 'org.slf4j:jul-to-slf4j:2.0.17'
                    testRuntimeOnly('org.slf4j:jul-to-slf4j:2.0.17') { transitive = false }
                }
                """
        ));
    }

    @Test
    void alignsExplicitSlf4jApiProvidersAndBridges() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>aligned-logging</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>1.7.36</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.30</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>1.7.32</version><scope>runtime</scope></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-nop</artifactId><version>1.7.36</version><scope>test</scope></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-reload4j</artifactId><version>1.7.36</version><scope>test</scope></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId><version>1.7.30</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>1.7.32</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>aligned-logging</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>2.0.17</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>2.0.17</version><scope>runtime</scope></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-nop</artifactId><version>2.0.17</version><scope>test</scope></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-reload4j</artifactId><version>2.0.17</version><scope>test</scope></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId><version>2.0.17</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>2.0.17</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void dependencyOnlyRecipeDoesNotMakeProviderPolicyDecisions() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>dependency-only</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>1.7.30</version></dependency>
                            <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.30</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>dependency-only</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>2.0.17</version></dependency>
                            <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.30</version></dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void migratesSafeStaticBinderAccessorsToPublicSlf4jApi() {
        rewriteRun(java(
                """
                import org.slf4j.impl.StaticLoggerBinder;
                import org.slf4j.impl.StaticMDCBinder;
                import org.slf4j.impl.StaticMarkerBinder;

                class BinderAccess {
                    org.slf4j.ILoggerFactory loggerFactory() {
                        return StaticLoggerBinder.getSingleton().getLoggerFactory();
                    }

                    String loggerFactoryClassName() {
                        return StaticLoggerBinder.getSingleton().getLoggerFactoryClassStr();
                    }

                    org.slf4j.spi.MDCAdapter mdcAdapter() {
                        return StaticMDCBinder.getSingleton().getMDCA();
                    }

                    org.slf4j.IMarkerFactory markerFactory() {
                        return StaticMarkerBinder.getSingleton().getMarkerFactory();
                    }
                }
                """,
                """
                import org.slf4j.LoggerFactory;
                import org.slf4j.MDC;
                import org.slf4j.MarkerFactory;

                class BinderAccess {
                    org.slf4j.ILoggerFactory loggerFactory() {
                        return LoggerFactory.getILoggerFactory();
                    }

                    String loggerFactoryClassName() {
                        return LoggerFactory.getILoggerFactory().getClass().getName();
                    }

                    org.slf4j.spi.MDCAdapter mdcAdapter() {
                        return MDC.getMDCAdapter();
                    }

                    org.slf4j.IMarkerFactory markerFactory() {
                        return MarkerFactory.getIMarkerFactory();
                    }
                }
                """
        ));
    }

    @Test
    void comprehensiveRecipeUpdatesBuildAndSourceTogether() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>full-migration</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>1.7.36</version></dependency>
                            <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.36</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>full-migration</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>2.0.17</version></dependency>
                            <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                          </dependencies>
                        </project>
                        """
                ),
                java(
                        """
                        import org.slf4j.impl.StaticLoggerBinder;

                        class LoggingDiagnostics {
                            org.slf4j.ILoggerFactory loggerFactory() {
                                return StaticLoggerBinder.getSingleton().getLoggerFactory();
                            }
                        }
                        """,
                        """
                        import org.slf4j.LoggerFactory;

                        class LoggingDiagnostics {
                            org.slf4j.ILoggerFactory loggerFactory() {
                                return LoggerFactory.getILoggerFactory();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksJulToSlf4jAndSlf4jJdk14RecursionLoop() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>logging-loop</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>1.7.36</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-jdk14</artifactId><version>1.7.36</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>logging-loop</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>2.0.17</version></dependency>
                    <!--~~>--><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-jdk14</artifactId><version>1.7.36</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void marksPlatformManagedSlf4j1ApiThatCannotBeOverridden() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-api</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>2.7.10</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>1.7.36</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-api</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>2.7.10</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>2.0.17</version></dependency>
                    <!--~~>--><dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void marksUnsupportedStaticBinderMembersAndReflectiveLookup() {
        rewriteRun(java(
                """
                import org.slf4j.impl.StaticLoggerBinder;

                class ProviderInternals {
                    String requestedVersion = StaticLoggerBinder.REQUESTED_API_VERSION;
                    String binderClass = "org.slf4j.impl.StaticLoggerBinder";
                }
                """,
                """
                import org.slf4j.impl.StaticLoggerBinder;

                class ProviderInternals {
                    String requestedVersion = /*~~(SLF4J 2.0 removed the Static*Binder contract; migrate this provider-internal reference manually)~~>*/StaticLoggerBinder.REQUESTED_API_VERSION;
                    String binderClass = /*~~(SLF4J 2.0 removed the Static*Binder contract; migrate this provider-internal reference manually)~~>*/"org.slf4j.impl.StaticLoggerBinder";
                }
                """
        ));
    }

    @ParameterizedTest(name = "unlisted version {0} is a strict no-op")
    @ValueSource(strings = {"1.7.25", "1.7.31", "1.7.35", "2.0.0", "2.0.16"})
    void preservesUnlistedVersions(String version) {
        rewriteRun(pomXml(directPom(version)));
    }

    @Test
    void preservesUnlistedGradleVariableWhenListedLiteralIsUnrelated() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        def julBridgeVersion = '1.7.35'
                        def unrelatedProtocolVersion = '1.7.36'
                        dependencies {
                            runtimeOnly "org.slf4j:jul-to-slf4j:$julBridgeVersion"
                            implementation "example:protocol:$unrelatedProtocolVersion"
                        }
                        """
                )
        );
    }

    @Test
    void preservesTargetVersion() {
        rewriteRun(pomXml(directPom("2.0.17")));
    }

    @Test
    void doesNotDowngradeNewerVersion() {
        rewriteRun(pomXml(directPom("2.0.18")));
    }

    @Test
    void preservesMavenBomManagedVersionlessDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom-managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>2.7.10</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency>
                    <groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><scope>runtime</scope>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesGradlePlatformManagedVersionlessDependency() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation platform('org.springframework.boot:spring-boot-dependencies:2.7.10')
                    runtimeOnly 'org.slf4j:jul-to-slf4j'
                }
                """
        ));
    }

    @Test
    void doesNotChangeSimilarCoordinates() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>similar</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-to-slf4j</artifactId><version>2.17.2</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId><version>1.7.36</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-jdk14</artifactId><version>1.7.36</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesSourceAndRuntimeConfigurationUntouched() {
        rewriteRun(
                spec -> spec.typeValidationOptions(TypeValidation.none()),
                java(
                        """
                        package example;
                        class LoggingBootstrap {
                            static final String COORDINATE = "org.slf4j:jul-to-slf4j:1.7.36";
                            void install() {
                                org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
                                org.slf4j.bridge.SLF4JBridgeHandler.install();
                            }
                        }
                        """,
                        source -> source.path("src/main/java/example/LoggingBootstrap.java")
                ),
                text(
                        "logging.bridge.coordinate=org.slf4j:jul-to-slf4j:1.7.36\n",
                        source -> source.path("src/main/resources/application.properties")
                ),
                text(
                        "logging:\n  bridge: org.slf4j:jul-to-slf4j:1.7.36\n",
                        source -> source.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void leavesUnsupportedVersionCatalogUntouched() {
        rewriteRun(text(
                """
                [versions]
                slf4j = "1.7.36"

                [libraries]
                jul-to-slf4j = { module = "org.slf4j:jul-to-slf4j", version.ref = "slf4j" }
                """,
                source -> source.path("gradle/libs.versions.toml")
        ));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertEquals(6, recipe.getRecipeList().size());
    }

    private static String directPom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>logging</artifactId><version>1</version>
                 <dependencies><dependency>
                   <groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>%s</version><scope>runtime</scope>
                 </dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static String propertyPom(String version, boolean managed) {
        String open = managed ? "<dependencyManagement><dependencies>" : "<dependencies>";
        String close = managed ? "</dependencies></dependencyManagement>" : "</dependencies>";
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                 <properties><jul-to-slf4j.version>%s</jul-to-slf4j.version></properties>
                 %s<dependency>
                   <groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>${jul-to-slf4j.version}</version>
                 </dependency>%s
               </project>
               """.formatted(version, open, close);
    }

    private static String managedPom(String version, boolean withConsumer) {
        String consumer = withConsumer ? """
                <dependencies><dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId></dependency></dependencies>
                """ : "";
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                 <dependencyManagement><dependencies><dependency>
                   <groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>%s</version>
                 </dependency></dependencies></dependencyManagement>
                 %s
               </project>
               """.formatted(version, consumer);
    }

    private static String profilePom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiled</artifactId><version>1</version>
                 <profiles><profile><id>logging</id><activation><activeByDefault>true</activeByDefault></activation>
                   <dependencies><dependency>
                     <groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>%s</version>
                   </dependency></dependencies>
                 </profile></profiles>
               </project>
               """.formatted(version);
    }

    private static String gradleBuild(String version, String configuration) {
        return """
               plugins { id 'java-library' }
               repositories { mavenCentral() }
               dependencies { %s 'org.slf4j:jul-to-slf4j:%s' }
               """.formatted(configuration, version);
    }

    private static String kotlinGradleBuild(String version) {
        return """
               plugins { java }
               repositories { mavenCentral() }
               dependencies { runtimeOnly("org.slf4j:jul-to-slf4j:%s") }
               """.formatted(version);
    }

    private static String sharedSlf4jPropertyPom(String version, String artifactId) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>%s</artifactId><version>1</version>
                 <properties><slf4j.version>%s</slf4j.version></properties>
                 <dependencyManagement><dependencies>
                   <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${slf4j.version}</version></dependency>
                   <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>${slf4j.version}</version></dependency>
                   <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>${slf4j.version}</version></dependency>
                 </dependencies></dependencyManagement>
               </project>
               """.formatted(artifactId, version);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jultoslf4j")
                .scanYamlResources()
                .build();
    }
}
