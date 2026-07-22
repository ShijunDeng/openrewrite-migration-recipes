package com.huawei.clouds.openrewrite.springcloudeureka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Type-aware Java review markers for Spring Cloud Netflix Eureka Client 4.2.0. */
public final class FindEurekaClientJavaMigrationRisks extends Recipe {
    private static final Set<String> EUREKA_LIFECYCLE_METHODS = Set.of(
            "getApplication", "getApplications", "getInstancesById", "getInstancesByVipAddress",
            "getNextServerFromEureka", "registerHealthCheck", "registerEventListener", "shutdown"
    );
    private static final Set<String> INSTANCE_METHODS = Set.of(
            "setInstanceStatus", "setStatus", "setIsCoordinatingDiscoveryServer"
    );

    @Override
    public String getDisplayName() {
        return "Find Eureka Client 4.2 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed Ribbon and optional-args APIs, custom Eureka lifecycle/transport code, bootstrap and " +
               "refresh hooks, discovery/load-balancer annotations, and Javax/Jakarta boundaries.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import i = super.visitImport(anImport, ctx);
                String name = i.getQualid().printTrimmed(getCursor());
                if (name.startsWith("javax.servlet.") || name.startsWith("javax.persistence.") ||
                    name.startsWith("javax.validation.") || name.startsWith("javax.annotation.")) {
                    return SearchResult.found(i,
                            "Spring Boot 3.4 uses Spring Framework 6 and Jakarta EE; migrate this Javax API and its dependency together");
                }
                if (name.startsWith("org.springframework.cloud.bootstrap.") ||
                    name.contains("PropertySourceLocator")) {
                    return SearchResult.found(i,
                            "Legacy bootstrap customization detected; choose Config Data or add/test spring-cloud-starter-bootstrap explicitly");
                }
                if (name.startsWith("org.springframework.nativex.")) {
                    return SearchResult.found(i,
                            "Spring Native-era hints are not the Boot 3 AOT model; port to RuntimeHints and test Eureka's native restrictions");
                }
                return i;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(v.getType());
                String fqn = type == null ? "" : type.getFullyQualifiedName();
                if (isRibbon(fqn)) {
                    return SearchResult.found(v,
                            "Netflix Ribbon is absent from the 2024.0 release train; port rule/server-list behavior to Spring Cloud LoadBalancer explicitly");
                }
                if (fqn.equals("org.springframework.cloud.netflix.eureka.MutableDiscoveryClientOptionalArgs")) {
                    return SearchResult.found(v,
                            "MutableDiscoveryClientOptionalArgs was removed; choose the target HTTP transport and its transport-specific optional args/customizer");
                }
                return v;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getType());
                if (type != null && type.getFullyQualifiedName().equals(
                        "org.springframework.cloud.netflix.eureka.CloudEurekaClient")) {
                    return SearchResult.found(n,
                            "CloudEurekaClient constructors now require TransportClientFactories; prefer auto-configuration or choose RestTemplate/RestClient/WebClient/Jersey deliberately");
                }
                return n;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(a.getType());
                String fqn = type == null ? "" : type.getFullyQualifiedName();
                if (isRibbon(fqn)) {
                    return SearchResult.found(a,
                            "Netflix Ribbon annotation detected; port per-client rule and server-list configuration to Spring Cloud LoadBalancer");
                }
                if (fqn.equals("org.springframework.cloud.client.discovery.EnableDiscoveryClient")) {
                    return SearchResult.found(a,
                            "Discovery auto-configuration no longer needs this annotation in a normal Boot application; verify multi-discovery intent before removal");
                }
                if (fqn.equals("org.springframework.cloud.client.loadbalancer.LoadBalanced")) {
                    return SearchResult.found(a,
                            "Verify spring-cloud-loadbalancer is present and retest blocking/reactive client, retry, cache, zone, hint and health-check behavior");
                }
                if (fqn.equals("org.springframework.cloud.openfeign.FeignClient")) {
                    return SearchResult.found(a,
                            "Verify Feign name/contextId matches the Eureka service ID and that OpenFeign plus Spring Cloud LoadBalancer are explicitly present");
                }
                if (fqn.equals("org.springframework.cloud.context.config.annotation.RefreshScope")) {
                    return SearchResult.found(a,
                            "Eureka refresh can briefly unregister the client; AOT/native requires spring.cloud.refresh.enabled=false");
                }
                return a;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                JavaType.FullyQualified returnType = m.getReturnTypeExpression() == null ? null :
                        TypeUtils.asFullyQualified(m.getReturnTypeExpression().getType());
                if (returnType != null && isRibbon(returnType.getFullyQualifiedName())) {
                    return SearchResult.found(m,
                            "Ribbon return type detected; replace the policy bean with a Spring Cloud LoadBalancer strategy after mapping behavior");
                }
                return m;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(c.getType());
                if (type != null && type.getInterfaces().stream().map(JavaType.FullyQualified::getFullyQualifiedName)
                        .anyMatch(FindEurekaClientJavaMigrationRisks::isCustomEurekaSpi)) {
                    return SearchResult.found(c,
                            "Custom Eureka client/config/health/transport SPI detected; compile against 4.2.0 and retest lifecycle, TLS, timeouts and registration status");
                }
                return c;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                JavaType.FullyQualified owner = methodType == null ? null : TypeUtils.asFullyQualified(methodType.getDeclaringType());
                String fqn = owner == null ? "" : owner.getFullyQualifiedName();
                if ((fqn.equals("com.netflix.discovery.EurekaClient") ||
                     fqn.equals("com.netflix.discovery.DiscoveryClient") ||
                     fqn.equals("org.springframework.cloud.netflix.eureka.CloudEurekaClient")) &&
                    EUREKA_LIFECYCLE_METHODS.contains(m.getSimpleName())) {
                    return SearchResult.found(m,
                            "Direct Eureka client lifecycle/registry API detected; retest initialization, refresh, empty registry, shutdown and transport failures");
                }
                if (fqn.equals("com.netflix.appinfo.ApplicationInfoManager") &&
                    INSTANCE_METHODS.contains(m.getSimpleName())) {
                    return SearchResult.found(m,
                            "Manual instance status affects registration and health propagation; verify Actuator health integration and shutdown/drain ordering");
                }
                if (fqn.startsWith("org.springframework.cloud.netflix.eureka.http.")) {
                    return SearchResult.found(m,
                            "Custom Eureka HTTP transport API detected; select and test RestTemplate, RestClient, WebClient or Jersey with proxy/TLS/timeouts");
                }
                return m;
            }
        };
    }

    private static boolean isRibbon(String fqn) {
        return fqn.startsWith("org.springframework.cloud.netflix.ribbon.") ||
               fqn.startsWith("com.netflix.loadbalancer.");
    }

    private static boolean isCustomEurekaSpi(String fqn) {
        return fqn.equals("com.netflix.appinfo.HealthCheckHandler") ||
               fqn.equals("com.netflix.discovery.EurekaClientConfig") ||
               fqn.equals("com.netflix.appinfo.EurekaInstanceConfig") ||
               fqn.equals("com.netflix.discovery.shared.transport.jersey.TransportClientFactories");
    }
}
