package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class Angular20RiskMarkerTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksInjectFlagsAtTheImportFromRealTabbyFixture() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20TypeScriptRisks()),
                typescript(
                        """
                        import { Injectable, InjectFlags, Injector } from '@angular/core';
                        const value = injector.get(Token, [], InjectFlags.Optional);
                        """,
                        """
                        /*~~(Angular 20 removed or changed InjectFlags; apply the official migration and verify DI/runtime semantics before selecting a replacement)~~>*/import { Injectable, InjectFlags, Injector } from '@angular/core';
                        const value = injector.get(Token, [], InjectFlags.Optional);
                        """,
                        source -> source.path("tabby-ssh/src/profiles.ts")
                )
        );
    }

    @Test
    void marksEntryComponentsAtTheMetadataMemberFromRealFrappeFixture() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20TypeScriptRisks()),
                typescript(
                        """
                        import { NgModule } from '@angular/core';
                        @NgModule({
                          bootstrap: [IonicApp],
                          entryComponents: [MyApp, HomePage]
                        })
                        export class AppModule {}
                        """,
                        """
                        import { NgModule } from '@angular/core';
                        @NgModule({
                          bootstrap: [IonicApp],
                          /*~~(Ivy ignores entryComponents; remove it only after verifying dynamic component creation and all View Engine dependencies)~~>*/entryComponents: [MyApp, HomePage]
                        })
                        export class AppModule {}
                        """,
                        source -> source.path("src/app/app.module.ts")
                )
        );
    }

    @Test
    void marksMixedDocumentImportInsteadOfPartiallyRewritingIt() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20TypeScriptRisks()),
                typescript(
                        "import { CommonModule, DOCUMENT } from '@angular/common';\n",
                        "/*~~(Mixed DOCUMENT import cannot be moved without editing the remaining @angular/common bindings; split or merge imports intentionally)~~>*/import { CommonModule, DOCUMENT } from '@angular/common';\n",
                        source -> source.path("src/document.ts")
                )
        );
    }

    @Test
    void marksTimingSensitiveTestAndRouterCallsIndividually() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20TypeScriptRisks()),
                typescript(
                        """
                        import { TestBed } from '@angular/core/testing';
                        import { Router } from '@angular/router';
                        TestBed.flushEffects();
                        const navigation = router.getCurrentNavigation();
                        """,
                        """
                        import { TestBed } from '@angular/core/testing';
                        import { Router } from '@angular/router';
                        /*~~(TestBed.flushEffects was replaced by broader TestBed.tick behavior; verify pending effects, change detection, and timer ordering)~~>*/TestBed.flushEffects();
                        const navigation = /*~~(Router.getCurrentNavigation migration is optional; choose the currentNavigation signal and update reads/reactivity deliberately)~~>*/router.getCurrentNavigation();
                        """,
                        source -> source.path("src/router.spec.ts")
                ),
                typescript(
                        "OtherTestHarness.flushEffects();\nconst value = router.getCurrentNavigation();\n",
                        source -> source.path("src/unrelated.ts")
                )
        );
    }

    @Test
    void marksAfterRenderImportAndCallButNotSameNamedLocalFunction() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20TypeScriptRisks()),
                typescript(
                        "import { afterRender } from '@angular/core';\nafterRender(() => render());\n",
                        "/*~~(afterRender was replaced by phase-specific rendering APIs; choose afterNextRender or afterEveryRender and verify callback timing)~~>*/import { afterRender } from '@angular/core';\n/*~~(Angular rendering/zoneless API changed; select the stable Angular 20 API and retest scheduling and change detection)~~>*/afterRender(() => render());\n",
                        source -> source.path("src/render.ts")
                ),
                typescript(
                        "function afterRender(callback: () => void) { callback(); }\nafterRender(render);\n",
                        source -> source.path("src/local-render.ts")
                )
        );
    }

    @Test
    void marksSsrBootstrapCallOnlyInServerEntryPoint() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20TypeScriptRisks()),
                typescript(
                        "import { bootstrapApplication } from '@angular/platform-browser';\nconst bootstrap = () => bootstrapApplication(AppComponent, config);\n",
                        "import { bootstrapApplication } from '@angular/platform-browser';\nconst bootstrap = () => /*~~(Angular 20.3 SSR requires BootstrapContext propagation; add the context parameter/import while preserving this server entry point's wrapper contract)~~>*/bootstrapApplication(AppComponent, config);\n",
                        source -> source.path("src/main.server.ts")
                ),
                typescript(
                        "import { bootstrapApplication } from '@angular/platform-browser';\nconst bootstrap = () => bootstrapApplication(AppComponent, config);\n",
                        source -> source.path("src/main.ts")
                )
        );
    }

    @Test
    void marksRealNgccScriptAtItsJsonValue() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20JsonRisks()),
                json(
                        "{\"scripts\":{\"postinstall\":\"ngcc\"},\"dependencies\":{\"@angular/core\":\"9.0.0\"}}",
                        "{\"scripts\":{\"postinstall\":/*~~(Angular 20 no longer supports View Engine/ngcc; remove this script only after replacing or upgrading every View Engine dependency)~~>*/\"ngcc\"},\"dependencies\":{\"@angular/core\":/*~~(@angular/core declaration is a range, protocol, variable, unlisted, or already-newer version; select the intended constraint and regenerate this package manager's lockfile)~~>*/\"9.0.0\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void marksPackageGroupToolchainAndRuntimeValues() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20JsonRisks()),
                json(
                        """
                        {
                          "engines":{"node":"14.x"},
                          "dependencies":{"@angular/core":"20.3.26","@angular/common":"12.2.17","rxjs":"6.5.3","zone.js":"0.11.4"},
                          "devDependencies":{"typescript":"4.3.5"}
                        }
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> org.junit.jupiter.api.Assertions.assertTrue(after.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void marksRangeAndCentralOwnershipWithoutUpgradingThem() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20JsonRisks()),
                json(
                        """
                        {"dependencies":{"@angular/core":"^10.0.14"},"pnpm":{"overrides":{"@angular/core":"10.0.14"}}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> org.junit.jupiter.api.Assertions.assertTrue(after.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void doesNotMarkAnUnrelatedNodePackage() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20JsonRisks()),
                json(
                        "{\"engines\":{\"node\":\"14.x\"},\"devDependencies\":{\"typescript\":\"4.3.5\"}}",
                        source -> source.path("services/api/package.json")
                )
        );
    }

    @Test
    void marksAngularWorkspaceAndTsconfigAtExactMembers() {
        rewriteRun(
                spec -> spec.recipe(new FindAngular20JsonRisks()),
                json(
                        "{\"defaultProject\":\"web\",\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@nrwl/angular:webpack-browser\"},\"server\":{}}}}}",
                        source -> source.path("angular.json").after(actual -> actual)
                                .afterRecipe(after -> org.junit.jupiter.api.Assertions.assertTrue(after.printAll().contains("~~>")))
                ),
                json(
                        "{\"compilerOptions\":{\"target\":\"es2015\",\"moduleResolution\":\"node\"},\"angularCompilerOptions\":{\"enableIvy\":false}}",
                        source -> source.path("tsconfig.app.json").after(actual -> actual)
                                .afterRecipe(after -> org.junit.jupiter.api.Assertions.assertTrue(after.printAll().contains("~~>")))
                ),
                json(
                        "{\"compilerOptions\":{\"target\":\"es2015\",\"moduleResolution\":\"node\"}}",
                        source -> source.path("services/api/tsconfig.json")
                )
        );
    }

    @Test
    void recommendedRecipeCombinesDependencyAndDeterministicSourceMigration() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build();
        rewriteRun(
                spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.angular.MigrateAngularCoreTo20_3_26")),
                json(
                        "{\"dependencies\":{\"@angular/core\":\"12.2.17\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"20.3.26\"}}",
                        source -> source.path("package.json")
                ),
                typescript(
                        "import { TestBed } from '@angular/core/testing';\nconst service = TestBed.get(Token);\n",
                        "import { TestBed } from '@angular/core/testing';\nconst service = TestBed.inject(Token);\n",
                        source -> source.path("src/service.spec.ts")
                )
        );
    }
}
