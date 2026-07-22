package com.huawei.clouds.openrewrite.okhttp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/** Replaces the removed OkHttpClient Cloneable API with its immutable builder equivalent. */
public final class MigrateOkHttpClientClone extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replace OkHttpClient clone calls";
    }

    @Override
    public String getDescription() {
        return "Replace type-attributed no-argument OkHttpClient.clone() calls with newBuilder().build(), " +
               "without changing clone methods on Retrofit calls or user types.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                if (!"clone".equals(visited.getSimpleName()) || !hasNoArguments(visited) ||
                    visited.getSelect() == null || visited.getMethodType() == null ||
                    !TypeUtils.isOfClassType(visited.getMethodType().getDeclaringType(), "okhttp3.OkHttpClient")) {
                    return visited;
                }
                JavaType.FullyQualified clientType = visited.getMethodType().getDeclaringType();
                JavaType.FullyQualified builderType = JavaType.ShallowClass.build("okhttp3.OkHttpClient$Builder");
                JavaType.Method newBuilderType = methodType(clientType, "newBuilder", builderType);
                JavaType.Method buildType = methodType(builderType, "build", clientType);

                J.MethodInvocation newBuilder = visited
                        .withPrefix(Space.EMPTY)
                        .withName(visited.getName().withSimpleName("newBuilder").withType(newBuilderType))
                        .withMethodType(newBuilderType);
                return visited
                        .withId(Tree.randomId())
                        .withSelect(newBuilder)
                        .withName(visited.getName().withId(Tree.randomId()).withSimpleName("build").withType(buildType))
                        .withMethodType(buildType);
            }

            private boolean hasNoArguments(J.MethodInvocation method) {
                return method.getArguments().isEmpty() ||
                       (method.getArguments().size() == 1 && method.getArguments().get(0) instanceof J.Empty);
            }
        };
    }

    private static JavaType.Method methodType(JavaType.FullyQualified owner, String name, JavaType returnType) {
        return new JavaType.Method(
                null, 0L, owner, name, returnType,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
