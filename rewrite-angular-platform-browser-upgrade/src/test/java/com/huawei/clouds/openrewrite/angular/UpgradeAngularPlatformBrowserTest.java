package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeAngularPlatformBrowserTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.angular.UpgradeAngularPlatformBrowserTo20_3_26";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE));
    }

    @ParameterizedTest(name = "upgrades spreadsheet version {0}")
    @ValueSource(strings = {
            "10.0.14", "10.2.5", "11.2.14", "12.2.10", "12.2.13", "12.2.14",
            "12.2.16", "12.2.17", "13.1.3", "13.2.6"
    })
    void upgradesEveryVisibleSpreadsheetVersion(String version) {
        rewriteRun(packageVersion("package.json", version));
    }

    @ParameterizedTest(name = "upgrades exact anchored declaration {0}")
    @ValueSource(strings = {
            "^10.0.14", "~10.0.14", "^10.2.5", "~10.2.5", "^11.2.14", "~11.2.14",
            "^12.2.10", "~12.2.10", "^12.2.13", "~12.2.13", "^12.2.14", "~12.2.14",
            "^12.2.16", "~12.2.16", "^12.2.17", "~12.2.17", "^13.1.3", "~13.1.3",
            "^13.2.6", "~13.2.6"
    })
    void upgradesEveryCaretAndTildeForm(String declaration) {
        rewriteRun(packageVersion("package.json", declaration));
    }

    @Test
    void upgradesRealPokedexManifestAndPreservesLockstepEvidence() {
        // HybridShivam/pokedex-angular-app, fixed commit a39ca00439e160069ea711ee98326288f9a1443e.
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/common": "~10.2.5",
                    "@angular/core": "~10.2.5",
                    "@angular/platform-browser": "~10.2.5",
                    "@angular/platform-browser-dynamic": "~10.2.5"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@angular/common": "~10.2.5",
                    "@angular/core": "~10.2.5",
                    "@angular/platform-browser": "20.3.26",
                    "@angular/platform-browser-dynamic": "~10.2.5"
                  }
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesRealApacheNifiManifest() {
        // apache/nifi, fixed commit 59cff970ca8b98ee51ae4418cf4de6830fa28c37.
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/core": "11.2.14",
                    "@angular/platform-browser": "11.2.14",
                    "@angular/platform-browser-dynamic": "11.2.14"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@angular/core": "11.2.14",
                    "@angular/platform-browser": "20.3.26",
                    "@angular/platform-browser-dynamic": "11.2.14"
                  }
                }
                """,
                source -> source.path("nifi-registry/nifi-registry-core/nifi-registry-web-ui/src/main/package.json")
        ));
    }

    @Test
    void upgradesRealElasticApmManifest() {
        // elastic/apm-agent-rum-js, fixed commit 997138a38ab3253072e710a97343dea240447b7c.
        rewriteRun(json(
                """
                {
                  "devDependencies": {
                    "@angular/core": "^12.2.17",
                    "@angular/platform-browser": "^12.2.17",
                    "@angular/platform-browser-dynamic": "^12.2.17"
                  }
                }
                """,
                """
                {
                  "devDependencies": {
                    "@angular/core": "^12.2.17",
                    "@angular/platform-browser": "20.3.26",
                    "@angular/platform-browser-dynamic": "^12.2.17"
                  }
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@angular/platform-browser": "10.0.14"},
                  "devDependencies": {"@angular/platform-browser": "^11.2.14"},
                  "peerDependencies": {"@angular/platform-browser": "~12.2.17"},
                  "optionalDependencies": {"@angular/platform-browser": "13.2.6"}
                }
                """,
                """
                {
                  "dependencies": {"@angular/platform-browser": "20.3.26"},
                  "devDependencies": {"@angular/platform-browser": "20.3.26"},
                  "peerDependencies": {"@angular/platform-browser": "20.3.26"},
                  "optionalDependencies": {"@angular/platform-browser": "20.3.26"}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesRootAndNestedWorkspaceManifests() {
        rewriteRun(
                packageVersion("package.json", "10.2.5"),
                packageVersion("apps/browser/package.json", "^12.2.14"),
                packageVersion("libs/ui/package.json", "~13.1.3")
        );
    }

    @ParameterizedTest(name = "leaves unlisted scalar {0}")
    @ValueSource(strings = {
            "9.1.13", "10.0.13", "10.1.6", "11.2.13", "12.2.11", "12.2.15",
            "13.0.0", "13.2.5", "14.0.0", "19.2.0", "20.3.25", "20.3.27", "21.0.0"
    })
    void leavesUnlistedTargetAndNewerScalarVersionsUntouched(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"@angular/platform-browser\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves complex or unsafe declaration {0}")
    @ValueSource(strings = {
            ">=10.0.14 <20", "10.2.5 || ^12.2.17", "10.0.14 - 13.2.6", "12.x", "*",
            "v11.2.14", "=12.2.17", "^v13.1.3", "12.2.17-next.1", "13.2.6+vendor.2"
    })
    void leavesComplexRangesAndVersionLookalikesUntouched(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"@angular/platform-browser\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void leavesProtocolsAliasesTagsAndVariablesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@angular/platform-browser": "workspace:^12.2.17"},
                  "devDependencies": {"@angular/platform-browser": "npm:@company/platform-browser@13.2.6"},
                  "peerDependencies": {"@angular/platform-browser": "file:../platform-browser"},
                  "optionalDependencies": {"@angular/platform-browser": "${ANGULAR_VERSION}"},
                  "catalog": {"@angular/platform-browser": "12.2.17"}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void leavesGitUrlsCatalogAndNonStringValuesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@angular/platform-browser": "github:angular/angular#12.2.17"},
                  "devDependencies": {"@angular/platform-browser": "https://registry.example/angular-12.2.17.tgz"},
                  "peerDependencies": {"@angular/platform-browser": {"version": "12.2.17"}},
                  "optionalDependencies": {"@angular/platform-browser": null}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void leavesOverridesResolutionsMetadataAndLockfilesUntouched() {
        rewriteRun(
                json(
                        """
                        {
                          "overrides": {"@angular/platform-browser": "10.2.5"},
                          "resolutions": {"@angular/platform-browser": "11.2.14"},
                          "pnpm": {"overrides": {"@angular/platform-browser": "12.2.17"}},
                          "peerDependenciesMeta": {"@angular/platform-browser": {"optional": true}},
                          "metadata": {"dependencies": {"@angular/platform-browser": "13.2.6"}}
                        }
                        """,
                        source -> source.path("package.json")
                ),
                json("{\"packages\":{\"\":{\"dependencies\":{\"@angular/platform-browser\":\"12.2.17\"}}}}",
                        source -> source.path("package-lock.json"))
        );
    }

    @Test
    void leavesOrdinaryJsonSimilarPackagesAndAngularPeersUntouched() {
        rewriteRun(
                json("{\"dependencies\":{\"@angular/platform-browser\":\"12.2.17\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                json(
                        """
                        {
                          "dependencies": {
                            "@angular/platform-browser-dynamic": "12.2.17",
                            "@angular/core": "12.2.17",
                            "@angular/common": "12.2.17",
                            "angular-platform-browser": "12.2.17",
                            "@angular/Platform-Browser": "12.2.17"
                          }
                        }
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void discoversValidRecipeMetadata() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE.equals(candidate.getName())));
        assertEquals("Upgrade selected @angular/platform-browser declarations to 20.3.26", recipe.getDisplayName());
        assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                () -> recipe.validateAll().toString());
    }

    private static SourceSpecs packageVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"@angular/platform-browser\":\"" + version + "\"}}",
                "{\"dependencies\":{\"@angular/platform-browser\":\"20.3.26\"}}",
                source -> source.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build();
    }
}
