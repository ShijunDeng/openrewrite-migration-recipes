package com.huawei.clouds.openrewrite.kubernetesclientjava;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Type-aware markers for Fabric8 5 to 7 changes that need application semantics. */
public final class FindFabric8KubernetesClient7MigrationRisks extends Recipe {
    private static final String DEFAULT_CLIENT = "io.fabric8.kubernetes.client.DefaultKubernetesClient";
    private static final Set<String> RESOURCE_DSL = Set.of(
            "customResource", "lists", "resource", "resources", "deletingExisting", "delete", "evict", "apply"
    );
    private static final Set<String> LIFECYCLE = Set.of(
            "watch", "watchLog", "watchAndWait", "informers", "informer", "run", "stop",
            "exec", "attach", "portForward", "upload", "file"
    );
    private static final Set<String> CAPABILITY = Set.of(
            "isAdaptable", "supports", "supportsApiPath", "isSupported", "hasApiGroup"
    );

    @Override
    public String getDisplayName() {
        return "Find Fabric8 Kubernetes Client 7 migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark only type-attributed Fabric8 construction, configuration, resource DSL, deletion, " +
               "watch/informer, streaming, and capability calls whose version 7 migration requires behavior choices.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (TypeUtils.isOfClassType(n.getType(), DEFAULT_CLIENT)) {
                    return SearchResult.found(n,
                            "Direct DefaultKubernetesClient construction remains: choose KubernetesClientBuilder and preserve namespace/HTTP-client semantics");
                }
                return n;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (!isFabric8(methodType)) {
                    return m;
                }
                String name = m.getSimpleName();
                if ("getKubeconfigFilename".equals(name)) {
                    return SearchResult.found(m,
                            "Config.getKubeconfigFilename() became getKubeconfigFilenames(); migrate String consumers to the returned collection deliberately");
                }
                if (RESOURCE_DSL.contains(name)) {
                    return SearchResult.found(m,
                            "Fabric8 resource/delete DSL changed across 6/7; verify resource(item), createOrReplace, delete StatusDetails, and propagation semantics");
                }
                if (LIFECYCLE.contains(name)) {
                    return SearchResult.found(m,
                            "Fabric8 watch/informer/stream lifecycle changed; verify close, reconnect, timeout, executor, and exception behavior on 7.3.1");
                }
                if (CAPABILITY.contains(name)) {
                    return SearchResult.found(m,
                            "Fabric8 capability/adapt check semantics changed; choose hasApiGroup, supports, or adaptation according to intent");
                }
                return m;
            }

            private boolean isFabric8(JavaType.Method method) {
                JavaType.FullyQualified owner = method == null ? null : TypeUtils.asFullyQualified(method.getDeclaringType());
                return owner != null && (owner.getFullyQualifiedName().startsWith("io.fabric8.kubernetes.") ||
                                         owner.getFullyQualifiedName().startsWith("io.fabric8.openshift."));
            }
        };
    }
}
