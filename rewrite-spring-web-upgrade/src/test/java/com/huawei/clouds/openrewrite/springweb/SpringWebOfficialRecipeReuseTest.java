package com.huawei.clouds.openrewrite.springweb;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SpringWebOfficialRecipeReuseTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springweb.MigrateDeterministicSpringWeb6Java";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(AUTO)).parser(parser()).typeValidationOptions(TypeValidation.none());
    }

    @Test
    void runtimeCompositionUsesExactOfficialRecipes() {
        List<String> official = ApplyOfficialSpringWebMigrations.officialRecipes().stream()
                .map(recipe -> recipe.getClass().getName())
                .toList();
        assertEquals(List.of(
                "org.openrewrite.java.spring.framework.MigrateUtf8MediaTypes",
                "org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetRawStatusCodeMethod",
                "org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetStatusCodeMethod",
                "org.openrewrite.java.spring.framework.MigrateClientHttpResponseGetRawStatusCodeMethod"
        ), official);
        assertFalse(official.contains("org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_2"));
        assertFalse(official.contains("org.openrewrite.java.spring.framework.MigrateUriComponentsBuilderMethods"));

        List<String> aggregate = recipe(AUTO).getRecipeList().stream().map(Recipe::getName).toList();
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.springweb.MigrateSpringWebDeterministicJava",
                "com.huawei.clouds.openrewrite.springweb.MigrateRemovedSpringWebCalls",
                "com.huawei.clouds.openrewrite.springweb.ApplyOfficialSpringWebMigrations"
        ), aggregate);
    }

    @Test
    void officialRecipeMigratesDeprecatedUtf8MediaTypes() {
        rewriteRun(java(
                """
                import org.springframework.http.MediaType;
                class MediaTypes {
                    MediaType json() { return MediaType.APPLICATION_JSON_UTF8; }
                    String problem() { return MediaType.APPLICATION_PROBLEM_JSON_UTF8_VALUE; }
                }
                """,
                """
                import org.springframework.http.MediaType;
                class MediaTypes {
                    MediaType json() { return MediaType.APPLICATION_JSON; }
                    String problem() { return MediaType.APPLICATION_PROBLEM_JSON_VALUE; }
                }
                """));
    }

    @Test
    void officialRecipesMigrateSelectedResponseStatusCallsAndTypes() {
        rewriteRun(java(
                """
                import org.springframework.http.HttpStatus;
                import org.springframework.web.server.ResponseStatusException;
                class Statuses {
                    int raw(ResponseStatusException exception) {
                        return exception.getRawStatusCode();
                    }
                    HttpStatus status(ResponseStatusException exception) {
                        HttpStatus status = exception.getStatus();
                        return status;
                    }
                }
                """,
                source -> source.after(actual -> {
                    assertTrue(actual.contains("exception.getStatusCode().value()"), actual);
                    assertTrue(actual.contains("HttpStatusCode status = exception.getStatusCode()"), actual);
                    assertFalse(actual.contains("getRawStatusCode()"), actual);
                    return actual;
                })));
    }

    @Test
    void officialRawStatusRecipeAlsoCoversRestClientAndClientResponse() {
        rewriteRun(java(
                """
                import org.springframework.http.client.ClientHttpResponse;
                import org.springframework.web.client.HttpServerErrorException;
                import org.springframework.web.client.RestClientResponseException;
                class ClientStatuses {
                    int response(ClientHttpResponse response) {
                        return response.getRawStatusCode();
                    }
                    int rest(RestClientResponseException exception) {
                        return exception.getRawStatusCode();
                    }
                    int subclass(HttpServerErrorException exception) {
                        return exception.getRawStatusCode();
                    }
                }
                """,
                source -> source.after(actual -> {
                    assertEquals(3, occurrences(actual, ".getStatusCode().value()"));
                    assertFalse(actual.contains("getRawStatusCode()"), actual);
                    return actual;
                })));
    }

    @Test
    void narrowFallbackHandlesOfficialImplicitReceiverGap() {
        rewriteRun(java(
                """
                import org.springframework.http.HttpStatusCode;
                import org.springframework.http.client.ClientHttpResponse;
                import org.springframework.web.server.ResponseStatusException;
                class ServerStatus extends ResponseStatusException {
                    int raw() { return getRawStatusCode(); }
                    HttpStatusCode status() { return getStatus(); }
                }
                abstract class ClientStatus implements ClientHttpResponse {
                    int raw() { return getRawStatusCode(); }
                }
                """,
                source -> source.after(actual -> {
                    assertTrue(actual.contains("return getStatusCode().value();"), actual);
                    assertTrue(actual.contains("return getStatusCode();"), actual);
                    assertFalse(actual.contains("getRawStatusCode()"), actual);
                    assertFalse(actual.contains("getStatus();"), actual);
                    return actual;
                })));
    }

    @Test
    void officialReuseRetainsGeneratedExclusionAndIsIdempotent() {
        rewriteRun(java(
                """
                import org.springframework.http.MediaType;
                import org.springframework.web.server.ResponseStatusException;
                class Generated {
                    Object media() { return MediaType.APPLICATION_JSON_UTF8; }
                    int status(ResponseStatusException exception) { return exception.getRawStatusCode(); }
                }
                """,
                source -> source.path("target/generated-sources/Generated.java")));

        rewriteRun(spec -> spec.recipe(recipe(AUTO)).parser(parser())
                        .typeValidationOptions(TypeValidation.none())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import org.springframework.http.MediaType;
                        import org.springframework.http.client.ClientHttpResponse;
                        class Idempotent {
                            Object media() { return MediaType.APPLICATION_JSON_UTF8; }
                            int status(ClientHttpResponse response) { return response.getRawStatusCode(); }
                        }
                        """,
                        """
                        import org.springframework.http.MediaType;
                        import org.springframework.http.client.ClientHttpResponse;
                        class Idempotent {
                            Object media() { return MediaType.APPLICATION_JSON; }
                            int status(ClientHttpResponse response) { return response.getStatusCode().value(); }
                        }
                        """));
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package org.springframework.http; public interface HttpStatusCode { int value(); }",
                "package org.springframework.http; public enum HttpStatus implements HttpStatusCode { " +
                "INTERNAL_SERVER_ERROR; public int value() { return 500; } }",
                "package org.springframework.http; public class MediaType { " +
                "public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(); " +
                "public static final MediaType APPLICATION_JSON = new MediaType(); " +
                "public static final String APPLICATION_JSON_UTF8_VALUE = \"application/json;charset=UTF-8\"; " +
                "public static final String APPLICATION_JSON_VALUE = \"application/json\"; " +
                "public static final MediaType APPLICATION_PROBLEM_JSON_UTF8 = new MediaType(); " +
                "public static final MediaType APPLICATION_PROBLEM_JSON = new MediaType(); " +
                "public static final String APPLICATION_PROBLEM_JSON_UTF8_VALUE = \"application/problem+json;charset=UTF-8\"; " +
                "public static final String APPLICATION_PROBLEM_JSON_VALUE = \"application/problem+json\"; }",
                "package org.springframework.web.server; public class ResponseStatusException extends RuntimeException { " +
                "public int getRawStatusCode() { return 500; } " +
                "public org.springframework.http.HttpStatus getStatus() { return null; } " +
                "public org.springframework.http.HttpStatusCode getStatusCode() { return null; } }",
                "package org.springframework.http.client; public interface ClientHttpResponse { " +
                "int getRawStatusCode(); default org.springframework.http.HttpStatusCode getStatusCode() { return null; } }",
                "package org.springframework.web.client; public class RestClientResponseException extends RuntimeException { " +
                "public int getRawStatusCode() { return 500; } " +
                "public org.springframework.http.HttpStatusCode getStatusCode() { return null; } }",
                "package org.springframework.web.client; public class HttpServerErrorException extends RestClientResponseException {}"
        );
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int offset = 0; (offset = text.indexOf(needle, offset)) >= 0; offset += needle.length()) {
            count++;
        }
        return count;
    }
}
