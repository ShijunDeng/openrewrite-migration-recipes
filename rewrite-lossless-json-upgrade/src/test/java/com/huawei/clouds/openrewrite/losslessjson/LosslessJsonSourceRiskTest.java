package com.huawei.clouds.openrewrite.losslessjson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class LosslessJsonSourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksEveryDeprecatedJavaScriptAndJsonTypeSpecifier() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        """
                        import type {
                          JavaScriptValue, JavaScriptObject, JavaScriptArray, JavaScriptPrimitive,
                          JSONValue, JSONObject, JSONArray, JSONPrimitive
                        } from 'lossless-json';
                        """,
                        source -> source.path("src/types.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    for (String type : new String[]{
                                            "JavaScriptValue", "JavaScriptObject", "JavaScriptArray", "JavaScriptPrimitive",
                                            "JSONValue", "JSONObject", "JSONArray", "JSONPrimitive"}) {
                                        assertTrue(printed.contains(type + " is deprecated in 4.x"), type);
                                    }
                                })
                )
        );
    }

    @Test
    void marksCallbackTypesWhoseValueBoundaryBecameUnknown() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        "import type { Reviver, NumberParser, Replacer, NumberStringifier } from 'lossless-json';\n",
                        source -> source.path("src/callbacks.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    for (String type : new String[]{"Reviver", "NumberParser", "Replacer", "NumberStringifier"}) {
                                        assertTrue(printed.contains(type + " now accepts or returns unknown"), type);
                                    }
                                })
                )
        );
    }

    @Test
    void marksNamedParseCallAtUnknownReturnBoundary() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        "import { parse } from 'lossless-json';\nconst value = parse(text);\n",
                        "import { parse } from 'lossless-json';\nconst value = /*~~(parse returns unknown and numbers default to LosslessNumber; validate or guard the result before property/iteration use and verify numeric conversions)~~>*/parse(text);\n",
                        source -> source.path("src/parse.ts")
                )
        );
    }

    @Test
    void marksAliasedParseCall() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        "import { parse as parseLossless } from 'lossless-json';\nconst value = parseLossless(text);\n",
                        source -> source.path("src/alias.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("parse returns unknown")))
                )
        );
    }

    @Test
    void marksCustomNumberParserCallWithNumericSemantics() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        """
                        import { parse, isInteger } from 'lossless-json';
                        const parser = (value: string) => isInteger(value) ? BigInt(value) : parseFloat(value);
                        const result = parse(text, undefined, parser);
                        """,
                        source -> source.path("src/bigint.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains(
                                        "this custom NumberParser controls every integer, decimal, exponent, negative zero, overflow, and underflow value")))
                )
        );
    }

    @Test
    void marksReviverCallAndDeletionOrdering() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        "import { parse } from 'lossless-json';\nconst result = parse(text, reviver);\n",
                        source -> source.path("src/reviver.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("returning undefined deletes a property")))
                )
        );
    }

    @Test
    void marksLiteralDuplicateKeysWithSpecificPolicyMessage() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                javascript(
                        "import { parse } from 'lossless-json';\nconst result = parse('{\"id\":1,\"id\":2}');\n",
                        source -> source.path("src/duplicates.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("rejects duplicate keys with unequal values")))
                )
        );
    }

    @Test
    void marksStringifyAndCustomNumberStringifierCallsSeparately() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        """
                        import { stringify } from 'lossless-json';
                        const first = stringify(value);
                        const second = stringify(value, undefined, undefined, numberStringifiers);
                        """,
                        source -> source.path("src/stringify.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("downstream parser preserves their precision"));
                                    assertTrue(printed.contains("must emit valid unquoted JSON numbers"));
                                })
                )
        );
    }

    @Test
    void marksNamespaceParseAndStringifyCallsFromPowerSyncShape() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        """
                        import * as json from 'lossless-json';
                        export const JSONBig = {
                          parse(text: string) { return json.parse(text); },
                          stringify(value: unknown) { return json.stringify(value); }
                        };
                        """,
                        source -> source.path("packages/jsonbig/src/json.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("parse returns unknown"));
                                    assertTrue(printed.contains("downstream parser preserves their precision"));
                                })
                )
        );
    }

    @Test
    void marksNumericImportsConstructionAndValueOf() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        """
                        import { LosslessNumber, parseNumberAndBigInt } from 'lossless-json';
                        const amount = new LosslessNumber('2.3e500');
                        const converted = amount.valueOf();
                        const integer = parseNumberAndBigInt('42');
                        """,
                        source -> source.path("src/numbers.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("participates in lossless number"));
                                    assertTrue(printed.contains("LosslessNumber preserves numeric text"));
                                    assertTrue(printed.contains("LosslessNumber.valueOf may return number or bigint"));
                                    assertTrue(printed.contains("parseNumberAndBigInt returns bigint for every integer"));
                                })
                )
        );
    }

    @Test
    void marksDateRevivalAtCall() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        "import { reviveDate } from 'lossless-json';\nconst date = reviveDate('created', value);\n",
                        source -> source.path("src/date.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("matching ISO strings into Date objects")))
                )
        );
    }

    @Test
    void marksDefaultDeepCommonJsAndDynamicImports() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                javascript(
                        """
                        import lossless from 'lossless-json';
                        import { parse } from 'lossless-json/lib/esm/index.js';
                        const cjs = require('lossless-json');
                        const deep = require('lossless-json/lib/umd/lossless-json.js');
                        const lazy = import('lossless-json');
                        """,
                        source -> source.path("src/modules.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("not a default export"));
                                    assertTrue(printed.contains("Deep lossless-json imports bypass"));
                                    assertTrue(printed.contains("CommonJS/dynamic loading"));
                                    assertTrue(printed.contains("Deep lossless-json loading bypasses"));
                                })
                )
        );
    }

    @Test
    void marksDeprecatedConfigAndCircularReferenceBoundary() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        "import { config } from 'lossless-json';\nconfig({ circularRefs: true });\n",
                        source -> source.path("src/config.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("config is deprecated"));
                                    assertTrue(printed.contains("cannot restore removed circularRefs behavior"));
                                })
                )
        );
    }

    @Test
    void ordinaryJsonParseAndStringifyAreNotMarked() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        "const value = JSON.parse(text);\nconst output = JSON.stringify(value);\n",
                        source -> source.path("src/native.ts").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void UnusedSafeNamedImportIsNotMarked() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        "import { parse, stringify } from 'lossless-json';\nexport const untouched = true;\n",
                        source -> source.path("src/unused.ts")
                )
        );
    }

    @Test
    void conservativelyLeavesShadowedImportedFunctionNameUnmarked() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks()),
                typescript(
                        """
                        import { parse } from 'lossless-json';
                        function decode(parse: (text: string) => LocalValue) {
                          return parse(text);
                        }
                        """,
                        source -> source.path("src/shadowed.ts")
                )
        );
    }

    @Test
    void riskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindLosslessJsonJavaScriptRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import { parse } from 'lossless-json';\nconst value = parse(text);\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual)
                )
        );
    }
}
