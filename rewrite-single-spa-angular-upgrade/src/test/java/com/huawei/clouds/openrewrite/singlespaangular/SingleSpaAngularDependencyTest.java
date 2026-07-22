package com.huawei.clouds.openrewrite.singlespaangular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class SingleSpaAngularDependencyTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.singlespaangular.UpgradeSingleSpaAngularTo9_2_0";
    static final String MIGRATE = "com.huawei.clouds.openrewrite.singlespaangular.MigrateSingleSpaAngularTo9_2_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "exact workbook source {0}")
    @ValueSource(strings = {"4.3.1", "4.9.2", "5.0.2", "6.3.1", "7.1.0", "8.1.0"})
    void upgradesEveryExactWorkbookSource(String version) {
        assertUpgrade(version, "9.2.0");
    }

    @ParameterizedTest(name = "caret workbook source {0}")
    @ValueSource(strings = {"4.3.1", "4.9.2", "5.0.2", "6.3.1", "7.1.0", "8.1.0"})
    void upgradesEveryCaretWorkbookSource(String version) {
        assertUpgrade("^" + version, "^9.2.0");
    }

    @ParameterizedTest(name = "tilde workbook source {0}")
    @ValueSource(strings = {"4.3.1", "4.9.2", "5.0.2", "6.3.1", "7.1.0", "8.1.0"})
    void upgradesEveryTildeWorkbookSource(String version) {
        assertUpgrade("~" + version, "~9.2.0");
    }

    @ParameterizedTest(name = "complex range is NOOP {0}")
    @ValueSource(strings = {
            ">=4.3.1", ">=5.0.2 <9", "<=8.1.0", "4.3.1 || 8.1.0", "4.9.2 - 7.1.0",
            "4.x", "6.3.x", "*", ">7.1.0", "<9.2.0", "^5.0.2 || ^8.1.0"
    })
    void leavesComplexRangesUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "protocol/fork is NOOP {0}")
    @ValueSource(strings = {
            "workspace:4.3.1", "workspace:^8.1.0", "npm:@company/single-spa-angular@7.1.0",
            "github:single-spa/single-spa-angular#6.3.1",
            "git+https://github.com/single-spa/single-spa-angular.git#5.0.2",
            "file:../single-spa-angular", "link:../single-spa-angular",
            "https://example.test/single-spa-angular-8.1.0.tgz"
    })
    void leavesProtocolsAliasesAndForksUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "unlisted/decorated is NOOP {0}")
    @ValueSource(strings = {
            "4.3.0", "4.3.2", "4.9.1", "4.9.3", "5.0.1", "5.0.3", "6.3.0", "6.3.2",
            "7.0.0", "7.1.1", "8.0.0", "8.1.1", "9.0.0", "9.2.0", "^9.2.0", "~9.2.0",
            "10.0.0", "v7.1.0", "=8.1.0", "8.1.0-beta.1", "latest", "next", ""
    })
    void leavesUnlistedTargetsAndDecoratedVersionsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void upgradesAllDirectSectionsAndPreservesOperator() {
        rewriteRun(json(
                "{\"dependencies\":{\"single-spa-angular\":\"4.3.1\"},\"devDependencies\":{\"single-spa-angular\":\"^4.9.2\"},\"peerDependencies\":{\"single-spa-angular\":\"~5.0.2\"},\"optionalDependencies\":{\"single-spa-angular\":\"8.1.0\"}}",
                "{\"dependencies\":{\"single-spa-angular\":\"9.2.0\"},\"devDependencies\":{\"single-spa-angular\":\"^9.2.0\"},\"peerDependencies\":{\"single-spa-angular\":\"~9.2.0\"},\"optionalDependencies\":{\"single-spa-angular\":\"9.2.0\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void ignoresOverridesResolutionsCatalogNestedAndLockfile() {
        rewriteRun(
                json("{\"overrides\":{\"single-spa-angular\":\"4.3.1\"},\"resolutions\":{\"single-spa-angular\":\"4.9.2\"},\"pnpm\":{\"overrides\":{\"single-spa-angular\":\"5.0.2\"}},\"catalog\":{\"single-spa-angular\":\"6.3.1\"}}", source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"single-spa-angular\":\"7.1.0\"}}}}", source -> source.path("package-lock.json")));
    }

    @Test
    void ignoresLookalikesNonStringsAndOtherJson() {
        rewriteRun(
                json("{\"dependencies\":{\"single-spa-angular-extra\":\"4.3.1\",\"Single-Spa-Angular\":\"5.0.2\",\"single-spa-angular\":false}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"single-spa-angular\":\"6.3.1\"}}", source -> source.path("fixture.json")));
    }

    @Test
    void nestedWorkspacePackagesUpgradeButGeneratedParentsDoNot() {
        rewriteRun(
                json("{\"dependencies\":{\"single-spa-angular\":\"^7.1.0\"}}", "{\"dependencies\":{\"single-spa-angular\":\"^9.2.0\"}}", source -> source.path("apps/car/package.json")),
                json("{\"dependencies\":{\"single-spa-angular\":\"8.1.0\"}}", source -> source.path("generated-fixtures/package.json")),
                json("{\"dependencies\":{\"single-spa-angular\":\"8.1.0\"}}", source -> source.path("GeneratedClient/package.json")),
                json("{\"dependencies\":{\"single-spa-angular\":\"8.1.0\"}}", source -> source.path("installation/package.json")),
                json("{\"dependencies\":{\"single-spa-angular\":\"8.1.0\"}}", source -> source.path("install-cache/package.json")));
    }

    @Test
    void realPuzzlefactoryAndKarmaRepositoryManifestShapesMigrate() {
        rewriteRun(
                // Puzzlefactory/single-spa-cs@4cc2973d574bc1c52a078e8e163f40508101438a
                json("{\"dependencies\":{\"@angular/core\":\"^15.2.0\",\"single-spa\":\">=4.0.0\",\"single-spa-angular\":\"^8.1.0\",\"rxjs\":\"~7.8.0\",\"tslib\":\"^2.3.0\",\"zone.js\":\"~0.12.0\"},\"devDependencies\":{\"@angular-builders/custom-webpack\":\"15.0.0\",\"style-loader\":\"^3.3.1\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"^15.2.0\",\"single-spa\":\">=4.0.0\",\"single-spa-angular\":\"^9.2.0\",\"rxjs\":\"~7.8.0\",\"tslib\":\"^2.3.0\",\"zone.js\":\"~0.12.0\"},\"devDependencies\":{\"@angular-builders/custom-webpack\":\"15.0.0\",\"style-loader\":\"^3.3.1\"}}",
                        source -> source.path("fixtures/puzzlefactory/vehicles/package.json")),
                // OriolInvernonLlaneza/karma-webpack-error-example@13e1b9f9da1497d2a96f03e8bf5fa56df49d4df4
                json("{\"dependencies\":{\"@angular/core\":\"~12.1.3\",\"single-spa\":\"~5.9.3\",\"single-spa-angular\":\"~5.0.2\",\"zone.js\":\"~0.11.4\"},\"devDependencies\":{\"@angular-builders/custom-webpack\":\"12.1.1\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"~12.1.3\",\"single-spa\":\"~5.9.3\",\"single-spa-angular\":\"~9.2.0\",\"zone.js\":\"~0.11.4\"},\"devDependencies\":{\"@angular-builders/custom-webpack\":\"12.1.1\"}}",
                        source -> source.path("fixtures/karma/package.json")));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"single-spa-angular\":\"^6.3.1\"}}", "{\"dependencies\":{\"single-spa-angular\":\"^9.2.0\"}}", source -> source.path("package.json")));
    }

    @Test
    void recommendedRecipeAppliesAutoBeforePreciseReviewMarkers() {
        Recipe migrate = environment().activateRecipes(MIGRATE);
        rewriteRun(spec -> spec.recipe(migrate),
                json("{\"dependencies\":{\"single-spa-angular\":\"^8.1.0\",\"@angular/core\":\"^15.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("^9.2.0"), after.printAll());
                            assertTrue(after.printAll().contains("one aligned framework set"), after.printAll());
                        })),
                json("{\"projects\":{\"app\":{\"targets\":{\"build\":{\"builder\":\"@angular-devkit/build-angular:application\",\"options\":{\"browser\":\"src/main.single-spa.ts\",\"customWebpackConfig\":{\"path\":\"webpack.js\"}}}}}}}",
                        source -> source.path("angular.json").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("@angular-devkit/build-angular:browser"), after.printAll());
                            assertTrue(after.printAll().contains("\"main\""), after.printAll());
                            assertTrue(after.printAll().contains("Review this build/deployment owner"), after.printAll());
                        })),
                org.openrewrite.javascript.Assertions.typescript(
                        "import { singleSpaAngular } from 'single-spa-angular/src/public_api';\nconst lifecycle = singleSpaAngular({ bootstrapFunction: () => app });",
                        source -> source.path("src/main.single-spa.ts").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("from 'single-spa-angular'"), after.printAll());
                            assertTrue(after.printAll().contains("Review singleSpaAngular lifecycle options"), after.printAll());
                        })));
    }

    @Test
    void recipesAreDiscoverableAndValid() {
        Environment environment = environment();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> UPGRADE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATE.equals(recipe.getName())));
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
    }

    private void assertUpgrade(String before, String after) {
        rewriteRun(json("{\"dependencies\":{\"single-spa-angular\":\"" + before + "\"}}",
                "{\"dependencies\":{\"single-spa-angular\":\"" + after + "\"}}", source -> source.path("package.json")));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"single-spa-angular\":\"" + declaration + "\"}}", source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.singlespaangular")
                .scanYamlResources().build();
    }
}
