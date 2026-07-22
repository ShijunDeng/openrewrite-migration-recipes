package com.huawei.clouds.openrewrite.losslessjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class LosslessJsonDependencyUpgradeTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.losslessjson.UpgradeLosslessJsonTo4_0_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0.8", "2.0.11", "^2.0.8", "~2.0.11", "~2.0.8", "^2.0.11"})
    void upgradesSafeScalarSpreadsheetVersions(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"" + version + "\"}}",
                "{\"dependencies\":{\"lossless-json\":\"4.0.1\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"lossless-json": "2.0.8"},
                  "devDependencies": {"lossless-json": "2.0.11"},
                  "peerDependencies": {"lossless-json": "2.0.8"},
                  "optionalDependencies": {"lossless-json": "2.0.11"}
                }
                """,
                """
                {
                  "dependencies": {"lossless-json": "4.0.1"},
                  "devDependencies": {"lossless-json": "4.0.1"},
                  "peerDependencies": {"lossless-json": "4.0.1"},
                  "optionalDependencies": {"lossless-json": "4.0.1"}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=2.0.8", "<=2.0.11", ">=2.0.8 <3",
            "2.0.8 || 2.0.11", "2.0.8 - 2.0.11", "2.x", "2.0.x", "*",
            "v2.0.8", "=2.0.11", "2.0.8-beta.1", "2.0.11+build.7", "latest", "next"
    })
    void leavesRangesDynamicAndDecoratedVersionsUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"" + declaration + "\"}}",
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "workspace:2.0.8", "workspace:^2.0.11", "npm:@example/lossless-json@2.0.8",
            "github:josdejong/lossless-json#v2.0.11",
            "git+https://github.com/josdejong/lossless-json.git#v2.0.8",
            "file:../lossless-json", "link:../lossless-json",
            "https://registry.example/lossless-json-2.0.11.tgz"
    })
    void leavesProtocolsAliasesAndExternalReferencesUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"" + declaration + "\"}}",
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1.0.5", "2.0.7", "2.0.9", "2.0.10", "3.0.0", "3.0.2",
            "4.0.0", "4.0.1", "4.0.2", "5.0.0"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"" + version + "\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesMultipleWorkspacePackageJsonFiles() {
        rewriteRun(
                json(
                        "{\"dependencies\":{\"lossless-json\":\"2.0.8\"}}",
                        "{\"dependencies\":{\"lossless-json\":\"4.0.1\"}}",
                        source -> source.path("packages/parser/package.json")
                ),
                json(
                        "{\"devDependencies\":{\"lossless-json\":\"2.0.11\"}}",
                        "{\"devDependencies\":{\"lossless-json\":\"4.0.1\"}}",
                        source -> source.path("apps/web/package.json")
                )
        );
    }

    @Test
    void doesNotTreatNestedDependenciesAsRootDirectDependencies() {
        rewriteRun(json(
                """
                {
                  "overrides": {"example": {"dependencies": {"lossless-json": "2.0.8"}}},
                  "pnpm": {"overrides": {"lossless-json": "2.0.11"}},
                  "resolutions": {"lossless-json": "2.0.8"}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void leavesCatalogAndVariableOwnershipUntouched() {
        rewriteRun(json(
                """
                {
                  "catalog": {"lossless-json": "2.0.8"},
                  "dependencies": {"lossless-json": "$losslessJsonVersion"}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void leavesNonStringValuesUntouched() {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":false},\"devDependencies\":{\"lossless-json\":2.011}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void leavesPackageLockAndOrdinaryJsonUntouched() {
        rewriteRun(
                json(
                        "{\"packages\":{\"\":{\"dependencies\":{\"lossless-json\":\"2.0.11\"}}}}",
                        source -> source.path("package-lock.json")
                ),
                json(
                        "{\"dependencies\":{\"lossless-json\":\"2.0.8\"}}",
                        source -> source.path("fixtures/dependencies.json")
                )
        );
    }

    @Test
    void leavesSimilarPackageNamesUntouched() {
        rewriteRun(json(
                """
                {"dependencies":{"@types/lossless-json":"2.0.8","lossless-json2":"2.0.11","@scope/lossless-json":"2.0.8"}}
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void strictUpgradeIsIdempotentInOneChangingCycle() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json(
                        "{\"dependencies\":{\"lossless-json\":\"2.0.8\"}}",
                        "{\"dependencies\":{\"lossless-json\":\"4.0.1\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void recipeIsDiscoverableAndValid() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.losslessjson")
                .scanYamlResources()
                .build();
    }
}
