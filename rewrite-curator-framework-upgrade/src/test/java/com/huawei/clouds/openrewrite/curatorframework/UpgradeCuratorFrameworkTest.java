package com.huawei.clouds.openrewrite.curatorframework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeCuratorFrameworkTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.curatorframework.UpgradeCuratorFrameworkTo5_7_1";
    private static final String MIGRATION_RECIPE_NAME =
            "com.huawei.clouds.openrewrite.curatorframework.MigrateCuratorFrameworkTo5_7_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesSpreadsheetSourceVersion() {
        rewriteRun(pomXml(
                pomWithVersion("2.7.1"),
                pomWithVersion("5.7.1")
        ));
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
                  <properties>
                    <curator.version>5.7.1</curator.version>
                  </properties>
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
    void upgradesGradleInterpolatedVersionVariable() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def curatorVersion = '2.7.1'
                dependencies { implementation "org.apache.curator:curator-framework:${curatorVersion}" }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def curatorVersion = '5.7.1'
                dependencies { implementation "org.apache.curator:curator-framework:${curatorVersion}" }
                """
        ));
    }

    @Test
    void upgradesGradleMapVersionVariable() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def curatorVersion = '2.7.1'
                dependencies {
                    runtimeOnly group: 'org.apache.curator', name: 'curator-framework', version: curatorVersion
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def curatorVersion = '5.7.1'
                dependencies {
                    runtimeOnly group: 'org.apache.curator', name: 'curator-framework', version: curatorVersion
                }
                """
        ));
    }

    @Test
    void leavesGradleKotlinStringNotationWithoutSemanticModelUntouched() {
        // UpgradeDependencyVersion relies on Gradle's dependency model. A parser-only
        // Kotlin DSL test has no GradleProject marker and must fail safe.
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("org.apache.curator:curator-framework:2.7.1") }
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
    void upgradesMavenPluginDependencyWithSameCoordinate() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-dependency</artifactId><version>1</version>
                  <build><plugins><plugin><groupId>com.acme</groupId><artifactId>generator</artifactId><version>1</version><dependencies><dependency>
                    <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>2.7.1</version>
                  </dependency></dependencies></plugin></plugins></build>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-dependency</artifactId><version>1</version>
                  <build><plugins><plugin><groupId>com.acme</groupId><artifactId>generator</artifactId><version>1</version><dependencies><dependency>
                    <groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>5.7.1</version>
                  </dependency></dependencies></plugin></plugins></build>
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
                                return /*~~>*/RetryLoop.shouldRetry(resultCode);
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
                                /*~~>*/listeners.forEach(listener -> {
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
                            /*~~>*/NodeCache cache;
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
                                /*~~>*/builder.executorService(executor);
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
                          <!--~~>--><dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeper</artifactId><version>3.4.14</version></dependency>
                        </dependencies></project>
                        """
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
}
