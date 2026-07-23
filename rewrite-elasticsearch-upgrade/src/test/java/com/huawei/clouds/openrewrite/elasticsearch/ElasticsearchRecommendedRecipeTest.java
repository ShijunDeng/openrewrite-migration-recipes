package com.huawei.clouds.openrewrite.elasticsearch;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class ElasticsearchRecommendedRecipeTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.elasticsearch.MigrateElasticsearchTo1_21_4";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(ElasticsearchTestSupport.recipe(RECIPE))
                .parser(ElasticsearchTestSupport.parser());
    }

    @Test
    void upgradesDependencyRunsOfficialLeafAndMarksRuntimeBoundary() {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom("1.17.6"),
                        ElasticsearchTestSupport.pom("1.21.4"),
                        source -> source.path("pom.xml")),
                java("""
                        import org.testcontainers.elasticsearch.ElasticsearchContainer;
                        class Containers {
                            ElasticsearchContainer elasticsearch = new ElasticsearchContainer();
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(
                            "new ElasticsearchContainer(\"docker.elastic.co/elasticsearch/elasticsearch:7.9.2\")"),
                            printed);
                    assertTrue(printed.contains(
                            FindElasticsearchContainerSourceRisks.OPERATIONAL_DEFAULTS), printed);
                })));
    }

    @Test
    void neverConvertsElasticsearchServerToTheTestcontainersTarget() {
        rewriteRun(
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.serverDependency("7.10.2") + "</dependencies>"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("org.elasticsearch"), printed);
                            assertTrue(printed.contains("<version>7.10.2</version>"), printed);
                            assertTrue(printed.contains(FindElasticsearchBuildRisks.IDENTITY_CONFLICT), printed);
                            assertFalse(printed.contains("<groupId>org.testcontainers</groupId>"), printed);
                            assertFalse(printed.contains("<version>1.21.4</version>"), printed);
                        })),
                buildGradle("dependencies { implementation 'org.elasticsearch:elasticsearch:7.9.3' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("org.elasticsearch:elasticsearch:7.9.3"), printed);
                            assertTrue(printed.contains(FindElasticsearchBuildRisks.IDENTITY_CONFLICT), printed);
                            assertFalse(printed.contains("org.testcontainers:elasticsearch:1.21.4"), printed);
                        })));
    }

    @Test
    void higherTestcontainersVersionRemainsAndGetsExactConflictMarker() {
        rewriteRun(xml(ElasticsearchTestSupport.pom("2.0.0"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>2.0.0</version>"), printed);
                    assertTrue(printed.contains(ElasticsearchUpgradeSupport.TARGET_CONFLICT), printed);
                    assertFalse(printed.contains("<version>1.21.4</version>"), printed);
                })));
    }

    @Test
    void targetFutureOffListServerAndMixedRootsCannotRunSourceAutoOrRisks() {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom("1.21.4"),
                        source -> source.path("target/pom.xml")),
                untouchedSource("Target", "target/src/test/java/Target.java"),
                xml(ElasticsearchTestSupport.pom("1.21.5"),
                        source -> source.path("future/pom.xml")
                                .after(actual -> actual)
                                .afterRecipe(after -> assertTrue(after.printAll().contains(
                                        ElasticsearchUpgradeSupport.TARGET_CONFLICT),
                                        after::printAll))),
                untouchedSource("Future", "future/src/test/java/Future.java"),
                xml(ElasticsearchTestSupport.pom("1.20.6"),
                        source -> source.path("off-list/pom.xml")
                                .after(actual -> actual)
                                .afterRecipe(after -> assertTrue(after.printAll().contains(
                                        FindElasticsearchBuildRisks.OUTSIDE),
                                        after::printAll))),
                untouchedSource("OffList", "off-list/src/test/java/OffList.java"),
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.serverDependency("7.10.2") +
                                "</dependencies>"),
                        source -> source.path("server/pom.xml")
                                .after(actual -> actual)
                                .afterRecipe(after -> assertTrue(after.printAll().contains(
                                        FindElasticsearchBuildRisks.IDENTITY_CONFLICT),
                                        after::printAll))),
                untouchedSource("Server", "server/src/test/java/Server.java"),
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("1.17.6", "") +
                                ElasticsearchTestSupport.serverDependency("7.10.2") +
                                "</dependencies>"),
                        source -> source.path("mixed/pom.xml")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("<version>1.17.6</version>"), printed);
                                    assertTrue(printed.contains(
                                            FindElasticsearchBuildRisks.IDENTITY_CONFLICT), printed);
                                })),
                untouchedSource("Mixed", "mixed/src/test/java/Mixed.java"));
    }

    @Test
    void completeRecipeConvergesAfterOneChangingCycle() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(ElasticsearchTestSupport.pom("1.17.6"),
                        ElasticsearchTestSupport.pom("1.21.4"), source -> source.path("pom.xml")),
                java("""
                        import org.testcontainers.elasticsearch.ElasticsearchContainer;
                        class Containers {
                            ElasticsearchContainer elasticsearch = new ElasticsearchContainer();
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("elasticsearch:7.9.2"), printed);
                    assertTrue(printed.contains(
                            FindElasticsearchContainerSourceRisks.OPERATIONAL_DEFAULTS), printed);
                })));
    }

    @Test
    void generatedBuildAndJavaRemainByteStable() {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom("1.17.6"),
                        source -> source.path("target/generated/pom.xml")),
                java("""
                        import org.testcontainers.elasticsearch.ElasticsearchContainer;
                        class Generated {
                            ElasticsearchContainer elasticsearch = new ElasticsearchContainer();
                        }
                """, source -> source.path("build/generated/Generated.java")));
    }

    private static org.openrewrite.test.SourceSpecs untouchedSource(
            String className, String path) {
        return java("""
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class %s {
                    ElasticsearchContainer container = new ElasticsearchContainer();
                }
                """.formatted(className), source -> source.path(path)
                .afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("new ElasticsearchContainer()"), printed);
                    assertFalse(printed.contains("elasticsearch:7.9.2"), printed);
                    assertFalse(printed.contains(
                            FindElasticsearchContainerSourceRisks.OPERATIONAL_DEFAULTS), printed);
                }));
    }
}
