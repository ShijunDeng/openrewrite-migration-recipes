package com.huawei.clouds.openrewrite.elasticsearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class ElasticsearchBuildRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.elasticsearch.FindElasticsearch1_21_4BuildRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(ElasticsearchTestSupport.recipe(RECIPE));
    }

    @Test
    void versionClassifierUsesArbitraryPrecisionAndExactBoundaries() {
        assertNull(FindElasticsearchBuildRisks.primaryMessage("1.17.6"));
        assertNull(FindElasticsearchBuildRisks.primaryMessage("1.21.4"));
        assertEquals(FindElasticsearchBuildRisks.OUTSIDE,
                FindElasticsearchBuildRisks.primaryMessage("1.21.3"));
        assertEquals(ElasticsearchUpgradeSupport.TARGET_CONFLICT,
                FindElasticsearchBuildRisks.primaryMessage("1.21.5"));
        assertEquals(ElasticsearchUpgradeSupport.TARGET_CONFLICT,
                FindElasticsearchBuildRisks.primaryMessage("2.0.0"));
        assertEquals(ElasticsearchUpgradeSupport.TARGET_CONFLICT,
                FindElasticsearchBuildRisks.primaryMessage("999999999999999999999.0.0"));
        assertEquals(FindElasticsearchBuildRisks.OWNER,
                FindElasticsearchBuildRisks.primaryMessage("${version}"));
    }

    @ParameterizedTest(name = "Maven higher {0} receives the exact no-downgrade marker")
    @ValueSource(strings = {"1.21.5", "1.22.0", "2.0.0", "10.0.0", "999999999999999999999.0.0"})
    void marksHigherMavenVersionsWithoutChangingThem(String version) {
        rewriteRun(xml(ElasticsearchTestSupport.pom(version),
                source -> source.path(version + "/pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(ElasticsearchUpgradeSupport.TARGET_CONFLICT), printed);
                    assertTrue(printed.contains("<version>" + version + "</version>"), printed);
                })));
    }

    @Test
    void marksHigherVersionsAcrossBothGradleDslVariants() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.testcontainers:elasticsearch:1.22.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(ElasticsearchUpgradeSupport.TARGET_CONFLICT), printed);
                            assertTrue(printed.contains("1.22.0"), printed);
                        })),
                buildGradleKts("dependencies { implementation(\"org.testcontainers:elasticsearch:2.0.0\") }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(ElasticsearchUpgradeSupport.TARGET_CONFLICT), printed);
                            assertTrue(printed.contains("2.0.0"), printed);
                        })));
    }

    @ParameterizedTest(name = "Elasticsearch Server {0} is marked as an identity conflict")
    @ValueSource(strings = {"7.9.3", "7.10.2"})
    void marksWorkbookServerCoordinatesAndPreservesThem(String version) {
        rewriteRun(
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.serverDependency(version) + "</dependencies>"),
                        source -> source.path("maven/" + version + "/pom.xml")
                                .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindElasticsearchBuildRisks.IDENTITY_CONFLICT), printed);
                            assertTrue(printed.contains("<version>" + version + "</version>"), printed);
                            assertFalse(printed.contains("<version>1.21.4</version>"), printed);
                        })),
                buildGradle("dependencies { implementation 'org.elasticsearch:elasticsearch:" +
                                version + "' }",
                        source -> source.path("groovy/" + version + "/build.gradle")
                                .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindElasticsearchBuildRisks.IDENTITY_CONFLICT), printed);
                            assertTrue(printed.contains(version), printed);
                        })),
                buildGradleKts("dependencies { implementation(\"org.elasticsearch:elasticsearch:" +
                                version + "\") }",
                        source -> source.path("kotlin/" + version + "/build.gradle.kts")
                                .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindElasticsearchBuildRisks.IDENTITY_CONFLICT), printed);
                            assertTrue(printed.contains(version), printed);
                        })));
    }

    @Test
    void marksOtherServerCoordinateAsIdentityBoundary() {
        rewriteRun(xml(ElasticsearchTestSupport.project("<dependencies>" +
                        ElasticsearchTestSupport.serverDependency("8.15.0") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindElasticsearchBuildRisks.SERVER_IDENTITY_BOUNDARY), printed);
                    assertTrue(printed.contains("8.15.0"), printed);
                })));
    }

    @Test
    void resolvesAnExclusivelyOwnedServerPropertyBeforeClassifyingIdentity() {
        rewriteRun(xml(ElasticsearchTestSupport.project(
                        "<properties><server.version>7.10.2</server.version></properties>" +
                        "<dependencies>" +
                        ElasticsearchTestSupport.serverDependency("${server.version}") +
                        "</dependencies>"),
                source -> source.path("pom.xml")
                        .after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(
                                    FindElasticsearchBuildRisks.IDENTITY_CONFLICT), printed);
                            assertTrue(printed.contains(
                                    "<server.version>7.10.2</server.version>"), printed);
                        })));
    }

    @ParameterizedTest(name = "unsupported lower/fixed source {0} is marked")
    @ValueSource(strings = {"1.15.1", "1.17.5", "1.17.7", "1.20.6", "1.21.3"})
    void marksNonWhitelistFixedVersions(String version) {
        rewriteRun(xml(ElasticsearchTestSupport.pom(version),
                source -> source.path(version + "/pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindElasticsearchBuildRisks.OUTSIDE), printed);
                    assertTrue(printed.contains(version), printed);
                })));
    }

    @Test
    void marksVersionlessUnresolvedAndSharedMavenOwners() {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom(null),
                        source -> source.path("versionless/pom.xml")
                                .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindElasticsearchBuildRisks.OWNER),
                                        after::printAll))),
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("${missing}", "") +
                                "</dependencies>"),
                        source -> source.path("missing/pom.xml")
                                .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindElasticsearchBuildRisks.OWNER),
                                        after::printAll))),
                xml(ElasticsearchTestSupport.project("<properties><v>1.17.6</v></properties><dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("${v}", "") +
                                ElasticsearchTestSupport.dependency("x", "shared", "${v}", "") +
                                "</dependencies>"),
                        source -> source.path("shared/pom.xml")
                                .after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains(FindElasticsearchBuildRisks.OWNER),
                                    after::printAll);
                        })));
    }

    @Test
    void marksMavenAndGradleVariants() {
        rewriteRun(
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("1.17.6",
                                        "<classifier>tests</classifier>") +
                                ElasticsearchTestSupport.testcontainersDependency("1.17.6",
                                        "<type>test-jar</type>") + "</dependencies>"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertEquals(2, ElasticsearchTestSupport.occurrences(
                                        after.printAll(), FindElasticsearchBuildRisks.VARIANT)))),
                buildGradle("""
                        dependencies {
                          implementation 'org.testcontainers:elasticsearch:1.17.6@zip'
                          implementation group: 'org.testcontainers', name: 'elasticsearch',
                                         version: '1.17.6', classifier: 'tests'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertEquals(2, ElasticsearchTestSupport.occurrences(
                                after.printAll(), FindElasticsearchBuildRisks.VARIANT)))));
    }

    @Test
    void marksDynamicCatalogAndPlatformOwners() {
        rewriteRun(
                buildGradle("""
                        def v = '1.17.6'
                        dependencies {
                          testImplementation "org.testcontainers:elasticsearch:${v}"
                          testImplementation libs.testcontainers.elasticsearch
                          testImplementation platform('org.testcontainers:testcontainers-bom:1.17.6')
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindElasticsearchBuildRisks.OWNER),
                                after::printAll))),
                buildGradleKts("""
                        val v = "1.17.6"
                        dependencies {
                          testImplementation("org.testcontainers:elasticsearch:$v")
                          testImplementation(libs.testcontainers.elasticsearch)
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindElasticsearchBuildRisks.OWNER),
                                after::printAll))));
    }

    @Test
    void sourceAndTargetNeedNoBuildRiskMarker() {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom("1.17.6"), source -> source.path("source/pom.xml")),
                xml(ElasticsearchTestSupport.pom("1.21.4"), source -> source.path("target/pom.xml")),
                buildGradle("dependencies { testImplementation 'org.testcontainers:elasticsearch:1.17.6' }"),
                buildGradleKts("dependencies { testImplementation(\"org.testcontainers:elasticsearch:1.21.4\") }"));
    }

    @Test
    void generatedBuildFilesAreExcluded() {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom("2.0.0"),
                        source -> source.path("target/generated/pom.xml")),
                buildGradle("dependencies { testImplementation 'org.elasticsearch:elasticsearch:7.10.2' }",
                        source -> source.path("build/generated/build.gradle")));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("2.0.0", "") +
                                ElasticsearchTestSupport.serverDependency("7.10.2") + "</dependencies>"),
                        source -> source.path("pom.xml")
                                .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertEquals(1, ElasticsearchTestSupport.occurrences(
                                    printed, ElasticsearchUpgradeSupport.TARGET_CONFLICT), printed);
                            assertEquals(1, ElasticsearchTestSupport.occurrences(
                                    printed, FindElasticsearchBuildRisks.IDENTITY_CONFLICT), printed);
                        })));
    }
}
