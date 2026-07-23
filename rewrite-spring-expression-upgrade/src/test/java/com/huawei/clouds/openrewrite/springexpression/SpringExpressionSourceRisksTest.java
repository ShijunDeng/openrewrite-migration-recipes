package com.huawei.clouds.openrewrite.springexpression;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SpringExpressionSourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringExpressionSourceRisks())
                .parser(SpringExpressionTestSupport.parser())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void marksParserLimitDynamicInputAndPowerfulDefaultEvaluation() {
        rewriteRun(java(
                """
                import org.springframework.expression.Expression;
                import org.springframework.expression.spel.standard.SpelExpressionParser;
                class GatewayExpressions {
                    Object evaluate(String routeExpression, Object route) {
                        SpelExpressionParser parser = new SpelExpressionParser();
                        Expression expression = parser.parseExpression(routeExpression);
                        return expression.getValue(route);
                    }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String output = after.printAll();
                    assertContains(output, FindSpringExpressionSourceRisks.LIMIT);
                    assertContains(output, FindSpringExpressionSourceRisks.DYNAMIC);
                    assertContains(output, FindSpringExpressionSourceRisks.POWERFUL_CONTEXT);
                })));
    }

    @Test
    void marksLiteralCapabilitiesJakartaAndTypedConversion() {
        rewriteRun(java(
                """
                import org.springframework.expression.Expression;
                import org.springframework.expression.spel.standard.SpelExpressionParser;
                class ConfiguredExpressions {
                    String run(Object root) {
                        Expression expression = new SpelExpressionParser()
                                .parseExpression("T(javax.servlet.http.HttpServletRequest).getName() = 'x'");
                        return expression.getValue(root, String.class);
                    }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String output = after.printAll();
                    assertContains(output, FindSpringExpressionSourceRisks.EXPRESSION_FEATURE);
                    assertContains(output, FindSpringExpressionSourceRisks.JAKARTA);
                    assertContains(output, FindSpringExpressionSourceRisks.CONVERSION);
                })));
    }

    @Test
    void marksSimpleAndStandardContextCapabilityBoundaries() {
        rewriteRun(java(
                """
                import java.util.List;
                import org.springframework.expression.MethodResolver;
                import org.springframework.expression.PropertyAccessor;
                import org.springframework.expression.spel.support.SimpleEvaluationContext;
                import org.springframework.expression.spel.support.StandardEvaluationContext;
                class ContextPolicies {
                    Object simple(PropertyAccessor accessor, MethodResolver resolver) {
                        return SimpleEvaluationContext.forPropertyAccessors(accessor)
                                .withMethodResolvers(resolver).withInstanceMethods().build();
                    }
                    Object standard(PropertyAccessor accessor) {
                        StandardEvaluationContext context = new StandardEvaluationContext();
                        context.addPropertyAccessor(accessor);
                        return context;
                    }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String output = after.printAll();
                    assertContains(output, FindSpringExpressionSourceRisks.SIMPLE_CONTEXT);
                    assertContains(output, FindSpringExpressionSourceRisks.POWERFUL_CONTEXT);
                    assertContains(output, FindSpringExpressionSourceRisks.ACCESSOR);
                    assertContains(output, FindSpringExpressionSourceRisks.RESOLUTION);
                })));
    }

    @Test
    void marksTargetSpecificAccessorResolversAndConverters() {
        rewriteRun(java(
                """
                import org.springframework.expression.MethodResolver;
                import org.springframework.expression.PropertyAccessor;
                import org.springframework.expression.TypeConverter;
                class MapAccessor implements PropertyAccessor {}
                class CustomMethodResolver implements MethodResolver {}
                class DomainConverter implements TypeConverter {}
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String output = after.printAll();
                    assertContains(output, FindSpringExpressionSourceRisks.ACCESSOR);
                    assertContains(output, FindSpringExpressionSourceRisks.RESOLUTION);
                    assertContains(output, FindSpringExpressionSourceRisks.CONVERSION);
                })));
    }

    @Test
    void marksCompilerModeClassLoaderAndLegacyConstructors() {
        rewriteRun(java(
                """
                import org.springframework.expression.spel.SpelCompilerMode;
                import org.springframework.expression.spel.SpelParserConfiguration;
                import org.springframework.expression.spel.standard.SpelCompiler;
                class CompiledExpressions {
                    void configure(ClassLoader loader) {
                        new SpelParserConfiguration(SpelCompilerMode.MIXED, loader, true, true, 256, 10000);
                        SpelCompiler.getCompiler(loader);
                    }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String output = after.printAll();
                    assertContains(output, FindSpringExpressionSourceRisks.LIMIT);
                    assertContains(output, FindSpringExpressionSourceRisks.COMPILER);
                    assertContains(output, FindSpringExpressionSourceRisks.MODULE);
                })));
    }

    @Test
    void sevenArgumentOffConfigurationStillRecordsExplicitSecurityReview() {
        rewriteRun(java(
                """
                import org.springframework.expression.spel.SpelCompilerMode;
                import org.springframework.expression.spel.SpelParserConfiguration;
                class LimitedExpressions {
                    Object configure() {
                        return new SpelParserConfiguration(
                                SpelCompilerMode.OFF, null, false, false, 256, 10000, 5000);
                    }
                }
                """,
                source -> source.afterRecipe(after -> {
                    String output = after.printAll();
                    assertFalse(output.contains(FindSpringExpressionSourceRisks.LIMIT), output);
                    assertFalse(output.contains(FindSpringExpressionSourceRisks.COMPILER), output);
                })));
    }

    @Test
    void marksRemovedInternalsByTypeAttributedImportAndInvocation() {
        rewriteRun(java(
                """
                import org.springframework.expression.spel.ast.AstUtils;
                import org.springframework.expression.spel.ExpressionState.VariableScope;
                import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
                import org.springframework.expression.spel.support.ReflectivePropertyAccessor.OptimalPropertyAccessor;
                class InternalDependencies {
                    Object invoke(ReflectivePropertyAccessor accessor) {
                        OptimalPropertyAccessor optimal = new OptimalPropertyAccessor();
                        VariableScope scope = new VariableScope();
                        return accessor.getLastReadInvokerPair();
                    }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSpringExpressionSourceRisks.REMOVED_INTERNAL))));
    }

    @Test
    void ignoresSameNamedUserApisStringsAndGeneratedSources() {
        rewriteRun(
                java(
                        """
                        class SpelExpressionParser {
                            Object parseExpression(String expression) { return null; }
                        }
                        class StandardEvaluationContext {}
                        class Lookalikes {
                            Object run() {
                                String documentation = "org.springframework.expression.spel.ast.AstUtils";
                                return new SpelExpressionParser().parseExpression("new Object()");
                            }
                        }
                        """,
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                java(
                        """
                        import org.springframework.expression.spel.standard.SpelExpressionParser;
                        class Generated {
                            Object run(String input) {
                                return new SpelExpressionParser().parseExpression(input);
                            }
                        }
                        """,
                        source -> source.path("target/generated/Generated.java")
                                .afterRecipe(after -> assertNoMarker(after.printAll())))
        );
    }

    @Test
    void sourceMarkersAreTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import org.springframework.expression.spel.standard.SpelExpressionParser;
                        class DynamicExpression {
                            Object parse(String input) {
                                return new SpelExpressionParser().parseExpression(input);
                            }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String output = after.printAll();
                            assertEqualsOccurrences(output, FindSpringExpressionSourceRisks.DYNAMIC, 1);
                        })));
    }

    @Test
    void marksFixedCommitRealRepositoryFixtures() {
        rewriteRun(
                java(fixture("spring-cloud-gateway-discovery.java.txt"),
                        source -> source.path("fixtures/GatewayDiscoveryFixture.java")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String output = after.printAll();
                                    assertContains(output, FindSpringExpressionSourceRisks.DYNAMIC);
                                    assertContains(output, FindSpringExpressionSourceRisks.SIMPLE_CONTEXT);
                                    assertContains(output, FindSpringExpressionSourceRisks.CONVERSION);
                                })),
                java(fixture("java-sec-code-spel.java.txt"),
                        source -> source.path("fixtures/JavaSecCodeFixture.java")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String output = after.printAll();
                                    assertContains(output, FindSpringExpressionSourceRisks.DYNAMIC);
                                    assertContains(output, FindSpringExpressionSourceRisks.POWERFUL_CONTEXT);
                                    assertContains(output, FindSpringExpressionSourceRisks.SIMPLE_CONTEXT);
                                })),
                java(fixture("datagear-map-accessor.java.txt"),
                        source -> source.path("fixtures/DataGearMapAccessorFixture.java")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertContains(after.printAll(),
                                                FindSpringExpressionSourceRisks.ACCESSOR))),
                java(fixture("thymeleaf-spel-compiler.java.txt"),
                        source -> source.path("fixtures/ThymeleafCompilerFixture.java")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String output = after.printAll();
                                    assertContains(output, FindSpringExpressionSourceRisks.COMPILER);
                                    assertContains(output, FindSpringExpressionSourceRisks.LIMIT);
                                }))
        );
    }

    private static void assertNoMarker(String output) {
        assertFalse(output.contains("/*~~("), output);
    }

    private static void assertContains(String output, String expected) {
        assertTrue(output.contains(expected), () -> "Missing <" + expected + "> in:\n" + output);
    }

    private static void assertEqualsOccurrences(String output, String expected, int count) {
        int actual = 0;
        for (int at = 0; (at = output.indexOf(expected, at)) >= 0; at += expected.length()) actual++;
        int found = actual;
        assertTrue(found == count, () -> "Expected " + count + " but found " + found + " in:\n" + output);
    }

    private static String fixture(String name) {
        try (InputStream input = SpringExpressionSourceRisksTest.class.getResourceAsStream(
                "/fixtures/real/" + name)) {
            if (input == null) throw new IllegalArgumentException("Missing fixture " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
