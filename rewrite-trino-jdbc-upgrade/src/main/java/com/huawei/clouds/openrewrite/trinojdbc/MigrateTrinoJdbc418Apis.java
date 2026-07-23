package com.huawei.clouds.openrewrite.trinojdbc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.regex.Pattern;

/** Deterministic public API and connection-property renames between Trino JDBC 418 and 453. */
public final class MigrateTrinoJdbc418Apis extends Recipe {
    private static final Pattern LEGACY_PREPARE = Pattern.compile("(^|&)legacyPreparedStatements=");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Trino JDBC 418 APIs";
    }

    @Override
    public String getDescription() {
        return "Renames TrinoConnection.isUseLegacyPreparedStatements() to useExplicitPrepare() and the " +
               "equivalent JDBC URL property legacyPreparedStatements to explicitPrepare.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return TrinoJdbcSupport.generated(compilationUnit.getSourcePath()) ?
                        compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method type = visited.getMethodType();
                boolean noArguments = visited.getArguments().isEmpty() || visited.getArguments().stream().allMatch(J.Empty.class::isInstance);
                boolean trinoReceiver = (type != null && TypeUtils.isAssignableTo("io.trino.jdbc.TrinoConnection", type.getDeclaringType())) ||
                        (visited.getSelect() != null && TypeUtils.isAssignableTo("io.trino.jdbc.TrinoConnection", visited.getSelect().getType()));
                if (!"isUseLegacyPreparedStatements".equals(visited.getSimpleName()) || !noArguments || !trinoReceiver) {
                    return visited;
                }
                JavaType.Method replacement = type == null ? null :
                        type.withName("useExplicitPrepare").withReturnType(JavaType.Primitive.Boolean);
                return visited.withName(visited.getName().withSimpleName("useExplicitPrepare").withType(replacement))
                        .withMethodType(replacement);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value) || !value.startsWith("jdbc:trino:")) return visited;
                String migrated = migrateUrl(value);
                return value.equals(migrated) ? visited : TrinoJdbcSupport.replaceLiteral(visited, migrated);
            }
        };
    }

    static String migrateUrl(String value) {
        int question = value.indexOf('?');
        if (question < 0) return value;
        int fragment = value.indexOf('#', question + 1);
        int end = fragment < 0 ? value.length() : fragment;
        String query = value.substring(question + 1, end);
        String migrated = LEGACY_PREPARE.matcher(query).replaceAll("$1explicitPrepare=");
        return query.equals(migrated) ? value : value.substring(0, question + 1) + migrated + value.substring(end);
    }
}
