package com.huawei.clouds.openrewrite.prettier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class PrettierDependencyTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.prettier.UpgradePrettierTo3_6_2";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.prettier.MigratePrettierTo3_6_2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedPrettierDependency());
    }

    @ParameterizedTest(name = "exact source {0}")
    @ValueSource(strings = {"1.19.1", "2.8.8"})
    void upgradesExactWorkbookSources(String version) {
        assertUpgrade(version, "3.6.2");
    }

    @ParameterizedTest(name = "caret source {0}")
    @ValueSource(strings = {"1.19.1", "2.8.8"})
    void upgradesCaretWorkbookSources(String version) {
        assertUpgrade("^" + version, "^3.6.2");
    }

    @ParameterizedTest(name = "tilde source {0}")
    @ValueSource(strings = {"1.19.1", "2.8.8"})
    void upgradesTildeWorkbookSources(String version) {
        assertUpgrade("~" + version, "~3.6.2");
    }

    @ParameterizedTest(name = "complex declaration NOOP {0}")
    @ValueSource(strings = {
            ">=1.19.1", ">=2.8.8 <4", "<=2.8.8", "1.19.1 || 2.8.8", "1.19.1 - 2.8.8",
            "1.x", "2.8.x", "*", ">2.8.8", "<3.6.2", "^1.19.1 || ^2.8.8",
            "~1.19.1 || ~2.8.8", "1.19.1 2.8.8", ">=1 <3", "2"
    })
    void leavesComplexRangesUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "protocol/fork NOOP {0}")
    @ValueSource(strings = {
            "workspace:1.19.1", "workspace:^2.8.8", "workspace:*", "npm:@company/prettier@2.8.8",
            "github:prettier/prettier#2.8.8", "git+https://github.com/prettier/prettier.git#1.19.1",
            "git://github.com/prettier/prettier.git#2.8.8", "file:../prettier", "link:../prettier",
            "https://example.test/prettier-2.8.8.tgz", "catalog:", "catalog:formatters"
    })
    void leavesProtocolsAliasesAndForksUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "unlisted/decorated NOOP {0}")
    @ValueSource(strings = {
            "1.19.0", "1.19.2", "2.8.7", "2.8.9", "3.0.0", "3.6.1", "3.6.2", "^3.6.2",
            "~3.6.2", "4.0.0", "v1.19.1", "=2.8.8", "2.8.8-beta.1", "2.8.8+build.1",
            "latest", "next", "dev", "", " 2.8.8", "2.8.8 "
    })
    void leavesUnlistedAndDecoratedVersionsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void upgradesEveryDirectSectionAndPreservesOperators() {
        rewriteRun(json(
                "{\"dependencies\":{\"prettier\":\"1.19.1\"},\"devDependencies\":{\"prettier\":\"^2.8.8\"},\"peerDependencies\":{\"prettier\":\"~1.19.1\"},\"optionalDependencies\":{\"prettier\":\"2.8.8\"}}",
                "{\"dependencies\":{\"prettier\":\"3.6.2\"},\"devDependencies\":{\"prettier\":\"^3.6.2\"},\"peerDependencies\":{\"prettier\":\"~3.6.2\"},\"optionalDependencies\":{\"prettier\":\"3.6.2\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void ignoresOverridesResolutionsCatalogNestedOwnersAndLockfiles() {
        rewriteRun(
                json("{\"overrides\":{\"prettier\":\"1.19.1\"},\"resolutions\":{\"prettier\":\"2.8.8\"},\"pnpm\":{\"overrides\":{\"prettier\":\"1.19.1\"}},\"catalog\":{\"prettier\":\"2.8.8\"}}", source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"prettier\":\"2.8.8\"}},\"node_modules/prettier\":{\"version\":\"2.8.8\"}}}", source -> source.path("package-lock.json")));
    }

    @Test
    void ignoresLookalikesNonStringsAndOtherJson() {
        rewriteRun(
                json("{\"dependencies\":{\"prettier-extra\":\"2.8.8\",\"@scope/prettier\":\"1.19.1\",\"Prettier\":\"2.8.8\",\"prettier\":false}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"prettier\":\"2.8.8\"}}", source -> source.path("fixture.json")));
    }

    @Test
    void nestedWorkspacePackagesUpgradeWhileExcludedParentsDoNot() {
        rewriteRun(
                json("{\"devDependencies\":{\"prettier\":\"^2.8.8\"}}", "{\"devDependencies\":{\"prettier\":\"^3.6.2\"}}", source -> source.path("apps/web/package.json")),
                json("{\"devDependencies\":{\"prettier\":\"2.8.8\"}}", source -> source.path("generated-fixtures/package.json")),
                json("{\"devDependencies\":{\"prettier\":\"2.8.8\"}}", source -> source.path("GeneratedClient/package.json")),
                json("{\"devDependencies\":{\"prettier\":\"2.8.8\"}}", source -> source.path("installation/package.json")),
                json("{\"devDependencies\":{\"prettier\":\"2.8.8\"}}", source -> source.path("node_modules/pkg/package.json")));
    }

    @Test
    void realRepositoryManifestShapesMigrate() {
        rewriteRun(
                // prettier/prettier 2.8.8 tag, reduced to migration-relevant direct owners.
                json("{\"name\":\"prettier\",\"version\":\"2.8.8\",\"devDependencies\":{\"prettier\":\"2.8.8\",\"typescript\":\"4.9.5\"}}",
                        "{\"name\":\"prettier\",\"version\":\"2.8.8\",\"devDependencies\":{\"prettier\":\"3.6.2\",\"typescript\":\"4.9.5\"}}",
                        source -> source.path("fixtures/prettier-2/package.json")),
                json("{\"private\":true,\"scripts\":{\"format\":\"prettier --write .\"},\"devDependencies\":{\"prettier\":\"^2.8.8\",\"eslint\":\"^8.0.0\"}}",
                        "{\"private\":true,\"scripts\":{\"format\":\"prettier --write .\"},\"devDependencies\":{\"prettier\":\"^3.6.2\",\"eslint\":\"^8.0.0\"}}",
                        source -> source.path("fixtures/application/package.json")));
    }

    @Test
    void deterministicUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"devDependencies\":{\"prettier\":\"~1.19.1\"}}",
                        "{\"devDependencies\":{\"prettier\":\"~3.6.2\"}}", source -> source.path("package.json")));
    }

    @Test
    void recipesAreDiscoverableOrderedAndValid() {
        Environment environment = environment();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> UPGRADE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATE.equals(recipe.getName())));
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
        assertTrue(upgrade.getRecipeList().get(0) instanceof UpgradeSelectedPrettierDependency);
        assertTrue(upgrade.getRecipeList().size() == 1);
        assertTrue(UPGRADE.equals(migrate.getRecipeList().get(0).getName()));
        assertTrue(migrate.getRecipeList().get(1) instanceof MigratePrettierConfigOption);
    }

    @Test
    void publicUpgradeRecipeDoesNotRunConfigurationMigrationOrMarkers() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"devDependencies\":{\"prettier\":\"2.8.8\"},\"prettier\":{\"jsxBracketSameLine\":true,\"plugins\":[\"./plugin.js\"]}}",
                        "{\"devDependencies\":{\"prettier\":\"3.6.2\"},\"prettier\":{\"jsxBracketSameLine\":true,\"plugins\":[\"./plugin.js\"]}}",
                        source -> source.path("package.json")));
    }

    private void assertUpgrade(String before, String after) {
        rewriteRun(json("{\"devDependencies\":{\"prettier\":\"" + before + "\"}}",
                "{\"devDependencies\":{\"prettier\":\"" + after + "\"}}", source -> source.path("package.json")));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"devDependencies\":{\"prettier\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.prettier")
                .scanYamlResources().build();
    }
}
