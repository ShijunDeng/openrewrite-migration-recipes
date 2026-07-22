package com.huawei.clouds.openrewrite.prettier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class PrettierSourceRiskTest implements RewriteTest {
    @ParameterizedTest(name = "namespace async API {0}")
    @ValueSource(strings = {
            "format", "formatWithCursor", "formatAST", "check", "getSupportInfo",
            "clearConfigCache", "resolveConfig", "resolveConfigFile", "getFileInfo"
    })
    void marksEveryNamespaceAsyncApi(String api) {
        assertMarked("import * as prettier from 'prettier';\nconst result = prettier." + api + "(value);",
                "src/namespace.ts", "made this public API asynchronous", api);
    }

    @ParameterizedTest(name = "named async API {0}")
    @ValueSource(strings = {
            "format", "formatWithCursor", "formatAST", "check", "getSupportInfo",
            "clearConfigCache", "resolveConfig", "resolveConfigFile", "getFileInfo"
    })
    void marksEveryNamedAsyncApi(String api) {
        assertMarked("import { " + api + " as owned } from 'prettier';\nconst result = owned(value);",
                "src/named.ts", "made this public API asynchronous", api);
    }

    @ParameterizedTest(name = "CommonJS async API {0}")
    @ValueSource(strings = {
            "format", "formatWithCursor", "formatAST", "check", "getSupportInfo",
            "clearConfigCache", "resolveConfig", "resolveConfigFile", "getFileInfo"
    })
    void marksEveryCommonJsAsyncApi(String api) {
        assertMarked("const prettier = require('prettier');\nconst result = prettier." + api + "(value);",
                "scripts/format.js", "made this public API asynchronous", api);
    }

    @ParameterizedTest(name = "removed sync API {0}")
    @ValueSource(strings = {"resolveConfig", "resolveConfigFile", "getFileInfo"})
    void marksRemovedSyncApis(String api) {
        assertMarked("const prettier = require('prettier');\nconst value = prettier." + api + ".sync(file);",
                "scripts/prettier.js", "removed this .sync API", api + ".sync");
    }

    @ParameterizedTest(name = "physical entry {0}")
    @ValueSource(strings = {
            "prettier/parser-babel", "prettier/parser-typescript", "prettier/esm/parser-babel.mjs",
            "prettier/esm/standalone.mjs", "prettier/bin-prettier.js"
    })
    void marksOldPhysicalImports(String module) {
        assertMarked("import value from '" + module + "';", "src/loader.ts",
                "reorganized physical parser, ESM, and bin files", null);
    }

    @ParameterizedTest(name = "physical require {0}")
    @ValueSource(strings = {"prettier/parser-babel", "prettier/esm/parser-babel.mjs", "prettier/bin-prettier.js"})
    void marksOldPhysicalRequires(String module) {
        assertMarked("const value = require('" + module + "');", "src/install.js",
                "reorganized physical parser, ESM, and bin files", null);
    }

    @ParameterizedTest(name = "removed doc API {0}")
    @ValueSource(strings = {"concat", "getDocParts", "propagateBreaks", "cleanDoc", "getDocType", "printDocToDebug"})
    void marksRemovedDocApis(String api) {
        String chain = "concat".equals(api) ? "prettier.doc.builders.concat(parts)" : "prettier.doc." + api + "(doc)";
        assertMarked("const prettier = require('prettier');\nconst result = " + chain + ";",
                "plugin/index.js", "doc/plugin API was removed or changed", api);
    }

    @Test
    void marksPluginParserAndPrinterOwners() {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()),
                javascript("""
                        import { doc } from 'prettier';
                        export const languages = [{ name: 'Example', parsers: ['example'] }];
                        export const parsers = { example: { parse(text) { return { text }; }, astFormat: 'example' } };
                        export const printers = { example: { print(path) { return path.getValue().text; }, embed(path, print, textToDoc) {} } };
                        """, source -> source.path("plugin/index.mjs").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "Review this Prettier plugin contract");
                            assertContains(after.printAll(), "parsers");
                            assertContains(after.printAll(), "printers");
                        })));
    }

    @Test
    void awaitedAndReturnedCallsStillReceiveContractReview() {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()),
                typescript("""
                        import { format, check } from 'prettier';
                        export async function write(source: string) {
                          const output = await format(source, { parser: 'typescript' });
                          return check(output, { parser: 'typescript' });
                        }
                        """, source -> source.path("src/format.ts").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "made this public API asynchronous");
                            assertContains(printed, "format");
                            assertContains(printed, "check");
                        })));
    }

    @Test
    void defaultEsmImportAndRootCommonJsRequireAreBothOwnedAndValidBoundaries() {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()),
                javascript("import prettier from 'prettier';\nconst output = prettier.format(source, options);",
                        source -> source.path("src/esm.mjs").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "made this public API asynchronous");
                            assertFalse(after.printAll().contains("reorganized physical"), after.printAll());
                        })),
                javascript("const prettier = require('prettier');\nconst output = prettier.format(source, options);",
                        source -> source.path("src/cjs.cjs").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "made this public API asynchronous");
                            assertFalse(after.printAll().contains("reorganized physical"), after.printAll());
                        })));
    }

    @Test
    void sameNamedUnownedAndShadowedFunctionsAreNotMarked() {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()),
                typescript("function format(value: string) { return value; }\nconst output = format(source);",
                        source -> source.path("src/unowned.ts")),
                typescript("import { format } from 'prettier';\nfunction run(format: (x: string) => string) { return format('x'); }",
                        source -> source.path("src/shadow.ts").afterRecipe(after ->
                                assertFalse(after.printAll().contains("made this public API asynchronous"), after.printAll()))),
                javascript("const prettier = require('prettier');\nfunction run(prettier) { return prettier.format('x'); }",
                        source -> source.path("src/shadow-cjs.js").afterRecipe(after ->
                                assertFalse(after.printAll().contains("made this public API asynchronous"), after.printAll()))));
    }

    @Test
    void ordinaryOptionsNamedPrintOrParseAreNotPluginOwners() {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()),
                javascript("import prettier from 'prettier';\nconst options = { print: true, parse: false };\nprettier.resolveConfig(file);",
                        source -> source.path("src/options.js").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "resolveConfig");
                            assertFalse(after.printAll().contains("plugin contract"), after.printAll());
                        })));
    }

    @Test
    void distinguishesExportedPluginContractsFromOrdinaryParserMetadata() {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()),
                javascript("""
                        import prettier from 'prettier';
                        export const languages = [{ name: 'Example', parsers: ['example'] }];
                        const local = { parsers: ['business'], printers: ['receipt'] };
                        prettier.format(source, options);
                        """, source -> source.path("src/options.js").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "made this public API asynchronous");
                            assertFalse(after.printAll().contains("plugin contract"), after.printAll());
                        })),
                javascript("""
                        module.exports = {
                          parsers: { example: { parse(text) { return { text }; }, astFormat: 'example' } },
                          printers: { example: { print(path) { return path.getValue().text; } } }
                        };
                        """, source -> source.path("plugin.cjs").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "plugin contract");
                            assertContains(after.printAll(), "parsers");
                            assertContains(after.printAll(), "printers");
                        })));
    }

    @Test
    void unrelatedStringsSimilarPackagesDynamicRequiresAndExcludedParentsAreIgnored() {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()),
                javascript("const text = 'prettier/parser-babel';", source -> source.path("src/string.js")),
                javascript("import prettier from 'prettier-extra';\nprettier.format(value);", source -> source.path("src/similar.js")),
                javascript("const prettier = require(moduleName);\nprettier.format(value);", source -> source.path("src/dynamic.js")),
                javascript("const prettier = require('prettier');\nprettier.format(value);", source -> source.path("generated/src/file.js")),
                javascript("const prettier = require('prettier');\nprettier.format(value);", source -> source.path("install-cache/file.js")));
    }

    @Test
    void realLiciaFormattingCallsAreDetected() {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()),
                // liriliri/licia@0cfbc3f1b589f2181aff027811d48a889f432966, reduced only by unrelated branches.
                javascript("""
                        const prettier = require('prettier');
                        const { fs, defaults } = require('licia');
                        const prettierCfg = require('../.prettierrc.json');
                        async function format(path, typescript) {
                            let data = await fs.readFile(path, 'utf8');
                            typescript = prettier.format(typescript, defaults({ parser: 'typescript' }, prettierCfg));
                            data = replaceComment(data, 'typescript', typescript);
                            await fs.writeFile(path, data, 'utf8');
                        }
                        """, source -> source.path("fixtures/licia/lib/format.js").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "made this public API asynchronous"))));
    }

    @Test
    void realAntDesignVueSyncAndFormatCallsAreDetected() {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()),
                // vueComponent/ant-design-vue@ca85aec996e0019cffb07a0f879b47ec770a0be4
                javascript("""
                        const prettier = require('prettier');
                        const fs = require('fs');
                        files.forEach(file => {
                          const options = prettier.resolveConfig.sync(file, { config: prettierConfigPath });
                          const fileInfo = prettier.getFileInfo.sync(file);
                          if (fileInfo.ignored) return;
                          const input = fs.readFileSync(file, 'utf8');
                          const output = prettier.format(input, { ...options, parser: fileInfo.inferredParser });
                          if (output !== input) fs.writeFileSync(file, output, 'utf8');
                        });
                        """, source -> source.path("fixtures/ant-design-vue/scripts/prettier.js")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "resolveConfig.sync");
                            assertContains(printed, "getFileInfo.sync");
                            assertContains(printed, "made this public API asynchronous");
                        })));
    }

    @Test
    void sourceMarkersAreIdempotentAndInstallLeafIsEligible() {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                javascript("const prettier = require('prettier');\nconst output = prettier.format(source);",
                        source -> source.path("src/install.js").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            String message = "made this public API asynchronous";
                            assertTrue(printed.indexOf(message) == printed.lastIndexOf(message), printed);
                        })));
    }

    private void assertMarked(String code, String path, String first, String second) {
        rewriteRun(spec -> spec.recipe(new FindPrettierSourceRisks()),
                javascript(code, source -> source.path(path).after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), first);
                    if (second != null) assertContains(after.printAll(), second);
                })));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
