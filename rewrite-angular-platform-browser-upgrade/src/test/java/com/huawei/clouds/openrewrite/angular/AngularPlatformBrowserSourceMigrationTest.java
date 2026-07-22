package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class AngularPlatformBrowserSourceMigrationTest implements RewriteTest {
    private static final String SOURCE = "com.huawei.clouds.openrewrite.angular.MigrateDeterministicPlatformBrowserTo20";
    private static final String AUDIT = "com.huawei.clouds.openrewrite.angular.AuditAngularPlatformBrowser20Source";
    private static final String MIGRATION = "com.huawei.clouds.openrewrite.angular.MigrateAngularPlatformBrowserTo20_3_26";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void movesSoleApplicationConfigImportToCore() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { ApplicationConfig } from '@angular/platform-browser';\nexport const config: ApplicationConfig = {};\n",
                        "import { ApplicationConfig } from '@angular/core';\nexport const config: ApplicationConfig = {};\n",
                        source -> source.path("src/app/app.config.ts")
                )
        );
    }

    @Test
    void movesAliasedTransferStateImportsAndPreservesQuotes() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { TransferState as Store, StateKey, makeStateKey } from \"@angular/platform-browser\";\n" +
                        "const KEY: StateKey<string> = makeStateKey('key');\n",
                        "import { TransferState as Store, StateKey, makeStateKey } from \"@angular/core\";\n" +
                        "const KEY: StateKey<string> = makeStateKey('key');\n",
                        source -> source.path("src/transfer.ts")
                )
        );
    }

    @Test
    void migratesRealNgToolkitTransferStateImport() {
        // maciejtreder/ng-toolkit, fixed commit eab11d43df4efc6e604d84cfc63d589872007cb2.
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { makeStateKey, StateKey, TransferState } from '@angular/platform-browser';\n" +
                        "const key: StateKey<string> = makeStateKey<string>('response');\n",
                        "import { makeStateKey, StateKey, TransferState } from '@angular/core';\n" +
                        "const key: StateKey<string> = makeStateKey<string>('response');\n",
                        source -> source.path("application/src/app/services/resolvers/hitWithTransferState.resolver.ts")
                )
        );
    }

    @Test
    void movesAllCanonicalCoreReexportsTogether() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { ApplicationConfig, makeStateKey, StateKey, TransferState } from '@angular/platform-browser';\n",
                        "import { ApplicationConfig, makeStateKey, StateKey, TransferState } from '@angular/core';\n",
                        source -> source.path("src/platform.ts")
                )
        );
    }

    @Test
    void leavesMixedDefaultNamespaceAndUnrelatedImportsUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript("import { ApplicationConfig, BrowserModule } from '@angular/platform-browser';\n",
                        source -> source.path("src/mixed.ts")),
                typescript("import * as browser from '@angular/platform-browser';\n",
                        source -> source.path("src/namespace.ts")),
                typescript("import { DomSanitizer } from '@angular/platform-browser';\n",
                        source -> source.path("src/sanitize.ts")),
                typescript("import { ApplicationConfig } from '@company/platform-browser';\n",
                        source -> source.path("src/unrelated.ts"))
        );
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import { TransferState } from '@angular/platform-browser';\n",
                        "import { TransferState } from '@angular/core';\n",
                        source -> source.path("src/idempotent.ts")
                )
        );
    }

    @Test
    void removesEnableIvyTrueFromDirectAngularCompilerOptions() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                json(
                        """
                        {
                          "compilerOptions": {"target": "ES2022"},
                          "angularCompilerOptions": {
                            "enableIvy": true,
                            "strictTemplates": true
                          }
                        }
                        """,
                        """
                        {
                          "compilerOptions": {"target": "ES2022"},
                          "angularCompilerOptions": {
                            "strictTemplates": true
                          }
                        }
                        """,
                        source -> source.path("tsconfig.app.json")
                )
        );
    }

    @Test
    void removesOnlyEnableIvyTrueWhenItIsTheOnlyOption() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                json("{\"angularCompilerOptions\":{\"enableIvy\":true}}",
                        "{\"angularCompilerOptions\":{}}",
                        source -> source.path("tsconfig.json"))
        );
    }

    @Test
    void leavesEnableIvyFalseNestedLookalikesAndNonTsconfigUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                json("{\"angularCompilerOptions\":{\"enableIvy\":false}}", source -> source.path("tsconfig.lib.json")),
                json("{\"metadata\":{\"angularCompilerOptions\":{\"enableIvy\":true}}}", source -> source.path("tsconfig.json")),
                json("{\"angularCompilerOptions\":{\"enableIvy\":true}}", source -> source.path("config.json"))
        );
    }

    @Test
    void marksMixedMovedImportInsteadOfSplittingBindings() {
        String message = "Mixed platform-browser import contains APIs moved to @angular/core; split/merge imports without duplicating local bindings";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(
                        "import { ApplicationConfig, BrowserModule } from '@angular/platform-browser';\n",
                        marker(message) + "import { ApplicationConfig, BrowserModule } from '@angular/platform-browser';\n",
                        source -> source.path("src/mixed.ts")
                )
        );
    }

    @Test
    void marksBrowserModuleAndWithServerTransitionAtExactAstNodes() {
        String importMessage = "BrowserModule must provide browser services only once; verify this is the root module and replace feature/lazy imports with CommonModule or standalone imports";
        String callMessage = "BrowserModule.withServerTransition was removed; migrate APP_ID/modern SSR and hydration configuration together";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(
                        "import { BrowserModule } from '@angular/platform-browser';\nconst browser = BrowserModule.withServerTransition({appId: 'serverApp'});\n",
                        marker(importMessage) + "import { BrowserModule } from '@angular/platform-browser';\n" +
                        "const browser = " + marker(callMessage) + "BrowserModule.withServerTransition({appId: 'serverApp'});\n",
                        source -> source.path("src/app/app.module.ts")
                )
        );
    }

    @Test
    void marksRealBullhornWithServerTransitionShape() {
        // bullhorn/career-portal, fixed commit c450bae71461bf667ebb480456d8ef920689a9d1.
        String importMessage = "BrowserModule must provide browser services only once; verify this is the root module and replace feature/lazy imports with CommonModule or standalone imports";
        String callMessage = "BrowserModule.withServerTransition was removed; migrate APP_ID/modern SSR and hydration configuration together";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(
                        "import { BrowserModule } from '@angular/platform-browser';\n" +
                        "const imports = [BrowserModule.withServerTransition({ appId: 'serverApp' })];\n",
                        marker(importMessage) + "import { BrowserModule } from '@angular/platform-browser';\n" +
                        "const imports = [" + marker(callMessage) + "BrowserModule.withServerTransition({ appId: 'serverApp' })];\n",
                        source -> source.path("src/app/app.module.ts")
                )
        );
    }

    @Test
    void marksHammerAndEventManagerImports() {
        String hammer = "Angular 20 deprecates HammerJS integration; replace gesture providers/events with an owned Pointer Events or maintained library strategy";
        String events = "Custom EventManager integration is order, zone and DOM-runtime sensitive; verify plugin supports(), listener cleanup, SSR and zoneless behavior";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript("import { HammerModule, HAMMER_GESTURE_CONFIG } from '@angular/platform-browser';\n",
                        marker(hammer) + "import { HammerModule, HAMMER_GESTURE_CONFIG } from '@angular/platform-browser';\n",
                        source -> source.path("src/gestures.ts")),
                typescript("import { EventManagerPlugin, EVENT_MANAGER_PLUGINS } from '@angular/platform-browser';\n",
                        marker(events) + "import { EventManagerPlugin, EVENT_MANAGER_PLUGINS } from '@angular/platform-browser';\n",
                        source -> source.path("src/events.ts"))
        );
    }

    @Test
    void marksAnimationsAndBrowserTestingImports() {
        String animations = "Angular 20.2 deprecates legacy animations providers/modules; migrate templates to animate.enter/leave and verify lazy animation loading";
        String testing = "Browser test platform setup is global and timing-sensitive; align TestBed environment, teardown, compiler mode, DOM adapter and test runner for Angular 20";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript("import { BrowserAnimationsModule } from '@angular/platform-browser/animations';\n",
                        marker(animations) + "import { BrowserAnimationsModule } from '@angular/platform-browser/animations';\n",
                        source -> source.path("src/animations.ts")),
                typescript("import { BrowserTestingModule } from '@angular/platform-browser/testing';\n",
                        marker(testing) + "import { BrowserTestingModule } from '@angular/platform-browser/testing';\n",
                        source -> source.path("src/test.ts"))
        );
    }

    @Test
    void marksPlatformBrowserDynamicCallButNotSameNamedLocalFunction() {
        String message = "platformBrowserDynamic is deprecated; choose platformBrowser for AOT or retain @angular/compiler explicitly for a proven JIT requirement";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(
                        "import { platformBrowserDynamic as createPlatform } from '@angular/platform-browser-dynamic';\ncreatePlatform().bootstrapModule(AppModule);\n",
                        "import { platformBrowserDynamic as createPlatform } from '@angular/platform-browser-dynamic';\n" +
                        marker(message) + "createPlatform().bootstrapModule(AppModule);\n",
                        source -> source.path("src/main.ts")
                ),
                typescript("function platformBrowserDynamic() { return platform; }\nplatformBrowserDynamic();\n",
                        source -> source.path("src/local.ts"))
        );
    }

    @Test
    void marksRealSanitizerBypassCall() {
        // woodstream/appetite, fixed commit 39ebc6e8e3721f0b04a50216e16a4c91baff7a67.
        String message = "bypassSecurityTrust* disables Angular sanitization; prove the value's trust boundary and retest CSP/Trusted Types before retaining it";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(
                        "import { DomSanitizer } from '@angular/platform-browser';\n" +
                        "class Util {\n  constructor(private sanitizer: DomSanitizer) {}\n" +
                        "  trust(html: string) { return this.sanitizer.bypassSecurityTrustHtml(html); }\n}\n",
                        "import { DomSanitizer } from '@angular/platform-browser';\n" +
                        "class Util {\n  constructor(private sanitizer: DomSanitizer) {}\n" +
                        "  trust(html: string) { return " + marker(message) +
                        "this.sanitizer.bypassSecurityTrustHtml(html); }\n}\n",
                        source -> source.path("src/providers/common/util.ts")
                )
        );
    }

    @Test
    void marksTransferStateSerializationCalls() {
        String message = "TransferState crosses the server/client HTML boundary; verify stable keys, JSON-lossless values, cache lifecycle and sensitive-data exposure";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(
                        "import { TransferState } from '@angular/core';\ntransferState.set(KEY, user);\nconst value = transferState.get(KEY, null);\n",
                        "import { TransferState } from '@angular/core';\n" + marker(message) + "transferState.set(KEY, user);\n" +
                        "const value = " + marker(message) + "transferState.get(KEY, null);\n",
                        source -> source.path("src/state.ts")
                )
        );
    }

    @Test
    void marksHydrationProviderAndServerBootstrap() {
        String hydration = "Review provideClientHydration features, server/client DOM parity, event replay, incremental boundaries and direct DOM manipulation";
        String server = "Angular 20.3 server bootstrap must propagate BootstrapContext as the third argument without sharing a platform across requests";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(
                        "import { bootstrapApplication, provideClientHydration } from '@angular/platform-browser';\n" +
                        "bootstrapApplication(App, {providers: [provideClientHydration()]});\n",
                        "import { bootstrapApplication, provideClientHydration } from '@angular/platform-browser';\n" +
                        marker(server) + "bootstrapApplication(App, {providers: [" + marker(hydration) + "provideClientHydration()]});\n",
                        source -> source.path("src/main.server.ts")
                )
        );
    }

    @Test
    void marksProtractorSupportAndDebugToolsImportAndCalls() {
        String importMessage = "Platform-browser metadata/debug/testability/style integration needs SSR, production and teardown review under Angular 20";
        String protractor = "provideProtractorTestingSupport opts into legacy Testability; confirm the remaining E2E harness needs it or remove during runner migration";
        String debug = "Angular debug tools must not leak into production or SSR; verify environment guards and replacement diagnostics";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(
                        "import { enableDebugTools, provideProtractorTestingSupport } from '@angular/platform-browser';\n" +
                        "const providers = provideProtractorTestingSupport();\nenableDebugTools(ref);\n",
                        marker(importMessage) + "import { enableDebugTools, provideProtractorTestingSupport } from '@angular/platform-browser';\n" +
                        "const providers = " + marker(protractor) + "provideProtractorTestingSupport();\n" +
                        marker(debug) + "enableDebugTools(ref);\n",
                        source -> source.path("src/debug.ts")
                )
        );
    }

    @Test
    void auditLeavesUnrelatedSameNamedCallsUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(
                        """
                        class LocalSanitizer { bypassSecurityTrustHtml(value: string) { return value; } }
                        sanitizer.bypassSecurityTrustHtml(html);
                        state.set(KEY, value);
                        provideClientHydration();
                        """,
                        source -> source.path("src/local.ts")
                )
        );
    }

    @Test
    void discoversAndValidatesPublicSourceRecipes() {
        Environment environment = environment();
        for (String name : new String[]{SOURCE, AUDIT, MIGRATION}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
            assertEquals(name, recipe.getName());
        }
    }

    private static String marker(String message) {
        return "/*~~(" + message + ")~~>*/";
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build();
    }
}
