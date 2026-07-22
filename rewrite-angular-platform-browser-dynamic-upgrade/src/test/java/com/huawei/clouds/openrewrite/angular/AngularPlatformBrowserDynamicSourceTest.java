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

class AngularPlatformBrowserDynamicSourceTest implements RewriteTest {
    private static final String SOURCE =
            "com.huawei.clouds.openrewrite.angular.MigrateDeterministicAngularPlatformBrowserDynamicSourceTo20";
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.angular.AuditAngularPlatformBrowserDynamic20Source";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesPinnedGoogleMapsOfficialReplacementShape() {
        // googlemaps/extended-component-library 70aff8d2 -> bddb510441b4f3794b3eb683bb7834d6d7ee687e.
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        """
                        import {platformBrowserDynamic} from '@angular/platform-browser-dynamic';
                        import {AppModule} from './app/app.module';
                        platformBrowserDynamic().bootstrapModule(AppModule).catch(err => console.error(err));
                        """,
                        """
                        import {platformBrowser} from '@angular/platform-browser';
                        import {AppModule} from './app/app.module';
                        platformBrowser().bootstrapModule(AppModule).catch(err => console.error(err));
                        """,
                        source -> source.path("googlemaps/extended-component-library/examples/angular_sample_app/src/main.ts")));
    }

    @Test
    void migratesPinnedTodoAngularFirebaseBootstrap() {
        // r-park/todo-angular-firebase 9057353abb7fb9827beda3a940331445e2feb552.
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        """
                        import { enableProdMode } from '@angular/core';
                        import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
                        import { AppModule } from './app/app.module';
                        platformBrowserDynamic().bootstrapModule(AppModule).then(() => ready());
                        """,
                        """
                        import { enableProdMode } from '@angular/core';
                        import { platformBrowser } from '@angular/platform-browser';
                        import { AppModule } from './app/app.module';
                        platformBrowser().bootstrapModule(AppModule).then(() => ready());
                        """,
                        source -> source.path("r-park/todo-angular-firebase/src/main.ts")));
    }

    @Test
    void migratesAliasedSoleImportWithoutRenamingLocalBinding() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { platformBrowserDynamic as boot } from \"@angular/platform-browser-dynamic\";\nboot().bootstrapModule(AppModule);\n",
                        "import { platformBrowser as boot } from \"@angular/platform-browser\";\nboot().bootstrapModule(AppModule);\n",
                        source -> source.path("src/main.ts")));
    }

    @Test
    void leavesMixedNamespaceDefaultAndEscapedBindingUsesUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript("import { platformBrowserDynamic, JitCompilerFactory } from '@angular/platform-browser-dynamic';\nplatformBrowserDynamic().bootstrapModule(AppModule);\n",
                        source -> source.path("src/mixed.ts")),
                typescript("import * as dynamic from '@angular/platform-browser-dynamic';\ndynamic.platformBrowserDynamic().bootstrapModule(AppModule);\n",
                        source -> source.path("src/namespace.ts")),
                typescript("import dynamic from '@angular/platform-browser-dynamic';\ndynamic().bootstrapModule(AppModule);\n",
                        source -> source.path("src/default.ts")),
                typescript("import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';\nconst factory = platformBrowserDynamic;\nfactory().bootstrapModule(AppModule);\n",
                        source -> source.path("src/reference.ts")),
                typescript("import { platformBrowserDynamic as boot } from '@angular/platform-browser-dynamic';\nconst factory = boot;\nfactory().bootstrapModule(AppModule);\n",
                        source -> source.path("src/aliased-reference.ts")));
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';\nplatformBrowserDynamic().bootstrapModule(AppModule);\n",
                        "import { platformBrowser } from '@angular/platform-browser';\nplatformBrowser().bootstrapModule(AppModule);\n",
                        source -> source.path("src/main.ts")));
    }

    @Test
    void marksRemainingDynamicJitAndTestingImports() {
        assertRisk("import { platformBrowserDynamic, JitCompilerFactory } from '@angular/platform-browser-dynamic';\nplatformBrowserDynamic().bootstrapModule(AppModule);\n",
                "JitCompilerFactory is deprecated", "src/jit-main.ts");
        assertRisk("import { platformBrowserDynamicTesting } from '@angular/platform-browser-dynamic/testing';\n",
                "deep/testing entry", "src/test.ts");
        assertRisk("import { Compiler, ResourceLoader } from '@angular/compiler';\n",
                "Runtime @angular/compiler coupling", "src/compiler.ts");
        assertRisk("import { COMPILER_OPTIONS } from '@angular/core';\n",
                "COMPILER_OPTIONS customizes runtime JIT", "src/options.ts");
    }

    @Test
    void marksRuntimeCompilerAndComponentFactoryCalls() {
        assertRisk("import { Compiler } from '@angular/core';\nfunction load(compiler: Compiler) { return compiler.compileModuleAndAllComponentsAsync(DynamicModule); }\n",
                "Runtime Compiler API", "src/dynamic-loader.ts");
        assertRisk("import { ComponentFactoryResolver } from '@angular/core';\nfunction open(resolver: ComponentFactoryResolver) { return resolver.resolveComponentFactory(Dialog); }\n",
                "ComponentFactoryResolver-era", "src/dialog.ts");
    }

    @Test
    void marksEntryComponentsAndComputedRuntimeTemplates() {
        assertRisk("import { NgModule } from '@angular/core';\n@NgModule({entryComponents: [Dialog]})\nclass AppModule {}\n",
                "Ivy ignores entryComponents", "src/app.module.ts");
        assertRisk("import { Compiler } from '@angular/compiler';\nconst metadata = {template: runtimeTemplate};\n",
                "Runtime-computed component template", "src/plugin.ts");
    }

    @Test
    void sourceAuditLeavesUnownedLookalikesAndModernBootstrapUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript("function platformBrowserDynamic() { return platform; }\nplatformBrowserDynamic();\n",
                        source -> source.path("src/unrelated.ts")),
                typescript("import { platformBrowser, bootstrapApplication } from '@angular/platform-browser';\nplatformBrowser().bootstrapModule(AppModule);\nbootstrapApplication(AppComponent);\n",
                        source -> source.path("src/modern.ts")),
                typescript("const compiler = {compileModuleAsync(value: unknown) { return value; }};\ncompiler.compileModuleAsync(Module);\n",
                        source -> source.path("src/lookalike.ts")));
    }

    @Test
    void discoversAndValidatesSourceRecipes() {
        Environment environment = environment();
        for (String name : new String[]{SOURCE, AUDIT}) {
            Recipe recipe = environment.activateRecipes(name);
            assertEquals(name, recipe.getName());
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()));
        }
    }

    private void assertRisk(String before, String message, String path) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(before, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources().build();
    }
}
