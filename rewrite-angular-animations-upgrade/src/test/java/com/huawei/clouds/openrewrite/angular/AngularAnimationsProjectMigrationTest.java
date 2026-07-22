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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class AngularAnimationsProjectMigrationTest implements RewriteTest {
    private static final String PROJECT =
            "com.huawei.clouds.openrewrite.angular.AuditAngularAnimations20Project";
    private static final String TEMPLATE =
            "com.huawei.clouds.openrewrite.angular.AuditAngularAnimations20TemplatesAndStyles";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.angular.MigrateAngularAnimationsTo20_3_26";

    @ParameterizedTest(name = "marks unresolved declaration {0}")
    @MethodSource("unresolvedDeclarations")
    void marksEveryUnresolvedAnimationsDeclaration(String declaration, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/animations\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), message)))
        );
    }

    static Stream<Arguments> unresolvedDeclarations() {
        return Stream.of(
                Arguments.of(">=10 <14", "Complex @angular/animations range"),
                Arguments.of("10.0.14 || 12.2.17", "Complex @angular/animations range"),
                Arguments.of("10.0.14 - 13.2.6", "Complex @angular/animations range"),
                Arguments.of("v12.2.17", "Complex @angular/animations range"),
                Arguments.of("=12.2.17", "Complex @angular/animations range"),
                Arguments.of("workspace:^", "Protocol, alias, tag or dynamic"),
                Arguments.of("npm:@angular/animations@12.2.17", "Protocol, alias, tag or dynamic"),
                Arguments.of("file:../animations", "Protocol, alias, tag or dynamic"),
                Arguments.of("github:angular/angular", "Protocol, alias, tag or dynamic"),
                Arguments.of("latest", "Protocol, alias, tag or dynamic"),
                Arguments.of("next", "Protocol, alias, tag or dynamic"),
                Arguments.of("${angularVersion}", "Protocol, alias, tag or dynamic"),
                Arguments.of("catalog:", "Protocol, alias, tag or dynamic")
        );
    }

    @Test
    void marksNonStringAnimationsDeclaration() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/animations\":true}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "Non-string @angular/animations declaration")))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.2.5", "14.0.0"})
    void marksUnlistedOrNotYetUpgradedScalarDeclarations(String version) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/animations\":\"" + version + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "was not changed")))
        );
    }

    @Test
    void marksTargetTransitionForPlannedRemoval() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/animations\":\"20.3.26\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(),
                                        "deprecated since Angular 20.2 and intended for removal in v23"))));
    }

    @ParameterizedTest(name = "marks lockstep package {0}")
    @ValueSource(strings = {
            "@angular/common", "@angular/compiler", "@angular/compiler-cli", "@angular/core",
            "@angular/elements", "@angular/forms", "@angular/language-service", "@angular/localize",
            "@angular/platform-browser", "@angular/platform-browser-dynamic", "@angular/platform-server",
            "@angular/router", "@angular/service-worker", "@angular/upgrade"
    })
    void marksEveryAngularPackageThatMustMoveInLockstep(String dependency) {
        assertPackageDependencyMarker(dependency, "12.2.17", "must be upgraded in lockstep to 20.3.26");
    }

    @ParameterizedTest(name = "marks Angular tool {0}")
    @ValueSource(strings = {
            "@angular/cli", "@angular-devkit/build-angular", "@angular-devkit/core",
            "@angular-devkit/schematics", "@schematics/angular"
    })
    void marksAngularBuildAndMigrationTooling(String dependency) {
        assertPackageDependencyMarker(dependency, "12.2.17", "Align Angular CLI/build/schematics tooling");
    }

    @ParameterizedTest(name = "marks third-party consumer {0}")
    @ValueSource(strings = {
            "@angular/material", "@angular/cdk", "primeng", "ngx-bootstrap", "ngx-toastr",
            "@ng-bootstrap/ng-bootstrap", "ng-zorro-antd", "@swimlane/ngx-charts"
    })
    void marksThirdPartyPackagesThatMayStillRequireLegacyRenderer(String dependency) {
        assertPackageDependencyMarker(dependency, "1.0.0", "Third-party UI package may still require");
    }

    @ParameterizedTest(name = "marks runtime/tool {0}")
    @MethodSource("runtimeDependencies")
    void marksCompilerZoneAndPolyfillConstraints(String dependency, String message) {
        assertPackageDependencyMarker(dependency, "1.0.0", message);
    }

    static Stream<Arguments> runtimeDependencies() {
        return Stream.of(
                Arguments.of("typescript", "requires TypeScript >=5.8 and <6.0"),
                Arguments.of("zone.js", "timing differs in zoned/zoneless applications"),
                Arguments.of("web-animations-js", "Legacy Web Animations polyfill")
        );
    }

    @Test
    void marksNodeEngineWhenAnimationsPackageIsPresent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"18.x\"},\"dependencies\":{\"@angular/animations\":\"20.3.26\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "requires Node ^20.19.0")))
        );
    }

    @Test
    void leavesSupportedToolchainDeclarationsWithoutToolchainNoise() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"^20.19.0 || ^22.12.0 || >=24.0.0\"},\"dependencies\":{\"@angular/animations\":\"20.3.26\",\"@angular/cli\":\"20.3.1\"},\"devDependencies\":{\"typescript\":\"5.9.2\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertFalse(printed.contains("requires Node"), printed);
                            assertFalse(printed.contains("requires TypeScript"), printed);
                            assertFalse(printed.contains("Align Angular CLI"), printed);
                        })));
    }

    @Test
    void marksWorkspaceWebAnimationsPolyfill() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"options\":{\"polyfills\":[\"zone.js\",\"web-animations-js\"]}}}}}}",
                        source -> source.path("angular.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "Workspace still loads web-animations-js")))
        );
    }

    @Test
    void marksCustomBuilderCssPipeline() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@nrwl/angular:webpack-browser\"}}}}}",
                        source -> source.path("workspace.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "Custom builder controls CSS processing")))
        );
    }

    @ParameterizedTest(name = "marks workspace SSR key {0}")
    @ValueSource(strings = {"server", "ssr", "prerender"})
    void marksEveryServerRenderingTarget(String key) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"projects\":{\"web\":{\"architect\":{\"" + key + "\":{}}}}}",
                        source -> source.path("angular.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "SSR/prerender does not run browser animations")))
        );
    }

    @Test
    void leavesUnrelatedManifestUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"18.x\"},\"dependencies\":{\"typescript\":\"4.3.5\",\"zone.js\":\"0.11.4\"}}",
                        source -> source.path("services/api/package.json"))
        );
    }

    @Test
    void leavesOverridesAndMetadataAlone() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"overrides\":{\"@angular/animations\":\"10.2.5\"},\"metadata\":{\"@angular/animations\":\"12.2.17\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void leavesModernAngularBuilderAndUnrelatedPolyfillsAlone() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@angular/build:application\",\"options\":{\"polyfills\":[\"zone.js\"]}}}}}}",
                        source -> source.path("angular.json"))
        );
    }

    @ParameterizedTest(name = "marks template construct {0}")
    @MethodSource("templateRisks")
    void marksLegacyAndNativeTemplateLifecycleBoundaries(String input, String message) {
        assertTextMarker(input, "src/app.component.html", message);
    }

    static Stream<Arguments> templateRisks() {
        return Stream.of(
                Arguments.of("<section [@fade]=\"state\"></section>", "Legacy animation trigger binding"),
                Arguments.of("<section @fade=\"state\"></section>", "Legacy animation trigger binding"),
                Arguments.of("<section (@fade.done)=\"saved()\"></section>", "Legacy animation callback depends"),
                Arguments.of("<main [@.disabled]=\"reduceMotion\"></main>", "Legacy @.disabled inheritance"),
                Arguments.of("<div animate.enter=\"fade-in\"></div>", "Native animate.enter/leave class binding"),
                Arguments.of("<div [animate.leave]=\"leaveClass\"></div>", "Native animate.enter/leave class binding"),
                Arguments.of("<div (animate.leave)=\"leave($event)\"></div>", "Native animate callback must call animationComplete")
        );
    }

    @ParameterizedTest(name = "marks style construct {0}")
    @MethodSource("styleRisks")
    void marksNativeCssAnimationLifecycleBoundaries(String input, String path, String message) {
        assertTextMarker(input, path, message);
    }

    static Stream<Arguments> styleRisks() {
        return Stream.of(
                Arguments.of("@starting-style { .fade { opacity: 0; } }", "src/fade.css", "@starting-style defines transition entry state"),
                Arguments.of("@keyframes fade-in { from {opacity: 0} to {opacity: 1} }", "src/fade.scss", "CSS keyframes replacement"),
                Arguments.of("@media (prefers-reduced-motion: reduce) { * {animation: none} }", "src/a11y.css", "Reduced-motion media handling"),
                Arguments.of(".enter { animation-duration: 200ms; }", "src/enter.less", "Native CSS animation timing"),
                Arguments.of(".enter { transition: opacity 200ms; }", "src/enter.sass", "Native CSS transition needs"),
                Arguments.of("::view-transition-old(root) { animation: fade 100ms; }", "src/route.css", "View Transition pseudo-element animation")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!-- <div [@fade] (@fade.done)=\"done()\" animate.enter=\"x\"></div> -->",
            "<main><p>@if is Angular control flow text, not an animation binding.</p></main>"
    })
    void leavesHtmlCommentsAndNonBindingsUnchanged(String input) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(input, source -> source.path("src/safe.html")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/* @keyframes ignored {} .x { animation: none; } */",
            "// .x { transition: opacity 1s; }\n.safe { color: red; }"
    })
    void leavesStyleCommentsUnchanged(String input) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(input, source -> source.path("src/safe.scss")));
    }

    @Test
    void leavesAnimationTextInNonTemplateAndNonStyleFilesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text("const example = '<div [@fade] animate.enter>';\n",
                        source -> source.path("src/example.ts")),
                text("[@fade] animation: transition;", source -> source.path("docs/notes.md"))
        );
    }

    @Test
    void templateAndStyleMarkersRemainIdempotentAcrossCycles() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(TEMPLATE)).cycles(2),
                text("<div [@fade] animate.enter=\"fade\"></div>",
                        source -> source.path("src/idempotent.html").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "Legacy animation trigger binding");
                                    assertContains(after.printAll(), "Native animate.enter/leave class binding");
                                }))
        );
    }

    @Test
    void recommendedRecipeCombinesStrictUpgradeAutomaticMigrationAndMarkers() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"@angular/animations\":\"12.2.17\",\"@angular/core\":\"12.2.17\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "\"@angular/animations\":\"20.3.26\"");
                                    assertContains(after.printAll(), "deprecated since Angular 20.2");
                                    assertContains(after.printAll(), "must be upgraded in lockstep");
                                })),
                text("<main animate.enter=\"fade-in\"></main>",
                        source -> source.path("src/app.html").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "Native animate.enter/leave class binding")))
        );
    }

    @Test
    void exposesAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{PROJECT, TEMPLATE, RECOMMENDED,
                "com.huawei.clouds.openrewrite.angular.UpgradeAngularAnimationsTo20_3_26",
                "com.huawei.clouds.openrewrite.angular.MigrateDeterministicAngularAnimationsTo20",
                "com.huawei.clouds.openrewrite.angular.AuditAngularAnimations20Source"}) {
            Recipe recipe = environment.activateRecipes(name);
            assertNotNull(recipe);
            assertTrue(recipe.getRecipeList().size() > 0, name);
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()), name);
        }
    }

    private void assertPackageDependencyMarker(String dependency, String version, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/animations\":\"20.3.26\",\"" + dependency + "\":\"" + version + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), message)))
        );
    }

    private void assertTextMarker(String before, String path, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(before, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message)))
        );
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
