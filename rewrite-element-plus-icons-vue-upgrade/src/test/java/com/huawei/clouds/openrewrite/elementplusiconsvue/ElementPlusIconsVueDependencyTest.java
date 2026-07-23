package com.huawei.clouds.openrewrite.elementplusiconsvue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class ElementPlusIconsVueDependencyTest implements RewriteTest {
    static final String UPGRADE =
            "com.huawei.clouds.openrewrite.elementplusiconsvue.UpgradeElementPlusIconsVueTo2_3_2";
    static final String MIGRATE =
            "com.huawei.clouds.openrewrite.elementplusiconsvue.MigrateElementPlusIconsVueTo2_3_2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "selected {0} -> {1}")
    @CsvSource({
            "0.2.4, 2.3.2", "^0.2.4, ^2.3.2", "~0.2.4, ~2.3.2",
            "2.0.10, 2.3.2", "^2.0.10, ^2.3.2", "~2.0.10, ~2.3.2",
            "2.1.0, 2.3.2", "^2.1.0, ^2.3.2", "~2.1.0, ~2.3.2"
    })
    void upgradesEveryWorkbookSelectedScalar(String before, String after) {
        assertUpgrade(before, after);
    }

    @Test
    void upgradesAllFourDirectSections() {
        rewriteRun(json(
                """
                {"dependencies":{"@element-plus/icons-vue":"0.2.4"},"devDependencies":{"@element-plus/icons-vue":"^2.0.10"},"peerDependencies":{"@element-plus/icons-vue":"~2.1.0"},"optionalDependencies":{"@element-plus/icons-vue":"2.1.0"}}
                """,
                """
                {"dependencies":{"@element-plus/icons-vue":"2.3.2"},"devDependencies":{"@element-plus/icons-vue":"^2.3.2"},"peerDependencies":{"@element-plus/icons-vue":"~2.3.2"},"optionalDependencies":{"@element-plus/icons-vue":"2.3.2"}}
                """, source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "complex NOOP {0}")
    @ValueSource(strings = {
            ">=0.2.4", ">=0.2.4 <3", "0.2.4 || 2.1.0", "0.2.4 - 2.3.2", "0.x", "2.1.x",
            "*", "^0.2.4 || ^2.1.0", "=0.2.4", "v2.1.0", "2.1.0-beta.1", "2.1.0+build.1"
    })
    void protectsComplexRangesAndDecoratedVersions(String value) {
        assertNoOp(value);
    }

    @ParameterizedTest(name = "protocol NOOP {0}")
    @ValueSource(strings = {
            "workspace:0.2.4", "workspace:^2.1.0", "npm:@company/icons-vue@2.1.0",
            "github:element-plus/element-plus-icons#v2.1.0", "git+https://example.test/icons.git#v2.1.0",
            "file:../icons", "link:../icons", "https://example.test/icons-vue-2.1.0.tgz", "catalog:", "latest", "next"
    })
    void protectsProtocolsAliasesForksCatalogsAndTags(String value) {
        assertNoOp(value);
    }

    @ParameterizedTest(name = "unlisted/target NOOP {0}")
    @ValueSource(strings = {
            "0.2.3", "0.2.5", "1.0.0", "2.0.9", "2.0.11", "2.1.1", "2.2.0",
            "2.3.1", "2.3.2", "^2.3.2", "~2.3.2", "3.0.0", ""
    })
    void protectsUnlistedAndTargetVersions(String value) {
        assertNoOp(value);
    }

    @Test
    void protectsCentralOwnersNestedObjectsLockfilesAndNonStrings() {
        rewriteRun(
                json("""
                     {"overrides":{"@element-plus/icons-vue":"0.2.4"},"resolutions":{"@element-plus/icons-vue":"2.0.10"},"pnpm":{"overrides":{"app>@element-plus/icons-vue":"2.1.0"}},"catalog":{"@element-plus/icons-vue":"0.2.4"},"tool":{"dependencies":{"@element-plus/icons-vue":"2.1.0"}},"dependencies":{"@element-plus/icons-vue":false}}
                     """, source -> source.path("package.json")),
                json("{" + "\"packages\":{\"\":{\"dependencies\":{\"@element-plus/icons-vue\":\"0.2.4\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"0.2.4\"}}",
                        source -> source.path("fixtures/dependencies.json")));
    }

    @Test
    void filtersArtifactParentsAndKeepsOrdinaryLeafNamesEligible() {
        rewriteRun(
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"0.2.4\"}}", source -> source.path("node_modules/pkg/package.json")),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"0.2.4\"}}", source -> source.path("GeneratedClient/package.json")),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"0.2.4\"}}", source -> source.path("installation-cache/pkg/package.json")),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"0.2.4\"}}", source -> source.path(".pnpm/pkg/package.json")),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"0.2.4\"}}", source -> source.path(".m2/pkg/package.json")),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"0.2.4\"}}",
                        "{\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\"}}", source -> source.path("packages/widgets/package.json")));
    }

    @Test
    void pinnedRealRepositoryFixtures() {
        rewriteRun(
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"^0.2.4\",\"element-plus\":\"^2.0.2\"}}",
                        "{\"dependencies\":{\"@element-plus/icons-vue\":\"^2.3.2\",\"element-plus\":\"^2.0.2\"}}",
                        source -> source.path("fixtures/song-will-vue3-ts/package.json")),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"^2.0.10\",\"vue\":\"^3.2.37\"}}",
                        "{\"dependencies\":{\"@element-plus/icons-vue\":\"^2.3.2\",\"vue\":\"^3.2.37\"}}",
                        source -> source.path("fixtures/newPermissionBaseLocalMock/package.json")),
                json("{\"devDependencies\":{\"@element-plus/icons-vue\":\"^2.1.0\"}}",
                        "{\"devDependencies\":{\"@element-plus/icons-vue\":\"^2.3.2\"}}",
                        source -> source.path("fixtures/vite-vue3-ts-pinia-demo/package.json")));
    }

    @Test
    void convergesInTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"~0.2.4\"}}",
                        "{\"dependencies\":{\"@element-plus/icons-vue\":\"~2.3.2\"}}",
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
    }

    private void assertUpgrade(String before, String after) {
        rewriteRun(json("{\"dependencies\":{\"@element-plus/icons-vue\":\"" + before + "\"}}",
                "{\"dependencies\":{\"@element-plus/icons-vue\":\"" + after + "\"}}",
                source -> source.path("package.json")));
    }

    private void assertNoOp(String value) {
        rewriteRun(json("{\"dependencies\":{\"@element-plus/icons-vue\":\"" + value + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.elementplusiconsvue")
                .scanYamlResources()
                .build();
    }
}
