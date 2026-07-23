package com.huawei.clouds.openrewrite.jsoup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

/** Removes the deprecated no-op normalization call deleted before jsoup 1.21.1. */
public final class RemoveDocumentNormalise extends Recipe {
    private static final MethodMatcher NORMALISE = new MethodMatcher("org.jsoup.nodes.Document normalise()");

    @Override
    public String getDisplayName() {
        return "Remove deleted jsoup Document.normalise() calls";
    }

    @Override
    public String getDescription() {
        return "Replaces calls to the deleted no-op Document.normalise() method with their receiver expression.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return JsoupSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J visited = super.visitMethodInvocation(method, ctx);
                if (!(visited instanceof J.MethodInvocation invocation) || !NORMALISE.matches(invocation)) return visited;
                Expression receiver = invocation.getSelect();
                return receiver == null ? invocation : receiver.withPrefix(invocation.getPrefix());
            }
        };
    }
}
