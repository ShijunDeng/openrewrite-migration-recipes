package com.huawei.clouds.openrewrite.springexpression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class PreserveLegacyUnlimitedSpelOperationsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreserveLegacyUnlimitedSpelOperations())
                .parser(SpringExpressionTestSupport.parser())
                .typeValidationOptions(TypeValidation.none());
    }

    @ParameterizedTest(name = "expands {0}-argument constructor")
    @MethodSource("eligibleConstructors")
    void expandsEligibleConstructors(String label, String before, String after) {
        rewriteRun(java(source(before), source(after)));
    }

    static Stream<Arguments> eligibleConstructors() {
        return Stream.of(
                Arguments.of("two",
                        "new SpelParserConfiguration(SpelCompilerMode.OFF, loader)",
                        """
                        new SpelParserConfiguration(SpelCompilerMode.OFF, loader, false, false, Integer.MAX_VALUE, \
                        SpelParserConfiguration.DEFAULT_MAX_EXPRESSION_LENGTH, Integer.MAX_VALUE)"""),
                Arguments.of("five",
                        "new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, loader, true, false, 512)",
                        """
                        new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, loader, true, false, 512, \
                        SpelParserConfiguration.DEFAULT_MAX_EXPRESSION_LENGTH, Integer.MAX_VALUE)"""),
                Arguments.of("six",
                        "new SpelParserConfiguration(SpelCompilerMode.MIXED, loader, false, true, 128, 20000)",
                        """
                        new SpelParserConfiguration(SpelCompilerMode.MIXED, loader, false, true, 128, 20000, \
                        Integer.MAX_VALUE)""")
        );
    }

    @Test
    void supportsStaticImportedCompilerMode() {
        rewriteRun(java(
                """
                import static org.springframework.expression.spel.SpelCompilerMode.OFF;
                import org.springframework.expression.spel.SpelParserConfiguration;
                class ParserFactory {
                    Object parser(ClassLoader loader) {
                        return new SpelParserConfiguration(OFF, loader);
                    }
                }
                """,
                """
                import static org.springframework.expression.spel.SpelCompilerMode.OFF;
                import org.springframework.expression.spel.SpelParserConfiguration;
                class ParserFactory {
                    Object parser(ClassLoader loader) {
                        return new SpelParserConfiguration(OFF, loader, false, false, Integer.MAX_VALUE, SpelParserConfiguration.DEFAULT_MAX_EXPRESSION_LENGTH, Integer.MAX_VALUE);
                    }
                }
                """));
    }

    @Test
    void preservesNullDynamicSevenArgumentAndUnrelatedConstructors() {
        rewriteRun(java(
                """
                import org.springframework.expression.spel.SpelCompilerMode;
                import org.springframework.expression.spel.SpelParserConfiguration;
                class ParserPolicies {
                    Object[] create(SpelCompilerMode mode, ClassLoader loader) {
                        return new Object[] {
                            new SpelParserConfiguration(null, loader),
                            new SpelParserConfiguration(mode, loader),
                            new SpelParserConfiguration(
                                    SpelCompilerMode.OFF, loader, false, false, 256, 10000, 10000),
                            new Object()
                        };
                    }
                }
                """));
    }

    @Test
    void skipsGeneratedSources() {
        rewriteRun(java(source("new SpelParserConfiguration(SpelCompilerMode.OFF, loader)"),
                source -> source.path("build/generated/ParserFactory.java")));
    }

    @Test
    void transformationIsTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        source("new SpelParserConfiguration(SpelCompilerMode.MIXED, loader)"),
                        source("""
                               new SpelParserConfiguration(SpelCompilerMode.MIXED, loader, false, false, \
                               Integer.MAX_VALUE, SpelParserConfiguration.DEFAULT_MAX_EXPRESSION_LENGTH, \
                               Integer.MAX_VALUE)""")));
    }

    private static String source(String expression) {
        return """
               import org.springframework.expression.spel.SpelCompilerMode;
               import org.springframework.expression.spel.SpelParserConfiguration;
               class ParserFactory {
                   Object parser(ClassLoader loader) {
                       return %s;
                   }
               }
               """.formatted(expression);
    }
}
