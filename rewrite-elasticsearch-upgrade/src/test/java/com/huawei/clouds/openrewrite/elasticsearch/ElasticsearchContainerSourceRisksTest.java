package com.huawei.clouds.openrewrite.elasticsearch;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class ElasticsearchContainerSourceRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.elasticsearch.FindElasticsearchContainer1_21_4SourceRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(ElasticsearchTestSupport.recipe(RECIPE))
                .parser(ElasticsearchTestSupport.parser());
    }

    @Test
    void marksEveryContainerConstructionForChangedOperationalDefaults() {
        rewriteRun(
                selectedPom(),
                java("""
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class Containers {
                    ElasticsearchContainer a = new ElasticsearchContainer();
                    ElasticsearchContainer b =
                        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.10.4");
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindElasticsearchContainerSourceRisks.OPERATIONAL_DEFAULTS), printed);
                    assertEquals(2, ElasticsearchTestSupport.occurrences(
                    printed, FindElasticsearchContainerSourceRisks.OPERATIONAL_DEFAULTS), printed);
                })));
    }

    @Test
    void marksNetworkAliasDependentCalls() {
        rewriteRun(
                selectedPom(),
                java("""
                import java.util.List;
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class NetworkUse {
                    List<String> aliases(ElasticsearchContainer container) {
                        return container.getNetworkAliases();
                    }
                    String host(ElasticsearchContainer container) {
                        return container.getHost();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertEquals(2, ElasticsearchTestSupport.occurrences(
                    printed, FindElasticsearchContainerSourceRisks.NETWORK_ALIAS), printed);
                })));
    }

    @Test
    void marksExplicitDiskThresholdOverride() {
        rewriteRun(
                selectedPom(),
                java("""
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class DiskThreshold {
                    ElasticsearchContainer container = new ElasticsearchContainer()
                        .withEnv("cluster.routing.allocation.disk.threshold_enabled", "true");
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindElasticsearchContainerSourceRisks.DISK_THRESHOLD), printed);
            assertTrue(printed.contains("\"true\""), printed);
                })));
    }

    @Test
    void marksOssImageWithoutChangingIt() {
        rewriteRun(
                selectedPom(),
                java("""
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class OssImage {
                    ElasticsearchContainer container = new ElasticsearchContainer(
                        "docker.elastic.co/elasticsearch/elasticsearch-oss:7.10.2");
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindElasticsearchContainerSourceRisks.OSS_IMAGE), printed);
            assertTrue(printed.contains("elasticsearch-oss:7.10.2"), printed);
                })));
    }

    @Test
    void marksLazyCertificateAndSslExceptionBoundaries() {
        rewriteRun(
                selectedPom(),
                java("""
                import java.util.Optional;
                import javax.net.ssl.SSLContext;
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class Certificates {
                    Optional<byte[]> ca(ElasticsearchContainer container) {
                        return container.caCertAsBytes();
                    }
                    SSLContext ssl(ElasticsearchContainer container) {
                        return container.createSslContextFromCa();
                    }
                    ElasticsearchContainer path(ElasticsearchContainer container) {
                        return container.withCertPath("/custom/ca.crt");
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertEquals(3, ElasticsearchTestSupport.occurrences(
                    printed, FindElasticsearchContainerSourceRisks.CERTIFICATE), printed);
                })));
    }

    @Test
    void unrelatedOssTextAndSameNamedClassAreNotMarked() {
        rewriteRun(spec -> spec.expectedCyclesThatMakeChanges(1),
                selectedPom(),
                java("""
                class ElasticsearchContainer {
                    ElasticsearchContainer() {}
                }
                class Local {
                    String note = "elasticsearch-oss";
                    ElasticsearchContainer value = new ElasticsearchContainer();
                }
                """));
    }

    @Test
    void generatedJavaIsExcludedFromAllSourceMarkers() {
        rewriteRun(spec -> spec.expectedCyclesThatMakeChanges(1),
                selectedPom(),
                java("""
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class Generated {
                    ElasticsearchContainer value = new ElasticsearchContainer();
                    byte[] ca(ElasticsearchContainer value) {
                        return value.caCertAsBytes().orElseThrow();
                    }
                }
                """, source -> source.path("target/generated-sources/Generated.java")));
    }

    @Test
    void lowLevelRiskVisitorCannotBypassThePreUpgradeProjectMarker() {
        rewriteRun(spec -> spec.recipe(new FindElasticsearchContainerSourceRisks()),
                java("""
                        import org.testcontainers.elasticsearch.ElasticsearchContainer;
                        class NoMarker {
                            ElasticsearchContainer value = new ElasticsearchContainer();
                        }
                        """));
    }

    @Test
    void sourceMarkersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                selectedPom(),
                java("""
                        import org.testcontainers.elasticsearch.ElasticsearchContainer;
                        class Containers {
                            ElasticsearchContainer value = new ElasticsearchContainer(
                                "docker.elastic.co/elasticsearch/elasticsearch-oss:7.10.2");
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(1, ElasticsearchTestSupport.occurrences(
                            printed, FindElasticsearchContainerSourceRisks.OPERATIONAL_DEFAULTS), printed);
                    assertEquals(1, ElasticsearchTestSupport.occurrences(
                            printed, FindElasticsearchContainerSourceRisks.OSS_IMAGE), printed);
                })));
    }

    private static org.openrewrite.test.SourceSpecs selectedPom() {
        return xml(ElasticsearchTestSupport.pom("1.17.6"),
                source -> source.path("pom.xml"));
    }

    private static void assertEquals(int expected, int actual, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, message);
    }
}
