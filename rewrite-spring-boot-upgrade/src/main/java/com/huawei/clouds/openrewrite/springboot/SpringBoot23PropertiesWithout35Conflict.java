package com.huawei.clouds.openrewrite.springboot;

import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.spring.ChangeSpringPropertyKey;

import java.util.List;

/**
 * Reuses every official Spring Boot 2.3 property leaf except the key migration
 * that Spring Boot 3.5 deliberately reverses.
 */
public final class SpringBoot23PropertiesWithout35Conflict extends Recipe {
    static final String OFFICIAL_RECIPE =
            "org.openrewrite.java.spring.boot2.SpringBootProperties_2_3";
    static final String REVERSED_OLD_KEY =
            "spring.http.converters.preferred-json-mapper";
    static final String REVERSED_NEW_KEY =
            "spring.mvc.converters.preferred-json-mapper";

    @Override
    public String getDisplayName() {
        return "Migrate Spring Boot properties to 2.3 without the 3.5-reversed key";
    }

    @Override
    public String getDescription() {
        return "Delegate to the precise official Spring Boot 2.3 property leaves, excluding only the " +
               "preferred JSON mapper key migration that Spring Boot 3.5 reverses.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return OfficialLeaves.RECIPES;
    }

    private static final class OfficialLeaves {
        private static final List<Recipe> RECIPES = officialRecipeList();

        private static List<Recipe> officialRecipeList() {
            Recipe official = unwrap(Environment.builder()
                    .scanRuntimeClasspath("org.openrewrite.java.spring")
                    .build()
                    .activateRecipes(OFFICIAL_RECIPE));
            return official.getRecipeList().stream()
                    .filter(SpringBoot23PropertiesWithout35Conflict::isCompositionRecipe)
                    .filter(recipe -> !isReversedBy35(unwrap(recipe)))
                    .toList();
        }
    }

    private static boolean isReversedBy35(Recipe recipe) {
        if (!(recipe instanceof ChangeSpringPropertyKey change)) {
            return false;
        }
        return REVERSED_OLD_KEY.equals(change.getOldPropertyKey()) &&
               REVERSED_NEW_KEY.equals(change.getNewPropertyKey());
    }

    private static boolean isCompositionRecipe(Recipe recipe) {
        return !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether"
                .equals(recipe.getName());
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) {
            current = delegating.getDelegate();
        }
        return current;
    }
}
