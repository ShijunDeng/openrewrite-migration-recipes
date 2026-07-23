package com.huawei.clouds.openrewrite.commonscli;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class CommonsCliJavaRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindCommonsCliJavaRisks())
                .parser(JavaParser.fromJavaVersion().classpath("commons-cli"));
    }

    @Test
    void marksRealLegacyPosixParserEntryPoint() {
        // internetarchive/heritrix3@10ebb393df515630e6be82dc7d8910dc6627b5a3, Warc2Arc.java
        rewriteRun(java(
                """
                import org.apache.commons.cli.*;
                class Warc2Arc {
                    CommandLine parse(Options options, String[] argv) throws Exception {
                        return new PosixParser().parse(options, argv);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(2, count(printed, "/*~~("), printed);
                    assertTrue(printed.contains("deprecated legacy tokenizers"), printed);
                    assertTrue(printed.contains("replay success and failure argv fixtures"), printed);
                })));
    }

    @Test
    void marksDefaultParserConstructionAndParseSemantics() {
        rewriteRun(java(
                """
                import org.apache.commons.cli.*;
                class Parse {
                    CommandLine parse(Options options, String[] argv) throws Exception {
                        return new DefaultParser().parse(options, argv, true);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(2, count(printed, "/*~~("), printed);
                    assertTrue(printed.contains("partial matching"), printed);
                    assertTrue(printed.contains("stopAtNonOption"), printed);
                })));
    }

    @Test
    void marksEveryResidualSplitOptionBuilderCall() {
        // redpen-cc/redpen@875029898b36d9dd4a053a0f925cf7c1a726c6af, RedPenRunner.java
        rewriteRun(java(
                """
                import org.apache.commons.cli.Option;
                import org.apache.commons.cli.OptionBuilder;
                class Split {
                    Option option() {
                        OptionBuilder.withLongOpt("lang");
                        OptionBuilder.hasArg();
                        return OptionBuilder.create("l");
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(3, count(printed, "process-global mutable state"), printed);
                })));
    }

    @Test
    void marksTypeHandlerDateFilesAndRegistryConversionsPrecisely() {
        rewriteRun(java(
                """
                import org.apache.commons.cli.TypeHandler;
                class Convert {
                    Object values(String value) throws Exception {
                        TypeHandler.createDate(value);
                        TypeHandler.createFiles(value);
                        TypeHandler.createNumber(value);
                        TypeHandler.openFile(value);
                        return TypeHandler.createValue(value, Integer.class);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(5, count(printed, "/*~~("), printed);
                    assertTrue(printed.contains("fixed EEE MMM dd HH:mm:ss zzz yyyy format"), printed);
                    assertTrue(printed.contains("no replacement"), printed);
                    assertEquals(3, count(printed, "Converter registry"), printed);
                })));
    }

    @Test
    void marksTypedOptionValuesAndMultiValueBuilderPolicy() {
        rewriteRun(java(
                """
                import org.apache.commons.cli.*;
                class Typed {
                    Object read(CommandLine commandLine) throws Exception {
                        Option option = Option.builder("D").hasArgs().valueSeparator('=').optionalArg(true).build();
                        return commandLine.getParsedOptionValue(option);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(3, count(printed, "/*~~("), printed);
                    assertEquals(2, count(printed, "Multi-value and Java-property"), printed);
                    assertTrue(printed.contains("Supplier defaults"), printed);
                })));
    }

    @Test
    void marksHelpOutputAndExtensionPoints() {
        rewriteRun(java(
                """
                import org.apache.commons.cli.*;
                class CustomParser extends DefaultParser {}
                class Help {
                    void print(Options options) {
                        new HelpFormatter().printHelp("tool", options);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(3, count(printed, "/*~~("), printed);
                    assertTrue(printed.contains("Custom Commons CLI subclass"), printed);
                    assertTrue(printed.contains("externally visible contract"), printed);
                })));
    }

    @Test
    void marksEmptyOptionNameAtTheExactLiteral() {
        rewriteRun(java(
                """
                import org.apache.commons.cli.Option;
                class Empty { Option invalid() { return Option.builder("").build(); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(1, count(printed, "Empty option names now fail fast"), printed);
                    assertTrue(printed.contains("Option.builder(/*~~("), printed);
                })));
    }

    @Test
    void sameNamedApplicationApisAndOrdinaryCliReadsStayClean() {
        rewriteRun(java(
                """
                class DefaultParser { Object parse(Object o) { return o; } }
                class HelpFormatter { void printHelp(String value) {} }
                class Clean {
                    void use(DefaultParser p, HelpFormatter h) {
                        p.parse("x"); h.printHelp("x");
                    }
                }
                """, source -> source.afterRecipe(after -> assertFalse(after.printAll().contains("/*~~(")))));
    }

    @Test
    void generatedParentIsIgnored() {
        rewriteRun(java(
                """
                import org.apache.commons.cli.DefaultParser;
                class Generated { DefaultParser parser = new DefaultParser(); }
                """, source -> source.path("build/generated/Generated.java")));
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = 0; (i = text.indexOf(needle, i)) >= 0; i += needle.length()) count++;
        return count;
    }
}
