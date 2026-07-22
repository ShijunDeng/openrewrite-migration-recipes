package com.huawei.clouds.openrewrite.mssqljdbc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Type- and syntax-aware markers for behavior changes between SQL Server JDBC 7/9/10/11 and 13.2. */
public final class FindMssqlJdbc13MigrationRisks extends Recipe {
    private static final Pattern VECTOR_SQL = Pattern.compile("(?is)\\b(?:CREATE|ALTER|CAST|CONVERT)[^;]*\\bVECTOR\\s*\\(");
    private static final Set<String> TLS_METHODS = Set.of(
            "setEncrypt", "setTrustServerCertificate", "setTrustStore", "setTrustStorePassword",
            "setHostNameInCertificate", "setServerCertificate", "setSSLProtocol"
    );
    private static final Set<String> AUTH_METHODS = Set.of(
            "setAuthentication", "setAuthenticationScheme", "setAADSecurePrincipalId",
            "setAADSecurePrincipalSecret", "setAccessToken", "setAccessTokenCallback",
            "setAccessTokenCallbackClass", "setMSIClientId"
    );
    private static final Set<String> ENCRYPTION_METHODS = Set.of(
            "setColumnEncryptionSetting", "registerColumnEncryptionKeyStoreProviders",
            "registerColumnEncryptionKeyStoreProvidersOnConnection", "setEnclaveAttestationUrl",
            "setEnclaveAttestationProtocol", "setAllowEncryptedValueModifications"
    );
    private static final Set<String> SESSION_METHODS = Set.of(
            "setQuotedIdentifier", "setConcatNullYieldsNull"
    );

    @Override
    public String getDisplayName() {
        return "Find Microsoft SQL Server JDBC 13.2 migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark complete SQL Server JDBC URLs, typed DataSource security/authentication/session calls, " +
               "vector SQL, and native authentication library references that require runtime decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedMssqlJdbcDependency.isProjectPath(compilationUnit.getSourcePath()) ?
                        super.visitCompilationUnit(compilationUnit, ctx) : compilationUnit;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (!(l.getValue() instanceof String value)) {
                    return l;
                }
                String lower = value.toLowerCase(Locale.ROOT);
                if (lower.contains("jdbc:sqlserver:")) {
                    List<String> risks = new ArrayList<>();
                    risks.add(lower.contains("encrypt=") || lower.contains("trustservercertificate=") ||
                              lower.contains("hostnameincertificate=") || lower.contains("servercertificate=")
                            ? "retest explicit TLS/encrypt/certificate policy"
                            : "encrypt now defaults to true; define and test certificate policy");
                    risks.add(lower.contains("logintimeout=")
                            ? "retest explicit loginTimeout/failover timing"
                            : "loginTimeout default changed from 15s to 30s");
                    if (containsAny(lower, "authentication=", "aadsecureprincipal", "accesstoken", "msiclientid")) {
                        risks.add("review Microsoft Entra authentication and optional Azure/MSAL dependencies");
                    }
                    if (containsAny(lower, "columnencryptionsetting=", "enclaveattestation", "keyvaultprovider")) {
                        risks.add("retest Always Encrypted/key-provider/enclave behavior");
                    }
                    if (lower.contains("vectortypesupport=")) {
                        risks.add("verify native vector versus legacy JSON-string mapping");
                    }
                    if (containsAny(lower, "quotedidentifier=", "concatnullyieldsnull=")) {
                        risks.add("verify pooled-connection session settings");
                    }
                    return mark(l, "SQL Server JDBC 13.2: " + String.join("; ", risks));
                }
                if (containsAny(lower, "columnencryptionsetting=", "enclaveattestation", "keyvaultprovider")) {
                    return mark(l,
                            "Always Encrypted connection fragment detected; verify key provider, optional dependencies, attestation, metadata cache and failover behavior");
                }
                if (containsAny(lower, "authentication=activedirectory", "aadsecureprincipal", "accesstokencallbackclass=", "msiclientid=")) {
                    return mark(l,
                            "Microsoft Entra connection fragment detected; review deprecated modes/properties, token callbacks and Azure Identity/MSAL dependencies");
                }
                if (lower.contains("vectortypesupport=")) {
                    return mark(l,
                            "vectorTypeSupport connection fragment detected; verify native vector versus legacy JSON-string ResultSet/ORM mappings");
                }
                if (containsAny(lower, "quotedidentifier=", "concatnullyieldsnull=")) {
                    return mark(l,
                            "SQL Server session-property fragment detected; verify new and pooled connection state and dependent SQL semantics");
                }
                if (VECTOR_SQL.matcher(value).find()) {
                    return mark(l,
                            "SQL Server VECTOR detected: 13.2 returns native vector values unless vectorTypeSupport=off; update ResultSet/ORM mappings deliberately");
                }
                if (lower.contains("mssql-jdbc_auth") || lower.contains("sqljdbc_auth")) {
                    return mark(l,
                            "Native integrated-authentication library reference detected; deploy the 13.2 architecture-specific mssql-jdbc_auth binary and retest Kerberos/NTLM");
                }
                return l;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (!isMicrosoftSqlServerMethod(m.getMethodType())) {
                    return m;
                }
                String name = m.getSimpleName();
                if (TLS_METHODS.contains(name)) {
                    return mark(m,
                            "Retest SQL Server JDBC 13.2 encryption defaults, strict/TDS 8.0, certificate chain and hostname validation");
                }
                if ("setLoginTimeout".equals(name)) {
                    return mark(m,
                            "13.2 inherits the 30-second default and revised socket/login timeout interaction; verify failover, pool and probe budgets");
                }
                if (AUTH_METHODS.contains(name)) {
                    return mark(m,
                            "Review Microsoft Entra/integrated authentication: deprecated AAD properties, renamed modes, optional Azure Identity/MSAL dependencies and token callbacks");
                }
                if (ENCRYPTION_METHODS.contains(name)) {
                    return mark(m,
                            "Always Encrypted or enclave API detected; verify key provider, attestation, metadata cache, optional dependencies and failover behavior");
                }
                if ("setVectorTypeSupport".equals(name) || "getVectorTypeSupport".equals(name)) {
                    return mark(m,
                            "Verify SQL Server 13.2 native vector representation and bulk-copy/ResultSet/ORM mappings; use off only as a temporary compatibility choice");
                }
                if (SESSION_METHODS.contains(name)) {
                    return mark(m,
                            "Verify quotedIdentifier/concatNullYieldsNull on new and pooled connections; session state affects SQL semantics");
                }
                return m;
            }
        };
    }

    private static boolean isMicrosoftSqlServerMethod(JavaType.Method method) {
        JavaType.FullyQualified owner = method == null ? null : TypeUtils.asFullyQualified(method.getDeclaringType());
        return owner != null && owner.getFullyQualifiedName().startsWith("com.microsoft.sqlserver.jdbc.");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
