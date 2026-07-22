package com.huawei.clouds.openrewrite.graalvmjs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/** Replaces the deprecated disable-eval switch with its exact inverse allow-eval form. */
public final class MigrateDeprecatedGraalVmJsOptions extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate GraalJS disable-eval option";
    }

    @Override
    public String getDescription() {
        return "Change typed Context.Builder.option and System.setProperty calls from js.disable-eval to " +
               "js.allow-eval while inverting literal boolean values.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || GraalVmJsSupport.excluded(source.getSourcePath()) ||
                    !(tree instanceof J.CompilationUnit compilationUnit)) return tree;
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                                    ExecutionContext executionContext) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                        if (m.getArguments().size() != 2 || !(m.getArguments().get(0) instanceof J.Literal key) ||
                            !(m.getArguments().get(1) instanceof J.Literal value) ||
                            !(key.getValue() instanceof String keyText) ||
                            !(value.getValue() instanceof String valueText) ||
                            !("true".equalsIgnoreCase(valueText) || "false".equalsIgnoreCase(valueText))) return m;

                        if (isContextOption(m) && "js.disable-eval".equals(keyText)) {
                            return replaceArguments(m, key, "js.allow-eval", value,
                                    Boolean.toString(!Boolean.parseBoolean(valueText)));
                        }
                        if (isSystemSetProperty(m) && "polyglot.js.disable-eval".equals(keyText)) {
                            return replaceArguments(m, key, "polyglot.js.allow-eval", value,
                                    Boolean.toString(!Boolean.parseBoolean(valueText)));
                        }
                        return m;
                    }
                }.visitNonNull(compilationUnit, ctx);
            }
        };
    }

    private static boolean isContextOption(J.MethodInvocation invocation) {
        if (!"option".equals(invocation.getSimpleName())) return false;
        JavaType.Method methodType = invocation.getMethodType();
        if (methodType == null) return false;
        String declaring = TypeUtils.asFullyQualified(methodType.getDeclaringType()) == null ? "" :
                TypeUtils.asFullyQualified(methodType.getDeclaringType()).getFullyQualifiedName();
        return "org.graalvm.polyglot.Context$Builder".equals(declaring) ||
               "org.graalvm.polyglot.Context.Builder".equals(declaring);
    }

    private static boolean isSystemSetProperty(J.MethodInvocation invocation) {
        return "setProperty".equals(invocation.getSimpleName()) && invocation.getMethodType() != null &&
               TypeUtils.isOfClassType(invocation.getMethodType().getDeclaringType(), "java.lang.System");
    }

    private static J.MethodInvocation replaceArguments(J.MethodInvocation method, J.Literal oldKey, String newKey,
                                                       J.Literal oldValue, String newValue) {
        List<Expression> arguments = method.getArguments();
        return method.withArguments(List.of(
                GraalVmJsSupport.replaceLiteral(oldKey, newKey).withPrefix(arguments.get(0).getPrefix()),
                GraalVmJsSupport.replaceLiteral(oldValue, newValue).withPrefix(arguments.get(1).getPrefix())));
    }
}
