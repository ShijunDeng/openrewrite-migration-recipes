package com.huawei.clouds.openrewrite.guava;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Makes the former implicit direct-executor behavior explicit before upgrading Guava. */
public final class AddGuavaDirectExecutor extends Recipe {
    private static final String FUTURES = "com.google.common.util.concurrent.Futures";
    private static final String SERVICE_MANAGER = "com.google.common.util.concurrent.ServiceManager";
    private static final String MORE_EXECUTORS = "com.google.common.util.concurrent.MoreExecutors";

    private static final Set<String> FUTURES_METHODS = Set.of(
            "addCallback", "catching", "catchingAsync", "transform", "transformAsync"
    );

    @Override
    public String getDisplayName() {
        return "Add explicit direct executors to removed Guava overloads";
    }

    @Override
    public String getDescription() {
        return "Add MoreExecutors.directExecutor() to the removed Futures overloads and to the removed " +
               "single-argument ServiceManager.addListener overload, preserving their documented legacy behavior.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (methodType == null || !needsDirectExecutor(methodType, m.getArguments().size())) {
                    return m;
                }

                List<Expression> arguments = m.getArguments();
                String replacement = IntStream.range(0, arguments.size())
                        .mapToObj(index -> "#{any()}")
                        .collect(Collectors.joining(", ")) + ", MoreExecutors.directExecutor()";

                maybeAddImport(MORE_EXECUTORS);
                return directExecutorTemplate(replacement)
                        .apply(updateCursor(m), m.getCoordinates().replaceArguments(), arguments.toArray());
            }
        };
    }

    private static JavaTemplate directExecutorTemplate(String replacement) {
        return JavaTemplate.builder(replacement)
                .imports(MORE_EXECUTORS)
                .javaParser(JavaParser.fromJavaVersion().dependsOn(
                        "package com.google.common.util.concurrent; " +
                        "public final class MoreExecutors { " +
                        "public static java.util.concurrent.Executor directExecutor() { return null; } }"
                ))
                .build();
    }

    private static boolean needsDirectExecutor(JavaType.Method method, int argumentCount) {
        JavaType.FullyQualified declaringType = TypeUtils.asFullyQualified(method.getDeclaringType());
        if (declaringType == null) {
            return false;
        }

        String owner = declaringType.getFullyQualifiedName();
        if (SERVICE_MANAGER.equals(owner)) {
            return "addListener".equals(method.getName()) && argumentCount == 1;
        }
        if (!FUTURES.equals(owner) || !FUTURES_METHODS.contains(method.getName())) {
            return false;
        }
        return switch (method.getName()) {
            case "addCallback", "transform", "transformAsync" -> argumentCount == 2;
            case "catching", "catchingAsync" -> argumentCount == 3;
            default -> false;
        };
    }
}
