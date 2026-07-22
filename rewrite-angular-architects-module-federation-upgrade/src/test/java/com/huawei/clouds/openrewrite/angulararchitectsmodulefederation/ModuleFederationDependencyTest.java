package com.huawei.clouds.openrewrite.angulararchitectsmodulefederation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class ModuleFederationDependencyTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.angulararchitectsmodulefederation.UpgradeModuleFederationTo20_0_0";
    static final String MIGRATE = "com.huawei.clouds.openrewrite.angulararchitectsmodulefederation.MigrateModuleFederationTo20_0_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @Test
    void workbookVersionsAreComplete() {
        assertEquals(java.util.Set.of("15.0.3", "16.0.4"), ModuleFederationSupport.SOURCES);
        assertEquals("20.0.0", ModuleFederationSupport.TARGET);
    }

    @ParameterizedTest(name = "upgrades selected declaration {0}")
    @MethodSource("selectedDeclarations")
    void upgradesOnlyExactCaretAndTildeSources(String declaration) {
        rewriteRun(json(pkg("dependencies", declaration), pkg("dependencies", "20.0.0"),
                source -> source.path("package.json")));
    }

    static Stream<String> selectedDeclarations() {
        return Stream.of("15.0.3", "^15.0.3", "~15.0.3", "16.0.4", "^16.0.4", "~16.0.4");
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {"dependencies":{"@angular-architects/module-federation":"15.0.3"},"devDependencies":{"@angular-architects/module-federation":"^16.0.4"},"peerDependencies":{"@angular-architects/module-federation":"~15.0.3"},"optionalDependencies":{"@angular-architects/module-federation":"16.0.4"}}
                """,
                """
                {"dependencies":{"@angular-architects/module-federation":"20.0.0"},"devDependencies":{"@angular-architects/module-federation":"20.0.0"},"peerDependencies":{"@angular-architects/module-federation":"20.0.0"},"optionalDependencies":{"@angular-architects/module-federation":"20.0.0"}}
                """, source -> source.path("package.json")));
    }

    @Test
    void upgradesRealInfosysManifestAtFixedCommit() {
        // Infosys/Infosys-Responsible-AI-Toolkit@598d0b470a6cf25ad717f89092cb4f4bf49a206b
        rewriteRun(json(
                """
                {"dependencies":{"@angular-architects/module-federation":"15.0.3","@angular/core":"15.2.10"},"devDependencies":{"ngx-build-plus":"15.0.0","typescript":"4.9.5"}}
                """,
                """
                {"dependencies":{"@angular-architects/module-federation":"20.0.0","@angular/core":"15.2.10"},"devDependencies":{"ngx-build-plus":"15.0.0","typescript":"4.9.5"}}
                """, source -> source.path("package.json")));
    }

    @Test
    void upgradesRealFledgeManifestAtFixedCommit() {
        // fledge-iot/fledge-gui@ba3efe4a011ee32b24ba4f4b5785603dca4d03a1
        rewriteRun(json(
                """
                {"dependencies":{"@angular-architects/module-federation":"~16.0.4","@angular/core":"^16.2.12","rxjs":"~6.6.6"},"devDependencies":{"ngx-build-plus":"^13.0.1"}}
                """,
                """
                {"dependencies":{"@angular-architects/module-federation":"20.0.0","@angular/core":"^16.2.12","rxjs":"~6.6.6"},"devDependencies":{"ngx-build-plus":"^13.0.1"}}
                """, source -> source.path("package.json")));
    }

    @Test
    void upgradesRealEdumserranoWorkspaceManifestAtFixedCommit() {
        // edumserrano/webpack-module-federation-with-angular@d0638a3908b650ea67f96e88a06fc190062c344c
        rewriteRun(json(
                """
                {"dependencies":{"@angular/core":"^16.1.0"},"devDependencies":{"@angular-architects/module-federation":"^16.0.4","ngx-build-plus":"^16.0.0"}}
                """,
                """
                {"dependencies":{"@angular/core":"^16.1.0"},"devDependencies":{"@angular-architects/module-federation":"20.0.0","ngx-build-plus":"^16.0.0"}}
                """, source -> source.path("apps/shell/package.json")));
    }

    @ParameterizedTest(name = "leaves unselected version {0}")
    @ValueSource(strings = {"14.3.0", "15.0.0", "15.0.2", "15.0.4", "16.0.0", "16.0.3", "16.0.5", "17.0.0", "18.0.6", "19.0.3", "20.0.0", "21.2.2"})
    void leavesUnselectedVersions(String version) {
        rewriteRun(json(pkg("dependencies", version), source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves externally owned declaration {0}")
    @ValueSource(strings = {">=15.0.3", "15.x", "15.0.3 || 16.0.4", "15.0.3 - 20.0.0", "workspace:^15.0.3", "workspace:*", "catalog:", "npm:fork@15.0.3", "file:../mf", "link:../mf", "git+https://github.com/example/fork.git", "https://registry.example/mf.tgz", "latest", "*"})
    void leavesRangesProtocolsAliasesWorkspacesAndTags(String declaration) {
        rewriteRun(json(pkg("dependencies", declaration), source -> source.path("package.json")));
    }

    @Test
    void protectsNestedConfigOverridesResolutionsAndMetadata() {
        rewriteRun(json(
                """
                {"name":"@angular-architects/module-federation","version":"15.0.3","overrides":{"@angular-architects/module-federation":"15.0.3"},"resolutions":{"@angular-architects/module-federation":"16.0.4"},"pnpm":{"overrides":{"@angular-architects/module-federation":"15.0.3"}},"config":{"dependencies":{"@angular-architects/module-federation":"15.0.3"}}}
                """, source -> source.path("package.json")));
    }

    @Test
    void protectsOtherPackagesAndNonPackageJson() {
        rewriteRun(
                json("{\"dependencies\":{\"@angular-architects/native-federation\":\"15.0.3\",\"@angular-architects/module-federation-runtime\":\"16.0.4\"}}", source -> source.path("package.json")),
                json(pkg("dependencies", "15.0.3"), source -> source.path("fixture.json")));
    }

    @ParameterizedTest(name = "skips generated/install path {0}")
    @ValueSource(strings = {"node_modules/a/package.json", ".pnpm/a/package.json", ".yarn/cache/package.json", ".angular/cache/package.json", ".nx/cache/package.json", "dist/package.json", "build/package.json", "target/package.json", "generated/package.json", "generated-client/package.json", "install/package.json", "vendor/package.json"})
    void skipsGeneratedAndInstalledTrees(String path) {
        rewriteRun(json(pkg("dependencies", "15.0.3"), source -> source.path(path)));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json(pkg("devDependencies", "^16.0.4"), pkg("devDependencies", "20.0.0"),
                        source -> source.path("package.json")));
    }

    @Test
    void recipesAreDiscoverableAndValid() {
        Recipe upgrade = environment().activateRecipes(UPGRADE);
        Recipe migrate = environment().activateRecipes(MIGRATE);
        assertFalse(upgrade.getRecipeList().isEmpty());
        assertFalse(migrate.getRecipeList().isEmpty());
        assertTrue(upgrade.validateAll().stream().allMatch(validation -> validation.isValid()));
        assertTrue(migrate.validateAll().stream().allMatch(validation -> validation.isValid()));
    }

    @ParameterizedTest(name = "migrates deterministic command {0}")
    @MethodSource("scriptMigrations")
    void migratesOnlyStaticClassicWebpackScripts(String before, String after) {
        rewriteRun(spec -> spec.recipe(new MigrateSelectedClassicWebpackCompanions()),
                json(manifestWithScript(before), manifestWithScript(after), source -> source.path("package.json")));
    }

    static Stream<Arguments> scriptMigrations() {
        return Stream.of(
                Arguments.of("ng add @angular-architects/module-federation --project shell --port 4200 --type host", "ng add @angular-architects/module-federation --project shell --port 4200 --type host --stack module-federation-webpack"),
                Arguments.of("npx ng add @angular-architects/module-federation --project mfe --port 4201", "npx ng add @angular-architects/module-federation --project mfe --port 4201 --stack module-federation-webpack"),
                Arguments.of("ng g @angular-architects/module-federation:init --project shell --port 4200", "ng g @angular-architects/module-federation:init-webpack --project shell --port 4200"),
                Arguments.of("nx generate @angular-architects/module-federation:config --project app", "nx generate @angular-architects/module-federation:init-webpack --project app"),
                Arguments.of("pnpm exec ng add @angular-architects/module-federation", "pnpm exec ng add @angular-architects/module-federation --stack module-federation-webpack"),
                Arguments.of("  ng add @angular-architects/module-federation  ", "  ng add @angular-architects/module-federation --stack module-federation-webpack  ")
        );
    }

    @ParameterizedTest(name = "leaves ambiguous command {0}")
    @ValueSource(strings = {
            "ng add @angular-architects/module-federation --stack native-federation-esbuild",
            "ng add @angular-architects/module-federation && npm install",
            "ng add @angular-architects/module-federation --project $PROJECT",
            "node node_modules/@angular-architects/module-federation/src/server/mf-dev-server.js",
            "echo @angular-architects/module-federation",
            "ng g @angular-architects/module-federation:init-rspack"
    })
    void leavesDynamicChainedAndExplicitStackScripts(String command) {
        rewriteRun(spec -> spec.recipe(new MigrateSelectedClassicWebpackCompanions()),
                json(manifestWithScript(command), source -> source.path("package.json")));
    }

    @Test
    void alignsOnlyOfficialGeneratedClassicCompanions() {
        rewriteRun(spec -> spec.recipe(new MigrateSelectedClassicWebpackCompanions()),
                json(
                        """
                        {"dependencies":{"@angular-architects/module-federation":"15.0.3","@angular-architects/module-federation-runtime":"^15.0.3"},"devDependencies":{"ngx-build-plus":"~15.0.0"}}
                        """,
                        """
                        {"dependencies":{"@angular-architects/module-federation":"15.0.3","@angular-architects/module-federation-runtime":"20.0.0"},"devDependencies":{"ngx-build-plus":"20.0.0"}}
                        """, source -> source.path("package.json")));
    }

    @Test
    void alignsOnlyCompanionsFromTheSameSelectedReleaseLine() {
        rewriteRun(spec -> spec.recipe(new MigrateSelectedClassicWebpackCompanions()),
                json(
                        """
                        {"dependencies":{"@angular-architects/module-federation":"^16.0.4","@angular-architects/module-federation-runtime":"~16.0.4"},"devDependencies":{"ngx-build-plus":"^16.0.0"}}
                        """,
                        """
                        {"dependencies":{"@angular-architects/module-federation":"^16.0.4","@angular-architects/module-federation-runtime":"20.0.0"},"devDependencies":{"ngx-build-plus":"20.0.0"}}
                        """, source -> source.path("packages/shell/package.json")));
    }

    @Test
    void companionAutoRejectsCrossLineAndMixedMainOwners() {
        rewriteRun(spec -> spec.recipe(new MigrateSelectedClassicWebpackCompanions()),
                json("{" +
                     "\"dependencies\":{\"@angular-architects/module-federation\":\"15.0.3\",\"@angular-architects/module-federation-runtime\":\"16.0.4\"}," +
                     "\"devDependencies\":{\"ngx-build-plus\":\"16.0.0\"}}", source -> source.path("package.json")),
                json("{" +
                     "\"dependencies\":{\"@angular-architects/module-federation\":\"15.0.3\",\"@angular-architects/module-federation-runtime\":\"15.0.3\"}," +
                     "\"devDependencies\":{\"@angular-architects/module-federation\":\"16.0.4\",\"ngx-build-plus\":\"15.0.0\"}}",
                        source -> source.path("packages/mixed/package.json")));
    }

    @Test
    void nestedOrOverrideVersionCannotAuthorizeCompanionOrScriptAuto() {
        rewriteRun(spec -> spec.recipe(new MigrateSelectedClassicWebpackCompanions()),
                json("""
                        {"scripts":{"mf:init":"ng add @angular-architects/module-federation"},"dependencies":{"@angular-architects/module-federation-runtime":"15.0.3"},"devDependencies":{"ngx-build-plus":"15.0.0"},"config":{"dependencies":{"@angular-architects/module-federation":"15.0.3"}},"overrides":{"@angular-architects/module-federation":"16.0.4"}}
                        """, source -> source.path("package.json")));
    }

    @Test
    void companionAutoSkipsGeneratedTreesAndIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateSelectedClassicWebpackCompanions()),
                json("{" +
                     "\"dependencies\":{\"@angular-architects/module-federation\":\"15.0.3\",\"@angular-architects/module-federation-runtime\":\"15.0.3\"}," +
                     "\"devDependencies\":{\"ngx-build-plus\":\"15.0.0\"}}", source -> source.path(".cache/package.json")));
        rewriteRun(spec -> spec.recipe(new MigrateSelectedClassicWebpackCompanions()).cycles(2)
                        .expectedCyclesThatMakeChanges(1),
                json("{" +
                     "\"dependencies\":{\"@angular-architects/module-federation\":\"15.0.3\",\"@angular-architects/module-federation-runtime\":\"15.0.3\"}," +
                     "\"devDependencies\":{\"ngx-build-plus\":\"15.0.0\"}}",
                     "{" +
                     "\"dependencies\":{\"@angular-architects/module-federation\":\"15.0.3\",\"@angular-architects/module-federation-runtime\":\"20.0.0\"}," +
                     "\"devDependencies\":{\"ngx-build-plus\":\"20.0.0\"}}", source -> source.path("package.json")));
    }

    @Test
    void companionAutoProtectsUnselectedOwnersAndUnrelatedManifest() {
        rewriteRun(spec -> spec.recipe(new MigrateSelectedClassicWebpackCompanions()),
                json("{\"dependencies\":{\"@angular-architects/module-federation\":\"^15.0.3\",\"@angular-architects/module-federation-runtime\":\">=15\"},\"devDependencies\":{\"ngx-build-plus\":\"^13.0.1\"}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"@angular-architects/module-federation-runtime\":\"15.0.3\"},\"devDependencies\":{\"ngx-build-plus\":\"15.0.0\"}}", source -> source.path("workspace/package.json")));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angulararchitectsmodulefederation")
                .scanYamlResources().build();
    }

    static String pkg(String section, String declaration) {
        return "{\"name\":\"app\",\"" + section + "\":{\"@angular-architects/module-federation\":\"" + declaration + "\"}}";
    }

    private static String manifestWithScript(String command) {
        return "{\"scripts\":{\"mf:init\":\"" + ModuleFederationSupport.escape(command) + "\"},\"devDependencies\":{\"@angular-architects/module-federation\":\"16.0.4\"}}";
    }
}
