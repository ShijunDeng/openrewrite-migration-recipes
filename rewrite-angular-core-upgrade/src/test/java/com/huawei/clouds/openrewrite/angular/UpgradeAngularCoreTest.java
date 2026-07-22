package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeAngularCoreTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.angular.UpgradeAngularCoreTo20_3_26";
    private static final String MIGRATE_RECIPE_NAME =
            "com.huawei.clouds.openrewrite.angular.MigrateAngularCoreTo20_3_26";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesSafeScalarValuesInEveryDirectDependencySection() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@angular/core": "~10.0.14"},
                  "devDependencies": {"@angular/core": "12.2.17"},
                  "peerDependencies": {"@angular/core": "13.1.3"},
                  "optionalDependencies": {"@angular/core": "10.2.5"}
                }
                """,
                """
                {
                  "dependencies": {"@angular/core": "20.3.26"},
                  "devDependencies": {"@angular/core": "20.3.26"},
                  "peerDependencies": {"@angular/core": "20.3.26"},
                  "optionalDependencies": {"@angular/core": "20.3.26"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFilesOrAngularCompiler() {
        rewriteRun(
                json(
                        """
                        {"dependencies": {"@angular/core": "10.2.5"}}
                        """,
                        spec -> spec.path("config/dependencies.json")
                ),
                json(
                        """
                        {"dependencies": {"@angular/compiler": "10.2.5"}}
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
        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> MIGRATE_RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment.activateRecipes(MIGRATE_RECIPE_NAME).validate().isValid());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build();
    }
}
