package com.huawei.clouds.openrewrite.springbootactuator;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
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

class ActuatorSourceMigrationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBoot3JakartaPackages";
    private static final String RISKS =
            "com.huawei.clouds.openrewrite.springbootactuator.FindSpringBootActuator3_5Risks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(AUTO)).parser(jakartaParser()).typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesServletValidationPersistenceAndInjectPackages() {
        rewriteRun(java(
                """
                import javax.inject.Inject;
                import javax.persistence.Entity;
                import javax.servlet.http.HttpServletRequest;
                import javax.validation.Valid;
                class LegacyWebEndpoint {
                    @Inject HttpServletRequest request;
                    @Valid @Entity Object body;
                }
                """,
                """
                import jakarta.inject.Inject;
                import jakarta.persistence.Entity;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.validation.Valid;
                class LegacyWebEndpoint {
                    @Inject HttpServletRequest request;
                    @Valid @Entity Object body;
                }
                """));
    }

    @Test
    void migratesJaxbJaxRsMailAndActivationPackages() {
        rewriteRun(java(
                """
                import javax.activation.DataSource;
                import javax.mail.Message;
                import javax.ws.rs.Path;
                import javax.xml.bind.JAXBContext;
                class Integrations {
                    DataSource source; Message message; @Path("/") Object endpoint; JAXBContext context;
                }
                """,
                """
                import jakarta.activation.DataSource;
                import jakarta.mail.Message;
                import jakarta.ws.rs.Path;
                import jakarta.xml.bind.JAXBContext;
                class Integrations {
                    DataSource source; Message message; @Path("/") Object endpoint; JAXBContext context;
                }
                """));
    }

    @Test
    void migratesOnlySelectedJavaxAnnotationTypes() {
        rewriteRun(java(
                """
                import javax.annotation.PostConstruct;
                import javax.annotation.PreDestroy;
                import javax.annotation.Resource;
                class Lifecycle {
                    @Resource Object dependency;
                    @PostConstruct void start() {}
                    @PreDestroy void stop() {}
                }
                """,
                """
                import jakarta.annotation.PostConstruct;
                import jakarta.annotation.PreDestroy;
                import jakarta.annotation.Resource;

                class Lifecycle {
                    @Resource Object dependency;
                    @PostConstruct void start() {}
                    @PreDestroy void stop() {}
                }
                """));
    }

    @Test
    void neverRewritesJavaSeJavaxPackages() {
        rewriteRun(java("""
                import javax.annotation.processing.Processor;
                import javax.crypto.Cipher;
                import javax.naming.Context;
                import javax.sql.DataSource;
                import javax.transaction.xa.XAResource;
                class JavaSeApis {
                    Processor processor; Cipher cipher; Context context; DataSource dataSource; XAResource xa;
                }
                """));
    }

    @Test
    void officialJakartaLeavesSkipGeneratedJava() {
        rewriteRun(java("""
                import javax.servlet.http.HttpServletRequest;
                class GeneratedEndpoint {
                    HttpServletRequest request;
                }
                """, source -> source.path("target/generated-sources/GeneratedEndpoint.java")));
    }

    @Test
    void officialJakartaCompositionIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                import javax.servlet.http.HttpServletRequest;
                class LegacyEndpoint {
                    HttpServletRequest request;
                }
                """,
                """
                import jakarta.servlet.http.HttpServletRequest;
                class LegacyEndpoint {
                    HttpServletRequest request;
                }
                """));
    }

    @Test
    void marksApacheLicensedRealHttpTraceRepositoryFixture() throws IOException {
        rewriteRun(ActuatorSourceMigrationTest::riskSpec,
                java(fixture("service-registry-httptrace.java"), source -> source
                        .path("src/main/java/net/maritimeconnectivity/serviceregistry/config/GlobalConfig.java")
                        .after(actual -> {
                    assertTrue(actual.contains(FindActuatorSourceRisks.HTTP_EXCHANGES));
                    assertTrue(actual.contains("management.trace.http.enabled"));
                    return actual;
                })));
    }

    @Test
    void marksCustomEndpointHealthSecurityAndMicrometerBoundaries() {
        rewriteRun(ActuatorSourceMigrationTest::riskSpec,
                java("""
                        import io.micrometer.core.instrument.MeterRegistry;
                        import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics;
                        import org.springframework.boot.actuate.endpoint.OperationResponseBody;
                        import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
                        import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
                        import org.springframework.boot.actuate.health.HealthIndicator;
                        import org.springframework.security.web.SecurityFilterChain;
                        @Endpoint(id = "tenant", enableByDefault = true)
                        class TenantEndpoint {
                            MeterRegistry registry;
                            JvmInfoMetrics jvm;
                            SecurityFilterChain security;
                            HealthIndicator health;
                            @ReadOperation OperationResponseBody read() { return null; }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindActuatorSourceRisks.CUSTOM_ENDPOINT));
                    assertTrue(actual.contains(FindActuatorSourceRisks.HEALTH));
                    assertTrue(actual.contains(FindActuatorSourceRisks.SECURITY));
                    assertTrue(actual.contains(FindActuatorSourceRisks.MICROMETER));
                    assertTrue(actual.contains(FindActuatorSourceRisks.JVM_INFO));
                    return actual;
                })));
    }

    @Test
    void marksActuatorJacksonIsolationAndCloudFoundryExposure() {
        rewriteRun(ActuatorSourceMigrationTest::riskSpec,
                java("""
                        import com.fasterxml.jackson.databind.ObjectMapper;
                        import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
                        import org.springframework.boot.actuate.autoconfigure.endpoint.expose.EndpointExposure;
                        @Endpoint(id = "json")
                        class JsonEndpoint {
                            ObjectMapper mapper;
                            EndpointExposure exposure = EndpointExposure.CLOUD_FOUNDRY;
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindActuatorSourceRisks.JACKSON));
                    assertTrue(actual.contains(FindActuatorSourceRisks.CLOUD_FOUNDRY));
                    return actual;
                })));
    }

    @Test
    void marksRemainingJavaEeButNotJavaSeJavaxImports() {
        rewriteRun(ActuatorSourceMigrationTest::riskSpec,
                java("""
                        import javax.annotation.security.RolesAllowed;
                        import javax.annotation.processing.Processor;
                        import javax.transaction.UserTransaction;
                        import javax.transaction.xa.XAResource;
                        class MixedJavax {
                            RolesAllowed roles; Processor processor; UserTransaction transaction; XAResource xa;
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindActuatorSourceRisks.JAKARTA));
                    String processor = actual.substring(actual.indexOf("javax.annotation.processing"));
                    assertFalse(processor.substring(0, processor.indexOf('\n'))
                            .contains(FindActuatorSourceRisks.JAKARTA));
                    String xa = actual.substring(actual.indexOf("javax.transaction.xa"));
                    assertFalse(xa.substring(0, xa.indexOf('\n'))
                            .contains(FindActuatorSourceRisks.JAKARTA));
                    return actual;
                })));
    }

    @Test
    void skipsGeneratedJavaAndMarkersAreIdempotent() {
        rewriteRun(ActuatorSourceMigrationTest::riskSpec,
                java("import org.springframework.boot.actuate.trace.http.HttpTraceRepository; class A { HttpTraceRepository r; }",
                        source -> source.path("target/generated/A.java")));
        rewriteRun(spec -> riskSpec(spec.cycles(2).expectedCyclesThatMakeChanges(1)),
                java("import org.springframework.boot.actuate.trace.http.HttpTraceRepository; class A { HttpTraceRepository r; }",
                        source -> source.after(actual -> {
                            assertTrue(actual.contains(FindActuatorSourceRisks.HTTP_EXCHANGES));
                            return actual;
                        })));
    }

    private static void riskSpec(RecipeSpec spec) {
        spec.recipe(recipe(RISKS)).typeValidationOptions(TypeValidation.none());
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream stream = ActuatorSourceMigrationTest.class.getResourceAsStream(
                "/fixtures/real/" + name)) {
            assertNotNull(stream, "Missing real-repository fixture " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JavaParser.Builder<?, ?> jakartaParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package javax.inject; public @interface Inject {}",
                "package javax.persistence; public @interface Entity {}",
                "package javax.servlet.http; public interface HttpServletRequest {}",
                "package javax.validation; public @interface Valid {}",
                "package javax.activation; public interface DataSource {}",
                "package javax.mail; public class Message {}",
                "package javax.ws.rs; public @interface Path { String value(); }",
                "package javax.xml.bind; public class JAXBContext {}",
                "package javax.annotation; public @interface PostConstruct {}",
                "package javax.annotation; public @interface PreDestroy {}",
                "package javax.annotation; public @interface Resource {}"
        );
    }
}
