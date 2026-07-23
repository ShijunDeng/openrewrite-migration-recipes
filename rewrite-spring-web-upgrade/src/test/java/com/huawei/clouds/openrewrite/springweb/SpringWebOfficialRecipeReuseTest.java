package com.huawei.clouds.openrewrite.springweb;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.ReplaceConstantWithAnotherConstant;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SpringWebOfficialRecipeReuseTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springweb.MigrateDeterministicSpringWeb6Java";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springweb",
                                  "org.openrewrite.java.spring.framework")
            .build();

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(ENVIRONMENT.activateRecipes(AUTO))
                .parser(legacyParser())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void runtimeCompositionUsesExactCoreAndOfficialLeaves() {
        DeclarativeRecipe recipe = assertInstanceOf(
                DeclarativeRecipe.class, ENVIRONMENT.activateRecipes(AUTO));
        List<Recipe> composition = composition(recipe);

        assertEquals(List.of(
                        "org.openrewrite.java.ChangePackage",
                        "org.openrewrite.java.ChangePackage",
                        "org.openrewrite.java.ChangePackage",
                        "org.openrewrite.java.ChangePackage",
                        "org.openrewrite.java.ChangePackage",
                        "org.openrewrite.java.ChangeType",
                        "org.openrewrite.java.ChangeMethodName",
                        "com.huawei.clouds.openrewrite.springweb.MigrateRemovedSpringWebCalls",
                        "org.openrewrite.java.spring.framework.MigrateUtf8MediaTypes",
                        "org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetRawStatusCodeMethod",
                        "org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetStatusCodeMethod",
                        "org.openrewrite.java.spring.framework.MigrateClientHttpResponseGetRawStatusCodeMethod",
                        "org.openrewrite.java.spring.framework.MigrateMethodArgumentNotValidExceptionErrorMethod",
                        "org.openrewrite.java.ReplaceConstantWithAnotherConstant"),
                composition.stream().map(Recipe::getName).toList());
        assertEquals(List.of(IsSpringWebNonGeneratedSource.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());

        List<ChangePackage> packageMoves = composition.subList(0, 5).stream()
                .map(ChangePackage.class::cast)
                .toList();
        assertEquals(List.of(
                        "javax.servlet->jakarta.servlet:true",
                        "javax.validation->jakarta.validation:true",
                        "javax.xml.bind->jakarta.xml.bind:true",
                        "javax.json->jakarta.json:true",
                        "javax.activation->jakarta.activation:true"),
                packageMoves.stream()
                        .map(change -> change.getOldPackageName() + "->" +
                                       change.getNewPackageName() + ":" + change.getRecursive())
                        .toList());

        ChangeType resources = assertInstanceOf(ChangeType.class, composition.get(5));
        assertEquals("org.springframework.http.client.reactive.ReactorResourceFactory",
                resources.getOldFullyQualifiedTypeName());
        assertEquals("org.springframework.http.client.ReactorResourceFactory",
                resources.getNewFullyQualifiedTypeName());
        assertFalse(Boolean.TRUE.equals(resources.getIgnoreDefinition()));

        ChangeMethodName status = assertInstanceOf(ChangeMethodName.class, composition.get(6));
        assertEquals(
                "org.springframework.web.util.ContentCachingResponseWrapper getStatusCode()",
                status.getMethodPattern());
        assertEquals("getStatus", status.getNewMethodName());
        assertFalse(Boolean.TRUE.equals(status.getMatchOverrides()));
        assertFalse(Boolean.TRUE.equals(status.getIgnoreDefinition()));

        ReplaceConstantWithAnotherConstant observation = assertInstanceOf(
                ReplaceConstantWithAnotherConstant.class, composition.get(13));
        assertEquals(
                "org.springframework.http.client.observation.ClientHttpObservationDocumentation.HighCardinalityKeyNames.CLIENT_NAME",
                observation.getExistingFullyQualifiedConstantName());
        assertEquals(
                "org.springframework.http.client.observation.ClientHttpObservationDocumentation.LowCardinalityKeyNames.CLIENT_NAME",
                observation.getFullyQualifiedConstantName());
    }

    @Test
    void runtimeTreeRejectsBroadAndCrossModuleRecipes() {
        Recipe recipe = ENVIRONMENT.activateRecipes(AUTO);
        Set<String> activated = flatten(recipe).map(Recipe::getName).collect(Collectors.toSet());

        assertTrue(activated.contains(
                "org.openrewrite.java.spring.framework.MigrateMethodArgumentNotValidExceptionErrorMethod"));
        assertTrue(activated.contains(
                "org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetRawStatusCodeMethod"));
        assertTrue(activated.contains(
                "org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetStatusCodeMethod"));

        Set<String> rejected = Set.of(
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_5_2",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_5_3",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_0",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_1",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_2",
                "org.openrewrite.java.migrate.jakarta.JakartaEE10",
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion",
                "org.openrewrite.java.spring.framework.MigrateUriComponentsBuilderMethods",
                "org.openrewrite.java.spring.framework.MigrateBase64Utils",
                "org.openrewrite.java.spring.framework.MigrateSpringAssert",
                "org.openrewrite.java.spring.framework.MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode",
                "org.openrewrite.java.spring.framework.MigrateHandlerResultHasExceptionHandlerMethod",
                "org.openrewrite.java.spring.framework.MigrateHandlerResultSetExceptionHandlerMethod",
                "org.openrewrite.java.spring.framework.MigrateResourceHttpMessageWriterAddHeadersMethod",
                "org.openrewrite.java.spring.framework.MigrateWebExchangeBindExceptionResolveErrorMethod",
                "org.openrewrite.java.spring.framework.HttpComponentsClientHttpRequestFactoryReadTimeout",
                "org.openrewrite.java.spring.framework.HttpComponentsClientHttpRequestFactoryConnectTimeout",
                "org.openrewrite.java.RemoveMethodInvocations",
                "org.openrewrite.java.ChangeMethodInvocationReturnType",
                "org.openrewrite.java.spring.boot3.MaintainTrailingSlashURLMappings",
                "org.openrewrite.java.spring.boot3.AddRouteTrailingSlash",
                "org.openrewrite.java.spring.boot3.AddSetUseTrailingSlashMatch");
        assertTrue(java.util.Collections.disjoint(activated, rejected));

        List<ChangeType> typeMoves = flatten(recipe)
                .filter(ChangeType.class::isInstance)
                .map(ChangeType.class::cast)
                .toList();
        assertEquals(List.of("org.springframework.http.client.reactive.ReactorResourceFactory"),
                typeMoves.stream().map(ChangeType::getOldFullyQualifiedTypeName).toList());

        List<ReplaceConstantWithAnotherConstant> constants = flatten(recipe)
                .filter(ReplaceConstantWithAnotherConstant.class::isInstance)
                .map(ReplaceConstantWithAnotherConstant.class::cast)
                .toList();
        assertEquals(List.of(
                        "org.springframework.http.client.observation.ClientHttpObservationDocumentation.HighCardinalityKeyNames.CLIENT_NAME"),
                constants.stream()
                        .map(ReplaceConstantWithAnotherConstant::getExistingFullyQualifiedConstantName)
                        .toList());
        assertFalse(constants.stream().anyMatch(change ->
                change.getExistingFullyQualifiedConstantName().contains("web.reactive")));
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
    void officialRawStatusRecipeMigratesPinnedApacheKylinPattern() {
        // apache/kylin@b5b94b51abaf, reduced BaseFilter.java catch-body pattern.
        rewriteRun(java(
                """
                import org.springframework.web.client.HttpStatusCodeException;
                class BaseFilter {
                    int responseStatus(HttpStatusCodeException exception) {
                        return exception.getRawStatusCode();
                    }
                }
                """,
                """
                import org.springframework.web.client.HttpStatusCodeException;
                class BaseFilter {
                    int responseStatus(HttpStatusCodeException exception) {
                        return exception.getStatusCode().value();
                    }
                }
                """));
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
    void officialMethodArgumentNotValidErrorApisUseBindErrorUtils() {
        rewriteRun(java(
                """
                import org.springframework.context.MessageSource;
                import org.springframework.validation.ObjectError;
                import org.springframework.web.bind.MethodArgumentNotValidException;

                import java.util.List;
                import java.util.Locale;
                import java.util.Map;

                class ValidationErrors {
                    List<String> simple(List<ObjectError> errors) {
                        return MethodArgumentNotValidException.errorsToStringList(errors);
                    }
                    List<String> localized(List<ObjectError> errors, MessageSource messages, Locale locale) {
                        return MethodArgumentNotValidException.errorsToStringList(errors, messages, locale);
                    }
                    Map<ObjectError, String> resolved(MethodArgumentNotValidException exception,
                                                       MessageSource messages, Locale locale) {
                        return exception.resolveErrorMessages(messages, locale);
                    }
                }
                """,
                """
                import org.springframework.context.MessageSource;
                import org.springframework.validation.ObjectError;
                import org.springframework.web.bind.MethodArgumentNotValidException;
                import org.springframework.web.util.BindErrorUtils;

                import java.util.List;
                import java.util.Locale;
                import java.util.Map;

                class ValidationErrors {
                    List<String> simple(List<ObjectError> errors) {
                        return BindErrorUtils.resolve(errors).values().stream().toList();
                    }
                    List<String> localized(List<ObjectError> errors, MessageSource messages, Locale locale) {
                        return BindErrorUtils.resolve(errors, messages, locale).values().stream().toList();
                    }
                    Map<ObjectError, String> resolved(MethodArgumentNotValidException exception,
                                                       MessageSource messages, Locale locale) {
                        return BindErrorUtils.resolve(exception.getAllErrors(), messages, locale);
                    }
                }
                """));
    }

    @Test
    void officialHttpClientObservationConstantMovesToLowCardinality() {
        rewriteRun(java(
                """
                import org.springframework.http.client.observation.ClientHttpObservationDocumentation;
                class ObservationKeys {
                    Object clientName() {
                        return ClientHttpObservationDocumentation.HighCardinalityKeyNames.CLIENT_NAME;
                    }
                }
                """,
                """
                import org.springframework.http.client.observation.ClientHttpObservationDocumentation.LowCardinalityKeyNames;

                class ObservationKeys {
                    Object clientName() {
                        return LowCardinalityKeyNames.CLIENT_NAME;
                    }
                }
                """));
    }

    @Test
    void migratesOnlyStableForwardedRequestFromPinnedHapiFhirPattern() {
        // hapifhir/hapi-fhir@28652be3664a, reduced ApacheProxyAddressStrategy.java.
        rewriteRun(java(
                """
                import org.springframework.http.HttpRequest;
                import org.springframework.web.util.UriComponentsBuilder;
                class ApacheProxyAddressStrategy {
                    UriComponentsBuilder determine(HttpRequest requestWrapper) {
                        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpRequest(requestWrapper);
                        return uriBuilder;
                    }
                }
                """,
                """
                import org.springframework.http.HttpRequest;
                import org.springframework.web.util.UriComponentsBuilder;
                class ApacheProxyAddressStrategy {
                    UriComponentsBuilder determine(HttpRequest requestWrapper) {
                        UriComponentsBuilder uriBuilder = org.springframework.web.util.ForwardedHeaderUtils.adaptFromForwardedHeaders(requestWrapper.getURI(), requestWrapper.getHeaders());
                        return uriBuilder;
                    }
                }
                """));

        rewriteRun(java(
                """
                import org.springframework.http.HttpRequest;
                import org.springframework.web.util.UriComponentsBuilder;
                class SideEffects {
                    HttpRequest nextRequest() { return null; }
                    Object determine() {
                        return UriComponentsBuilder.fromHttpRequest(nextRequest());
                    }
                }
                """));
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

        rewriteRun(spec -> spec.recipe(ENVIRONMENT.activateRecipes(AUTO))
                        .parser(legacyParser())
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

    private static List<Recipe> composition(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .filter(SpringWebOfficialRecipeReuseTest::isCompositionRecipe)
                .map(SpringWebOfficialRecipeReuseTest::unwrap)
                .toList();
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = unwrap(recipe);
        return Stream.concat(Stream.of(unwrapped), unwrapped.getRecipeList().stream()
                .filter(SpringWebOfficialRecipeReuseTest::isCompositionRecipe)
                .flatMap(SpringWebOfficialRecipeReuseTest::flatten));
    }

    private static boolean isCompositionRecipe(Recipe recipe) {
        return !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether".equals(recipe.getName());
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegate) {
            current = delegate.getDelegate();
        }
        return current;
    }

    private static JavaParser.Builder<?, ?> legacyParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package org.springframework.http; public interface HttpStatusCode { int value(); }",
                "package org.springframework.http; public enum HttpStatus implements HttpStatusCode { " +
                "INTERNAL_SERVER_ERROR; public int value() { return 500; } }",
                "package org.springframework.http; public class HttpHeaders {}",
                "package org.springframework.http; public interface HttpRequest { " +
                "java.net.URI getURI(); HttpHeaders getHeaders(); }",
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
                "package org.springframework.web.client; public class HttpServerErrorException extends RestClientResponseException {}",
                "package org.springframework.web.client; public class HttpStatusCodeException extends RestClientResponseException {}",
                "package org.springframework.context; public interface MessageSource {}",
                "package org.springframework.validation; public class ObjectError {}",
                "package org.springframework.web.bind; public class MethodArgumentNotValidException extends RuntimeException { " +
                "public static java.util.List<String> errorsToStringList(" +
                "java.util.List<? extends org.springframework.validation.ObjectError> errors) { return null; } " +
                "public static java.util.List<String> errorsToStringList(" +
                "java.util.List<? extends org.springframework.validation.ObjectError> errors, " +
                "org.springframework.context.MessageSource source, java.util.Locale locale) { return null; } " +
                "public java.util.Map<org.springframework.validation.ObjectError, String> resolveErrorMessages(" +
                "org.springframework.context.MessageSource source, java.util.Locale locale) { return null; } " +
                "public java.util.List<org.springframework.validation.ObjectError> getAllErrors() { return null; } }",
                "package org.springframework.web.util; public class BindErrorUtils { " +
                "public static <E> java.util.Map<E, String> resolve(java.util.List<E> errors) { return null; } " +
                "public static <E> java.util.Map<E, String> resolve(java.util.List<E> errors, " +
                "org.springframework.context.MessageSource source, java.util.Locale locale) { return null; } }",
                "package org.springframework.web.util; public class UriComponentsBuilder { " +
                "public static UriComponentsBuilder fromHttpRequest(org.springframework.http.HttpRequest request) { return null; } }",
                "package org.springframework.web.util; public class ForwardedHeaderUtils { " +
                "public static UriComponentsBuilder adaptFromForwardedHeaders(" +
                "java.net.URI uri, org.springframework.http.HttpHeaders headers) { return null; } }",
                "package org.springframework.http.client.observation; public class ClientHttpObservationDocumentation { " +
                "public enum HighCardinalityKeyNames { CLIENT_NAME, HTTP_URL } " +
                "public enum LowCardinalityKeyNames { CLIENT_NAME } }"
        );
    }
}
