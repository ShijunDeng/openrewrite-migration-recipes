package com.huawei.clouds.openrewrite.fastglob;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;

class FastGlobJavaScriptRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "await fg(['**/*', '!**/*']);",
            "fg.sync(['**/*', '!/workspace/private/**']);",
            "fg.sync('src/{a,b}//*.ts');",
            "fg.sync('src/{a,b\\\\}/**');",
            "fg.sync('directory/');",
            "fg.sync(['./file.md', 'file.md', '*']);",
            "fg.sync('C:\\\\work\\\\**\\\\*.ts');",
            "fg.sync('\\\\\\\\?\\\\C:\\\\work\\\\**');"
    })
    void marksChangedPatternSemantics(String statement) {
        assertMarked("import fg from 'fast-glob';\n" + statement + "\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ignore: ['!**/.git/**']", "baseNameMatch: true", "braceExpansion: false",
            "caseSensitiveMatch: false", "dot: true", "extglob: false", "globstar: false",
            "cwd: root", "deep: 3", "followSymbolicLinks: false", "suppressErrors: true",
            "throwErrorOnBrokenSymbolicLink: true", "absolute: true", "markDirectories: true",
            "objectMode: true", "onlyDirectories: true", "onlyFiles: false", "stats: true",
            "unique: false", "fs: adapter", "concurrency: 64"
    })
    void marksOwnedOptionBoundaries(String property) {
        assertMarked("import fg from 'fast-glob';\nfg('**/*', { " + property + " });\n");
    }

    @Test
    void marksStreamAndPathHelperContracts() {
        rewriteRun(spec -> spec.recipe(new FindFastGlobJavaScriptRisks()),
                typescript(
                        """
                        import fg from 'fast-glob';
                        const stream = fg.stream('**/*');
                        const escaped = fg.escapePath(input);
                        const pattern = fg.convertPathToPattern(input);
                        """, source -> source.path("src/helpers.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("backpressure"));
                                    assertTrue(printed.contains("Windows brackets"));
                                })));
    }

    @Test
    void marksDeepImportsAndDeepRequire() {
        rewriteRun(spec -> spec.recipe(new FindFastGlobJavaScriptRisks()),
                typescript("import Settings from 'fast-glob/out/settings';\n",
                        source -> source.path("src/deep.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("deep distribution/type import")))),
                typescript("const Settings = require('fast-glob/out/settings');\n",
                        source -> source.path("src/deep.cjs").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("binds to fast-glob internals")))));
    }

    @Test
    void recognizesNamedNamespaceAndCommonJsBindings() {
        rewriteRun(spec -> spec.recipe(new FindFastGlobJavaScriptRisks()),
                typescript(
                        """
                        import { sync as findSync } from 'fast-glob';
                        import * as namespace from 'fast-glob';
                        const common = require('fast-glob');
                        findSync(['*', '!**/*']);
                        namespace.sync('directory/');
                        common.sync('src/{a,b}/**');
                        """, source -> source.path("src/bindings.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("dot:true"));
                                    assertTrue(printed.contains("directory matching"));
                                    assertTrue(printed.contains("brace expansion"));
                                })));
    }

    @Test
    void ignoresOrdinaryPatternsUnrelatedOptionsAndExcludedTrees() {
        rewriteRun(spec -> spec.recipe(new FindFastGlobJavaScriptRisks()),
                typescript("import fg from 'fast-glob';\nfg('src/**/*.ts');\n",
                        source -> source.path("src/simple.ts").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~")))),
                typescript("import other from 'other';\nother('!**/*', { ignore: 'dist/**', fs: adapter });\n",
                        source -> source.path("src/unrelated.ts").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~")))),
                typescript("import fg from 'fast-glob'; fg('!**/*', { dot: true });",
                        source -> source.path("dist/bundle.ts")));
    }

    @Test
    void markerRecipeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindFastGlobJavaScriptRisks()).cycles(2),
                typescript("import fg from 'fast-glob';\nfg(['**/*', '!**/*']);\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("dot:true"));
                                    assertFalse(printed.contains("~~(~~("));
                                })));
    }

    private void assertMarked(String sourceCode) {
        rewriteRun(spec -> spec.recipe(new FindFastGlobJavaScriptRisks()),
                typescript(sourceCode, source -> source.path("src/glob.ts").after(actual -> actual)
                        .afterRecipe(cu -> assertTrue(cu.printAll().contains("~~")))));
    }
}
