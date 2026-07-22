package com.huawei.clouds.openrewrite.losslessjson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class LosslessJsonRecommendedRecipeTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.losslessjson.MigrateLosslessJsonTo4_0_1";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void provectusSafeRangeUpgradesTypesAreRemovedAndNode16IsMarked() {
        // provectus/kafka-ui @ 83b5a60cc08501b570a0c4d0b4cdfceb1b88d6b7
        rewriteRun(
                spec -> spec.recipe(recipe()),
                json(
                        """
                        {
                          "dependencies": {"lossless-json": "^2.0.8"},
                          "devDependencies": {"@types/lossless-json": "^1.0.1", "@types/node": "^16.4.13"},
                          "engines": {"node": "v18.17.1"}
                        }
                        """,
                        source -> source.path("kafka-ui-react-app/package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertFalse(printed.contains("Strict migration skipped"));
                                    assertFalse(printed.contains("@types/lossless-json"));
                                    assertTrue(printed.contains("@types/node"));
                                    assertTrue(printed.contains("dropped official Node.js 16 support"));
                                    assertTrue(printed.contains("\"4.0.1\""));
                                })
                ),
                typescript(
                        """
                        import { parse, stringify } from 'lossless-json';
                        const getSchemaValue = (data: string) => stringify(parse(data), undefined, '\\t');
                        """,
                        source -> source.path("kafka-ui-react-app/src/components/common/EditorViewer/EditorViewer.tsx")
                                .after(actual -> actual)
                                .afterRecipe(cu -> {
                                    assertTrue(cu.printAll().contains("parse returns unknown"));
                                    assertTrue(cu.printAll().contains("downstream parser preserves their precision"));
                                })
                )
        );
    }

    @Test
    void powerSyncRangeUpgradesWhileTypesCallbacksAndBigIntParserAreMarked() {
        // powersync-ja/powersync-service @ 1c44c31b1ceef675f3bffaef2d5bd26c7ccf69b6
        rewriteRun(
                spec -> spec.recipe(recipe()),
                json(
                        """
                        {"name":"@powersync/jsonbig","type":"module","dependencies":{"lossless-json":"^2.0.8"},"devDependencies":{}}
                        """,
                        source -> source.path("packages/jsonbig/package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertFalse(printed.contains("Strict migration skipped"));
                                    assertTrue(printed.contains("\"4.0.1\""));
                                    assertTrue(printed.contains("conditional import/require exports"));
                                })
                ),
                typescript(
                        """
                        import * as json from 'lossless-json';
                        import { isInteger, JavaScriptValue, NumberParser, Replacer, Reviver } from 'lossless-json';
                        const numberParser: NumberParser = value => isInteger(value) ? BigInt(value) : parseFloat(value);
                        export const JSONBig = {
                          parse(text: string, reviver?: Reviver): JavaScriptValue {
                            return json.parse(text, reviver, numberParser);
                          },
                          stringify(value: any, replacer?: Replacer): string {
                            return json.stringify(value, replacer)!;
                          }
                        };
                        """,
                        source -> source.path("packages/jsonbig/src/json.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("JavaScriptValue is deprecated"));
                                    assertTrue(printed.contains("NumberParser now accepts or returns unknown"));
                                    assertTrue(printed.contains("this custom NumberParser controls every integer"));
                                    assertTrue(printed.contains("downstream parser preserves their precision"));
                                })
                )
        );
    }

    @Test
    void ethstakerRangeUpgradesAndCustomWeiParserRemainsForReview() {
        // serenita-org/ethstaker.tax @ 1baf7fbfe024576770fe56b13fe25b37b68e08b5
        rewriteRun(
                spec -> spec.recipe(recipe()),
                json(
                        "{\"type\":\"module\",\"dependencies\":{\"lossless-json\":\"^2.0.11\",\"vue\":\"^3.3.4\"}}",
                        source -> source.path("src/frontend_vue/package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertFalse(document.printAll().contains("Strict migration skipped"));
                                    assertTrue(document.printAll().contains("\"4.0.1\""));
                                    assertTrue(document.printAll().contains("conditional import/require exports"));
                                })
                ),
                typescript(
                        """
                        import { parse, isInteger } from 'lossless-json';
                        function customNumberParser(value: string) {
                          return isInteger(value) ? BigInt(value) : parseFloat(value);
                        }
                        const response = parse(text, undefined, customNumberParser);
                        """,
                        source -> source.path("src/frontend_vue/src/lossless-response.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("this custom NumberParser controls every integer")))
                )
        );
    }

    @Test
    void lidoRangeUpgradesAndNamespaceParseRemainsForTypedNarrowing() {
        // color-typea/lido-trustless-tvl-oracle-solution @ 89bdedcbb5f1e2bcb66e7f4d2aea58ff4b666fb2
        rewriteRun(
                spec -> spec.recipe(recipe()),
                json(
                        "{\"dependencies\":{\"ethers\":\"^6.7.1\",\"lossless-json\":\"^2.0.11\"}}",
                        "{\"dependencies\":{\"ethers\":\"^6.7.1\",\"lossless-json\":\"4.0.1\"}}",
                        source -> source.path("package.json")
                ),
                typescript(
                        """
                        import * as losslessJSON from 'lossless-json';
                        const libs = losslessJSON.parse(fs.readFileSync('./contracts/gates/linked_libs_list.json', 'utf8'));
                        for (const lib of libs) console.log(lib);
                        """,
                        source -> source.path("deploy/00-gates.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("parse returns unknown")))
                )
        );
    }

    @Test
    void kafbatExactVersionUpgradesAndRedundantTypesAreRemoved() {
        // kafbat/kafka-ui @ bc2d7cad4678d5ebb6fd06af49068e34c9cc8b59
        rewriteRun(
                spec -> spec.recipe(recipe()),
                json(
                        """
                        {
                          "dependencies": {"jsonpath-plus":"10.4.0","lossless-json":"2.0.11","pretty-ms":"7.0.1"},
                          "devDependencies": {"@types/lossless-json":"1.0.4","@types/node":"20.11.17"},
                          "engines": {"node":"^22"}
                        }
                        """,
                        """
                        {
                          "dependencies": {"jsonpath-plus":"10.4.0","lossless-json":"4.0.1","pretty-ms":"7.0.1"},
                          "devDependencies": {"@types/node":"20.11.17"},
                          "engines": {"node":"^22"}
                        }
                        """,
                        source -> source.path("frontend/package.json")
                )
        );
    }

    @Test
    void officialTargetPackageManifestIsANoOp() {
        // lossless-json v4.0.1 @ ff25d5086295d0b21decd6bd4b67d9a5c2be9143
        rewriteRun(
                spec -> spec.recipe(recipe()),
                json(
                        """
                        {"name":"lossless-json","version":"4.0.1","main":"lib/umd/lossless-json.js","module":"lib/esm/index.js","types":"lib/types/index.d.ts","sideEffects":false,"exports":{".":{"import":"./lib/esm/index.js","require":"./lib/umd/lossless-json.js","types":"./lib/types/index.d.ts"}}}
                        """,
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void recommendedRecipeCompletesDeterministicChangesAndMarkersInOneCycle() {
        rewriteRun(
                spec -> spec.recipe(recipe()).cycles(1).expectedCyclesThatMakeChanges(1),
                json(
                        "{\"dependencies\":{\"lossless-json\":\"2.0.8\"},\"devDependencies\":{\"@types/lossless-json\":\"1.0.4\"}}",
                        "{\"dependencies\":{\"lossless-json\":\"4.0.1\"},\"devDependencies\":{}}",
                        source -> source.path("package.json")
                ),
                typescript(
                        "import { parse } from 'lossless-json';\nconst value = parse(text);\n",
                        source -> source.path("src/api.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("parse returns unknown")))
                )
        );
    }

    @Test
    void recommendedDeterministicManifestMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(recipe()).cycles(2).expectedCyclesThatMakeChanges(1),
                json(
                        "{\"dependencies\":{\"lossless-json\":\"2.0.11\"},\"devDependencies\":{\"@types/lossless-json\":\"^1.0.1\"}}",
                        "{\"dependencies\":{\"lossless-json\":\"4.0.1\"},\"devDependencies\":{}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void recommendedRecipeIsDiscoverableAndValid() {
        Environment environment = LosslessJsonDependencyUpgradeTest.environment();
        Recipe recipe = environment.activateRecipes(RECIPE);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static Recipe recipe() {
        return LosslessJsonDependencyUpgradeTest.environment().activateRecipes(RECIPE);
    }
}
