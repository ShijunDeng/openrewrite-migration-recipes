package com.huawei.clouds.openrewrite.springexpression;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Set;

/**
 * Explicit opt-in compatibility recipe. It is deliberately not part of the
 * recommended migration because it disables the target security limit.
 */
public final class PreserveLegacyUnlimitedSpelOperations extends Recipe {
    private static final String CONFIG =
            "org.springframework.expression.spel.SpelParserConfiguration";
    private static final String MODE =
            "org.springframework.expression.spel.SpelCompilerMode";
    private static final Set<String> MODES = Set.of("OFF", "IMMEDIATE", "MIXED");

    @Override
    public String getDisplayName() {
        return "Preserve pre-6.2.19 unlimited SpEL operations";
    }

    @Override
    public String getDescription() {
        return "For constructors with a provably non-null compiler-mode enum constant, append an explicit " +
               "Integer.MAX_VALUE operation limit. This exact compatibility escape hatch is not recommended for " +
               "untrusted expressions.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return SpringExpressionSupport.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J visitedTree = super.visitNewClass(newClass, ctx);
                if (!(visitedTree instanceof J.NewClass visited) ||
                    !TypeUtils.isOfClassType(visited.getType(), CONFIG) ||
                    visited.getBody() != null) return visitedTree;
                List<Expression> arguments = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                if (!explicitMode(arguments)) return visited;
                JavaTemplate template;
                if (arguments.size() == 2) {
                    template = template(
                            "new SpelParserConfiguration(" +
                            "#{any(" + MODE + ")}, #{any(java.lang.ClassLoader)}, false, false, " +
                            "Integer.MAX_VALUE, SpelParserConfiguration.DEFAULT_MAX_EXPRESSION_LENGTH, " +
                            "Integer.MAX_VALUE)");
                } else if (arguments.size() == 5) {
                    template = template(
                            "new SpelParserConfiguration(" +
                            "#{any(" + MODE + ")}, #{any(java.lang.ClassLoader)}, #{any(boolean)}, " +
                            "#{any(boolean)}, #{any(int)}, " +
                            "SpelParserConfiguration.DEFAULT_MAX_EXPRESSION_LENGTH, Integer.MAX_VALUE)");
                } else if (arguments.size() == 6) {
                    template = template(
                            "new SpelParserConfiguration(" +
                            "#{any(" + MODE + ")}, #{any(java.lang.ClassLoader)}, #{any(boolean)}, " +
                            "#{any(boolean)}, #{any(int)}, #{any(int)}, Integer.MAX_VALUE)");
                } else {
                    return visited;
                }
                maybeAddImport(CONFIG);
                return template.apply(
                        updateCursor(visited), visited.getCoordinates().replace(), arguments.toArray());
            }
        };
    }

    private static boolean explicitMode(List<Expression> arguments) {
        if (arguments.isEmpty()) return false;
        Expression mode = arguments.get(0);
        if (mode instanceof J.FieldAccess access) {
            return MODES.contains(access.getSimpleName()) &&
                   TypeUtils.isOfClassType(access.getTarget().getType(), MODE);
        }
        if (mode instanceof J.Identifier identifier) {
            return MODES.contains(identifier.getSimpleName()) &&
                   identifier.getFieldType() != null &&
                   TypeUtils.isOfClassType(identifier.getFieldType().getOwner(), MODE);
        }
        return false;
    }

    private static JavaTemplate template(String source) {
        return JavaTemplate.builder(source)
                .imports(CONFIG)
                .javaParser(JavaParser.fromJavaVersion().dependsOn(
                        "package org.springframework.expression.spel; " +
                        "public enum SpelCompilerMode { OFF, IMMEDIATE, MIXED }",
                        """
                        package org.springframework.expression.spel;
                        public class SpelParserConfiguration {
                            public static final int DEFAULT_MAX_EXPRESSION_LENGTH = 10000;
                            public SpelParserConfiguration(SpelCompilerMode mode, ClassLoader loader,
                                    boolean growNull, boolean growCollections, int maxGrow,
                                    int maxLength, int maxOperations) {}
                        }
                        """))
                .build();
    }
}
