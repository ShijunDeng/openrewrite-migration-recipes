package com.huawei.clouds.openrewrite.springwebflux;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.ReplaceConstantWithAnotherConstant;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringWebFluxOfficialRecipeReuseTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springwebflux.MigrateDeterministicSpringWebFlux6Java";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springwebflux",
                                  "org.openrewrite.java.spring.framework")
            .build();

    @Test
    void deterministicCompositionUsesOnlyAuditedCoreAndSpringComponents() {
        DeclarativeRecipe recipe = assertInstanceOf(
                DeclarativeRecipe.class, ENVIRONMENT.activateRecipes(AUTO));
        List<Recipe> composition = composition(recipe);

        assertEquals(List.of(
                        "org.openrewrite.java.ChangeType",
                        "org.openrewrite.java.spring.framework.MigrateResponseStatusException",
                        "org.openrewrite.java.spring.framework.MigrateHandlerResultHasExceptionHandlerMethod",
                        "org.openrewrite.java.spring.framework.MigrateHandlerResultSetExceptionHandlerMethod",
                        "org.openrewrite.java.spring.framework.MigrateResourceHttpMessageWriterAddHeadersMethod",
                        "org.openrewrite.java.spring.framework.MigrateWebExchangeBindExceptionResolveErrorMethod",
                        "org.openrewrite.java.ReplaceConstantWithAnotherConstant",
                        "org.openrewrite.java.ChangeMethodName",
                        "org.openrewrite.java.ChangeMethodName"),
                composition.stream().map(Recipe::getName).toList());
        assertEquals(List.of(IsSpringWebFluxNonGeneratedSource.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());

        ChangeType resources = assertInstanceOf(ChangeType.class, composition.get(0));
        assertEquals("org.springframework.http.client.reactive.ReactorResourceFactory",
                resources.getOldFullyQualifiedTypeName());
        assertEquals("org.springframework.http.client.ReactorResourceFactory",
                resources.getNewFullyQualifiedTypeName());

        ReplaceConstantWithAnotherConstant observation = assertInstanceOf(
                ReplaceConstantWithAnotherConstant.class, composition.get(6));
        assertEquals(
                "org.springframework.web.reactive.function.client.ClientHttpObservationDocumentation.HighCardinalityKeyNames.CLIENT_NAME",
                observation.getExistingFullyQualifiedConstantName());
        assertEquals(
                "org.springframework.web.reactive.function.client.ClientHttpObservationDocumentation.LowCardinalityKeyNames.CLIENT_NAME",
                observation.getFullyQualifiedConstantName());

        ChangeMethodName exchange = assertInstanceOf(ChangeMethodName.class, composition.get(7));
        assertEquals(
                "org.springframework.web.filter.reactive.ServerWebExchangeContextFilter get(reactor.util.context.Context)",
                exchange.getMethodPattern());
        assertEquals("getExchange", exchange.getNewMethodName());

        ChangeMethodName webClient = assertInstanceOf(ChangeMethodName.class, composition.get(8));
        assertEquals(
                "org.springframework.web.reactive.function.client.support.WebClientAdapter forClient(org.springframework.web.reactive.function.client.WebClient)",
                webClient.getMethodPattern());
        assertEquals("create", webClient.getNewMethodName());
    }

    @Test
    void runtimeTreeIncludesPreciseNestedAccessorsAndRejectsBroadAggregates() {
        Recipe recipe = ENVIRONMENT.activateRecipes(AUTO);
        Set<String> activated = flatten(recipe).map(Recipe::getName).collect(Collectors.toSet());

        assertTrue(activated.contains(
                "org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetRawStatusCodeMethod"));
        assertTrue(activated.contains(
                "org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetStatusCodeMethod"));

        Set<String> rejected = Set.of(
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_5_3",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_0",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_1",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_2",
                "org.openrewrite.java.migrate.jakarta.JakartaEE10",
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion",
                "org.openrewrite.java.spring.boot3.MaintainTrailingSlashURLMappings",
                "org.openrewrite.java.spring.boot3.AddRouteTrailingSlash",
                "org.openrewrite.java.spring.boot3.AddSetUseTrailingSlashMatch",
                "org.openrewrite.java.spring.framework.MigrateSpringAssert",
                "org.openrewrite.java.spring.framework.MigrateClientHttpResponseGetRawStatusCodeMethod",
                "org.openrewrite.java.spring.framework.MigrateMethodArgumentNotValidExceptionErrorMethod");
        assertTrue(java.util.Collections.disjoint(activated, rejected));

        List<ChangeType> typeMoves = flatten(recipe)
                .filter(ChangeType.class::isInstance)
                .map(ChangeType.class::cast)
                .toList();
        assertEquals(List.of("org.springframework.http.client.reactive.ReactorResourceFactory"),
                typeMoves.stream().map(ChangeType::getOldFullyQualifiedTypeName).toList());
        assertFalse(typeMoves.stream().anyMatch(change ->
                change.getOldFullyQualifiedTypeName().contains("JettyWebSocketClient")));
    }

    private static List<Recipe> composition(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .filter(SpringWebFluxOfficialRecipeReuseTest::isCompositionRecipe)
                .map(SpringWebFluxOfficialRecipeReuseTest::unwrap)
                .toList();
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = unwrap(recipe);
        return Stream.concat(Stream.of(unwrapped), unwrapped.getRecipeList().stream()
                .filter(SpringWebFluxOfficialRecipeReuseTest::isCompositionRecipe)
                .flatMap(SpringWebFluxOfficialRecipeReuseTest::flatten));
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
}
