package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeAngularRouterTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.angular.UpgradeAngularRouterTo20_3_26";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesEveryDirectDependencySectionInPackageJson() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@angular/router": "~10.0.14"},
                  "devDependencies": {"@angular/router": "12.2.17"},
                  "peerDependencies": {"@angular/router": ">=13 <20"},
                  "optionalDependencies": {"@angular/router": "19.2.0"}
                }
                """,
                """
                {
                  "dependencies": {"@angular/router": "20.3.26"},
                  "devDependencies": {"@angular/router": "20.3.26"},
                  "peerDependencies": {"@angular/router": "20.3.26"},
                  "optionalDependencies": {"@angular/router": "20.3.26"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFilesOrAngularCore() {
        rewriteRun(
                json(
                        """
                        {"dependencies": {"@angular/router": "10.2.5"}}
                        """,
                        spec -> spec.path("config/dependencies.json")
                ),
                json(
                        """
                        {"dependencies": {"@angular/core": "10.2.5"}}
                        """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);
        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build();
    }
}
