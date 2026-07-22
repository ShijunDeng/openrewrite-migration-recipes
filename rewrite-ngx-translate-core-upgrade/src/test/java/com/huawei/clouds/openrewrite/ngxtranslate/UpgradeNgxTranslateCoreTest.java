package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeNgxTranslateCoreTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateCoreTo17";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesScopedPackageInPackageJson() {
        rewriteRun(
                json(
                        """
                        {
                          "name": "translated-app",
                          "dependencies": {
                            "@ngx-translate/core": "^11.0.1"
                          },
                          "devDependencies": {
                            "@ngx-translate/core": "15.0.0"
                          }
                        }
                        """,
                        """
                        {
                          "name": "translated-app",
                          "dependencies": {
                            "@ngx-translate/core": "17.0.0"
                          },
                          "devDependencies": {
                            "@ngx-translate/core": "17.0.0"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(
                json(
                        """
                        {
                          "dependencies": {
                            "@ngx-translate/core": "14.0.0"
                          }
                        }
                        """,
                        spec -> spec.path("config/dependencies.json")
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
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxtranslate")
                .scanYamlResources()
                .build();
    }
}
