package com.huawei.clouds.openrewrite.jsoup;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class MigrateJsoupApisTest implements RewriteTest {
    private static final String MIGRATE = "com.huawei.clouds.openrewrite.jsoup.MigrateJsoupTo1_21_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.jsoup").build().activateRecipes(MIGRATE))
                .parser(JavaParser.fromJavaVersion().classpath("jsoup"));
    }

    @Test
    void migratesWhitelistToSafelist() {
        rewriteRun(java(
                """
                import org.jsoup.safety.Whitelist;
                class Policy {
                    Whitelist safe() { return Whitelist.relaxed(); }
                }
                """,
                """
                import org.jsoup.safety.Safelist;

                class Policy {
                    Safelist safe() { return Safelist.relaxed(); }
                }
                """));
    }

    @Test
    void migratesRealRenrenWhitelistSubclassAndMarksPolicyBoundary() {
        // renrenio/renren-security@d492800bb364c8f054461a18ad10f0eb7162f675, XssUtils.java
        rewriteRun(java(
                """
                import org.jsoup.Jsoup;
                import org.jsoup.safety.Whitelist;
                class XssUtils extends Whitelist {
                    static String filter(String html) { return Jsoup.clean(html, xssWhitelist()); }
                    private static Whitelist xssWhitelist() {
                        return new Whitelist().addTags("a", "img").addProtocols("a", "href", "http", "https");
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("import org.jsoup.safety.Safelist;"), printed);
                    assertTrue(printed.contains("class XssUtils extends Safelist"), printed);
                    assertTrue(printed.contains("Custom Safelist subclass"), printed);
                    assertTrue(printed.contains("XSS security boundary"), printed);
                    assertTrue(printed.contains("Cleaner/Safelist behavior changed"), printed);
                })));
    }

    @Test
    void removesDeletedNoOpNormaliseFromChain() {
        rewriteRun(spec -> spec.recipe(new RemoveDocumentNormalise()), java(
                """
                import org.jsoup.nodes.Document;
                class Docs { String html(Document doc) { return doc.normalise().outerHtml(); } }
                """,
                """
                import org.jsoup.nodes.Document;
                class Docs { String html(Document doc) { return doc.outerHtml(); } }
                """));
    }

    @Test
    void removesRealZemberekAssignmentInvocation() {
        // ahmetaa/zemberek-nlp@ae2fbe31438dda4dddc674a2a8991d518984d392, TdkLoader.java
        rewriteRun(spec -> spec.recipe(new RemoveDocumentNormalise()), java(
                """
                import org.jsoup.nodes.Document;
                class TdkLoader { void normalize(Document doc) { doc = doc.normalise(); } }
                """,
                """
                import org.jsoup.nodes.Document;
                class TdkLoader { void normalize(Document doc) { doc = doc; } }
                """));
    }

    @Test
    void doesNotTouchApplicationNormaliseMethod() {
        rewriteRun(spec -> spec.recipe(new RemoveDocumentNormalise()), java(
                """
                class Document { Document normalise() { return this; } }
                class SameName { Document use(Document doc) { return doc.normalise(); } }
                """));
    }

    @Test
    void normaliseAutoHonorsGeneratedParentButNotLeafName() {
        String before = """
                import org.jsoup.nodes.Document;
                class generated { Document use(Document doc) { return doc.normalise(); } }
                """;
        String after = """
                import org.jsoup.nodes.Document;
                class generated { Document use(Document doc) { return doc; } }
                """;
        rewriteRun(spec -> spec.recipe(new RemoveDocumentNormalise()),
                java(before.replace("class generated", "class Generated"), source -> source.path("generated/Generated.java")),
                java(before, after, source -> source.path("generated.java")));
    }

    @Test
    void whitelistAutoHonorsGeneratedParentButNotLeafName() {
        String before = """
                import org.jsoup.safety.Whitelist;
                class Policy { Whitelist safe() { return Whitelist.basic(); } }
                """;
        String after = """
                import org.jsoup.safety.Safelist;

                class Policy { Safelist safe() { return Safelist.basic(); } }
                """;
        rewriteRun(
                java(before.replace("class Policy", "class GeneratedPolicy"),
                        source -> source.path("build/generated/GeneratedPolicy.java")),
                java(before, after, source -> source.path("generated.java")));
    }

    @Test
    void removedUncheckedIOExceptionIsMarkedNotUnsafelyChanged() {
        rewriteRun(java(
                """
                import org.jsoup.UncheckedIOException;
                class Errors {
                    RuntimeException text() { return new UncheckedIOException("message-only"); }
                    Throwable cause(UncheckedIOException ex) { return ex.ioException(); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("import org.jsoup.UncheckedIOException"), printed);
                    assertTrue(printed.contains("String constructor and ioException()"), printed);
                })));
    }

    @Test
    void recommendedRecipeUpgradesDependencyBeforeSourceMigration() {
        rewriteRun(
                xml(UpgradeJsoupDependencyTest.pom("1.14.2"), UpgradeJsoupDependencyTest.pom("1.21.1"), source -> source.path("pom.xml")),
                java(
                        """
                        import org.jsoup.safety.Whitelist;
                        class Policy { Whitelist safe() { return Whitelist.basic(); } }
                        """,
                        """
                        import org.jsoup.safety.Safelist;

                        class Policy { Safelist safe() { return Safelist.basic(); } }
                        """));
    }

    @Test
    void publicUpgradeDoesNotRunSourceMigration() {
        var strict = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.jsoup")
                .build().activateRecipes("com.huawei.clouds.openrewrite.jsoup.UpgradeJsoupTo1_21_1");
        rewriteRun(spec -> spec.recipe(strict), java(
                """
                import org.jsoup.safety.Whitelist;
                import org.jsoup.nodes.Document;
                class Legacy { Whitelist policy; Document use(Document doc) { return doc.normalise(); } }
                """));
    }

    @Test
    void recipeCompositionAndTwoCyclesAreStable() {
        var recipe = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.jsoup").build().activateRecipes(MIGRATE);
        assertEquals(5, recipe.getRecipeList().size());
        assertEquals("com.huawei.clouds.openrewrite.jsoup.UpgradeJsoupTo1_21_1", recipe.getRecipeList().get(0).getName());
        assertEquals(MigrateWhitelistToSafelist.class, recipe.getRecipeList().get(1).getClass());
        assertEquals(RemoveDocumentNormalise.class, recipe.getRecipeList().get(2).getClass());
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                import org.jsoup.safety.Whitelist;
                class Once { Whitelist safe() { return Whitelist.none(); } }
                """,
                """
                import org.jsoup.safety.Safelist;

                class Once { Safelist safe() { return Safelist.none(); } }
                """));
    }
}
