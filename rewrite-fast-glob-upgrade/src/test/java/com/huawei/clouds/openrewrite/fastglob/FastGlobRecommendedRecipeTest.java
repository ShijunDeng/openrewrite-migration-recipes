package com.huawei.clouds.openrewrite.fastglob;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

/** Reduced fixtures from immutable official and real-repository revisions documented in README.md. */
class FastGlobRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.fastglob.MigrateFastGlobTo3_3_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(FastGlobDependencyUpgradeTest.environment().activateRecipes(RECOMMENDED));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesWorkbookDependencyAndOfficialIgnoreCompatibilityCase() {
        rewriteRun(
                json("{\"devDependencies\":{\"fast-glob\":\"^3.2.2\"}}",
                        "{\"devDependencies\":{\"fast-glob\":\"^3.3.3\"}}",
                        source -> source.path("package.json")),
                typescript("import fg from 'fast-glob';\nawait fg('**/*.ts', { ignore: '**/*.spec.ts' });\n",
                        source -> source.path("scripts/check.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("ignore: ['**/*.spec.ts']"));
                                    assertTrue(printed.contains("negative matching"));
                                })));
    }

    @Test
    void evaluatesPrettierPinnedCwdFixture() {
        rewriteRun(typescript(
                """
                import path from 'node:path';
                import fastGlob from 'fast-glob';
                for (const fileName of await fastGlob(['plugins/*.mjs'], {
                  cwd: path.join(packagesDirectory, 'prettier'),
                })) {
                  pluginFiles.push(`prettier/${fileName}`);
                }
                """, source -> source.path("scripts/build-website.js").after(actual -> actual)
                        .afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("cwd depth"));
                            assertTrue(printed.contains("plugins/*.mjs"));
                        })));
    }

    @Test
    void evaluatesStylelintPinnedEscapePathFixture() {
        rewriteRun(typescript(
                """
                import fastGlob from 'fast-glob';
                import normalizePath from 'normalize-path';
                const escaped = entries.map((entry) => fastGlob.escapePath(normalizePath(entry)));
                """, source -> source.path("lib/standalone.mjs").after(actual -> actual)
                        .afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("Windows brackets"));
                            assertTrue(printed.contains("escapePath"));
                        })));
    }

    @Test
    void evaluatesTailwindPinnedCwdFixtureWithoutInventingPatternChanges() {
        rewriteRun(typescript(
                """
                import fastGlob from 'fast-glob';
                import path from 'node:path';
                async function glob(root: string, pattern: string) {
                  const files = await fastGlob(pattern, { cwd: root });
                  return Promise.all(files.map(async (file) => path.join(root, file)));
                }
                """, source -> source.path("integrations/utils.ts").after(actual -> actual)
                        .afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("cwd depth"));
                            assertFalse(printed.contains("brace expansion"));
                            assertFalse(printed.contains("dot:true"));
                        })));
    }
}
