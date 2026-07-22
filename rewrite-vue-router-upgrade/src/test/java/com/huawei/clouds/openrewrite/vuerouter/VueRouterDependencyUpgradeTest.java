package com.huawei.clouds.openrewrite.vuerouter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class VueRouterDependencyUpgradeTest implements RewriteTest {
    static final String STRICT = "com.huawei.clouds.openrewrite.vuerouter.UpgradeVueRouterTo5_0_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3.0.6", "3.1.6", "3.4.3", "3.5.1", "3.5.2",
            "3.5.3", "3.5.4", "3.6.2", "3.6.5", "4.0.12"
    })
    void upgradesEveryExactXlsxVersion(String version) {
        assertUpgrade(version);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3.0.6", "3.1.6", "3.4.3", "3.5.1", "3.5.2",
            "3.5.3", "3.5.4", "3.6.2", "3.6.5", "4.0.12"
    })
    void upgradesEveryCaretXlsxVersion(String version) {
        assertUpgrade("^" + version);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3.0.6", "3.1.6", "3.4.3", "3.5.1", "3.5.2",
            "3.5.3", "3.5.4", "3.6.2", "3.6.5", "4.0.12"
    })
    void upgradesEveryTildeXlsxVersion(String version) {
        assertUpgrade("~" + version);
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {"dependencies":{"vue-router":"3.0.6"},"devDependencies":{"vue-router":"^3.5.2"},"peerDependencies":{"vue-router":"~3.6.5"},"optionalDependencies":{"vue-router":"4.0.12"}}
                """,
                """
                {"dependencies":{"vue-router":"5.0.3"},"devDependencies":{"vue-router":"5.0.3"},"peerDependencies":{"vue-router":"5.0.3"},"optionalDependencies":{"vue-router":"5.0.3"}}
                """,
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=3.0.6", ">=3.6.5 <4", "3.0.6 || 4.0.12", "3.5.1 - 3.6.5",
            "3.x", "3.5.x", "*", ">3.0.6", "<=4.0.12", "^3.5.2 || ^4.0.12"
    })
    void leavesComplexRangesUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "workspace:^3.6.5", "workspace:*", "npm:@company/router@4.0.12",
            "github:vuejs/router#v4.0.12", "git+https://github.com/vuejs/router.git#v4.0.12",
            "file:../router", "link:../router", "https://example.test/vue-router-4.0.12.tgz"
    })
    void leavesProtocolsAliasesAndUrlsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "v3.0.6", "=3.1.6", " 3.4.3", "3.5.1 ", "3.5.2-beta.1",
            "3.6.5+company.1", "latest", "next", "$routerVersion", ""
    })
    void leavesDecoratedAndDynamicDeclarationsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2.8.1", "3.0.1", "3.0.2", "3.6.4", "3.6.6", "4.0.11", "4.0.13",
            "4.2.5", "4.6.4", "5.0.0", "5.0.1", "5.0.2", "5.0.3", "^5.0.3", "5.1.0", "6.0.0"
    })
    void leavesUnlistedTargetAndOtherVersionsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void upgradesWorkspacePackageJsonFilesIndependently() {
        rewriteRun(
                json("{\"dependencies\":{\"vue-router\":\"^3.6.5\"}}",
                        "{\"dependencies\":{\"vue-router\":\"5.0.3\"}}",
                        source -> source.path("apps/admin/package.json")),
                json("{\"peerDependencies\":{\"vue-router\":\"~4.0.12\"}}",
                        "{\"peerDependencies\":{\"vue-router\":\"5.0.3\"}}",
                        source -> source.path("packages/router-adapter/package.json"))
        );
    }

    @Test
    void leavesNestedObjectsCatalogsLocksAndSimilarPackagesUntouched() {
        rewriteRun(
                json("{\"overrides\":{\"tool\":{\"dependencies\":{\"vue-router\":\"3.6.5\"}}},\"resolutions\":{\"vue-router\":\"4.0.12\"}}",
                        source -> source.path("package.json")),
                json("{\"catalog\":{\"vue-router\":\"3.6.5\"},\"dependencies\":{\"vue-router\":\"$routerVersion\"}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"vue-router\":\"3.6.5\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"vue-router\":\"3.6.5\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                json("{\"dependencies\":{\"vue-router-mock\":\"3.6.5\",\"@example/vue-router\":\"4.0.12\",\"Vue-Router\":\"3.5.2\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"vue-router\":\"^4.0.12\"}}",
                        "{\"dependencies\":{\"vue-router\":\"5.0.3\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void strictRecipeIsDiscoverableAndValid() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(STRICT);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> STRICT.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private void assertUpgrade(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"vue-router\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"vue-router\":\"5.0.3\"}}",
                source -> source.path("package.json")));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"vue-router\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.vuerouter")
                .scanYamlResources()
                .build();
    }
}
