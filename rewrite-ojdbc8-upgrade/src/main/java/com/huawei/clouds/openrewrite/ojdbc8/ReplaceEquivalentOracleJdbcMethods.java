package com.huawei.clouds.openrewrite.ojdbc8;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Set;

/** Rename Oracle APIs whose target documentation guarantees identical behavior. */
public final class ReplaceEquivalentOracleJdbcMethods extends Recipe {
    private static final Set<String> LOB_INTERFACES = Set.of(
            "oracle.jdbc.OracleBlob", "oracle.jdbc.OracleClob", "oracle.jdbc.OracleBfile");
    private static final Set<String> SQL_LOBS = Set.of("oracle.sql.BLOB", "oracle.sql.CLOB", "oracle.sql.BFILE");

    @Override
    public String getDisplayName() {
        return "Replace equivalent deprecated Oracle JDBC methods";
    }

    @Override
    public String getDescription() {
        return "Rename deprecated LOB open/close/isOpen and one-argument statement-cache methods only where " +
               "Oracle documents an exact supported replacement.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedOjdbc8Dependency.isProjectPath(compilationUnit.getSourcePath())
                        ? super.visitCompilationUnit(compilationUnit, ctx) : compilationUnit;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String replacement = replacement(method.getDeclaringType().getFullyQualifiedName(), method.getName(),
                        method.getParameterTypes().size());
                if (replacement == null) return visited;
                JavaType.Method replacementType = method.withName(replacement);
                return visited.withName(visited.getName().withSimpleName(replacement).withType(replacementType))
                        .withMethodType(replacementType);
            }
        };
    }

    private static String replacement(String owner, String name, int arity) {
        if (LOB_INTERFACES.contains(owner)) {
            if ("close".equals(name) && arity == 0) return "closeLob";
            if ("isOpen".equals(name) && arity == 0) return "isOpenLob";
            if ("open".equals(name) && arity == 1) return "openLob";
        }
        if (SQL_LOBS.contains(owner) && "open".equals(name) &&
            (arity == 1 || arity == 0 && "oracle.sql.BFILE".equals(owner))) return "openLob";
        if ("oracle.jdbc.OracleConnection".equals(owner)) {
            if ("getStmtCacheSize".equals(name) && arity == 0) return "getStatementCacheSize";
            if ("setStmtCacheSize".equals(name) && arity == 1) return "setStatementCacheSize";
        }
        return null;
    }
}
