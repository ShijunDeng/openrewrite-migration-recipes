package com.huawei.clouds.openrewrite.marked;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

/** End-to-end coverage using reduced, verbatim fragments from repositories pinned in README.md. */
class MarkedRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.marked.MigrateMarkedTo17_0_6";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(MarkedDependencyUpgradeTest.environment().activateRecipes(RECOMMENDED));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=4.0.10", ">=4.2.12 <5", "4.0.10 || 5.1.1", "4.2.3 - 5.1.0", "4.x",
            "workspace:^5.1.1", "npm:@company/marked@4.2.12", "github:markedjs/marked#v5.1.1",
            "file:../marked", "https://example.test/marked-4.2.12.tgz", "latest", "$markedVersion"
    })
    void recommendedRecipeLeavesUnsafeDeclarationsAndMarksTheDecision(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"marked\":\"" + declaration + "\"}}",
                source -> source.path("package.json").after(actual -> actual)
                        .afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains(declaration));
                            assertTrue(printed.contains("Strict migration skipped this complex range"));
                            assertFalse(printed.contains("\"marked\":\"17.0.6\""));
                        })
        ));
    }

    @Test
    void migratesGolangPkgsiteAtPinnedCommit() {
        rewriteRun(
                json(
                        """
                        {"name":"pkgsite","dependencies":{"@types/marked":"4.0.1","jest":"27.3.1","marked":"4.0.10","typescript":"4.0.3"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"marked\":\"17.0.6\""));
                                    assertTrue(printed.contains("@types/marked remains because ownership"));
                                })
                ),
                typescript(
                        """
                        import { marked } from 'marked';
                        import fs from 'fs';

                        export async function parse(file: string): Promise<string> {
                          marked.use({ renderer: { code: code => code } });
                          const f = await new Promise<string>((resolve, reject) =>
                            fs.readFile(file, { encoding: 'utf-8' }, (err, data) => {
                              if (err) {
                                reject(err);
                                return;
                              }
                              resolve(data);
                            })
                          );
                          return marked(f);
                        }
                        """,
                        source -> source.path("static/markdown.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("renderer crosses token-object"));
                                    assertTrue(printed.contains("Marked parse output changes"));
                                })
                )
        );
    }

    @Test
    void migratesMdToPdfAtPinnedCommit() {
        rewriteRun(
                json(
                        """
                        {"name":"md-to-pdf","engines":{"node":">=12.0"},"dependencies":{"highlight.js":"^11.7.0","marked":"^4.2.12"},"devDependencies":{"@types/marked":"4.0.8","typescript":"5.0.3"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"marked\":\"17.0.6\""));
                                    assertFalse(printed.contains("@types/marked"));
                                    assertTrue(printed.contains("requires Node >=20"));
                                })
                ),
                typescript(
                        """
                        import hljs from 'highlight.js';
                        import { marked } from 'marked';

                        export const getMarked = (options: marked.MarkedOptions, extensions: marked.MarkedExtension[]) => {
                            marked.setOptions({
                                highlight: (code, languageName) => {
                                    const language = hljs.getLanguage(languageName) ? languageName : 'plaintext';
                                    return hljs.highlight(code, { language }).value;
                                },
                                langPrefix: 'hljs ',
                                ...options,
                            });
                            marked.use(...extensions);
                            return marked;
                        };
                        """,
                        source -> source.path("src/lib/get-marked-with-highlighter.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("highlight was removed in Marked 8"));
                                    assertTrue(printed.contains("langPrefix was removed in Marked 8"));
                                    assertTrue(printed.contains("Global Marked options/extensions are cumulative"));
                                })
                )
        );
    }

    @Test
    void migratesMongooseAtPinnedCommit() {
        rewriteRun(
                json(
                        """
                        {"name":"mongoose","engines":{"node":">=12.0.0"},"devDependencies":{"highlight.js":"11.7.0","marked":"4.2.12","typescript":"4.9.5"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"marked\":\"17.0.6\""));
                                    assertTrue(printed.contains("requires Node >=20"));
                                })
                ),
                javascript(
                        """
                        'use strict';
                        const { marked: markdown } = require('marked');
                        const highlight = require('highlight.js');
                        const renderer = {
                          heading: function(text, level, raw, slugger) {
                            const slug = slugger.slug(raw);
                            return `<h${level} id="${slug}">${text}</h${level}>\n`;
                          }
                        };
                        markdown.setOptions({
                          highlight: function(code, language) {
                            return highlight.highlight(code, { language }).value;
                          }
                        });
                        markdown.use({ renderer });
                        """,
                        source -> source.path("scripts/website.js").after(actual -> actual)
                                .afterRecipe(cu ->
                                        assertTrue(cu.printAll().contains("removed the CommonJS build")))
                )
        );
    }

    @Test
    void migratesTypeDocNamespaceApiAtPinnedCommit() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json(
                        """
                        {"name":"typedoc","engines":{"node":">= 12.10.0"},"dependencies":{"marked":"^4.0.12"},"devDependencies":{"@types/marked":"^4.0.2","@types/node":"^17.0.21","typescript":"^4.6.2"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"marked\":\"17.0.6\""));
                                    assertFalse(printed.contains("@types/marked"));
                                    assertTrue(printed.contains("requires Node >=20"));
                                })
                ),
                typescript(
                        """
                        import * as Marked from "marked";

                        const customMarkedRenderer = new Marked.Renderer();
                        customMarkedRenderer.heading = (text, level, _, slugger) => {
                            const slug = slugger.slug(text);
                            return `<h${level}>${text}</h${level}>`;
                        };

                        function configure(options: Marked.marked.MarkedOptions) {
                            Marked.marked.setOptions({ highlight, mangle: false, renderer: customMarkedRenderer, ...options });
                            return Marked.marked('# heading');
                        }
                        """,
                        source -> source.path("src/lib/output/themes/MarkedPlugin.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Renderer methods now receive token objects"));
                                    assertTrue(printed.contains("This Renderer override must accept"));
                                    assertTrue(printed.contains("MarkedOptions changed across Marked's TypeScript"));
                                    assertTrue(printed.contains("highlight was removed in Marked 8"));
                                    assertTrue(printed.contains("Marked parse output changes"));
                                })
                )
        );
    }

    @Test
    void migratesJ2MAtPinnedNpmGitHead() {
        rewriteRun(
                json(
                        """
                        {"name":"jira2md","version":"3.0.1","dependencies":{"marked":"^4.0.12"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document ->
                                        assertTrue(document.printAll().contains("\"marked\":\"17.0.6\"")))
                ),
                javascript(
                        """
                        const { marked } = require('marked');
                        marked.setOptions({ breaks: true, smartyPants: true });
                        class J2M {
                            static md_to_html(str) {
                                return marked.parse(str);
                            }
                        }
                        module.exports = J2M;
                        """,
                        source -> source.path("index.js").after(actual -> actual)
                                .afterRecipe(cu ->
                                        assertTrue(cu.printAll().contains("removed the CommonJS build")))
                )
        );
    }

    @Test
    void officialMarked17ManifestAndUnusedNamedApiAreNoOp() {
        rewriteRun(
                json(
                        """
                        {"name":"marked","version":"17.0.6","type":"module","main":"./lib/marked.esm.js","module":"./lib/marked.esm.js","browser":"./lib/marked.umd.js","types":"./lib/marked.d.ts","exports":{".":{"types":"./lib/marked.d.ts","default":"./lib/marked.esm.js"}},"engines":{"node":">= 20"}}
                        """,
                        source -> source.path("package.json")
                ),
                javascript(
                        "import { marked } from 'marked';\nexport const version = '17.0.6';\n",
                        source -> source.path("src/target.js")
                )
        );
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossManifestAndSource() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json(
                        """
                        {"engines":{"node":">=18"},"dependencies":{"marked":"~5.1.1"},"devDependencies":{"@types/marked":"5.0.2"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                ),
                typescript(
                        "import { marked } from 'marked';\nmarked.use({ useNewRenderer: true, renderer });\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertFalse(printed.contains("useNewRenderer"));
                                    assertTrue(printed.contains("renderer crosses token-object"));
                                })
                )
        );
    }

    @Test
    void recommendedRecipeIsDiscoverableAndValid() {
        Environment environment = MarkedDependencyUpgradeTest.environment();
        Recipe recipe = environment.activateRecipes(RECOMMENDED);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECOMMENDED.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }
}
