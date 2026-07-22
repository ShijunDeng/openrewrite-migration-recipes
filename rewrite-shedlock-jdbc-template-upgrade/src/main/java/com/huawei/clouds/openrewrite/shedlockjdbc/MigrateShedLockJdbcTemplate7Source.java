package com.huawei.clouds.openrewrite.shedlockjdbc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/** Deterministic source migrations documented by ShedLock 7.2.1. */
public final class MigrateShedLockJdbcTemplate7Source extends Recipe {
    private static final String BUILDER =
            "net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider$Configuration$Builder";

    @Override
    public String getDisplayName() {
        return "Migrate deterministic ShedLock JdbcTemplate 7 source constructs";
    }

    @Override
    public String getDescription() {
        return "Replace an exactly-UTC JdbcTemplateLockProvider builder withTimeZone call with forceUtcTimeZone. " +
               "Non-UTC and unresolved calls are intentionally left for review.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!"withTimeZone".equals(m.getSimpleName()) || !isBuilderMethod(m.getMethodType()) ||
                    m.getArguments().size() != 1 || !isUtcTimeZone(m.getArguments().get(0))) {
                    return m;
                }
                maybeRemoveImport("java.util.TimeZone");
                return m.withName(m.getName().withSimpleName("forceUtcTimeZone"))
                        .withArguments(List.of())
                        .withMethodType(null);
            }
        };
    }

    private static boolean isBuilderMethod(JavaType.Method method) {
        JavaType.FullyQualified owner = method == null ? null : TypeUtils.asFullyQualified(method.getDeclaringType());
        if (owner == null) {
            return false;
        }
        String fqn = owner.getFullyQualifiedName().replace('.', '$');
        return fqn.endsWith(BUILDER.replace('.', '$'));
    }

    private static boolean isUtcTimeZone(Expression expression) {
        if (!(expression instanceof J.MethodInvocation invocation) ||
            !"getTimeZone".equals(invocation.getSimpleName()) || invocation.getArguments().size() != 1) {
            return false;
        }
        JavaType.Method method = invocation.getMethodType();
        JavaType.FullyQualified owner = method == null ? null : TypeUtils.asFullyQualified(method.getDeclaringType());
        if (owner == null || !"java.util.TimeZone".equals(owner.getFullyQualifiedName())) {
            return false;
        }
        Expression argument = invocation.getArguments().get(0);
        return argument instanceof J.Literal literal && literal.getValue() instanceof String zone &&
               "UTC".equalsIgnoreCase(zone.trim());
    }
}
