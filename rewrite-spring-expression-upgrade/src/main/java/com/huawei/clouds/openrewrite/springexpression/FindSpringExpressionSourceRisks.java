package com.huawei.clouds.openrewrite.springexpression;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Type-attributed markers for SpEL parser, context, SPI, compiler, and security boundaries. */
public final class FindSpringExpressionSourceRisks extends Recipe {
    private static final String EXPRESSION_PACKAGE = "org.springframework.expression.";
    private static final String PARSER = EXPRESSION_PACKAGE + "spel.standard.SpelExpressionParser";
    private static final String CONFIG = EXPRESSION_PACKAGE + "spel.SpelParserConfiguration";
    private static final String SIMPLE = EXPRESSION_PACKAGE + "spel.support.SimpleEvaluationContext";
    private static final String STANDARD = EXPRESSION_PACKAGE + "spel.support.StandardEvaluationContext";
    private static final MethodMatcher EXPRESSION_GET_VALUE =
            new MethodMatcher("org.springframework.expression.Expression getValue(..)", true);
    private static final Set<String> SPI_TYPES = Set.of(
            EXPRESSION_PACKAGE + "PropertyAccessor",
            EXPRESSION_PACKAGE + "IndexAccessor",
            EXPRESSION_PACKAGE + "MethodResolver",
            EXPRESSION_PACKAGE + "MethodExecutor",
            EXPRESSION_PACKAGE + "MethodFilter",
            EXPRESSION_PACKAGE + "ConstructorResolver",
            EXPRESSION_PACKAGE + "ConstructorExecutor",
            EXPRESSION_PACKAGE + "TypeConverter",
            EXPRESSION_PACKAGE + "TypeLocator",
            EXPRESSION_PACKAGE + "TypeComparator",
            EXPRESSION_PACKAGE + "OperatorOverloader"
    );
    private static final Set<String> CONTEXT_MUTATORS = Set.of(
            "setPropertyAccessors", "addPropertyAccessor", "removePropertyAccessor",
            "setIndexAccessors", "addIndexAccessor", "removeIndexAccessor",
            "setMethodResolvers", "addMethodResolver", "removeMethodResolver",
            "setConstructorResolvers", "addConstructorResolver", "removeConstructorResolver",
            "setTypeConverter", "setTypeLocator", "setTypeComparator", "setOperatorOverloader",
            "setBeanResolver", "setVariable", "setVariables", "registerFunction", "registerMethodFilter",
            "withMethodResolvers", "withInstanceMethods", "withTypeConverter", "withConversionService",
            "withIndexAccessors", "forPropertyAccessors", "forReadOnlyDataBinding", "forReadWriteDataBinding"
    );
    private static final Pattern METHOD_OR_CONSTRUCTOR =
            Pattern.compile("(?s).*\\b(?:new\\s+|T\\s*\\(|@[A-Za-z_$]|[A-Za-z_$][\\w$]*\\s*\\().*");
    private static final Pattern ASSIGNMENT =
            Pattern.compile("(?s).*(?:\\+\\+|--|(?<![=!<>])=(?!=)).*");

    static final String LIMIT =
            "Spring 6.2.19 limits each SpEL evaluation to 10,000 operations by default; size trusted expressions and choose an explicit positive maxOperations only from measured requirements";
    static final String DYNAMIC =
            "A non-literal value reaches parseExpression; establish its trust boundary, reject attacker-controlled grammar, and evaluate with the smallest SimpleEvaluationContext capability set";
    static final String POWERFUL_CONTEXT =
            "StandardEvaluationContext or default Expression evaluation exposes reflective properties, methods, types, constructors and bean resolution; do not use it for untrusted expressions";
    static final String SIMPLE_CONTEXT =
            "SimpleEvaluationContext intentionally excludes type, constructor and bean references; since Spring 6.0 it also rejects array allocation, so regression-test the accepted expression subset";
    static final String ACCESSOR =
            "Spring 6.2 evaluates target-specific PropertyAccessor implementations before generic fallbacks; verify getSpecificTargetClasses plus canRead/canWrite precedence and registration order";
    static final String RESOLUTION =
            "Custom method/constructor resolution or invocation detected; verify overload, varargs, conversion, public declaring type, parameter-name metadata, and exception semantics on Spring 6.2";
    static final String CONVERSION =
            "Custom SpEL type conversion or typed getValue detected; regression-test nulls, generics, collections, numeric coercion, ConversionService ordering, and conversion failures";
    static final String COMPILER =
            "SpEL compilation detected; MIXED and IMMEDIATE modes depend on stable runtime types, public/module-visible members, generated child ClassLoaders, and interpreted fallback behavior";
    static final String REMOVED_INTERNAL =
            "This code depends on a removed or encapsulated SpEL implementation detail (AstUtils, OptimalPropertyAccessor, getLastReadInvokerPair, or VariableScope); replace the internal dependency with a public SPI design";
    static final String EXPRESSION_FEATURE =
            "This expression invokes methods, constructors, types, beans, or assignment; verify resolver capability, side effects, security policy, and the Spring 6 SimpleEvaluationContext restrictions";
    static final String JAKARTA =
            "This SpEL expression embeds a javax type name; Spring 6 uses Jakarta EE 9/10 namespaces, so migrate the literal only after verifying the target API and provider";
    static final String MODULE =
            "Custom SpEL ClassLoader/type lookup or compilation crosses JPMS/native-image boundaries; verify requires/opens, public declaring types, generated classes, and runtime hints";

    @Override
    public String getDisplayName() {
        return "Find Spring Expression 6.2 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark type-attributed parser, evaluation context, accessor/resolver, conversion, compiler, " +
               "operation-limit, security, Jakarta, removed-internal, and module boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(
                    J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return SpringExpressionSupport.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String name = visited.getQualid().printTrimmed(getCursor());
                if (name.contains("org.springframework.expression.spel.ast.AstUtils") ||
                    name.contains("ReflectivePropertyAccessor.OptimalPropertyAccessor") ||
                    name.contains("ExpressionState.VariableScope")) {
                    return SpringExpressionSupport.mark(visited, REMOVED_INTERNAL);
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(
                    J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                J.ClassDeclaration attributed = visited;
                boolean accessor = implementsType(attributed, EXPRESSION_PACKAGE + "PropertyAccessor") ||
                                   implementsType(attributed, EXPRESSION_PACKAGE + "IndexAccessor");
                boolean resolver = SPI_TYPES.stream().filter(type ->
                                !type.endsWith("PropertyAccessor") && !type.endsWith("IndexAccessor") &&
                                !type.endsWith("TypeConverter"))
                        .anyMatch(type -> implementsType(attributed, type));
                boolean converter = implementsType(attributed, EXPRESSION_PACKAGE + "TypeConverter");
                if (accessor) visited = SpringExpressionSupport.mark(visited, ACCESSOR);
                if (resolver) visited = SpringExpressionSupport.mark(visited, RESOLUTION);
                if (converter) visited = SpringExpressionSupport.mark(visited, CONVERSION);
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = fqn(visited.getType());
                if (STANDARD.equals(type)) return SpringExpressionSupport.mark(visited, POWERFUL_CONTEXT);
                if (CONFIG.equals(type)) {
                    List<Expression> args = realArguments(visited);
                    if (args.size() < 7) visited = SpringExpressionSupport.mark(visited, LIMIT);
                    if (!args.isEmpty() && !compilerOff(args.get(0))) {
                        visited = SpringExpressionSupport.mark(visited, COMPILER);
                    }
                    if (args.size() >= 2 && !isNull(args.get(1))) {
                        visited = SpringExpressionSupport.mark(visited, MODULE);
                    }
                    return visited;
                }
                if (SIMPLE.equals(type)) return SpringExpressionSupport.mark(visited, SIMPLE_CONTEXT);
                if (type.equals(EXPRESSION_PACKAGE + "spel.support.StandardTypeConverter")) {
                    return SpringExpressionSupport.mark(visited, CONVERSION);
                }
                if (type.equals(EXPRESSION_PACKAGE + "spel.support.StandardTypeLocator")) {
                    return SpringExpressionSupport.mark(visited, MODULE);
                }
                if (type.equals(EXPRESSION_PACKAGE + "spel.standard.SpelCompiler")) {
                    return SpringExpressionSupport.mark(visited, COMPILER);
                }
                if (visited.getBody() != null && SPI_TYPES.contains(type)) {
                    if (type.endsWith("PropertyAccessor") || type.endsWith("IndexAccessor")) {
                        return SpringExpressionSupport.mark(visited, ACCESSOR);
                    }
                    if (type.endsWith("TypeConverter")) {
                        return SpringExpressionSupport.mark(visited, CONVERSION);
                    }
                    return SpringExpressionSupport.mark(visited, RESOLUTION);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                String owner = methodType == null ? "" : fqn(methodType.getDeclaringType());
                String name = visited.getSimpleName();

                if (isParseExpression(owner, name)) {
                    visited = markParsedExpression(visited);
                    return SpringExpressionSupport.mark(visited, LIMIT);
                }
                if (expressionGetValue(visited) && noEvaluationContextArgument(visited)) {
                    visited = SpringExpressionSupport.mark(visited, POWERFUL_CONTEXT);
                }
                if ((STANDARD.equals(owner) || simpleOwner(owner)) &&
                    CONTEXT_MUTATORS.contains(name)) {
                    if (name.contains("PropertyAccessor") || name.contains("IndexAccessor") ||
                        "forPropertyAccessors".equals(name)) {
                        visited = SpringExpressionSupport.mark(visited, ACCESSOR);
                    } else if (name.contains("TypeConverter") ||
                               "withConversionService".equals(name)) {
                        visited = SpringExpressionSupport.mark(visited, CONVERSION);
                    } else {
                        visited = SpringExpressionSupport.mark(visited, RESOLUTION);
                    }
                    if (STANDARD.equals(owner)) {
                        visited = SpringExpressionSupport.mark(visited, POWERFUL_CONTEXT);
                    } else {
                        visited = SpringExpressionSupport.mark(visited, SIMPLE_CONTEXT);
                    }
                }
                if (expressionGetValue(visited) &&
                    hasExpectedResultType(visited)) {
                    visited = SpringExpressionSupport.mark(visited, CONVERSION);
                }
                if (owner.equals(EXPRESSION_PACKAGE + "spel.standard.SpelCompiler") ||
                    owner.equals(EXPRESSION_PACKAGE + "spel.standard.SpelExpression") &&
                    Set.of("compileExpression", "revertToInterpreted").contains(name)) {
                    visited = SpringExpressionSupport.mark(visited, COMPILER);
                }
                if (owner.equals(EXPRESSION_PACKAGE + "spel.support.ReflectivePropertyAccessor") &&
                    "getLastReadInvokerPair".equals(name)) {
                    visited = SpringExpressionSupport.mark(visited, REMOVED_INTERNAL);
                }
                if ((owner.equals(EXPRESSION_PACKAGE + "spel.support.StandardTypeLocator") &&
                     "registerImport".equals(name)) ||
                    owner.equals(EXPRESSION_PACKAGE + "spel.standard.SpelCompiler") &&
                    "getCompiler".equals(name)) {
                    visited = SpringExpressionSupport.mark(visited, MODULE);
                }
                return visited;
            }

            private J.MethodInvocation markParsedExpression(J.MethodInvocation invocation) {
                if (invocation.getArguments().isEmpty()) return invocation;
                Expression first = invocation.getArguments().get(0);
                if (!(first instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String expression)) {
                    return SpringExpressionSupport.mark(invocation, DYNAMIC);
                }
                String normalized = expression.replace("#{", "").toLowerCase(Locale.ROOT);
                J.MethodInvocation marked = invocation;
                if (normalized.contains("t(javax.") || normalized.contains("new javax.")) {
                    marked = SpringExpressionSupport.mark(marked, JAKARTA);
                }
                if (METHOD_OR_CONSTRUCTOR.matcher(expression).matches() ||
                    ASSIGNMENT.matcher(expression).matches()) {
                    marked = SpringExpressionSupport.mark(marked, EXPRESSION_FEATURE);
                }
                return marked;
            }
        };
    }

    private static boolean isParseExpression(String owner, String name) {
        return "parseExpression".equals(name) &&
               (owner.equals(EXPRESSION_PACKAGE + "ExpressionParser") ||
                owner.equals(PARSER) || owner.startsWith(EXPRESSION_PACKAGE + "common."));
    }

    private static boolean simpleOwner(String owner) {
        return SIMPLE.equals(owner) || owner.startsWith(SIMPLE + "$");
    }

    private static boolean noEvaluationContextArgument(J.MethodInvocation method) {
        return method.getArguments().stream()
                .noneMatch(argument -> TypeUtils.isAssignableTo(
                        EXPRESSION_PACKAGE + "EvaluationContext", argument.getType()));
    }

    private static boolean expressionGetValue(J.MethodInvocation method) {
        if (!"getValue".equals(method.getSimpleName())) return false;
        if (EXPRESSION_GET_VALUE.matches(method)) return true;
        Expression select = method.getSelect();
        return select != null && TypeUtils.isAssignableTo(
                EXPRESSION_PACKAGE + "Expression", select.getType());
    }

    private static boolean hasExpectedResultType(J.MethodInvocation method) {
        if (method.getArguments().stream().anyMatch(argument ->
                argument instanceof J.FieldAccess access && "class".equals(access.getSimpleName()) ||
                TypeUtils.isOfClassType(argument.getType(), "java.lang.Class"))) return true;
        JavaType.Method type = method.getMethodType();
        return type != null && type.getParameterTypes().stream()
                .anyMatch(parameter -> TypeUtils.isOfClassType(parameter, "java.lang.Class"));
    }

    private static boolean implementsType(J.ClassDeclaration declaration, String target) {
        if (declaration.getImplements() != null &&
            declaration.getImplements().stream().anyMatch(type ->
                    TypeUtils.isAssignableTo(target, type.getType()))) return true;
        return declaration.getExtends() != null &&
               TypeUtils.isAssignableTo(target, declaration.getExtends().getType());
    }

    private static List<Expression> realArguments(J.NewClass newClass) {
        return newClass.getArguments().stream()
                .filter(argument -> !(argument instanceof J.Empty)).toList();
    }

    private static boolean compilerOff(Expression expression) {
        if (expression instanceof J.FieldAccess field) return "OFF".equals(field.getSimpleName());
        return expression instanceof J.Identifier identifier && "OFF".equals(identifier.getSimpleName());
    }

    private static boolean isNull(Expression expression) {
        return expression.getType() == JavaType.Primitive.Null ||
               expression instanceof J.Literal literal && literal.getValue() == null;
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }
}
