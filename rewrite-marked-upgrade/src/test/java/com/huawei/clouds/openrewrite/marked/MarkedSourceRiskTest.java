package com.huawei.clouds.openrewrite.marked;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class MarkedSourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksEveryRemovedOptionAtItsProperty() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        """
                        import { marked } from 'marked';
                        marked.setOptions({
                          highlight, langPrefix: 'lang-', mangle: true, baseUrl: '/docs/',
                          smartypants: true, xhtml: true, headerIds: true, headerPrefix: 'doc-',
                          sanitize: true, sanitizer
                        });
                        """,
                        source -> source.path("src/options.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    for (String option : new String[]{
                                            "highlight", "langPrefix", "mangle", "baseUrl", "smartypants",
                                            "xhtml", "headerIds", "headerPrefix", "sanitize", "sanitizer"}) {
                                        assertTrue(printed.contains(option + " was removed in Marked 8"), option);
                                    }
                                })
                )
        );
    }

    @Test
    void marksUnsafeRendererTransitionFlag() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        "import { marked } from 'marked';\nmarked.use({ useNewRenderer: false, renderer });\n",
                        source -> source.path("src/renderer.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("useNewRenderer was removed")))
                )
        );
    }

    @Test
    void marksRendererTokenizerWalkTokensHooksAndAsyncBoundaries() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        """
                        import { marked } from 'marked';
                        marked.use({ renderer, tokenizer, walkTokens, extensions, hooks, async: true });
                        """,
                        source -> source.path("src/extensions.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    for (String boundary : new String[]{"renderer", "tokenizer", "walkTokens", "extensions", "hooks"}) {
                                        assertTrue(printed.contains(boundary + " crosses token-object"), boundary);
                                    }
                                    assertTrue(printed.contains("enforces async extension contracts"));
                                })
                )
        );
    }

    @Test
    void marksRemovedCallbackApi() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                javascript(
                        "import { marked } from 'marked';\nmarked(markdown, options, (error, html) => done(error, html));\n",
                        source -> source.path("src/callback.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("Marked 5 removed the callback API")))
                )
        );
    }

    @Test
    void marksTwoArgumentCallbackApi() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                javascript(
                        "import { parse } from 'marked';\nparse(markdown, (error, html) => done(error, html));\n",
                        source -> source.path("src/callback-short.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("Marked 5 removed the callback API")))
                )
        );
    }

    @Test
    void marksExplicitAsyncParseContract() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        "import { marked } from 'marked';\nconst html = marked.parse(markdown, { async: true });\n",
                        source -> source.path("src/async.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("explicit async contract")))
                )
        );
    }

    @Test
    void marksParseAndParseInlineOutputBoundaries() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        """
                        import { parse, parseInline } from 'marked';
                        const html = parse(markdown);
                        const inline = parseInline(fragment);
                        """,
                        source -> source.path("src/parse.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Marked parse output changes"));
                                    assertTrue(printed.contains("parseInline output changed"));
                                    assertTrue(printed.contains("does not sanitize HTML"));
                                })
                )
        );
    }

    @Test
    void marksGlobalUseMutationAndInstanceBoundary() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        "import { marked, Marked } from 'marked';\nmarked.use(extension);\nconst isolated = new Marked(extension);\n",
                        source -> source.path("src/instance.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Global Marked options/extensions are cumulative"));
                                    assertTrue(printed.contains("A Marked instance isolates cumulative extensions"));
                                })
                )
        );
    }

    @Test
    void marksLexerParserAndWalkTokenConsumers() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        """
                        import { lexer, parser, walkTokens } from 'marked';
                        const tokens = lexer(markdown);
                        walkTokens(tokens, visit);
                        const html = parser(tokens);
                        """,
                        source -> source.path("src/tokens.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Lexer/token shapes changed"));
                                    assertTrue(printed.contains("walkTokens sees changed list"));
                                    assertTrue(printed.contains("Parser and renderer now consume token objects"));
                                })
                )
        );
    }

    @Test
    void marksChangedApiTypesAndRemovedSluggerImports() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        """
                        import type { MarkedOptions, MarkedExtension, Token, TokensList, RendererObject, TokenizerObject } from 'marked';
                        import { Renderer, Tokenizer, Lexer, Parser, Marked, Slugger } from 'marked';
                        """,
                        source -> source.path("src/types.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Slugger is no longer exported"));
                                    assertTrue(printed.contains("changed across Marked's TypeScript"));
                                    assertTrue(printed.contains("crosses renderer, tokenizer, parser"));
                                })
                )
        );
    }

    @Test
    void marksDefaultDeepRequireAndDynamicLoading() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                javascript(
                        """
                        import marked from 'marked';
                        import { parse } from 'marked/lib/marked.esm.js';
                        const cjs = require('marked');
                        const deep = require('marked/lib/marked.cjs');
                        const lazy = import('marked');
                        """,
                        source -> source.path("src/loading.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("no default export"));
                                    assertTrue(printed.contains("Deep Marked imports"));
                                    assertTrue(printed.contains("removed the CommonJS build"));
                                    assertTrue(printed.contains("Deep Marked loading"));
                                })
                )
        );
    }

    @Test
    void marksRendererConstructionAndOverrideAssignment() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        """
                        import { Renderer } from 'marked';
                        const renderer = new Renderer();
                        renderer.heading = (text, level) => `<h${level}>${text}</h${level}>`;
                        """,
                        source -> source.path("src/custom-renderer.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Renderer methods now receive token objects"));
                                    assertTrue(printed.contains("This Renderer override must accept"));
                                })
                )
        );
    }

    @Test
    void marksLexerRulesInternalAccess() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        "import { Lexer } from 'marked';\nconst blockRules = Lexer.rules.block;\n",
                        source -> source.path("src/rules.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("Lexer.rules internals changed")))
                )
        );
    }

    @Test
    void marksDirectInnerHtmlSink() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        "import { marked } from 'marked';\nelement.innerHTML = marked.parse(untrustedMarkdown);\n",
                        source -> source.path("src/dom.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("before assigning innerHTML")))
                )
        );
    }

    @Test
    void ordinaryMarkdownHelpersAndNativeDomAssignmentAreNotMarked() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        "const html = markdownParser.parse(text);\nelement.textContent = html;\n",
                        source -> source.path("src/unrelated.ts").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void unusedSafeNamedImportIsNotMarked() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        "import { marked, parse } from 'marked';\nexport const value = true;\n",
                        source -> source.path("src/unused.ts")
                )
        );
    }

    @Test
    void shadowedImportedParseNameIsNotMarked() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks()),
                typescript(
                        """
                        import { parse } from 'marked';
                        function render(parse: LocalParser) {
                          return parse(markdown);
                        }
                        """,
                        source -> source.path("src/shadowed.ts")
                )
        );
    }

    @Test
    void riskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindMarked17JavaScriptRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import { parse } from 'marked';\nconst html = parse(markdown);\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual)
                )
        );
    }
}
