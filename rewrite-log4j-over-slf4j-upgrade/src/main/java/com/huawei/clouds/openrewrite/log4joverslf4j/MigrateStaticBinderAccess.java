package com.huawei.clouds.openrewrite.log4joverslf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Map;

/** Replace exact legacy static-binder access chains with their public SLF4J API equivalents. */
public final class MigrateStaticBinderAccess extends Recipe {
    private static final Map<String, Replacement> REPLACEMENTS = Map.of(
            "org.slf4j.impl.StaticLoggerBinder#getLoggerFactory",
            new Replacement("org.slf4j.LoggerFactory", "LoggerFactory.getILoggerFactory()"),
            "org.slf4j.impl.StaticMDCBinder#getMDCA",
            new Replacement("org.slf4j.MDC", "MDC.getMDCAdapter()"),
            "org.slf4j.impl.StaticMarkerBinder#getMarkerFactory",
            new Replacement("org.slf4j.MarkerFactory", "MarkerFactory.getIMarkerFactory()")
    );

    @Override
    public String getDisplayName() {
        return "Migrate deterministic SLF4J static-binder access";
    }

    @Override
    public String getDescription() {
        return "Replace the three exact getSingleton().get* access chains on SLF4J 1.x static binders with " +
               "the corresponding public LoggerFactory, MDC, and MarkerFactory accessors used by SLF4J 2 providers.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedLog4jOverSlf4jDependency.excluded(compilationUnit.getSourcePath())
                        ? compilationUnit : (J.CompilationUnit) super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J visitedTree = super.visitMethodInvocation(invocation, ctx);
                if (!(visitedTree instanceof J.MethodInvocation visited)) return visitedTree;
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                Replacement replacement = REPLACEMENTS.get(owner + "#" + method.getName());
                if (replacement == null || !(visited.getSelect() instanceof J.MethodInvocation singleton) ||
                    !"getSingleton".equals(singleton.getSimpleName()) || !noArguments(visited) ||
                    !method.getParameterTypes().isEmpty() || !noArguments(singleton)) return visited;
                JavaType.Method singletonType = singleton.getMethodType();
                if (singletonType == null || !singletonType.getParameterTypes().isEmpty() ||
                    !TypeUtils.isOfClassType(singletonType.getDeclaringType(), owner)) return visited;
                maybeAddImport(replacement.owner());
                maybeRemoveImport(owner);
                return template(replacement).apply(updateCursor(visited), visited.getCoordinates().replace());
            }
        };
    }

    private static boolean noArguments(J.MethodInvocation invocation) {
        return invocation.getArguments().isEmpty() ||
               invocation.getArguments().size() == 1 && invocation.getArguments().get(0) instanceof J.Empty;
    }

    private static JavaTemplate template(Replacement replacement) {
        return JavaTemplate.builder(replacement.source())
                .imports(replacement.owner())
                .javaParser(JavaParser.fromJavaVersion().dependsOn(
                        "package org.slf4j; public interface ILoggerFactory {}",
                        "package org.slf4j; public interface IMarkerFactory {}",
                        "package org.slf4j.spi; public interface MDCAdapter {}",
                        "package org.slf4j; public final class LoggerFactory { " +
                        "public static ILoggerFactory getILoggerFactory() { return null; } }",
                        "package org.slf4j; import org.slf4j.spi.MDCAdapter; public final class MDC { " +
                        "public static MDCAdapter getMDCAdapter() { return null; } }",
                        "package org.slf4j; public final class MarkerFactory { " +
                        "public static IMarkerFactory getIMarkerFactory() { return null; } }"
                )).build();
    }

    private record Replacement(String owner, String source) {
    }
}
