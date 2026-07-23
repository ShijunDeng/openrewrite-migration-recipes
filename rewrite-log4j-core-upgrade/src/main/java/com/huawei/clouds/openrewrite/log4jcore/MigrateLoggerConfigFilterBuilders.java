package com.huawei.clouds.openrewrite.log4jcore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/** Move deprecated/misspelled LoggerConfig filter builder methods to the 2.25 setter. */
public final class MigrateLoggerConfigFilterBuilders extends Recipe {
    private static final Set<String> LEGACY = Set.of("withtFilter", "withFilter");

    @Override
    public String getDisplayName() {
        return "Migrate Log4j LoggerConfig filter builders";
    }

    @Override
    public String getDescription() {
        return "Rename the misspelled/deprecated LoggerConfig builder filter methods to setFilter while preserving the receiver and filter expression.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return Log4jCoreSupport.generated(cu.getSourcePath()) ? cu :
                        super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(
                    J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || !LEGACY.contains(visited.getSimpleName()) ||
                    !loggerConfigBuilder(method.getDeclaringType())) return visited;
                JavaType.Method replacement = method.withName("setFilter");
                return visited.withName(visited.getName().withSimpleName("setFilter").withType(replacement))
                        .withMethodType(replacement);
            }
        };
    }

    private static boolean loggerConfigBuilder(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq == null) return false;
        String name = fq.getFullyQualifiedName();
        return name.startsWith("org.apache.logging.log4j.core.config.LoggerConfig$") &&
               name.endsWith("Builder");
    }
}
