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

/** Body-preserving migration not covered safely by the official JUnit 6 recipes. */
public final class MigrateJUnitJupiter6Java extends Recipe {
    private static final String DYNAMIC_CONTEXT =
            "org.junit.jupiter.api.extension.DynamicTestInvocationContext";
    private static final MethodMatcher OLD_DYNAMIC = new MethodMatcher(
            "org.junit.jupiter.api.extension.InvocationInterceptor interceptDynamicTest(..)", true);

    @Override
    public String getDisplayName() {
        return "Preserve two-argument dynamic-test interceptor bodies on JUnit 6";
    }

    @Override
    public String getDescription() {
        return "Insert DynamicTestInvocationContext into the maintained interceptor signature without deleting " +
               "the existing implementation body; generic official recipes handle the other deterministic APIs.";
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
