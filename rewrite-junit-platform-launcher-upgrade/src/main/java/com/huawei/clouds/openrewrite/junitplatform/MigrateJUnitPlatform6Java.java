package com.huawei.clouds.openrewrite.junitplatform;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/** Deterministic replacements for public Launcher APIs removed in JUnit 6. */
public final class MigrateJUnitPlatform6Java extends Recipe {
    private static final String BUILDER =
            "org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder";
    private static final String TEST_PLAN = "org.junit.platform.launcher.TestPlan";
    private static final String REPORT_ENTRY = "org.junit.platform.engine.reporting.ReportEntry";

    @Override
    public String getDisplayName() {
        return "Migrate deterministic JUnit Platform 6 Launcher APIs";
    }

    @Override
    public String getDescription() {
        return "Replace removed LauncherDiscoveryRequestBuilder and ReportEntry constructors and adapt " +
               "TestPlan.getChildren(String) to the maintained UniqueId overload.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private final JavaParser.Builder<?, ?> parser = JavaParser.fromJavaVersion().dependsOn(
                    "package org.junit.platform.launcher.core; " +
                    "public final class LauncherDiscoveryRequestBuilder { " +
                    "public LauncherDiscoveryRequestBuilder() {} " +
                    "public static LauncherDiscoveryRequestBuilder request() { return null; } }",
                    "package org.junit.platform.engine; public final class UniqueId { " +
                    "public static UniqueId parse(String value) { return null; } }",
                    "package org.junit.platform.launcher; import java.util.Set; " +
                    "import org.junit.platform.engine.UniqueId; public class TestPlan { " +
                    "public Set<Object> getChildren(String id) { return null; } " +
                    "public Set<Object> getChildren(UniqueId id) { return null; } }",
                    "package org.junit.platform.engine.reporting; import java.util.Map; " +
                    "public final class ReportEntry { public ReportEntry() {} " +
                    "public static ReportEntry from(Map<String,String> values) { return null; } }"
            );
            private final JavaTemplate builderFactory = JavaTemplate.builder(
                    "LauncherDiscoveryRequestBuilder.request()")
                    .imports(BUILDER).javaParser(parser).build();
            private final JavaTemplate uniqueIdChildren = JavaTemplate.builder(
                    "#{any(org.junit.platform.launcher.TestPlan)}.getChildren(" +
                    "UniqueId.parse(#{any(java.lang.String)}))")
                    .imports("org.junit.platform.engine.UniqueId").javaParser(parser).build();
            private final JavaTemplate emptyReportEntry = JavaTemplate.builder(
                    "ReportEntry.from(java.util.Map.of())")
                    .imports(REPORT_ENTRY).javaParser(parser).build();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedJUnitPlatformLauncherDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : (J.CompilationUnit) super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J visited = super.visitNewClass(newClass, ctx);
                if (!(visited instanceof J.NewClass candidate) || !noArguments(candidate)) return visited;
                if (TypeUtils.isOfClassType(candidate.getType(), BUILDER)) {
                    maybeAddImport(BUILDER);
                    return builderFactory.apply(updateCursor(candidate), candidate.getCoordinates().replace());
                }
                if (TypeUtils.isOfClassType(candidate.getType(), REPORT_ENTRY)) {
                    maybeAddImport(REPORT_ENTRY);
                    return emptyReportEntry.apply(updateCursor(candidate), candidate.getCoordinates().replace());
                }
                return visited;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J visited = super.visitMethodInvocation(invocation, ctx);
                if (!(visited instanceof J.MethodInvocation candidate) || candidate.getMethodType() == null ||
                    candidate.getSelect() == null || candidate.getArguments().size() != 1) return visited;
                JavaType.Method method = candidate.getMethodType();
                if (!"getChildren".equals(method.getName()) ||
                    !TypeUtils.isOfClassType(method.getDeclaringType(), TEST_PLAN) ||
                    method.getParameterTypes().size() != 1 ||
                    !TypeUtils.isOfClassType(method.getParameterTypes().get(0), "java.lang.String")) return visited;
                maybeAddImport("org.junit.platform.engine.UniqueId");
                return uniqueIdChildren.apply(updateCursor(candidate), candidate.getCoordinates().replace(),
                        candidate.getSelect(), candidate.getArguments().get(0));
            }

            private boolean noArguments(J.NewClass candidate) {
                return candidate.getArguments().isEmpty() ||
                       candidate.getArguments().stream().allMatch(J.Empty.class::isInstance);
            }
        };
    }
}
