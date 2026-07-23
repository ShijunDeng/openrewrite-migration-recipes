package com.huawei.clouds.openrewrite.appium;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/** Remove generic arguments from drivers made non-generic in Appium Java Client 8. */
public final class RemoveAppiumDriverTypeArguments extends Recipe {
    private static final Set<String> DRIVERS = Set.of(
            "io.appium.java_client.AppiumDriver",
            "io.appium.java_client.android.AndroidDriver",
            "io.appium.java_client.ios.IOSDriver");

    @Override
    public String getDisplayName() {
        return "Remove obsolete Appium driver type arguments";
    }

    @Override
    public String getDescription() {
        return "Remove type arguments and constructor diamonds from the three Appium driver types that became " +
               "non-generic while preserving their enclosing declaration or construction.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedAppiumDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J visitParameterizedType(J.ParameterizedType parameterized, ExecutionContext ctx) {
                J visitedTree = super.visitParameterizedType(parameterized, ctx);
                if (!(visitedTree instanceof J.ParameterizedType visited) ||
                    DRIVERS.stream().noneMatch(driver -> TypeUtils.isOfClassType(visited.getClazz().getType(), driver))) {
                    return visitedTree;
                }
                J raw = (J) visited.getClazz();
                return raw.withPrefix(visited.getPrefix());
            }
        };
    }
}
