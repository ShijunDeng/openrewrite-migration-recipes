package com.huawei.clouds.openrewrite.testinglibraryjestdom;

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

class JestDomDependencyTest implements RewriteTest {
    static final String UPGRADE =
            "com.huawei.clouds.openrewrite.testinglibraryjestdom.UpgradeJestDomTo6_9_1";
    static final String MIGRATE =
            "com.huawei.clouds.openrewrite.testinglibraryjestdom.MigrateJestDomTo6_9_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "selected declaration {0}")
    @CsvSource({"5.17.0, 6.9.1", "^5.17.0, ^6.9.1", "~5.17.0, ~6.9.1"})
    void upgradesOnlySelectedDeclarationForms(String before, String after) {
        assertUpgrade(before, after);
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {"dependencies":{"@testing-library/jest-dom":"5.17.0"},"devDependencies":{"@testing-library/jest-dom":"^5.17.0"},"peerDependencies":{"@testing-library/jest-dom":"~5.17.0"},"optionalDependencies":{"@testing-library/jest-dom":"5.17.0"}}
                """,
                """
                {"dependencies":{"@testing-library/jest-dom":"6.9.1"},"devDependencies":{"@testing-library/jest-dom":"^6.9.1"},"peerDependencies":{"@testing-library/jest-dom":"~6.9.1"},"optionalDependencies":{"@testing-library/jest-dom":"6.9.1"}}
                """,
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest(name = "complex range NOOP {0}")
    @ValueSource(strings = {
            ">=5.17.0", ">=5.17.0 <7", "<=5.17.0", "5.17.0 || 6.9.1", "5.17.0 - 6.9.1",
            "5.x", "5.17.x", "*", ">5.17.0", "<6.9.1", "^5.17.0 || ^6.9.1"
    })
    void leavesComplexRangesUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "protocol/alias NOOP {0}")
    @ValueSource(strings = {
            "workspace:5.17.0", "workspace:^5.17.0", "npm:@company/jest-dom@5.17.0",
            "github:testing-library/jest-dom#v5.17.0",
            "git+https://github.com/testing-library/jest-dom.git#v5.17.0",
            "file:../jest-dom", "link:../jest-dom", "https://example.test/jest-dom-5.17.0.tgz"
    })
    void leavesProtocolsAliasesAndForksUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "unlisted/target NOOP {0}")
    @ValueSource(strings = {
            "5.16.5", "5.17.1", "6.0.0", "6.1.0", "6.9.0", "6.9.1", "^6.9.1", "~6.9.1",
            "v5.17.0", "=5.17.0", "5.17.0-beta.1", "latest", "next", ""
    })
    void leavesUnlistedTargetAndDecoratedDeclarationsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void upgradesWorkspaceManifestsIndependently() {
        rewriteRun(
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"^5.17.0\"}}",
                        "{\"devDependencies\":{\"@testing-library/jest-dom\":\"^6.9.1\"}}",
                        source -> source.path("apps/web/package.json")),
                json("{\"dependencies\":{\"@testing-library/jest-dom\":\"~5.17.0\"}}",
                        "{\"dependencies\":{\"@testing-library/jest-dom\":\"~6.9.1\"}}",
                        source -> source.path("packages/test-utils/package.json"))
        );
    }

    @Test
    void doesNotUpgradeOverridesCatalogsOrArbitraryNestedKeys() {
        rewriteRun(json(
                """
                {"overrides":{"@testing-library/jest-dom":"5.17.0"},"resolutions":{"**/@testing-library/jest-dom":"5.17.0"},"pnpm":{"overrides":{"app>@testing-library/jest-dom":"5.17.0"}},"catalog":{"@testing-library/jest-dom":"5.17.0"},"tool":{"dependencies":{"@testing-library/jest-dom":"5.17.0"}}}
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void ignoresLockfilesOtherJsonSimilarPackagesAndNonStringValues() {
        rewriteRun(
                json("{\"packages\":{\"\":{\"devDependencies\":{\"@testing-library/jest-dom\":\"5.17.0\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"5.17.0\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                json("{\"devDependencies\":{\"@testing-library/react\":\"5.17.0\",\"@types/testing-library__jest-dom\":\"5.17.0\",\"@testing-library/jest-dom\":false}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void excludesArtifactParentsButAcceptsOrdinaryWorkspaceLeaves() {
        rewriteRun(
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"5.17.0\"}}",
                        source -> source.path("NODE_MODULES/tool/package.json")),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"5.17.0\"}}",
                        source -> source.path("generated-fixtures/package.json")),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"5.17.0\"}}",
                        source -> source.path("GeneratedClient/package.json")),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"5.17.0\"}}",
                        source -> source.path("INSTALL-CACHE/tool/package.json")),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"5.17.0\"}}",
                        source -> source.path("packages/installer/package.json")),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"5.17.0\"}}",
                        source -> source.path(".m2/cache/package.json"))
        );
    }

    @Test
    void realLendsqrManifestFixturePinnedByCommit() {
        rewriteRun(json(
                """
                {"private":true,"dependencies":{"@reduxjs/toolkit":"^1.9.7","@testing-library/jest-dom":"^5.17.0","@testing-library/react":"^13.4.0","@types/jest":"^27.5.2"},"devDependencies":{"@jest/globals":"^29.7.0","ts-jest":"^29.1.1"}}
                """,
                """
                {"private":true,"dependencies":{"@reduxjs/toolkit":"^1.9.7","@testing-library/jest-dom":"^6.9.1","@testing-library/react":"^13.4.0","@types/jest":"^27.5.2"},"devDependencies":{"@jest/globals":"^29.7.0","ts-jest":"^29.1.1"}}
                """,
                source -> source.path("fixtures/lendsqr/package.json")
        ));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"^5.17.0\"}}",
                        "{\"devDependencies\":{\"@testing-library/jest-dom\":\"^6.9.1\"}}",
                        source -> source.path("package.json"))
        );
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
        rewriteRun(json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"" + before + "\"}}",
                "{\"devDependencies\":{\"@testing-library/jest-dom\":\"" + after + "\"}}",
                source -> source.path("package.json")));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.testinglibraryjestdom")
                .scanYamlResources()
                .build();
    }
}
