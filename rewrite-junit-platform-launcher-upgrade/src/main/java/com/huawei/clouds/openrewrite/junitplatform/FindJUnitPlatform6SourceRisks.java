package com.huawei.clouds.openrewrite.junitplatform;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/** Locate removed Launcher behavior for which syntax does not provide an equivalent migration. */
public final class FindJUnitPlatform6SourceRisks extends Recipe {
    static final String TEST_PLAN_ADD =
            "JUnit 6 removed TestPlan.add(TestIdentifier); it already always failed in JUnit Platform 1.8.2. " +
            "Remove the call or migrate the IDE/tool integration to discovery/runtime registration after reviewing ownership";

    @Override
    public String getDisplayName() {
        return "Find JUnit Platform 6 Launcher source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark type-attributed uses of the removed TestPlan mutation API that have no safe mechanical replacement.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedJUnitPlatformLauncherDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || !"add".equals(method.getName()) || method.getParameterTypes().size() != 1 ||
                    !TypeUtils.isOfClassType(method.getDeclaringType(), "org.junit.platform.launcher.TestPlan") ||
                    !TypeUtils.isOfClassType(method.getParameterTypes().get(0),
                            "org.junit.platform.launcher.TestIdentifier")) return visited;
                return mark(visited, TEST_PLAN_ADD);
            }
        };
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
