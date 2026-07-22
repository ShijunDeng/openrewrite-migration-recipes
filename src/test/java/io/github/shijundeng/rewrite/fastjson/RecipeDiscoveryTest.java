package io.github.shijundeng.rewrite.fastjson;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeDiscoveryTest {
    @Test
    void discoversAndValidatesPublicRecipe() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("io.github.shijundeng.rewrite.fastjson")
                .scanYamlResources()
                .build();

        Recipe recipe = environment.activateRecipes(MigrateFastjsonToJackson.class.getName());

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> MigrateFastjsonToJackson.class.getName().equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }
}
