package com.huawei.clouds.openrewrite.marked;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class MarkedDependencyUpgradeTest implements RewriteTest {
    static final String STRICT = "com.huawei.clouds.openrewrite.marked.UpgradeMarkedTo17_0_6";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4.0.10", "4.0.12", "4.0.17", "4.1.0", "4.2.3",
            "4.2.12", "4.3.0", "5.1.0", "5.1.1"
    })
    void upgradesEveryExactSpreadsheetVersion(String version) {
        assertUpgrade(version);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4.0.10", "4.0.12", "4.0.17", "4.1.0", "4.2.3",
            "4.2.12", "4.3.0", "5.1.0", "5.1.1"
    })
    void upgradesEveryCaretSingleSpreadsheetVersion(String version) {
        assertUpgrade("^" + version);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4.0.10", "4.0.12", "4.0.17", "4.1.0", "4.2.3",
            "4.2.12", "4.3.0", "5.1.0", "5.1.1"
    })
    void upgradesEveryTildeSingleSpreadsheetVersion(String version) {
        assertUpgrade("~" + version);
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {"dependencies":{"marked":"4.0.10"},"devDependencies":{"marked":"^4.2.12"},"peerDependencies":{"marked":"~5.1.0"},"optionalDependencies":{"marked":"5.1.1"}}
                """,
                """
                {"dependencies":{"marked":"17.0.6"},"devDependencies":{"marked":"17.0.6"},"peerDependencies":{"marked":"17.0.6"},"optionalDependencies":{"marked":"17.0.6"}}
                """,
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=4.0.10", ">=4.2.12 <5", "<=5.1.1", "4.0.10 || 5.1.1",
            "4.2.3 - 5.1.0", "4.x", "4.2.x", "*", ">4.0.12", "<5.1.1",
            "^4.0.10 || ^5.1.1", "~4.2.12 >=4.2.3"
    })
    void leavesComplexRangesUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "workspace:4.2.12", "workspace:^5.1.1", "npm:@company/marked@4.2.12",
            "github:markedjs/marked#v5.1.1", "git+https://github.com/markedjs/marked.git#v4.3.0",
            "file:../marked", "link:../marked", "https://example.test/marked-4.2.12.tgz"
    })
    void leavesProtocolsAndAliasesUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "v4.0.10", "=4.0.12", " 4.0.17", "4.1.0 ",
            "4.2.3-beta.1", "4.2.12+company.7", "latest", "next", ""
    })
    void leavesDecoratedDynamicAndEmptyDeclarationsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3.0.8", "4.0.9", "4.0.11", "4.2.13", "5.0.0", "5.1.2",
            "6.0.0", "16.0.0", "17.0.0", "17.0.6", "17.0.7", "18.0.0"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void upgradesWorkspacePackageJsonFilesIndependently() {
        rewriteRun(
                json(
                        "{\"dependencies\":{\"marked\":\"^4.0.10\"}}",
                        "{\"dependencies\":{\"marked\":\"17.0.6\"}}",
                        source -> source.path("apps/docs/package.json")
                ),
                json(
                        "{\"devDependencies\":{\"marked\":\"~5.1.1\"}}",
                        "{\"devDependencies\":{\"marked\":\"17.0.6\"}}",
                        source -> source.path("packages/parser/package.json")
                )
        );
    }

    @Test
    void nestedDependencyLikeObjectsAreNotDirectSections() {
        rewriteRun(json(
                """
                {"overrides":{"tool":{"dependencies":{"marked":"4.2.12"}}},"pnpm":{"overrides":{"marked":"5.1.1"}},"resolutions":{"marked":"4.0.10"}}
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void leavesCatalogVariablesAndNonStringValuesUntouched() {
        rewriteRun(json(
                """
                {"catalog":{"marked":"4.2.12"},"dependencies":{"marked":"$markedVersion"},"devDependencies":{"marked":false},"optionalDependencies":{"marked":4.212}}
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void leavesLockfileOrdinaryJsonAndSimilarPackagesUntouched() {
        rewriteRun(
                json(
                        "{\"packages\":{\"\":{\"dependencies\":{\"marked\":\"4.2.12\"}}}}",
                        source -> source.path("package-lock.json")
                ),
                json(
                        "{\"dependencies\":{\"marked\":\"4.2.12\"}}",
                        source -> source.path("fixtures/dependencies.json")
                ),
                json(
                        "{\"dependencies\":{\"@types/marked\":\"4.2.12\",\"marked-highlight\":\"4.2.12\",\"Marked\":\"5.1.1\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json(
                        "{\"dependencies\":{\"marked\":\"^4.2.12\"}}",
                        "{\"dependencies\":{\"marked\":\"17.0.6\"}}",
                        source -> source.path("package.json")
                )
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
        rewriteRun(json(
                "{\"dependencies\":{\"marked\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"marked\":\"17.0.6\"}}",
                source -> source.path("package.json")
        ));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"marked\":\"" + declaration + "\"}}",
                source -> source.path("package.json")
        ));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.marked")
                .scanYamlResources()
                .build();
    }
}
