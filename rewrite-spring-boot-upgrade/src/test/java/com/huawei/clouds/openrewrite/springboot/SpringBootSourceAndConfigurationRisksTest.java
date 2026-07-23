package com.huawei.clouds.openrewrite.springboot;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringBootSourceAndConfigurationRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springboot.FindSpringBoot3_5Risks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe()).parser(parser());
    }

    @Test
    void marksExactSourceRiskCategories() {
        rewriteRun(java(
                """
                import javax.jms.Message;

                import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
                import org.springframework.boot.json.YamlJsonParser;
                import org.springframework.context.SmartLifecycle;
                import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

                class MigrationBoundaries {
                    Message message;
                    Jackson2ObjectMapperBuilderCustomizer jackson;
                    YamlJsonParser parser;
                    SmartLifecycle lifecycle;
                    WebSecurityConfigurerAdapter security;
                }
                """,
                source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringBoot35SourceRisks.SECURITY), actual);
                    assertTrue(actual.contains(FindSpringBoot35SourceRisks.LIFECYCLE), actual);
                    assertTrue(actual.contains(FindSpringBoot35SourceRisks.REMOVED), actual);
                    assertTrue(actual.contains(FindSpringBoot35SourceRisks.INTEGRATION), actual);
                    assertTrue(actual.contains(FindSpringBoot35SourceRisks.JAKARTA), actual);
                    return actual;
                })));
    }

    @Test
    void preservesJavaSeJavaxImportsAndUnrelatedApplicationSource() {
        rewriteRun(java(
                """
                import javax.annotation.processing.Processor;
                import javax.sql.DataSource;
                import javax.transaction.xa.XAResource;

                class JavaSeApis {
                    Processor processor;
                    DataSource dataSource;
                    XAResource resource;
                }
                """));
    }

    @Test
    void marksEveryStructuredPropertiesRiskCategory() {
        rewriteRun(properties(
                """
                spring.config.import=optional:configserver:
                spring.main.allow-circular-references=true
                spring.mvc.pathmatch.matching-strategy=ant_path_matcher
                server.max-http-request-header-size=16KB
                server.shutdown=graceful
                logging.pattern.dateformat=yyyy-MM-dd
                spring.jpa.open-in-view=false
                spring.threads.virtual.enabled=true
                management.endpoints.web.exposure.include=health,info
                """,
                source -> source.path("src/main/resources/application.properties")
                        .after(actual -> {
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.CONFIG_DATA), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.CIRCULAR), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.WEB), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.HEADER), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.SHUTDOWN), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.LOGGING), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.SQL), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.VIRTUAL_THREADS), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.ACTUATOR), actual);
                            return actual;
                        })));
    }

    @Test
    void marksNestedYamlLeavesByTheirCompletePath() {
        rewriteRun(yaml(
                """
                spring:
                  config:
                    activate:
                      on-profile: prod
                  lifecycle:
                    timeout-per-shutdown-phase: 30s
                  sql:
                    init:
                      mode: always
                management:
                  endpoint:
                    health:
                      access: read-only
                """,
                source -> source.path("src/main/resources/application.yml")
                        .after(actual -> {
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.CONFIG_DATA), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.SHUTDOWN), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.SQL), actual);
                            assertTrue(actual.contains(FindSpringBoot35ConfigurationRisks.ACTUATOR), actual);
                            return actual;
                        })));
    }

    @Test
    void generatedAndUnrelatedFilesAreStrictlyNoop() {
        rewriteRun(
                java(
                        """
                        import javax.jms.Message;
                        import org.springframework.boot.json.YamlJsonParser;

                        class GeneratedRisk {
                            Message message;
                            YamlJsonParser parser;
                        }
                        """,
                        source -> source.path("target/generated-sources/GeneratedRisk.java")),
                properties(
                        """
                        spring.config.import=optional:configserver:
                        management.endpoints.web.exposure.include=*
                        """,
                        source -> source.path("build/generated/application.properties")),
                properties(
                        """
                        app.spring.config.import=local
                        custom.management.endpoint.health.access=read-only
                        """,
                        source -> source.path("src/main/resources/custom.properties"))
        );
    }

    @Test
    void riskMarkersAreTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties(
                        "spring.threads.virtual.enabled=true",
                        source -> source.path("src/main/resources/application.properties")
                                .after(actual -> {
                                    assertTrue(actual.contains(
                                            FindSpringBoot35ConfigurationRisks.VIRTUAL_THREADS), actual);
                                    return actual;
                                })));
    }

    private static Recipe recipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE);
    }

    private static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package javax.jms;
                public interface Message {
                }
                """,
                """
                package org.springframework.boot.autoconfigure.jackson;
                public interface Jackson2ObjectMapperBuilderCustomizer {
                }
                """,
                """
                package org.springframework.boot.json;
                public class YamlJsonParser {
                }
                """,
                """
                package org.springframework.context;
                public interface SmartLifecycle {
                }
                """,
                """
                package org.springframework.security.config.annotation.web.configuration;
                public abstract class WebSecurityConfigurerAdapter {
                }
                """);
    }
}
