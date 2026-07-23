package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/** Deterministic source migrations for JUnit Jupiter 6 APIs. */
public final class MigrateJUnitJupiter6Java extends Recipe {
    private static final String STORE = "org.junit.jupiter.api.extension.ExtensionContext$Store";
    private static final String METHOD_ORDERER = "org.junit.jupiter.api.MethodOrderer";
    private static final String ALPHANUMERIC = METHOD_ORDERER + ".Alphanumeric";
    private static final String METHOD_NAME = METHOD_ORDERER + ".MethodName";
    private static final String DYNAMIC_CONTEXT =
            "org.junit.jupiter.api.extension.DynamicTestInvocationContext";
    private static final MethodMatcher OLD_DYNAMIC = new MethodMatcher(
            "org.junit.jupiter.api.extension.InvocationInterceptor interceptDynamicTest(..)", true);

    @Override
    public String getDisplayName() {
        return "Migrate deterministic JUnit Jupiter 6 APIs";
    }

    @Override
    public String getDescription() {
        return "Rename ExtensionContext.Store compute APIs, replace the removed MethodOrderer.Alphanumeric type, " +
               "and preserve deprecated two-argument dynamic-test interceptors using the maintained signature.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion().dependsOn(
                    "package org.junit.jupiter.api.extension; public interface ExtensionContext {}",
                    "package org.junit.jupiter.api.extension; public interface DynamicTestInvocationContext {}",
                    "package org.junit.jupiter.api.extension; public interface InvocationInterceptor { " +
                    "interface Invocation<T> { T proceed() throws Throwable; } " +
                    "default void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext context) throws Throwable {} " +
                    "default void interceptDynamicTest(Invocation<Void> invocation, " +
                    "DynamicTestInvocationContext invocationContext, ExtensionContext context) throws Throwable {} }"
            );
            private final JavaTemplate dynamicParameters = JavaTemplate.builder(
                    "#{}, DynamicTestInvocationContext invocationContext, #{}")
                    .contextSensitive().imports(DYNAMIC_CONTEXT).javaParser(parser).build();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedJUnitJupiterDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || !"getOrComputeIfAbsent".equals(method.getName()) ||
                    !TypeUtils.isOfClassType(method.getDeclaringType(), STORE)) return visited;
                JavaType.Method renamed = method.withName("computeIfAbsent");
                return visited.withName(visited.getName().withSimpleName("computeIfAbsent").withType(renamed))
                        .withMethodType(renamed);
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                if (!"Alphanumeric".equals(visited.getSimpleName()) ||
                    !TypeUtils.isOfClassType(visited.getTarget().getType(), METHOD_ORDERER)) return visited;
                maybeRemoveImport(ALPHANUMERIC);
                JavaType targetType = JavaType.ShallowClass.build(METHOD_NAME);
                return visited.withName(visited.getName().withSimpleName("MethodName").withType(targetType))
                        .withType(targetType);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (!"Alphanumeric".equals(visited.getSimpleName()) ||
                    isFieldAccessName(visited) ||
                    !TypeUtils.isOfClassType(visited.getType(), ALPHANUMERIC)) return visited;
                maybeRemoveImport(ALPHANUMERIC);
                maybeAddImport(METHOD_NAME);
                return visited.withSimpleName("MethodName").withType(JavaType.ShallowClass.build(METHOD_NAME));
            }

            private boolean isFieldAccessName(J.Identifier identifier) {
                return getCursor().getParentTreeCursor().getValue() instanceof J.FieldAccess fieldAccess &&
                       fieldAccess.getName().getId().equals(identifier.getId());
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                JavaType.Method type = visited.getMethodType();
                if (type == null || !OLD_DYNAMIC.matches(type) || visited.getParameters().size() != 2 ||
                    type.getParameterTypes().size() != 2 ||
                    !TypeUtils.isOfClassType(type.getParameterTypes().get(1),
                            "org.junit.jupiter.api.extension.ExtensionContext")) return visited;
                maybeAddImport(DYNAMIC_CONTEXT);
                J.MethodDeclaration migrated = dynamicParameters.apply(updateCursor(visited),
                        visited.getCoordinates().replaceParameters(),
                        visited.getParameters().get(0), visited.getParameters().get(1));
                JavaType.Method migratedType = type.withParameterTypes(java.util.List.of(
                        type.getParameterTypes().get(0), JavaType.ShallowClass.build(DYNAMIC_CONTEXT),
                        type.getParameterTypes().get(1)));
                return migrated.withName(migrated.getName().withType(migratedType)).withMethodType(migratedType);
            }
        };
    }
}
