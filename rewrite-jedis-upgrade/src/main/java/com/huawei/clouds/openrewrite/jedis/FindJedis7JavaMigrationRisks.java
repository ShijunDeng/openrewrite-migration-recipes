package com.huawei.clouds.openrewrite.jedis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Marks attributed Jedis nodes whose correct version 7 replacement depends on application semantics. */
public final class FindJedis7JavaMigrationRisks extends Recipe {
    private static final Pattern SIMPLE_HOST = Pattern.compile("[A-Za-z0-9._-]+");
    private static final String SHARDING =
            "Jedis sharding APIs were removed; choose JedisPooled, JedisCluster, Sentinel, or an application router from the real Redis topology and verify hash ownership, failover, retries, pooling, transactions, and multi-key behavior";
    private static final String EXCEPTION =
            "This Jedis exception contract was removed or consolidated; rebuild the catch/retry policy against Jedis 7 exceptions and verify timeout, pool exhaustion, cluster refresh, failover, transaction abort, and idempotency behavior";
    private static final String INTERNAL =
            "This code extends or imports an internal Jedis boundary that changed across major versions; recompile against public Jedis 7 contracts and verify connection ownership, command encoding, retries, pooling, threading, and shutdown";
    private static final String REMOVED_CLIENT =
            "This low-level Jedis client/configuration type was removed; migrate to Connection, UnifiedJedis/JedisPooled, or the target builder/config contracts and verify protocol encoding, authentication, TLS, timeout, retry, threading, and close ownership";
    private static final Map<String, String> TYPE_MESSAGES = Map.ofEntries(
            Map.entry("redis.clients.jedis.ShardedJedisPool", SHARDING),
            Map.entry("redis.clients.jedis.ShardedJedis", SHARDING),
            Map.entry("redis.clients.jedis.BinaryShardedJedis", SHARDING),
            Map.entry("redis.clients.jedis.ShardedJedisPipeline", SHARDING),
            Map.entry("redis.clients.jedis.JedisShardInfo", SHARDING),
            Map.entry("redis.clients.jedis.util.ShardInfo", SHARDING),
            Map.entry("redis.clients.jedis.JedisSharding", SHARDING),
            Map.entry("redis.clients.jedis.ShardedPipeline", SHARDING),
            Map.entry("redis.clients.jedis.providers.ShardedConnectionProvider", SHARDING),
            Map.entry("redis.clients.jedis.Client", REMOVED_CLIENT),
            Map.entry("redis.clients.jedis.BinaryClient", REMOVED_CLIENT),
            Map.entry("redis.clients.jedis.DebugParams", REMOVED_CLIENT),
            Map.entry("redis.clients.jedis.JedisPoolAbstract", REMOVED_CLIENT),
            Map.entry("redis.clients.jedis.exceptions.JedisNoReachableClusterNodeException", EXCEPTION),
            Map.entry("redis.clients.jedis.exceptions.JedisClusterMaxAttemptsException", EXCEPTION),
            Map.entry("redis.clients.jedis.exceptions.JedisExhaustedPoolException", EXCEPTION),
            Map.entry("redis.clients.jedis.exceptions.AbortedTransactionException", EXCEPTION),
            Map.entry("redis.clients.jedis.exceptions.JedisDataException",
                    "Jedis 4 changed several invalid Pipeline/MULTI states from JedisDataException to IllegalStateException; review this exact exception boundary and verify retry, cleanup, transaction abort, response alignment, and operational alerts")
    );
    private static final Set<String> EXTENSION_TYPES = Set.of(
            "redis.clients.jedis.Connection", "redis.clients.jedis.JedisCluster",
            "redis.clients.jedis.Pipeline", "redis.clients.jedis.Transaction",
            "redis.clients.jedis.AbstractPipeline", "redis.clients.jedis.AbstractTransaction"
    );
    private static final Set<String> CHANGED_RETURN_COMMANDS = Set.of(
            "scriptExists", "blpop", "brpop", "bzpopmin", "bzpopmax", "blmpop", "bzmpop",
            "configGet", "zdiff", "zdiffWithScores", "zinter", "zinterWithScores",
            "zunion", "zunionWithScores"
    );
    private static final Set<String> SEARCH_COMMANDS = Set.of(
            "ftSearch", "ftAggregate", "ftProfile", "setDefaultSearchDialect"
    );

    @Override
    public String getDisplayName() {
        return "Find exact Jedis 7 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks attributed sharding, construction, pool, cluster, pipeline, transaction, command-return, " +
               "exception, module, and extension nodes with decision-specific Jedis 7 guidance.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private boolean usesJedisCluster;

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (UpgradeSelectedJedisDependency.generated(compilationUnit.getSourcePath())) {
                    return compilationUnit;
                }
                boolean previous = usesJedisCluster;
                usesJedisCluster = compilationUnit.getClasses().stream()
                        .anyMatch(declaration -> usesType(declaration, "redis.clients.jedis.JedisCluster"));
                J.CompilationUnit visited = super.visitCompilationUnit(compilationUnit, ctx);
                usesJedisCluster = previous;
                return visited;
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String imported = visited.getQualid().printTrimmed(getCursor());
                String message = TYPE_MESSAGES.get(imported);
                if (message != null) return mark(visited, message);
                if (imported.startsWith("redis.clients.jedis.graph.") ||
                    imported.startsWith("redis.clients.jedis.gears.")) {
                    return mark(visited, "RedisGraph and RedisGears v2 support was removed from Jedis; select a maintained module client or direct command boundary and verify serialization, errors, deployment modules, and response shapes");
                }
                if (imported.startsWith("redis.clients.jedis.commands.") ||
                    imported.startsWith("redis.clients.jedis.util.") ||
                    imported.startsWith("redis.clients.jedis.builders.") ||
                    imported.startsWith("redis.clients.jedis.providers.")) {
                    return mark(visited, INTERNAL);
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                    ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
                TypeTree type = visited.getTypeExpression();
                String message = type == null ? null : typeMessage(type.getType());
                if (message == null) return visited;
                TypeTree marked = mark(type, message);
                return marked == type ? visited : visited.withTypeExpression(marked);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                TypeTree type = visited.getReturnTypeExpression();
                String message = type == null ? null : typeMessage(type.getType());
                if (message == null) return visited;
                TypeTree marked = mark(type, message);
                return marked == type ? visited : visited.withReturnTypeExpression(marked);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
                TypeTree base = visited.getExtends();
                if (base != null && extensionType(base.getType())) {
                    TypeTree marked = mark(base, INTERNAL);
                    if (marked != base) visited = visited.withExtends(marked);
                }
                return visited.withImplements(ListUtils.map(visited.getImplements(), type ->
                        extensionType(type.getType()) ? mark(type, INTERNAL) : type));
            }

            @Override
            public J.ParameterizedType visitParameterizedType(J.ParameterizedType parameterized,
                                                              ExecutionContext ctx) {
                J.ParameterizedType visited = super.visitParameterizedType(parameterized, ctx);
                if ("org.apache.commons.pool2.impl.GenericObjectPoolConfig".equals(typeName(visited.getClazz().getType())) &&
                    visited.getTypeParameters() != null && visited.getTypeParameters().stream()
                            .anyMatch(type -> "redis.clients.jedis.Jedis".equals(typeName(type.getType()))) &&
                    usesJedisCluster) {
                    return mark(visited, "Jedis Cluster pool values changed from Jedis to Connection/ConnectionPool; update this exact generic owner and verify node-map consumers, resource closing, topology refresh, metrics, and pool tuning");
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = typeName(visited.getType());
                String typeRisk = typeMessage(visited.getType());
                if (typeRisk != null && visited.getClazz() != null) {
                    TypeTree marked = mark(visited.getClazz(), typeRisk);
                    if (marked != visited.getClazz()) visited = visited.withClazz(marked);
                }
                if (("redis.clients.jedis.Jedis".equals(type) || "redis.clients.jedis.JedisPool".equals(type)) &&
                    visited.getArguments().size() == 1 && !safeSingleArgument(visited.getArguments().get(0))) {
                    return mark(visited, "The pre-v4 one-string host constructor became URL/URI-oriented; choose an explicit HostAndPort/client builder, port, TLS, authentication, timeout, and client-name policy for this dynamic value");
                }
                if (type != null && Set.of("redis.clients.jedis.JedisCluster", "redis.clients.jedis.JedisSentinelPool",
                           "redis.clients.jedis.UnifiedJedis").contains(type) && visited.getArguments().size() >= 4) {
                    return mark(visited, "This multi-argument client constructor crosses connection/timeout/authentication/pool changes; rebuild it with the Jedis 7 builder/config objects and verify TLS hostname checks, credentials, retries, blocking timeout, failover, and resource ownership");
                }
                if ("redis.clients.jedis.admin.JedisClusterCommand".equals(type)) {
                    return mark(visited, INTERNAL);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                String name = visited.getSimpleName();
                String message = methodMessage(owner, name, method.getParameterTypes().size());
                return message == null ? visited : mark(visited, message);
            }
        };
    }

    private static boolean usesType(Tree tree, String fullyQualifiedTypeName) {
        boolean[] found = {false};
        new JavaIsoVisitor<boolean[]>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, boolean[] accumulator) {
                J.Identifier visited = super.visitIdentifier(identifier, accumulator);
                if (TypeUtils.isOfClassType(visited.getType(), fullyQualifiedTypeName)) accumulator[0] = true;
                return visited;
            }
        }.visit(tree, found);
        return found[0];
    }

    private static String methodMessage(String owner, String name, int parameterCount) {
        if (("redis.clients.jedis.JedisPool".equals(owner) || "redis.clients.jedis.util.Pool".equals(owner)) &&
            Set.of("getResource", "returnResource", "returnBrokenResource").contains(name)) {
            return "Review this pooled connection ownership boundary: borrowed Jedis resources must be closed exactly once and must not escape threads/transactions; verify exhaustion, validation, broken-resource eviction, shutdown, and metrics";
        }
        if (("redis.clients.jedis.JedisCluster".equals(owner) || owner.contains("ClusterConnectionProvider")) &&
            Set.of("getClusterNodes", "getConnectionFromSlot").contains(name)) {
            return "Jedis Cluster node/slot access now exposes ConnectionPool/Connection rather than Jedis; migrate the exact consumer and verify close ownership, topology refresh, MOVED/ASK retries, metrics, and concurrent access";
        }
        if ("redis.clients.jedis.Pipeline".equals(owner) && Set.of("multi", "exec", "discard").contains(name)) {
            return "Pipeline transaction control was removed; choose a Transaction or restructure batching without changing command order, response alignment, atomicity, error propagation, and connection ownership";
        }
        if ("redis.clients.jedis.Transaction".equals(owner) &&
            Set.of("execGetResponse", "watch", "unwatch").contains(name)) {
            return "This Transaction API changed; preserve WATCH/MULTI connection affinity and explicitly handle EXEC aborts, response ordering, retries, cleanup, and exceptions with the Jedis 7 contract";
        }
        if ("redis.clients.jedis.params.SetParams".equals(owner) && "get".equals(name)) {
            return "SetParams.get() was removed; use the target SET/GET option deliberately and verify atomic return values, null handling, NX/XX interaction, expiration, retries, and serialization";
        }
        if (isJedisClient(owner) && "xpending".equals(name) && parameterCount >= 6) {
            return "The legacy multi-argument XPENDING overload changed; create XPendingParams explicitly and verify range bounds, idle filter, consumer selection, pagination, and returned entry semantics";
        }
        if (isJedisClient(owner) && CHANGED_RETURN_COMMANDS.contains(name)) {
            return "This command crosses Jedis return/timeout signature changes; recompile its exact assignment and verify binary/string overload, null/empty behavior, collection ordering, numeric/boxed types, blocking units, and Redis server compatibility";
        }
        if (isJedisClient(owner) && SEARCH_COMMANDS.contains(name)) {
            return "Jedis Search/module behavior changed, including dialect and response shapes; validate query syntax, default dialect, profile/aggregate parsing, module availability, errors, and serialization against the production Redis version";
        }
        if ("redis.clients.jedis.HostAndPort".equals(owner) &&
            Set.of("extractParts", "parseString", "convertHost", "setLocalhost", "getLocalhost",
                   "getLocalHostQuietly").contains(name)) {
            return "This HostAndPort helper was removed; parse and validate the endpoint explicitly while preserving IPv6, DNS, default-port, Sentinel/Cluster discovery, malformed-input, and security behavior";
        }
        if (isJedisClient(owner) && Set.of("shutdown", "eval", "evalsha", "pubsubNumSub", "debug", "sync").contains(name)) {
            return "This Jedis command crosses a removed or behavior/return-type change; recompile the exact call and verify blocking behavior, response type, script error/retry semantics, Pub/Sub shape, permissions, and connection state against the target server";
        }
        if ("redis.clients.jedis.Connection".equals(owner) &&
            Set.of("quit", "getRawObjectMultiBulkReply").contains(name)) {
            return "This low-level Connection method was removed; use the public close/response contract deliberately and verify pending replies, protocol synchronization, authentication, errors, and resource ownership";
        }
        if ("redis.clients.jedis.util.Pool".equals(owner) && "initPool".equals(name)) {
            return "Pool.initPool() was removed; construct/configure the target pool through supported constructors/builders and verify initialization failure, validation, eviction, JMX, exhaustion, and shutdown";
        }
        return null;
    }

    private static boolean isJedisClient(String owner) {
        return owner.equals("redis.clients.jedis.Jedis") || owner.equals("redis.clients.jedis.UnifiedJedis") ||
               owner.equals("redis.clients.jedis.JedisPooled") || owner.equals("redis.clients.jedis.JedisCluster") ||
               owner.equals("redis.clients.jedis.Pipeline") || owner.equals("redis.clients.jedis.Transaction") ||
               owner.startsWith("redis.clients.jedis.commands.");
    }

    private static boolean safeSingleArgument(org.openrewrite.java.tree.Expression argument) {
        if (TypeUtils.isOfClassType(argument.getType(), "java.net.URI")) return true;
        if (!(argument instanceof J.Literal literal) || !(literal.getValue() instanceof String value)) return false;
        return value.startsWith("redis://") || value.startsWith("rediss://") || SIMPLE_HOST.matcher(value).matches();
    }

    private static boolean extensionType(JavaType type) {
        String fqn = typeName(type);
        return fqn != null && EXTENSION_TYPES.contains(fqn);
    }

    private static String typeMessage(JavaType type) {
        String fqn = typeName(type);
        return fqn == null ? null : TYPE_MESSAGES.get(fqn);
    }

    private static String typeName(JavaType type) {
        JavaType.FullyQualified fqn = TypeUtils.asFullyQualified(type);
        return fqn == null ? null : fqn.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        boolean present = tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()));
        return present ? tree : SearchResult.found(tree, message);
    }
}
