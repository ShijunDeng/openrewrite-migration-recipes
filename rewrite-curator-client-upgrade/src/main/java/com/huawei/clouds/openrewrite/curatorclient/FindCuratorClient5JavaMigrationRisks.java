package com.huawei.clouds.openrewrite.curatorclient;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Map;
import java.util.Set;

/** Mark attributed curator-client APIs whose replacement needs application semantics. */
public final class FindCuratorClient5JavaMigrationRisks extends Recipe {
    private static final String ENSEMBLE_PROVIDER = "org.apache.curator.ensemble.EnsembleProvider";
    private static final String GROUP_MEMBER = "org.apache.curator.framework.recipes.nodes.GroupMember";
    private static final String CLOSEABLE_EXECUTOR = "org.apache.curator.utils.CloseableExecutorService";
    private static final Set<String> DISCOVERY_BUILDERS = Set.of(
            "org.apache.curator.x.discovery.ServiceCacheBuilder",
            "org.apache.curator.x.discovery.ServiceProviderBuilder");
    private static final Map<String, String> TYPE_MESSAGES = Map.ofEntries(
            Map.entry("org.apache.curator.framework.listen.ListenerContainer",
                    "Curator 5 removed the Guava-leaking ListenerContainer; migrate ownership and dispatch semantics to StandardListenerManager or MappingListenerManager and verify listener ordering, executor use, removal, exceptions, and shutdown"),
            Map.entry("org.apache.curator.framework.recipes.locks.Reaper",
                    "Curator 5 removed Reaper; use ZooKeeper container nodes and verify server-version support, empty-parent lifetime, ACLs, races, leader behavior, and cleanup guarantees"),
            Map.entry("org.apache.curator.framework.recipes.locks.ChildReaper",
                    "Curator 5 removed ChildReaper; use ZooKeeper container nodes and verify server-version support, child discovery, empty-parent lifetime, ACLs, races, and cleanup guarantees"),
            Map.entry("org.apache.curator.ConnectionHandlingPolicy",
                    "ConnectionHandlingPolicy and related classes were removed in Curator 5; remove the custom policy and verify SUSPENDED, LOST, RECONNECTED, session-expiration, and retry behavior under real network faults")
    );

    @Override
    public String getDisplayName() {
        return "Find Apache Curator Client 5 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact attributed Exhibitor, connection-policy, exception-classification, and custom " +
               "EnsembleProvider boundaries that cannot be changed safely without application decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedCuratorClientDependency.isProjectPath(compilationUnit.getSourcePath())
                        ? super.visitCompilationUnit(compilationUnit, ctx) : compilationUnit;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDeclaration, ctx);
                if (visited.getType() == null || !TypeUtils.isAssignableTo(ENSEMBLE_PROVIDER, visited.getType()) ||
                    ENSEMBLE_PROVIDER.equals(visited.getType().getFullyQualifiedName())) return visited;
                return mark(visited,
                        "Custom EnsembleProvider implementations from Curator 2.7.1 must implement and define semantics for setConnectionString(String) and updateServerListEnabled(); recompile every provider and verify dynamic reconfiguration, stale lists, rollback, and close ordering");
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                JavaType instantiated = visited.getClazz() == null ? visited.getType() : visited.getClazz().getType();
                if (visited.getBody() == null || !TypeUtils.isAssignableTo(ENSEMBLE_PROVIDER, instantiated)) {
                    return visited;
                }
                return mark(visited,
                        "Anonymous EnsembleProvider implementations from Curator 2.7.1 must implement and define semantics for setConnectionString(String) and updateServerListEnabled(); recompile every provider and verify dynamic reconfiguration, stale lists, rollback, and close ordering");
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                if (type == null || getCursor().firstEnclosing(J.Import.class) != null ||
                    !visited.getSimpleName().equals(type.getClassName())) return visited;
                String fqn = type.getFullyQualifiedName();
                String message = TYPE_MESSAGES.get(fqn);
                if (message == null && fqn.startsWith("org.apache.curator.ensemble.exhibitor.")) {
                    message = "Curator 5 removed Exhibitor support; replace this ensemble-discovery boundary with a maintained static, DNS, service-discovery, configuration-center, or custom EnsembleProvider strategy and test server-list updates";
                }
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                if ("org.apache.curator.RetryLoop".equals(owner) && "isRetryException".equals(method.getName())) {
                    return mark(visited,
                            "RetryLoop.isRetryException(Throwable) was removed; redesign exception handling around RetryPolicy, cancellation, interruption, and operation idempotency instead of copying Curator internals");
                }
                if (TypeUtils.isAssignableTo(GROUP_MEMBER, method.getDeclaringType()) &&
                    ("newPersistentEphemeralNode".equals(method.getName()) ||
                     "newPathChildrenCache".equals(method.getName()))) {
                    return mark(visited,
                            "Curator 5 removed GroupMember's protected node/cache factory hooks; redesign this customization using supported recipes and verify membership identity, cache consistency, reconnects, session expiration, watches, and close ordering");
                }
                if (DISCOVERY_BUILDERS.contains(owner) && "executorService".equals(method.getName()) &&
                    method.getParameterTypes().size() == 1 &&
                    TypeUtils.isOfClassType(method.getParameterTypes().get(0), CLOSEABLE_EXECUTOR)) {
                    return mark(visited,
                            "Curator 5 removed the ServiceCacheBuilder/ServiceProviderBuilder CloseableExecutorService overload; pass the supported ExecutorService form and explicitly preserve executor ownership, close, rejection, and thread-lifecycle semantics");
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (owner == null || owner.getType() == null || !TypeUtils.isAssignableTo(GROUP_MEMBER, owner.getType()) ||
                    !("newPersistentEphemeralNode".equals(visited.getSimpleName()) ||
                      "newPathChildrenCache".equals(visited.getSimpleName()))) return visited;
                return mark(visited,
                        "Curator 5 removed this GroupMember protected factory hook; redesign the customization using supported recipes and verify membership identity, cache consistency, reconnects, session expiration, watches, and close ordering");
            }
        };
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
