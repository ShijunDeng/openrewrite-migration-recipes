package com.huawei.clouds.openrewrite.commonspool2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks behavior changes that require application-specific policy or regression fixtures. */
public final class FindCommonsPool2JavaRisks extends Recipe {
    private static final String PREFIX = "org.apache.commons.pool2.";
    private static final Set<String> CAPACITY_OPERATIONS = Set.of("addObject", "invalidateObject", "clear", "returnObject", "preparePool");
    private static final Set<String> VALIDATION_OPTIONS = Set.of("setTestOnCreate", "setTestOnBorrow", "setTestOnReturn", "setTestWhileIdle");
    private static final Set<String> EVICTION_OPTIONS = Set.of(
            "setMinEvictableIdleTimeMillis", "setMinEvictableIdleTime", "setMinEvictableIdleDuration",
            "setSoftMinEvictableIdleTimeMillis", "setSoftMinEvictableIdleTime", "setSoftMinEvictableIdleDuration",
            "setTimeBetweenEvictionRunsMillis", "setTimeBetweenEvictionRuns", "setDurationBetweenEvictionRuns",
            "setNumTestsPerEvictionRun", "setEvictionPolicy", "setEvictionPolicyClassName");
    private static final Set<String> ABANDONED_OPTIONS = Set.of(
            "setAbandonedConfig", "setRemoveAbandonedOnBorrow", "setRemoveAbandonedOnMaintenance",
            "setRemoveAbandonedTimeout", "setLogAbandoned", "setLogWriter", "setUseUsageTracking", "setRequireFullStackTrace");
    private static final Set<String> TIMING_GETTERS = Set.of(
            "getActiveTimeMillis", "getIdleTimeMillis", "getCreateTime", "getLastBorrowTime", "getLastReturnTime", "getLastUsedTime",
            "getMeanActiveTimeMillis", "getMeanBorrowWaitTimeMillis", "getMeanIdleTimeMillis", "getMaxBorrowWaitTimeMillis");

    @Override
    public String getDisplayName() {
        return "Find Apache Commons Pool 2.13.1 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks borrowing, capacity, concurrent return/invalidation, validation, eviction, abandoned-object, timing/statistics, factory and lifecycle decisions changed through 2.13.1.";
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
                if (type == null || !commonsPoolType(type.getDeclaringType())) return visited;
                String name = visited.getSimpleName();
                if ("borrowObject".equals(name)) {
                    return SearchResult.found(visited, "Borrow timeout, spurious-wakeup and capacity-reuse behavior changed through 2.13.1; test finite/indefinite waits, fairness, exhaustion, interruption and keyed pools under contention");
                }
                if (CAPACITY_OPERATIONS.contains(name)) {
                    return SearchResult.found(visited, "Pool capacity behavior changed: addObject now respects maxIdle/capacity and invalidate/clear can wake or replace waiting borrowers; prevent concurrent returnObject/invalidateObject for the same identity and stress-test counters");
                }
                if (Set.of("setMaxTotal", "setMaxIdle", "setMinIdle", "setBlockWhenExhausted", "setLifo", "setFairness", "setMaxWait", "setMaxWaitMillis").contains(name)) {
                    return SearchResult.found(visited, "This call defines saturation, ordering or wait policy; replay concurrency tests because wake-up deadlines, fairness and capacity reuse changed through 2.13.1");
                }
                if (VALIDATION_OPTIONS.contains(name)) {
                    return SearchResult.found(visited, "Validation policy controls factory callbacks and replacement objects; verify exception swallowing, destroy modes, validation failures and waiter progress on 2.13.1");
                }
                if (EVICTION_OPTIONS.contains(name)) {
                    return SearchResult.found(visited, "Eviction scheduling/eligibility changed, including shared timer cleanup, negative-duration normalization and idle thresholds; test disabled, zero and boundary durations plus custom policies");
                }
                if (ABANDONED_OPTIONS.contains(name)) {
                    return SearchResult.found(visited, "Abandoned-object removal can now create replacement capacity and reports swallowed exceptions differently; test leak thresholds, usage tracking, maintenance/borrow triggers and stack-trace cost");
                }
                if (TIMING_GETTERS.contains(name)) {
                    return SearchResult.found(visited, "Millis timing/statistics moved to Instant/Duration with nanosecond precision and fixes for negative active time; verify units, clocks, negative values, aggregation and monitoring thresholds");
                }
                if (Set.of("setCollectDetailedStatistics", "setMessagesStatistics", "setMessageStatistics").contains(name)) {
                    return SearchResult.found(visited, "Detailed statistics and exception-message statistics affect cost and observability; benchmark contention and update metric/log assertions");
                }
                return visited;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference visited = super.visitMemberReference(memberRef, ctx);
                JavaType.Method type = visited.getMethodType();
                String name = visited.getReference().getSimpleName();
                if (type != null && commonsPoolType(type.getDeclaringType()) && ABANDONED_OPTIONS.contains(name)) {
                    return SearchResult.found(visited, "Abandoned-object policy callback detected; removal can now create replacement capacity, so verify thresholds, triggers, exception handling and waiter progress");
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                if (implementsType(visited, PREFIX + "PooledObjectFactory") || implementsType(visited, PREFIX + "KeyedPooledObjectFactory")) {
                    return SearchResult.found(visited, "Custom pool factory detected; regression-test make/activate/passivate/validate/destroy ordering, exceptions, DestroyMode and replacement creation under concurrency");
                }
                if (implementsType(visited, PREFIX + "impl.EvictionPolicy")) {
                    return SearchResult.found(visited, "Custom eviction policy detected; verify idle-duration boundaries, negative-duration normalization, minIdle behavior and class-loader/OSGi loading on 2.13.1");
                }
                if (extendsType(visited, PREFIX + "impl.GenericObjectPool") || extendsType(visited, PREFIX + "impl.GenericKeyedObjectPool")) {
                    return SearchResult.found(visited, "Custom pool subclass detected; audit synchronization/counter assumptions and close/AutoCloseable lifecycle against the 2.13.1 concurrency fixes");
                }
                return visited;
            }
        };
    }

    private static boolean commonsPoolType(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq != null && fq.getFullyQualifiedName().startsWith(PREFIX);
    }

    private static boolean implementsType(J.ClassDeclaration declaration, String fqn) {
        return declaration.getImplements() != null && declaration.getImplements().stream()
                .anyMatch(type -> TypeUtils.isAssignableTo(fqn, type.getType()));
    }

    private static boolean extendsType(J.ClassDeclaration declaration, String fqn) {
        return declaration.getExtends() != null && TypeUtils.isAssignableTo(fqn, declaration.getExtends().getType());
    }
}
