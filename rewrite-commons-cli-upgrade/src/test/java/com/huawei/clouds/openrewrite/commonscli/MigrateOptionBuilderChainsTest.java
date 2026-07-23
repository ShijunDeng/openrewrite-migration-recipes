package com.huawei.clouds.openrewrite.commonscli;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateOptionBuilderChainsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateOptionBuilderChains())
                .parser(JavaParser.fromJavaVersion().classpath("commons-cli"));
    }

    @Test
    void migratesRealSOptCompleteChain() {
        // maniero/SOpt@3bd7fe7ded60581e3e186766eac574b8f78fa611, Java/Algorithm/CliArguments.java
        rewriteRun(java(
                """
                import org.apache.commons.cli.Option;
                import org.apache.commons.cli.OptionBuilder;
                class CliArguments {
                    Option blockSize() {
                        return OptionBuilder.withLongOpt("block-size")
                                .withDescription("use SIZE-byte blocks")
                                .hasArg()
                                .withArgName("SIZE")
                                .create("b");
                    }
                }
                """,
                """
                import org.apache.commons.cli.Option;
                class CliArguments {
                    Option blockSize() {
                        return Option.builder("b").longOpt("block-size").desc("use SIZE-byte blocks").hasArg().argName("SIZE").build();
                    }
                }
                """));
    }

    @Test
    void migratesRealGroovyCompilerStyleChains() {
        // groovy/groovy-core@4c05980922a927b32691e4c3eba5633825cc01e3, FileSystemCompiler.java
        rewriteRun(java(
                """
                import org.apache.commons.cli.Option;
                import org.apache.commons.cli.OptionBuilder;
                class CompilerOptions {
                    Option encoding() {
                        return OptionBuilder.hasArg().withArgName("charset").withDescription("source encoding").create("encoding");
                    }
                }
                """,
                """
                import org.apache.commons.cli.Option;
                class CompilerOptions {
                    Option encoding() {
                        return Option.builder("encoding").hasArg().argName("charset").desc("source encoding").build();
                    }
                }
                """));
    }

    @Test
    void mapsAllSafeBuilderShapes() {
        rewriteRun(java(
                """
                import org.apache.commons.cli.Option;
                import org.apache.commons.cli.OptionBuilder;
                class Shapes {
                    Option a() { return OptionBuilder.hasArg(true).isRequired(true).withValueSeparator('=').withType(Integer.class).create('D'); }
                    Option b() { return OptionBuilder.hasArgs(3).hasOptionalArgs(3).create("values"); }
                    Option c() { return OptionBuilder.hasOptionalArg().withLongOpt("maybe").create(); }
                    Option d() { return OptionBuilder.hasArgs().isRequired().withValueSeparator().create("D"); }
                }
                """,
                """
                import org.apache.commons.cli.Option;
                class Shapes {
                    Option a() { return Option.builder(String.valueOf('D')).hasArg(true).required(true).valueSeparator('=').type(Integer.class).build(); }
                    Option b() { return Option.builder("values").numberOfArgs(3).numberOfArgs(3).optionalArg(true).build(); }
                    Option c() { return Option.builder().hasArg().optionalArg(true).longOpt("maybe").build(); }
                    Option d() { return Option.builder("D").hasArgs().required().valueSeparator().build(); }
                }
                """));
    }

    @Test
    void doesNotMigrateSplitGlobalState() {
        // apache/kylin@b5b94b51abaf83d68088e4fcab606d2a6530e54d, CryptTool.java
        rewriteRun(java(
                """
                import org.apache.commons.cli.Option;
                import org.apache.commons.cli.OptionBuilder;
                class SplitState {
                    Option option() {
                        OptionBuilder.withLongOpt("encrypt");
                        OptionBuilder.hasArg();
                        return OptionBuilder.create("e");
                    }
                }
                """));
    }

    @Test
    void leavesDeprecatedObjectOverloadForManualReview() {
        rewriteRun(java(
                """
                import org.apache.commons.cli.Option;
                import org.apache.commons.cli.OptionBuilder;
                class ObjectType {
                    Option option(Object type) {
                        return OptionBuilder.withType(type).withLongOpt("typed").create("t");
                    }
                }
                """));
    }

    @Test
    void ignoresApplicationBuilderWithTheSameSimpleName() {
        rewriteRun(java(
                """
                class OptionBuilder {
                    static OptionBuilder hasArg() { return new OptionBuilder(); }
                    Option create(String name) { return new Option(); }
                }
                class Option {}
                class SameName { Option option() { return OptionBuilder.hasArg().create("x"); } }
                """));
    }

    @Test
    void ignoresGeneratedParentsButProcessesGeneratedLeafName() {
        String before = """
                import org.apache.commons.cli.Option;
                import org.apache.commons.cli.OptionBuilder;
                class generated { Option o() { return OptionBuilder.hasArg().create("x"); } }
                """;
        String after = """
                import org.apache.commons.cli.Option;
                class generated { Option o() { return Option.builder("x").hasArg().build(); } }
                """;
        rewriteRun(
                java(before.replace("class generated", "class Generated"), source -> source.path("generated/Generated.java")),
                java(before, after, source -> source.path("generated.java")));
    }

    @Test
    void isIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                import org.apache.commons.cli.Option;
                import org.apache.commons.cli.OptionBuilder;
                class Once { Option o() { return OptionBuilder.hasArg().create("x"); } }
                """,
                """
                import org.apache.commons.cli.Option;
                class Once { Option o() { return Option.builder("x").hasArg().build(); } }
                """));
    }
}
