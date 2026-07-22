package com.huawei.clouds.openrewrite.chalk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class ChalkDependencyUpgradeTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.chalk.UpgradeChalkDependencyTo5_6_2";
    static final String MIGRATE = "com.huawei.clouds.openrewrite.chalk.MigrateChalkTo5_6_2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    static Stream<String> selectedDeclarations() {
        return Stream.of("2.4.2", "4.1.2", "5.3.0", "^2.4.2", "^4.1.2", "^5.3.0",
                "~2.4.2", "~4.1.2", "~5.3.0");
    }

    @ParameterizedTest
    @MethodSource("selectedDeclarations")
    void upgradesEveryXlsxVersionAndPreservesSimpleOperator(String declaration) {
        String operator = declaration.startsWith("^") || declaration.startsWith("~") ? declaration.substring(0, 1) : "";
        rewriteRun(json("{\"dependencies\":{\"chalk\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"chalk\":\"" + operator + "5.6.2\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dependencies", "devDependencies", "peerDependencies", "optionalDependencies"})
    void upgradesEveryDirectDependencySection(String section) {
        rewriteRun(json("{\"" + section + "\":{\"chalk\":\"4.1.2\"}}",
                "{\"" + section + "\":{\"chalk\":\"5.6.2\"}}",
                source -> source.path("packages/cli/package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.4.1", "2.4.3", "4.1.1", "4.1.3", "5.2.0", "5.5.0", "5.6.2", "6.0.0", "latest", "next"})
    void leavesUnlistedTargetFutureAndTags(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"chalk\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {">=2.4.2 <6", "2.4.2 || 4.1.2", "2.x", "*", "workspace:^2.4.2", "npm:chalk@2.4.2",
            "file:../chalk", "git+https://github.com/chalk/chalk.git#v2.4.2", "https://example.test/chalk.tgz", "$CHALK_VERSION"})
    void protectsComplexRangeDynamicAliasFileGitUrlAndVariable(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"chalk\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"overrides", "resolutions"})
    void doesNotRewriteOverrideOrResolutionOwnership(String section) {
        rewriteRun(json("{\"dependencies\":{\"other\":\"1.0.0\"},\"" + section + "\":{\"chalk\":\"2.4.2\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void doesNotRewritePnpmNestedOverride() {
        rewriteRun(json("{\"pnpm\":{\"overrides\":{\"chalk\":\"4.1.2\"}}}", source -> source.path("package.json")));
    }

    @Test
    void doesNotRewritePackageLock() {
        rewriteRun(json("{\"packages\":{\"node_modules/chalk\":{\"version\":\"2.4.2\"}}}",
                source -> source.path("package-lock.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"target/package.json", "build/package.json", "dist/package.json", "generated/package.json",
            "generated-fixture/package.json", "generatedClient/package.json", "installed-sdk/package.json",
            "vendor/package.json", "node_modules/app/package.json", ".next/package.json", ".pnpm/package.json"})
    void excludesGeneratedInstalledAndBuiltTrees(String path) {
        rewriteRun(json("{\"dependencies\":{\"chalk\":\"2.4.2\"}}", source -> source.path(path)));
    }

    @Test
    void ignoresSimilarPackageNamesAndNestedMetadata() {
        rewriteRun(json("{\"dependencies\":{\"chalk-template\":\"2.4.2\",\"@scope/chalk\":\"4.1.2\"},\"config\":{\"chalk\":\"5.3.0\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void whitelistIsExactAndVisible() {
        assertEquals(Set.of("2.4.2", "4.1.2", "5.3.0"), ChalkSupport.SOURCE_VERSIONS);
        assertEquals("5.6.2", ChalkSupport.TARGET_VERSION);
    }

    @Test
    void lowLevelRecipeIsDiscoverableValidAndIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"chalk\":\"^4.1.2\"}}",
                        "{\"dependencies\":{\"chalk\":\"^5.6.2\"}}", source -> source.path("package.json")));
        Recipe recipe = environment().activateRecipes(UPGRADE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> UPGRADE.equals(candidate.getName())));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.chalk")
                .scanYamlResources().build();
    }
}
