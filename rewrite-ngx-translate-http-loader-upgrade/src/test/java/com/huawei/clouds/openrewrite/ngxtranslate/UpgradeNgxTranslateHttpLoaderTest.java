package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeNgxTranslateHttpLoaderTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateHttpLoaderTo17";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesEveryDirectDependencySectionInPackageJson() {
        rewriteRun(
                json(
                        """
                        {
                          "dependencies": {
                            "@ngx-translate/http-loader": "^4.0.0"
                          },
                          "devDependencies": {
                            "@ngx-translate/http-loader": "6.0.0"
                          },
                          "peerDependencies": {
                            "@ngx-translate/http-loader": ">=7 <9"
                          },
                          "optionalDependencies": {
                            "@ngx-translate/http-loader": "8.0.0"
                          }
                        }
                        """,
                        """
                        {
                          "dependencies": {
                            "@ngx-translate/http-loader": "17.0.0"
                          },
                          "devDependencies": {
                            "@ngx-translate/http-loader": "17.0.0"
                          },
                          "peerDependencies": {
                            "@ngx-translate/http-loader": "17.0.0"
                          },
                          "optionalDependencies": {
                            "@ngx-translate/http-loader": "17.0.0"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void doesNotModifyOtherJsonFilesOrCorePackage() {
        rewriteRun(
                json(
                        """
                        {
                          "dependencies": {
                            "@ngx-translate/http-loader": "8.0.0"
                          }
                        }
                        """,
                        spec -> spec.path("config/dependencies.json")
                ),
                json(
                        """
                        {
                          "dependencies": {
                            "@ngx-translate/core": "15.0.0"
                          }
                        }
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
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxtranslate")
                .scanYamlResources()
                .build();
    }
}
