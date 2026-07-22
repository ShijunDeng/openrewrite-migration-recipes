package com.huawei.clouds.openrewrite.jultoslf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * Replaces the public accessor patterns that were commonly (but incorrectly)
 * reached through SLF4J 1.7 provider-internal Static*Binder classes.
 */
public final class MigrateStaticBinderAccess extends Recipe {
    private static final String STATIC_LOGGER_BINDER = "org.slf4j.impl.StaticLoggerBinder";
    private static final String STATIC_MDC_BINDER = "org.slf4j.impl.StaticMDCBinder";
    private static final String STATIC_MARKER_BINDER = "org.slf4j.impl.StaticMarkerBinder";

    @Override
    public String getDisplayName() {
        return "Migrate SLF4J StaticBinder accessors";
    }

    @Override
    public String getDescription() {
        return "Replace type-attributed StaticLoggerBinder, StaticMDCBinder, and StaticMarkerBinder accessor calls " +
               "with their public SLF4J 2.0 API equivalents; provider implementations and unsupported binder " +
               "members are intentionally left for review.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                boolean noArguments = m.getArguments().isEmpty() ||
                                      m.getArguments().size() == 1 && m.getArguments().get(0) instanceof J.Empty;
                if (methodType == null || !noArguments) {
                    return m;
                }

                JavaType.FullyQualified declaringType = TypeUtils.asFullyQualified(methodType.getDeclaringType());
                if (declaringType == null) {
                    return m;
                }

                String owner = declaringType.getFullyQualifiedName();
                String name = methodType.getName();
                if (STATIC_LOGGER_BINDER.equals(owner) && "getLoggerFactory".equals(name)) {
                    return replace(m, "LoggerFactory.getILoggerFactory()", "org.slf4j.LoggerFactory", owner, ctx);
                }
                if (STATIC_LOGGER_BINDER.equals(owner) && "getLoggerFactoryClassStr".equals(name)) {
                    return replace(m, "LoggerFactory.getILoggerFactory().getClass().getName()",
                            "org.slf4j.LoggerFactory", owner, ctx);
                }
                if (STATIC_MDC_BINDER.equals(owner) && "getMDCA".equals(name)) {
                    return replace(m, "MDC.getMDCAdapter()", "org.slf4j.MDC", owner, ctx);
                }
                if (STATIC_MARKER_BINDER.equals(owner) && "getMarkerFactory".equals(name)) {
                    return replace(m, "MarkerFactory.getIMarkerFactory()", "org.slf4j.MarkerFactory", owner, ctx);
                }
                return m;
            }

            private J.MethodInvocation replace(J.MethodInvocation original, String source,
                                                 String targetType, String oldType, ExecutionContext ctx) {
                maybeAddImport(targetType);
                maybeRemoveImport(oldType);
                J.MethodInvocation replacement = template(source, targetType)
                        .apply(updateCursor(original), original.getCoordinates().replace());
                return maybeAutoFormat(original, replacement, ctx);
            }
        };
    }

    private static JavaTemplate template(String source, String targetType) {
        return JavaTemplate.builder(source)
                .imports(targetType)
                .javaParser(JavaParser.fromJavaVersion().classpath(JavaParser.runtimeClasspath()))
                .build();
    }
}
