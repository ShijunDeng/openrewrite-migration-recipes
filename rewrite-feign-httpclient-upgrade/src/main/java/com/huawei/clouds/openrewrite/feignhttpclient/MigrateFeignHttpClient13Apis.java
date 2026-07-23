package com.huawei.clouds.openrewrite.feignhttpclient;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/** Deterministic Feign API migration reached through a Feign Apache HttpClient upgrade. */
public final class MigrateFeignHttpClient13Apis extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Feign Apache HttpClient 13 APIs";
    }

    @Override
    public String getDescription() {
        return "Rename Feign builder decode404() to the 13.6 dismiss404() spelling when method attribution proves " +
               "the receiver is a Feign builder; the behavior is explicitly documented as equivalent.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedFeignHttpClientDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (!methodOn(method, "feign.BaseBuilder", "decode404") &&
                    !methodOn(method, "feign.Feign$Builder", "decode404")) return visited;
                JavaType.Method replacement = method.withName("dismiss404");
                return visited.withName(visited.getName().withSimpleName("dismiss404").withType(replacement))
                        .withMethodType(replacement);
            }
        };
    }

    static boolean methodOn(JavaType.Method method, String owner, String name) {
        return method != null && name.equals(method.getName()) && method.getDeclaringType() != null &&
               TypeUtils.isAssignableTo(owner, method.getDeclaringType());
    }
}
