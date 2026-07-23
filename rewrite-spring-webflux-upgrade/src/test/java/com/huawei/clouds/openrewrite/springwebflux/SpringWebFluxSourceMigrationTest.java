package com.huawei.clouds.openrewrite.springwebflux;

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

class SpringWebFluxSourceMigrationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springwebflux.MigrateDeterministicSpringWebFlux6Java";
    private static final String RISKS =
            "com.huawei.clouds.openrewrite.springwebflux.FindSpringWebFlux6SourceAndConfigurationRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(AUTO)).parser(parser()).typeValidationOptions(TypeValidation.none());
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
    void migratesReducedCtripXPipeWebClientFactoryFixture() {
        // ctripcorp/x-pipe@58996e595df0, core/.../WebClientFactory.java.
        rewriteRun(java(
                """
                package com.ctrip.xpipe.spring;
                import org.springframework.http.client.reactive.ReactorResourceFactory;
                public class WebClientFactory {
                    public static ReactorResourceFactory makeResources() {
                        ReactorResourceFactory resourceFactory = new ReactorResourceFactory();
                        resourceFactory.setUseGlobalResources(false);
                        return resourceFactory;
                    }
                }
                """,
                """
                package com.ctrip.xpipe.spring;

                import org.springframework.http.client.ReactorResourceFactory;

                public class WebClientFactory {
                    public static ReactorResourceFactory makeResources() {
                        ReactorResourceFactory resourceFactory = new ReactorResourceFactory();
                        resourceFactory.setUseGlobalResources(false);
                        return resourceFactory;
                    }
                }
                """));
    }

    @Test
    void renamesExactServerExchangeContextAlias() {
        rewriteRun(java(
                """
                import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
                import reactor.util.context.Context;
                class ExchangeLookup {
                    Object exchange(Context context) {
                        return ServerWebExchangeContextFilter.get(context);
                    }
                }
                """,
                """
                import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
                import reactor.util.context.Context;
                class ExchangeLookup {
                    Object exchange(Context context) {
                        return ServerWebExchangeContextFilter.getExchange(context);
                    }
                }
                """));
    }

    @Test
    void migratesReducedHandleInterceptorAuthenticationAspectFixture() {
        // ranarula/handleInterceptor@bbcc400cf4a1, AuthenticationAspect.java.
        rewriteRun(java(
                """
                package com.example.demo.aop;
                import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
                import reactor.util.context.Context;
                public class AuthenticationAspect {
                    Object exchange(Context context) {
                        return ServerWebExchangeContextFilter.get(context);
                    }
                }
                """,
                """
                package com.example.demo.aop;
                import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
                import reactor.util.context.Context;
                public class AuthenticationAspect {
                    Object exchange(Context context) {
                        return ServerWebExchangeContextFilter.getExchange(context);
                    }
                }
                """));
    }

    @Test
    void preservesSameNamedBusinessTypesAndOverloads() {
        rewriteRun(java("""
                class ReactorResourceFactory {}
                class Context {}
                class ServerWebExchangeContextFilter {
                    static Object get(Context context) { return null; }
                    static Object get(String key) { return null; }
                }
                class Business {
                    ReactorResourceFactory factory = new ReactorResourceFactory();
                    Object lookup(Context context) {
                        ServerWebExchangeContextFilter.get("key");
                        return ServerWebExchangeContextFilter.get(context);
                    }
                }
                """));
    }

    @Test
    void skipsGeneratedJavaAndIsIdempotent() {
        rewriteRun(java(
                "import org.springframework.http.client.reactive.ReactorResourceFactory; class Generated { ReactorResourceFactory f; }",
                source -> source.path("build/generated/Generated.java")));
        rewriteRun(spec -> spec.recipe(recipe(AUTO)).parser(parser())
                        .typeValidationOptions(TypeValidation.none())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        "import org.springframework.http.client.reactive.ReactorResourceFactory; class Config { ReactorResourceFactory f; }",
                        "import org.springframework.http.client.ReactorResourceFactory;\n\nclass Config { ReactorResourceFactory f; }"));
    }

    @Test
    void marksHttpMethodSwitchAndEnumSetButNotBusinessEnums() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                java("""
                        import java.util.EnumSet;
                        import org.springframework.http.HttpMethod;
                        class Routing {
                            int route(HttpMethod method) {
                                EnumSet<HttpMethod> supported = EnumSet.of(HttpMethod.GET, HttpMethod.POST);
                                return switch (method) {
                                    case GET -> 1;
                                    default -> 0;
                                };
                            }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.HTTP_METHOD_CHANGE));
                    return actual;
                })),
                java("""
                        import java.util.EnumSet;
                        enum HttpMethod { GET }
                        class Business {
                            int route(HttpMethod method) {
                                EnumSet<HttpMethod> values = EnumSet.of(HttpMethod.GET);
                                return switch (method) { case GET -> 1; };
                            }
                        }
                        """));
    }

    @Test
    void marksRequestMappingOnlyControllerButNotAnnotatedControllers() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.stereotype.Controller;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;
                        @RequestMapping("/legacy") class Legacy {}
                        @Controller @RequestMapping("/mvc") class Mvc {}
                        @RestController @RequestMapping("/reactive") class Reactive {}
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.CONTROLLER));
                    assertTrue(actual.indexOf(FindSpringWebFluxSourceRisks.CONTROLLER) <
                               actual.indexOf("class Legacy"));
                    return actual;
                })));
    }

    @Test
    void marksFluxControllerResponseSemantics() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.web.bind.annotation.GetMapping;
                        import reactor.core.publisher.Flux;
                        class Events {
                            @GetMapping(produces = "application/json")
                            Flux<String> values() { return Flux.just("a", "b"); }
                            Flux<String> internal() { return Flux.just("x"); }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.FLUX_SEMANTICS));
                    assertFalse(actual.substring(actual.indexOf("Flux<String> internal"))
                            .contains(FindSpringWebFluxSourceRisks.FLUX_SEMANTICS));
                    return actual;
                })));
    }

    @Test
    void doesNotMarkExplicitStreamingFluxHandler() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.web.bind.annotation.GetMapping;
                        import reactor.core.publisher.Flux;
                        class Events {
                            @GetMapping(produces = "text/event-stream")
                            Flux<String> stream() { return Flux.just("a", "b"); }
                        }
                        """));
    }

    @Test
    void marksValidatedControllerDefaultsAndExceptionNegotiation() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.validation.annotation.Validated;
                        import org.springframework.web.bind.annotation.ExceptionHandler;
                        import org.springframework.web.bind.annotation.RequestHeader;
                        import org.springframework.web.bind.annotation.RequestParam;
                        import org.springframework.web.bind.annotation.RestController;
                        @RestController @Validated
                        class Api {
                            void search(@RequestParam(defaultValue = "all") String query,
                                        @RequestHeader(defaultValue = "x") String header) {}
                            @ExceptionHandler(IllegalArgumentException.class)
                            String error() { return "bad"; }
                        }
                        @Validated class Service {}
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.VALIDATION));
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.DEFAULT_VALUE));
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.EXCEPTION_NEGOTIATION));
                    return actual;
                })));
    }

    @Test
    void marksDispatcherObservationAndHttpInterfaceApis() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                java("""
                        import io.micrometer.observation.ObservationRegistry;
                        import org.springframework.web.filter.reactive.ServerHttpObservationFilter;
                        import org.springframework.web.reactive.DispatcherHandler;
                        import org.springframework.web.service.annotation.HttpExchange;
                        import org.springframework.web.service.invoker.HttpServiceProxyFactory;
                        @HttpExchange("/inventory") interface InventoryClient {}
                        class Configuration {
                            void configure(DispatcherHandler dispatcher, ObservationRegistry registry,
                                           HttpServiceProxyFactory factory) {
                                dispatcher.setThrowExceptionIfNoHandlerFound(false);
                                new ServerHttpObservationFilter(registry);
                                factory.createClient(InventoryClient.class);
                            }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.NOT_FOUND));
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.OBSERVABILITY));
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.HTTP_INTERFACE));
                    return actual;
                })));
    }

    @Test
    void marksEmptyMonoOnlyInsideMappedHandler() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.web.bind.annotation.GetMapping;
                        import reactor.core.publisher.Mono;
                        class EmptyResponses {
                            @GetMapping("/empty") Mono<Void> empty() { return Mono.empty(); }
                            Mono<Void> internal() { return Mono.empty(); }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.EMPTY_MONO));
                    String internal = actual.substring(actual.indexOf("Mono<Void> internal"));
                    assertFalse(internal.contains(FindSpringWebFluxSourceRisks.EMPTY_MONO));
                    return actual;
                })));
    }

    @Test
    void marksStaticResourceAndWebJarsApis() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                java("""
                        import org.springframework.web.reactive.config.ResourceHandlerRegistration;
                        import org.springframework.web.reactive.resource.WebJarsResourceResolver;
                        class Resources {
                            void configure(ResourceHandlerRegistration registration) {
                                registration.addResourceLocations("classpath:/static");
                                new WebJarsResourceResolver();
                            }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.RESOURCE));
                    return actual;
                })));
    }

    @Test
    void marksKotlinBodySpecAndCoroutineImportsInWebFluxFile() {
        rewriteRun(spec -> spec.recipe(recipe(RISKS))
                        .parser(KotlinParser.builder().dependsOn(
                                "package org.springframework.web.reactive.function.server\nclass CoWebFilter",
                                "package org.springframework.test.web.reactive.server\ninterface KotlinBodySpec<T>",
                                "package kotlinx.coroutines.reactor\nsuspend fun awaitSingle(): Any = Any()"))
                        .typeValidationOptions(TypeValidation.none()),
                kotlin("""
                        import kotlinx.coroutines.reactor.awaitSingle
                        import org.springframework.test.web.reactive.server.KotlinBodySpec
                        import org.springframework.web.reactive.function.server.CoWebFilter
                        class ReactiveTest {
                            lateinit var body: KotlinBodySpec<String>
                            lateinit var filter: CoWebFilter
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.KOTLIN_BODY));
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.COROUTINES));
                    return actual;
                })));
    }

    @Test
    void sourceMarkersIgnoreSameNamedTypesAndGeneratedFiles() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                java("""
                        class DispatcherHandler { void setThrowExceptionIfNoHandlerFound(boolean value) {} }
                        class ServerHttpObservationFilter { ServerHttpObservationFilter(Object registry) {} }
                        class ResourceHandlerRegistration { void addResourceLocations(String value) {} }
                        class Business {
                            void use(DispatcherHandler d, ResourceHandlerRegistration r) {
                                d.setThrowExceptionIfNoHandlerFound(false);
                                r.addResourceLocations("business");
                                new ServerHttpObservationFilter(new Object());
                            }
                        }
                        """),
                java("""
                        import org.springframework.web.reactive.resource.WebJarsResourceResolver;
                        class Generated { WebJarsResourceResolver value; }
                        """, source -> source.path("target/generated-sources/Generated.java")));
    }

    @Test
    void preservesActiveSpringBootJsonControllerFixtureWithoutFalseMarkers() {
        // spring-projects/spring-boot@533df5e2f4de, spring-boot-webflux-test JsonController.java.
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                java("""
                        package org.springframework.boot.webflux.test.autoconfigure;
                        import reactor.core.publisher.Mono;
                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RestController;
                        @RestController
                        public class JsonController {
                            @GetMapping(value = "/json", produces = "application/json")
                            public Mono<ExamplePojo> json() {
                                return Mono.just(new ExamplePojo("a", "b"));
                            }
                        }
                        record ExamplePojo(String first, String second) {}
                        """));
    }

    @ParameterizedTest(name = "marks exact WebFlux configuration key {0}")
    @ValueSource(strings = {
            "spring.webflux.base-path",
            "spring.webflux.static-path-pattern",
            "spring.webflux.problemdetails.enabled",
            "spring.web.resources.add-mappings",
            "spring.web.resources.chain.enabled",
            "spring.codec.max-in-memory-size",
            "spring.codec.log-request-details",
            "server.http2.enabled",
            "management.observations.http.server.requests.name",
            "spring.web.resources.static-locations",
            "spring.web.resources.chain.strategy.content.enabled",
            "spring.webflux.multipart.max-in-memory-size",
            "server.netty.connection-timeout"
    })
    void marksEveryExactConfigurationBoundary(String key) {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                properties(key + "=value\n", source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxConfigurationRisks.CONFIG));
                    return actual;
                })));
    }

    @ParameterizedTest(name = "ignores configuration lookalike {0}")
    @ValueSource(strings = {
            "app.spring.webflux.base-path",
            "springish.webflux.base-path",
            "spring.webflux.base-path-extra",
            "company.server.netty.connection-timeout",
            "spring.codec.custom",
            "management.observations.http.client.requests.name"
    })
    void ignoresConfigurationLookalikes(String key) {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec, properties(key + "=value\n"));
    }

    @Test
    void marksExactPropertiesNestedYamlAndXmlBeanTypes() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                properties("""
                        spring.webflux.base-path=/api
                        spring.codec.max-in-memory-size=2MB
                        normal.key=true
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxConfigurationRisks.CONFIG));
                    return actual;
                })),
                yaml("""
                        spring:
                          web:
                            resources:
                              static-locations: classpath:/public
                        server:
                          netty:
                            connection-timeout: 5s
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxConfigurationRisks.CONFIG));
                    return actual;
                })),
                xml("""
                        <beans>
                          <bean class="org.springframework.http.client.reactive.ReactorResourceFactory"/>
                          <bean class="org.springframework.web.filter.reactive.ServerHttpObservationFilter"/>
                        </beans>
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxConfigurationRisks.XML));
                    return actual;
                })));
    }

    @Test
    void configurationMarkersIgnoreLookalikesPomTextAndGeneratedFiles() {
        rewriteRun(SpringWebFluxSourceMigrationTest::riskSpec,
                properties("app.spring.webflux.base-path=/safe\n"),
                yaml("springish:\n  webflux:\n    base-path: /safe\n"),
                xml("<project><properties><spring.webflux.base-path>/api</spring.webflux.base-path></properties></project>",
                        source -> source.path("pom.xml")),
                xml("<bean class=\"company.org.springframework.http.client.reactive.ReactorResourceFactory\"/>"),
                properties("spring.webflux.base-path=/api\n",
                        source -> source.path("target/classes/application.properties")));
    }

    @Test
    void riskMarkersAreIdempotent() {
        rewriteRun(spec -> {
                    riskSpec(spec);
                    spec.cycles(2).expectedCyclesThatMakeChanges(1);
                },
                properties("spring.webflux.base-path=/api\n",
                        "~~(" + FindSpringWebFluxConfigurationRisks.CONFIG + ")~~>spring.webflux.base-path=/api\n"));
    }

    @Test
    void recommendedAggregateRunsUpgradeAutoAndMarkPhases() {
        rewriteRun(spec -> spec.recipe(recipe(
                                "com.huawei.clouds.openrewrite.springwebflux.MigrateSpringWebFluxTo6_2_19"))
                        .parser(parser()).typeValidationOptions(TypeValidation.none()),
                pomXml(pom("6.1.14"), pom("6.2.19")),
                java("""
                        import org.springframework.http.client.reactive.ReactorResourceFactory;
                        import org.springframework.web.bind.annotation.GetMapping;
                        import reactor.core.publisher.Mono;
                        class Api {
                            ReactorResourceFactory resources = new ReactorResourceFactory();
                            @GetMapping Mono<Void> empty() { return Mono.empty(); }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains("import org.springframework.http.client.ReactorResourceFactory;"));
                    assertTrue(actual.contains(FindSpringWebFluxSourceRisks.EMPTY_MONO));
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
                "package org.springframework.http.client.reactive; public class ReactorResourceFactory { public void setUseGlobalResources(boolean value) {} }",
                "package org.springframework.http.client; public class ReactorResourceFactory { public void setUseGlobalResources(boolean value) {} }",
                "package reactor.util.context; public interface ContextView {}",
                "package reactor.util.context; public interface Context extends ContextView {}",
                "package org.springframework.web.filter.reactive; public class ServerWebExchangeContextFilter { " +
                "public static Object get(reactor.util.context.Context context) { return null; } " +
                "public static Object getExchange(reactor.util.context.ContextView context) { return null; } }",
                "package org.springframework.http; public enum HttpMethod { GET, POST, PATCH }",
                "package org.springframework.stereotype; public @interface Controller {}",
                "package org.springframework.web.bind.annotation; public @interface RestController {}",
                "package org.springframework.web.bind.annotation; public @interface RequestMapping { String[] value() default {}; String[] produces() default {}; }",
                "package org.springframework.web.bind.annotation; public @interface GetMapping { String[] value() default {}; String[] produces() default {}; }",
                "package org.springframework.web.bind.annotation; public @interface PostMapping { String[] value() default {}; String[] produces() default {}; }",
                "package org.springframework.web.bind.annotation; public @interface PutMapping { String[] value() default {}; String[] produces() default {}; }",
                "package org.springframework.web.bind.annotation; public @interface PatchMapping { String[] value() default {}; String[] produces() default {}; }",
                "package org.springframework.web.bind.annotation; public @interface DeleteMapping { String[] value() default {}; String[] produces() default {}; }",
                "package org.springframework.web.bind.annotation; public @interface RequestParam { String defaultValue() default \"\"; }",
                "package org.springframework.web.bind.annotation; public @interface RequestHeader { String defaultValue() default \"\"; }",
                "package org.springframework.web.bind.annotation; public @interface CookieValue { String defaultValue() default \"\"; }",
                "package org.springframework.web.bind.annotation; public @interface ExceptionHandler { Class<?>[] value() default {}; String[] produces() default {}; }",
                "package org.springframework.validation.annotation; public @interface Validated {}",
                "package reactor.core.publisher; public class Flux<T> { @SafeVarargs public static <T> Flux<T> just(T... values) { return new Flux<>(); } }",
                "package reactor.core.publisher; public class Mono<T> { public static <T> Mono<T> empty() { return new Mono<>(); } public static <T> Mono<T> just(T value) { return new Mono<>(); } }",
                "package org.springframework.web.reactive; public class DispatcherHandler { public void setThrowExceptionIfNoHandlerFound(boolean value) {} }",
                "package org.springframework.web.reactive.config; public class ResourceHandlerRegistration { public ResourceHandlerRegistration addResourceLocations(String... values) { return this; } }",
                "package org.springframework.web.reactive.resource; public class WebJarsResourceResolver {}",
                "package io.micrometer.observation; public class ObservationRegistry {}",
                "package org.springframework.web.filter.reactive; public class ServerHttpObservationFilter { public ServerHttpObservationFilter(io.micrometer.observation.ObservationRegistry registry) {} }",
                "package org.springframework.web.service.annotation; public @interface HttpExchange { String value() default \"\"; }",
                "package org.springframework.web.service.invoker; public abstract class AbstractReactorHttpExchangeAdapter { public void setBlockTimeout(java.time.Duration timeout) {} }",
                "package org.springframework.web.service.invoker; public class HttpServiceProxyFactory { public <T> T createClient(Class<T> type) { return null; } }"
        );
    }

    private static String pom(String version) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId>" +
               "<version>1</version><dependencies><dependency><groupId>org.springframework</groupId>" +
               "<artifactId>spring-webflux</artifactId><version>" + version +
               "</version></dependency></dependencies></project>";
    }
}
