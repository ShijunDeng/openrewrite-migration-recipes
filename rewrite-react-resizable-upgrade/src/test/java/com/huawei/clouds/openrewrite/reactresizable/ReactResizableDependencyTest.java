package com.huawei.clouds.openrewrite.reactresizable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class ReactResizableDependencyTest implements RewriteTest {
    static final String UPGRADE =
            "com.huawei.clouds.openrewrite.reactresizable.UpgradeReactResizableTo3_1_3";
    static final String MIGRATE =
            "com.huawei.clouds.openrewrite.reactresizable.MigrateReactResizableTo3_1_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @Test
    void workbookSelectionIsExact() {
        assertEquals("1.11.1", ReactResizableSupport.SOURCE);
        assertEquals("3.1.3", ReactResizableSupport.TARGET);
    }

    @ParameterizedTest(name = "upgrades declaration {0}")
    @ValueSource(strings = {"1.11.1", "^1.11.1", "~1.11.1"})
    void upgradesExactCaretAndTilde(String declaration) {
        rewriteRun(json(pkg("dependencies", declaration), pkg("dependencies", "3.1.3"),
                source -> source.path("package.json")));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {"dependencies":{"react-resizable":"1.11.1"},"devDependencies":{"react-resizable":"^1.11.1"},"peerDependencies":{"react-resizable":"~1.11.1"},"optionalDependencies":{"react-resizable":"1.11.1"}}
                """,
                """
                {"dependencies":{"react-resizable":"3.1.3"},"devDependencies":{"react-resizable":"3.1.3"},"peerDependencies":{"react-resizable":"3.1.3"},"optionalDependencies":{"react-resizable":"3.1.3"}}
                """, source -> source.path("package.json")));
    }

    @Test
    void upgradesRealLearnCodingFixtureAtFixedCommit() {
        // ac030540/learn-coding@2c74881d97d9859481424a2bffc3fbfe9d41d6bc, package.json#L16-L26
        rewriteRun(json(
                """
                {"dependencies":{"react":"^17.0.1","react-dom":"^17.0.1","react-resizable":"^1.11.1","react-router-dom":"^5.2.0"}}
                """,
                """
                {"dependencies":{"react":"^17.0.1","react-dom":"^17.0.1","react-resizable":"3.1.3","react-router-dom":"^5.2.0"}}
                """, source -> source.path("package.json")));
    }

    @Test
    void upgradesRealRuoyiFixtureAndPreservesTypes() {
        // jiangzhangxiang/ruoyi-ant-design-pro@1ad690162686f93d57324f2ca94a61640ba58cf1, package.json#L57-L66,L104
        rewriteRun(json(
                """
                {"dependencies":{"react":"^17.0.0","react-resizable":"^1.11.1"},"devDependencies":{"@types/react-resizable":"^1.7.4"}}
                """,
                """
                {"dependencies":{"react":"^17.0.0","react-resizable":"3.1.3"},"devDependencies":{"@types/react-resizable":"^1.7.4"}}
                """, source -> source.path("package.json")));
    }

    @Test
    void upgradesRealJbookFixtureAndPreservesGraph() {
        // GurcanH/jbook@61c8ec16e38eaca12b8e37390ecc9ac4b535027b, package.json#L20-L38
        rewriteRun(json(
                """
                {"dependencies":{"@types/react":"^17.0.0","@types/react-resizable":"^1.7.2","react":"^17.0.1","react-dom":"^17.0.1","react-resizable":"^1.11.1"}}
                """,
                """
                {"dependencies":{"@types/react":"^17.0.0","@types/react-resizable":"^1.7.2","react":"^17.0.1","react-dom":"^17.0.1","react-resizable":"3.1.3"}}
                """, source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "does not widen to version {0}")
    @ValueSource(strings = {"1.10.1", "1.11.0", "2.0.0", "3.0.0", "3.0.5", "3.1.0", "3.1.2", "3.1.3", "3.1.4", "4.0.2"})
    void leavesUnselectedVersions(String version) {
        rewriteRun(json(pkg("dependencies", version), source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves externally owned declaration {0}")
    @ValueSource(strings = {">=1.11.1", "1.11.x", "1.11.1 || 3.1.3", "workspace:^1.11.1", "npm:other@1.11.1", "file:../react-resizable", "git+https://github.com/example/fork.git", "https://registry.example/react-resizable.tgz", "*", "latest"})
    void leavesRangesProtocolsAliasesAndTags(String declaration) {
        rewriteRun(json(pkg("dependencies", declaration), source -> source.path("package.json")));
    }

    @Test
    void leavesOverridesResolutionsMetadataAndNestedObjects() {
        rewriteRun(json(
                """
                {"name":"react-resizable","version":"1.11.1","overrides":{"react-resizable":"1.11.1"},"resolutions":{"react-resizable":"1.11.1"},"config":{"dependencies":{"react-resizable":"1.11.1"}}}
                """, source -> source.path("package.json")));
    }

    @Test
    void leavesOtherPackageNamesAndNonPackageJson() {
        rewriteRun(
                json("{\"dependencies\":{\"react-resizable-panels\":\"1.11.1\",\"@types/react-resizable\":\"1.11.1\"}}", source -> source.path("package.json")),
                json(pkg("dependencies", "1.11.1"), source -> source.path("fixture.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"node_modules/a/package.json", "dist/package.json", "build/package.json", "target/package.json", "generated/package.json", "generated-client/package.json", ".next/package.json", ".cache/package.json", ".turbo/package.json", "vendor/package.json"})
    void skipsGeneratedAndInstalledTrees(String path) {
        rewriteRun(json(pkg("dependencies", "1.11.1"), source -> source.path(path)));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json(pkg("dependencies", "^1.11.1"), pkg("dependencies", "3.1.3"),
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

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.reactresizable")
                .scanYamlResources().build();
    }

    static String pkg(String section, String declaration) {
        return "{\"name\":\"app\",\"" + section + "\":{\"react-resizable\":\"" + declaration + "\"}}";
    }
}
