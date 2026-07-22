package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class AngularPlatformBrowserDynamicProjectTest implements RewriteTest {
    private static final String PROJECT =
            "com.huawei.clouds.openrewrite.angular.AuditAngularPlatformBrowserDynamic20Project";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.angular.MigrateAngularPlatformBrowserDynamicTo20_3_26";

    @ParameterizedTest(name = "marks unresolved declaration {0}")
    @MethodSource("unresolvedDeclarations")
    void marksUnresolvedDeclarationsAtTheirOwner(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "was not changed"))));
    }

    static Stream<Arguments> unresolvedDeclarations() {
        return Stream.of(">=10 <14", "10.0.14 || 13.2.6", "10.0.14 - 13.2.6", "14.0.0",
                        "^20.3.26", "workspace:^12.2.17", "npm:@company/dynamic@12.2.17",
                        "file:../dynamic", "github:angular/angular", "latest", "${ANGULAR_VERSION}")
                .map(Arguments::of);
    }

    @Test
    void marksNonStringDeclaration() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":{\"version\":\"12.2.17\"}}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "was not changed"))));
    }

    @ParameterizedTest(name = "marks mismatched Angular peer {0}")
    @MethodSource("angularPeers")
    void marksEveryRequiredLockstepAngularPeer(String dependency) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"20.3.26\",\"" + dependency + "\":\"12.2.17\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "exact same 20.3.26 patch"))));
    }

    static Stream<Arguments> angularPeers() {
        return Stream.of("@angular/common", "@angular/compiler", "@angular/core", "@angular/platform-browser")
                .map(Arguments::of);
    }

    @Test
    void marksIncompatibleToolchainAndNgcc() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"18.x\"},\"scripts\":{\"postinstall\":\"ngcc\"},\"dependencies\":{\"@angular/platform-browser-dynamic\":\"20.3.26\"},\"devDependencies\":{\"typescript\":\"4.6.4\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "requires Node");
                                    assertContains(after.printAll(), "requires TypeScript");
                                    assertContains(after.printAll(), "ngcc/View Engine");
                                })));
    }

    @Test
    void leavesAlignedPeersAndSupportedToolchainUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"^20.19.0 || ^22.12.0 || >=24.0.0\"},\"dependencies\":{\"@angular/platform-browser-dynamic\":\"20.3.26\",\"@angular/common\":\"20.3.26\",\"@angular/compiler\":\"20.3.26\",\"@angular/core\":\"20.3.26\",\"@angular/platform-browser\":\"20.3.26\"},\"devDependencies\":{\"typescript\":\"5.9.2\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void leavesMetadataLockfilesNestedLookalikesAndUnrelatedPackagesUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"overrides\":{\"@angular/platform-browser-dynamic\":\"12.2.17\"}}", source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"12.2.17\"}}}}", source -> source.path("package-lock.json")),
                json("{\"fixtures\":{\"@angular/platform-browser-dynamic\":\"12.2.17\"},\"dependencies\":{\"@angular/core\":\"12.2.17\"}}", source -> source.path("fixtures/package.json")),
                json("{\"engines\":{\"node\":\"18.x\"},\"devDependencies\":{\"typescript\":\"4.6.4\"}}", source -> source.path("services/api/package.json")));
    }

    @ParameterizedTest(name = "marks workspace/compiler risk {1}")
    @MethodSource("configurationRisks")
    void marksAotIvyBuilderAndSsrConfiguration(String path, String input, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(input, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> configurationRisks() {
        return Stream.of(
                Arguments.of("angular.json", "{\"projects\":{\"web\":{\"architect\":{\"build\":{\"options\":{\"aot\":false}}}}}}", "AOT/build optimization is disabled"),
                Arguments.of("angular.json", "{\"projects\":{\"web\":{\"architect\":{\"build\":{\"options\":{\"buildOptimizer\":false}}}}}}", "AOT/build optimization is disabled"),
                Arguments.of("angular.json", "{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@nrwl/angular:webpack-browser\"}}}}}", "Custom builder detected"),
                Arguments.of("angular.json", "{\"projects\":{\"web\":{\"architect\":{\"server\":{}}}}}", "SSR/prerender"),
                Arguments.of("workspace.json", "{\"projects\":{\"web\":{\"targets\":{\"build\":{\"options\":{\"ssr\":true}}}}}}", "SSR/prerender"),
                Arguments.of("tsconfig.app.json", "{\"angularCompilerOptions\":{\"enableIvy\":false}}", "Legacy compiler/template checking"),
                Arguments.of("tsconfig.json", "{\"angularCompilerOptions\":{\"fullTemplateTypeCheck\":false}}", "Legacy compiler/template checking"),
                Arguments.of("tsconfig.spec.json", "{\"angularCompilerOptions\":{\"strictTemplates\":false}}", "Legacy compiler/template checking")
        );
    }

    @Test
    void leavesModernAndNestedConfigurationLookalikesUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@angular/build:application\",\"options\":{\"aot\":true,\"buildOptimizer\":true}}}}}}", source -> source.path("angular.json")),
                json("{\"angularCompilerOptions\":{\"enableIvy\":true,\"strictTemplates\":true},\"fixtures\":{\"strictTemplates\":false}}", source -> source.path("tsconfig.json")));
    }

    @Test
    void recommendedRecipeUpgradesMigratesAndMarksRemainingWork() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"^12.2.17\",\"@angular/core\":\"12.2.17\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "\"@angular/platform-browser-dynamic\":\"20.3.26\"");
                                    assertContains(after.printAll(), "exact same 20.3.26 patch");
                                })),
                typescript("import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';\nplatformBrowserDynamic().bootstrapModule(AppModule);\n",
                        "import { platformBrowser } from '@angular/platform-browser';\nplatformBrowser().bootstrapModule(AppModule);\n",
                        source -> source.path("src/main.ts")));
    }

    @Test
    void recommendedRecipeIsDiscoverableValidAndIdempotent() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECOMMENDED);
        assertEquals(RECOMMENDED, recipe.getName());
        assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()));
        rewriteRun(spec -> spec.recipe(recipe).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"12.2.17\"}}",
                        "{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"20.3.26\"}}",
                        source -> source.path("package.json")));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), actual);
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources().build();
    }
}
