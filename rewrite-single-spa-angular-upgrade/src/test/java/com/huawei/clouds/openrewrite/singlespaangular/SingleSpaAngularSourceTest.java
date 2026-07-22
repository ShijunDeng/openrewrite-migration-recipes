package com.huawei.clouds.openrewrite.singlespaangular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class SingleSpaAngularSourceTest implements RewriteTest {
    @ParameterizedTest(name = "normalizes static entry {0}")
    @MethodSource("staticEntries")
    void normalizesKnownStaticEntries(String before, String after) {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicSingleSpaAngularSource()),
                typescript("import { singleSpaAngular } from '" + before + "';",
                        "import { singleSpaAngular } from '" + after + "';",
                        source -> source.path("src/main.single-spa.ts")));
    }

    static Stream<Arguments> staticEntries() {
        return Stream.of(
                Arguments.of("single-spa-angular/src/public_api", "single-spa-angular"),
                Arguments.of("single-spa-angular/src/single-spa-angular", "single-spa-angular"),
                Arguments.of("single-spa-angular/lib/single-spa-angular", "single-spa-angular"),
                Arguments.of("single-spa-angular/src/extra-providers", "single-spa-angular"),
                Arguments.of("single-spa-angular/src/prod-mode", "single-spa-angular"),
                Arguments.of("single-spa-angular/src/parcel-lib", "single-spa-angular/parcel"),
                Arguments.of("single-spa-angular/src/parcel-lib/index", "single-spa-angular/parcel"),
                Arguments.of("single-spa-angular/lib/webpack/index", "single-spa-angular/lib/webpack"));
    }

    @ParameterizedTest(name = "normalizes direct require {0}")
    @MethodSource("requireEntries")
    void normalizesKnownDirectLiteralRequire(String before, String after) {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicSingleSpaAngularSource()),
                javascript("const value = require(\"" + before + "\");",
                        "const value = require(\"" + after + "\");",
                        source -> source.path("src/install.js")));
    }

    static Stream<Arguments> requireEntries() {
        return Stream.of(
                Arguments.of("single-spa-angular/src/public_api", "single-spa-angular"),
                Arguments.of("single-spa-angular/src/extra-providers", "single-spa-angular"),
                Arguments.of("single-spa-angular/src/prod-mode", "single-spa-angular"),
                Arguments.of("single-spa-angular/src/parcel-lib/index", "single-spa-angular/parcel"),
                Arguments.of("single-spa-angular/lib/webpack/index", "single-spa-angular/lib/webpack"));
    }

    @ParameterizedTest(name = "public/unknown entry NOOP {0}")
    @MethodSource("untouchedEntries")
    void preservesPublicAndUnknownEntries(String module) {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicSingleSpaAngularSource()),
                typescript("import value from '" + module + "';", source -> source.path("src/app.ts")));
    }

    static Stream<Arguments> untouchedEntries() {
        return Stream.of(
                Arguments.of("single-spa-angular"),
                Arguments.of("single-spa-angular/parcel"),
                Arguments.of("single-spa-angular/elements"),
                Arguments.of("single-spa-angular/internals"),
                Arguments.of("single-spa-angular/lib/webpack"),
                Arguments.of("single-spa-angular/src/private-new-api"),
                Arguments.of("single-spa-angular-extra/src/public_api"),
                Arguments.of("@company/single-spa-angular/src/public_api"));
    }

    @Test
    void ignoresDynamicRequireAndExcludedParentsButProcessesInstallLeaf() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicSingleSpaAngularSource()),
                javascript("const value = require(name);", source -> source.path("src/dynamic.js")),
                javascript("const value = require('single-spa-angular/src/public_api');", source -> source.path("install-cache/index.js")),
                javascript("const value = require('single-spa-angular/src/public_api');",
                        "const value = require('single-spa-angular');", source -> source.path("src/install.js")));
    }

    @Test
    void marksOwnedLifecycleCallAndNotSameNameFunction() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularSourceRisks()),
                typescript("import { singleSpaAngular as createLifecycles } from 'single-spa-angular';\nconst lifecycles = createLifecycles({ bootstrapFunction: () => app });",
                        source -> source.path("src/main.single-spa.ts").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Review singleSpaAngular lifecycle options"))),
                typescript("function singleSpaAngular(x: unknown) { return x; }\nconst value = singleSpaAngular({});",
                        source -> source.path("src/unrelated.ts").afterRecipe(after ->
                                assertFalse(after.printAll().contains("lifecycle options"), after.printAll()))));
    }

    @Test
    void marksWebpackCallAndMissingOptionsPrecisely() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularSourceRisks()),
                javascript("const adapter = require('single-spa-angular/lib/webpack').default;\nmodule.exports = (config) => adapter(config);",
                        source -> source.path("extra-webpack.config.js").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "requires both config and options"))),
                javascript("const adapter = require('single-spa-angular/lib/webpack').default;\nmodule.exports = (config, options) => adapter(config, options);",
                        source -> source.path("webpack.config.js").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Review this webpack adapter call"))));
    }

    @Test
    void marksCommonJsEsmBoundaryUnknownPhysicalAndLeavesStableWebpackRequire() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularSourceRisks()),
                javascript("const parcel = require('single-spa-angular/parcel');", source -> source.path("parcel.js")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "entries are ESM"))),
                typescript("import value from 'single-spa-angular/src/new-private';", source -> source.path("private.ts")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "unpublished or unstable"))),
                javascript("const adapter = require('single-spa-angular/lib/webpack').default;", source -> source.path("webpack.js")
                        .afterRecipe(after -> assertFalse(after.printAll().contains("entries are ESM"), after.printAll()))));
    }

    @Test
    void marksAngularEnableProdModeOnlyInOwnedFile() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularSourceRisks()),
                typescript("import { enableProdMode } from '@angular/core';\nimport { singleSpaAngular } from 'single-spa-angular';\nenableProdMode();\nconst x = singleSpaAngular({ bootstrapFunction: () => app });",
                        source -> source.path("main.single-spa.ts").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "prefer enableProdMode from single-spa-angular"))),
                typescript("import { enableProdMode } from '@angular/core';\nenableProdMode();",
                        source -> source.path("main.ts")));
    }

    @Test
    void realPuzzlefactoryLifecycleAndWebpackFixturesAreRecognized() {
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularSourceRisks()),
                // Puzzlefactory/single-spa-cs@4cc2973d574bc1c52a078e8e163f40508101438a
                typescript("""
                        import { enableProdMode, NgZone } from '@angular/core';
                        import { Router, NavigationStart } from '@angular/router';
                        import { singleSpaAngular, getSingleSpaExtraProviders } from 'single-spa-angular';
                        const lifecycles = singleSpaAngular({
                          bootstrapFunction: extraProps => platformBrowserDynamic(getSingleSpaExtraProviders()).bootstrapModule(AppModule),
                          template: '<app-root />', Router, NavigationStart, NgZone
                        });
                        export const bootstrap = lifecycles.bootstrap;
                        """, source -> source.path("fixtures/puzzlefactory/vehicles/src/main.single-spa.ts")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "lifecycle options");
                            assertContains(after.printAll(), "prefer enableProdMode");
                        })),
                javascript("const singleSpaAngularWebpack = require('single-spa-angular/lib/webpack').default;\nmodule.exports = (config, options) => singleSpaAngularWebpack(config, options);",
                        source -> source.path("fixtures/puzzlefactory/vehicles/extra-webpack.config.js")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "webpack adapter"))));
    }

    @Test
    void sourceAutoAndMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicSingleSpaAngularSource()).cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { singleSpaAngular } from 'single-spa-angular/src/public_api';",
                        "import { singleSpaAngular } from 'single-spa-angular';", source -> source.path("src/install.ts")));
        rewriteRun(spec -> spec.recipe(new FindSingleSpaAngularSourceRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { singleSpaAngular } from 'single-spa-angular';\nconst l = singleSpaAngular({ bootstrapFunction: () => app });",
                        source -> source.path("src/main.single-spa.ts").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            String message = "Review singleSpaAngular lifecycle options";
                            assertTrue(printed.indexOf(message) == printed.lastIndexOf(message), printed);
                        })));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
