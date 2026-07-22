package com.huawei.clouds.openrewrite.curatorframework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Map;

/** Marks attributed Curator APIs whose 5.x replacement depends on application behavior. */
public final class FindCurator5JavaMigrationRisks extends Recipe {
    private static final Map<String, String> TYPE_MESSAGES = Map.ofEntries(
            Map.entry("org.apache.curator.ConnectionHandlingPolicy",
                    "ConnectionHandlingPolicy was removed in Curator 5; remove the custom policy and verify SUSPENDED, LOST, RECONNECTED, session-expiration, and retry behavior under real network faults"),
            Map.entry("org.apache.curator.framework.recipes.locks.Reaper",
                    "Reaper was removed in Curator 5; redesign cleanup with ZooKeeper container nodes and verify create mode, ACLs, concurrent owners, retention, and rollback"),
            Map.entry("org.apache.curator.framework.recipes.locks.ChildReaper",
                    "ChildReaper was removed in Curator 5; redesign cleanup with ZooKeeper container nodes and verify child ownership, ACLs, races, retention, and rollback"),
            Map.entry("org.apache.curator.framework.recipes.cache.NodeCache",
                    "NodeCache is a legacy cache API; migrate deliberately to CuratorCache SINGLE_NODE_CACHE and verify initialization, missing/deleted nodes, payloads, reconnect, and close ordering"),
            Map.entry("org.apache.curator.framework.recipes.cache.PathChildrenCache",
                    "PathChildrenCache is a legacy cache API; migrate deliberately to CuratorCache and verify root/depth semantics, event ordering, reconnect rebuilds, memory, and listener lifecycle"),
            Map.entry("org.apache.curator.framework.recipes.cache.TreeCache",
                    "TreeCache is a legacy cache API; migrate deliberately to CuratorCache and verify selectors, storage/depth, event payloads, reconnect rebuilds, executors, and error listeners")
    );

    @Override
    public String getDisplayName() {
        return "Find Curator 5 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact attributed listener, Exhibitor, connection, reaper, retry, group, cache, and discovery " +
               "APIs removed or behaviorally changed by Curator 5 with decision-specific reasons.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedCuratorFrameworkDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
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
                    message = "Curator 5 removed Exhibitor support; replace this exact boundary with a maintained static, DNS, service-discovery, configuration-center, or custom EnsembleProvider strategy and test server-list updates";
                }
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                String name = method.getName();
                String message = methodMessage(owner, name, method.getParameterTypes());
                return message == null ? visited : mark(visited, message);
            }
        };
    }

    private static String methodMessage(String owner, String name, java.util.List<JavaType> parameters) {
        if ("org.apache.curator.framework.listen.ListenerContainer".equals(owner) && "forEach".equals(name)) {
            return "ListenerContainer.forEach used a Guava Function whose return value is discarded; migrate to StandardListenerManager.forEach with a Consumer only after removing return-null semantics and reviewing listener exceptions";
        }
        if ("org.apache.curator.RetryLoop".equals(owner) && "shouldRetry".equals(name)) {
            return "RetryLoop.shouldRetry(int) was removed; let Curator and RetryPolicy drive retries or classify KeeperException.Code at the business boundary while preserving idempotency and interruption";
        }
        if ("org.apache.curator.RetryLoop".equals(owner) && "isRetryException".equals(name)) {
            return "RetryLoop.isRetryException(Throwable) was removed; redesign exception handling around RetryPolicy, cancellation, interruption, and the operation's idempotency rather than copying Curator internals";
        }
        if ("org.apache.curator.framework.recipes.nodes.GroupMember".equals(owner) &&
            "newPersistentEphemeralNode".equals(name)) {
            return "GroupMember.newPersistentEphemeralNode was removed; explicitly own a PersistentNode only if external lifecycle access is required and verify start, reconnect, close, ACL, and member-data behavior";
        }
        if ("org.apache.curator.framework.recipes.nodes.GroupMember".equals(owner) &&
            "newPathChildrenCache".equals(name)) {
            return "GroupMember.newPathChildrenCache was removed; create a separately owned supported cache and verify path scope, startup, close ordering, reconnect events, and listener lifecycle";
        }
        if (("org.apache.curator.x.discovery.ServiceCacheBuilder".equals(owner) ||
             "org.apache.curator.x.discovery.ServiceProviderBuilder".equals(owner)) &&
            "executorService".equals(name) && parameters.size() == 1 &&
            TypeUtils.isOfClassType(parameters.get(0), "org.apache.curator.utils.CloseableExecutorService")) {
            return "The CloseableExecutorService overload was removed; select a supported executor overload or default and explicitly verify executor ownership, shutdown ordering, rejection, thread leakage, and process exit";
        }
        return null;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
