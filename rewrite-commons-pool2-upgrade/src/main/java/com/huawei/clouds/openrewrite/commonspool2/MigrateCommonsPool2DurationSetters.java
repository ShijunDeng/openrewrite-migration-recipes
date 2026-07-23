package com.huawei.clouds.openrewrite.commonspool2;

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
import java.util.Map;

/** Replaces deprecated millisecond setters with their exact Duration equivalents. */
public final class MigrateCommonsPool2DurationSetters extends Recipe {
    private static final String BASE_CONFIG = "org.apache.commons.pool2.impl.BaseObjectPoolConfig";
    private static final String BASE_POOL = "org.apache.commons.pool2.impl.BaseGenericObjectPool";
    private static final Map<String, String> REPLACEMENTS = Map.of(
            "setMaxWaitMillis", "setMaxWait",
            "setEvictorShutdownTimeoutMillis", "setEvictorShutdownTimeout",
            "setMinEvictableIdleTimeMillis", "setMinEvictableIdleDuration",
            "setSoftMinEvictableIdleTimeMillis", "setSoftMinEvictableIdleDuration",
            "setTimeBetweenEvictionRunsMillis", "setTimeBetweenEvictionRuns"
    );

    @Override
    public String getDisplayName() {
        return "Migrate Commons Pool 2 millisecond setters to Duration";
    }

    @Override
    public String getDescription() {
        return "Converts the five deprecated long-millisecond pool/configuration setters to Duration.ofMillis without changing units or values.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return CommonsPool2Support.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = visited.getMethodType();
                List<Expression> arguments = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                String replacement = REPLACEMENTS.get(visited.getSimpleName());
                if (replacement == null || type == null || visited.getSelect() == null || arguments.size() != 1 ||
                    !poolOwner(type) || !longParameter(type)) return visited;

                if ("setTimeBetweenEvictionRunsMillis".equals(visited.getSimpleName()) &&
                    TypeUtils.isAssignableTo(BASE_POOL, type.getDeclaringType())) {
                    replacement = "setDurationBetweenEvictionRuns";
                }
                maybeAddImport("java.time.Duration");
                J.MethodInvocation migrated = JavaTemplate.builder("#{any()}." + replacement + "(Duration.ofMillis(#{any()}))")
                        .imports("java.time.Duration")
                        .javaParser(targetParser())
                        .build()
                        .apply(updateCursor(visited), visited.getCoordinates().replace(), visited.getSelect(), arguments.get(0));
                JavaType.Method replacementType = type.withName(replacement)
                        .withParameterTypes(List.of(JavaType.ShallowClass.build("java.time.Duration")));
                return migrated.withName(migrated.getName().withType(replacementType))
                        .withMethodType(replacementType);
            }
        };
    }

    private static boolean poolOwner(JavaType.Method method) {
        return TypeUtils.isAssignableTo(BASE_CONFIG, method.getDeclaringType()) ||
               TypeUtils.isAssignableTo(BASE_POOL, method.getDeclaringType());
    }

    private static boolean longParameter(JavaType.Method method) {
        return method.getParameterTypes().size() == 1 && method.getParameterTypes().get(0) == JavaType.Primitive.Long;
    }

    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().dependsOn("""
                package org.apache.commons.pool2.impl;
                public abstract class BaseObjectPoolConfig<T> {
                    public void setMaxWait(java.time.Duration value) {}
                    public void setEvictorShutdownTimeout(java.time.Duration value) {}
                    public void setMinEvictableIdleDuration(java.time.Duration value) {}
                    public void setSoftMinEvictableIdleDuration(java.time.Duration value) {}
                    public void setTimeBetweenEvictionRuns(java.time.Duration value) {}
                }
                """, """
                package org.apache.commons.pool2.impl;
                public abstract class BaseGenericObjectPool<T> {
                    public void setMaxWait(java.time.Duration value) {}
                    public void setEvictorShutdownTimeout(java.time.Duration value) {}
                    public void setMinEvictableIdleDuration(java.time.Duration value) {}
                    public void setSoftMinEvictableIdleDuration(java.time.Duration value) {}
                    public void setDurationBetweenEvictionRuns(java.time.Duration value) {}
                }
                """);
    }
}
