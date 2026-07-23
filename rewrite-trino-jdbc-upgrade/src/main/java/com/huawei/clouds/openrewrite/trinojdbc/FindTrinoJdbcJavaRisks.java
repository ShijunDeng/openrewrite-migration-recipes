package com.huawei.clouds.openrewrite.trinojdbc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks application-specific JDBC behavior decisions that cannot be changed safely without domain knowledge. */
public final class FindTrinoJdbcJavaRisks extends Recipe {
    private static final Set<String> PREPARED = Set.of(
            "prepareStatement", "addBatch", "executeBatch", "executeLargeBatch", "getMetaData", "getParameterMetaData");
    private static final Set<String> METADATA = Set.of(
            "getSchemas", "getTables", "getColumns", "getPrimaryKeys", "getIndexInfo", "getImportedKeys", "getExportedKeys");
    private static final Set<String> RESULT_CONVERSIONS = Set.of(
            "getObject", "getDate", "getTime", "getTimestamp", "getArray", "getBigDecimal");
    private static final Set<String> SESSION_CONTEXT = Set.of(
            "setCatalog", "setSchema", "setTimeZoneId", "setLocale", "setSessionProperty", "setClientInfo");
    private static final Set<String> LIFECYCLE = Set.of(
            "cancel", "partialCancel", "abort", "close", "setNetworkTimeout", "setQueryTimeout");
    private static final Set<String> QUERY_STATS = Set.of(
            "setProgressMonitor", "getQueryId", "getState", "getProgressPercentage", "getCpuTimeMillis",
            "getWallTimeMillis", "getProcessedRows", "getProcessedBytes", "getPeakMemoryBytes", "getRootStage");
    private static final String TRINO_SOURCE = "com.huawei.clouds.openrewrite.trinojdbc.source";

    @Override
    public String getDisplayName() {
        return "Find Trino JDBC 453 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed URI construction, URL/security, prepared statement, metadata null-catalog, temporal " +
               "conversion, session context, cancellation, statistics and internal/subclass compatibility decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (TrinoJdbcSupport.generated(cu.getSourcePath())) return cu;
                boolean relevant = cu.getImports().stream().anyMatch(i -> i.getQualid().printTrimmed().startsWith("io.trino.jdbc")) ||
                                   cu.printAll().contains("jdbc:trino:");
                getCursor().putMessage(TRINO_SOURCE, relevant);
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import import_, ExecutionContext ctx) {
                J.Import visited = super.visitImport(import_, ctx);
                return visited.getQualid().printTrimmed().startsWith("io.trino.jdbc.$internal") ?
                        SearchResult.found(visited, "The shaded io.trino.jdbc.$internal namespace is not a public API; remove this coupling because relocation/content can change on every driver release") : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (visited.getValue() instanceof String value && value.startsWith("jdbc:trino:")) {
                    return SearchResult.found(visited, "Review Trino JDBC URL policy: authentication/TLS validation changed and 453 adds hostnameInCertificate, timezone, explicitPrepare, assumeNullCatalogMeansCurrent, session authorization, SQL PATH and OS-keystore support");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = visited.getMethodType();
                String name = visited.getSimpleName();
                String owner = owner(type == null ? null : type.getDeclaringType());

                if ("create".equals(name) && ("io.trino.jdbc.TrinoDriverUri".equals(owner) ||
                    (visited.getSelect() != null && "io.trino.jdbc.TrinoDriverUri".equals(owner(visited.getSelect().getType()))))) {
                    return SearchResult.found(visited, "TrinoDriverUri.create(String, Properties) is no longer public in 453; use the public Driver/DriverManager connection path and move URL validation out of this internal construction dependency");
                }
                if (!Boolean.TRUE.equals(getCursor().getNearestMessage(TRINO_SOURCE))) return visited;
                if (PREPARED.contains(name) && (jdbcOwner(owner, "Connection") || jdbcOwner(owner, "PreparedStatement"))) {
                    return SearchResult.found(visited, "Prepared statement transport changed: 453 supports EXECUTE IMMEDIATE when explicitPrepare=false; test parameter metadata, repeated execution, batching, complex types, errors and latency under the chosen mode");
                }
                if (METADATA.contains(name) && jdbcOwner(owner, "DatabaseMetaData")) {
                    return SearchResult.found(visited, "DatabaseMetaData null-catalog semantics are configurable with assumeNullCatalogMeansCurrent; assert schema/table discovery with null, empty, current and explicit catalogs");
                }
                if (RESULT_CONVERSIONS.contains(name) && jdbcOwner(owner, "ResultSet")) {
                    return SearchResult.found(visited, "Result conversion boundary detected; verify LocalDateTime/timezone, decimal, array/row and typed getObject behavior against 453 with null and boundary values");
                }
                if (SESSION_CONTEXT.contains(name) && jdbcOwner(owner, "Connection")) {
                    return SearchResult.found(visited, "Session context affects pooled-connection reuse; verify catalog/schema/timezone/locale/client/session properties and decide whether 453 session-user and SQL PATH support must be set/reset");
                }
                if (LIFECYCLE.contains(name) && (jdbcOwner(owner, "Connection") || jdbcOwner(owner, "Statement"))) {
                    return SearchResult.found(visited, "Cancellation/lifecycle boundary detected; test close, abort, network/query timeout, partial results and race behavior with the 453 HTTP client and OpenTelemetry instrumentation");
                }
                if (QUERY_STATS.contains(name) && (owner.startsWith("io.trino.jdbc.QueryStats") || owner.startsWith("io.trino.jdbc.TrinoStatement"))) {
                    return SearchResult.found(visited, "Trino query progress/statistics contract detected; verify callback threading, terminal states, physical/logical byte metrics, timing units and warning assertions on 453");
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                if (visited.getExtends() != null) {
                    String parent = owner(visited.getExtends().getType());
                    if (parent.startsWith("io.trino.jdbc.") && !"io.trino.jdbc.TrinoConnection".equals(parent)) {
                        return SearchResult.found(visited, "Custom subclass of a concrete Trino JDBC implementation detected; prefer java.sql/public extension points and audit constructor, lifecycle and shaded-internal assumptions on 453");
                    }
                }
                return visited;
            }
        };
    }

    private static boolean jdbcOwner(String owner, String type) {
        return owner.equals("java.sql." + type) || owner.equals("io.trino.jdbc.Trino" + type) ||
               ("Statement".equals(type) && owner.equals("io.trino.jdbc.TrinoPreparedStatement"));
    }

    private static String owner(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? "" : fq.getFullyQualifiedName();
    }
}
