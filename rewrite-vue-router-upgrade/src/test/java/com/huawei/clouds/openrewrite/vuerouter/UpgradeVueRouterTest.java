package com.huawei.clouds.openrewrite.vuerouter;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeVueRouterTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.vuerouter.UpgradeVueRouterTo5_0_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesExactV3DependencyFromVueElementAdmin() {
        // Adapted from PanJiaChen/vue-element-admin at 6858a9ad:
        // https://github.com/PanJiaChen/vue-element-admin/blob/6858a9ad67483025f6a9432a926beb9327037be3/package.json
        rewriteRun(json(
                """
                {
                  "name": "vue-element-admin",
                  "dependencies": {
                    "vue": "2.6.10",
                    "vue-router": "3.0.2",
                    "vuex": "3.1.0"
                  }
                }
                """,
                """
                {
                  "name": "vue-element-admin",
                  "dependencies": {
                    "vue": "2.6.10",
                    "vue-router": "5.0.3",
                    "vuex": "3.1.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesCaretV3DependencyFromVueHackernews() {
        // Adapted from vuejs/vue-hackernews-2.0 at 98399b55:
        // https://github.com/vuejs/vue-hackernews-2.0/blob/98399b55c6f1da4197840ba76189795b3e95be0f/package.json
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "vue": "^2.5.22",
                    "vue-router": "^3.0.1",
                    "vue-server-renderer": "^2.5.22",
                    "vuex-router-sync": "^5.0.0"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "vue": "^2.5.22",
                    "vue-router": "5.0.3",
                    "vue-server-renderer": "^2.5.22",
                    "vuex-router-sync": "^5.0.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesV4DependencyFromVueManageSystem() {
        // Adapted from lin-xin/vue-manage-system at 6a7019ec:
        // https://github.com/lin-xin/vue-manage-system/blob/6a7019ec1a74cc05297d18647a5f944c242d468a/package.json
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "pinia": "^2.1.7",
                    "vue": "^3.4.5",
                    "vue-router": "^4.2.5"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "pinia": "^2.1.7",
                    "vue": "^3.4.5",
                    "vue-router": "5.0.3"
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
                  "dependencies": {"vue-router": "3.5.4"},
                  "devDependencies": {"vue-router": "~3.6.5"},
                  "peerDependencies": {"vue-router": ">=4.0.0 <5"},
                  "optionalDependencies": {"vue-router": "^4.5.1"}
                }
                """,
                """
                {
                  "dependencies": {"vue-router": "5.0.3"},
                  "devDependencies": {"vue-router": "5.0.3"},
                  "peerDependencies": {"vue-router": "5.0.3"},
                  "optionalDependencies": {"vue-router": "5.0.3"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesVersionPrefixesRangesAndPrereleases() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"vue-router": "v3.6.5"},
                  "devDependencies": {"vue-router": "^4.0.0-beta.13"},
                  "peerDependencies": {"vue-router": "  >=4.0.12 <5.0.0"},
                  "optionalDependencies": {"vue-router": "5.0.2-rc.1"}
                }
                """,
                """
                {
                  "dependencies": {"vue-router": "5.0.3"},
                  "devDependencies": {"vue-router": "5.0.3"},
                  "peerDependencies": {"vue-router": "5.0.3"},
                  "optionalDependencies": {"vue-router": "5.0.3"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesEveryPatchBeforeTarget() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"vue-router": "5.0.0"},
                  "devDependencies": {"vue-router": "~5.0.1"},
                  "peerDependencies": {"vue-router": "^5.0.2"}
                }
                """,
                """
                {
                  "dependencies": {"vue-router": "5.0.3"},
                  "devDependencies": {"vue-router": "5.0.3"},
                  "peerDependencies": {"vue-router": "5.0.3"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesNestedWorkspacePackageJson() {
        rewriteRun(json(
                """
                {
                  "name": "@example/router-adapter",
                  "peerDependencies": {"vue": "^3.5.0", "vue-router": "^4.6.4"}
                }
                """,
                """
                {
                  "name": "@example/router-adapter",
                  "peerDependencies": {"vue": "^3.5.0", "vue-router": "5.0.3"}
                }
                """,
                spec -> spec.path("packages/router-adapter/package.json")
        ));
    }

    @Test
    void preservesAdjacentVueEcosystemDependencies() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@vue/compiler-sfc": "^3.5.17",
                    "pinia": "^3.0.4",
                    "vue": "^3.5.0",
                    "vue-router": "4.6.4"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@vue/compiler-sfc": "^3.5.17",
                    "pinia": "^3.0.4",
                    "vue": "^3.5.0",
                    "vue-router": "5.0.3"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTargetVersionRangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"vue-router": "5.0.3"},
                  "devDependencies": {"vue-router": "^5.0.3"},
                  "peerDependencies": {"vue-router": ">=5.0.3 <6"}
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
                  "dependencies": {"vue-router": "5.0.20"},
                  "devDependencies": {"vue-router": "^5.1.0"},
                  "peerDependencies": {"vue-router": "6.0.0-beta.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNonRegistryReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"vue-router": "workspace:*"},
                  "devDependencies": {"vue-router": "npm:@example/router@4.0.0"},
                  "peerDependencies": {"vue-router": "github:vuejs/router#v4.6.4"},
                  "optionalDependencies": {"vue-router": "file:../router"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesUnscopedAndPreV3RangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"vue-router": "2.8.1"},
                  "devDependencies": {"vue-router": "*"},
                  "peerDependencies": {"vue-router": "latest"}
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
                    "": {"dependencies": {"vue-router": "3.6.5"}},
                    "node_modules/vue-router": {"version": "3.6.5"}
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
                {"dependencies": {"vue-router": "4.6.4"}}
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
                    "vue-router-mock": "1.1.0",
                    "@example/vue-router": "4.6.4",
                    "vue-router-next": "4.0.0"
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
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.vuerouter")
                .scanYamlResources()
                .build();
    }
}
