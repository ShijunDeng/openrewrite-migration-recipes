package com.huawei.clouds.openrewrite.fastglob;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class FastGlobDependencyUpgradeTest implements RewriteTest {
    static final String STRICT = "com.huawei.clouds.openrewrite.fastglob.UpgradeFastGlobTo3_3_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(STRICT));
    }

    @Test
    void upgradesExactWorkbookVersion() {
        assertUpgrade("3.2.2", "3.3.3");
    }

    @Test
    void upgradesCaretWorkbookVersionAndPreservesOperator() {
        assertUpgrade("^3.2.2", "^3.3.3");
    }

    @Test
    void upgradesTildeWorkbookVersionAndPreservesOperator() {
        assertUpgrade("~3.2.2", "~3.3.3");
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {"dependencies":{"fast-glob":"3.2.2"},"devDependencies":{"fast-glob":"^3.2.2"},"peerDependencies":{"fast-glob":"~3.2.2"},"optionalDependencies":{"fast-glob":"3.2.2"}}
                """,
                """
                {"dependencies":{"fast-glob":"3.3.3"},"devDependencies":{"fast-glob":"^3.3.3"},"peerDependencies":{"fast-glob":"~3.3.3"},"optionalDependencies":{"fast-glob":"3.3.3"}}
                """, source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=3.2.2", ">=3.2.2 <4", "<=3.2.2", "3.2.2 || 3.3.0", "3.2.2 - 3.3.3",
            "3.x", "3.2.x", "*", ">3.2.2", "<3.3.3", "^3.2.2 || ^3.3.3", "~3.2.2 >=3.2.0"
    })
    void protectsComplexSemver(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "workspace:3.2.2", "workspace:^3.2.2", "npm:@company/fast-glob@3.2.2",
            "github:mrmlnc/fast-glob#3.2.2", "git+https://github.com/mrmlnc/fast-glob.git#3.2.2",
            "file:../fast-glob", "link:../fast-glob", "https://example.test/fast-glob-3.2.2.tgz"
    })
    void protectsProtocolsAliasesAndUrls(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "v3.2.2", "=3.2.2", " 3.2.2", "3.2.2 ", "3.2.2-beta.1", "3.2.2+company.4",
            "latest", "next", "", "$fastGlobVersion"
    })
    void protectsDecoratedDynamicAndVariableDeclarations(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2.2.7", "3.2.0", "3.2.1", "3.2.3", "3.2.4", "3.2.12", "3.3.0", "3.3.1",
            "3.3.2", "3.3.3", "^3.3.3", "4.0.0"
    })
    void protectsUnlistedTargetAndOtherVersions(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void protectsCentralOwnersNestedObjectsNonStringsAndSimilarPackages() {
        rewriteRun(
                json("{\"overrides\":{\"fast-glob\":\"3.2.2\"},\"resolutions\":{\"fast-glob\":\"^3.2.2\"},\"catalog\":{\"fast-glob\":\"~3.2.2\"}}",
                        source -> source.path("package.json")),
                json("{\"tool\":{\"dependencies\":{\"fast-glob\":\"3.2.2\"}},\"dependencies\":{\"fast-glob\":false}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"@company/fast-glob\":\"3.2.2\",\"fast-globber\":\"3.2.2\",\"Fast-Glob\":\"3.2.2\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void protectsLockfilesOrdinaryJsonAndExcludedParents() {
        rewriteRun(
                json("{\"packages\":{\"\":{\"dependencies\":{\"fast-glob\":\"3.2.2\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"fast-glob\":\"3.2.2\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                json("{\"dependencies\":{\"fast-glob\":\"3.2.2\"}}",
                        source -> source.path("vendor/tool/package.json")),
                json("{\"dependencies\":{\"fast-glob\":\"3.2.2\"}}",
                        source -> source.path("generated-client/package.json")),
                json("{\"dependencies\":{\"fast-glob\":\"3.2.2\"}}",
                        source -> source.path("install-cache/package.json")),
                json("{\"dependencies\":{\"fast-glob\":\"3.2.2\"}}",
                        source -> source.path(".cache/package.json")));
    }

    @Test
    void workspaceManifestsUpgradeIndependentlyAndRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"fast-glob\":\"^3.2.2\"}}",
                        "{\"dependencies\":{\"fast-glob\":\"^3.3.3\"}}",
                        source -> source.path("apps/cli/package.json")),
                json("{\"devDependencies\":{\"fast-glob\":\"~3.2.2\"}}",
                        "{\"devDependencies\":{\"fast-glob\":\"~3.3.3\"}}",
                        source -> source.path("packages/tooling/package.json")));
    }

    @Test
    void strictRecipeIsDiscoverableAndValid() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(STRICT);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> STRICT.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private void assertUpgrade(String declaration, String expected) {
        rewriteRun(json("{\"dependencies\":{\"fast-glob\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"fast-glob\":\"" + expected + "\"}}",
                source -> source.path("package.json")));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"fast-glob\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.fastglob")
                .scanYamlResources().build();
    }
}
