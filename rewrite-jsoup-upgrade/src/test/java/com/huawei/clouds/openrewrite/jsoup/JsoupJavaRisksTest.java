package com.huawei.clouds.openrewrite.jsoup;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class JsoupJavaRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJsoupJavaRisks()).parser(JavaParser.fromJavaVersion().classpath("jsoup"));
    }

    @Test
    void marksNetworkEntryPointsAndResponseStreamContracts() {
        rewriteRun(java(
                """
                import org.jsoup.Connection;
                import org.jsoup.Jsoup;
                class Fetch {
                    void fetch(Connection.Response response) throws Exception {
                        Jsoup.connect("https://example.test").get();
                        response.bufferUp();
                        response.bodyStream();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("prefers JDK HttpClient"), out);
                    assertTrue(out.contains("checked IOException"), out);
                    assertTrue(out.contains("unconstrained BufferedInputStream"), out);
                })));
    }

    @Test
    void marksCleanerAndEverySafelistPolicyMutation() {
        rewriteRun(java(
                """
                import org.jsoup.Jsoup;
                import org.jsoup.safety.Safelist;
                class Clean {
                    String clean(String html) {
                        Safelist safelist = Safelist.relaxed().preserveRelativeLinks(true)
                                .addTags("noscript").addProtocols("a", "href", "https");
                        return Jsoup.clean(html, safelist);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(count(out, "XSS security boundary") >= 3, out);
                    assertTrue(out.contains("malicious and allowed HTML corpora"), out);
                })));
    }

    @Test
    void marksMatchTextAtTheSelectorLiteral() {
        rewriteRun(java(
                """
                import org.jsoup.nodes.Element;
                class Select { void find(Element root) { root.select("p:matchText:first-child"); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("/*~~(:matchText mutates the DOM"), out);
                    assertTrue(out.contains("::textnode"), out);
                })));
    }

    @Test
    void marksChangedHasEmptyAndInvalidCombinatorSelectors() {
        rewriteRun(java(
                """
                import org.jsoup.nodes.Element;
                class Selectors { void find(Element root) {
                    root.select("div:empty");
                    root.select("h1:has(+ h2)");
                    root.select("div >> p");
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertEquals(3, count(after.printAll(), "Selector parsing/matching changed"), after.printAll()))));
    }

    @Test
    void marksElementsAndNodeMutationSemantics() {
        rewriteRun(java(
                """
                import org.jsoup.nodes.Element;
                import org.jsoup.select.Elements;
                class Mutate { void change(Elements selected, Element element) {
                    selected.clear();
                    selected.remove(0);
                    element.empty();
                    element.remove();
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertEquals(2, count(out, "backing DOM"), out);
                    assertEquals(2, count(out, "Node mutation and detachment"), out);
                })));
    }

    @Test
    void marksSerializationAndW3cNamespaceBoundaries() {
        rewriteRun(java(
                """
                import org.jsoup.helper.W3CDom;
                import org.jsoup.nodes.Document;
                import org.jsoup.nodes.Element;
                class Output { String write(Document doc, Element element) {
                    doc.outputSettings().prettyPrint(true).syntax(Document.OutputSettings.Syntax.xml);
                    org.w3c.dom.Document w3c = W3CDom.convert(doc);
                    W3CDom.asString(w3c, W3CDom.OutputXml());
                    return element.outerHtml();
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(count(out, "serialized bytes") >= 3, out);
                    assertTrue(count(out, "W3CDom/XML namespace") >= 2, out);
                })));
    }

    @Test
    void marksRemovedInternalAndCompatibilityImportsOnly() {
        rewriteRun(java(
                """
                import org.jsoup.internal.StringUtil;
                import org.jsoup.nodes.Document;
                class Internal { Document document; String value = StringUtil.normaliseWhitespace(" x "); }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertEquals(1, count(out, "internal/compatibility API"), out);
                    assertTrue(out.contains("~~>*/import org.jsoup.internal.StringUtil"), out);
                    assertFalse(out.contains("~~>*/import org.jsoup.nodes.Document"), out);
                })));
    }

    @Test
    void typeAttributionRejectsSameNamedApplicationApis() {
        rewriteRun(java(
                """
                class Safelist { Safelist addTags(String s) { return this; } }
                class Response { Response bufferUp() { return this; } }
                class Same { void use(Safelist s, Response r) { s.addTags("x"); r.bufferUp(); } }
                """));
    }

    @Test
    void generatedParentIsIgnored() {
        rewriteRun(java(
                """
                import org.jsoup.Jsoup;
                class Generated { void fetch() throws Exception { Jsoup.connect("https://example.test").get(); } }
                """, source -> source.path("build/generated/Generated.java")));
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = 0; (i = text.indexOf(needle, i)) >= 0; i += needle.length()) count++;
        return count;
    }
}
