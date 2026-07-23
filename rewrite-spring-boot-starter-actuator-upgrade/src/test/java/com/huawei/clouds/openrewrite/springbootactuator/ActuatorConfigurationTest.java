package com.huawei.clouds.openrewrite.springbootactuator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.ChangeSpringPropertyKey;
import org.openrewrite.java.spring.SpringConfigFile;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class ActuatorConfigurationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBootActuatorConfiguration";
    private static final String RISKS =
            "com.huawei.clouds.openrewrite.springbootactuator.FindSpringBootActuator3_5Risks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(AUTO))
                .allSources(source -> source.markers(new SpringConfigFile(Tree.randomId())));
    }

    @Test
    void officialSpringPropertyPrimitiveIsActive() {
        rewriteRun(spec -> spec.recipe(new ChangeSpringPropertyKey(
                        "management.metrics.export.prometheus",
                        "management.prometheus.metrics.export",
                        null)),
                properties(
                        "management.metrics.export.prometheus.enabled=true",
                        "management.prometheus.metrics.export.enabled=true",
                        source -> source.path("src/main/resources/application.properties")));
    }

    @Test
    void officialSpringPropertyPrimitiveMigratesValueAnnotation() {
        rewriteRun(spec -> spec
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.springframework.beans.factory.annotation;
                                public @interface Value {
                                    String value();
                                }
                                """))
                        .typeValidationOptions(TypeValidation.none()),
                java(
                        """
                        import org.springframework.beans.factory.annotation.Value;

                        class MetricsConfiguration {
                            @Value("${management.metrics.export.prometheus.enabled:false}")
                            boolean enabled;
                        }
                        """,
                        """
                        import org.springframework.beans.factory.annotation.Value;

                        class MetricsConfiguration {
                            @Value("${management.prometheus.metrics.export.enabled:false}")
                            boolean enabled;
                        }
                        """));
    }

    @Test
    void configurationCompositeContainsOfficialAccessRecipe() {
        Recipe configuration = recipe(AUTO);
        assertTrue(configuration.getRecipeList().stream().anyMatch(child ->
                "org.openrewrite.java.spring.boot3.SpringBootProperties_3_4_EnabledToAccess"
                        .equals(child.getName())));
        assertTrue(configuration.getRecipeList().stream()
                .filter(child -> "org.openrewrite.java.spring.ChangeSpringPropertyKey"
                        .equals(child.getName()))
                .count() >= 21);
    }

    @ParameterizedTest(name = "Properties moves {0} exporter")
    @ValueSource(strings = {
            "appoptics", "atlas", "datadog", "defaults", "dynatrace", "elastic", "ganglia", "graphite",
            "humio", "influx", "jmx", "kairos", "newrelic", "prometheus", "signalfx", "simple",
            "stackdriver", "statsd"
    })
    void movesEveryDocumentedMetricsExporterInProperties(String product) {
        rewriteRun(properties(
                "management.metrics.export." + product + ".enabled=true",
                "management." + product + ".metrics.export.enabled=true"));
    }

    @ParameterizedTest(name = "YAML moves {0} exporter")
    @ValueSource(strings = {
            "appoptics", "atlas", "datadog", "defaults", "dynatrace", "elastic", "ganglia", "graphite",
            "humio", "influx", "jmx", "kairos", "newrelic", "prometheus", "signalfx", "simple",
            "stackdriver", "statsd"
    })
    void movesEveryDocumentedMetricsExporterInFlattenedYaml(String product) {
        rewriteRun(yaml(
                "management.metrics.export." + product + ".enabled: true",
                "management." + product + ".metrics.export.enabled: true"));
    }

    @Test
    void movesNestedPrometheusTreeWithoutChangingValues() {
        rewriteRun(yaml(
                """
                management:
                  metrics:
                    export:
                      prometheus:
                        enabled: true
                        step: 30s
                """,
                """
                management:
                  prometheus.metrics.export:
                    enabled: true
                    step: 30s
                """));
    }

    @Test
    void migratesDynatraceV1ExceptionsAndLeavesWavefrontForReview() {
        rewriteRun(properties(
                """
                management.metrics.export.dynatrace.device-id=node-1
                management.metrics.export.dynatrace.group=prod
                management.metrics.export.dynatrace.technology-type=java
                management.metrics.export.wavefront.uri=https://wavefront.example
                """,
                """
                management.dynatrace.metrics.export.device-id=node-1
                management.dynatrace.metrics.export.group=prod
                management.dynatrace.metrics.export.technology-type=java
                management.metrics.export.wavefront.uri=https://wavefront.example
                """));
    }

    @Test
    void migratesProbesAndJmxProperties() {
        rewriteRun(properties(
                """
                management.health.probes.enabled=true
                management.endpoints.jmx.unique-names=true
                micrometer.observations.annotations.enabled=false
                """,
                """
                management.endpoint.health.probes.enabled=true
                spring.jmx.unique-names=true
                management.observations.annotations.enabled=false
                """));
    }

    @Test
    void migratesNestedProbesAndJmxYaml() {
        rewriteRun(yaml(
                """
                management:
                  health:
                    probes:
                      enabled: true
                  endpoints:
                    jmx:
                      unique-names: true
                micrometer:
                  observations:
                    annotations:
                      enabled: false
                """,
                """
                management:
                  endpoint.health.probes.enabled: true
                  observations.annotations.enabled: false
                spring.jmx.unique-names: true
                """));
    }

    @ParameterizedTest(name = "Endpoint enabled={0} becomes access")
    @ValueSource(strings = {"true", "TRUE", "false", "False"})
    void convertsEndpointEnabledBooleanInProperties(String value) {
        String access = "true".equals(value) ? "read-only" : "false".equals(value) ? "none" : value;
        rewriteRun(properties(
                "management.endpoint.loggers.enabled=" + value,
                "management.endpoint.loggers.access=" + access));
    }

    @Test
    void convertsGlobalAndEndpointAccessInYaml() {
        rewriteRun(yaml(
                """
                management:
                  endpoints:
                    enabled-by-default: false
                  endpoint:
                    loggers:
                      enabled: true
                """,
                """
                management:
                  endpoints:
                    access.default: none
                  endpoint:
                    loggers:
                      access: read-only
                """));
    }

    @Test
    void renamesNonBooleanEndpointAccessAndPreservesPushgatewayUrlForReview() {
        rewriteRun(
                properties(
                        """
                        management.endpoint.loggers.enabled=${LOGGERS_ENABLED}
                        management.prometheus.metrics.export.pushgateway.base-url=https://push.example:9091
                        """,
                        """
                        management.endpoint.loggers.access=${LOGGERS_ENABLED}
                        management.prometheus.metrics.export.pushgateway.base-url=https://push.example:9091
                        """),
                yaml(
                        """
                        management:
                          endpoint:
                            loggers:
                              enabled: ${LOGGERS_ENABLED}
                          prometheus:
                            metrics:
                              export:
                                pushgateway:
                                  base-url: https://push.example:9091
                        """,
                        """
                        management:
                          endpoint:
                            loggers:
                              access: ${LOGGERS_ENABLED}
                          prometheus:
                            metrics:
                              export:
                                pushgateway:
                                  base-url: https://push.example:9091
                        """)
        );
    }

    @Test
    void officialAccessRecipeLeavesUnknownEndpointForExplicitReview() {
        rewriteRun(properties("management.endpoint.company-sync.enabled=true"));
    }

    @Test
    void aggregateMarksNonLiteralAccessAfterOfficialKeyMigration() {
        rewriteRun(spec -> spec.recipe(recipe(
                        "com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBootActuatorTo3_5_15")),
                properties(
                        "management.endpoint.loggers.enabled=${LOGGERS_ENABLED}",
                        source -> source.after(actual -> {
                            assertTrue(actual.contains("management.endpoint.loggers.access=${LOGGERS_ENABLED}"));
                            assertTrue(actual.contains(FindActuatorConfigurationRisks.LEGACY_ENABLED));
                            return actual;
                        })));
    }

    @Test
    void skipsGeneratedConfigurationAndIsIdempotent() {
        rewriteRun(
                properties("management.metrics.export.prometheus.enabled=true",
                        source -> source.path("target/classes/application.properties")),
                yaml("management.metrics.export.prometheus.enabled: true",
                        source -> source.path("build/resources/main/application.yml"))
        );
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties("management.metrics.export.prometheus.enabled=true",
                           "management.prometheus.metrics.export.enabled=true"));
    }

    @Test
    void marksAllBehaviorSensitivePropertyFamilies() {
        rewriteRun(ActuatorConfigurationTest::riskSpec,
                properties("""
                        management.server.port=9090
                        management.endpoints.web.exposure.include=health,heapdump
                        management.endpoint.env.show-values=ALWAYS
                        management.endpoint.health.probes.enabled=true
                        management.endpoints.jackson.isolated-object-mapper=false
                        management.prometheus.metrics.export.enabled=true
                        management.prometheus.metrics.export.pushgateway.base-url=https://push:9091
                        micrometer.observations.annotations.enabled=true
                        management.endpoint.httptrace.enabled=true
                        management.endpoint.loggers.enabled=${LOGGERS}
                        management.endpoints.jmx.exposure.include=*
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.MANAGEMENT_SERVER));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.HEAPDUMP));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.SANITIZATION));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.HEALTH));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.JSON));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.METRICS));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.PUSHGATEWAY));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.REMOVED_OBSERVATIONS));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.HTTP_EXCHANGES));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.LEGACY_ENABLED));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.JMX));
                    return actual;
                })));
    }

    @Test
    void marksNestedYamlNodesButNotLookalikesOrGeneratedFiles() {
        rewriteRun(ActuatorConfigurationTest::riskSpec,
                yaml("""
                        management:
                          server:
                            port: 9090
                          endpoints:
                            web:
                              exposure:
                                include:
                                  - health
                                  - heapdump
                          endpoint:
                            configprops:
                              show-values: WHEN_AUTHORIZED
                        company:
                          management:
                            server:
                              port: 9999
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.MANAGEMENT_SERVER));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.HEAPDUMP));
                    assertTrue(actual.contains(FindActuatorConfigurationRisks.SANITIZATION));
                    String company = actual.substring(actual.indexOf("company:"));
                    assertFalse(company.contains(FindActuatorConfigurationRisks.MANAGEMENT_SERVER));
                    return actual;
                })),
                yaml("management.server.port: 9090",
                        source -> source.path("target/generated/application.yml"))
        );
    }

    @Test
    void migratesAndMarksMitLicensedRealRepositoryConfiguration() throws IOException {
        rewriteRun(spec -> spec.recipe(recipe(
                        "com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBootActuatorTo3_5_15")),
                properties(fixture("veilarbportefolje-application-local.properties"),
                        source -> source.path("src/main/resources/application-local.properties")
                                .after(actual -> {
                                    assertTrue(actual.contains(
                                            "management.endpoint.metrics.access=read-only"));
                                    assertTrue(actual.contains(
                                            "management.endpoint.prometheus.access=read-only"));
                                    assertTrue(actual.contains(
                                            "management.prometheus.metrics.export.enabled=true"));
                                    assertTrue(actual.contains(
                                            FindActuatorConfigurationRisks.ACCESS));
                                    assertTrue(actual.contains(
                                            FindActuatorConfigurationRisks.METRICS));
                                    return actual;
                                })));
    }

    private static void riskSpec(RecipeSpec spec) {
        spec.recipe(recipe(RISKS));
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream stream = ActuatorConfigurationTest.class.getResourceAsStream(
                "/fixtures/real/" + name)) {
            assertNotNull(stream, "Missing real-repository fixture " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
