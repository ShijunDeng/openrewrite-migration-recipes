package com.huawei.clouds.openrewrite.reactredux;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class ReactReduxRiskAndValidationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksEveryDocumentedIncompatibleManifestCompanionAtItsValue() {
        assertManifest("""
                {"dependencies":{"react-redux":"9.3.0","react":"^17.0.2","react-dom":"17.0.2","redux":"^4.2.1","@reduxjs/toolkit":"^1.9.7","@types/react":"^18.0.28","@types/react-redux":"^7.1.34"},"devDependencies":{"typescript":"~4.6.4"}}
                """, "requires React 18 or 19", "below the React 18 runtime", "declares Redux ^5.0.0",
                "Redux Toolkit 2 / Redux 5", "@types/react ^18.2.25", "ships its own TypeScript declarations",
                "drops TypeScript 4.6");
    }

    @Test
    void supportedPeersTargetAndToolchainAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindReactReduxManifestRisks()),
                json("{\"dependencies\":{\"react-redux\":\"^9.3.0\",\"react\":\"^19.0.0\",\"react-dom\":\"^19.0.0\",\"redux\":\"^5.0.1\"},\"devDependencies\":{\"@types/react\":\"^19.0.1\",\"typescript\":\"^5.8.2\"}}",
                        source -> source.path("package.json").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void compatibleBoundedRangesAreRecognizedWithoutGuessingUnboundedOnes() {
        rewriteRun(spec -> spec.recipe(new FindReactReduxManifestRisks()),
                json("{\"dependencies\":{\"react-redux\":\"9.3.0\",\"react\":\">=18 <20\",\"redux\":\">=5 <6\"},\"devDependencies\":{\"@types/react\":\"^18.2.25\",\"typescript\":\">=4.7\"}}",
                        source -> source.path("supported/package.json").afterRecipe(after -> assertNoMarker(after.printAll()))),
                json("{\"dependencies\":{\"react-redux\":\"9.3.0\",\"react\":\">=18\",\"redux\":\">=5\"},\"devDependencies\":{\"@types/react\":\"18.x\",\"typescript\":\"workspace:^5.0.0\"}}",
                        source -> source.path("ambiguous/package.json").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertTrue(out.contains("requires React 18 or 19"), out);
                            assertTrue(out.contains("declares Redux ^5.0.0"), out);
                            assertTrue(out.contains("@types/react ^18.2.25"), out);
                            assertTrue(out.contains("TypeScript 4.6"), out);
                        })));
    }

    @Test
    void marksMissingPeerOwnershipOnTheTargetDeclaration() {
        assertManifest("{\"dependencies\":{\"react-redux\":\"9.3.0\"}}",
                "add or verify React 18/19 and Redux 5 (or Redux Toolkit 2)");
    }

    @Test
    void marksComplexDirectDeclarationAndEveryCentralOwnerWithoutAutoChangingThem() {
        assertManifest("""
                {"dependencies":{"react-redux":">=7.2.2 <9"},"overrides":{"react-redux":"7.2.2"},"resolutions":{"**/react-redux":"7.2.4"},"pnpm":{"overrides":{"app>react-redux":"7.2.8"},"packageExtensions":{"react-redux@7.2.9":{"peerDependencies":{"react":"17"}}}},"catalog":{"react-redux":"8.0.2"},"catalogs":{"old":{"react-redux":"8.0.5"}},"packageExtensions":{"react-redux@7":{"peerDependencies":{"redux":"4"}}}}
                """, "not the exact workbook target", "central package-manager owner");
    }

    @Test
    void centralOwnerIsMarkedEvenWhenNoDirectDeclarationExists() {
        assertManifest("{\"resolutions\":{\"react-redux\":\"7.2.2\"}}", "central package-manager owner");
    }

    @Test
    void marksAllKindsOfPrivatePackageEntryReferences() {
        assertSource("""
                import { useSelector } from 'react-redux/es/hooks/useSelector';
                export { Provider } from 'react-redux/lib/components/Provider';
                const a = require('react-redux/dist/react-redux');
                const b = import('react-redux/src/hooks/useDispatch');
                """, "exports only the root", "does not export this deep re-export", "package exports block");
    }

    @Test
    void publicRootAlternateRendererAndPackageJsonEntriesAreNotDeepImportRisks() {
        rewriteRun(spec -> spec.recipe(new FindReactReduxSourceRisks()),
                typescript("import {Provider, useSelector} from 'react-redux';\nimport * as Alt from 'react-redux/alternate-renderers';\nimport pkg from 'react-redux/package.json';\n",
                        source -> source.path("src/public.ts").afterRecipe(after -> {
                            String out = after.printAll();
                            assertFalse(out.contains("exports only the root"), out);
                            assertFalse(out.contains("package exports block"), out);
                        })));
    }

    @Test
    void marksRemovedRuntimeAndTypeExportsAtExactImportSpecifiers() {
        assertSource("""
                import { connectAdvanced as advanced } from 'react-redux';
                import type { DefaultRootState, RootStateOrAny } from 'react-redux';
                advanced(factory)(View);
                """, "connectAdvanced was removed", "DefaultRootState was removed", "RootStateOrAny depended");
    }

    @Test
    void marksConnectDeprecationRemovedPureOptionAndBatch() {
        assertSource("""
                import { connect as rrConnect, batch } from 'react-redux';
                export const App = rrConnect(mapState, mapDispatch, null, { pure: false, areStatesEqual })(View);
                batch(flush);
                """, "9.3 deprecates connect", "removed connect's pure option", "deprecates batch");
    }

    @Test
    void marksLegacyConnectAsAnExplicitRemainingArchitectureDecision() {
        assertSource("""
                import { legacy_connect as connect } from 'react-redux';
                export default connect(mapState)(View);
                """, "remains the legacy HOC architecture");
    }

    @Test
    void marksProviderAndSelectorNoopCheckAtOwnedNodes() {
        assertSource("""
                import { Provider as ReduxProvider, useSelector as select } from 'react-redux';
                export const App=()=> <ReduxProvider store={store} noopCheck="always"><View/></ReduxProvider>;
                export const state = select(s=>s, {noopCheck:'never'});
                """, "Provider noopCheck was renamed", "devModeChecks.identityFunctionCheck");
    }

    @Test
    void marksHydratingProviderWithoutServerSnapshot() {
        assertSource("""
                import { hydrateRoot } from 'react-dom/client';
                import { Provider as ReduxProvider } from 'react-redux';
                hydrateRoot(root, <ReduxProvider store={store}><App/></ReduxProvider>);
                """, "pass the serialized server snapshot as Provider serverState");
    }

    @Test
    void hydratedProviderWithServerStateAndOrdinaryRenderAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindReactReduxSourceRisks()),
                typescript("import {hydrateRoot} from 'react-dom/client'; import {Provider} from 'react-redux'; hydrateRoot(root,<Provider store={store} serverState={preloaded}><App/></Provider>);\n",
                        source -> source.path("src/hydrate.tsx").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import ReactDOM from 'react-dom'; import {Provider} from 'react-redux'; ReactDOM.render(<Provider store={store}><App/></Provider>,root);\n",
                        source -> source.path("src/render.tsx").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksExplicitReactServerBoundaryAtOwnedModule() {
        assertSource("""
                'use server';
                import { useSelector } from 'react-redux';
                export async function action(){ return useSelector(s=>s.value); }
                """, "react-server condition intentionally throws");
    }

    @Test
    void marksNamespaceRemovedAndDeprecatedApisButNotSupportedHooks() {
        assertSource("""
                import * as RR from 'react-redux';
                const a = RR.connect(map)(View);
                const b = RR.connectAdvanced(factory)(Other);
                RR.batch(run);
                const state = RR.useSelector(select);
                """, "namespace-owned connect HOC", "connectAdvanced was removed", "deprecated no-op");
    }

    @Test
    void sameNamedForeignLocalAndShadowedSymbolsAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindReactReduxSourceRisks()),
                typescript("import {connect, Provider, useSelector} from '@company/state'; const A=connect(a)(C); const x=<Provider noopCheck='once'/>; useSelector(s=>s,{noopCheck:'once'});\n",
                        source -> source.path("src/foreign.tsx").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("function connect(a:any){return a} const Provider=(p:any)=>p.children; const useSelector=(a:any,b:any)=>a; connect({pure:false}); const x=<Provider noopCheck='once'/>;\n",
                        source -> source.path("src/local.tsx").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import {Provider} from 'react-redux'; function Preview(Provider:any){return <Provider/>};\n",
                        source -> source.path("src/shadowed.tsx").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksPinnedApitableDefaultRootStateAugmentation() {
        assertSource("""
                import { IWidgetState } from 'interface';
                declare module 'react-redux' {
                  interface DefaultRootState extends IWidgetState {}
                  export function useSelector<TState = IWidgetState, TSelected = unknown>(selector: (state: TState) => TSelected): TSelected;
                }
                """, "DefaultRootState module augmentation was removed");
    }

    @Test
    void marksPinnedIbaxConnectPureFixture() {
        assertSource("""
                import { connect } from 'react-redux';
                export default connect(mapStateToProps, mapDispatchToProps, null, { pure: false })(App);
                """, "9.3 deprecates connect", "removed connect's pure option");
    }

    @Test
    void marksPinnedHyperDeepTypeAndConnectArchitecture() {
        assertSource("""
                import {connect as reduxConnect} from 'react-redux';
                import type {ConnectOptions} from 'react-redux/es/components/connect';
                export function connect(d: ConnectOptions = {}) { return reduxConnect(a, b, null, d)(View); }
                """, "9.3 deprecates connect", "exports only the root");
    }

    @Test
    void realRenProjectProviderAndHooksWithoutHydrationRemainNoOps() {
        rewriteRun(spec -> spec.recipe(new FindReactReduxSourceRisks()),
                typescript("import ReactDOM from 'react-dom'; import {Provider} from 'react-redux'; ReactDOM.render(<Provider store={store}><App/></Provider>, root);\n",
                        source -> source.path("fixtures/renproject-bridge-v2/src/index.tsx")
                                .afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import {useDispatch,useSelector} from 'react-redux'; export const value=()=>{const d=useDispatch(); return useSelector(select)};\n",
                        source -> source.path("fixtures/renproject-bridge-v2/src/hooks.ts")
                                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void realV2exTypedHooksRemainSupportedNoOps() {
        rewriteRun(spec -> spec.recipe(new FindReactReduxSourceRisks()), typescript("""
                import { AppDispatch, RootState } from '@src/store'
                import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux'
                export const useAppDispatch = () => useDispatch<AppDispatch>()
                export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector
                """, source -> source.path("fixtures/funnyzak-react-native-v2ex/src/hooks/index.ts")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void riskFinderExcludesArtifactParentsButAcceptsInstallAndGeneratedLeaves() {
        rewriteRun(spec -> spec.recipe(new FindReactReduxSourceRisks()),
                typescript("import {connect} from 'react-redux';\n", source -> source.path("GENERATED-code/a.ts")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import {connect} from 'react-redux';\n", source -> source.path("Installer/a.ts")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import {connect} from 'react-redux';\n", source -> source.path(".CACHE/a.ts")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import {connect} from 'react-redux';\n", source -> source.path("src/install.ts")
                        .after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("9.3 deprecates connect")))),
                typescript("import {connect} from 'react-redux';\n", source -> source.path("src/generated.ts")
                        .after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("9.3 deprecates connect")))));
    }

    @Test
    void recommendedRecipeAutoNormalizesThenMarksRemainingHyperDecisions() {
        rewriteRun(spec -> spec.recipe(ReactReduxDependencyTest.environment().activateRecipes(ReactReduxDependencyTest.MIGRATE)),
                typescript("import {connect as reduxConnect} from 'react-redux/es/index.js';\nimport type {ConnectOptions} from 'react-redux/es/components/connect';\nreduxConnect(a,b,null,opts)(View);\n",
                        source -> source.path("src/hyper.ts").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertTrue(out.contains("legacy_connect as reduxConnect"), out);
                            assertTrue(out.contains("legacy HOC architecture"), out);
                            assertTrue(out.contains("exports only the root"), out);
                        })));
    }

    @Test
    void sourceMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindReactReduxSourceRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import {connect} from 'react-redux';\nconnect(a)(View);\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("9.3 deprecates connect")))));
    }

    private void assertManifest(String before, String... messages) {
        rewriteRun(spec -> spec.recipe(new FindReactReduxManifestRisks()),
                json(before, source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    for (String message : messages) assertTrue(out.contains(message), out);
                })));
    }

    private void assertSource(String before, String... messages) {
        rewriteRun(spec -> spec.recipe(new FindReactReduxSourceRisks()),
                typescript(before, source -> source.path("src/fixture.tsx").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    for (String message : messages) assertTrue(out.contains(message), out);
                })));
    }

    private static void assertNoMarker(String source) {
        assertFalse(source.contains("~~("), source);
    }
}
