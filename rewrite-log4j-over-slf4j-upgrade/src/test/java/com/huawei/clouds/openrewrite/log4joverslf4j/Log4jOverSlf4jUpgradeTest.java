package com.huawei.clouds.openrewrite.log4joverslf4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class Log4jOverSlf4jUpgradeTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.log4joverslf4j.UpgradeLog4jOverSlf4jTo2_0_17";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.log4joverslf4j.MigrateLog4jOverSlf4jTo2_0_17";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @Test
    void sourceWhitelistExactlyMatchesVisibleWorkbookRows() {
        assertEquals(Set.of("1.7.30", "1.7.32", "1.7.36"),
                UpgradeSelectedLog4jOverSlf4jDependency.SOURCE_VERSIONS);
        assertEquals("2.0.17", UpgradeSelectedLog4jOverSlf4jDependency.TARGET);
    }

    @ParameterizedTest(name = "Maven {0} -> 2.0.17")
    @ValueSource(strings = {"1.7.30", "1.7.32", "1.7.36"})
    void upgradesEveryWorkbookMavenVersion(String source) {
        rewriteRun(pomXml(pom(source), pom("2.0.17")));
    }

    @Test
    void upgradesRealEternalEngineGradleAtFixedCommit() {
        // windflow-io/EternalEngine@00beead6b31d9d8bd8cde9ed0a94892a4b919fdf, build.gradle#L15-L24
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'org.slf4j:log4j-over-slf4j:1.7.30'
                    runtimeOnly 'org.postgresql:postgresql'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'org.slf4j:log4j-over-slf4j:2.0.17'
                    runtimeOnly 'org.postgresql:postgresql'
                }
                """
        ));
    }

    @Test
    void upgradesRealGrandUnifiedGradleAtFixedCommit() {
        // lemiorhan/grand-unified-divisibility-rule@71ac1c6cbeee863f3191157d41464327155a0c9c, build.gradle#L12-L22
        rewriteRun(buildGradle(
                """
                plugins { id 'groovy' }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:1.7.32'
                    implementation 'org.slf4j:jcl-over-slf4j:1.7.32'
                    implementation 'org.slf4j:log4j-over-slf4j:1.7.32'
                    implementation 'org.slf4j:slf4j-simple:1.7.32'
                }
                """,
                """
                plugins { id 'groovy' }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:1.7.32'
                    implementation 'org.slf4j:jcl-over-slf4j:1.7.32'
                    implementation 'org.slf4j:log4j-over-slf4j:2.0.17'
                    implementation 'org.slf4j:slf4j-simple:1.7.32'
                }
                """
        ));
    }

    @Test
    void upgradesRealIntershopKotlinAtFixedCommit() {
        // intershop/intershop-xsd@2dd25c593ea202d641ee0a5177416019e1303df2, build.gradle.kts#L234-L241
        rewriteRun(buildGradleKts(
                """
                plugins { `java-library` }
                dependencies {
                    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
                    implementation("org.slf4j:log4j-over-slf4j:1.7.36")
                    implementation("org.slf4j:slf4j-api:1.7.36")
                }
                """,
                """
                plugins { `java-library` }
                dependencies {
                    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
                    implementation("org.slf4j:log4j-over-slf4j:2.0.17")
                    implementation("org.slf4j:slf4j-api:1.7.36")
                }
                """
        ));
    }

    @Test
    void upgradesExclusivelyOwnedMavenProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                  <properties><bridge.version>1.7.36</bridge.version></properties><dependencies><dependency>
                    <groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>${bridge.version}</version>
                  </dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                  <properties><bridge.version>2.0.17</bridge.version></properties><dependencies><dependency>
                    <groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>${bridge.version}</version>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesSharedMavenPropertyForFamilyOwner() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><slf4j.version>1.7.32</slf4j.version></properties><dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>${slf4j.version}</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${slf4j.version}</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesLocalDependencyManagementButLeavesBomForItsOwner() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version><dependencyManagement><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-bom</artifactId><version>2.0.17</version><type>pom</type><scope>import</scope></dependency>
                  <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>1.7.36</version></dependency>
                </dependencies></dependencyManagement></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version><dependencyManagement><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-bom</artifactId><version>2.0.17</version><type>pom</type><scope>import</scope></dependency>
                  <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>2.0.17</version></dependency>
                </dependencies></dependencyManagement></project>
                """
        ));
    }

    @Test
    void upgradesExclusivelyOwnedDependencyManagementProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-property</artifactId><version>1</version>
                  <properties><bridge.version>1.7.30</bridge.version></properties><dependencyManagement><dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>${bridge.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-property</artifactId><version>1</version>
                  <properties><bridge.version>2.0.17</bridge.version></properties><dependencyManagement><dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>${bridge.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesProfileAndPreservesDependencyMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version><profiles><profile><id>legacy</id><dependencies><dependency>
                  <groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>1.7.30</version><scope>test</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>
                </dependency></dependencies></profile></profiles></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version><profiles><profile><id>legacy</id><dependencies><dependency>
                  <groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>2.0.17</version><scope>test</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>
                </dependency></dependencies></profile></profiles></project>
                """
        ));
    }

    @ParameterizedTest(name = "does not widen to {0}")
    @ValueSource(strings = {"1.7.25", "1.7.31", "1.7.33", "1.7.35", "2.0.0", "2.0.16", "2.0.17", "2.0.18"})
    void doesNotWidenMavenVersions(String version) {
        rewriteRun(pomXml(pom(version)));
    }

    @Test
    void leavesVariantsVersionlessAndOtherCoordinates() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>negative</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId></dependency>
                  <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>1.7.30</version><classifier>sources</classifier></dependency>
                  <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>1.7.32</version><type>test-jar</type></dependency>
                  <dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId><version>1.7.36</version></dependency>
                  <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-api</artifactId><version>2.20.0</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target/pom.xml", "build/pom.xml", "out/pom.xml", "dist/pom.xml", "generated/pom.xml",
            "GeneratedClient/pom.xml", "install/pom.xml", "INSTALL-image/pom.xml", "node_modules/pom.xml",
            ".m2/cache/pom.xml", ".yarn/cache/pom.xml", "vendor/pom.xml"})
    void skipsGeneratedAndInstalledMavenTrees(String path) {
        rewriteRun(pomXml(pom("1.7.36"), source -> source.path(path)));
    }

    @Test
    void upgradesGroovyStringAndBothMapNotations() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                dependencies {
                    implementation 'org.slf4j:log4j-over-slf4j:1.7.30'
                    testImplementation group: 'org.slf4j', name: 'log4j-over-slf4j', version: '1.7.32'
                    runtimeOnly([group: 'org.slf4j', name: 'log4j-over-slf4j', version: '1.7.36'])
                }
                """,
                """
                plugins { id 'java-library' }
                dependencies {
                    implementation 'org.slf4j:log4j-over-slf4j:2.0.17'
                    testImplementation group: 'org.slf4j', name: 'log4j-over-slf4j', version: '2.0.17'
                    runtimeOnly([group: 'org.slf4j', name: 'log4j-over-slf4j', version: '2.0.17'])
                }
                """
        ));
    }

    @Test
    void leavesGradleVariablesCatalogsPlatformsVariantsAndLookalikeText() {
        rewriteRun(buildGradle(
                """
                def slf4jVersion = '1.7.36'
                dependencies {
                    implementation "org.slf4j:log4j-over-slf4j:${slf4jVersion}"
                    implementation libs.log4j.over.slf4j
                    implementation platform('org.slf4j:slf4j-bom:1.7.36')
                    implementation 'org.slf4j:log4j-over-slf4j:1.7.30:sources'
                    implementation group: 'org.slf4j', name: 'log4j-over-slf4j', version: '1.7.32', classifier: 'sources'
                }
                println 'org.slf4j:log4j-over-slf4j:1.7.36'
                """
        ));
    }

    @Test
    void leavesDependenciesBlockNestedInsideAnotherDslOwner() {
        rewriteRun(buildGradle("subprojects { dependencies { implementation 'org.slf4j:log4j-over-slf4j:1.7.36' } }"));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("1.7.36"), pom("2.0.17")),
                buildGradleKts(
                        "dependencies { implementation(\"org.slf4j:log4j-over-slf4j:1.7.30\") }",
                        "dependencies { implementation(\"org.slf4j:log4j-over-slf4j:2.0.17\") }"));
    }

    @Test
    void recipesAreDiscoverableAndValid() {
        Recipe upgrade = environment().activateRecipes(UPGRADE);
        Recipe migrate = environment().activateRecipes(MIGRATE);
        assertFalse(upgrade.getRecipeList().isEmpty());
        assertFalse(migrate.getRecipeList().isEmpty());
        assertTrue(upgrade.validateAll().stream().allMatch(validation -> validation.isValid()));
        assertTrue(migrate.validateAll().stream().allMatch(validation -> validation.isValid()));
    }

    @ParameterizedTest(name = "migrates {0}.{1}")
    @CsvSource({
            "StaticLoggerBinder,getLoggerFactory,LoggerFactory,getILoggerFactory",
            "StaticMDCBinder,getMDCA,MDC,getMDCAdapter",
            "StaticMarkerBinder,getMarkerFactory,MarkerFactory,getIMarkerFactory"
    })
    void migratesExactStaticBinderChains(String binder, String oldMethod, String owner, String newMethod) {
        String returnType = switch (binder) {
            case "StaticLoggerBinder" -> "org.slf4j.ILoggerFactory";
            case "StaticMDCBinder" -> "org.slf4j.spi.MDCAdapter";
            default -> "org.slf4j.IMarkerFactory";
        };
        rewriteRun(
                spec -> spec.recipe(new MigrateStaticBinderAccess()).parser(binderParser()),
                java(
                        """
                        import org.slf4j.impl.%s;
                        class BindingAccess { Object get() { return %s.getSingleton().%s(); } }
                        """.formatted(binder, binder, oldMethod),
                        """
                        import org.slf4j.%s;

                        class BindingAccess { Object get() { return %s.%s(); } }
                        """.formatted(owner, owner, newMethod)
                )
        );
    }

    @Test
    void leavesLocalBinderLookalikesAndNonCanonicalChainsAlone() {
        rewriteRun(
                spec -> spec.recipe(new MigrateStaticBinderAccess()),
                java("class StaticLoggerBinder { static StaticLoggerBinder getSingleton(){return null;} Object getLoggerFactory(){return null;} } class Local { Object get(){return StaticLoggerBinder.getSingleton().getLoggerFactory();} }")
        );
    }

    @Test
    void leavesAttributedBinderOverloadsAlone() {
        rewriteRun(
                spec -> spec.recipe(new MigrateStaticBinderAccess()).parser(JavaParser.fromJavaVersion().dependsOn(
                        "package org.slf4j.impl; public class StaticLoggerBinder { public static StaticLoggerBinder getSingleton(String key){return null;} public Object getLoggerFactory(String key){return null;} }"
                )),
                java("import org.slf4j.impl.StaticLoggerBinder; class Overloads { Object get(){ return StaticLoggerBinder.getSingleton(\"x\").getLoggerFactory(\"x\"); } }")
        );
    }

    @Test
    void deterministicMigrationSkipsGeneratedJava() {
        rewriteRun(
                spec -> spec.recipe(new MigrateStaticBinderAccess()).parser(binderParser()),
                java(
                        "import org.slf4j.impl.StaticLoggerBinder; class Generated { Object get(){ return StaticLoggerBinder.getSingleton().getLoggerFactory(); } }",
                        source -> source.path("target/generated-sources/Generated.java")
                )
        );
    }

    @Test
    void marksBridgeNoOpConfiguratorAppenderAndCategoryApis() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jJavaRisks()).parser(log4jParser()),
                java(
                        """
                        import org.apache.log4j.*;
                        class LegacyConfig {
                            void configure(Logger logger) {
                                PropertyConfigurator.configure("log4j.properties");
                                BasicConfigurator.configure();
                                FileAppender fileAppender = new FileAppender();
                                Appender appender = null;
                                logger.addAppender(appender);
                                logger.setLevel(Level.DEBUG);
                            }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "only as a stub/no-op or incomplete compatibility surface"))
                )
        );
    }

    @Test
    void marksFatalSeverityMapping() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jJavaRisks()).parser(log4jParser()),
                java(
                        "import org.apache.log4j.Logger; class Fatal { void fail(Logger logger){ logger.fatal(\"boom\"); } }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "maps Log4j FATAL to SLF4J ERROR"))
                )
        );
    }

    @Test
    void marksMissingLog4jNetworkApiFromAttributedStub() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jJavaRisks()).parser(JavaParser.fromJavaVersion().dependsOn(
                        "package org.apache.log4j.net; public class SocketAppender {}")),
                java(
                        "import org.apache.log4j.net.SocketAppender; class Network { SocketAppender appender; }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "not implemented by log4j-over-slf4j"))
                )
        );
    }

    @Test
    void marksCustomSlf4jServiceProvider() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jJavaRisks()).parser(JavaParser.fromJavaVersion().dependsOn(
                        "package org.slf4j.spi; public interface SLF4JServiceProvider {}")),
                java(
                        "import org.slf4j.spi.SLF4JServiceProvider; class CustomProvider implements SLF4JServiceProvider {}",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "custom SLF4J provider must implement"))
                )
        );
    }

    @Test
    void marksProgrammaticProviderSelectionAndLeavesOtherPropertiesAlone() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jJavaRisks()),
                java(
                        """
                        class ProviderSelection {
                            void select() {
                                System.setProperty("slf4j.provider", "example.Provider");
                                System.setProperty("example.provider", "safe");
                            }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "uses the slf4j.provider system property"))
                )
        );
    }

    @Test
    void javaRiskFinderLeavesSupportedLoggerCallsAndLookalikesAlone() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jJavaRisks()).parser(log4jParser()),
                java(
                        """
                        import org.apache.log4j.Logger;
                        class Supported { void log(Logger logger) { logger.info("ok"); logger.error("bad"); } }
                        class LocalPropertyConfigurator { static void configure(String value) {} }
                        """
                )
        );
    }

    @Test
    void marksMavenRecursiveBridgeLegacyProviderManagementVariantAndJava7() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jBuildRisks()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>risks</artifactId><version>1</version>
                          <properties><maven.compiler.release>7</maven.compiler.release></properties>
                          <dependencyManagement><dependencies><dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>1.7.36</version></dependency></dependencies></dependencyManagement>
                          <dependencies>
                            <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>1.7.30</version><classifier>sources</classifier></dependency>
                            <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-reload4j</artifactId><version>2.0.17</version></dependency>
                            <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>1.7.36</version></dependency>
                            <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.36</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "requires Java 8 or newer");
                            assertContains(printed, "outside the workbook source selection");
                            assertContains(printed, "classified or non-JAR");
                            assertContains(printed, "causing an endless recursion");
                            assertContains(printed, "ignores 1.7 StaticLoggerBinder bindings");
                            assertContains(printed, "companion is not in the 2.0 family");
                        })
                )
        );
    }

    @Test
    void marksGradleRecursiveLog4jRuntimeDynamicAndProviderBoundaries() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jBuildRisks()),
                buildGradle(
                        """
                        dependencies {
                            implementation 'org.slf4j:log4j-over-slf4j:1.7.35'
                            implementation 'org.slf4j:log4j-over-slf4j:2.+'
                            implementation libs.log4j.over.slf4j
                            implementation platform('org.slf4j:slf4j-bom:1.7.36')
                            runtimeOnly 'log4j:log4j:1.2.17'
                            runtimeOnly 'org.apache.logging.log4j:log4j-slf4j-impl:2.20.0'
                            runtimeOnly 'org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0'
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "outside the workbook source selection");
                            assertContains(printed, "versionless, property/BOM/catalog-managed");
                            assertContains(printed, "companion is not in the 2.0 family");
                            assertContains(printed, "causing an endless recursion");
                            assertContains(printed, "ignores 1.7 StaticLoggerBinder bindings");
                            assertContains(printed, "selects providers through ServiceLoader");
                        })
                )
        );
    }

    @Test
    void marksLegacyLog4jPropertiesAndXmlFiles() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jConfigurationRisks()),
                properties(
                        "log4j.rootLogger=INFO, stdout\nlog4j.appender.stdout=org.apache.log4j.ConsoleAppender\n",
                        source -> source.path("src/main/resources/log4j.properties").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "does not initialize Log4j 1 configuration"))
                ),
                xml(
                        "<log4j:configuration xmlns:log4j=\"http://jakarta.apache.org/log4j/\"><root/></log4j:configuration>",
                        source -> source.path("src/main/resources/log4j.xml").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "does not initialize Log4j 1 configuration"))
                )
        );
    }

    @Test
    void marksProviderPropertyLegacyLocationAndServiceDescriptor() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jConfigurationRisks()),
                properties(
                        "slf4j.provider=example.CustomProvider\nlogging.config=classpath:log4j.xml\napplication.name=safe\n",
                        source -> source.path("src/main/resources/application.properties").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "discovers providers with ServiceLoader");
                                    assertContains(after.printAll(), "does not initialize Log4j 1 configuration");
                                })
                ),
                text(
                        "example.CustomProvider\n",
                        source -> source.path("src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertContains(after.printAll(), "discovers providers with ServiceLoader"))
                )
        );
    }

    @Test
    void marksEachProviderDescriptorEntryButNotComments() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jConfigurationRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text(
                        "# provider list\nexample.FirstProvider\nexample.SecondProvider # fallback",
                        source -> source.path("src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertEquals(2, occurrences(printed, "discovers providers with ServiceLoader"));
                                    assertContains(printed, "# provider list");
                                    assertContains(printed, "# fallback");
                                })
                )
        );
    }

    @Test
    void configurationFinderSkipsGeneratedAndUnrelatedFiles() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4jOverSlf4jConfigurationRisks()),
                properties("log4j.rootLogger=INFO\n", source -> source.path("target/classes/log4j.properties")),
                properties("logging.config=classpath:logback.xml\napplication.name=safe\n",
                        source -> source.path("src/main/resources/application.properties")),
                xml("<configuration/>", source -> source.path("src/main/resources/logback.xml"))
        );
    }

    @Test
    void recommendedRecipeUpgradesRealGraphAndMarksProviderAlignment() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                buildGradle(
                        """
                        dependencies {
                            implementation 'org.slf4j:slf4j-api:1.7.32'
                            implementation 'org.slf4j:log4j-over-slf4j:1.7.32'
                            implementation 'org.slf4j:slf4j-simple:1.7.32'
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "log4j-over-slf4j:2.0.17");
                            assertContains(printed, "companion is not in the 2.0 family");
                            assertContains(printed, "ignores 1.7 StaticLoggerBinder bindings");
                        })
                )
        );
    }

    @Test
    void recommendedRecipeResolvesLocalManagedVersionlessConsumerWithoutFalseMarker() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-app</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>1.7.36</version></dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId></dependency></dependencies>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>2.0.17</version>");
                            assertFalse(after.printAll().contains("outside the workbook source selection"));
                            assertFalse(after.printAll().contains("versionless, property/BOM/catalog-managed"));
                        })
                )
        );
    }

    @Test
    void rootManagementAppliesToProfileConsumerWithoutFalseMarker() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed-profile</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>1.7.30</version></dependency></dependencies></dependencyManagement>
                          <profiles><profile><id>runtime</id><dependencies><dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId></dependency></dependencies></profile></profiles>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>2.0.17</version>");
                            assertFalse(after.printAll().contains("outside the workbook source selection"));
                            assertFalse(after.printAll().contains("versionless, property/BOM/catalog-managed"));
                        })
                )
        );
    }

    @Test
    void profileManagementOverridesRootWhenAuditingConsumer() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-override</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>2.0.17</version></dependency></dependencies></dependencyManagement>
                          <profiles><profile><id>legacy</id>
                            <dependencyManagement><dependencies><dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>1.7.35</version></dependency></dependencies></dependencyManagement>
                            <dependencies><dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId></dependency></dependencies>
                          </profile></profiles>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertEquals(2, occurrences(after.printAll(), "outside the workbook source selection")))
                )
        );
    }

    @Test
    void recommendedRecipeResolvesUpgradedExclusivePropertyWithoutFalseMarker() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property-app</artifactId><version>1</version>
                          <properties><bridge.version>1.7.30</bridge.version></properties><dependencies>
                            <dependency><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>${bridge.version}</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<bridge.version>2.0.17</bridge.version>");
                            assertFalse(after.printAll().contains("outside the workbook source selection"));
                            assertFalse(after.printAll().contains("versionless, property/BOM/catalog-managed"));
                        })
                )
        );
    }

    @Test
    void recommendedRecipeIsStableAcrossTwoCycles() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)).parser(binderParser())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("1.7.36"), pom("2.0.17")),
                java(
                        """
                        import org.slf4j.impl.StaticLoggerBinder;

                        class Access { Object get(){return StaticLoggerBinder.getSingleton().getLoggerFactory();} }
                        """,
                        """
                        import org.slf4j.LoggerFactory;

                        class Access { Object get(){return LoggerFactory.getILoggerFactory();} }
                        """
                ),
                properties(
                        "slf4j.provider=example.Provider\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "discovers providers with ServiceLoader"))
                )
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static JavaParser.Builder<?, ?> log4jParser() {
        return JavaParser.fromJavaVersion().classpath("log4j-over-slf4j", "slf4j-api");
    }

    private static JavaParser.Builder<?, ?> binderParser() {
        return JavaParser.fromJavaVersion().classpath("slf4j-api").dependsOn(
                "package org.slf4j.impl; import org.slf4j.ILoggerFactory; public class StaticLoggerBinder { public static StaticLoggerBinder getSingleton(){return null;} public ILoggerFactory getLoggerFactory(){return null;} }",
                "package org.slf4j.impl; import org.slf4j.spi.MDCAdapter; public class StaticMDCBinder { public static StaticMDCBinder getSingleton(){return null;} public MDCAdapter getMDCA(){return null;} }",
                "package org.slf4j.impl; import org.slf4j.IMarkerFactory; public class StaticMarkerBinder { public static StaticMarkerBinder getSingleton(){return null;} public IMarkerFactory getMarkerFactory(){return null;} }"
        );
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency>
                 <groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected output to contain: " + expected + "\nActual:\n" + actual);
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(token, index)) >= 0; index += token.length()) count++;
        return count;
    }
}
