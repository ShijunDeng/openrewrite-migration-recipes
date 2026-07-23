package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Map;

/** Migrate only API calls for which the removed method documented a direct equivalent. */
public final class MigrateTomcatCatalina101Java extends Recipe {
    private static final Map<String, Map<String, String>> DIRECT_RENAMES = Map.of(
            "jakarta.servlet.http.HttpServletRequest", Map.of(
                    "isRequestedSessionIdFromUrl", "isRequestedSessionIdFromURL"),
            "jakarta.servlet.http.HttpServletResponse", Map.of(
                    "encodeUrl", "encodeURL",
                    "encodeRedirectUrl", "encodeRedirectURL"),
            "jakarta.servlet.http.HttpSession", Map.of(
                    "getValue", "getAttribute",
                    "putValue", "setAttribute",
                    "removeValue", "removeAttribute"),
            "jakarta.el.MethodExpression", Map.of(
                    "isParmetersProvided", "isParametersProvided"));

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Tomcat 10.1 Servlet and EL APIs";
    }

    @Override
    public String getDescription() {
        return "Rename documented Servlet 5 compatibility delegates removed by Servlet 6, fix the deprecated " +
               "MethodExpression spelling, and reorder ServletContext.log(Exception,String) to log(String,Throwable).";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedTomcatCatalinaDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null) return visited;
                String owner = normalizedOwner(method.getDeclaringType());

                if (isLegacyServletContextLog(owner, method, visited)) {
                    JavaType.Method replacementType = method.withParameterTypes(List.of(
                            JavaType.ShallowClass.build("java.lang.String"),
                            JavaType.ShallowClass.build("java.lang.Throwable")));
                    Expression oldException = visited.getArguments().get(0);
                    Expression oldMessage = visited.getArguments().get(1);
                    return visited.withArguments(List.of(
                                    oldMessage.withPrefix(oldException.getPrefix()),
                                    oldException.withPrefix(oldMessage.getPrefix())))
                            .withName(visited.getName().withType(replacementType))
                            .withMethodType(replacementType);
                }

                String replacement = DIRECT_RENAMES.getOrDefault(owner, Map.of()).get(method.getName());
                if (replacement == null) return visited;
                JavaType.Method replacementType = method.withName(replacement);
                return visited.withName(visited.getName().withSimpleName(replacement).withType(replacementType))
                        .withMethodType(replacementType);
            }
        };
    }

    private static boolean isLegacyServletContextLog(String owner, JavaType.Method method, J.MethodInvocation invocation) {
        if (!"jakarta.servlet.ServletContext".equals(owner) || !"log".equals(method.getName()) ||
            invocation.getArguments().size() != 2 || method.getParameterTypes().size() != 2) return false;
        return "java.lang.Exception".equals(owner(method.getParameterTypes().get(0))) &&
               "java.lang.String".equals(owner(method.getParameterTypes().get(1)));
    }

    private static String normalizedOwner(JavaType type) {
        String owner = owner(type);
        if (owner.startsWith("javax.servlet.")) return "jakarta.servlet." + owner.substring("javax.servlet.".length());
        if (owner.startsWith("javax.el.")) return "jakarta.el." + owner.substring("javax.el.".length());
        return owner;
    }

    private static String owner(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }
}
