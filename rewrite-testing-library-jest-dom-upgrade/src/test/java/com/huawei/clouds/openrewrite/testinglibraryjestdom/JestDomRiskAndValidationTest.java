package com.huawei.clouds.openrewrite.testinglibraryjestdom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class JestDomRiskAndValidationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksUnlistedDirectDeclarationAtValueNode() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomManifestRisks()),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\">=5.17 <7\"}}",
                        source -> source.path("package.json")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("not the exact workbook target"), printed);
                                    assertTrue(printed.contains(":/*~~("), printed);
                                }))
        );
    }

    @Test
    void targetDeclarationsAreClean() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomManifestRisks()),
                json("{\"dependencies\":{\"@testing-library/jest-dom\":\"6.9.1\"},\"devDependencies\":{\"@testing-library/jest-dom\":\"^6.9.1\"},\"peerDependencies\":{\"@testing-library/jest-dom\":\"~6.9.1\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void marksOnlyRootPackageManagerOverrideSelectors() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomManifestRisks()),
                json(
                        """
                        {"overrides":{"@testing-library/jest-dom@^5":"6.9.1"},"resolutions":{"**/@testing-library/jest-dom":"6.9.1"},"pnpm":{"overrides":{"app>@testing-library/jest-dom":"6.9.1"}},"tool":{"overrides":{"@testing-library/jest-dom":"5.17.0"}},"nested":{"resolutions":{"@testing-library/jest-dom":"5.17.0"}}}
                        """,
                        source -> source.path("package.json")
                                .after(actual -> actual)
                                .afterRecipe(after ->
                                        assertEquals(3, occurrences(after.printAll(), "root package-manager override"),
                                                after.printAll())))
        );
    }

    @Test
    void marksTypesRunnerNodeInjectGlobalsAndJavaScriptSetupDecisions() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomManifestRisks()),
                json(
                        """
                        {"devDependencies":{"@testing-library/jest-dom":"6.9.1","@types/testing-library__jest-dom":"^5.14.9","@jest/globals":"^29.7.0","vitest":"^0.34.1"},"engines":{"node":">=12"},"jest":{"injectGlobals":false,"setupFilesAfterEnv":["./jest.setup.js"]}}
                        """,
                        source -> source.path("package.json")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertEquals(2, occurrences(printed, "runner requires an intentional"), printed);
                                    assertEquals(1, occurrences(printed, "ships runner-specific local types"), printed);
                                    assertEquals(1, occurrences(printed, "drops Node versions below 14"), printed);
                                    assertEquals(1, occurrences(printed, "injectGlobals=false"), printed);
                                    assertEquals(1, occurrences(printed, "setup leaf is TypeScript"), printed);
                                }))
        );
    }

    @Test
    void node14AndTypeScriptSetupAreClean() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomManifestRisks()),
                json(
                        """
                        {"devDependencies":{"@testing-library/jest-dom":"6.9.1"},"engines":{"node":">=14"},"jest":{"setupFilesAfterEnv":["./jest.setup.ts"]}}
                        """,
                        source -> source.path("package.json"))
        );
    }

    @Test
    void evaluatesEveryNodeEngineUnionBranch() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomManifestRisks()),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"6.9.1\"},\"engines\":{\"node\":\"^14 || >=16\"}}",
                        source -> source.path("safe/package.json")),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\"6.9.1\"},\"engines\":{\"node\":\"^14 || 12\"}}",
                        source -> source.path("unsafe/package.json").after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(), "drops Node versions below 14"),
                                        after.printAll())))
        );
    }

    @Test
    void marksRemovedEntryAtExactModuleLiteral() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                typescript("import '@testing-library/jest-dom/extend-expect';\n",
                        source -> contains(source, "v5 entry is removed or hidden"))
        );
    }

    @Test
    void marksUnknownDeepImportRequireDynamicImportAndConfigReference() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                typescript(
                        """
                        import visible from '@testing-library/jest-dom/dist/to-be-visible';
                        export * from '@testing-library/jest-dom/types/matchers';
                        const lazy = import('@testing-library/jest-dom/dist/utils');
                        const legacy = require('@testing-library/jest-dom/dist/to-have-style');
                        """,
                        source -> source.path("src/deep.ts")
                                .after(actual -> actual)
                                .afterRecipe(after ->
                                        assertEquals(4, occurrences(after.printAll(), "exports only root"),
                                                after.printAll()))),
                javascript(
                        "module.exports = {alias: '@testing-library/jest-dom/dist/to-be-visible'};\n",
                        source -> source.path("jest.config.js")
                                .after(actual -> actual)
                                .afterRecipe(after ->
                                        assertEquals(1, occurrences(after.printAll(), "exports only root"),
                                                after.printAll())))
        );
    }

    @Test
    void publishedPublicEntriesWithValidShapesAreClean() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                typescript("import '@testing-library/jest-dom';\n",
                        source -> source.path("src/jest-runtime.ts")),
                typescript("import '@testing-library/jest-dom/jest-globals';\n",
                        source -> source.path("src/jest-globals-runtime.ts")),
                typescript("import '@testing-library/jest-dom/vitest';\n",
                        source -> source.path("src/vitest-runtime.ts")),
                typescript("import * as matchers from '@testing-library/jest-dom/matchers';\nimport {toBeVisible} from '@testing-library/jest-dom/matchers';\n",
                        source -> source.path("src/public-matchers.ts"))
        );
    }

    @Test
    void marksMultipleOrAmbiguousRunnerOwners() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                typescript("import '@testing-library/jest-dom';\nimport '@testing-library/jest-dom/vitest';\n",
                        source -> contains(source, "multiple jest-dom runner side-effect entries")),
                typescript("import {expect as v} from 'vitest';\nimport {expect as j} from '@jest/globals';\nimport '@testing-library/jest-dom';\n",
                        source -> contains(source, "imports both Vitest and @jest/globals"))
        );
    }

    @Test
    void marksInvalidV6PublicEntryImportShapes() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                typescript(
                        """
                        import jestDom from '@testing-library/jest-dom';
                        import matchers from '@testing-library/jest-dom/matchers';
                        import '@testing-library/jest-dom/matchers';
                        """,
                        source -> source.path("src/import-shapes.ts")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertEquals(1, occurrences(printed, "side-effect-only"), printed);
                                    assertEquals(2, occurrences(printed, "named exports and no default"), printed);
                                }))
        );
    }

    @Test
    void marksRunnerEntryMismatchInSameFile() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                typescript(
                        """
                        import {expect} from 'vitest';
                        import '@testing-library/jest-dom';
                        """,
                        source -> contains(source, "file owns Vitest expect")),
                typescript(
                        """
                        import {expect} from '@jest/globals';
                        import '@testing-library/jest-dom/vitest';
                        """,
                        source -> contains(source, "owns @jest/globals but loads the Vitest entry"))
        );
    }

    @Test
    void correctRunnerSpecificEntriesAreClean() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                typescript("import {expect} from 'vitest';\nimport '@testing-library/jest-dom/vitest';\n",
                        source -> source.path("test/vitest.setup.ts")),
                typescript("import {expect} from '@jest/globals';\nimport '@testing-library/jest-dom/jest-globals';\n",
                        source -> source.path("test/jest.setup.ts"))
        );
    }

    @Test
    void marksJavaScriptSetupLeafButNotTypeScriptSetupLeaf() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                javascript("import '@testing-library/jest-dom';\n",
                        source -> source.path("test/jest.setup.js")
                                .after(actual -> actual)
                                .afterRecipe(after ->
                                        assertTrue(after.printAll().contains("official v6 setup must be a .ts file"),
                                                after.printAll()))),
                typescript("import '@testing-library/jest-dom';\n",
                        source -> source.path("test/jest.setup.ts"))
        );
    }

    @ParameterizedTest(name = "deprecated matcher {0}")
    @ValueSource(strings = {"toBeEmpty", "toBeInTheDOM", "toHaveDescription", "toHaveErrorMessage"})
    void marksDeprecatedMatcherOnExpectChain(String matcher) {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                javascript("expect(node)." + matcher + "();\n",
                        source -> contains(source, "matcher remains deprecated"))
        );
    }

    @Test
    void marksNegatedDeprecatedMatcherChain() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                javascript("expect(node).not.toBeInTheDOM();\n",
                        source -> contains(source, "detached-node semantics"))
        );
    }

    @Test
    void marksDeprecatedNamedMatcherImportPrecisely() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                typescript("import {toBeInTheDOM as detachedMatcher, toBeVisible} from '@testing-library/jest-dom/matchers';\n",
                        source -> source.path("test/custom-expect.ts")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertEquals(1, occurrences(printed, "named jest-dom matcher remains deprecated"), printed);
                                    assertFalse(printed.contains("~~>toBeVisible"), printed);
                                }))
        );
    }

    @Test
    void doesNotMarkMatcherLookalikesOrOrdinaryExactStrings() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                javascript(
                        """
                        assertion.toBeInTheDOM();
                        engine.toHaveDescription();
                        const docs = '@testing-library/jest-dom/dist/to-be-visible';
                        """,
                        source -> source.path("src/lookalikes.js"))
        );
    }

    @Test
    void excludesGeneratedAndDependencySourceRisks() {
        rewriteRun(
                spec -> spec.recipe(new FindJestDomSourceRisks()),
                javascript("import x from '@testing-library/jest-dom/dist/to-be-visible';\n",
                        source -> source.path("NODE_MODULES/plugin/index.js")),
                javascript("expect(node).toBeInTheDOM();\n",
                        source -> source.path("generated-tests/test.js"))
        );
    }

    @Test
    void recommendedRecipeCombinesAutoAndPreciseMarks() {
        rewriteRun(
                spec -> spec.recipe(JestDomDependencyTest.environment()
                        .activateRecipes(JestDomDependencyTest.MIGRATE)),
                json(
                        """
                        {"devDependencies":{"@testing-library/jest-dom":"^5.17.0","@types/testing-library__jest-dom":"^5.14.9"}}
                        """,
                        source -> source.path("package.json")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("^6.9.1"), printed);
                                    assertTrue(printed.contains("ships runner-specific local types"), printed);
                                })),
                typescript(
                        """
                        import '@testing-library/jest-dom/extend-expect';
                        expect(detached).toBeInTheDOM();
                        """,
                        source -> source.path("test/setup.ts")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("import '@testing-library/jest-dom'"), printed);
                                    assertTrue(printed.contains("matcher remains deprecated"), printed);
                                }))
        );
    }

    @Test
    void recommendedRecipeMarksAmbiguousLegacySideEffectConsumptionWithoutRewritingIt() {
        rewriteRun(
                spec -> spec.recipe(JestDomDependencyTest.environment()
                        .activateRecipes(JestDomDependencyTest.MIGRATE)),
                typescript("""
                        import setup from '@testing-library/jest-dom/extend-expect';
                        export * from '@testing-library/jest-dom/dist/index.js';
                        const lazy = import('@testing-library/jest-dom/extend-expect.js');
                        const required = require('@testing-library/jest-dom/dist/extend-expect');
                        """, source -> source.path("test/ambiguous-legacy.ts")
                        .after(actual -> actual)
                        .afterRecipe(after -> assertEquals(4,
                                occurrences(after.printAll(), "v5 entry is removed or hidden"), after.printAll())))
        );
    }

    @Test
    void recommendedMarkersAreIdempotentAcrossTwoCycles() {
        rewriteRun(
                spec -> spec.recipe(JestDomDependencyTest.environment()
                                .activateRecipes(JestDomDependencyTest.MIGRATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"devDependencies\":{\"@testing-library/jest-dom\":\">=5 <7\"}}",
                        source -> source.path("package.json")
                                .after(actual -> actual)
                                .afterRecipe(after ->
                                        assertEquals(1, occurrences(after.printAll(), "not the exact workbook target"),
                                                after.printAll()))),
                javascript("const x = require('@testing-library/jest-dom/dist/to-be-visible');\n",
                        source -> source.path("src/deep.js")
                                .after(actual -> actual)
                                .afterRecipe(after ->
                                        assertEquals(1, occurrences(after.printAll(), "exports only root"),
                                                after.printAll())))
        );
    }

    @Test
    void recommendedRecipeDiscoveryAndValidationRemainStable() {
        Environment environment = JestDomDependencyTest.environment();
        Recipe recipe = environment.activateRecipes(JestDomDependencyTest.MIGRATE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static <T extends org.openrewrite.SourceFile> void contains(
            org.openrewrite.test.SourceSpec<T> source, String text) {
        source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains(text), after.printAll()));
    }

    private static int occurrences(String text, String fragment) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(fragment, index)) >= 0; index += fragment.length()) count++;
        return count;
    }
}
