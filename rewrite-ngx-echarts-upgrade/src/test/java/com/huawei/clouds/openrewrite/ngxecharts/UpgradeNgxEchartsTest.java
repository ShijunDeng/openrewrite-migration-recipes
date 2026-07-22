package com.huawei.clouds.openrewrite.ngxecharts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeNgxEchartsTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.ngxecharts.UpgradeNgxEchartsTo20_0_2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesNiceFishAngular16Application() {
        // Adapted from damoqiongqiu/NiceFish at 4454db90:
        // https://github.com/damoqiongqiu/NiceFish/blob/4454db9074a614ec9cdf3661cc5a05273d393b11/package.json
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/core": "16.2.0",
                    "echarts": "5.4.2",
                    "ngx-echarts": "15.0.3",
                    "rxjs": "6.5.3"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@angular/core": "16.2.0",
                    "echarts": "5.4.2",
                    "ngx-echarts": "20.0.2",
                    "rxjs": "6.5.3"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesCareyDevelopmentCrmAngular11Application() {
        // Adapted from careydevelopment/careydevelopmentcrm at 7d6f44b8:
        // https://github.com/careydevelopment/careydevelopmentcrm/blob/7d6f44b88e3fcbb54673b896c2f68d48a9f58dd4/package.json
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/core": "~11.1.1",
                    "echarts": "^5.0.2",
                    "ngx-echarts": "^6.0.1"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@angular/core": "~11.1.1",
                    "echarts": "^5.0.2",
                    "ngx-echarts": "20.0.2"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesMatxAngular14Application() {
        // Adapted from uilibrary/matx-angular at 6b16bbe0:
        // https://github.com/uilibrary/matx-angular/blob/6b16bbe0efa9c387e6d21141981fbfe01a8043e4/package.json
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/core": "^14.2.0",
                    "echarts": "^5.3.3",
                    "ngx-echarts": "^14.0.0"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@angular/core": "^14.2.0",
                    "echarts": "^5.3.3",
                    "ngx-echarts": "20.0.2"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "14.0.0", "15.0.0", "15.0.2", "15.0.3", "16.0.0",
            "5.2.2", "6.0.1", "7.0.2", "7.1.0", "8.0.1"
    })
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(json(
                "{\"dependencies\":{\"ngx-echarts\":\"" + oldVersion + "\"}}",
                "{\"dependencies\":{\"ngx-echarts\":\"20.0.2\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"ngx-echarts": "5.2.2"},
                  "devDependencies": {"ngx-echarts": "~8.0.1"},
                  "peerDependencies": {"ngx-echarts": ">=14.0.0 <20"},
                  "optionalDependencies": {"ngx-echarts": "^19.0.0"}
                }
                """,
                """
                {
                  "dependencies": {"ngx-echarts": "20.0.2"},
                  "devDependencies": {"ngx-echarts": "20.0.2"},
                  "peerDependencies": {"ngx-echarts": "20.0.2"},
                  "optionalDependencies": {"ngx-echarts": "20.0.2"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesCommonRangesAndPatchesBeforeTarget() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"ngx-echarts": "v7.1.0"},
                  "devDependencies": {"ngx-echarts": "17.x"},
                  "peerDependencies": {"ngx-echarts": "  >=18.0.0 <21"},
                  "optionalDependencies": {"ngx-echarts": "^20.0.1"}
                }
                """,
                """
                {
                  "dependencies": {"ngx-echarts": "20.0.2"},
                  "devDependencies": {"ngx-echarts": "20.0.2"},
                  "peerDependencies": {"ngx-echarts": "20.0.2"},
                  "optionalDependencies": {"ngx-echarts": "20.0.2"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesNestedWorkspaceManifest() {
        rewriteRun(json(
                """
                {"name":"@example/charts","peerDependencies":{"@angular/core":">=20","ngx-echarts":"^16.0.0"}}
                """,
                """
                {"name":"@example/charts","peerDependencies":{"@angular/core":">=20","ngx-echarts":"20.0.2"}}
                """,
                spec -> spec.path("packages/charts/package.json")
        ));
    }

    @Test
    void preservesAngularAndEchartsVersions() {
        rewriteRun(json(
                """
                {"dependencies":{"@angular/core":"20.3.26","echarts":"6.0.0","ngx-echarts":"16.0.0"}}
                """,
                """
                {"dependencies":{"@angular/core":"20.3.26","echarts":"6.0.0","ngx-echarts":"20.0.2"}}
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOfficialTargetManifestShapeUntouched() {
        // Reduced from the official v20.0.2 package manifest:
        // https://github.com/xieziyu/ngx-echarts/blob/v20.0.2/projects/ngx-echarts/package.json
        rewriteRun(json(
                """
                {
                  "name": "ngx-echarts",
                  "version": "20.0.2",
                  "peerDependencies": {"@angular/core": ">=20.0.0", "echarts": ">=5.0.0"}
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
                  "dependencies": {"ngx-echarts": "20.0.3"},
                  "devDependencies": {"ngx-echarts": "^21.0.0"},
                  "peerDependencies": {"ngx-echarts": "22.0.0-beta.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesExternalProtocolsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"ngx-echarts": "workspace:^16.0.0"},
                  "devDependencies": {"ngx-echarts": "npm:@example/charts@16.0.0"},
                  "peerDependencies": {"ngx-echarts": "github:xieziyu/ngx-echarts#v16.0.0"},
                  "optionalDependencies": {"ngx-echarts": "file:../ngx-echarts"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOutOfScopeOldAndUnboundedVersionsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"ngx-echarts": "4.2.2"},
                  "devDependencies": {"ngx-echarts": "*"},
                  "peerDependencies": {"ngx-echarts": "latest"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyPackageLock() {
        rewriteRun(json(
                """
                {"packages":{"":{"dependencies":{"ngx-echarts":"16.0.0"}},"node_modules/ngx-echarts":{"version":"16.0.0"}}}
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                "{\"dependencies\":{\"ngx-echarts\":\"16.0.0\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNames() {
        rewriteRun(json(
                """
                {"dependencies":{"@example/ngx-echarts":"16.0.0","ngx-echarts-testing":"16.0.0","echarts":"5.4.2"}}
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxecharts")
                .scanYamlResources()
                .build();
    }
}
