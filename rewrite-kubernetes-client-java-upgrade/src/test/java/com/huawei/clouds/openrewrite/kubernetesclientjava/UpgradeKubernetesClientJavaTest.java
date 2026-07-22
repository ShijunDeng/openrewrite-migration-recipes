package com.huawei.clouds.openrewrite.kubernetesclientjava;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeKubernetesClientJavaTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.kubernetesclientjava.UpgradeKubernetesClientJavaDependencyTo7_3_1";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.kubernetesclientjava.MigrateDeterministicFabric8SourceTo7";
    private static final String JAVA_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.kubernetesclientjava.FindManualFabric8KubernetesClient7JavaRisks";
    private static final String BUILD_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.kubernetesclientjava.FindManualFabric8KubernetesClient7BuildRisks";
    private static final String CONFIG_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.kubernetesclientjava.FindManualFabric8KubernetesClient7ConfigurationRisks";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.kubernetesclientjava.MigrateKubernetesClientJavaTo7_3_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(DEPENDENCY_RECIPE));
    }

    @Test
    void upgradesBothSpreadsheetVersionsInMaven() {
        rewriteRun(
                pomXml(pom("5.12.0"), pom("7.3.1"), source -> source.path("v5.12.0/pom.xml")),
                pomXml(pom("5.12.4"), pom("7.3.1"), source -> source.path("v5.12.4/pom.xml"))
        );
    }

    @Test
    void upgradesSelectedDependencyManagementLiteralAndPreservesVersionlessUse() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>5.12.4</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>7.3.1</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId></dependency></dependencies>
                </project>
                """,
                source -> source.path("pom.xml")
        ));
    }

    @Test
    void upgradesIsolatedMavenProperty() {
        rewriteRun(pomXml(
                propertyPom("5.12.0"),
                propertyPom("7.3.1")
        ));
    }

    @Test
    void preservesSharedMavenPropertyEvenWhenItsValueIsSelected() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><fabric8.version>5.12.4</fabric8.version></properties>
                  <dependencies>
                    <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>${fabric8.version}</version></dependency>
                    <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-model-core</artifactId><version>${fabric8.version}</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesUnresolvedRangeTargetUnlistedAndFutureVersions() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>safety</artifactId><version>1</version><dependencies>
                  <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>${missing.version}</version></dependency>
                  <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>[5.12,6)</version></dependency>
                  <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>5.12.3</version></dependency>
                  <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>6.13.5</version></dependency>
                  <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>7.3.1</version></dependency>
                  <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>7.4.0</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void preservesBomManagedVersionlessDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom-owned</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client-bom</artifactId><version>5.12.0</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void quarkusRealBomFixtureIsDeliberatelyNotRewritten() {
        // Reduced from Quarkus 2.7.0.Final, fixed commit 6378c697:
        // https://github.com/quarkusio/quarkus/blob/6378c69703a485f55b3d221493b5f1e3cfdf9003/bom/application/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>io.quarkus</groupId><artifactId>quarkus-bom</artifactId><version>2.7.0.Final</version>
                  <properties><kubernetes-client.version>5.12.0</kubernetes-client.version></properties>
                  <dependencyManagement><dependencies><dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client-bom</artifactId><version>${kubernetes-client.version}</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesGroovyAndKotlinDirectStringNotation() {
        rewriteRun(
                buildGradle(
                        "plugins { id 'java' }\ndependencies { implementation 'io.fabric8:kubernetes-client:5.12.0' }",
                        "plugins { id 'java' }\ndependencies { implementation 'io.fabric8:kubernetes-client:7.3.1' }"
                ),
                buildGradleKts(
                        "plugins { java }\ndependencies { implementation(\"io.fabric8:kubernetes-client:5.12.4\") }",
                        "plugins { java }\ndependencies { implementation(\"io.fabric8:kubernetes-client:7.3.1\") }"
                )
        );
    }

    @Test
    void preservesGradleInterpolationMapNotationAndVersionCatalogAlias() {
        rewriteRun(
                buildGradle(
                        """
                        plugins { id 'java' }
                        ext { fabric8Version = '5.12.0' }
                        dependencies {
                          implementation "io.fabric8:kubernetes-client:${fabric8Version}"
                          implementation group: 'io.fabric8', name: 'kubernetes-client', version: '5.12.4'
                          implementation libs.fabric8.client
                        }
                        """
                ),
                buildGradleKts(
                        """
                        plugins { java }
                        val fabric8Version = "5.12.4"
                        dependencies { implementation("io.fabric8:kubernetes-client:$fabric8Version") }
                        """
                )
        );
    }

    @Test
    void preservesSimilarGroupsAndCompanionArtifacts() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>coordinates</artifactId><version>1</version><dependencies>
                  <dependency><groupId>com.example</groupId><artifactId>kubernetes-client</artifactId><version>5.12.0</version></dependency>
                  <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client-api</artifactId><version>5.12.0</version></dependency>
                  <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client-bom</artifactId><version>5.12.4</version></dependency>
                  <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-model</artifactId><version>5.12.4</version></dependency>
                  <dependency><groupId>io.kubernetes</groupId><artifactId>client-java</artifactId><version>5.12.0</version></dependency>
                </dependencies></project>
                """,
                source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesMavenDependencyShape() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shape</artifactId><version>1</version><dependencies><dependency>
                  <groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>5.12.4</version><classifier>tests</classifier><scope>test</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shape</artifactId><version>1</version><dependencies><dependency>
                  <groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>7.3.1</version><classifier>tests</classifier><scope>test</scope><optional>true</optional>
                  <exclusions><exclusion><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void dependencyUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("5.12.0"), pom("7.3.1")));
    }

    @Test
    void epamRealSourceMigratesSafeConfigConstructorToBuilder() {
        // Reduced from epam/cloud-pipeline at fixed commit 0474f0b1:
        // https://github.com/epam/cloud-pipeline/blob/0474f0b13233edbf600f339001aa77b82edb8a28/vm-monitor/src/main/java/com/epam/pipeline/vmmonitor/service/k8s/KubernetesDeploymentMonitor.java
        rewriteRun(
                sourceSpec(),
                java(
                        """
                        package example;
                        import io.fabric8.kubernetes.client.Config;
                        import io.fabric8.kubernetes.client.ConfigBuilder;
                        import io.fabric8.kubernetes.client.DefaultKubernetesClient;
                        import io.fabric8.kubernetes.client.KubernetesClient;
                        class Monitor {
                            void check() {
                                Config config = new ConfigBuilder().build();
                                try (KubernetesClient client = new DefaultKubernetesClient(config)) {}
                            }
                        }
                        """,
                        """
                        package example;
                        import io.fabric8.kubernetes.client.Config;
                        import io.fabric8.kubernetes.client.ConfigBuilder;
                        import io.fabric8.kubernetes.client.KubernetesClient;
                        class Monitor {
                            void check() {
                                Config config = new ConfigBuilder().build();
                                try (KubernetesClient client = new io.fabric8.kubernetes.client.KubernetesClientBuilder().withConfig(config).build()) {}
                            }
                        }
                        """
                )
        );
    }

    @Test
    void yugabyteRealSourceBuilderMigrationIsIdempotent() {
        // Reduced from yugabyte/yugabyte-db at fixed commit d2dbffa5:
        // https://github.com/yugabyte/yugabyte-db/blob/d2dbffa51b6ad60903dac46442f36b2a2a786299/managed/src/main/java/com/yugabyte/yw/common/operator/KubernetesOperator.java
        rewriteRun(
                spec -> {
                    sourceSpec().accept(spec);
                    spec.cycles(2).expectedCyclesThatMakeChanges(1);
                },
                java(
                        """
                        package example;
                        import io.fabric8.kubernetes.client.Config;
                        import io.fabric8.kubernetes.client.DefaultKubernetesClient;
                        import io.fabric8.kubernetes.client.KubernetesClient;
                        class Operator {
                            void run() {
                                Config config = new Config();
                                try (KubernetesClient client = new DefaultKubernetesClient(config)) {}
                            }
                        }
                        """,
                        """
                        package example;
                        import io.fabric8.kubernetes.client.Config;
                        import io.fabric8.kubernetes.client.KubernetesClient;
                        class Operator {
                            void run() {
                                Config config = new Config();
                                try (KubernetesClient client = new io.fabric8.kubernetes.client.KubernetesClientBuilder().withConfig(config).build()) {}
                            }
                        }
                        """
                )
        );
    }

    @Test
    void leavesConcreteAndNamespacedClientTargetsForReview() {
        rewriteRun(
                sourceSpec(),
                java(
                        """
                        package example;
                        import io.fabric8.kubernetes.client.*;
                        class UnsafeTargets {
                            DefaultKubernetesClient concrete = new DefaultKubernetesClient(new Config());
                            NamespacedKubernetesClient namespaced = new DefaultKubernetesClient();
                        }
                        """
                )
        );
    }

    @Test
    void migratesReadinessCustomResourceInformerAndApplicable() {
        rewriteRun(
                sourceSpec(),
                java(
                        """
                        package example;
                        import io.fabric8.kubernetes.client.CustomResourceList;
                        import io.fabric8.kubernetes.client.dsl.Applicable;
                        import io.fabric8.kubernetes.client.informers.SharedInformer;
                        import io.fabric8.kubernetes.client.internal.readiness.Readiness;
                        class APIs {
                            CustomResourceList<?> list;
                            SharedInformer<?> informer;
                            Readiness readiness;
                            Object create(Applicable<Object> a) { return a.apply(); }
                        }
                        """,
                        """
                        package example;
                        import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
                        import io.fabric8.kubernetes.client.dsl.Applicable;
                        import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
                        import io.fabric8.kubernetes.client.readiness.Readiness;

                        class APIs {
                            DefaultKubernetesResourceList<?> list;
                            SharedIndexInformer<?> informer;
                            Readiness readiness;
                            Object create(Applicable<Object> a) { return a.createOrReplace(); }
                        }
                        """
                )
        );
    }

    @Test
    void migratesOpenShiftAndVerticalPodAutoscalerPackages() {
        rewriteRun(
                sourceSpec(),
                java(
                        """
                        package example;
                        import io.fabric8.openshift.api.model.clusterautoscaling.v1.ClusterAutoscaler;
                        import io.fabric8.openshift.api.model.machineconfig.v1.MachineConfig;
                        import io.fabric8.openshift.client.OpenShiftClient;
                        import io.fabric8.verticalpodautoscaler.api.model.VerticalPodAutoscaler;
                        class Extensions {
                            ClusterAutoscaler autoscaler; MachineConfig machine; VerticalPodAutoscaler vpa;
                            Object dsl(OpenShiftClient client) { return client.clusterAutoscaling(); }
                        }
                        """,
                        """
                        package example;
                        import io.fabric8.openshift.api.model.autoscaling.v1.ClusterAutoscaler;
                        import io.fabric8.openshift.api.model.machineconfiguration.v1.MachineConfig;
                        import io.fabric8.openshift.client.OpenShiftClient;
                        import io.fabric8.autoscaling.api.model.v1.VerticalPodAutoscaler;
                        class Extensions {
                            ClusterAutoscaler autoscaler; MachineConfig machine; VerticalPodAutoscaler vpa;
                            Object dsl(OpenShiftClient client) { return client.openShiftAutoscaling(); }
                        }
                        """
                )
        );
    }

    @Test
    void migratesOfficialMockWebServerReplacementTypes() {
        rewriteRun(
                mockSourceSpec(),
                java(
                        """
                        package example;
                        import okhttp3.Headers;
                        import okhttp3.mockwebserver.MockResponse;
                        import okhttp3.mockwebserver.MockWebServer;
                        import okhttp3.mockwebserver.RecordedRequest;
                        class MockTest { MockWebServer server; MockResponse response; RecordedRequest request; Headers headers; }
                        """,
                        """
                        package example;
                        import io.fabric8.mockwebserver.MockWebServer;
                        import io.fabric8.mockwebserver.http.Headers;
                        import io.fabric8.mockwebserver.http.MockResponse;
                        import io.fabric8.mockwebserver.http.RecordedRequest;

                        class MockTest { MockWebServer server; MockResponse response; RecordedRequest request; Headers headers; }
                        """
                )
        );
    }

    @Test
    void doesNotRewriteOrdinaryOkHttpTypesOutsideFabric8MockTests() {
        rewriteRun(
                mockSourceSpec(),
                java(
                        """
                        package example;
                        import okhttp3.Headers;
                        class ProductionHttp { Headers headers; }
                        """
                )
        );
    }

    @Test
    void flinkRealNamespacedConstructorIsMarkedNotBrokenByAutomation() {
        // Reduced from apache/flink release-1.14.3 at fixed commit 98997ea3:
        // https://github.com/apache/flink/blob/98997ea37ba08eae0f9aa6dd34823238097d8e0d/flink-kubernetes/src/main/java/org/apache/flink/kubernetes/kubeclient/FlinkKubeClientFactory.java
        rewriteRun(
                riskSourceSpec(),
                java(
                        """
                        package example;
                        import io.fabric8.kubernetes.client.Config;
                        import io.fabric8.kubernetes.client.DefaultKubernetesClient;
                        import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
                        class FlinkKubeClientFactory {
                            NamespacedKubernetesClient create(Config config) {
                                return new DefaultKubernetesClient(config);
                            }
                        }
                        """,
                        """
                        package example;
                        import io.fabric8.kubernetes.client.Config;
                        import io.fabric8.kubernetes.client.DefaultKubernetesClient;
                        import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
                        class FlinkKubeClientFactory {
                            NamespacedKubernetesClient create(Config config) {
                                return /*~~(Direct DefaultKubernetesClient construction remains: choose KubernetesClientBuilder and preserve namespace/HTTP-client semantics)~~>*/new DefaultKubernetesClient(config);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void hugegraphRealKubeconfigStringConsumerIsPreciselyMarked() {
        // Reduced from apache/hugegraph-computer at fixed commit 20cb8852:
        // https://github.com/apache/hugegraph-computer/blob/20cb8852ac69af14871df20618a541cb1725594a/computer/computer-test/src/main/java/org/apache/hugegraph/computer/k8s/MiniKubeTest.java
        rewriteRun(
                riskSourceSpec(),
                java(
                        """
                        package example;
                        import java.io.File;
                        import static io.fabric8.kubernetes.client.Config.getKubeconfigFilename;
                        class MiniKubeTest { File config() { return new File(getKubeconfigFilename()); } }
                        """,
                        """
                        package example;
                        import java.io.File;
                        import static io.fabric8.kubernetes.client.Config.getKubeconfigFilename;
                        class MiniKubeTest { File config() { return new File(/*~~(Config.getKubeconfigFilename() became getKubeconfigFilenames(); migrate String consumers to the returned collection deliberately)~~>*/getKubeconfigFilename()); } }
                        """
                )
        );
    }

    @Test
    void marksDeleteWatchAndCapabilityCallsWithTypedFabric8Owners() {
        rewriteRun(
                riskSourceSpec(),
                java(
                        """
                        package example;
                        import io.fabric8.kubernetes.client.KubernetesClient;
                        class Risks {
                            void use(KubernetesClient client) {
                                client.delete();
                                client.watch();
                                client.supports(String.class);
                            }
                        }
                        """,
                        """
                        package example;
                        import io.fabric8.kubernetes.client.KubernetesClient;
                        class Risks {
                            void use(KubernetesClient client) {
                                /*~~(Fabric8 resource/delete DSL changed across 6/7; verify resource(item), createOrReplace, delete StatusDetails, and propagation semantics)~~>*/client.delete();
                                /*~~(Fabric8 watch/informer/stream lifecycle changed; verify close, reconnect, timeout, executor, and exception behavior on 7.3.1)~~>*/client.watch();
                                /*~~(Fabric8 capability/adapt check semantics changed; choose hasApiGroup, supports, or adaptation according to intent)~~>*/client.supports(String.class);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksQuarkusBomJavaBaselineAndHttpTransportBuildRisks() {
        rewriteRun(
                spec -> spec.recipe(recipe(BUILD_RISK_RECIPE)),
                text(
                        """
                        <project><properties><maven.compiler.release>8</maven.compiler.release></properties><dependencies>
                          <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client-bom</artifactId><version>5.12.0</version></dependency>
                          <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-httpclient-okhttp</artifactId></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><properties>~~><maven.compiler.release>8</maven.compiler.release></properties><dependencies>
                          <dependency><groupId>io.fabric8</groupId>~~><artifactId>kubernetes-client-bom</artifactId><version>5.12.0</version></dependency>
                          <dependency><groupId>io.fabric8</groupId>~~><artifactId>kubernetes-httpclient-okhttp</artifactId></dependency>
                        </dependencies></project>
                        """,
                        source -> source.path("pom.xml")
                )
        );
    }

    @Test
    void marksBehaviorConfigurationAndManifestApiVersion() {
        rewriteRun(
                spec -> spec.recipe(recipe(CONFIG_RISK_RECIPE)),
                text(
                        """
                        kubernetes.backwardsCompatibilityInterceptor.disable=true
                        KUBERNETES_KUBECONFIG_FILE=/work/kubeconfig
                        """,
                        """
                        ~~>kubernetes.backwardsCompatibilityInterceptor.disable=true
                        ~~>KUBERNETES_KUBECONFIG_FILE=/work/kubeconfig
                        """,
                        source -> source.path("application.properties")
                ),
                text(
                        "apiVersion: apps/v1\nkind: Deployment",
                        "~~>apiVersion: apps/v1\nkind: Deployment",
                        source -> source.path("deployment.yaml")
                )
        );
    }

    @Test
    void compositeUpgradesDependencyMigratesSafeSourceAndMarksRemainingRisk() {
        rewriteRun(
                spec -> {
                    sourceSpec().accept(spec);
                    spec.recipe(recipe(MIGRATION_RECIPE)).typeValidationOptions(TypeValidation.none());
                },
                pomXml(pom("5.12.4"), pom("7.3.1")),
                java(
                        """
                        package example;
                        import io.fabric8.kubernetes.client.Config;
                        import io.fabric8.kubernetes.client.DefaultKubernetesClient;
                        import io.fabric8.kubernetes.client.KubernetesClient;
                        class App {
                            KubernetesClient safe = new DefaultKubernetesClient(new Config());
                            String config = Config.getKubeconfigFilename();
                        }
                        """,
                        """
                        package example;
                        import io.fabric8.kubernetes.client.Config;
                        import io.fabric8.kubernetes.client.KubernetesClient;
                        class App {
                            KubernetesClient safe = new io.fabric8.kubernetes.client.KubernetesClientBuilder().withConfig(new Config()).build();
                            String config = /*~~(Config.getKubeconfigFilename() became getKubeconfigFilenames(); migrate String consumers to the returned collection deliberately)~~>*/Config.getKubeconfigFilename();
                        }
                        """
                )
        );
    }

    @Test
    void allDeclarativeRecipesAreDiscoverableAndValid() {
        Environment environment = environment();
        String[] names = {
                DEPENDENCY_RECIPE, SOURCE_RECIPE, JAVA_RISK_RECIPE, BUILD_RISK_RECIPE,
                CONFIG_RISK_RECIPE, MIGRATION_RECIPE,
                "com.huawei.clouds.openrewrite.kubernetesclientjava.MigrateFabric8MockWebServerTo7"
        };
        for (String name : names) {
            Recipe recipe = environment.activateRecipes(name);
            assertEquals(name, recipe.getName());
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
        }
    }

    private Recipe recipe(String name) {
        return environment().activateRecipes(name);
    }

    private Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static Consumer<RecipeSpec> sourceSpec() {
        return spec -> spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(SOURCE_RECIPE))
                .parser(fabric8Parser()).typeValidationOptions(TypeValidation.none());
    }

    private static Consumer<RecipeSpec> riskSourceSpec() {
        return spec -> spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(JAVA_RISK_RECIPE))
                .parser(fabric8Parser()).typeValidationOptions(TypeValidation.none());
    }

    private static Consumer<RecipeSpec> mockSourceSpec() {
        return spec -> spec.recipe(Environment.builder().scanRuntimeClasspath().build()
                        .activateRecipes("com.huawei.clouds.openrewrite.kubernetesclientjava.MigrateFabric8MockWebServerTo7"))
                .parser(fabric8Parser()).typeValidationOptions(TypeValidation.none());
    }

    private static JavaParser.Builder<?, ?> fabric8Parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package io.fabric8.kubernetes.client;
                public interface KubernetesClient extends AutoCloseable {
                    boolean delete(); Object watch(); boolean supports(Class<?> type); void close();
                }
                """,
                "package io.fabric8.kubernetes.client; public interface NamespacedKubernetesClient extends KubernetesClient {}",
                """
                package io.fabric8.kubernetes.client;
                public class Config { public static String getKubeconfigFilename() { return null; } }
                """,
                "package io.fabric8.kubernetes.client; public class ConfigBuilder { public Config build() { return null; } }",
                """
                package io.fabric8.kubernetes.client;
                public class DefaultKubernetesClient implements NamespacedKubernetesClient {
                    public DefaultKubernetesClient() {} public DefaultKubernetesClient(Config c) {}
                    public boolean delete() { return false; } public Object watch() { return null; }
                    public boolean supports(Class<?> type) { return false; } public void close() {}
                }
                """,
                """
                package io.fabric8.kubernetes.client;
                public class KubernetesClientBuilder {
                    public KubernetesClientBuilder withConfig(Config c) { return this; }
                    public KubernetesClient build() { return null; }
                }
                """,
                "package io.fabric8.kubernetes.client; public class CustomResourceList<T> {}",
                "package io.fabric8.kubernetes.api.model; public class DefaultKubernetesResourceList<T> {}",
                "package io.fabric8.kubernetes.client.dsl; public interface Applicable<T> { T apply(); T createOrReplace(); }",
                "package io.fabric8.kubernetes.client.informers; public interface SharedInformer<T> {}",
                "package io.fabric8.kubernetes.client.informers; public interface SharedIndexInformer<T> {}",
                "package io.fabric8.kubernetes.client.internal.readiness; public class Readiness {}",
                "package io.fabric8.kubernetes.client.readiness; public class Readiness {}",
                "package io.fabric8.openshift.api.model.clusterautoscaling.v1; public class ClusterAutoscaler {}",
                "package io.fabric8.openshift.api.model.autoscaling.v1; public class ClusterAutoscaler {}",
                "package io.fabric8.openshift.api.model.machineconfig.v1; public class MachineConfig {}",
                "package io.fabric8.openshift.api.model.machineconfiguration.v1; public class MachineConfig {}",
                "package io.fabric8.verticalpodautoscaler.api.model; public class VerticalPodAutoscaler {}",
                "package io.fabric8.autoscaling.api.model.v1; public class VerticalPodAutoscaler {}",
                "package io.fabric8.openshift.client; public interface OpenShiftClient { Object clusterAutoscaling(); Object openShiftAutoscaling(); }",
                "package okhttp3; public class Headers {}",
                "package okhttp3.mockwebserver; public class MockWebServer {}",
                "package okhttp3.mockwebserver; public class MockResponse {}",
                "package okhttp3.mockwebserver; public class RecordedRequest {}",
                "package io.fabric8.mockwebserver; public class MockWebServer {}",
                "package io.fabric8.mockwebserver.http; public class Headers {}",
                "package io.fabric8.mockwebserver.http; public class MockResponse {}",
                "package io.fabric8.mockwebserver.http; public class RecordedRequest {}"
        );
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies>
                 <dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>%s</version></dependency>
               </dependencies></project>
               """.formatted(version);
    }

    private static String propertyPom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                 <properties><fabric8.version>%s</fabric8.version></properties>
                 <dependencies><dependency><groupId>io.fabric8</groupId><artifactId>kubernetes-client</artifactId><version>${fabric8.version}</version></dependency></dependencies>
               </project>
               """.formatted(version);
    }
}
