package com.huawei.clouds.openrewrite.datefns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeDateFnsTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.datefns.UpgradeDateFnsTo4_1_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesNextacularProductionDependencyAndLeavesSourceForManualMigration() {
        // Reduced from nextacular/nextacular at 06bee752; the source's default subpath import
        // is deliberately unchanged because v3+ requires a source-aware named-import migration.
        // https://github.com/nextacular/nextacular/blob/06bee752a0423faa2ba0217ea2fedd719a52bda9/package.json
        // https://github.com/nextacular/nextacular/blob/06bee752a0423faa2ba0217ea2fedd719a52bda9/src/pages/account/billing.tsx
        rewriteRun(
                json(
                        """
                        {
                          "name": "nextacular",
                          "engines": {"node": ">=22.0.0 <23.0.0"},
                          "dependencies": {
                            "@stripe/stripe-js": "^1.54.2",
                            "date-fns": "^2.30.0",
                            "next": "^13.5.11"
                          }
                        }
                        """,
                        """
                        {
                          "name": "nextacular",
                          "engines": {"node": ">=22.0.0 <23.0.0"},
                          "dependencies": {
                            "@stripe/stripe-js": "^1.54.2",
                            "date-fns": "4.1.0",
                            "next": "^13.5.11"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        "import formatDistance from 'date-fns/formatDistance';\n",
                        spec -> spec.path("src/pages/account/billing.tsx")
                )
        );
    }

    @Test
    void upgradesVueDatepickerDependencyAndPreservesTimezoneCompanion() {
        // Reduced from Vuepic/vue-datepicker v4.2.3 at b541061b:
        // https://github.com/Vuepic/vue-datepicker/blob/b541061b3d2a90a74e4c821992b7aa88e10d533b/package.json
        rewriteRun(json(
                """
                {
                  "name": "@vuepic/vue-datepicker",
                  "dependencies": {
                    "date-fns": "^2.29.3",
                    "date-fns-tz": "^1.3.7"
                  },
                  "peerDependencies": {"vue": ">=3.2.0"},
                  "engines": {"node": ">=14"}
                }
                """,
                """
                {
                  "name": "@vuepic/vue-datepicker",
                  "dependencies": {
                    "date-fns": "4.1.0",
                    "date-fns-tz": "^1.3.7"
                  },
                  "peerDependencies": {"vue": ">=3.2.0"},
                  "engines": {"node": ">=14"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesDateFnsTzDevelopmentDependencyButNotItsBroadPeerRange() {
        // Reduced from marnusw/date-fns-tz v1.3.3 at ab13900b; its v2-oriented source uses
        // date-fns internals, so the companion package must be migrated independently.
        // https://github.com/marnusw/date-fns-tz/blob/ab13900b2994d9c6fdaf29b86e70355b9037664d/package.json
        // https://github.com/marnusw/date-fns-tz/blob/ab13900b2994d9c6fdaf29b86e70355b9037664d/src/toDate/index.js
        rewriteRun(
                json(
                        """
                        {
                          "name": "date-fns-tz",
                          "peerDependencies": {"date-fns": ">=2.0.0"},
                          "devDependencies": {
                            "date-fns": "^2.23.0",
                            "typescript": "^4.1.3"
                          }
                        }
                        """,
                        """
                        {
                          "name": "date-fns-tz",
                          "peerDependencies": {"date-fns": ">=2.0.0"},
                          "devDependencies": {
                            "date-fns": "4.1.0",
                            "typescript": "^4.1.3"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import toInteger from 'date-fns/_lib/toInteger/index.js'
                        import getTimezoneOffsetInMilliseconds from 'date-fns/_lib/getTimezoneOffsetInMilliseconds/index.js'
                        """,
                        spec -> spec.path("src/toDate/index.js")
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.23.0", "2.25.0", "2.28.0", "2.29.3", "2.30.0"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(json(
                "{\"dependencies\":{\"date-fns\":\"" + oldVersion + "\"}}",
                "{\"dependencies\":{\"date-fns\":\"4.1.0\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"date-fns": "2.23.0"},
                  "devDependencies": {"date-fns": "~2.25.0"},
                  "peerDependencies": {"date-fns": "^2.28.0"},
                  "optionalDependencies": {"date-fns": "2.29.3"}
                }
                """,
                """
                {
                  "dependencies": {"date-fns": "4.1.0"},
                  "devDependencies": {"date-fns": "4.1.0"},
                  "peerDependencies": {"date-fns": "4.1.0"},
                  "optionalDependencies": {"date-fns": "4.1.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesCommonCaretTildeComparatorAndVPrefixForms() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"date-fns": "v2.23.0"},
                  "devDependencies": {"date-fns": "~2.25.0"},
                  "peerDependencies": {"date-fns": ">= 2.28.0 < 3"},
                  "optionalDependencies": {"date-fns": "^2.30.0"}
                }
                """,
                """
                {
                  "dependencies": {"date-fns": "4.1.0"},
                  "devDependencies": {"date-fns": "4.1.0"},
                  "peerDependencies": {"date-fns": "4.1.0"},
                  "optionalDependencies": {"date-fns": "4.1.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesOrHyphenAndPrereleaseFormsAnchoredOnListedVersions() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"date-fns": "2.23.0 || ^2.30.0"},
                  "devDependencies": {"date-fns": "2.25.0 - 2.30.0"},
                  "peerDependencies": {"date-fns": "2.28.0-beta.1"},
                  "optionalDependencies": {"date-fns": "  >=2.29.3 <4"}
                }
                """,
                """
                {
                  "dependencies": {"date-fns": "4.1.0"},
                  "devDependencies": {"date-fns": "4.1.0"},
                  "peerDependencies": {"date-fns": "4.1.0"},
                  "optionalDependencies": {"date-fns": "4.1.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesNestedWorkspaceManifest() {
        rewriteRun(json(
                """
                {
                  "name": "@example/scheduler",
                  "dependencies": {"date-fns": "^2.30.0", "react": "^18.3.1"}
                }
                """,
                """
                {
                  "name": "@example/scheduler",
                  "dependencies": {"date-fns": "4.1.0", "react": "^18.3.1"}
                }
                """,
                spec -> spec.path("packages/scheduler/package.json")
        ));
    }

    @Test
    void preservesCompanionPackagesAndDateAdapters() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@date-io/date-fns": "^2.17.0",
                    "@date-fns/tz": "^1.4.1",
                    "date-fns": "2.30.0",
                    "date-fns-tz": "^1.3.8"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@date-io/date-fns": "^2.17.0",
                    "@date-fns/tz": "^1.4.1",
                    "date-fns": "4.1.0",
                    "date-fns-tz": "^1.3.8"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTargetVersionAndTargetRangeUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"date-fns": "4.1.0"},
                  "devDependencies": {"date-fns": "^4.1.0"}
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
                  "dependencies": {"date-fns": "4.1.1"},
                  "devDependencies": {"date-fns": "^4.2.0"},
                  "peerDependencies": {"date-fns": ">=5.0.0"},
                  "optionalDependencies": {"date-fns": "5.0.0-beta.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesUnlistedOldVersionsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"date-fns": "2.22.1"},
                  "devDependencies": {"date-fns": "2.24.0"},
                  "peerDependencies": {"date-fns": "2.27.0"},
                  "optionalDependencies": {"date-fns": "2.29.2"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesBroadAndUnboundedRangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"date-fns": ">=2.0.0"},
                  "devDependencies": {"date-fns": "2.x"},
                  "peerDependencies": {"date-fns": "*"},
                  "optionalDependencies": {"date-fns": "latest"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesWorkspaceAliasGitAndFileReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"date-fns": "workspace:^2.30.0"},
                  "devDependencies": {"date-fns": "npm:@example/date-fns@2.30.0"},
                  "peerDependencies": {"date-fns": "github:date-fns/date-fns#v2.30.0"},
                  "optionalDependencies": {"date-fns": "file:../date-fns"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesGitAndHttpTarballReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"date-fns": "git+https://github.com/date-fns/date-fns.git#v2.30.0"},
                  "devDependencies": {"date-fns": "https://registry.example/date-fns-2.30.0.tgz"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOverridesAndResolutionsUntouched() {
        rewriteRun(json(
                """
                {
                  "overrides": {"date-fns": "2.30.0"},
                  "resolutions": {"date-fns": "2.29.3"},
                  "pnpm": {"overrides": {"date-fns": "2.28.0"}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNonStringVersionValuesUntouched() {
        rewriteRun(json(
                """
                {
                  "devDependencies": {"date-fns": false},
                  "optionalDependencies": {"date-fns": 2.30}
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
                  "packages": {
                    "": {"dependencies": {"date-fns": "2.30.0"}},
                    "node_modules/date-fns": {"version": "2.30.0"}
                  }
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                "{\"dependencies\":{\"date-fns\":\"2.30.0\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNames() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@date-io/date-fns": "2.30.0",
                    "@example/date-fns": "2.30.0",
                    "date-fns-jalali": "2.30.0"
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

        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.datefns")
                .scanYamlResources()
                .build();
    }
}
