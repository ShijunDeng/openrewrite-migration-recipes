package com.huawei.clouds.openrewrite.curatorframework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class UpgradeCuratorFrameworkTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.curatorframework.UpgradeCuratorFrameworkTo5_7_1";
    private static final String MIGRATION_RECIPE_NAME =
            "com.huawei.clouds.openrewrite.curatorframework.MigrateCuratorFrameworkTo5_7_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @ParameterizedTest(name = "upgrades workbook-visible source {0}")
    @ValueSource(strings = {"2.7.1", "5.2.0", "5.3.0", "5.4.0"})
    void upgradesEverySpreadsheetSourceVersion(String version) {
        rewriteRun(pomXml(
                pomWithVersion(version),
                pomWithVersion("5.7.1")
        ));
    }

    @Test
    void sourceVersionWhitelistExactlyMatchesWorkbookCells() {
        assertEquals(Set.of("2.7.1", "5.2.0", "5.3.0", "5.4.0"),
                UpgradeSelectedCuratorFrameworkDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesSharedPropertyFromApacheLinkis() {
        // Reduced from apache/linkis at 974438c:
        // https://github.com/apache/linkis/blob/974438c/pom.xml#L115-L117
        // https://github.com/apache/linkis/blob/974438c/pom.xml#L544-L557
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.apache.linkis</groupId><artifactId>linkis</artifactId><version>1</version>
                  <properties>
                    <zookeeper.version>3.8.4</zookeeper.version>
                    <curator.version>2.7.1</curator.version>
                  </properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-recipes</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.apache.linkis</groupId><artifactId>linkis</artifactId><version>1</version>
                  <properties>
                    <zookeeper.version>3.8.4</zookeeper.version>
                    <curator.version>5.7.1</curator.version>
                  </properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-recipes</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesSharedPropertyFromSeldonServer() {
        // Reduced from SeldonIO/seldon-server at bbb7566:
        // https://github.com/SeldonIO/seldon-server/blob/bbb7566/server/pom.xml#L20-L23
        // https://github.com/SeldonIO/seldon-server/blob/bbb7566/server/pom.xml#L452-L468
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>io.seldon</groupId><artifactId>seldon-server</artifactId><version>1.4.10</version>
                  <properties><curator.version>2.7.1</curator.version></properties>
                  <dependencies>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-recipes</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-test</artifactId><version>${curator.version}</version><scope>test</scope></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>io.seldon</groupId><artifactId>seldon-server</artifactId><version>1.4.10</version>
                  <properties><curator.version>5.7.1</curator.version></properties>
                  <dependencies>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-recipes</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-test</artifactId><version>${curator.version}</version><scope>test</scope></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesManagedPropertyFromApacheHadoopAndLeavesZooKeeperVisible() {
        // Reduced from apache/hadoop release-2.7.1 at ac0538a:
        // https://github.com/apache/hadoop/blob/ac0538aac347bfd97cc0dee1db49db503c15f1d9/hadoop-project/pom.xml#L75-L76
        // https://github.com/apache/hadoop/blob/ac0538aac347bfd97cc0dee1db49db503c15f1d9/hadoop-project/pom.xml#L931-L940
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.apache.hadoop</groupId><artifactId>hadoop-project</artifactId><version>2.7.1</version>
                  <properties><zookeeper.version>3.4.6</zookeeper.version><curator.version>2.7.1</curator.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeper</artifactId><version>${zookeeper.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.apache.hadoop</groupId><artifactId>hadoop-project</artifactId><version>2.7.1</version>
                  <properties><zookeeper.version>3.4.6</zookeeper.version><curator.version>5.7.1</curator.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeper</artifactId><version>${zookeeper.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesGradleDependencyFromApacheMyriadAndPreservesExclusion() {
        // Reduced from apache/incubator-myriad at 9bd85f6:
        // https://github.com/apache/incubator-myriad/blob/9bd85f6/myriad-scheduler/build.gradle#L39-L41
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    compile ("org.apache.curator:curator-framework:2.7.1") {
                        exclude group: "com.google.guava"
                    }
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    compile ("org.apache.curator:curator-framework:5.7.1") {
                        exclude group: "com.google.guava"
                    }
                }
                """
        ));
    }

    @Test
    void upgradesDirectDependencyInsideProfile() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiled</artifactId><version>1</version>
                  <profiles><profile><id>coordination</id><activation><activeByDefault>true</activeByDefault></activation><dependencies><dependency>
                    <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>2.7.1</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiled</artifactId><version>1</version>
                  <profiles><profile><id>coordination</id><activation><activeByDefault>true</activeByDefault></activation><dependencies><dependency>
                    <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>5.7.1</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void upgradesProfileLocalProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-property</artifactId><version>1</version>
                  <profiles><profile><id>legacy</id><activation><activeByDefault>true</activeByDefault></activation><properties><curator.version>2.7.1</curator.version></properties><dependencies><dependency>
                    <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-property</artifactId><version>1</version>
                  <profiles><profile><id>legacy</id><activation><activeByDefault>true</activeByDefault></activation><properties><curator.version>5.7.1</curator.version></properties><dependencies><dependency>
                    <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void preservesMavenMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>metadata</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>2.7.1</version>
                  <scope>test</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>com.google.guava</groupId><artifactId>guava</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>metadata</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>5.7.1</version>
                  <scope>test</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>com.google.guava</groupId><artifactId>guava</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void upgradesGradleStringNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'org.apache.curator:curator-framework:2.7.1' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'org.apache.curator:curator-framework:5.7.1' }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    testImplementation group: 'org.apache.curator', name: 'curator-framework', version: '2.7.1'
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    testImplementation group: 'org.apache.curator', name: 'curator-framework', version: '5.7.1'
                }
                """
        ));
    }

    @Test
    void leavesGradleInterpolatedVersionVariableForItsOwner() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def curatorVersion = '2.7.1'
                dependencies { implementation "org.apache.curator:curator-framework:${curatorVersion}" }
                """
        ));
    }

    @Test
    void leavesGradleMapVersionVariableForItsOwner() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def curatorVersion = '2.7.1'
                dependencies {
                    runtimeOnly group: 'org.apache.curator', name: 'curator-framework', version: curatorVersion
                }
                """
        ));
    }

    @Test
    void upgradesLiteralGradleKotlinStringNotationWithoutSemanticModel() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("org.apache.curator:curator-framework:2.7.1") }
                """,
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("org.apache.curator:curator-framework:5.7.1") }
                """
        ));
    }

    @Test
    void leavesGradleKotlinVersionVariableWithoutSemanticModelUntouched() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                val curatorVersion = "2.7.1"
                dependencies { implementation("org.apache.curator:curator-framework:$curatorVersion") }
                """
        ));
    }

    @Test
    void preservesTargetVersion() {
        rewriteRun(pomXml(pomWithVersion("5.7.1")));
    }

    @ParameterizedTest(name = "does not guess spreadsheet-external version {0}")
    @ValueSource(strings = {"2.7.0", "4.2.0"})
    void doesNotUpgradeSpreadsheetExternalVersions(String version) {
        rewriteRun(pomXml(pomWithVersion(version)));
    }

    @Test
    void leavesPropertyEmbeddedInUnrelatedMetadataUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared-metadata</artifactId><version>1</version>
                  <properties><curator.version>2.7.1</curator.version></properties>
                  <name>coordination-${curator.version}</name>
                  <dependencies><dependency>
                    <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @ParameterizedTest(name = "does not downgrade newer version {0}")
    @ValueSource(strings = {"5.8.0", "5.9.0"})
    void doesNotDowngradeNewerVersion(String version) {
        rewriteRun(pomXml(pomWithVersion(version)));
    }

    @ParameterizedTest(name = "does not change non-target Curator artifact {0}")
    @ValueSource(strings = {"curator-client", "curator-recipes", "curator-test", "curator-x-discovery"})
    void doesNotChangeOtherCuratorArtifacts(String artifactId) {
        rewriteRun(pomXml(pomWithCoordinate("org.apache.curator", artifactId, "2.7.1")));
    }

    @Test
    void doesNotChangeSameArtifactFromDifferentGroup() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'example.curator:curator-framework:2.7.1' }
                """
        ));
    }

    @Test
    void preservesVersionlessDependencyManagedByExternalParent() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.apache.hadoop</groupId><artifactId>hadoop-project</artifactId><version>2.7.1</version><relativePath/></parent>
                  <groupId>example</groupId><artifactId>managed</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void preservesVersionlessDependencyManagedByImportedBom() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom-managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-zookeeper-dependencies</artifactId><version>3.0.1</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesVersionlessGradleDependency() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'org.apache.curator:curator-framework' }
                """
        ));
    }

    @Test
    void doesNotChangeUnusedCuratorProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>client-only</artifactId><version>1</version>
                  <properties><curator.version>2.7.1</curator.version></properties>
                  <dependencies><dependency>
                    <groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesMavenPluginDependencyUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-dependency</artifactId><version>1</version>
                  <build><plugins><plugin><groupId>com.acme</groupId><artifactId>generator</artifactId><version>1</version><dependencies><dependency>
                    <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>2.7.1</version>
                  </dependency></dependencies></plugin></plugins></build>
                </project>
                """
        ));
    }

    @Test
    void requiresRealMavenAndGradleDependencyOwnershipAndStandardVariants() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>fake</artifactId><version>1</version>
                          <configuration><dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>5.2.0</version></dependency></dependencies></configuration>
                          <configuration><profiles><profile><dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>5.3.0</version></dependency></dependencies></profile></profiles></configuration>
                          <build><plugins><plugin><artifactId>fake</artifactId><configuration><project><dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>5.4.0</version></dependency></dependencies></project></configuration></plugin></plugins></build>
                        </project>
                        """
                ),
                buildGradle(
                        """
                        implementation 'org.apache.curator:curator-framework:5.2.0'
                        custom { implementation 'org.apache.curator:curator-framework:5.3.0' }
                        dependencies {
                            generatedFixture { implementation 'org.apache.curator:curator-framework:5.2.0' }
                            implementation group: 'org.apache.curator', name: 'curator-framework', version: '5.4.0', classifier: 'tests'
                            implementation([group: 'org.apache.curator', name: 'curator-framework', version: '5.2.0', ext: 'zip'])
                        }
                        """,
                        source -> source.path("ownership.gradle")
                ),
                buildGradleKts(
                        "implementation(\"org.apache.curator:curator-framework:5.4.0\")",
                        source -> source.path("outside.gradle.kts")
                ),
                pomXml(pomWithVersion("5.3.0"), source -> source.path("target/generated/pom.xml")),
                buildGradle(
                        "dependencies { implementation 'org.apache.curator:curator-framework:5.4.0' }",
                        source -> source.path("install/generated/dependencies.gradle")
                )
        );
    }

    @Test
    void leavesDuplicateMavenPropertyDefinitionsUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicates</artifactId><version>1</version>
                  <properties><curator.version>5.2.0</curator.version></properties>
                  <profiles><profile><id>alternate</id><properties><curator.version>5.3.0</curator.version></properties></profile></profiles>
                  <dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void leavesClassifiedMavenArtifactUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>classified</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>2.7.1</version>
                  <type>test-jar</type><classifier>tests</classifier><scope>test</scope>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void migratesRemovedListenerContainerConstructionToStandardManager() {
        // Reduced from Apache Curator 2.7.0 ConnectionStateManager at 206a590:
        // https://github.com/apache/curator/blob/206a59043cb94fe51dbd878b080f68d1c0b7595e/curator-framework/src/main/java/org/apache/curator/framework/state/ConnectionStateManager.java#L68
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE_NAME))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.apache.curator.framework.listen;

                                import java.util.concurrent.Executor;

                                public class ListenerContainer<T> {
                                    public ListenerContainer() {}
                                    public void addListener(T listener) {}
                                    public void addListener(T listener, Executor executor) {}
                                    public void removeListener(T listener) {}
                                    public void clear() {}
                                    public int size() { return 0; }
                                }
                                """,
                                """
                                package org.apache.curator.framework.listen;

                                import java.util.concurrent.Executor;

                                public class StandardListenerManager<T> {
                                    private StandardListenerManager() {}
                                    public static <T> StandardListenerManager<T> standard() { return null; }
                                    public void addListener(T listener) {}
                                    public void addListener(T listener, Executor executor) {}
                                    public void removeListener(T listener) {}
                                    public void clear() {}
                                    public int size() { return 0; }
                                }
                                """
                        )),
                java(
                        """
                        package example;

                        import org.apache.curator.framework.listen.ListenerContainer;

                        class ListenerRegistry {
                            private final ListenerContainer<Runnable> listeners = new ListenerContainer<>();

                            void register(Runnable listener) {
                                listeners.addListener(listener);
                            }
                        }
                        """,
                        """
                        package example;

                        import org.apache.curator.framework.listen.StandardListenerManager;

                        class ListenerRegistry {
                            private final StandardListenerManager<Runnable> listeners = StandardListenerManager.standard();

                            void register(Runnable listener) {
                                listeners.addListener(listener);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksRemovedRetryLoopClassificationForManualRetryDesign() {
        // Real Curator 2.7.0 call shapes at the fixed 206a590 commit:
        // https://github.com/apache/curator/blob/206a59043cb94fe51dbd878b080f68d1c0b7595e/curator-framework/src/main/java/org/apache/curator/framework/imps/CuratorFrameworkImpl.java#L512
        // https://github.com/apache/curator/blob/206a59043cb94fe51dbd878b080f68d1c0b7595e/curator-framework/src/main/java/org/apache/curator/framework/imps/DeleteBuilderImpl.java#L254
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE_NAME))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.apache.curator;
                                public class RetryLoop {
                                    public static boolean shouldRetry(int rc) { return false; }
                                    public static boolean isRetryException(Throwable error) { return false; }
                                }
                                """
                        )),
                java(
                        """
                        package example;

                        import org.apache.curator.RetryLoop;

                        class RetryClassifier {
                            boolean retry(int resultCode) {
                                return RetryLoop.shouldRetry(resultCode);
                            }
                        }
                        """,
                        """
                        package example;

                        import org.apache.curator.RetryLoop;

                        class RetryClassifier {
                            boolean retry(int resultCode) {
                                return /*~~(RetryLoop.shouldRetry(int) was removed; let Curator and RetryPolicy drive retries or classify KeeperException.Code at the business boundary while preserving idempotency and interruption)~~>*/RetryLoop.shouldRetry(resultCode);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksListenerForEachBecauseGuavaFunctionNeedsSemanticMigration() {
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE_NAME))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package com.google.common.base;
                                public interface Function<F, T> { T apply(F input); }
                                """,
                                """
                                package org.apache.curator.framework.listen;
                                import com.google.common.base.Function;
                                public class ListenerContainer<T> {
                                    public void forEach(Function<T, Void> function) {}
                                }
                                """
                        )),
                java(
                        """
                        package example;

                        import org.apache.curator.framework.listen.ListenerContainer;

                        class ListenerNotifier {
                            void notify(ListenerContainer<Runnable> listeners) {
                                listeners.forEach(listener -> {
                                    listener.run();
                                    return null;
                                });
                            }
                        }
                        """,
                        """
                        package example;

                        import org.apache.curator.framework.listen.StandardListenerManager;

                        class ListenerNotifier {
                            void notify(StandardListenerManager<Runnable> listeners) {
                                /*~~(ListenerContainer.forEach used a Guava Function whose return value is discarded; migrate to StandardListenerManager.forEach with a Consumer only after removing return-null semantics and reviewing listener exceptions)~~>*/listeners.forEach(listener -> {
                                    listener.run();
                                    return null;
                                });
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksDeprecatedCachesForCuratorCacheRedesign() {
        // NodeCache is retained as a minimized real type from Apache Curator 2.7.0 at 206a590:
        // https://github.com/apache/curator/blob/206a59043cb94fe51dbd878b080f68d1c0b7595e/curator-recipes/src/main/java/org/apache/curator/framework/recipes/cache/NodeCache.java#L63
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE_NAME))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.apache.curator.framework.recipes.cache;
                                public class NodeCache {}
                                """
                        )),
                java(
                        """
                        package example;

                        import org.apache.curator.framework.recipes.cache.NodeCache;

                        class LegacyCacheOwner {
                            NodeCache cache;
                        }
                        """,
                        """
                        package example;

                        import org.apache.curator.framework.recipes.cache.NodeCache;

                        class LegacyCacheOwner {
                            /*~~(NodeCache is a legacy cache API; migrate deliberately to CuratorCache SINGLE_NODE_CACHE and verify initialization, missing/deleted nodes, payloads, reconnect, and close ordering)~~>*/NodeCache cache;
                        }
                        """
                )
        );
    }

    @Test
    void marksRemovedCloseableExecutorOverload() {
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE_NAME))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.apache.curator.utils;
                                public class CloseableExecutorService {}
                                """,
                                """
                                package org.apache.curator.x.discovery;
                                import org.apache.curator.utils.CloseableExecutorService;
                                public interface ServiceCacheBuilder<T> {
                                    ServiceCacheBuilder<T> executorService(CloseableExecutorService executor);
                                }
                                """
                        )),
                java(
                        """
                        package example;

                        import org.apache.curator.utils.CloseableExecutorService;
                        import org.apache.curator.x.discovery.ServiceCacheBuilder;

                        class DiscoveryExecutorOwner {
                            void configure(ServiceCacheBuilder<String> builder, CloseableExecutorService executor) {
                                builder.executorService(executor);
                            }
                        }
                        """,
                        """
                        package example;

                        import org.apache.curator.utils.CloseableExecutorService;
                        import org.apache.curator.x.discovery.ServiceCacheBuilder;

                        class DiscoveryExecutorOwner {
                            void configure(ServiceCacheBuilder<String> builder, CloseableExecutorService executor) {
                                /*~~(The CloseableExecutorService overload was removed; select a supported executor overload or default and explicitly verify executor ownership, shutdown ordering, rejection, thread leakage, and process exit)~~>*/builder.executorService(executor);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void upgradesCuratorAndMarksUnsupportedZooKeeper34() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE_NAME)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy-zookeeper</artifactId><version>1</version><dependencies>
                          <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>2.7.1</version></dependency>
                          <dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeper</artifactId><version>3.4.14</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>legacy-zookeeper</artifactId><version>1</version><dependencies>
                          <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>5.7.1</version></dependency>
                          <dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeper</artifactId><!--~~(Curator 5 no longer supports ZooKeeper 3.4.x; upgrade the owning client/BOM and ensemble plan, then verify TLS/SASL, ACLs, watches, sessions, container nodes, and rolling compatibility)~~>--><version>3.4.14</version></dependency>
                        </dependencies></project>
                        """
                )
        );
    }

    @Test
    void marksAllOfficialRemovedApiFamiliesAndIgnoresSameNamedApplicationApis() {
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE_NAME))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator.ensemble.exhibitor; public class ExhibitorEnsembleProvider {}",
                                "package org.apache.curator; public interface ConnectionHandlingPolicy {}",
                                "package org.apache.curator; public final class RetryLoop { public static boolean isRetryException(Throwable error) { return false; } }",
                                "package org.apache.curator.framework.recipes.locks; public class Reaper {}",
                                "package org.apache.curator.framework.recipes.locks; public class ChildReaper {}",
                                "package org.apache.curator.framework.recipes.cache; public class PathChildrenCache {}",
                                "package org.apache.curator.framework.recipes.cache; public class TreeCache {}",
                                "package org.apache.curator.framework.recipes.nodes; public class GroupMember { public Object newPersistentEphemeralNode() { return null; } public Object newPathChildrenCache() { return null; } }",
                                "package org.apache.curator.utils; public class CloseableExecutorService {}",
                                "package org.apache.curator.x.discovery; import org.apache.curator.utils.CloseableExecutorService; public interface ServiceProviderBuilder<T> { ServiceProviderBuilder<T> executorService(CloseableExecutorService executor); }"
                        )),
                java(
                        """
                        package example;
                        import org.apache.curator.ConnectionHandlingPolicy;
                        import org.apache.curator.RetryLoop;
                        import org.apache.curator.ensemble.exhibitor.ExhibitorEnsembleProvider;
                        import org.apache.curator.framework.recipes.cache.PathChildrenCache;
                        import org.apache.curator.framework.recipes.cache.TreeCache;
                        import org.apache.curator.framework.recipes.locks.ChildReaper;
                        import org.apache.curator.framework.recipes.locks.Reaper;
                        import org.apache.curator.framework.recipes.nodes.GroupMember;
                        import org.apache.curator.utils.CloseableExecutorService;
                        import org.apache.curator.x.discovery.ServiceProviderBuilder;
                        class LegacyBoundaries {
                            ExhibitorEnsembleProvider exhibitor;
                            ConnectionHandlingPolicy policy;
                            Reaper reaper;
                            ChildReaper childReaper;
                            PathChildrenCache children;
                            TreeCache tree;
                            void configure(GroupMember group, ServiceProviderBuilder<String> provider,
                                           CloseableExecutorService executor, Throwable failure) {
                                group.newPersistentEphemeralNode();
                                group.newPathChildrenCache();
                                RetryLoop.isRetryException(failure);
                                provider.executorService(executor);
                            }
                        }
                        """,
                        source -> source.path("src/main/java/example/LegacyBoundaries.java")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String actual = after.printAll();
                                    assertContains(actual, "removed Exhibitor support");
                                    assertContains(actual, "ConnectionHandlingPolicy was removed");
                                    assertContains(actual, "Reaper was removed");
                                    assertContains(actual, "ChildReaper was removed");
                                    assertContains(actual, "PathChildrenCache is a legacy cache API");
                                    assertContains(actual, "TreeCache is a legacy cache API");
                                    assertContains(actual, "GroupMember.newPersistentEphemeralNode was removed");
                                    assertContains(actual, "GroupMember.newPathChildrenCache was removed");
                                    assertContains(actual, "RetryLoop.isRetryException(Throwable) was removed");
                                    assertContains(actual, "CloseableExecutorService overload was removed");
                                })
                ),
                java(
                        """
                        package example;
                        class LocalRetryLoop { static boolean isRetryException(Throwable error) { return false; } }
                        class LocalGroupMember { Object newPathChildrenCache() { return null; } }
                        class LocalApis { boolean run(Throwable error) { return LocalRetryLoop.isRetryException(error); } }
                        """,
                        source -> source.path("src/main/java/example/LocalApis.java")
                )
        );
    }

    @Test
    void marksOwnedBuildAndParsedConfigurationRisksPrecisely() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE_NAME)),
                buildGradle(
                        """
                        dependencies {
                            implementation group: 'org.apache.curator', name: 'curator-framework', version: '5.7.1'
                            implementation group: 'org.apache.curator', name: 'curator-recipes', version: '5.4.0'
                            runtimeOnly group: 'org.apache.zookeeper', name: 'zookeeper', version: '3.4.14'
                        }
                        """,
                        source -> source.path("owned.gradle").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "explicit Curator companion is not aligned");
                            assertContains(after.printAll(), "Curator 5 no longer supports ZooKeeper 3.4.x");
                        })
                ),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ambiguous</artifactId><version>1</version><dependencies>
                          <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId></dependency>
                          <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>5.4.0</version><type>test-jar</type></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ambiguous</artifactId><version>1</version><dependencies>
                          <!--~~(This Curator version is versionless, property-managed, ranged, dynamic, or otherwise not a fixed visible value; migrate its actual parent/BOM/catalog/property owner and verify that the complete Curator family resolves to 5.7.1)~~>--><dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId></dependency>
                          <!--~~(This classified or non-JAR Curator/ZooKeeper artifact is outside deterministic runtime upgrade scope; verify that the target release publishes the same artifact shape before migrating it)~~>--><dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>5.4.0</version><type>test-jar</type></dependency>
                        </dependencies></project>
                        """,
                        source -> source.path("ambiguous/pom.xml")
                ),
                buildGradle(
                        """
                        dependencies {
                            implementation 'org.apache.curator:curator-framework:5.4.0:tests'
                            runtimeOnly 'org.apache.curator:curator-framework:5.+'
                        }
                        """,
                        """
                        dependencies {
                            implementation /*~~(This classified or non-JAR Curator/ZooKeeper artifact is outside deterministic runtime upgrade scope; verify that the target release publishes the same artifact shape before migrating it)~~>*/'org.apache.curator:curator-framework:5.4.0:tests'
                            runtimeOnly /*~~(This Curator version is versionless, property-managed, ranged, dynamic, or otherwise not a fixed visible value; migrate its actual parent/BOM/catalog/property owner and verify that the complete Curator family resolves to 5.7.1)~~>*/'org.apache.curator:curator-framework:5.+'
                        }
                        """,
                        source -> source.path("ambiguous.gradle")
                ),
                properties(
                        "zookeeper.version=3.4.6\n",
                        source -> source.path("src/main/resources/coordination.properties")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertContains(after.printAll(), "Curator 5 cannot run with ZooKeeper 3.4.x"))
                ),
                yaml(
                        "zookeeper:\n  version: 3.4.14\n",
                        source -> source.path("src/main/resources/application.yml")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertContains(after.printAll(), "Curator 5 cannot run with ZooKeeper 3.4.x"))
                ),
                xml(
                        "<coordination><zookeeper-version>3.4.10</zookeeper-version></coordination>",
                        source -> source.path("src/main/resources/coordination.xml")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertContains(after.printAll(), "Curator 5 cannot run with ZooKeeper 3.4.x"))
                ),
                buildGradle(
                        "implementation group: 'org.apache.curator', name: 'curator-recipes', version: '5.4.0'",
                        source -> source.path("outside.gradle")
                ),
                properties(
                        "# zookeeper.version=3.4.6\napplication.version=3.4.6\n",
                        source -> source.path("src/main/resources/unrelated.properties")
                ),
                properties(
                        "zookeeper.version=3.4.6\n",
                        source -> source.path("install/generated/coordination.properties")
                )
        );
    }

    @Test
    void recommendedRecipeSkipsGeneratedAndInstalledJavaSources() {
        rewriteRun(
                spec -> spec
                        .recipe(environment().activateRecipes(MIGRATION_RECIPE_NAME))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator.framework.listen; public class ListenerContainer<T> { public ListenerContainer() {} }",
                                "package org.apache.curator.framework.listen; public class StandardListenerManager<T> { public static <T> StandardListenerManager<T> standard() { return null; } }"
                        )),
                java(
                        """
                        import org.apache.curator.framework.listen.ListenerContainer;
                        class GeneratedRegistry { ListenerContainer<Runnable> listeners = new ListenerContainer<>(); }
                        """,
                        source -> source.path("target/generated-sources/GeneratedRegistry.java")
                ),
                java(
                        """
                        import org.apache.curator.framework.listen.ListenerContainer;
                        class InstalledRegistry { ListenerContainer<Runnable> listeners = new ListenerContainer<>(); }
                        """,
                        source -> source.path("install/sources/InstalledRegistry.java")
                )
        );
    }

    @Test
    void recommendedAutoAndMarkersAreStableAcrossTwoCycles() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE_NAME))
                        .cycles(2).expectedCyclesThatMakeChanges(1)
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator; public class RetryLoop { public static boolean shouldRetry(int rc) { return false; } }"
                        )),
                pomXml(pomWithVersion("5.4.0"), pomWithVersion("5.7.1")),
                java(
                        """
                        import org.apache.curator.RetryLoop;
                        class RetryClassifier { boolean retry(int rc) { return RetryLoop.shouldRetry(rc); } }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "RetryLoop.shouldRetry(int) was removed"))
                ),
                properties(
                        "zookeeper.version=3.4.14\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Curator 5 cannot run with ZooKeeper 3.4.x"))
                )
        );
    }

    @Test
    void leavesJavaSourceUntouched() {
        rewriteRun(java(
                """
                package example;

                class CuratorConfiguration {
                    String coordinate = "org.apache.curator:curator-framework:2.7.1";
                    String removedApi = "org.apache.curator.framework.api.UnhandledErrorListener";
                }
                """
        ));
    }

    @ParameterizedTest(name = "leaves configuration file {0} untouched")
    @ValueSource(strings = {"application.yml", "application.properties", "Dockerfile", "dependencies.txt"})
    void leavesConfigurationAndTextFilesUntouched(String path) {
        rewriteRun(text(
                "curator.coordinate=org.apache.curator:curator-framework:2.7.1\ncurator.version=2.7.1\n",
                spec -> spec.path(path)
        ));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);
        Recipe migrationRecipe = environment.activateRecipes(MIGRATION_RECIPE_NAME);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> MIGRATION_RECIPE_NAME.equals(candidate.getName())));
        assertEquals(RECIPE_NAME, recipe.getName());
        assertEquals(MIGRATION_RECIPE_NAME, migrationRecipe.getName());
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(migrationRecipe.validate().isValid(), () -> migrationRecipe.validate().failures().toString());
    }

    private static String pomWithVersion(String version) {
        return pomWithCoordinate("org.apache.curator", "curator-framework", version);
    }

    private static String pomWithCoordinate(String groupId, String artifactId, String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>curator-app</artifactId><version>1</version><dependencies><dependency>
                 <groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(groupId, artifactId, version);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.curatorframework")
                .build();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
