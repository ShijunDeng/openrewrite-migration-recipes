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

class AngularAnimationsSourceMigrationTest implements RewriteTest {
    private static final String SOURCE =
            "com.huawei.clouds.openrewrite.angular.MigrateDeterministicAngularAnimationsTo20";
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.angular.AuditAngularAnimations20Source";
    private static final String MIGRATION =
            "com.huawei.clouds.openrewrite.angular.MigrateAngularAnimationsTo20_3_26";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void movesSoleAnimationModuleTypeImportToCore() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { ANIMATION_MODULE_TYPE } from '@angular/platform-browser/animations';\n",
                        "import { ANIMATION_MODULE_TYPE } from '@angular/core';\n",
                        source -> source.path("src/animation-token.ts")
                )
        );
    }

    @Test
    void movesAliasedTokenAndPreservesDoubleQuotes() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { ANIMATION_MODULE_TYPE as animationType } from \"@angular/platform-browser/animations\";\n",
                        "import { ANIMATION_MODULE_TYPE as animationType } from \"@angular/core\";\n",
                        source -> source.path("src/animation-token.ts")
                )
        );
    }

    @Test
    void leavesTokenImportWhenCoreAlreadyOwnsSameLocalBinding() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { ANIMATION_MODULE_TYPE } from '@angular/core';\n" +
                        "import { ANIMATION_MODULE_TYPE } from '@angular/platform-browser/animations';\n",
                        source -> source.path("src/conflict.ts")
                )
        );
    }

    @Test
    void leavesMixedDefaultNamespaceAndUnrelatedImportsUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript("import { ANIMATION_MODULE_TYPE, provideAnimations } from '@angular/platform-browser/animations';\n",
                        source -> source.path("src/mixed.ts")),
                typescript("import * as animations from '@angular/platform-browser/animations';\n",
                        source -> source.path("src/namespace.ts")),
                typescript("import { ANIMATION_MODULE_TYPE } from '@company/platform-browser/animations';\n",
                        source -> source.path("src/unrelated.ts"))
        );
    }

    @Test
    void removesRedundantAnimationsArgumentFromOwnedAsyncProvider() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';\n" +
                        "const providers = [provideAnimationsAsync('animations')];\n",
                        "import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';\n" +
                        "const providers = [provideAnimationsAsync()];\n",
                        source -> source.path("src/app.config.ts")
                )
        );
    }

    @Test
    void removesRedundantArgumentThroughImportAlias() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { provideAnimationsAsync as animations } from '@angular/platform-browser/animations/async';\n" +
                        "animations('animations');\n",
                        "import { provideAnimationsAsync as animations } from '@angular/platform-browser/animations/async';\n" +
                        "animations();\n",
                        source -> source.path("src/providers.ts")
                )
        );
    }

    @Test
    void leavesNoopDynamicAndSameNamedLocalAsyncCallsUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';\n" +
                        "provideAnimationsAsync('noop');\nprovideAnimationsAsync(mode);\n",
                        source -> source.path("src/modes.ts")
                ),
                typescript("function provideAnimationsAsync(mode: string) {}\nprovideAnimationsAsync('animations');\n",
                        source -> source.path("src/local.ts"))
        );
    }

    @Test
    void removesOnlyRedundantFalseWithConfigProperty() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { BrowserAnimationsModule } from '@angular/platform-browser/animations';\n" +
                        "const module = BrowserAnimationsModule.withConfig({ disableAnimations: false });\n",
                        "import { BrowserAnimationsModule } from '@angular/platform-browser/animations';\n" +
                        "const module = BrowserAnimationsModule.withConfig({ });\n",
                        source -> source.path("src/app.module.ts")
                )
        );
    }

    @Test
    void removesRedundantFalseThroughBrowserModuleAlias() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { BrowserAnimationsModule as Animations } from '@angular/platform-browser/animations';\n" +
                        "const module = Animations.withConfig({disableAnimations: false});\n",
                        "import { BrowserAnimationsModule as Animations } from '@angular/platform-browser/animations';\n" +
                        "const module = Animations.withConfig({});\n",
                        source -> source.path("src/alias.module.ts")
                )
        );
    }

    @Test
    void leavesTrueDynamicAndSameNamedLocalWithConfigUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                typescript(
                        "import { BrowserAnimationsModule } from '@angular/platform-browser/animations';\n" +
                        "BrowserAnimationsModule.withConfig({disableAnimations: true});\n" +
                        "BrowserAnimationsModule.withConfig({disableAnimations: disabled});\n",
                        source -> source.path("src/runtime.module.ts")
                ),
                typescript("LocalModule.withConfig({disableAnimations: false});\n",
                        source -> source.path("src/local.module.ts"))
        );
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import { ANIMATION_MODULE_TYPE } from '@angular/platform-browser/animations';\n",
                        "import { ANIMATION_MODULE_TYPE } from '@angular/core';\n",
                        source -> source.path("src/idempotent.ts")
                )
        );
    }

    @Test
    void marksBasicLegacyDslImportCallsAndComponentMetadata() {
        assertSourceMarkers(
                "import { trigger, transition, style, animate } from '@angular/animations';\n" +
                "const fade = trigger('fade', [transition(':enter', [style({opacity: 0}), animate('200ms')])]);\n" +
                "const component = {animations: [fade]};\n",
                "src/fade.ts",
                "legacy trigger/state/transition",
                "Legacy transition expressions",
                "Legacy animate timing",
                "Component animations metadata"
        );
    }

    @Test
    void marksRealNativeScriptQueryStaggerTimeline() {
        // NativeScript/ns-ng-animation-examples, fixed commit 75ca8272e8a4d2b448e510112327e06d9bb70bdd.
        assertSourceMarkers(
                "import { animate, query, stagger, style, transition, trigger } from '@angular/animations';\n" +
                "const list = trigger('listAnimation', [transition('* => *', [query(':enter', style({opacity: 0})), " +
                "query(':enter', stagger(200, [animate(1000, style({opacity: 1}))]), {delay: 200})])]);\n",
                "app/query-stagger.component.ts",
                "no mechanical CSS equivalent",
                "query composes a legacy timeline",
                "stagger composes a legacy timeline"
        );
    }

    @Test
    void marksRealEvictionMapsEnterLeaveTrigger() {
        // EvictionLab/eviction-maps, fixed commit 6969db5b29263c6fff1b8191efd60d5a972280be.
        assertSourceMarkers(
                "import { trigger, transition, style, animate, state } from '@angular/animations';\n" +
                "const popin = trigger('popin', [transition(':enter', [style({opacity: 0}), animate('0.4s ease-in')]), " +
                "transition(':leave', [style({opacity: 1}), animate('0.4s ease-out')])]);\n",
                "src/app/map-tool/guide/guide.component.ts",
                "deprecated since Angular 20.2",
                "Legacy transition expressions",
                "Legacy animate timing"
        );
    }

    @Test
    void marksReusableAnimationAndCompositionApis() {
        assertSourceMarkers(
                "import { animation, useAnimation, group, sequence, keyframes, animateChild } from '@angular/animations';\n" +
                "const reusable = animation([sequence([group([]), animateChild()]), keyframes([])]);\n" +
                "const applied = useAnimation(reusable);\n",
                "src/reusable.ts",
                "no mechanical CSS equivalent",
                "animation composes a legacy timeline",
                "sequence composes a legacy timeline",
                "group composes a legacy timeline",
                "animateChild composes a legacy timeline",
                "keyframes composes a legacy timeline",
                "useAnimation composes a legacy timeline"
        );
    }

    @Test
    void marksAnimationBuilderAndOwnedConstructionCall() {
        assertSourceMarkers(
                "import { AnimationBuilder } from '@angular/animations';\n" +
                "function build(animationBuilder: AnimationBuilder) { return animationBuilder.build([]); }\n",
                "src/builder.ts",
                "AnimationBuilder/Factory is deprecated",
                "Programmatic animation construction"
        );
    }

    @Test
    void marksAnimationPlayerAndLifecycleControl() {
        assertSourceMarkers(
                "import { AnimationPlayer } from '@angular/animations';\n" +
                "function stop(animationPlayer: AnimationPlayer) { animationPlayer.finish(); animationPlayer.destroy(); }\n",
                "src/player.ts",
                "Legacy AnimationPlayer lifecycle",
                "AnimationPlayer control affects callback ordering"
        );
    }

    @Test
    void marksLegacyAnimationEventImport() {
        assertSourceMarkers(
                "import { AnimationEvent } from '@angular/animations';\nfunction done(event: AnimationEvent) {}\n",
                "src/events.ts",
                "Legacy AnimationEvent callback shape differs"
        );
    }

    @Test
    void marksRealApacheNifiBrowserModuleAndConditionalProviders() {
        // apache/nifi, fixed commit c7725c43340292e2231b4024b2456aa0ac284c44.
        assertSourceMarkers(
                "import { BrowserAnimationsModule, provideAnimations, provideNoopAnimations } from '@angular/platform-browser/animations';\n" +
                "const imports = [BrowserAnimationsModule];\n" +
                "const providers = [disableAnimations ? provideNoopAnimations() : provideAnimations()];\n",
                "nifi-frontend/src/main/frontend/apps/nifi/src/app/app.module.ts",
                "deprecated since Angular 20.2",
                "provideNoopAnimations enables or disables",
                "provideAnimations enables or disables"
        );
    }

    @Test
    void marksRealIhtsdoStandaloneProvider() {
        // IHTSDO/request-management-portal-ui, fixed commit 17cddcf3255a67c7dd9472610967934b4193f9bb.
        assertSourceMarkers(
                "import { provideAnimations } from '@angular/platform-browser/animations';\n" +
                "export const config = {providers: [provideAnimations()]};\n",
                "src/app/app.config.ts",
                "provideAnimations providers are deprecated",
                "provideAnimations enables or disables"
        );
    }

    @Test
    void marksRealMoodleNoopTestingModule() {
        // moodlehq/moodleapp, fixed commit 4caf3f3f7d38c4bfe14939897910469aaa0a988a.
        assertSourceMarkers(
                "import { NoopAnimationsModule } from '@angular/platform-browser/animations';\n" +
                "const testImports = [NoopAnimationsModule];\n",
                "src/testing/utils.ts",
                "BrowserAnimationsModule/NoopAnimationsModule"
        );
    }

    @Test
    void marksAsyncProviderWithServerSpecificGuidance() {
        assertSourceMarkers(
                "import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';\n" +
                "export const config = {providers: [provideAnimationsAsync()]};\n",
                "src/main.server.ts",
                "provideAnimationsAsync is deprecated",
                "Server entry points must not share browser animation state"
        );
    }

    @Test
    void marksLegacyBrowserTestingDriverImport() {
        assertSourceMarkers(
                "import { MockAnimationDriver, MockAnimationPlayer } from '@angular/animations/browser/testing';\n",
                "src/animation.spec.ts",
                "tests assert legacy engine internals"
        );
    }

    @Test
    void marksLegacyBrowserDriverImport() {
        assertSourceMarkers(
                "import { AnimationDriver } from '@angular/animations/browser';\n",
                "src/custom-driver.ts",
                "AnimationDriver/engine browser APIs are legacy internals"
        );
    }

    @Test
    void marksRouteAnimationMetadataOnlyWhenLegacyApiIsImported() {
        assertSourceMarkers(
                "import { trigger } from '@angular/animations';\nconst route = {data: {animation: 'AdminPage'}};\ntrigger('route', []);\n",
                "src/app.routes.ts",
                "Animation state stored in route/component metadata"
        );
    }

    @Test
    void marksMixedCoreTokenImportInsteadOfAutomaticallySplittingIt() {
        assertSourceMarkers(
                "import { ANIMATION_MODULE_TYPE, provideAnimations } from '@angular/platform-browser/animations';\n",
                "src/mixed-token.ts",
                "Mixed platform-browser animations import contains core-owned ANIMATION_MODULE_TYPE"
        );
    }

    @Test
    void auditLeavesUnrelatedSameNamedLocalCallsAndMetadataUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(
                        "function trigger(name: string, value: unknown[]) {}\n" +
                        "trigger('local', []);\nconst component = {animations: []};\nprovideAnimations();\n",
                        source -> source.path("src/local.ts")
                )
        );
    }

    @Test
    void discoversAndValidatesAllPublicSourceRecipes() {
        Environment environment = environment();
        for (String name : new String[]{SOURCE, AUDIT, MIGRATION}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
            assertEquals(name, recipe.getName());
        }
    }

    private void assertSourceMarkers(String before, String path, String... messages) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(before, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            for (String message : messages) {
                                assertTrue(printed.contains(message), printed);
                            }
                        }))
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build();
    }
}
