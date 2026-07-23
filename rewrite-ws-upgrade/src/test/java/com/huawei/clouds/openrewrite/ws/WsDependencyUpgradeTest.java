package com.huawei.clouds.openrewrite.ws;

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

class WsDependencyUpgradeTest implements RewriteTest {
    static final String STRICT = "com.huawei.clouds.openrewrite.ws.UpgradeWsTo8_21_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(STRICT));
    }

    @ParameterizedTest
    @CsvSource({
            "8.5.0,8.21.0", "^8.5.0,^8.21.0", "~8.5.0,~8.21.0",
            "8.16.0,8.21.0", "^8.16.0,^8.21.0", "~8.16.0,~8.21.0",
            "8.18.3,8.21.0", "^8.18.3,^8.21.0", "~8.18.3,~8.21.0",
            "8.20.0,8.21.0", "^8.20.0,^8.21.0", "~8.20.0,~8.21.0"
    })
    void upgradesEveryWorkbookSourceAndOperator(String before, String after) {
        assertUpgrade(before, after);
    }

    @Test
    void upgradesEveryDirectDependencySection() {
        rewriteRun(json(
                """
                {"dependencies":{"ws":"8.5.0"},"devDependencies":{"ws":"^8.16.0"},"peerDependencies":{"ws":"~8.18.3"},"optionalDependencies":{"ws":"8.20.0"}}
                """,
                """
                {"dependencies":{"ws":"8.21.0"},"devDependencies":{"ws":"^8.21.0"},"peerDependencies":{"ws":"~8.21.0"},"optionalDependencies":{"ws":"8.21.0"}}
                """, source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=8.5.0", ">=8.16.0 <9", "<=8.18.3", "8.5.0 || 8.21.0", "8.16.0 - 8.21.0",
            "8.x", "8.20.x", "*", ">8.20.0", "<8.21.0", "^8.16.0 || ^8.20.0",
            "~8.18.3 >=8.18.0", ">=8.5 <8.21", "8.5 || 8.16", "^8", "~8"
    })
    void protectsComplexSemver(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "workspace:8.16.0", "workspace:^8.18.3", "workspace:*", "npm:@company/ws@8.20.0",
            "github:websockets/ws#8.20.0", "git+https://github.com/websockets/ws.git#8.18.3",
            "git+ssh://git@github.com/websockets/ws.git#8.16.0", "file:../ws", "link:../ws",
            "https://example.test/ws-8.20.0.tgz", "portal:../ws", "patch:ws@8.20.0#patches/ws.patch"
    })
    void protectsProtocolsAliasesAndUrls(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "v8.5.0", "=8.16.0", " 8.18.3", "8.20.0 ", "8.20.0-beta.1", "8.20.0+company.4",
            "latest", "next", "canary", "", "$wsVersion", "${wsVersion}", "8.20", "08.20.0"
    })
    void protectsDecoratedDynamicAndPartialVersions(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "7.5.9", "8.0.0", "8.4.2", "8.5.1", "8.6.0", "8.15.1", "8.16.1", "8.17.0",
            "8.18.0", "8.18.1", "8.18.2", "8.19.0", "8.20.1", "8.21.0", "^8.21.0", "~8.21.0", "9.0.0"
    })
    void protectsUnlistedTargetAndOtherVersions(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void protectsCentralOwnersNestedObjectsNonStringsAndSimilarPackages() {
        rewriteRun(
                json("{\"overrides\":{\"ws\":\"8.16.0\"},\"resolutions\":{\"ws\":\"^8.18.3\"},\"catalog\":{\"ws\":\"~8.20.0\"}}",
                        source -> source.path("package.json")),
                json("{\"tool\":{\"dependencies\":{\"ws\":\"8.16.0\"}},\"dependencies\":{\"ws\":false}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"@types/ws\":\"8.16.0\",\"wss\":\"8.18.3\",\"WS\":\"8.20.0\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void protectsLockfilesOrdinaryJsonAndExcludedParents() {
        rewriteRun(
                json("{\"packages\":{\"\":{\"dependencies\":{\"ws\":\"8.16.0\"}}}}", source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path("fixtures/dependencies.json")),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path("vendor/tool/package.json")),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path("generated-client/package.json")),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path(".cache/package.json")));
    }

    @Test
    void workspaceManifestsUpgradeIndependentlyAndRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"ws\":\"^8.5.0\"}}", "{\"dependencies\":{\"ws\":\"^8.21.0\"}}",
                        source -> source.path("apps/gateway/package.json")),
                json("{\"devDependencies\":{\"ws\":\"~8.20.0\"}}", "{\"devDependencies\":{\"ws\":\"~8.21.0\"}}",
                        source -> source.path("packages/transport/package.json")));
    }

    @Test
    void strictRecipeIsDiscoverableAndValid() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(STRICT);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> STRICT.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private void assertUpgrade(String declaration, String expected) {
        rewriteRun(json("{\"dependencies\":{\"ws\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"ws\":\"" + expected + "\"}}", source -> source.path("package.json")));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"ws\":\"" + declaration + "\"}}", source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.ws").scanYamlResources().build();
    }
}
