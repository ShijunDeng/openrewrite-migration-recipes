package com.huawei.clouds.openrewrite.ojdbc8;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;
import java.util.regex.Pattern;

/** Mark exact Oracle JDBC source boundaries that require application decisions. */
public final class FindOjdbcJavaMigrationRisks extends Recipe {
    private static final Set<String> IMPLICIT_CACHE_METHODS = Set.of(
            "applyConnectionAttributes", "close", "getConnectionAttributes", "getConnectionReleasePriority",
            "getUnMatchedConnectionAttributes", "registerConnectionCacheCallback", "setConnectionReleasePriority");
    private static final Set<String> ORACLE_BATCH_METHODS = Set.of(
            "getExecuteBatch", "sendBatch", "setExecuteBatch", "getDefaultExecuteBatch", "setDefaultExecuteBatch");
    private static final Pattern SID_URL = Pattern.compile("jdbc:oracle:thin:(?:[^@]*@)?[^/(;?]+:[0-9]+:[^/;?]+.*");

    @Override
    public String getDisplayName() {
        return "Find Oracle JDBC 23.26.1 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark attributed removed/deprecated Oracle extensions, rowsets, implicit caches, UCP/wallet usage, " +
               "explicit driver loading, old module names, and JDBC URL forms needing environment validation.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private boolean moduleInfo;

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (!UpgradeSelectedOjdbc8Dependency.isProjectPath(compilationUnit.getSourcePath())) return compilationUnit;
                moduleInfo = compilationUnit.getSourcePath().getFileName() != null &&
                             "module-info.java".equals(compilationUnit.getSourcePath().getFileName().toString());
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (moduleInfo && "ojdbc8".equals(visited.getSimpleName())) {
                    return mark(visited,
                            "ojdbc8 19.x had the derived JPMS module name ojdbc8, but 21.x and 23.26.1 declare Automatic-Module-Name com.oracle.database.jdbc; update requires and verify no duplicate ojdbc8/ojdbc11 modules or split packages");
                }
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                if (type == null || getCursor().firstEnclosing(J.Import.class) != null ||
                    !visited.getSimpleName().equals(type.getClassName())) return visited;
                String message = typeMessage(type.getFullyQualifiedName());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                String name = method.getName();
                if ((("java.lang.Class".equals(owner) && "forName".equals(name)) ||
                     ("java.lang.ClassLoader".equals(owner) && "loadClass".equals(name))) &&
                    !visited.getArguments().isEmpty() && visited.getArguments().get(0) instanceof J.Literal literal &&
                    (ReplaceLegacyOracleDriverString.OLD_DRIVER.equals(literal.getValue()) ||
                     ReplaceLegacyOracleDriverString.NEW_DRIVER.equals(literal.getValue()))) {
                    return mark(visited,
                            "Explicit Oracle JDBC driver class loading was found; JDBC 4 service loading should discover oracle.jdbc.OracleDriver, so verify META-INF/services survives shading, the context classloader/module layer can see the driver, and deregistration is handled on redeploy");
                }
                if ("java.sql.DriverManager".equals(owner) && "registerDriver".equals(name) &&
                    !visited.getArguments().isEmpty() &&
                    TypeUtils.isOfClassType(visited.getArguments().get(0).getType(), "oracle.jdbc.OracleDriver")) {
                    return mark(visited,
                            "Explicit Oracle DriverManager registration was found; verify it is required beyond JDBC service loading and prevent duplicate registration/classloader leaks during application redeploy");
                }
                String message = methodMessage(owner, name, method.getParameterTypes().size());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value) || !value.startsWith("jdbc:oracle:")) return visited;
                String message = urlMessage(value);
                return message == null ? visited : mark(visited, message);
            }
        };
    }

    private static String typeMessage(String fqn) {
        if (fqn.startsWith("oracle.jdbc.rowset.")) {
            return "The oracle.jdbc.rowset implementation package present in ojdbc8 19.x is absent from 23.26.1; migrate to javax.sql.rowset/RowSetProvider or another maintained rowset implementation and verify serialization, disconnected updates, and provider selection";
        }
        if (fqn.equals("oracle.jdbc.pool.OracleConnectionCacheManager") ||
            fqn.contains("OracleImplicitConnectionCache") || fqn.contains("OracleConnectionCacheEntry")) {
            return "Oracle Implicit Connection Cache was desupported and its manager/implementation is absent from 23.26.1; migrate pool ownership, labeling, timeout, validation, failover, metrics, and shutdown to aligned Oracle UCP";
        }
        if (fqn.equals("oracle.jdbc.pool.OracleConnectionCacheCallback")) {
            return "OracleConnectionCacheCallback belongs to the desupported implicit connection cache; use aligned Oracle UCP and redesign callback/abandoned-connection semantics explicitly";
        }
        if (fqn.startsWith("oracle.jdbc.driver.")) {
            return "Application code depends on deprecated Oracle driver implementation internals; migrate the complete boundary to public oracle.jdbc/java.sql APIs and recompile because 23.26.1 removes or reshapes internal classes";
        }
        if (fqn.startsWith("oracle.jdbc.logging.")) {
            return "Oracle JDBC internal logging annotations/runtime changed between 19.x and 23.26.1; remove implementation coupling and use supported diagnostics, JUL configuration, or application telemetry";
        }
        if (fqn.startsWith("oracle.sql.")) {
            return "oracle.sql concrete types are retained mainly for backward compatibility and discouraged; prefer java.sql interfaces and Connection/OracleConnection factory methods, verifying precision, object type maps, LOB lifecycle, and serialization";
        }
        if (fqn.startsWith("oracle.ucp.")) {
            return "This application uses Oracle UCP; align ucp with ojdbc8 23.26.1 and verify pool sizing, validation, labeling, FAN/ONS, replay, planned draining, credentials, metrics, and shutdown ownership";
        }
        if (fqn.startsWith("oracle.security.pki.")) {
            return "This code uses Oracle Wallet/PKI APIs; align oraclepki with 23.26.1 and verify wallet format, provider registration, trust/key material, password handling, TLS hostname/DN checks, rotation, and native-image/module access";
        }
        return null;
    }

    private static String methodMessage(String owner, String name, int arity) {
        if (("oracle.jdbc.OraclePreparedStatement".equals(owner) || "oracle.jdbc.OracleConnection".equals(owner)) &&
            ORACLE_BATCH_METHODS.contains(name)) {
            return "Oracle-style batching is desupported; redesign with JDBC addBatch/executeBatch, preserving flush boundaries, update counts, partial failures, generated keys, transaction semantics, and memory limits";
        }
        if ("oracle.jdbc.OracleConnection".equals(owner) && IMPLICIT_CACHE_METHODS.contains(name)) {
            return "This method belongs to the desupported Oracle Implicit Connection Cache and may throw UnsupportedOperationException; migrate the lifecycle and attributes to aligned UCP instead of renaming the call";
        }
        if ("oracle.jdbc.OracleConnection".equals(owner) &&
            Set.of("setEndToEndMetrics", "getEndToEndMetrics", "setApplicationContext", "clearAllApplicationContext")
                    .contains(name)) {
            return "This Oracle end-to-end metrics/application-context API is deprecated in favor of JDBC client info; map keys and session cleanup deliberately and do not mix old and new APIs on pooled connections";
        }
        if ("oracle.jdbc.OracleConnection".equals(owner) && "setStmtCacheSize".equals(name) && arity == 2) {
            return "The two-argument setStmtCacheSize overload has no proven one-to-one replacement; choose statement-cache size and clear-metadata semantics explicitly before using setStatementCacheSize";
        }
        if ("oracle.sql.ArrayDescriptor".equals(owner) || "oracle.sql.StructDescriptor".equals(owner)) {
            return "Oracle descriptor construction is deprecated; migrate at the allocation boundary to OracleConnection.createOracleArray or Connection.createStruct and verify type name, element/attribute conversion, unwrap, and connection ownership";
        }
        return null;
    }

    private static String urlMessage(String value) {
        if (value.startsWith("jdbc:oracle:oci:")) {
            return "Oracle has announced deprecation of the JDBC OCI/Type 2 driver; plan a Thin-driver or supported-client transition and verify native libraries, wallets, FAN, authentication, failover, and performance";
        }
        if (value.contains("(DESCRIPTION=") || value.contains("(description=")) {
            return "This Oracle JDBC URL embeds a connect descriptor; validate ADDRESS_LIST/load-balance/failover, service/SID, timeouts, TCPS/DN matching, escaping, and parity with the deployed tnsnames.ora";
        }
        if (SID_URL.matcher(value).matches()) {
            return "This Oracle Thin URL uses the host:port:SID form; verify the target database still exposes that SID or migrate deliberately to a service-name URL without guessing database topology";
        }
        if (value.startsWith("jdbc:oracle:thin:@") && !value.startsWith("jdbc:oracle:thin:@//") &&
            !value.contains("/") && !value.contains(":")) {
            return "This Oracle Thin URL uses a TNS alias; verify TNS_ADMIN/provider lookup, tnsnames.ora packaging, wallet/TLS material, container paths, rotation, and classloader/module visibility";
        }
        if (value.contains("TNS_ADMIN") || value.contains("tcps") || value.contains("wallet")) {
            return "This Oracle JDBC URL controls TNS, TCPS, or wallet behavior; verify provider availability, trust/key material, hostname/DN matching, secret rotation, file permissions, and container/native-image paths";
        }
        return null;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
