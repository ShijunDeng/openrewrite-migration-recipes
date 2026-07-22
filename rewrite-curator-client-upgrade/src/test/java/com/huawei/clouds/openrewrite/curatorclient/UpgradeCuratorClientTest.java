package com.huawei.clouds.openrewrite.curatorclient;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class UpgradeCuratorClientTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.curatorclient.UpgradeCuratorClientTo5_9_0";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.curatorclient.MigrateCuratorClientTo5_9_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "upgrades exact workbook source {0}")
    @ValueSource(strings = {"2.7.1", "5.2.0", "5.4.0"})
    void upgradesEveryVisibleWorkbookSource(String version) {
        rewriteRun(pomXml(pomWithVersion(version), pomWithVersion("5.9.0")));
    }

    @Test
    void sourceWhitelistIsExactlyTheThreeVisibleWorkbookCells() {
        assertEquals(Set.of("2.7.1", "5.2.0", "5.4.0"),
                UpgradeSelectedCuratorClientDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesSharedCuratorSuitePropertyFromApacheLinkis() {
        // Reduced from apache/linkis at fixed commit 974438c:
        // https://github.com/apache/linkis/blob/974438c/pom.xml#L115-L117
        // https://github.com/apache/linkis/blob/974438c/pom.xml#L544-L557
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>org.apache.linkis</groupId><artifactId>linkis</artifactId><version>1</version>
                  <properties><curator.version>2.7.1</curator.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-recipes</artifactId><version>${curator.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>org.apache.linkis</groupId><artifactId>linkis</artifactId><version>1</version>
                  <properties><curator.version>5.9.0</curator.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-recipes</artifactId><version>${curator.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesHadoopManagedCuratorPropertyWithoutHidingZooKeeper34() {
        // Reduced from apache/hadoop release-2.7.1 at fixed commit
        // ac0538aac347bfd97cc0dee1db49db503c15f1d9.
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>org.apache.hadoop</groupId><artifactId>hadoop-project</artifactId><version>2.7.1</version>
                  <properties><zookeeper.version>3.4.6</zookeeper.version><curator.version>2.7.1</curator.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeper</artifactId><version>${zookeeper.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>org.apache.hadoop</groupId><artifactId>hadoop-project</artifactId><version>2.7.1</version>
                  <properties><zookeeper.version>3.4.6</zookeeper.version><curator.version>5.9.0</curator.version></properties>
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
    void upgradesDependencyManagementLiteral() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencyManagement><dependencies>
                  <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>5.2.0</version></dependency>
                </dependencies></dependencyManagement></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencyManagement><dependencies>
                  <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>5.9.0</version></dependency>
                </dependencies></dependencyManagement></project>
                """
        ));
    }

    @Test
    void upgradesDirectProfileDependency() {
        rewriteRun(pomXml(
                profilePom("<dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>5.4.0</version></dependency></dependencies>"),
                profilePom("<dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>5.9.0</version></dependency></dependencies>")
        ));
    }

    @Test
    void upgradesProfileLocalPropertyInPlace() {
        rewriteRun(pomXml(
                profilePom("<properties><curator.version>2.7.1</curator.version></properties><dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency></dependencies>"),
                profilePom("<properties><curator.version>5.9.0</curator.version></properties><dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency></dependencies>")
        ));
    }

    @Test
    void preservesMavenMetadata() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>2.7.1</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies><dependency>
                  <groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>5.9.0</version><scope>runtime</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """
        ));
    }

    @ParameterizedTest(name = "leaves nonstandard Maven variant {0}")
    @ValueSource(strings = {"<classifier>tests</classifier>", "<type>test-jar</type>"})
    void leavesNonstandardMavenVariants(String variant) {
        rewriteRun(pomXml(pomWithBody("<dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>2.7.1</version>" + variant + "</dependency>")));
    }

    @Test
    void leavesExternalPropertyForItsOwner() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><platform.curator>4.2.0</platform.curator></properties><dependencies>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${platform.curator}</version></dependency>
                  </dependencies></project>
                """));
    }

    @Test
    void leavesPropertyWithUnrelatedConsumerUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><shared.version>2.7.1</shared.version></properties><name>service-${shared.version}</name><dependencies>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${shared.version}</version></dependency>
                  </dependencies></project>
                """));
    }

    @Test
    void leavesPropertyReferencedByXmlAttributeUntouched() {
        rewriteRun(pomXml("""
                <project label="${curator.version}"><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><curator.version>2.7.1</curator.version></properties><dependencies>
                    <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency>
                  </dependencies></project>
                """));
    }

    @Test
    void leavesDuplicateRootAndProfilePropertyDefinitionsUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><curator.version>2.7.1</curator.version></properties>
                  <dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency></dependencies>
                  <profiles><profile><id>other</id><properties><curator.version>5.4.0</curator.version></properties></profile></profiles>
                </project>
                """));
    }

    @Test
    void leavesVersionlessDependencyManagedByParent() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><parent><groupId>org.apache.hadoop</groupId><artifactId>hadoop-project</artifactId><version>2.7.1</version><relativePath/></parent>
                  <groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void leavesVersionlessDependencyManagedByImportedBom() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.apache.hadoop</groupId><artifactId>hadoop-project</artifactId><version>2.7.1</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId></dependency></dependencies>
                </project>
                """));
    }

    @ParameterizedTest(name = "leaves workbook-external source {0}")
    @ValueSource(strings = {"2.7.0", "5.3.0", "5.8.0", "[5.2,6)"})
    void leavesWorkbookExternalVersions(String version) {
        rewriteRun(pomXml(pomWithVersion(version)));
    }

    @ParameterizedTest(name = "does not downgrade {0}")
    @ValueSource(strings = {"5.9.0", "5.9.1", "6.0.0"})
    void neverDowngradesTargetOrNewerVersions(String version) {
        rewriteRun(buildGradle("dependencies { implementation 'org.apache.curator:curator-client:" + version + "' }"));
    }

    @Test
    void leavesMavenPluginDependencyUntouched() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><build><plugins><plugin>
                  <groupId>example</groupId><artifactId>tool</artifactId><version>1</version><dependencies><dependency>
                    <groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>2.7.1</version>
                  </dependency></dependencies></plugin></plugins></build></project>
                """));
    }

    @Test
    void leavesSimilarXmlAndCoordinatesUntouched() {
        rewriteRun(
                xml("<catalog><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>2.7.1</version></dependency></catalog>"),
                buildGradle("dependencies { implementation 'example:curator-client:2.7.1'; runtimeOnly 'org.apache.curator:curator-client-extra:2.7.1' }")
        );
    }

    @ParameterizedTest(name = "skips generated or installed build file {0}")
    @ValueSource(strings = {"target/generated/pom.xml", "build/generated/pom.xml", "install/pom.xml"})
    void skipsGeneratedAndInstalledBuildFiles(String path) {
        rewriteRun(pomXml(pomWithVersion("2.7.1"), source -> source.path(path)));
    }

    @Test
    void upgradesGradleGroovyStringNotation() {
        rewriteRun(buildGradle(
                "plugins { id 'java' }\ndependencies { implementation 'org.apache.curator:curator-client:2.7.1' }",
                "plugins { id 'java' }\ndependencies { implementation 'org.apache.curator:curator-client:5.9.0' }"
        ));
    }

    @Test
    void upgradesGradleGroovyMapNotation() {
        rewriteRun(buildGradle(
                "dependencies { runtimeOnly group: 'org.apache.curator', name: 'curator-client', version: '5.2.0' }",
                "dependencies { runtimeOnly group: 'org.apache.curator', name: 'curator-client', version: '5.9.0' }"
        ));
    }

    @Test
    void upgradesParenthesizedGradleDependencyAndPreservesExclusion() {
        rewriteRun(buildGradle(
                """
                dependencies {
                    compile ('org.apache.curator:curator-client:2.7.1') {
                        exclude group: 'com.google.guava'
                    }
                }
                """,
                """
                dependencies {
                    compile ('org.apache.curator:curator-client:5.9.0') {
                        exclude group: 'com.google.guava'
                    }
                }
                """
        ));
    }

    @Test
    void upgradesKotlinDslLiteralWithoutAGradleSemanticModel() {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.apache.curator:curator-client:5.4.0\") }",
                "dependencies { implementation(\"org.apache.curator:curator-client:5.9.0\") }"
        ));
    }

    @ParameterizedTest(name = "leaves Gradle owner expression {0}")
    @ValueSource(strings = {
            "implementation \"org.apache.curator:curator-client:$curatorVersion\"",
            "implementation 'org.apache.curator:curator-client:5.+'",
            "implementation libs.curator.client"
    })
    void leavesGradleExternalOwnersForRecommendedAudit(String declaration) {
        rewriteRun(buildGradle("dependencies { " + declaration + " }"));
    }

    @Test
    void leavesDependencyLikeCallsOutsideOwnedDependenciesBlock() {
        // Apache Myriad 9bd85f6 supplies the real parenthesized/exclusion shape; this negative
        // fixture proves that only the actual top-level Gradle dependencies owner is accepted.
        rewriteRun(buildGradle("implementation('org.apache.curator:curator-client:2.7.1')"));
    }

    @Test
    void leavesNestedConstraintsAndCustomConfigurationUntouched() {
        rewriteRun(buildGradle("""
                dependencies {
                    constraints { implementation 'org.apache.curator:curator-client:2.7.1' }
                    generatedFixture 'org.apache.curator:curator-client:2.7.1'
                }
                """));
    }

    @ParameterizedTest(name = "leaves Gradle variant {0}")
    @ValueSource(strings = {
            "implementation 'org.apache.curator:curator-client:2.7.1:tests'",
            "implementation 'org.apache.curator:curator-client:2.7.1@zip'",
            "implementation group: 'org.apache.curator', name: 'curator-client', version: '2.7.1', classifier: 'tests'"
    })
    void leavesGradleVariantsUntouched(String declaration) {
        rewriteRun(buildGradle("dependencies { " + declaration + " }"));
    }

    @Test
    void automaticallyReplacesRemovedRetryCodeClassifier() {
        // Reduced from Apache Curator 2.7.0 at fixed commit
        // 206a59043cb94fe51dbd878b080f68d1c0b7595e, where RetryLoop.shouldRetry(rc)
        // is used by the framework implementation.
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator; public final class RetryLoop { public static boolean shouldRetry(int rc) { return false; } }",
                                "package org.apache.zookeeper; public class KeeperException extends Exception { public enum Code { CONNECTIONLOSS, OPERATIONTIMEOUT, SESSIONMOVED, SESSIONEXPIRED; public static Code get(int rc) { return null; } } }"
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
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertFalse(printed.contains("RetryLoop.shouldRetry"));
                            assertContains(printed, "EnumSet.of");
                            assertContains(printed, "KeeperException.Code.CONNECTIONLOSS");
                            assertContains(printed, "KeeperException.Code.SESSIONEXPIRED");
                            assertEquals(1, occurrences(printed, "Code.get(resultCode)"));
                        })
                )
        );
    }

    @Test
    void marksRemovedRetryExceptionClassifier() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator; public final class RetryLoop { public static boolean isRetryException(Throwable error) { return false; } }"
                        )),
                java(
                        """
                        import org.apache.curator.RetryLoop;
                        class RetryErrors { boolean retry(Throwable error) { return RetryLoop.isRetryException(error); } }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "RetryLoop.isRetryException(Throwable) was removed"))
                )
        );
    }

    @Test
    void marksRemovedExhibitorBoundary() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator.ensemble.exhibitor; public class ExhibitorEnsembleProvider {}"
                        )),
                java(
                        """
                        import org.apache.curator.ensemble.exhibitor.ExhibitorEnsembleProvider;
                        class Discovery { ExhibitorEnsembleProvider provider; }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "removed Exhibitor support"))
                )
        );
    }

    @Test
    void marksRemovedConnectionHandlingPolicy() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator; public interface ConnectionHandlingPolicy {}"
                        )),
                java(
                        """
                        import org.apache.curator.ConnectionHandlingPolicy;
                        class Connections { ConnectionHandlingPolicy policy; }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "ConnectionHandlingPolicy and related classes were removed"))
                )
        );
    }

    @Test
    void marksCustomEnsembleProviderAddedMethodBoundary() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator.ensemble; import java.io.Closeable; public interface EnsembleProvider extends Closeable { void start() throws Exception; String getConnectionString(); }"
                        )),
                java(
                        """
                        import java.io.IOException;
                        import org.apache.curator.ensemble.EnsembleProvider;
                        class ConfigCenterProvider implements EnsembleProvider {
                            public void start() {}
                            public String getConnectionString() { return "zk:2181"; }
                            public void close() throws IOException {}
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "must implement and define semantics for setConnectionString(String)");
                            assertContains(after.printAll(), "updateServerListEnabled()");
                        })
                )
        );
    }

    @Test
    void marksOfficiallyRemovedListenerAndReaperTypes() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator.framework.listen; public class ListenerContainer<T> {}",
                                "package org.apache.curator.framework.recipes.locks; public class Reaper {}",
                                "package org.apache.curator.framework.recipes.locks; public class ChildReaper {}"
                        )),
                java(
                        """
                        import org.apache.curator.framework.listen.ListenerContainer;
                        import org.apache.curator.framework.recipes.locks.Reaper;
                        import org.apache.curator.framework.recipes.locks.ChildReaper;
                        class RemovedCuratorApis {
                            ListenerContainer<String> listeners;
                            Reaper reaper;
                            ChildReaper childReaper;
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "removed the Guava-leaking ListenerContainer");
                            assertContains(after.printAll(), "removed Reaper");
                            assertContains(after.printAll(), "removed ChildReaper");
                        })
                )
        );
    }

    @Test
    void marksRemovedGroupMemberProtectedHooks() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator.framework.recipes.nodes; public class GroupMember { protected Object newPersistentEphemeralNode() { return null; } protected Object newPathChildrenCache() { return null; } }"
                        )),
                java(
                        """
                        import org.apache.curator.framework.recipes.nodes.GroupMember;
                        class CustomMember extends GroupMember {
                            @Override protected Object newPersistentEphemeralNode() { return new Object(); }
                            Object oldCacheFactory() { return newPathChildrenCache(); }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "removed this GroupMember protected factory hook");
                            assertContains(after.printAll(), "removed GroupMember's protected node/cache factory hooks");
                        })
                )
        );
    }

    @Test
    void marksOnlyRemovedCloseableExecutorDiscoveryOverload() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator.utils; public class CloseableExecutorService {}",
                                "package org.apache.curator.x.discovery; import java.util.concurrent.ExecutorService; import org.apache.curator.utils.CloseableExecutorService; public interface ServiceCacheBuilder<T> { ServiceCacheBuilder<T> executorService(ExecutorService executor); ServiceCacheBuilder<T> executorService(CloseableExecutorService executor); }"
                        )),
                java(
                        """
                        import java.util.concurrent.ExecutorService;
                        import org.apache.curator.utils.CloseableExecutorService;
                        import org.apache.curator.x.discovery.ServiceCacheBuilder;
                        class DiscoveryThreads {
                            void configure(ServiceCacheBuilder<String> builder, CloseableExecutorService oldOwner, ExecutorService supported) {
                                builder.executorService(oldOwner);
                                builder.executorService(supported);
                            }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(), "removed the ServiceCacheBuilder/ServiceProviderBuilder CloseableExecutorService overload")))
                )
        );
    }

    @Test
    void ignoresSameNamedApplicationApis() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                java("""
                        class RetryLoop { static boolean isRetryException(Throwable error) { return false; } }
                        class ExhibitorEnsembleProvider {}
                        class Local { boolean test(Throwable error) { return RetryLoop.isRetryException(error); } }
                        """)
        );
    }

    @Test
    void marksParsedZooKeeper34ConfigurationAndIgnoresSimilarKeys() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                properties(
                        "zookeeper.version=3.4.6\napplication.version=3.4.6\n",
                        source -> source.path("src/main/resources/coordination.properties")
                                .after(actual -> actual).afterRecipe(after -> {
                                    assertContains(after.printAll(), "Curator 5 cannot run with ZooKeeper 3.4.x");
                                    assertEquals(1, occurrences(after.printAll(), "Curator 5 cannot run"));
                                })
                ),
                yaml(
                        "zookeeper:\n  version: 3.4.14\napplication:\n  version: 3.4.14\n",
                        source -> source.path("src/main/resources/application.yml")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertEquals(1, occurrences(after.printAll(), "Curator 5 cannot run")))
                ),
                xml(
                        "<coordination><zookeeper-version>3.4.10</zookeeper-version><application-version>3.4.10</application-version></coordination>",
                        source -> source.path("src/main/resources/coordination.xml")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertEquals(1, occurrences(after.printAll(), "Curator 5 cannot run")))
                )
        );
    }

    @Test
    void leavesCommentsSupportedVersionsAndGeneratedConfigurationUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                properties("# zookeeper.version=3.4.6\nzookeeper.version=3.9.3\n",
                        source -> source.path("src/main/resources/supported.properties")),
                properties("zookeeper.version=3.4.6\n",
                        source -> source.path("install/generated/coordination.properties")),
                yaml("zookeeper:\n  version: 3.9.3\n", source -> source.path("application.yml"))
        );
    }

    @Test
    void recommendedRecipeUpgradesClientAndMarksOwnedMavenRisks() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies>
                          <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>2.7.1</version></dependency>
                          <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>5.4.0</version></dependency>
                          <dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeper</artifactId><version>3.4.14</version></dependency>
                        </dependencies></project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "<version>5.9.0</version>");
                            assertContains(printed, "explicit Curator companion is not aligned to 5.9.0");
                            assertContains(printed, "Curator 5 no longer supports ZooKeeper 3.4.x");
                        })
                )
        );
    }

    @Test
    void recommendedRecipeMarksExternalVersionlessRangeAndVariantOwners() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version><dependencies>
                          <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId></dependency>
                          <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>[5.2,6)</version></dependency>
                          <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>5.4.0</version><classifier>tests</classifier></dependency>
                        </dependencies></project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertEquals(2, occurrences(after.printAll(), "versionless, property-managed, ranged"));
                            assertContains(after.printAll(), "classified or non-JAR Curator/ZooKeeper artifact");
                        })
                )
        );
    }

    @Test
    void recommendedRecipeResolvesSafeLocalTargetPropertyWithoutFalseMarker() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                          <properties><curator.version>5.4.0</curator.version></properties><dependencies>
                            <dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>${curator.version}</version></dependency>
                            <dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId><version>${curator.version}</version></dependency>
                          </dependencies></project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<curator.version>5.9.0</curator.version>");
                            assertFalse(after.printAll().contains("property-managed, ranged"));
                            assertFalse(after.printAll().contains("companion is not aligned"));
                        })
                )
        );
    }

    @Test
    void recommendedRecipeResolvesRootManagedVersionlessConsumerWithoutFalseMarker() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>5.4.0</version></dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId></dependency></dependencies>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>5.9.0</version>");
                            assertFalse(after.printAll().contains("versionless, property-managed, ranged"));
                        })
                )
        );
    }

    @Test
    void profileManagementDoesNotLeakToRootConsumer() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion>
                          <parent><groupId>org.apache.hadoop</groupId><artifactId>hadoop-project</artifactId><version>2.7.1</version><relativePath/></parent>
                          <groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                          <dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId></dependency></dependencies>
                          <profiles><profile><id>managed</id><dependencyManagement><dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>5.4.0</version></dependency></dependencies></dependencyManagement></profile></profiles>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>5.9.0</version>");
                            assertEquals(1, occurrences(after.printAll(), "versionless, property-managed, ranged"));
                        })
                )
        );
    }

    @Test
    void profileOverrideWinsOverRootManagementWhenAuditingVersionlessConsumer() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>5.9.0</version></dependency></dependencies></dependencyManagement>
                          <profiles><profile><id>legacy</id>
                            <dependencyManagement><dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId><version>5.3.0</version></dependency></dependencies></dependencyManagement>
                            <dependencies><dependency><groupId>org.apache.curator</groupId><artifactId>curator-client</artifactId></dependency></dependencies>
                          </profile></profiles>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertEquals(1, occurrences(after.printAll(), "explicit Curator Client version is outside"));
                            assertEquals(1, occurrences(after.printAll(), "versionless, property-managed, ranged"));
                        })
                )
        );
    }

    @Test
    void recommendedRecipeMarksGradleDynamicCatalogAndVariantPrecisely() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                buildGradle(
                        """
                        dependencies {
                            implementation 'org.apache.curator:curator-client:5.+'
                            implementation libs.curator.client
                            implementation 'org.apache.curator:curator-client:5.4.0:tests'
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "versionless, property-managed, ranged, dynamic, catalog-owned");
                            assertContains(printed, "version-catalog alias appears to own Curator Client");
                            assertContains(printed, "classified or non-JAR Curator/ZooKeeper artifact");
                        })
                ),
                buildGradleKts(
                        "dependencies { implementation(libs.curator.client) }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "version-catalog alias appears to own Curator Client"))
                )
        );
    }

    @Test
    void recommendedRecipeSkipsGeneratedJava() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator; public final class RetryLoop { public static boolean shouldRetry(int rc) { return false; } }"
                        )),
                java(
                        "import org.apache.curator.RetryLoop; class Generated { boolean retry(int rc) { return RetryLoop.shouldRetry(rc); } }",
                        source -> source.path("target/generated-sources/Generated.java")
                ),
                java(
                        "import org.apache.curator.RetryLoop; class Installed { boolean retry(int rc) { return RetryLoop.shouldRetry(rc); } }",
                        source -> source.path("install/sources/Installed.java")
                )
        );
    }

    @Test
    void marksAnonymousEnsembleProviderAddedMethodBoundary() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator.ensemble; import java.io.Closeable; public interface EnsembleProvider extends Closeable { void start() throws Exception; String getConnectionString(); }"
                        )),
                java(
                        """
                        import java.io.IOException;
                        import org.apache.curator.ensemble.EnsembleProvider;
                        class Providers {
                            EnsembleProvider provider = new EnsembleProvider() {
                                public void start() {}
                                public String getConnectionString() { return "zk:2181"; }
                                public void close() throws IOException {}
                            };
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Anonymous EnsembleProvider implementations"))
                )
        );
    }

    @Test
    void automaticChangesAndMarkersAreIdempotentAcrossTwoCycles() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1)
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.apache.curator; public final class RetryLoop { public static boolean shouldRetry(int rc) { return false; } }",
                                "package org.apache.zookeeper; public class KeeperException extends Exception { public enum Code { CONNECTIONLOSS, OPERATIONTIMEOUT, SESSIONMOVED, SESSIONEXPIRED; public static Code get(int rc) { return null; } } }"
                        )),
                pomXml(pomWithVersion("5.4.0"), pomWithVersion("5.9.0")),
                java(
                        "import org.apache.curator.RetryLoop; class Retry { boolean retry(int rc) { return RetryLoop.shouldRetry(rc); } }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertFalse(after.printAll().contains("RetryLoop.shouldRetry"));
                            assertEquals(1, occurrences(after.printAll(), "EnumSet.of"));
                        })
                ),
                properties(
                        "zookeeper.version=3.4.14\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(), "Curator 5 cannot run")))
                )
        );
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = environment();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> UPGRADE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATE.equals(recipe.getName())));
        assertEquals(UPGRADE, upgrade.getName());
        assertEquals(MIGRATE, migrate.getName());
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
    }

    private static String pomWithVersion(String version) {
        return pomWithCoordinate("org.apache.curator", "curator-client", version);
    }

    private static String pomWithCoordinate(String group, String artifact, String version) {
        return pomWithBody("<dependency><groupId>" + group + "</groupId><artifactId>" + artifact +
                           "</artifactId><version>" + version + "</version></dependency>");
    }

    private static String pomWithBody(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies>" +
               body + "</dependencies></project>";
    }

    private static String profilePom(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><profiles><profile><id>coordination</id>" +
               body + "</profile></profiles></project>";
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.curatorclient").build();
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(token, index)) >= 0; index += token.length()) count++;
        return count;
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
