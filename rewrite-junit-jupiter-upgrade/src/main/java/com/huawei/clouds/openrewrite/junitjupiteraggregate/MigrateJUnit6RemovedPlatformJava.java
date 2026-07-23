package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

/** Deterministic replacements for Platform APIs removed in JUnit 6. */
public final class MigrateJUnit6RemovedPlatformJava extends Recipe {
    private static final String CONFIGURATION = "org.junit.platform.engine.ConfigurationParameters";
    private static final String REQUEST_BUILDER =
            "org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder";
    private static final String REPORT_ENTRY = "org.junit.platform.engine.reporting.ReportEntry";
    private static final String REFLECTION_SUPPORT = "org.junit.platform.commons.support.ReflectionSupport";
    private static final String TEST_PLAN = "org.junit.platform.launcher.TestPlan";
    private static final String UNIQUE_ID = "org.junit.platform.engine.UniqueId";
    private static final MethodMatcher CONFIGURATION_SIZE = new MethodMatcher(CONFIGURATION + " size()");
    private static final MethodMatcher REQUEST_CONSTRUCTOR =
            new MethodMatcher(REQUEST_BUILDER + " <constructor>()");
    private static final MethodMatcher REPORT_CONSTRUCTOR =
            new MethodMatcher(REPORT_ENTRY + " <constructor>()");
    private static final MethodMatcher LOAD_CLASS =
            new MethodMatcher(REFLECTION_SUPPORT + " loadClass(java.lang.String)");
    private static final MethodMatcher GET_CHILDREN_STRING =
            new MethodMatcher(TEST_PLAN + " getChildren(java.lang.String)");
    private static final MethodMatcher GET_IDENTIFIER_STRING =
            new MethodMatcher(TEST_PLAN + " getTestIdentifier(java.lang.String)");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic JUnit 6 Platform API removals";
    }

    @Override
    public String getDescription() {
        return "Replace removed ConfigurationParameters, selector, launcher builder, and ReportEntry APIs with " +
               "their documented maintained equivalents while preserving values and chaining.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private final JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion().dependsOn(
                    "package org.junit.platform.engine; public interface ConfigurationParameters { " +
                    "java.util.Set<String> keySet(); }",
                    "package org.junit.platform.engine.discovery; public class MethodSelector { " +
                    "public String getParameterTypeNames(){return null;} }",
                    "package org.junit.platform.engine.discovery; public class NestedMethodSelector { " +
                    "public String getParameterTypeNames(){return null;} }",
                    "package org.junit.platform.launcher.core; public class LauncherDiscoveryRequestBuilder { " +
                    "public static LauncherDiscoveryRequestBuilder request(){return null;} }",
                    "package org.junit.platform.engine.reporting; public class ReportEntry { " +
                    "public static ReportEntry from(java.util.Map<String,String> values){return null;} }",
                    "package org.junit.platform.commons.function; public class Try<T> { " +
                    "public java.util.Optional<T> toOptional(){return java.util.Optional.empty();} }",
                    "package org.junit.platform.commons.support; public class ReflectionSupport { " +
                    "public static org.junit.platform.commons.function.Try<Class<?>> tryToLoadClass(String name){return null;} }",
                    "package org.junit.platform.engine; public class UniqueId { public static UniqueId parse(String id){return null;} }",
                    "package org.junit.platform.launcher; public class TestPlan { " +
                    "public java.util.Set<Object> getChildren(org.junit.platform.engine.UniqueId id){return null;} " +
                    "public Object getTestIdentifier(org.junit.platform.engine.UniqueId id){return null;} }",
                    "package org.junit.platform.commons.util; public class UnrecoverableExceptions { " +
                    "public static void rethrowIfUnrecoverable(Throwable throwable){} }"
            );
            private final JavaTemplate configurationSize = JavaTemplate.builder("#{any(" + CONFIGURATION + ")}.keySet().size()")
                    .javaParser(parser).build();
            private final JavaTemplate request = JavaTemplate.builder("LauncherDiscoveryRequestBuilder.request()")
                    .imports(REQUEST_BUILDER).javaParser(parser).build();
            private final JavaTemplate report = JavaTemplate.builder("ReportEntry.from(Map.of())")
                    .imports(REPORT_ENTRY, "java.util.Map").javaParser(parser).build();
            private final JavaTemplate loadClass = JavaTemplate.builder(
                            "ReflectionSupport.tryToLoadClass(#{any(java.lang.String)}).toOptional()")
                    .imports(REFLECTION_SUPPORT).javaParser(parser).build();
            private final JavaTemplate children = JavaTemplate.builder(
                            "#{any(" + TEST_PLAN + ")}.getChildren(UniqueId.parse(#{any(java.lang.String)}))")
                    .imports(UNIQUE_ID).javaParser(parser).build();
            private final JavaTemplate identifier = JavaTemplate.builder(
                            "#{any(" + TEST_PLAN + ")}.getTestIdentifier(UniqueId.parse(#{any(java.lang.String)}))")
                    .imports(UNIQUE_ID).javaParser(parser).build();

            @Override
            public J visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedJUnitJupiterDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = (J.MethodInvocation) super.visitMethodInvocation(invocation, ctx);
                if (CONFIGURATION_SIZE.matches(visited) && visited.getSelect() != null) {
                    return configurationSize.apply(updateCursor(visited), visited.getCoordinates().replace(),
                            visited.getSelect());
                }
                if (LOAD_CLASS.matches(visited) && visited.getArguments().size() == 1) {
                    maybeAddImport(REFLECTION_SUPPORT);
                    return loadClass.apply(updateCursor(visited), visited.getCoordinates().replace(),
                            visited.getArguments().get(0));
                }
                if (GET_CHILDREN_STRING.matches(visited) && visited.getSelect() != null &&
                    visited.getArguments().size() == 1) {
                    maybeAddImport(UNIQUE_ID);
                    return children.apply(updateCursor(visited), visited.getCoordinates().replace(),
                            visited.getSelect(), visited.getArguments().get(0));
                }
                if (GET_IDENTIFIER_STRING.matches(visited) && visited.getSelect() != null &&
                    visited.getArguments().size() == 1) {
                    maybeAddImport(UNIQUE_ID);
                    return identifier.apply(updateCursor(visited), visited.getCoordinates().replace(),
                            visited.getSelect(), visited.getArguments().get(0));
                }
                return visited;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = (J.NewClass) super.visitNewClass(newClass, ctx);
                if (REQUEST_CONSTRUCTOR.matches(visited)) {
                    maybeAddImport(REQUEST_BUILDER);
                    return request.apply(updateCursor(visited), visited.getCoordinates().replace());
                }
                if (REPORT_CONSTRUCTOR.matches(visited)) {
                    maybeAddImport(REPORT_ENTRY);
                    maybeAddImport("java.util.Map");
                    return report.apply(updateCursor(visited), visited.getCoordinates().replace());
                }
                return visited;
            }
        };
    }
}
