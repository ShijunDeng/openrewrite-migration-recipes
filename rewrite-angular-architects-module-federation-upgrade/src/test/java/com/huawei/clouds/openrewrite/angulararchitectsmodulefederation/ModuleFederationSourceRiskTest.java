package com.huawei.clouds.openrewrite.angulararchitectsmodulefederation;

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

class ModuleFederationSourceRiskTest implements RewriteTest {
    @ParameterizedTest(name = "marks exact source risk {0}")
    @MethodSource("sourceRisks")
    void marksExactOwnedSourceRisks(String label, String source, String message) {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                javascript(source, input -> input.path("src/" + label + ".js").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> sourceRisks() {
        return Stream.of(
                Arguments.of("webpack-entry", "const mf=require('@angular-architects/module-federation/webpack');", "Classic webpack ownership"),
                Arguments.of("private-deep", "const x=require('@angular-architects/module-federation/src/utils/share-utils');", "CommonJS deep import"),
                Arguments.of("share-all", "const {shareAll}=require('@angular-architects/module-federation/webpack'); module.exports={shared:shareAll({singleton:true})};", "sharing policy"),
                Arguments.of("with-plugin", "const {withModuleFederationPlugin}=require('@angular-architects/module-federation/webpack'); module.exports=withModuleFederationPlugin({});", "sharing policy"),
                Arguments.of("shared-mappings", "const mf=require('@angular-architects/module-federation/webpack'); const x=new mf.SharedMappings();", "SharedMappings is legacy"),
                Arguments.of("strict", "const mf=require('@angular-architects/module-federation/webpack'); const x={shared:{singleton:true,strictVersion:true,requiredVersion:'auto'}};", "ABI/runtime policy"),
                Arguments.of("remotes", "const mf=require('@angular-architects/module-federation/webpack'); const x={remotes:{mfe:'http://host/remoteEntry.js'}};", "cross-deployment contract"),
                Arguments.of("exposes", "const mf=require('@angular-architects/module-federation/webpack'); const x={name:'mfe',filename:'remoteEntry.js',exposes:{'./A':'./src/a'}};", "cross-deployment contract"),
                Arguments.of("bootstrap", "import '@angular-architects/module-federation'; import('./bootstrap').catch(console.error);", "async bootstrap boundary"),
                Arguments.of("load", "import {loadRemoteModule} from '@angular-architects/module-federation'; loadRemoteModule({type:'module',remoteEntry:'http://mfe/remoteEntry.js',exposedModule:'./A'});", "deployment-sensitive")
        );
    }

    @Test
    void marksRealInfosysLegacyWebpackConfigAtFixedCommit() {
        // Infosys/Infosys-Responsible-AI-Toolkit@598d0b470a6cf25ad717f89092cb4f4bf49a206b
        String source = """
                const ModuleFederationPlugin = require("webpack/lib/container/ModuleFederationPlugin");
                const mf = require("@angular-architects/module-federation/webpack");
                const share = mf.share;
                const sharedMappings = new mf.SharedMappings();
                module.exports = {output:{uniqueName:"rAIFrontend",publicPath:"auto"},plugins:[new ModuleFederationPlugin({name:"rAIFrontend",filename:"remoteEntry.js",exposes:{'./RemoteMfeModule':'./src/app/remote-mfe/remote-mfe.module.ts'},shared:share({"@angular/core":{singleton:true,strictVersion:false,requiredVersion:'auto'},...sharedMappings.getDescriptors()})}),sharedMappings.getPlugin()]};
                """;
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                javascript(source, input -> input.path("webpack.config.js").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertContains(printed, "Classic webpack ownership");
                    assertContains(printed, "SharedMappings is legacy");
                    assertContains(printed, "cross-deployment contract");
                    assertContains(printed, "ABI/runtime policy");
                })));
    }

    @Test
    void marksRealFledgeSharingPolicyAtFixedCommit() {
        // fledge-iot/fledge-gui@ba3efe4a011ee32b24ba4f4b5785603dca4d03a1
        String source = """
                const mf = require("@angular-architects/module-federation/webpack");
                const share = mf.share;
                module.exports={output:{uniqueName:'fledge',publicPath:'auto',scriptType:'text/javascript'},experiments:{outputModule:true},shared:share({'@angular/core':{singleton:true,strictVersion:true,requiredVersion:'auto'}})};
                """;
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                javascript(source, input -> input.path("webpack.config.js").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "ABI/runtime policy");
                    assertContains(after.printAll(), "cross-deployment contract");
                })));
    }

    @Test
    void marksRealEdumserranoDynamicRouteAtFixedCommit() {
        // edumserrano/webpack-module-federation-with-angular@d0638a3908b650ea67f96e88a06fc190062c344c
        String source = """
                import { loadRemoteModule } from '@angular-architects/module-federation';
                const route = {loadChildren: () => loadRemoteModule({type:'module',remoteEntry:'http://localhost:4201/remoteEntry.js',exposedModule:'./my-feature-module'}).then(m => m.MyFeatureModule)};
                """;
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                typescript(source, input -> input.path("src/app/app-routing.module.ts").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), "deployment-sensitive"))));
    }

    @Test
    void marksOfficialTargetStackEntrypointsAtFixedCommit() {
        // angular-architects/module-federation-plugin@d4bf6f035b01631fa7f1bf6f98838ae94db2f8ef
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                typescript("import {withFederation,shareAll} from '@angular-architects/module-federation/rspack'; export default withFederation({options:{shared:shareAll({singleton:true})}});",
                        input -> input.path("federation.config.ts").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "fixes the federation stack"))));
    }

    @Test
    void tracksNamedAndRequireAliasesToOwnedCalls() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                typescript("import {loadRemoteModule as loadMf} from '@angular-architects/module-federation'; loadMf({remoteEntry:'https://mfe/remoteEntry.js'});",
                        input -> input.path("src/route.ts").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "deployment-sensitive"))),
                javascript("const {shareAll: shareMf}=require('@angular-architects/module-federation/webpack'); module.exports={shared:shareMf({singleton:true})};",
                        input -> input.path("webpack.config.js").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "sharing policy"))));
    }

    @Test
    void marksDynamicDeepImportAtTheExactLoad() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                javascript("async function load(){ return import('@angular-architects/module-federation/src/internal'); }",
                        input -> input.path("src/load.js").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "dynamic/CommonJS deep import"))));
    }

    @Test
    void sameNamedLocalCallInRelevantFileIsNotClaimedAsFederationApi() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                javascript("const {shareAll}=require('@angular-architects/module-federation/webpack'); function loadRemoteModule(x){return x;} loadRemoteModule({remoteEntry:'local-value'}); shareAll({});",
                        input -> input.path("webpack.config.js").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "sharing policy");
                            assertFalse(printed.contains("Remote runtime loading is deployment-sensitive"), printed);
                        })));
    }

    @Test
    void shadowedImportedAndNamespaceBindingsAreConservativelyIgnored() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                typescript("import {loadRemoteModule as loadMf} from '@angular-architects/module-federation'; function local(loadMf){ return loadMf({value:1}); }",
                        input -> input.path("src/shadow.ts").after(actual -> actual).afterRecipe(after ->
                                assertFalse(after.printAll().contains("Remote runtime loading is deployment-sensitive"), after.printAll()))),
                javascript("const mf=require('@angular-architects/module-federation/webpack'); function local(mf){ return mf.shareAll({}); }",
                        input -> input.path("webpack.config.js").after(actual -> actual).afterRecipe(after ->
                                assertFalse(after.printAll().contains("Revalidate the target sharing policy"), after.printAll()))));
    }

    @Test
    void similarlyPrefixedPackageIsNotADeepImportAndGenericMetadataIsNotRemoteAbi() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                javascript("const runtime=require('@angular-architects/module-federation-runtime'); runtime.loadRemoteModule({});",
                        input -> input.path("src/runtime.js")),
                javascript("const mf=require('@angular-architects/module-federation/webpack'); const metadata={name:'Ada',filename:'avatar.png',library:'ui',publicPath:'/assets',uniqueName:'profile'}; console.log(mf, metadata);",
                        input -> input.path("webpack.config.js").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "Classic webpack ownership");
                            assertFalse(printed.contains("cross-deployment contract"), printed);
                        })));
    }

    @Test
    void leavesUnownedSameNamedCallsAlone() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                javascript("const shareAll=local; const x={remotes:{},shared:{}}; shareAll(x);", source -> source.path("src/local.js")));
    }

    @Test
    void skipsGeneratedAndInstalledSources() {
        rewriteRun(spec -> spec.recipe(new FindModuleFederationSourceRisks()),
                javascript("const mf=require('@angular-architects/module-federation/webpack'); const x={remotes:{mfe:'remoteEntry.js'}};",
                        source -> source.path("node_modules/pkg/webpack.config.js")),
                javascript("const mf=require('@angular-architects/module-federation/webpack'); const x={remotes:{mfe:'remoteEntry.js'}};",
                        source -> source.path("dist/webpack.config.js")),
                javascript("const mf=require('@angular-architects/module-federation/webpack'); const x={remotes:{mfe:'remoteEntry.js'}};",
                        source -> source.path(".turbo/webpack.config.js")),
                javascript("const mf=require('@angular-architects/module-federation/webpack'); const x={remotes:{mfe:'remoteEntry.js'}};",
                        source -> source.path("storybook-static/webpack.config.js")));
    }

    @Test
    void recommendedRecipePerformsAutoAndMarksRemainingRisksIdempotently() {
        rewriteRun(spec -> spec.recipe(ModuleFederationDependencyTest.environment().activateRecipes(ModuleFederationDependencyTest.MIGRATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                org.openrewrite.json.Assertions.json(
                        """
                        {"scripts":{"mf:init":"ng add @angular-architects/module-federation --project shell --port 4200"},"dependencies":{"@angular/core":"15.2.10","@angular-architects/module-federation":"15.0.3","@angular-architects/module-federation-runtime":"15.0.3"},"devDependencies":{"ngx-build-plus":"15.0.0"}}
                        """, input -> input.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "20.0.0");
                            assertContains(printed, "--stack module-federation-webpack");
                            assertContains(printed, "belongs to the Angular 20 line");
                        })),
                javascript("const {withModuleFederationPlugin,shareAll}=require('@angular-architects/module-federation/webpack'); module.exports=withModuleFederationPlugin({shared:shareAll({singleton:true}),remotes:{mfe:'http://mfe/remoteEntry.js'}});",
                        input -> input.path("webpack.config.js").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "Classic webpack ownership");
                            assertContains(after.printAll(), "sharing policy");
                        })));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
