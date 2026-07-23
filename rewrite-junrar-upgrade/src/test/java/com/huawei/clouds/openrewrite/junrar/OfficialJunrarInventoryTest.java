package com.huawei.clouds.openrewrite.junrar;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class OfficialJunrarInventoryTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                        "com.huawei.clouds.openrewrite.junrar.InventoryJunrarExtractionEntrypoints"))
                .parser(JavaParser.fromJavaVersion().classpath("junrar"));
    }

    @Test
    void officialFindMethodsActuallyMarksJunrarExtract() {
        rewriteRun(java("""
                import com.github.junrar.Junrar;
                class Extraction {
                    void run() throws Exception {
                        Junrar.extract("input.rar", "out");
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains("/*~~>*/Junrar.extract"), after.printAll()))));
    }

    @Test
    void officialFindMethodsActuallyMarksCustomArchiveCalls() {
        rewriteRun(java("""
                import java.io.OutputStream;
                import com.github.junrar.Archive;
                import com.github.junrar.rarfile.FileHeader;
                class Extraction {
                    void run(Archive archive, FileHeader header, OutputStream output) throws Exception {
                        archive.extractFile(header, output);
                        archive.getInputStream(header);
                        header.getFileName();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("/*~~>*/archive.extractFile"), printed);
            assertTrue(printed.contains("/*~~>*/archive.getInputStream"), printed);
            assertTrue(printed.contains("/*~~>*/header.getFileName"), printed);
        })));
    }

    @Test
    void officialInventorySkipsGeneratedSourcesThroughLocalPrecondition() {
        rewriteRun(java("""
                import com.github.junrar.Junrar;
                class Generated {
                    void run() throws Exception {
                        Junrar.extract("input.rar", "out");
                    }
                }
                """, source -> source.path("target/generated/Generated.java")));
    }

    @Test
    void officialInventoryDoesNotMatchSameNamedBusinessApi() {
        rewriteRun(java("""
                class Junrar {
                    static void extract(String source, String target) {}
                }
                class Business {
                    void run() {
                        Junrar.extract("a", "b");
                    }
                }
                """));
    }
}
