package com.huawei.clouds.openrewrite.springwebmvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class SpringWebMvcOfficialMigrationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springwebmvc.MigrateDeterministicSpringWebMvc6Java";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springwebmvc",
                                              "org.openrewrite.java.spring.framework")
                        .build()
                        .activateRecipes(AUTO))
                .parser(JavaParser.fromJavaVersion().classpathFromResources(
                        new InMemoryExecutionContext(),
                        "spring-webmvc-5", "spring-web-5", "spring-context-5",
                        "spring-core-5", "spring-beans-5"));
    }

    @Test
    void officialWebMvcConfigurerRecipeRemovesNoopSuperConstructor() {
        rewriteRun(java(
                """
                  import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

                  class WebConfig extends WebMvcConfigurerAdapter {
                      WebConfig() {
                          super();
                      }
                  }
                  """,
                """
                  import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                  class WebConfig implements WebMvcConfigurer {
                      WebConfig() {
                      }
                  }
                  """));
    }

    @Test
    void officialResponseEntityExceptionHandlerRecipeMigratesOverrideStatusType() {
        rewriteRun(java(
                """
                  import org.springframework.http.HttpHeaders;
                  import org.springframework.http.HttpStatus;
                  import org.springframework.http.ResponseEntity;
                  import org.springframework.web.context.request.WebRequest;
                  import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

                  class Errors extends ResponseEntityExceptionHandler {
                      @Override
                      protected ResponseEntity<Object> handleExceptionInternal(
                              Exception ex, Object body, HttpHeaders headers,
                              HttpStatus status, WebRequest request) {
                          return super.handleExceptionInternal(ex, body, headers, status, request);
                      }
                  }
                  """,
                """
                  import org.springframework.http.HttpHeaders;
                  import org.springframework.http.HttpStatusCode;
                  import org.springframework.http.ResponseEntity;
                  import org.springframework.web.context.request.WebRequest;
                  import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

                  class Errors extends ResponseEntityExceptionHandler {
                      @Override
                      protected ResponseEntity<Object> handleExceptionInternal(
                              Exception ex, Object body, HttpHeaders headers,
                              HttpStatusCode status, WebRequest request) {
                          return super.handleExceptionInternal(ex, body, headers, status, request);
                      }
                  }
                  """));
    }

    @Test
    void officialResponseStatusExceptionRecipeMigratesBothRemovedAccessors() {
        rewriteRun(java(
                """
                  import org.springframework.http.HttpStatus;
                  import org.springframework.web.server.ResponseStatusException;

                  class Errors {
                      void inspect(ResponseStatusException exception) {
                          int raw = exception.getRawStatusCode();
                          HttpStatus status = exception.getStatus();
                      }
                  }
                  """,
                """
                  import org.springframework.http.HttpStatusCode;
                  import org.springframework.web.server.ResponseStatusException;

                  class Errors {
                      void inspect(ResponseStatusException exception) {
                          int raw = exception.getStatusCode().value();
                          HttpStatusCode status = exception.getStatusCode();
                      }
                  }
                  """));
    }

    @Test
    void officialMediaTypeRecipeMigratesRemovedUtf8Constants() {
        rewriteRun(java(
                """
                  import org.springframework.http.MediaType;
                  class Media {
                      MediaType type = MediaType.APPLICATION_JSON_UTF8;
                      String value = MediaType.APPLICATION_PROBLEM_JSON_UTF8_VALUE;
                  }
                  """,
                """
                  import org.springframework.http.MediaType;
                  class Media {
                      MediaType type = MediaType.APPLICATION_JSON;
                      String value = MediaType.APPLICATION_PROBLEM_JSON_VALUE;
                  }
                  """));
    }

    @Test
    void officialResponseEntityReturnTypeRecipeMigratesOnlyTheSafeDirectVariable() {
        rewriteRun(java(
                """
                  import org.springframework.http.HttpStatus;
                  import org.springframework.http.ResponseEntity;
                  class Status {
                      HttpStatus status(ResponseEntity<String> response) {
                          HttpStatus status = response.getStatusCode();
                          return status;
                      }
                  }
                """,
                """
                  import org.springframework.http.HttpStatus;
                  import org.springframework.http.HttpStatusCode;
                  import org.springframework.http.ResponseEntity;

                  class Status {
                      HttpStatus status(ResponseEntity<String> response) {
                          HttpStatusCode status = response.getStatusCode();
                          return status;
                      }
                  }
                  """));
    }

    @Test
    void generatedSourceIsNoopForEveryOfficialComponent() {
        rewriteRun(java(
                """
                  import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
                  class GeneratedConfig extends WebMvcConfigurerAdapter { }
                  """,
                source -> source.path("build/generated/GeneratedConfig.java")));
    }
}
