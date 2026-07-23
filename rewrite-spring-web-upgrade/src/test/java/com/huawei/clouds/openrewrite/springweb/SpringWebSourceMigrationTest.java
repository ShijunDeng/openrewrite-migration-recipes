package com.huawei.clouds.openrewrite.springweb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringWebSourceMigrationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springweb.MigrateDeterministicSpringWeb6Java";
    private static final String RISKS =
            "com.huawei.clouds.openrewrite.springweb.FindSpringWeb6SourceAndConfigurationRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(AUTO)).parser(parser()).typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesAttributedJavaxPackagesToJakarta() {
        rewriteRun(java("""
                import javax.activation.DataSource;
                import javax.json.Json;
                import javax.json.bind.Jsonb;
                import javax.validation.Valid;
                import javax.xml.bind.JAXBContext;
                import javax.servlet.http.HttpServletRequest;
                class JakartaBoundaries {
                    HttpServletRequest request;
                    @Valid Object body;
                    JAXBContext context;
                    Json json;
                    Jsonb jsonb;
                    DataSource source;
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains("import jakarta.activation.DataSource;"));
            assertTrue(actual.contains("import jakarta.json.Json;"));
            assertTrue(actual.contains("import jakarta.json.bind.Jsonb;"));
            assertTrue(actual.contains("import jakarta.validation.Valid;"));
            assertTrue(actual.contains("import jakarta.xml.bind.JAXBContext;"));
            assertTrue(actual.contains("import jakarta.servlet.http.HttpServletRequest;"));
            assertFalse(actual.contains("import javax."));
            return actual;
        })));
    }

    @Test
    void movesAttributedReactorResourceFactoryType() {
        rewriteRun(java(
                """
                import org.springframework.http.client.reactive.ReactorResourceFactory;
                class ClientResources {
                    ReactorResourceFactory factory() {
                        return new ReactorResourceFactory();
                    }
                }
                """,
                """
                import org.springframework.http.client.ReactorResourceFactory;

                class ClientResources {
                    ReactorResourceFactory factory() {
                        return new ReactorResourceFactory();
                    }
                }
                """));
    }

    @Test
    void renamesExactContentCachingStatusAlias() {
        rewriteRun(java(
                """
                import org.springframework.web.util.ContentCachingResponseWrapper;
                class Status {
                    int value(ContentCachingResponseWrapper response) {
                        return response.getStatusCode();
                    }
                }
                """,
                """
                import org.springframework.web.util.ContentCachingResponseWrapper;
                class Status {
                    int value(ContentCachingResponseWrapper response) {
                        return response.getStatus();
                    }
                }
                """));
    }

    @Test
    void replacesHttpRequestMethodValueForSelectedAndImplicitReceivers() {
        rewriteRun(java(
                """
                import org.springframework.http.HttpRequest;
                class RequestNames implements HttpRequest {
                    String selected(HttpRequest request) {
                        return request.getMethodValue();
                    }
                    String implicit() {
                        return getMethodValue();
                    }
                }
                """,
                """
                import org.springframework.http.HttpRequest;
                class RequestNames implements HttpRequest {
                    String selected(HttpRequest request) {
                        return request.getMethod().name();
                    }
                    String implicit() {
                        return getMethod().name();
                    }
                }
                """));
    }

    @Test
    void replacesResponseStatusExceptionRawStatusForBothReceiverForms() {
        rewriteRun(java(
                """
                import org.springframework.web.server.ResponseStatusException;
                class StatusException extends ResponseStatusException {
                    int selected(ResponseStatusException exception) {
                        return exception.getRawStatusCode();
                    }
                    int implicit() {
                        return getRawStatusCode();
                    }
                }
                """,
                """
                import org.springframework.web.server.ResponseStatusException;
                class StatusException extends ResponseStatusException {
                    int selected(ResponseStatusException exception) {
                        return exception.getStatusCode().value();
                    }
                    int implicit() {
                        return getStatusCode().value();
                    }
                }
                """));
    }

    @ParameterizedTest(name = "migrates standard literal HttpMethod {0}")
    @ValueSource(strings = {"GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE"})
    void replacesOnlyStandardLiteralHttpMethodResolve(String method) {
        rewriteRun(java(
                """
                import org.springframework.http.HttpMethod;
                class Methods {
                    HttpMethod parse() {
                        return HttpMethod.resolve("%s");
                    }
                }
                """.formatted(method),
                """
                import org.springframework.http.HttpMethod;
                class Methods {
                    HttpMethod parse() {
                        return HttpMethod.valueOf("%s");
                    }
                }
                """.formatted(method)));
    }

    @Test
    void preservesDynamicUnknownLowercaseAndNullHttpMethodResolve() {
        rewriteRun(java("""
                import org.springframework.http.HttpMethod;
                class Methods {
                    HttpMethod parse(String value) {
                        HttpMethod one = HttpMethod.resolve(value);
                        HttpMethod two = HttpMethod.resolve("PROPFIND");
                        HttpMethod three = HttpMethod.resolve("get");
                        return HttpMethod.resolve(null);
                    }
                }
                """));
    }

    @Test
    void replacesForwardedParserOnlyForStableRequestIdentifier() {
        rewriteRun(java(
                """
                import java.net.InetSocketAddress;
                import org.springframework.http.HttpRequest;
                import org.springframework.web.util.UriComponentsBuilder;
                class Forwarded {
                    InetSocketAddress client(HttpRequest request, InetSocketAddress remote) {
                        return UriComponentsBuilder.parseForwardedFor(request, remote);
                    }
                }
                """,
                """
                import java.net.InetSocketAddress;
                import org.springframework.http.HttpRequest;
                class Forwarded {
                    InetSocketAddress client(HttpRequest request, InetSocketAddress remote) {
                        return org.springframework.web.util.ForwardedHeaderUtils.parseForwardedFor(request.getURI(), request.getHeaders(), remote);
                    }
                }
                """));
    }

    @Test
    void doesNotDuplicateSideEffectfulRequestExpression() {
        rewriteRun(java("""
                import java.net.InetSocketAddress;
                import org.springframework.http.HttpRequest;
                import org.springframework.web.util.UriComponentsBuilder;
                class Forwarded {
                    HttpRequest next() { return null; }
                    InetSocketAddress client(InetSocketAddress remote) {
                        return UriComponentsBuilder.parseForwardedFor(next(), remote);
                    }
                }
                """));
    }

    @Test
    void preservesUriComponentsBuilderImportWhenOtherUsesRemain() {
        rewriteRun(java("""
                import java.net.InetSocketAddress;
                import org.springframework.http.HttpRequest;
                import org.springframework.web.util.UriComponentsBuilder;
                class ForwardedAndUri {
                    Object values(HttpRequest request, InetSocketAddress remote, String uri) {
                        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(uri);
                        return UriComponentsBuilder.parseForwardedFor(request, remote);
                    }
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains("import org.springframework.web.util.UriComponentsBuilder;"));
            assertTrue(actual.contains(
                    "org.springframework.web.util.ForwardedHeaderUtils.parseForwardedFor("));
            return actual;
        })));
    }

    @Test
    void replacesStaticImportedForwardedParserAndRemovesObsoleteImport() {
        rewriteRun(java("""
                import java.net.InetSocketAddress;
                import org.springframework.http.HttpRequest;
                import static org.springframework.web.util.UriComponentsBuilder.parseForwardedFor;
                class StaticForwarded {
                    Object value(HttpRequest request, InetSocketAddress remote) {
                        return parseForwardedFor(request, remote);
                    }
                }
                """, source -> source.after(actual -> {
            assertFalse(actual.contains("import static org.springframework.web.util.UriComponentsBuilder"));
            assertTrue(actual.contains(
                    "org.springframework.web.util.ForwardedHeaderUtils.parseForwardedFor("));
            return actual;
        })));
    }

    @Test
    void doesNotPretendBasicAuthorizationRenameIsEquivalent() {
        rewriteRun(java("""
                import org.springframework.http.client.support.BasicAuthorizationInterceptor;
                class Security {
                    Object interceptor() {
                        return new BasicAuthorizationInterceptor("üser", "päss");
                    }
                }
                """));
    }

    @Test
    void preservesSameNamedBusinessSymbols() {
        rewriteRun(java("""
                class ReactorResourceFactory {}
                class ContentCachingResponseWrapper {
                    int getStatusCode() { return 200; }
                }
                class HttpMethod {
                    static HttpMethod resolve(String value) { return null; }
                }
                class Business {
                    ReactorResourceFactory resources = new ReactorResourceFactory();
                    int status(ContentCachingResponseWrapper response) {
                        HttpMethod.resolve("GET");
                        return response.getStatusCode();
                    }
                }
                """));
    }

    @Test
    void skipsGeneratedJavaAndAutoIsIdempotent() {
        rewriteRun(java(
                "import javax.servlet.http.HttpServletRequest; class Generated { HttpServletRequest request; }",
                source -> source.path("target/generated-sources/Generated.java")),
                java(
                        "import org.springframework.http.HttpMethod; class GeneratedCall { Object value() { return HttpMethod.resolve(\"GET\"); } }",
                        source -> source.path("build/generated/GeneratedCall.java")));
        rewriteRun(spec -> spec.recipe(recipe(AUTO)).parser(parser())
                        .typeValidationOptions(TypeValidation.none())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        "import org.springframework.http.client.reactive.ReactorResourceFactory; class Config { ReactorResourceFactory f; }",
                        "import org.springframework.http.client.ReactorResourceFactory;\n\nclass Config { ReactorResourceFactory f; }"));
    }

    @Test
    void marksBasicAuthorizationBehaviorInsteadOfAutoRenamingRealCnrFixture() {
        // consiglionazionaledellericerche/sprint-flows@d79ef47b06b0, reduced OIVRestConfiguration.java.
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        package it.cnr.si.config;
                        import org.springframework.http.client.support.BasicAuthorizationInterceptor;
                        public class OIVRestConfiguration {
                            Object interceptor(String username, String password) {
                                return new BasicAuthorizationInterceptor(username, password);
                            }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.BASIC_AUTH));
                    assertTrue(actual.contains("new BasicAuthorizationInterceptor(username, password)"));
                    assertFalse(actual.contains(
                            "import org.springframework.http.client.support.BasicAuthenticationInterceptor;"));
                    return actual;
                })));
    }

    @Test
    void marksSecondIndependentBasicAuthorizationFixture() {
        // yixingjia/flowgate@ed72bffe4b0f, reduced InfobloxClient.java.
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        package com.vmware.flowgate.infobloxworker.service;
                        import org.springframework.http.client.support.BasicAuthorizationInterceptor;
                        class InfobloxClient {
                            Object create(String user, String password) {
                                return new BasicAuthorizationInterceptor(user, password);
                            }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.BASIC_AUTH));
                    return actual;
                })));
    }

    @Test
    void marksHttpMethodEnumAssumptionsDynamicResolveAndCustomRequest() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        import java.util.EnumSet;
                        import org.springframework.http.HttpMethod;
                        import org.springframework.http.HttpRequest;
                        class Routing implements HttpRequest {
                            public String getMethodValue() { return "PROPFIND"; }
                            int route(HttpMethod method, String raw) {
                                EnumSet<HttpMethod> supported = EnumSet.of(HttpMethod.GET, HttpMethod.POST);
                                HttpMethod resolved = HttpMethod.resolve(raw);
                                int ordinal = method.ordinal();
                                return switch (method) {
                                    case GET -> ordinal;
                                    default -> 0;
                                };
                            }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.HTTP_METHOD));
                    return actual;
                })));
    }

    @Test
    void marksLegacyStatusCodeApis() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.http.client.ClientHttpResponse;
                        import org.springframework.web.server.ResponseStatusException;
                        class Statuses {
                            int client(ClientHttpResponse response) { return response.getRawStatusCode(); }
                            int exception(ResponseStatusException exception) {
                                return exception.getRawStatusCode() + exception.getStatus().value();
                            }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.STATUS_CODE));
                    return actual;
                })));
    }

    @Test
    void marksRemovedAsyncClientStack() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.web.client.AsyncRestTemplate;
                        class AsyncClient {
                            Object client() { return new AsyncRestTemplate(); }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.ASYNC_CLIENT));
                    return actual;
                })));
    }

    @Test
    void marksHttpClient5PortingBoundary() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        import org.apache.http.client.HttpClient;
                        import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
                        class CustomFactory extends HttpComponentsClientHttpRequestFactory {
                            HttpClient legacy;
                            void configure(HttpComponentsClientHttpRequestFactory factory) {
                                factory.setBufferRequestBody(true);
                            }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.HTTP_CLIENT_5));
                    assertTrue(actual.contains(FindSpringWebSourceRisks.BUFFERING), actual);
                    return actual;
                })));
    }

    @Test
    void marksRemovedRemotingAndMultipartTypesIncludingRealFixtureShape() {
        // chwshuang/web@b70de533fa3a, reduced MvcConfig.java CommonsMultipartResolver shape.
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean;
                        import org.springframework.web.multipart.commons.CommonsMultipartResolver;
                        class MvcConfig {
                            CommonsMultipartResolver multipartResolver() {
                                return new CommonsMultipartResolver();
                            }
                            Object remote() { return new HttpInvokerProxyFactoryBean(); }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.REMOTING));
                    assertTrue(actual.contains(FindSpringWebSourceRisks.MULTIPART));
                    return actual;
                })));
    }

    @Test
    void marksUriParserForwardedAndCorsBoundaries() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        import java.net.InetSocketAddress;
                        import java.util.List;
                        import org.springframework.http.HttpRequest;
                        import org.springframework.web.cors.CorsConfiguration;
                        import org.springframework.web.util.DefaultUriTemplateHandler;
                        import org.springframework.web.util.UriComponentsBuilder;
                        class WebPolicies {
                            Object parse(String value, HttpRequest request, InetSocketAddress remote) {
                                new DefaultUriTemplateHandler();
                                UriComponentsBuilder.fromUriString(value);
                                UriComponentsBuilder.parseForwardedFor(request, remote);
                                CorsConfiguration cors = new CorsConfiguration();
                                cors.setAllowedOrigins(List.of("*"));
                                cors.setAllowCredentials(true);
                                return cors;
                            }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.URI_PATH));
                    assertTrue(actual.contains(FindSpringWebSourceRisks.CORS));
                    return actual;
                })));
    }

    @Test
    void marksMessageConverterAndErrorHandlerExtensionPoints() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
                        import org.springframework.web.client.DefaultResponseErrorHandler;
                        class CustomConverter extends AbstractJackson2HttpMessageConverter {}
                        class CustomErrors extends DefaultResponseErrorHandler {
                            public void handleError() {}
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.MESSAGE_CONVERTER));
                    assertTrue(actual.contains(FindSpringWebSourceRisks.RESPONSE_ERROR));
                    return actual;
                })));
    }

    @Test
    void marksValidationDefaultsExceptionNegotiationAndControllerAdviceConstruction() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.validation.annotation.Validated;
                        import org.springframework.web.bind.annotation.ExceptionHandler;
                        import org.springframework.web.bind.annotation.RequestHeader;
                        import org.springframework.web.bind.annotation.RequestParam;
                        import org.springframework.web.method.ControllerAdviceBean;
                        @Validated
                        class Api {
                            void search(@RequestParam(defaultValue = "all") String query,
                                        @RequestHeader(defaultValue = "x") String header) {}
                            @ExceptionHandler(IllegalArgumentException.class)
                            String error() { return "bad"; }
                            Object advice() { return new ControllerAdviceBean(new Object()); }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.VALIDATION));
                    assertTrue(actual.contains(FindSpringWebSourceRisks.DEFAULT_VALUE));
                    assertTrue(actual.contains(FindSpringWebSourceRisks.CONTROLLER_ADVICE));
                    return actual;
                })));
    }

    @Test
    void marksRestClientRetrieveObservationAndHttpInterfaceBoundaries() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.web.client.RestClient;
                        import org.springframework.web.service.annotation.HttpExchange;
                        import org.springframework.web.service.invoker.HttpServiceProxyFactory;
                        import io.micrometer.observation.ObservationRegistry;
                        @HttpExchange("/inventory") interface InventoryClient {}
                        class Clients {
                            void invoke(RestClient.RequestHeadersSpec request,
                                        HttpServiceProxyFactory factory,
                                        ObservationRegistry registry,
                                        ClientObservation observation) {
                                request.retrieve();
                                factory.createClient(InventoryClient.class);
                                observation.start();
                            }
                        }
                        class ClientObservation { void start() {} }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.REST_CLIENT));
                    assertTrue(actual.contains(FindSpringWebSourceRisks.HTTP_INTERFACE));
                    assertTrue(actual.contains(FindSpringWebSourceRisks.OBSERVABILITY));
                    String businessCall = actual.substring(actual.indexOf("observation.start()"));
                    assertFalse(businessCall.substring(0, businessCall.indexOf(';'))
                            .contains(FindSpringWebSourceRisks.OBSERVABILITY));
                    return actual;
                })));
    }

    @Test
    void marksBufferingAndReactiveConnectorResources() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.http.client.BufferingClientHttpRequestFactory;
                        import org.springframework.http.client.reactive.JettyClientHttpConnector;
                        class Connectors {
                            Object buffering() { return new BufferingClientHttpRequestFactory(); }
                            Object jetty() { return new JettyClientHttpConnector(); }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.BUFFERING));
                    assertTrue(actual.contains(FindSpringWebSourceRisks.JETTY_REACTOR));
                    return actual;
                })));
    }

    @Test
    void sourceMarkersIgnoreSameNamedTypesAndGeneratedFiles() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                java("""
                        class AsyncRestTemplate {}
                        class CorsConfiguration {
                            void setAllowCredentials(Boolean value) {}
                        }
                        class RestClient {
                            static class Request { Object retrieve() { return null; } }
                        }
                        class Business {
                            Object use(RestClient.Request request, CorsConfiguration cors) {
                                cors.setAllowCredentials(true);
                                return request.retrieve();
                            }
                        }
                        """),
                java("""
                        import org.springframework.web.client.AsyncRestTemplate;
                        class Generated { AsyncRestTemplate value; }
                        """, source -> source.path("build/generated/Generated.java")));
    }

    @Test
    void kotlinMarksExactRemovedSpringTypesWithoutMatchingBusinessNames() {
        rewriteRun(spec -> spec.recipe(recipe(RISKS))
                        .parser(KotlinParser.builder().dependsOn(
                                "package org.springframework.web.client\nclass AsyncRestTemplate",
                                "package business\nclass AsyncRestTemplate"))
                        .typeValidationOptions(TypeValidation.none()),
                kotlin("""
                        import org.springframework.web.client.AsyncRestTemplate
                        class Client {
                            lateinit var spring: AsyncRestTemplate
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebSourceRisks.ASYNC_CLIENT));
                    return actual;
                })),
                kotlin("""
                        import business.AsyncRestTemplate
                        class Business {
                            lateinit var local: AsyncRestTemplate
                        }
                        """));
    }

    @ParameterizedTest(name = "marks exact Spring Web configuration key {0}")
    @ValueSource(strings = {
            "server.forward-headers-strategy",
            "spring.codec.max-in-memory-size",
            "spring.http.client.factory",
            "spring.http.client.connect-timeout",
            "spring.http.client.read-timeout",
            "spring.mvc.converters.preferred-json-mapper",
            "spring.mvc.problemdetails.enabled",
            "spring.mvc.servlet.path",
            "spring.mvc.throw-exception-if-no-handler-found",
            "spring.web.locale",
            "spring.servlet.multipart.max-file-size",
            "spring.web.resources.static-locations",
            "spring.mvc.contentnegotiation.favor-parameter",
            "spring.jackson.deserialization.fail-on-unknown-properties",
            "spring.mvc.pathmatch.matching-strategy"
    })
    void marksEveryExactConfigurationBoundary(String key) {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                properties(key + "=value\n", source -> source.after(actual -> {
                    assertTrue(actual.contains(key.startsWith("spring.servlet.multipart.")
                            ? FindSpringWebConfigurationRisks.MULTIPART
                            : FindSpringWebConfigurationRisks.CONFIG));
                    return actual;
                })));
    }

    @ParameterizedTest(name = "ignores Spring Web configuration lookalike {0}")
    @ValueSource(strings = {
            "app.server.forward-headers-strategy",
            "springish.codec.max-in-memory-size",
            "spring.http.client.factory-extra",
            "company.spring.mvc.servlet.path",
            "spring.web.locale-extra",
            "spring.servlet.multipartish.max-file-size"
    })
    void ignoresConfigurationLookalikes(String key) {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec, properties(key + "=value\n"));
    }

    @Test
    void marksExactPropertiesNestedYamlAndXmlValues() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                properties("""
                        spring.http.client.connect-timeout=5s
                        spring.servlet.multipart.max-file-size=10MB
                        normal.key=true
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebConfigurationRisks.CONFIG));
                    assertTrue(actual.contains(FindSpringWebConfigurationRisks.MULTIPART));
                    return actual;
                })),
                yaml("""
                        spring:
                          mvc:
                            problemdetails:
                              enabled: true
                          web:
                            resources:
                              static-locations: classpath:/public
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebConfigurationRisks.CONFIG));
                    return actual;
                })),
                xml("""
                        <beans>
                          <bean class="javax.servlet.http.HttpServletRequest"/>
                          <bean class="org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean"/>
                          <bean class="org.springframework.web.multipart.commons.CommonsMultipartResolver"/>
                          <bean class="org.springframework.http.client.HttpComponentsClientHttpRequestFactory">
                            <property name="bufferRequestBody" value="true"/>
                          </bean>
                        </beans>
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebConfigurationRisks.JAKARTA));
                    assertTrue(actual.contains(FindSpringWebConfigurationRisks.REMOTING));
                    assertTrue(actual.contains(FindSpringWebConfigurationRisks.MULTIPART));
                    assertTrue(actual.contains(FindSpringWebConfigurationRisks.HTTP_CLIENT));
                    assertTrue(actual.contains(FindSpringWebConfigurationRisks.XML_REMOVED));
                    return actual;
                })));
    }

    @Test
    void configurationMarkersIgnoreLookalikesPomTextAndGeneratedFiles() {
        rewriteRun(SpringWebSourceMigrationTest::riskSpec,
                properties("app.spring.http.client.factory=safe\n"),
                yaml("springish:\n  mvc:\n    servlet:\n      path: /safe\n"),
                xml("<project><properties><spring.mvc.servlet.path>/api</spring.mvc.servlet.path></properties></project>",
                        source -> source.path("pom.xml")),
                xml("<bean class=\"company.javax.servlet.http.HttpServletRequest\"/>"),
                xml("<bean class=\"com.acme.Cache\"><property name=\"readTimeout\" value=\"5s\"/></bean>"),
                properties("spring.http.client.factory=http-components\n",
                        source -> source.path("target/classes/application.properties")));
    }

    @Test
    void riskMarkersAreIdempotent() {
        rewriteRun(spec -> {
                    riskSpec(spec);
                    spec.cycles(2).expectedCyclesThatMakeChanges(1);
                },
                properties("spring.http.client.factory=http-components\n",
                        "~~(" + FindSpringWebConfigurationRisks.CONFIG +
                        ")~~>spring.http.client.factory=http-components\n"));
    }

    @Test
    void recommendedAggregateRunsUpgradeAutoAndMarkPhases() {
        rewriteRun(spec -> spec.recipe(recipe(
                                "com.huawei.clouds.openrewrite.springweb.MigrateSpringWebTo6_2_19"))
                        .parser(parser()).typeValidationOptions(TypeValidation.none()),
                pomXml(pom("5.3.27"), pom("6.2.19")),
                java("""
                        import org.springframework.http.client.reactive.ReactorResourceFactory;
                        import org.springframework.http.client.support.BasicAuthorizationInterceptor;
                        class ClientConfig {
                            ReactorResourceFactory resources = new ReactorResourceFactory();
                            Object auth() { return new BasicAuthorizationInterceptor("u", "p"); }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains("import org.springframework.http.client.ReactorResourceFactory;"));
                    assertTrue(actual.contains(FindSpringWebSourceRisks.BASIC_AUTH));
                    return actual;
                })));
    }

    private static void riskSpec(RecipeSpec spec) {
        spec.recipe(recipe(RISKS)).parser(parser()).typeValidationOptions(TypeValidation.none());
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package javax.servlet.http; public interface HttpServletRequest {}",
                "package jakarta.servlet.http; public interface HttpServletRequest {}",
                "package javax.validation; public @interface Valid {}",
                "package jakarta.validation; public @interface Valid {}",
                "package javax.xml.bind; public class JAXBContext {}",
                "package jakarta.xml.bind; public class JAXBContext {}",
                "package javax.json; public class Json {}",
                "package jakarta.json; public class Json {}",
                "package javax.json.bind; public interface Jsonb {}",
                "package jakarta.json.bind; public interface Jsonb {}",
                "package javax.activation; public interface DataSource {}",
                "package jakarta.activation; public interface DataSource {}",
                "package org.springframework.http.client; public class ReactorResourceFactory {}",
                "package org.springframework.http.client.reactive; " +
                "public class ReactorResourceFactory extends org.springframework.http.client.ReactorResourceFactory {}",
                "package org.springframework.web.util; public class ContentCachingResponseWrapper { " +
                "public int getStatusCode() { return 200; } public int getStatus() { return 200; } }",
                "package org.springframework.http; public class HttpHeaders {}",
                "package org.springframework.http; public enum HttpMethod { GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE; " +
                "public static HttpMethod resolve(String value) { return null; } }",
                "package org.springframework.http; public interface HttpRequest { " +
                "default HttpMethod getMethod() { return null; } default String getMethodValue() { return null; } " +
                "default java.net.URI getURI() { return null; } default HttpHeaders getHeaders() { return null; } }",
                "package org.springframework.http; public interface HttpStatusCode { int value(); }",
                "package org.springframework.web.server; public class ResponseStatusException extends RuntimeException { " +
                "public ResponseStatusException() {} public int getRawStatusCode() { return 500; } " +
                "public org.springframework.http.HttpStatusCode getStatusCode() { return null; } " +
                "public org.springframework.http.HttpStatusCode getStatus() { return null; } }",
                "package org.springframework.http.client; public interface ClientHttpResponse { int getRawStatusCode(); }",
                "package org.springframework.web.util; public class UriComponentsBuilder { " +
                "public static java.net.InetSocketAddress parseForwardedFor(org.springframework.http.HttpRequest request, java.net.InetSocketAddress remote) { return null; } " +
                "public static UriComponentsBuilder fromUriString(String value) { return null; } }",
                "package org.springframework.web.util; public class ForwardedHeaderUtils { " +
                "public static java.net.InetSocketAddress parseForwardedFor(java.net.URI uri, org.springframework.http.HttpHeaders headers, java.net.InetSocketAddress remote) { return null; } }",
                "package org.springframework.http.client.support; public class BasicAuthorizationInterceptor { " +
                "public BasicAuthorizationInterceptor(String username, String password) {} }",
                "package org.springframework.http.client.support; public class BasicAuthenticationInterceptor { " +
                "public BasicAuthenticationInterceptor(String username, String password) {} }",
                "package org.springframework.web.client; public class AsyncRestTemplate { public AsyncRestTemplate() {} }",
                "package org.springframework.http.client; public class HttpComponentsClientHttpRequestFactory { " +
                "public void setBufferRequestBody(boolean value) {} }",
                "package org.apache.http.client; public interface HttpClient {}",
                "package org.springframework.remoting.httpinvoker; public class HttpInvokerProxyFactoryBean {}",
                "package org.springframework.web.multipart.commons; public class CommonsMultipartResolver {}",
                "package org.springframework.web.util; public class DefaultUriTemplateHandler {}",
                "package org.springframework.web.cors; public class CorsConfiguration { " +
                "public void setAllowCredentials(Boolean value) {} public void setAllowedOrigins(java.util.List<String> values) {} }",
                "package org.springframework.http.converter.json; public abstract class AbstractJackson2HttpMessageConverter {}",
                "package org.springframework.web.client; public interface ResponseErrorHandler {}",
                "package org.springframework.web.client; public class DefaultResponseErrorHandler implements ResponseErrorHandler { " +
                "public void handleError() {} }",
                "package org.springframework.validation.annotation; public @interface Validated {}",
                "package org.springframework.web.bind.annotation; public @interface RequestParam { String defaultValue() default \"\"; }",
                "package org.springframework.web.bind.annotation; public @interface RequestHeader { String defaultValue() default \"\"; }",
                "package org.springframework.web.bind.annotation; public @interface CookieValue { String defaultValue() default \"\"; }",
                "package org.springframework.web.bind.annotation; public @interface ExceptionHandler { Class<?>[] value() default {}; String[] produces() default {}; }",
                "package org.springframework.web.method; public class ControllerAdviceBean { public ControllerAdviceBean(Object bean) {} }",
                "package org.springframework.web.client; public interface RestClient { " +
                "interface RequestHeadersSpec { ResponseSpec retrieve(); } interface ResponseSpec {} }",
                "package org.springframework.web.service.annotation; public @interface HttpExchange { String value() default \"\"; }",
                "package org.springframework.web.service.invoker; public class HttpServiceProxyFactory { " +
                "public <T> T createClient(Class<T> type) { return null; } }",
                "package io.micrometer.observation; public class ObservationRegistry {}",
                "package org.springframework.http.client; public class BufferingClientHttpRequestFactory {}",
                "package org.springframework.http.client.reactive; public class JettyClientHttpConnector {}"
        );
    }

    private static String pom(String version) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId>" +
               "<version>1</version><dependencies><dependency><groupId>org.springframework</groupId>" +
               "<artifactId>spring-web</artifactId><version>" + version +
               "</version></dependency></dependencies></project>";
    }
}
