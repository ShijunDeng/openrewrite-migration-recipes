package com.huawei.clouds.openrewrite.springexpression;

import org.openrewrite.java.JavaParser;

final class SpringExpressionTestSupport {
    private SpringExpressionTestSupport() {
    }

    static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package org.springframework.expression;
                public interface EvaluationContext {}
                """,
                """
                package org.springframework.expression;
                public interface Expression {
                    Object getValue();
                    Object getValue(Object root);
                    Object getValue(EvaluationContext context);
                    <T> T getValue(Class<T> expected);
                    <T> T getValue(EvaluationContext context, Class<T> expected);
                }
                """,
                """
                package org.springframework.expression;
                public interface ExpressionParser {
                    Expression parseExpression(String expression);
                }
                """,
                "package org.springframework.expression; public interface PropertyAccessor {}",
                "package org.springframework.expression; public interface IndexAccessor {}",
                "package org.springframework.expression; public interface MethodResolver {}",
                "package org.springframework.expression; public interface MethodExecutor {}",
                "package org.springframework.expression; public interface MethodFilter {}",
                "package org.springframework.expression; public interface ConstructorResolver {}",
                "package org.springframework.expression; public interface ConstructorExecutor {}",
                "package org.springframework.expression; public interface TypeConverter {}",
                "package org.springframework.expression; public interface TypeLocator {}",
                "package org.springframework.expression; public interface TypeComparator {}",
                "package org.springframework.expression; public interface OperatorOverloader {}",
                """
                package org.springframework.expression.spel;
                public enum SpelCompilerMode { OFF, IMMEDIATE, MIXED }
                """,
                """
                package org.springframework.expression.spel;
                public class SpelParserConfiguration {
                    public static final int DEFAULT_MAX_EXPRESSION_LENGTH = 10000;
                    public SpelParserConfiguration() {}
                    public SpelParserConfiguration(boolean a, boolean b) {}
                    public SpelParserConfiguration(SpelCompilerMode mode, ClassLoader loader) {}
                    public SpelParserConfiguration(SpelCompilerMode mode, ClassLoader loader,
                            boolean a, boolean b, int size) {}
                    public SpelParserConfiguration(SpelCompilerMode mode, ClassLoader loader,
                            boolean a, boolean b, int size, int length) {}
                    public SpelParserConfiguration(SpelCompilerMode mode, ClassLoader loader,
                            boolean a, boolean b, int size, int length, int operations) {}
                }
                """,
                """
                package org.springframework.expression.spel.standard;
                import org.springframework.expression.Expression;
                import org.springframework.expression.ExpressionParser;
                import org.springframework.expression.spel.SpelParserConfiguration;
                public class SpelExpressionParser implements ExpressionParser {
                    public SpelExpressionParser() {}
                    public SpelExpressionParser(SpelParserConfiguration configuration) {}
                    public Expression parseExpression(String expression) { return null; }
                }
                """,
                """
                package org.springframework.expression.spel.standard;
                public class SpelCompiler {
                    public static SpelCompiler getCompiler(ClassLoader loader) { return null; }
                }
                """,
                """
                package org.springframework.expression.spel.standard;
                public class SpelExpression {
                    public boolean compileExpression() { return false; }
                    public void revertToInterpreted() {}
                }
                """,
                """
                package org.springframework.expression.spel.support;
                import java.util.List;
                import java.util.Map;
                import org.springframework.expression.*;
                public class StandardEvaluationContext implements EvaluationContext {
                    public StandardEvaluationContext() {}
                    public StandardEvaluationContext(Object root) {}
                    public void addPropertyAccessor(PropertyAccessor value) {}
                    public void setPropertyAccessors(List<PropertyAccessor> value) {}
                    public void addIndexAccessor(IndexAccessor value) {}
                    public void setMethodResolvers(List<MethodResolver> value) {}
                    public void setConstructorResolvers(List<ConstructorResolver> value) {}
                    public void setTypeConverter(TypeConverter value) {}
                    public void setTypeLocator(TypeLocator value) {}
                    public void registerFunction(String name, java.lang.reflect.Method method) {}
                    public void setVariables(Map<String, Object> variables) {}
                }
                """,
                """
                package org.springframework.expression.spel.support;
                import org.springframework.expression.*;
                public final class SimpleEvaluationContext implements EvaluationContext {
                    public static Builder forReadOnlyDataBinding() { return new Builder(); }
                    public static Builder forReadWriteDataBinding() { return new Builder(); }
                    public static Builder forPropertyAccessors(PropertyAccessor... values) { return new Builder(); }
                    public static final class Builder {
                        public Builder withMethodResolvers(MethodResolver... values) { return this; }
                        public Builder withInstanceMethods() { return this; }
                        public Builder withTypeConverter(TypeConverter value) { return this; }
                        public Builder withIndexAccessors(IndexAccessor... values) { return this; }
                        public SimpleEvaluationContext build() { return new SimpleEvaluationContext(); }
                    }
                }
                """,
                """
                package org.springframework.expression.spel.support;
                import org.springframework.expression.PropertyAccessor;
                public class ReflectivePropertyAccessor implements PropertyAccessor {
                    public Object getLastReadInvokerPair() { return null; }
                    public static class OptimalPropertyAccessor implements PropertyAccessor {}
                }
                """,
                """
                package org.springframework.expression.spel.support;
                import org.springframework.expression.TypeConverter;
                public class StandardTypeConverter implements TypeConverter {}
                """,
                """
                package org.springframework.expression.spel.support;
                import org.springframework.expression.TypeLocator;
                public class StandardTypeLocator implements TypeLocator {
                    public StandardTypeLocator(ClassLoader loader) {}
                    public void registerImport(String value) {}
                }
                """,
                "package org.springframework.expression.spel.ast; public class AstUtils {}",
                """
                package org.springframework.expression.spel;
                public class ExpressionState {
                    public static class VariableScope {}
                }
                """
        );
    }
}
