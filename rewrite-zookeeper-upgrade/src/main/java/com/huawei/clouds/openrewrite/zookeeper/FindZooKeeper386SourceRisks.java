package com.huawei.clouds.openrewrite.zookeeper;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks source decisions whose correct migration depends on application and operational evidence. */
public final class FindZooKeeper386SourceRisks extends Recipe {
    static final String CLIENT_SESSION =
            "ZooKeeper client session, retry and close behavior changed across 3.4/3.6/3.7/3.8; test connection-loss, " +
            "session expiry, chroot, read-only mode, request timeout, reconnect and shutdown ordering";
    static final String WATCH =
            "ZooKeeper persistent/recursive watch and remove-watch behavior is version-sensitive; test duplicate " +
            "registration, reconnect, session expiry, local removal, event ordering and callback concurrency";
    static final String ACL_AUTH =
            "ZooKeeper ACL/authentication behavior is security-sensitive; verify digest/SASL/X.509 identities, " +
            "super-user policy, recursive ACL changes and authorization failures against the upgraded ensemble";
    static final String MULTI =
            "ZooKeeper multi/transaction result and failure behavior changed across these branches; test atomicity, " +
            "version checks, partial result decoding, connection loss and retry/idempotency policy";
    static final String RECONFIG =
            "Dynamic reconfiguration changes quorum membership and requires an enabled, compatible ensemble; verify " +
            "reconfigEnabled, standaloneEnabled, server.N client ports, rolling order and rollback on a clone";
    static final String SERVER_INTERNAL =
            "This application uses ZooKeeper server/quorum internals whose constructors, return types or lifecycle " +
            "changed before 3.8.6; migrate against the target API and exercise startup, leader election and shutdown";
    static final String PERSISTENCE =
            "ZooKeeper transaction-log/snapshot internals changed; never rewrite this semantically without data. " +
            "Validate snapshot.trust.empty, dataDir/dataLogDir, checksums, restore/truncate behavior and backups";
    static final String TLS_SASL =
            "ZooKeeper TLS/SASL internals and defaults changed; verify hostname/reverse-DNS behavior, FIPS mode, " +
            "keystore/truststore reload, clientAuth, SASL principals, login renewal and mixed rolling connections";
    static final String AUDIT =
            "ZooKeeper 3.8 uses SLF4J-backed audit logging; verify zookeeper.audit.enable, implementation selection, " +
            "logger routing, event completeness, credential redaction and provider compatibility";
    static final String JUTE =
            "This source directly consumes ZooKeeper Jute/generated protocol classes; align zookeeper-jute to 3.8.6 " +
            "and verify serialization compatibility instead of treating generated classes as ordinary public API";

    private static final Set<String> WATCH_METHODS = Set.of(
            "addWatch", "removeWatches", "removeAllWatches", "register", "process");
    private static final Set<String> ACL_METHODS = Set.of(
            "addAuthInfo", "getACL", "setACL", "fixupACL", "auth", "checkACL");
    private static final Set<String> MULTI_METHODS = Set.of("multi", "transaction", "commit");
    private static final Set<String> SESSION_METHODS = Set.of(
            "close", "getSessionId", "getSessionPasswd", "getSessionTimeout", "getState", "exists",
            "getData", "getChildren", "create", "delete", "sync");
    private static final Set<String> PERSISTENCE_METHODS = Set.of(
            "getDataDir", "getDataLogDir", "getSnapDir", "restore", "save", "truncateLog",
            "append", "commit", "rollLog", "snapLog");

    @Override
    public String getDisplayName() {
        return "Find ZooKeeper 3.8.6 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark client session/watch/ACL/multi behavior, dynamic reconfiguration, embedded server internals, " +
               "persistence, TLS/SASL, audit and direct Jute protocol usage requiring application evidence.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return ZooKeeperSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import import_, ExecutionContext ctx) {
                J.Import visited = super.visitImport(import_, ctx);
                String type = visited.getTypeName();
                String message = importMessage(type);
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String message = typeMessage(typeName(visited.getType()));
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                if (methodType == null) return visited;
                String owner = typeName(methodType.getDeclaringType());
                String message = methodMessage(owner, visited.getSimpleName());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference visited = super.visitMemberReference(memberRef, ctx);
                JavaType.Method methodType = visited.getMethodType();
                if (methodType == null) return visited;
                String message = methodMessage(typeName(methodType.getDeclaringType()),
                        visited.getReference().getSimpleName());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                JavaType.Variable field = visited.getFieldType();
                if (field == null) return visited;
                String owner = typeName(field.getOwner());
                String message = fieldMessage(owner, visited.getSimpleName());
                return message == null ? visited : mark(visited, message);
            }
        };
    }

    private static String importMessage(String type) {
        if (type == null) return null;
        if (type.startsWith("org.apache.zookeeper.proto.") || type.startsWith("org.apache.zookeeper.data.") ||
            type.startsWith("org.apache.jute.")) return JUTE;
        if (type.startsWith("org.apache.zookeeper.server.persistence.")) return PERSISTENCE;
        if (tlsType(type)) return TLS_SASL;
        if (auditType(type)) return AUDIT;
        if (serverType(type)) return SERVER_INTERNAL;
        return null;
    }

    private static String typeMessage(String type) {
        if (type == null) return null;
        if (type.startsWith("org.apache.zookeeper.proto.") || type.startsWith("org.apache.zookeeper.data.") ||
            type.startsWith("org.apache.jute.")) return JUTE;
        if (type.startsWith("org.apache.zookeeper.server.persistence.")) return PERSISTENCE;
        if (tlsType(type)) return TLS_SASL;
        if (auditType(type)) return AUDIT;
        if (serverType(type)) return SERVER_INTERNAL;
        if ("org.apache.zookeeper.ZooKeeper".equals(type)) return CLIENT_SESSION;
        return null;
    }

    private static String methodMessage(String owner, String method) {
        if (owner == null) return null;
        if (owner.startsWith("org.apache.zookeeper.proto.") || owner.startsWith("org.apache.zookeeper.data.") ||
            owner.startsWith("org.apache.jute.")) return JUTE;
        if (owner.startsWith("org.apache.zookeeper.server.persistence.")) {
            return PERSISTENCE_METHODS.contains(method) ? PERSISTENCE : SERVER_INTERNAL;
        }
        if (tlsType(owner)) return TLS_SASL;
        if (auditType(owner)) return AUDIT;
        if ("org.apache.zookeeper.ZooKeeper".equals(owner)) {
            if (WATCH_METHODS.contains(method)) return WATCH;
            if (ACL_METHODS.contains(method)) return ACL_AUTH;
            if (MULTI_METHODS.contains(method)) return MULTI;
            if (SESSION_METHODS.contains(method)) return CLIENT_SESSION;
        }
        if ("org.apache.zookeeper.admin.ZooKeeperAdmin".equals(owner) &&
            "reconfigure".equals(method)) return RECONFIG;
        if (owner.startsWith("org.apache.zookeeper.Transaction")) return MULTI;
        if (owner.startsWith("org.apache.zookeeper.ZKUtil") &&
            ("deleteRecursive".equals(method) || "visitSubTreeDFS".equals(method))) return ACL_AUTH;
        if ("org.apache.zookeeper.server.quorum.QuorumPeerConfig".equals(owner) ||
            owner.startsWith("org.apache.zookeeper.server.quorum.") &&
            ("reconfig".equals(method) || method.toLowerCase(java.util.Locale.ROOT).contains("config"))) {
            return RECONFIG;
        }
        if (serverType(owner)) return SERVER_INTERNAL;
        return null;
    }

    private static String fieldMessage(String owner, String field) {
        if (owner == null) return null;
        if (tlsType(owner)) return TLS_SASL;
        if (auditType(owner)) return AUDIT;
        if (owner.startsWith("org.apache.zookeeper.ZooDefs") &&
            ("Ids".equals(field) || field.contains("ACL") || field.contains("PERM"))) return ACL_AUTH;
        if (owner.startsWith("org.apache.zookeeper.server.persistence.")) return PERSISTENCE;
        return serverType(owner) ? SERVER_INTERNAL : null;
    }

    private static boolean serverType(String type) {
        return type.startsWith("org.apache.zookeeper.server.") ||
               type.startsWith("org.apache.zookeeper.admin.") &&
               !"org.apache.zookeeper.admin.ZooKeeperAdmin".equals(type);
    }

    private static boolean tlsType(String type) {
        return type.contains("X509") || type.contains("Sasl") || type.contains("SASL") ||
               type.startsWith("org.apache.zookeeper.common.") ||
               type.startsWith("org.apache.zookeeper.client.ZooKeeperSaslClient") ||
               type.startsWith("org.apache.zookeeper.util.SecurityUtils");
    }

    private static boolean auditType(String type) {
        return type.startsWith("org.apache.zookeeper.audit.");
    }

    private static String typeName(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? null : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree :
                SearchResult.found(tree, message);
    }
}
