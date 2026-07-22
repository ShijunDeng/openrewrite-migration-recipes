package com.huawei.clouds.openrewrite.ngxecharts;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class NgxEchartsRiskMarkerTest implements RewriteTest {
    private static final String SOURCE =
            "com.huawei.clouds.openrewrite.ngxecharts.AuditNgxEcharts20Source";
    private static final String PROJECT =
            "com.huawei.clouds.openrewrite.ngxecharts.AuditNgxEcharts20Project";
    private static final String TEMPLATE =
            "com.huawei.clouds.openrewrite.ngxecharts.AuditNgxEcharts20Templates";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.ngxecharts.MigrateNgxEchartsTo20_0_2";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "marks source boundary {2}")
    @MethodSource("sourceRisks")
    void marksRemovedAndBuildSensitiveSource(String input, String path, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(input, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> sourceRisks() {
        return Stream.of(
                Arguments.of("import { provideEcharts } from 'ngx-echarts';\nconst providers = [provideEcharts()];\n",
                        "src/app.config.ts", "provideEcharts was removed"),
                Arguments.of("import { NgxEchartsCoreModule } from 'ngx-echarts';\n",
                        "src/app.module.ts", "absent from the target API"),
                Arguments.of("import { NgxEchartsService } from 'ngx-echarts';\n",
                        "src/chart.service.ts", "absent from the target API"),
                Arguments.of("import { InternalThing } from 'ngx-echarts/lib/internal';\n",
                        "src/internal.ts", "Unsupported ngx-echarts deep entry point"),
                Arguments.of("import * as echarts from 'echarts';\n",
                        "src/app.module.ts", "index.js full entry"),
                Arguments.of("import * as echarts from 'echarts/index.js';\n",
                        "src/index.ts", "index.js full entry"),
                Arguments.of("import * as echarts from 'echarts/core';\n",
                        "src/custom.ts", "at least CanvasRenderer or SVGRenderer"),
                Arguments.of("import * as echarts from 'echarts/lib/echarts';\n",
                        "src/legacy.ts", "Legacy ECharts implementation entry point"),
                Arguments.of("import { Bar3DChart } from 'echarts-gl/charts';\n",
                        "src/three-d.ts", "ECharts GL extension registration")
        );
    }

    @Test
    void marksAliasedRemovedProviderCallAndOwnedForRoot() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { provideEcharts as chartsProvider, NgxEchartsModule as ChartsModule } from 'ngx-echarts';\n" +
                        "import * as echarts from './custom-echarts';\n" +
                        "const providers = [chartsProvider()];\n" +
                        "const imports = [ChartsModule.forRoot({ echarts })];\n",
                        source -> source.path("src/app.module.ts").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertContains(printed, "provideEcharts was removed");
                                    assertContains(printed, "Replace removed provideEcharts");
                                    assertContains(printed, "forRoot remains supported");
                                })));
    }

    @Test
    void leavesModernPackageImportAndUnownedSameNamedCallsUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript("import { NgxEchartsDirective, provideEchartsCore } from 'ngx-echarts';\n" +
                           "const local = { provideEcharts() {}, forRoot(_: unknown) {} };\n" +
                           "local.provideEcharts(); local.forRoot({});\n",
                        source -> source.path("src/modern.ts").afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~>")))));
    }

    @ParameterizedTest(name = "marks project boundary {2}")
    @MethodSource("projectRisks")
    void marksManifestCompatibilityBoundaries(String name, String declaration, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"18.20.0\"},\"dependencies\":{\"ngx-echarts\":\"20.0.2\",\"" +
                     name + "\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> projectRisks() {
        return Stream.of(
                Arguments.of("@angular/core", "16.2.0", "requires Angular core >=20"),
                Arguments.of("@angular/common", "14.2.0", "requires Angular core >=20"),
                Arguments.of("@angular/cli", "16.2.0", "Align Angular CLI/build/schematics"),
                Arguments.of("typescript", "5.7.3", "requires TypeScript >=5.8"),
                Arguments.of("echarts", "4.9.0", "requires ECharts >=5"),
                Arguments.of("echarts-gl", "2.0.9", "ECharts GL must match"),
                Arguments.of("@juggle/resize-observer", "3.4.0", "no longer an ngx-echarts peer"),
                Arguments.of("resize-observer-polyfill", "1.5.1", "no longer an ngx-echarts peer"),
                Arguments.of("@angular/ssr", "16.2.0", "defer chart creation to the browser")
        );
    }

    @Test
    void marksUnresolvedDirectDeclarationAndUnsupportedNode() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"18.20.0\"},\"dependencies\":{\"ngx-echarts\":\">=14 <20\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "Complex ngx-echarts range");
                                    assertContains(after.printAll(), "Angular 20 requires Node");
                                })));
    }

    @Test
    void marksGlobalEchartsWorkspaceScript() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"projects\":{\"app\":{\"architect\":{\"build\":{\"options\":{\"scripts\":[\"node_modules/echarts/dist/echarts.js\"]}}}}}}",
                        source -> source.path("angular.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "Global ECharts scripts"))));
    }

    @Test
    void leavesCompatibleTargetManifestAndUnrelatedPackageWithoutNoise() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"20.19.1\"},\"dependencies\":{\"ngx-echarts\":\"20.0.2\",\"@angular/core\":\"20.3.0\",\"@angular/common\":\"20.3.0\",\"echarts\":\"6.0.0\"},\"devDependencies\":{\"typescript\":\"5.9.2\",\"@angular/cli\":\"20.3.0\"}}",
                        source -> source.path("package.json").afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~>")))),
                json("{\"engines\":{\"node\":\"16.0.0\"},\"dependencies\":{\"echarts\":\"4.9.0\"}}",
                        source -> source.path("services/api/package.json").afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~>")))));
    }

    @ParameterizedTest(name = "marks template boundary {2}")
    @MethodSource("templateRisks")
    void marksTemplateCompatibilityBoundaries(String input, String path, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(input, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> templateRisks() {
        return Stream.of(
                Arguments.of("<div echarts [detectEventChanges]=\"policy\"></div>", "src/dynamic.html", "Dynamic detectEventChanges"),
                Arguments.of("<div echarts [merge]=\"delta\"></div>", "src/merge.html", "nullable/signal"),
                Arguments.of("<div echarts [theme]=\"theme\"></div>", "src/theme.html", "nullable/signal"),
                Arguments.of("<div echarts [initOpts]=\"opts\"></div>", "src/init.html", "nullable/signal"),
                Arguments.of("<div echarts (chartInit)=\"capture($event)\"></div>", "src/event.html", "event typing and lifecycle"),
                // NiceFish@4454db9074a614ec9cdf3661cc5a05273d393b11
                Arguments.of("<div echarts [options]=\"pieChart\" class=\"nf-chart\"></div>", "nicefish/chart.component.html", "stable dimensions"),
                // careydevelopmentcrm@7d6f44b88e3fcbb54673b896c2f68d48a9f58dd4
                Arguments.of("<div echarts [options]=\"options\"></div>", "carey/accounts-ranked.component.html", "stable dimensions")
        );
    }

    @Test
    void ignoresHtmlCommentsAndOrdinaryText() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text("<!-- <div echarts [merge]=\"delta\"></div> -->\n" +
                     "<p>echarts is documented here.</p>\n" +
                     "<company-chart [options]=\"options\" (chartInit)=\"capture()\"></company-chart>",
                        source -> source.path("src/safe.html").afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~>")))));
    }

    @Test
    void recommendedRecipeMigratesAndMarksFixedMatxRepositoryShapes() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                // uilibrary/matx-angular@6b16bbe0efa9c387e6d21141981fbfe01a8043e4
                json("{\"dependencies\":{\"@angular/core\":\"^14.2.0\",\"echarts\":\"^5.3.3\",\"ngx-echarts\":\"^14.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "\"ngx-echarts\":\"20.0.2\"");
                                    assertContains(after.printAll(), "requires Angular core >=20");
                                })),
                typescript("import { NgxEchartsModule } from 'ngx-echarts';\nimport * as echarts from 'echarts';\nconst imports = [NgxEchartsModule.forRoot({ echarts })];\n",
                        source -> source.path("src/assets/examples/chart/chart-examples.module.ts")
                                .after(actual -> actual).afterRecipe(after -> {
                                    assertContains(after.printAll(), "index.js full entry");
                                    assertContains(after.printAll(), "forRoot remains supported");
                                })),
                text("<div echarts [options]=\"zoomBarOptions\" style=\"height: 400px\"></div>",
                        source -> source.path("src/assets/examples/chart/echart-bar/echart-bar.component.html")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertContains(after.printAll(), "stable dimensions"))));
    }

    @Test
    void markerRecipesAreIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { provideEcharts } from 'ngx-echarts';\n",
                        source -> source.path("src/app.ts").after(actual -> actual)));
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("<div echarts [theme]=\"theme\"></div>",
                        source -> source.path("src/chart.html").after(actual -> actual)));
    }

    @Test
    void exposesAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{SOURCE, PROJECT, TEMPLATE, RECOMMENDED,
                "com.huawei.clouds.openrewrite.ngxecharts.UpgradeNgxEchartsTo20_0_2",
                "com.huawei.clouds.openrewrite.ngxecharts.MigrateDeterministicNgxEcharts20"}) {
            Recipe recipe = environment.activateRecipes(name);
            assertNotNull(recipe, name);
            assertTrue(recipe.getRecipeList().size() > 0, name);
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()), name);
        }
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxecharts")
                .scanYamlResources().build();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
