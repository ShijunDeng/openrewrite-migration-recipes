package com.huawei.clouds.openrewrite.junitjupiter;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JUnitJupiterOfficialRecipeReuseTest {
    private static final String DETERMINISTIC =
            "com.huawei.clouds.openrewrite.junitjupiter.MigrateDeterministicJUnitJupiter6Java";
    private static final String DEPENDENCY =
            "com.huawei.clouds.openrewrite.junitjupiter.UpgradeSelectedJUnitJupiterApiDependencyWithOfficialFallback";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.junitjupiter.MigrateJUnitJupiterApiTo6_0_1";
    private static final String OFFICIAL_ORDERER =
            "org.openrewrite.java.testing.junit6.MigrateMethodOrdererAlphanumeric";
    private static final String OFFICIAL_DEPENDENCY =
            "org.openrewrite.java.dependencies.UpgradeDependencyVersion";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath(
                    "com.huawei.clouds.openrewrite.junitjupiter",
                    "org.openrewrite.java",
                    "org.openrewrite.java.dependencies",
                    "org.openrewrite.java.testing.junit6")
            .build();

    @Test
    void discoversPinnedOfficialJUnit6Recipe() {
        Set<String> names = ENVIRONMENT.listRecipes().stream()
                .map(Recipe::getName).collect(Collectors.toSet());
        assertTrue(names.contains(OFFICIAL_ORDERER));
        Recipe orderer = ENVIRONMENT.listRecipes().stream()
                .filter(recipe -> OFFICIAL_ORDERER.equals(recipe.getName()))
                .findFirst().orElseThrow();
        assertEquals("3.42.1", orderer.getClass().getPackage().getImplementationVersion());
    }

    @Test
    void deterministicCompositionUsesOfficialLeavesAndOnlyTheInterceptorGap() {
        List<Recipe> leaves = effectiveChildren(activate(DETERMINISTIC));
        assertEquals(List.of(
                        ChangeMethodName.class.getName(),
                        OFFICIAL_ORDERER,
                        RepairMethodOrdererTypeMetadata.class.getName(),
                        MigrateJUnitJupiter6Java.class.getName()),
                leaves.stream().map(Recipe::getName).toList());

        ChangeMethodName store = assertInstanceOf(ChangeMethodName.class, leaves.get(0));
        assertEquals(
                "org.junit.jupiter.api.extension.ExtensionContext$Store getOrComputeIfAbsent(..)",
                store.getMethodPattern());
        assertEquals("computeIfAbsent", store.getNewMethodName());
        assertEquals(Boolean.TRUE, store.getMatchOverrides());
        assertEquals(Boolean.TRUE, store.getIgnoreDefinition());

        assertInstanceOf(RepairMethodOrdererTypeMetadata.class, leaves.get(2));
    }

    @Test
    void dependencyCompositionRunsOfficialUpgradeBeforeStrictRawFallback() {
        List<Recipe> leaves = effectiveChildren(activate(DEPENDENCY));
        assertEquals(List.of(
                        OFFICIAL_DEPENDENCY,
                        UpgradeSelectedJUnitJupiterApiDependency.class.getName()),
                leaves.stream().map(Recipe::getName).toList());
    }

    @Test
    void allRecommendedRecipesValidate() {
        Recipe recipe = ENVIRONMENT.activateRecipes(RECOMMENDED);
        assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                () -> recipe.validateAll().toString());
    }

    private static Recipe activate(String name) {
        return unwrap(ENVIRONMENT.activateRecipes(name));
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        List<Recipe> children = new ArrayList<>();
        for (Recipe child : recipe.getRecipeList()) {
            Recipe unwrapped = unwrap(child);
            if (!unwrapped.getClass().getName().endsWith("PreconditionBellwether")) {
                children.add(unwrapped);
            }
        }
        return children;
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) {
            current = delegating.getDelegate();
        }
        return current;
    }
}
