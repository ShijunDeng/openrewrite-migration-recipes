package com.huawei.clouds.openrewrite.springwebflux;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class SpringWebFluxOfficialMigrationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springwebflux.MigrateDeterministicSpringWebFlux6Java";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springwebflux",
                                              "org.openrewrite.java.spring.framework")
                        .build()
                        .activateRecipes(AUTO))
                .parser(spring61Parser())
                .afterTypeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesHandlerResultExceptionHandlerApis() {
        rewriteRun(java(
                """
                  import org.springframework.web.reactive.HandlerResult;
                  import reactor.core.publisher.Mono;

                  import java.util.function.Function;

                  class ResultAdapter {
                      void configure(HandlerResult result,
                                     Function<Throwable, Mono<HandlerResult>> handler) {
                          boolean configured = result.hasExceptionHandler();
                          result.setExceptionHandler(handler);
                      }
                  }
                  """,
                """
                  import org.springframework.web.reactive.HandlerResult;
                  import reactor.core.publisher.Mono;

                  import java.util.function.Function;

                  class ResultAdapter {
                      void configure(HandlerResult result,
                                     Function<Throwable, Mono<HandlerResult>> handler) {
                          boolean configured = result.getExceptionHandler() != null;
                          result.setExceptionHandler((exchange, ex) -> handler.apply(ex));
                      }
                  }
                  """));
    }

    @Test
    void migratesResourceWriterToReactiveDefaultHeadersApi() {
        rewriteRun(java(
                """
                  import org.springframework.core.io.Resource;
                  import org.springframework.http.MediaType;
                  import org.springframework.http.ReactiveHttpOutputMessage;
                  import org.springframework.http.codec.ResourceHttpMessageWriter;

                  import java.util.Map;

                  class Resources {
                      void headers(ResourceHttpMessageWriter writer, ReactiveHttpOutputMessage message,
                                   Resource resource, MediaType contentType, Map<String, Object> hints) {
                          writer.addHeaders(message, resource, contentType, hints);
                      }
                  }
                  """,
                """
                  import org.springframework.core.io.Resource;
                  import org.springframework.http.MediaType;
                  import org.springframework.http.ReactiveHttpOutputMessage;
                  import org.springframework.http.codec.ResourceHttpMessageWriter;

                  import java.util.Map;

                  class Resources {
                      void headers(ResourceHttpMessageWriter writer, ReactiveHttpOutputMessage message,
                                   Resource resource, MediaType contentType, Map<String, Object> hints) {
                          writer.addDefaultHeaders(message, resource, contentType, hints).block();
                      }
                  }
                  """));
    }

    @Test
    void migratesWebExchangeBindingErrorResolution() {
        rewriteRun(java(
                """
                  import org.springframework.context.MessageSource;
                  import org.springframework.validation.ObjectError;
                  import org.springframework.web.bind.support.WebExchangeBindException;

                  import java.util.Locale;
                  import java.util.Map;

                  class ValidationErrors {
                      Map<ObjectError, String> resolve(WebExchangeBindException exception,
                                                       MessageSource messages, Locale locale) {
                          return exception.resolveErrorMessages(messages, locale);
                      }
                  }
                  """,
                """
                  import org.springframework.context.MessageSource;
                  import org.springframework.validation.ObjectError;
                  import org.springframework.web.bind.support.WebExchangeBindException;
                  import org.springframework.web.util.BindErrorUtils;

                  import java.util.Locale;
                  import java.util.Map;

                  class ValidationErrors {
                      Map<ObjectError, String> resolve(WebExchangeBindException exception,
                                                       MessageSource messages, Locale locale) {
                          return BindErrorUtils.resolve(exception.getAllErrors(), messages, locale);
                      }
                  }
                  """));
    }

    @Test
    void migratesReactiveObservationConstantAndWebClientAdapterFactory() {
        rewriteRun(java(
                """
                  import org.springframework.web.reactive.function.client.ClientHttpObservationDocumentation;
                  import org.springframework.web.reactive.function.client.WebClient;
                  import org.springframework.web.reactive.function.client.support.WebClientAdapter;

                  class ClientConfiguration {
                      Object key = ClientHttpObservationDocumentation.HighCardinalityKeyNames.CLIENT_NAME;
                      WebClientAdapter adapter(WebClient client) {
                          return WebClientAdapter.forClient(client);
                      }
                  }
                """,
                """
                  import org.springframework.web.reactive.function.client.ClientHttpObservationDocumentation.LowCardinalityKeyNames;
                  import org.springframework.web.reactive.function.client.WebClient;
                  import org.springframework.web.reactive.function.client.support.WebClientAdapter;

                  class ClientConfiguration {
                      Object key = LowCardinalityKeyNames.CLIENT_NAME;
                      WebClientAdapter adapter(WebClient client) {
                          return WebClientAdapter.create(client);
                      }
                  }
                  """));
    }

    @Test
    void migratesPinnedPublicRepositoryPatternsAgainstSpring61Types() {
        rewriteRun(java(
                """
                  import org.springframework.http.client.reactive.ReactorResourceFactory;
                  import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
                  import reactor.util.context.Context;

                  class ReactiveInfrastructure {
                      ReactorResourceFactory resources() {
                          ReactorResourceFactory factory = new ReactorResourceFactory();
                          factory.setUseGlobalResources(false);
                          return factory;
                      }
                      Object exchange(Context context) {
                          return ServerWebExchangeContextFilter.get(context);
                      }
                  }
                  """,
                """
                  import org.springframework.http.client.ReactorResourceFactory;
                  import org.springframework.web.filter.reactive.ServerWebExchangeContextFilter;
                  import reactor.util.context.Context;

                  class ReactiveInfrastructure {
                      ReactorResourceFactory resources() {
                          ReactorResourceFactory factory = new ReactorResourceFactory();
                          factory.setUseGlobalResources(false);
                          return factory;
                      }
                      Object exchange(Context context) {
                          return ServerWebExchangeContextFilter.getExchange(context);
                      }
                  }
                  """));
    }

    @Test
    void migratesResponseStatusExceptionAccessorsFromSpring5Types() {
        rewriteRun(spec -> spec.parser(responseStatusParser()), java(
                """
                  import org.springframework.http.HttpStatus;
                  import org.springframework.web.server.ResponseStatusException;

                  class ErrorStatus {
                      void inspect(ResponseStatusException exception) {
                          int raw = exception.getRawStatusCode();
                          HttpStatus status = exception.getStatus();
                      }
                  }
                  """,
                """
                  import org.springframework.http.HttpStatusCode;
                  import org.springframework.web.server.ResponseStatusException;

                  class ErrorStatus {
                      void inspect(ResponseStatusException exception) {
                          int raw = exception.getStatusCode().value();
                          HttpStatusCode status = exception.getStatusCode();
                      }
                  }
                  """));
    }

    @Test
    void generatedSourceIsNoopForEveryOfficialComponent() {
        rewriteRun(java(
                """
                  import org.springframework.web.reactive.HandlerResult;
                  class GeneratedHandler {
                      boolean configured(HandlerResult result) {
                          return result.hasExceptionHandler();
                      }
                  }
                  """,
                source -> source.path("build/generated/GeneratedHandler.java")));
    }

    @Test
    void officialCompositionIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                  import org.springframework.web.reactive.function.client.WebClient;
                  import org.springframework.web.reactive.function.client.support.WebClientAdapter;
                  class ClientConfiguration {
                      WebClientAdapter adapter(WebClient client) {
                          return WebClientAdapter.forClient(client);
                      }
                  }
                  """,
                """
                  import org.springframework.web.reactive.function.client.WebClient;
                  import org.springframework.web.reactive.function.client.support.WebClientAdapter;
                  class ClientConfiguration {
                      WebClientAdapter adapter(WebClient client) {
                          return WebClientAdapter.create(client);
                      }
                  }
                  """));
    }

    private static JavaParser.Builder<?, ?> spring61Parser() {
        return JavaParser.fromJavaVersion().classpath(
                "reactor-core", "reactive-streams", "spring-webflux", "spring-web",
                "spring-context", "spring-core", "spring-beans",
                "micrometer-observation", "micrometer-commons");
    }

    private static JavaParser.Builder<?, ?> responseStatusParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                  package org.springframework.web.server;
                  import org.springframework.http.HttpStatus;
                  public class ResponseStatusException extends RuntimeException {
                      public int getRawStatusCode() { return 500; }
                      public HttpStatus getStatus() { return HttpStatus.INTERNAL_SERVER_ERROR; }
                  }
                  """,
                """
                  package org.springframework.http;
                  public enum HttpStatus { INTERNAL_SERVER_ERROR }
                  """);
    }
}
