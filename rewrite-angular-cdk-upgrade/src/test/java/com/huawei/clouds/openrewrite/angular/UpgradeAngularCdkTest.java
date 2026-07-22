package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeAngularCdkTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.angular.UpgradeAngularCdkTo20_2_14";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesCaretDependencyFromTaskBoard() {
        // Adapted from kiswa/TaskBoard at 857583e4:
        // https://github.com/kiswa/TaskBoard/blob/857583e4bb508c7b449a8e45bb0747d22d88abdb/package.json
        rewriteRun(json(
                """
                {
                  "name": "task-board",
                  "dependencies": {
                    "@angular/animations": "^10.0.11",
                    "@angular/cdk": "^10.1.3",
                    "@angular/common": "^10.0.11",
                    "@angular/core": "^10.0.11"
                  }
                }
                """,
                """
                {
                  "name": "task-board",
                  "dependencies": {
                    "@angular/animations": "^10.0.11",
                    "@angular/cdk": "20.2.14",
                    "@angular/common": "^10.0.11",
                    "@angular/core": "^10.0.11"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@angular/cdk": "10.2.6"},
                  "devDependencies": {"@angular/cdk": "~12.2.13"},
                  "peerDependencies": {"@angular/cdk": ">=13.0.0 <20"},
                  "optionalDependencies": {"@angular/cdk": "^19.2.0"}
                }
                """,
                """
                {
                  "dependencies": {"@angular/cdk": "20.2.14"},
                  "devDependencies": {"@angular/cdk": "20.2.14"},
                  "peerDependencies": {"@angular/cdk": "20.2.14"},
                  "optionalDependencies": {"@angular/cdk": "20.2.14"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesLastPatchBeforeTarget() {
        rewriteRun(json(
                """
                {"dependencies": {"@angular/cdk": "20.2.13"}}
                """,
                """
                {"dependencies": {"@angular/cdk": "20.2.14"}}
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesNestedWorkspacePackageJson() {
        rewriteRun(json(
                """
                {
                  "name": "@example/components",
                  "peerDependencies": {
                    "@angular/cdk": "^14.2.0",
                    "@angular/core": "^14.2.0"
                  }
                }
                """,
                """
                {
                  "name": "@example/components",
                  "peerDependencies": {
                    "@angular/cdk": "20.2.14",
                    "@angular/core": "^14.2.0"
                  }
                }
                """,
                spec -> spec.path("packages/components/package.json")
        ));
    }

    @Test
    void preservesAdjacentAngularPackages() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/cdk": "13.3.9",
                    "@angular/core": "13.3.11",
                    "@angular/material": "13.3.9"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@angular/cdk": "20.2.14",
                    "@angular/core": "13.3.11",
                    "@angular/material": "13.3.9"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTargetVersionAndDspacePackageShapeUntouched() {
        // Reduced from DSpace/dspace-angular at 8410074a. It demonstrates that CDK 20.2.14
        // legitimately coexists with Angular framework 20.3.x patch versions.
        // https://github.com/DSpace/dspace-angular/blob/8410074a9e74654a260d000a89b6f6fe1fd54167/package.json
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/animations": "^20.3.25",
                    "@angular/cdk": "^20.2.14",
                    "@angular/common": "^20.3.25",
                    "@angular/core": "^20.3.25"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotDowngradeNewerVersions() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@angular/cdk": "21.2.9"},
                  "devDependencies": {"@angular/cdk": "^22.0.2"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesWorkspaceProtocolUntouched() {
        // The official Angular Material workspace uses workspace:* for its local CDK dev dependency:
        // https://github.com/angular/components/blob/20.2.14/src/material/package.json
        rewriteRun(json(
                """
                {
                  "name": "@angular/material",
                  "peerDependencies": {"@angular/cdk": "0.0.0-PLACEHOLDER"},
                  "devDependencies": {"@angular/cdk": "workspace:*"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyPackageLock() {
        rewriteRun(json(
                """
                {
                  "name": "locked-app",
                  "packages": {
                    "": {"dependencies": {"@angular/cdk": "10.2.7"}},
                    "node_modules/@angular/cdk": {"version": "10.2.7"}
                  }
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                """
                {"dependencies": {"@angular/cdk": "10.2.7"}}
                """,
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNames() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/cdk-experimental": "10.2.7",
                    "angular-cdk": "10.2.7"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
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
