package com.huawei.clouds.openrewrite.reactredux;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class ReactReduxDependencyTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.reactredux.UpgradeReactReduxTo9_3_0";
    static final String MIGRATE = "com.huawei.clouds.openrewrite.reactredux.MigrateReactReduxTo9_3_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "selected {0} -> {1}")
    @MethodSource("selectedDeclarations")
    void upgradesEveryWorkbookDeclarationForm(String before, String after) {
        rewriteRun(json("{\"dependencies\":{\"react-redux\":\"" + before + "\"}}",
                "{\"dependencies\":{\"react-redux\":\"" + after + "\"}}",
                source -> source.path("package.json")));
    }

    static Stream<Arguments> selectedDeclarations() {
        return Stream.of("7.2.2", "7.2.4", "7.2.8", "7.2.9", "8.0.2", "8.0.5")
                .flatMap(version -> Stream.of(
                        Arguments.of(version, "9.3.0"),
                        Arguments.of("^" + version, "^9.3.0"),
                        Arguments.of("~" + version, "~9.3.0")));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {"dependencies":{"react-redux":"7.2.2"},"devDependencies":{"react-redux":"^7.2.4"},"peerDependencies":{"react-redux":"~7.2.8"},"optionalDependencies":{"react-redux":"8.0.5"}}
                """,
                """
                {"dependencies":{"react-redux":"9.3.0"},"devDependencies":{"react-redux":"^9.3.0"},"peerDependencies":{"react-redux":"~9.3.0"},"optionalDependencies":{"react-redux":"9.3.0"}}
                """,
                source -> source.path("packages/ui/package.json")));
    }

    @ParameterizedTest(name = "complex range NOOP {0}")
    @ValueSource(strings = {
            ">=7.2.2", ">=7.2.2 <9", "<=8.0.5", "7.2.2 || 8.0.5", "7.2.2 - 8.0.5",
            "7.x", "7.2.x", "*", ">7.2.4", "<9.3.0", "^7.2.8 || ^8.0.5", ">=8"
    })
    void leavesComplexRangesUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "protocol/alias NOOP {0}")
    @ValueSource(strings = {
            "workspace:7.2.2", "workspace:^8.0.5", "npm:@company/react-redux@7.2.4",
            "github:reduxjs/react-redux#v7.2.8", "git+https://github.com/reduxjs/react-redux.git#v8.0.2",
            "file:../react-redux", "link:../react-redux", "https://example.test/react-redux-7.2.9.tgz",
            "catalog:react", "$REACT_REDUX_VERSION"
    })
    void leavesProtocolsAliasesForksAndDynamicDeclarationsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "unlisted or decorated NOOP {0}")
    @ValueSource(strings = {
            "7.2.1", "7.2.3", "7.2.5", "7.2.6", "7.2.7", "8.0.0", "8.0.1", "8.0.3", "8.1.0",
            "9.0.0", "9.3.0", "^9.3.0", "~9.3.0", "v7.2.2", "=8.0.5", "8.0.5-beta.1",
            "7.2.2+company.1", "latest", "next"
    })
    void leavesUnlistedTargetAndDecoratedDeclarationsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void doesNotUpgradeCentralOwnersCatalogsOrNestedLookalikes() {
        rewriteRun(json(
                """
                {"overrides":{"react-redux":"7.2.2"},"resolutions":{"**/react-redux":"7.2.4"},"pnpm":{"overrides":{"app>react-redux":"7.2.8"}},"catalog":{"react-redux":"7.2.9"},"catalogs":{"legacy":{"react-redux":"8.0.2"}},"tool":{"dependencies":{"react-redux":"8.0.5"}}}
                """, source -> source.path("package.json")));
    }

    @Test
    void ignoresLockfilesOtherJsonSimilarNamesAndNonStrings() {
        rewriteRun(
                json("{\"packages\":{\"\":{\"dependencies\":{\"react-redux\":\"7.2.2\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"react-redux\":\"7.2.4\"}}", source -> source.path("deps.json")),
                json("{\"dependencies\":{\"preact-redux\":\"7.2.8\",\"@types/react-redux\":\"7.2.9\",\"react-redux-extra\":\"8.0.2\",\"react-redux\":false}}",
                        source -> source.path("package.json")));
    }

    @Test
    void excludesLowercasedArtifactParentsIncludingGeneratedAndInstallPrefixes() {
        rewriteRun(
                json("{\"dependencies\":{\"react-redux\":\"7.2.2\"}}", source -> source.path("NODE_MODULES/a/package.json")),
                json("{\"dependencies\":{\"react-redux\":\"7.2.4\"}}", source -> source.path("GeneratedFixtures/package.json")),
                json("{\"dependencies\":{\"react-redux\":\"7.2.8\"}}", source -> source.path("INSTALLER/cache/package.json")),
                json("{\"dependencies\":{\"react-redux\":\"7.2.9\"}}", source -> source.path(".M2/cache/package.json")),
                json("{\"dependencies\":{\"react-redux\":\"8.0.2\"}}", source -> source.path("REPORTS/package.json")),
                json("{\"dependencies\":{\"react-redux\":\"8.0.5\"}}", source -> source.path("apps/install/package.json")),
                json("{\"dependencies\":{\"react-redux\":\"8.0.5\"}}",
                        "{\"dependencies\":{\"react-redux\":\"9.3.0\"}}", source -> source.path("apps/web/package.json")));
    }

    @Test
    void upgradesPinnedRenProjectManifestButPreservesCompanionsForReview() {
        rewriteRun(json(
                """
                {"dependencies":{"@reduxjs/toolkit":"^1.4.0","@types/react":"^16.9.0","@types/react-redux":"^7.1.9","react":"^17.0.1","react-dom":"^17.0.1","react-redux":"^7.2.2","redux":"^4.1.0","typescript":"^4.5.5"}}
                """,
                """
                {"dependencies":{"@reduxjs/toolkit":"^1.4.0","@types/react":"^16.9.0","@types/react-redux":"^7.1.9","react":"^17.0.1","react-dom":"^17.0.1","react-redux":"^9.3.0","redux":"^4.1.0","typescript":"^4.5.5"}}
                """, source -> source.path("fixtures/renproject-bridge-v2/package.json")));
    }

    @Test
    void upgradesPinnedReactNativeV2exManifest() {
        rewriteRun(json(
                """
                {"dependencies":{"@types/react":"18.0.28","react":"18.2.0","react-native":"0.71.5","react-redux":"^8.0.5","redux":"^4.2.1"},"devDependencies":{"typescript":"4.9.5"}}
                """,
                """
                {"dependencies":{"@types/react":"18.0.28","react":"18.2.0","react-native":"0.71.5","react-redux":"^9.3.0","redux":"^4.2.1"},"devDependencies":{"typescript":"4.9.5"}}
                """, source -> source.path("fixtures/funnyzak-react-native-v2ex/package.json")));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"react-redux\":\"~7.2.9\"}}",
                        "{\"dependencies\":{\"react-redux\":\"~9.3.0\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void recipesAreDiscoverableAndValid() {
        Environment environment = environment();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> UPGRADE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATE.equals(recipe.getName())));
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
        assertEquals(1, upgrade.getRecipeList().size());
        assertTrue(upgrade.getRecipeList().get(0) instanceof UpgradeSelectedReactReduxDependency);
        assertEquals(UPGRADE, migrate.getRecipeList().get(0).getName());
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"react-redux\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.reactredux")
                .scanYamlResources()
                .build();
    }
}
