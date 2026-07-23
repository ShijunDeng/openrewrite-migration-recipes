package com.huawei.clouds.openrewrite.springboot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.config.Environment;
import org.openrewrite.java.spring.SpringConfigFile;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringBootOfficialConfigurationMigrationTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springboot.MigrateOfficialSpringBootConfiguration";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe())
                .allSources(source -> source.markers(new SpringConfigFile(Tree.randomId())));
    }

    @Test
    void migratesPropertiesAcrossBootGenerations() {
        rewriteRun(properties(
                """
                server.max-http-header-size=16KB
                spring.codec.max-in-memory-size=4MB
                spring.mvc.converters.preferred-json-mapper=gson
                management.endpoints.enabled-by-default=false
                """,
                """
                server.max-http-request-header-size=16KB
                spring.http.codecs.max-in-memory-size=4MB
                spring.http.converters.preferred-json-mapper=gson
                management.endpoints.access.default=none
                """,
                source -> source.path("src/main/resources/application.properties")));
    }

    @ParameterizedTest(name = "Single-cycle property mapping {0}")
    @CsvSource(delimiter = '|', value = {
            "server.max-http-header-size=16KB|server.max-http-request-header-size=16KB",
            "spring.codec.max-in-memory-size=4MB|spring.http.codecs.max-in-memory-size=4MB",
            "spring.mvc.converters.preferred-json-mapper=gson|spring.http.converters.preferred-json-mapper=gson",
            "management.endpoints.enabled-by-default=false|management.endpoints.access.default=none"
    })
    void eachCrossGenerationPropertyMappingStabilizesInOneCycle(String before, String after) {
        rewriteRun(properties(before, after,
                source -> source.path("src/main/resources/application.properties")));
    }

    @Test
    void migratesNestedYamlKeysAndValues() {
        rewriteRun(yaml(
                """
                server:
                  max-http-header-size: 16KB
                spring:
                  codec:
                    log-request-details: true
                  graphql:
                    path: /graphql
                management:
                  endpoint:
                    beans:
                      enabled: true
                """,
                """
                server:
                  max-http-request-header-size: 16KB
                spring:
                  graphql:
                    http.path: /graphql
                  http.codecs.log-request-details: true
                management:
                  endpoint:
                    beans:
                      access: read-only
                """,
                source -> source.path("src/main/resources/application.yml")));
    }

    @Test
    void migratesSamlIdentityProviderForPropertiesAndYaml() {
        rewriteRun(
                properties(
                        "spring.security.saml2.relyingparty.registration.acme.identityprovider.entity-id=https://idp",
                        "spring.security.saml2.relyingparty.registration.acme.assertingparty.entity-id=https://idp",
                        source -> source.path("src/main/resources/application.properties")),
                yaml(
                        """
                        spring:
                          security:
                            saml2:
                              relyingparty:
                                registration:
                                  acme:
                                    identityprovider:
                                      entity-id: https://idp
                        """,
                        """
                        spring:
                          security:
                            saml2:
                              relyingparty:
                                registration:
                                  acme:
                                    assertingparty:
                                      entity-id: https://idp
                        """,
                        source -> source.path("src/main/resources/application.yml"))
        );
    }

    @Test
    void reusesOfficialBoot25DatabaseCredentialMigration() {
        rewriteRun(spec -> spec.recipe(recipe(
                        "com.huawei.clouds.openrewrite.springboot.SpringBootConfiguration_2_5")),
                properties(
                        "spring.liquibase.url=jdbc:postgresql://localhost/app",
                        """
                        spring.liquibase.password=${spring.datasource.password}
                        spring.liquibase.url=jdbc:postgresql://localhost/app
                        spring.liquibase.username=${spring.datasource.username}
                        """,
                        source -> source.path("src/main/resources/application.properties")),
                yaml(
                        """
                        spring:
                          flyway:
                            url: jdbc:postgresql://localhost/app
                        """,
                        """
                        spring.flyway:
                          url: jdbc:postgresql://localhost/app
                          username: ${spring.datasource.username}
                          password: ${spring.datasource.password}
                        """,
                        source -> source.path("src/main/resources/application.yml"))
        );
    }

    @Test
    void destructiveBootstrapMergeRequiresExplicitOptIn() {
        rewriteRun(spec -> spec.recipe(recipe(
                        "com.huawei.clouds.openrewrite.springboot.MergeBootstrapIntoApplicationExplicitly")),
                yaml(
                        "spring.application.name: demo",
                        """
                        spring.application.name: demo
                        remote.config.enabled: true
                        """,
                        source -> source.path("src/main/resources/application.yml")),
                yaml(
                        """
                        remote:
                          config:
                            enabled: true
                        """,
                        doesNotExist(),
                        source -> source.path("src/main/resources/bootstrap.yml"))
        );
    }

    @Test
    void commentsOutOfficiallyRemovedPropertiesInsteadOfDeletingTheDecision() {
        rewriteRun(properties(
                """
                management.metrics.export.prometheus.histogram-flavor=Prometheus
                spring.flyway.clean-on-validation-error=true
                """,
                """
                # This property is deprecated: No longer supported. Works only when using the Prometheus simpleclient.
                # management.prometheus.metrics.export.histogram-flavor=Prometheus
                # This property is deprecated: Deprecated in Flyway 10.18
                # spring.flyway.clean-on-validation-error=true
                """,
                source -> source.path("src/main/resources/application.properties")));
    }

    @Test
    void generatedConfigurationIsStrictlyNoop() {
        rewriteRun(
                properties("server.max-http-header-size=16KB",
                        source -> source.path("target/generated/application.properties")),
                yaml("""
                        spring:
                          codec:
                            max-in-memory-size: 4MB
                        """, source -> source.path("build/generated/application.yml"))
        );
    }

    @Test
    void propertyMigrationIsTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties(
                        "spring.codec.max-in-memory-size=4MB",
                        "spring.http.codecs.max-in-memory-size=4MB",
                        source -> source.path("src/main/resources/application.properties")));
    }

    @Test
    void unrelatedApplicationConfigurationIsNoop() {
        rewriteRun(properties(
                """
                app.codec.max-in-memory-size=4MB
                custom.server.max-http-header-size=16KB
                """,
                source -> source.path("src/main/resources/application.properties")));
    }

    @Test
    void directOfficialControl() {
        rewriteRun(spec -> spec.recipe(Environment.builder().scanRuntimeClasspath().build()
                        .activateRecipes("org.openrewrite.java.spring.boot3.SpringBootProperties_3_5")),
                properties(
                        "spring.codec.max-in-memory-size=4MB",
                        "spring.http.codecs.max-in-memory-size=4MB",
                        source -> source.path("src/main/resources/application.properties")));
    }

    private static Recipe recipe() {
        return recipe(RECIPE);
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }
}
