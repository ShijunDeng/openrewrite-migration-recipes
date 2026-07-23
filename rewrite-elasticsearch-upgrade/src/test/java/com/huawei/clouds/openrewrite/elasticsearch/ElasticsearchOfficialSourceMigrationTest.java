package com.huawei.clouds.openrewrite.elasticsearch;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class ElasticsearchOfficialSourceMigrationTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.elasticsearch.MakeElasticsearchContainerImageExplicit";
    private static final String IMAGE =
            "docker.elastic.co/elasticsearch/elasticsearch:7.9.2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(ElasticsearchTestSupport.recipe(RECIPE))
                .parser(ElasticsearchTestSupport.parser());
    }

    @Test
    void replacesNoArgConstructorWithTheSameDefaultImage() {
        rewriteRun(
                selectedPom(),
                java(
                """
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class Containers {
                    ElasticsearchContainer elasticsearch = new ElasticsearchContainer();
                }
                """,
                """
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class Containers {
                    ElasticsearchContainer elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.9.2");
                }
                """));
    }

    @Test
    void handlesChainedCallsAndTryWithResources() {
        rewriteRun(
                selectedPom(),
                java(
                """
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class Containers {
                    void run() {
                        try (ElasticsearchContainer elasticsearch =
                                 new ElasticsearchContainer().withEnv("foo", "bar")) {
                            elasticsearch.start();
                        }
                    }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("new ElasticsearchContainer(\"" + IMAGE + "\")"), printed);
                    assertTrue(printed.contains(".withEnv(\"foo\", \"bar\")"), printed);
                })));
    }

    @Test
    void explicitStringAndDockerImageNameConstructorsRemainUnchanged() {
        rewriteRun(spec -> spec.expectedCyclesThatMakeChanges(1),
                selectedPom(),
                java("""
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                import org.testcontainers.utility.DockerImageName;
                class Containers {
                    ElasticsearchContainer a =
                        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.10.4");
                    ElasticsearchContainer b =
                        new ElasticsearchContainer(DockerImageName.parse("elasticsearch:8.10.4"));
                }
                """));
    }

    @Test
    void unrelatedSameSimpleNameRemainsUnchanged() {
        rewriteRun(spec -> spec.expectedCyclesThatMakeChanges(1),
                selectedPom(),
                java("""
                class ElasticsearchContainer {
                    ElasticsearchContainer() {}
                }
                class Local {
                    ElasticsearchContainer value = new ElasticsearchContainer();
                }
                """));
    }

    @Test
    void generatedSourcesAreExcluded() {
        rewriteRun(spec -> spec.expectedCyclesThatMakeChanges(1),
                selectedPom(),
                java("""
                import org.testcontainers.elasticsearch.ElasticsearchContainer;
                class Generated {
                    ElasticsearchContainer value = new ElasticsearchContainer();
                }
                """, source -> source.path("build/generated/sources/Generated.java")));
    }

    @Test
    void officialLeafIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                selectedPom(),
                java("""
                        import org.testcontainers.elasticsearch.ElasticsearchContainer;
                        class Containers {
                            ElasticsearchContainer elasticsearch = new ElasticsearchContainer();
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertTrue(after.printAll().contains("new ElasticsearchContainer(\"" + IMAGE + "\")"),
                            after::printAll);
                })));
    }

    @Test
    void processesPinnedTestcontainersUpstreamFixture() throws Exception {
        rewriteRun(
                selectedPom(),
                java(fixture("testcontainers-elasticsearch-container-test.java"),
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("new ElasticsearchContainer(\"" + IMAGE + "\")"), printed);
                    assertFalse(printed.contains("new ElasticsearchContainer()"), printed);
                })));
    }

    @Test
    void processesPinnedElasticApmFixture() throws Exception {
        rewriteRun(
                selectedPom(),
                java(fixture("elastic-apm-real-reporter.java"),
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("new ElasticsearchContainer(\"" + IMAGE + "\")"), printed);
                    assertFalse(printed.contains("new ElasticsearchContainer()"), printed);
                })));
    }

    private static org.openrewrite.test.SourceSpecs selectedPom() {
        return xml(ElasticsearchTestSupport.pom("1.17.6"),
                source -> source.path("pom.xml"));
    }

    private static String fixture(String name) throws IOException, URISyntaxException {
        return Files.readString(Path.of(ElasticsearchOfficialSourceMigrationTest.class
                .getResource("/fixtures/real/" + name).toURI()));
    }
}
