package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class AngularPlatformBrowserProjectMigrationTest implements RewriteTest {
    private static final String PROJECT =
            "com.huawei.clouds.openrewrite.angular.AuditAngularPlatformBrowser20Project";
    private static final String TEMPLATE =
            "com.huawei.clouds.openrewrite.angular.AuditAngularPlatformBrowser20Templates";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.angular.MigrateAngularPlatformBrowserTo20_3_26";

    @ParameterizedTest(name = "marks unresolved declaration {0}")
    @MethodSource("unresolvedDeclarations")
    void marksEveryUnresolvedPlatformBrowserDeclaration(String declaration, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/platform-browser\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), message)))
        );
    }

    static Stream<Arguments> unresolvedDeclarations() {
        return Stream.of(
                Arguments.of(">=10 <14", "Complex @angular/platform-browser range"),
                Arguments.of("10.0.14 || 12.2.17", "Complex @angular/platform-browser range"),
                Arguments.of("10.0.14 - 13.2.6", "Complex @angular/platform-browser range"),
                Arguments.of("v12.2.17", "Complex @angular/platform-browser range"),
                Arguments.of("=12.2.17", "Complex @angular/platform-browser range"),
                Arguments.of("workspace:^", "Protocol, alias, tag or dynamic"),
                Arguments.of("npm:@angular/platform-browser@12.2.17", "Protocol, alias, tag or dynamic"),
                Arguments.of("file:../platform-browser", "Protocol, alias, tag or dynamic"),
                Arguments.of("github:angular/angular", "Protocol, alias, tag or dynamic"),
                Arguments.of("latest", "Protocol, alias, tag or dynamic"),
                Arguments.of("next", "Protocol, alias, tag or dynamic"),
                Arguments.of("${angularVersion}", "Protocol, alias, tag or dynamic"),
                Arguments.of("catalog:", "Protocol, alias, tag or dynamic")
        );
    }

    @Test
    void marksNonStringPlatformBrowserDeclaration() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/platform-browser\":true}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "Non-string @angular/platform-browser declaration")))
        );
    }

    @ParameterizedTest(name = "marks lockstep package {0}")
    @MethodSource("lockstepPackages")
    void marksEveryAngularPackageThatMustMoveInLockstep(String dependency, String expected) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/platform-browser\":\"20.3.26\",\"" + dependency + "\":\"12.2.17\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), expected)))
        );
    }

    static Stream<Arguments> lockstepPackages() {
        String lockstep = "must be upgraded in lockstep to 20.3.26";
        return Stream.of(
                Arguments.of("@angular/animations", lockstep),
                Arguments.of("@angular/common", lockstep),
                Arguments.of("@angular/compiler", lockstep),
                Arguments.of("@angular/compiler-cli", lockstep),
                Arguments.of("@angular/core", lockstep),
                Arguments.of("@angular/elements", lockstep),
                Arguments.of("@angular/forms", lockstep),
                Arguments.of("@angular/language-service", lockstep),
                Arguments.of("@angular/localize", lockstep),
                Arguments.of("@angular/platform-browser-dynamic", "platform-browser-dynamic is deprecated"),
                Arguments.of("@angular/platform-server", lockstep),
                Arguments.of("@angular/router", lockstep),
                Arguments.of("@angular/service-worker", lockstep),
                Arguments.of("@angular/upgrade", lockstep)
        );
    }

    @ParameterizedTest(name = "marks tool/runtime dependency {0}")
    @MethodSource("toolAndRuntimeDependencies")
    void marksToolchainAndRuntimeDependencies(String dependency, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/platform-browser\":\"20.3.26\",\"" + dependency + "\":\"1.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), message)))
        );
    }

    static Stream<Arguments> toolAndRuntimeDependencies() {
        String tooling = "Align Angular CLI/build/schematics tooling";
        return Stream.of(
                Arguments.of("@angular/cli", tooling),
                Arguments.of("@angular-devkit/build-angular", tooling),
                Arguments.of("@angular-devkit/core", tooling),
                Arguments.of("@angular-devkit/schematics", tooling),
                Arguments.of("@schematics/angular", tooling),
                Arguments.of("typescript", "requires TypeScript >=5.8 and <6.0"),
                Arguments.of("zone.js", "Choose zoned or zoneless operation deliberately"),
                Arguments.of("rxjs", "Angular 20-compatible RxJS line"),
                Arguments.of("hammerjs", "deprecates built-in HammerJS integration")
        );
    }

    @Test
    void marksNodeEngineAndNgccCommandAtTheirMembers() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"18.x\"},\"scripts\":{\"postinstall\":\"ngcc\"}," +
                     "\"dependencies\":{\"@angular/platform-browser\":\"20.3.26\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "requires Node ^20.19.0");
                                    assertContains(after.printAll(), "has no ngcc");
                                }))
        );
    }

    @ParameterizedTest(name = "marks workspace option {0}")
    @MethodSource("workspaceOptions")
    void marksWorkspaceBuilderAndSsrOptions(String input, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(input, source -> source.path("angular.json").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message)))
        );
    }

    static Stream<Arguments> workspaceOptions() {
        return Stream.of(
                Arguments.of("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@nrwl/angular:webpack-browser\"}}}}}", "Custom builder detected"),
                Arguments.of("{\"projects\":{\"web\":{\"architect\":{\"serve\":{\"options\":{\"browserTarget\":\"web:build\"}}}}}}", "Legacy browserTarget option changed"),
                Arguments.of("{\"projects\":{\"web\":{\"architect\":{\"server\":{}}}}}", "SSR/prerender target"),
                Arguments.of("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"options\":{\"ssr\":true}}}}}}", "SSR/prerender target"),
                Arguments.of("{\"projects\":{\"web\":{\"architect\":{\"prerender\":{}}}}}", "SSR/prerender target"),
                Arguments.of("{\"defaultProject\":\"web\",\"projects\":{\"web\":{}}}", "Legacy defaultProject behavior changed")
        );
    }

    @ParameterizedTest(name = "marks compiler option {0}")
    @MethodSource("compilerOptions")
    void marksCompilerCompatibilityOptions(String property, String value, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"angularCompilerOptions\":{\"" + property + "\":" + value + "}}",
                        source -> source.path("tsconfig.app.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), message)))
        );
    }

    static Stream<Arguments> compilerOptions() {
        return Stream.of(
                Arguments.of("enableIvy", "false", "Angular 20 is Ivy-only"),
                Arguments.of("fullTemplateTypeCheck", "true", "superseded by strictTemplates"),
                Arguments.of("strictTemplates", "false", "Strict template checking is disabled"),
                Arguments.of("compilationMode", "\"partial\"", "full versus partial Ivy compilation"),
                Arguments.of("moduleResolution", "\"node\"", "module emit or resolution"),
                Arguments.of("target", "\"es2017\"", "module emit or resolution"),
                Arguments.of("module", "\"esnext\"", "module emit or resolution")
        );
    }

    @ParameterizedTest(name = "marks template construct {0}")
    @MethodSource("templateRisks")
    void marksTemplateRisksAtPreciseSnippets(String input, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(input, source -> source.path("src/app.component.html").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message)))
        );
    }

    static Stream<Arguments> templateRisks() {
        return Stream.of(
                Arguments.of("<app-shell ngSkipHydration />", "ngSkipHydration disables reconciliation"),
                Arguments.of("<div ng-reflect-value=\"debug\"></div>", "no longer emits ng-reflect attributes"),
                Arguments.of("<section (swipeleft)=\"next()\"></section>", "deprecated HammerJS integration"),
                Arguments.of("<a href=\"javascript:openLegacy()\">open</a>", "Unsafe javascript URL")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!-- <div ngSkipHydration (swipe)=\"go()\"></div> -->",
            "<main><a href=\"/docs\">Docs</a></main>"
    })
    void leavesCommentsAndSafeHtmlUnchanged(String input) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(input, source -> source.path("src/safe.html")));
    }

    @Test
    void marksUnlistedSimpleDeclarationsButLeavesUnrelatedManifestsUnmarked() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/platform-browser\":\"14.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "Unlisted @angular/platform-browser scalar"))),
                json("{\"engines\":{\"node\":\"18.x\"},\"dependencies\":{\"typescript\":\"4.3.5\"}}",
                        source -> source.path("services/api/package.json")),
                json("{\"fixtures\":{\"@angular/platform-browser\":\"12.2.17\"},\"dependencies\":{\"@angular/core\":\"12.2.17\"}}",
                        source -> source.path("fixtures/package.json"))
        );
    }

    @Test
    void leavesModernWorkspaceAndCompilerOptionsUnmarked() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@angular/build:application\"}}}}}",
                        source -> source.path("angular.json")),
                json("{\"angularCompilerOptions\":{\"enableIvy\":true,\"strictTemplates\":true}}",
                        source -> source.path("tsconfig.json")),
                json("{\"engines\":{\"node\":\"^20.19.0 || ^22.12.0 || >=24.0.0\"},\"dependencies\":{\"@angular/platform-browser\":\"20.3.26\",\"rxjs\":\"^7.8.2\",\"zone.js\":\"~0.15.1\"},\"devDependencies\":{\"typescript\":\"5.9.2\",\"@angular/cli\":\"20.3.6\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void recommendedRecipeCombinesAutomaticChangesAndProjectMarkers() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json(
                        "{\"dependencies\":{\"@angular/platform-browser\":\"12.2.17\",\"@angular/core\":\"12.2.17\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "\"@angular/platform-browser\":\"20.3.26\"");
                                    assertContains(after.printAll(), "must be upgraded in lockstep");
                                })),
                json("{\"angularCompilerOptions\":{\"enableIvy\":true}}",
                        "{\"angularCompilerOptions\":{}}",
                        source -> source.path("tsconfig.json")),
                text("<main ngSkipHydration></main>", source -> source.path("src/app.html").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), "ngSkipHydration disables reconciliation")))
        );
    }

    @Test
    void exposesAllPublicRecipesThroughRuntimeDiscovery() {
        Environment environment = environment();
        for (String recipeName : new String[]{PROJECT, TEMPLATE, RECOMMENDED,
                "com.huawei.clouds.openrewrite.angular.UpgradeAngularPlatformBrowserTo20_3_26",
                "com.huawei.clouds.openrewrite.angular.MigrateDeterministicPlatformBrowserTo20",
                "com.huawei.clouds.openrewrite.angular.AuditAngularPlatformBrowser20Source"}) {
            Recipe recipe = environment.activateRecipes(recipeName);
            assertNotNull(recipe);
            assertTrue(recipe.getRecipeList().size() > 0, recipeName);
        }
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), actual);
    }
}
