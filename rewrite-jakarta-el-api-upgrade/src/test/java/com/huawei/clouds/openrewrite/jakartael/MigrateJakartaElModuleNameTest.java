package com.huawei.clouds.openrewrite.jakartael;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;

class MigrateJakartaElModuleNameTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJakartaElModuleName());
    }

    @ParameterizedTest(name = "legacy module {0}")
    @ValueSource(strings = {"java.el", "javax.el"})
    void migratesHistoricRequiresNames(String oldName) {
        rewriteRun(text("module example.app { requires " + oldName + "; }",
                "module example.app { requires jakarta.el; }",
                source -> source.path("src/main/java/module-info.java")));
    }

    @Test
    void preservesStaticTransitiveModifiersAndFormatting() {
        rewriteRun(text(
                """
                module example.app {
                    requires static transitive java.el ;
                    requires transitive javax.el;
                }
                """,
                """
                module example.app {
                    requires static transitive jakarta.el ;
                    requires transitive jakarta.el;
                }
                """,
                source -> source.path("module-info.java")));
    }

    @Test
    void commentsStringsAndLookalikeModulesAreNoop() {
        String source = """
                // requires java.el;
                /* requires javax.el; */
                module example.app {
                    requires java.enterprise;
                    // String documentation = "requires java.el;";
                }
                """;
        rewriteRun(text(source, spec -> spec.path("src/main/java/module-info.java")));
    }

    @Test
    void targetModuleIsNoop() {
        rewriteRun(text("module example.app { requires jakarta.el; }",
                source -> source.path("src/main/java/module-info.java")));
    }

    @Test
    void ordinaryJavaFileAndGeneratedModuleAreNoop() {
        rewriteRun(
                text("class C { String value = \"requires java.el;\"; }",
                        source -> source.path("src/main/java/C.java")),
                text("module generated { requires java.el; }",
                        source -> source.path("build/generated/module-info.java")));
    }

    @Test
    void multipleRequiresAndTwoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), text(
                "module example { requires java.el; requires javax.el; }",
                "module example { requires jakarta.el; requires jakarta.el; }",
                source -> source.path("module-info.java")));
    }
}
