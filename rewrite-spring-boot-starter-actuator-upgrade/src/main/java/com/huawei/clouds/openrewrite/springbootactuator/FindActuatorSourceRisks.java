package com.huawei.clouds.openrewrite.springbootactuator;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/** Type-aware and exact-import markers for Actuator behavior that cannot be changed safely in isolation. */
public final class FindActuatorSourceRisks extends Recipe {
    private static final Set<String> TRACE_PREFIXES = Set.of(
            "org.springframework.boot.actuate.trace.http.",
            "org.springframework.boot.actuate.web.trace.");
    private static final Set<String> ENDPOINT_TYPES = Set.of(
            "org.springframework.boot.actuate.endpoint.annotation.Endpoint",
            "org.springframework.boot.actuate.endpoint.annotation.ReadOperation",
            "org.springframework.boot.actuate.endpoint.annotation.WriteOperation",
            "org.springframework.boot.actuate.endpoint.annotation.DeleteOperation",
            "org.springframework.boot.actuate.endpoint.web.EndpointServlet",
            "org.springframework.boot.actuate.endpoint.OperationResponseBody",
            "org.springframework.boot.actuate.endpoint.SanitizingFunction");
    private static final Set<String> HEALTH_TYPES = Set.of(
            "org.springframework.boot.actuate.health.HealthIndicator",
            "org.springframework.boot.actuate.health.ReactiveHealthIndicator",
            "org.springframework.boot.actuate.health.HealthContributor",
            "org.springframework.boot.actuate.health.CompositeHealthContributor",
            "org.springframework.boot.actuate.health.StatusAggregator",
            "org.springframework.boot.actuate.health.HttpCodeStatusMapper");
    private static final Set<String> SECURITY_TYPES = Set.of(
            "org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest",
            "org.springframework.boot.actuate.autoconfigure.security.reactive.EndpointRequest",
            "org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter",
            "org.springframework.security.web.SecurityFilterChain",
            "org.springframework.security.web.server.SecurityWebFilterChain");

    static final String HTTP_EXCHANGES =
            "Boot 3 renamed httptrace to httpexchanges and changed its response/infrastructure model; migrate HttpTraceRepository, filters, endpoint IDs, payload assertions, retention, and privacy controls together";
    static final String CUSTOM_ENDPOINT =
            "Custom Actuator endpoint API detected; verify endpoint access/defaultAccess, operation parameters, isolated JSON serialization, JMX/web exposure, and native reflection behavior";
    static final String HEALTH =
            "Custom health contribution detected; verify status aggregation, groups, probes/additional paths, detail authorization, timeouts, and readiness/liveness semantics";
    static final String SECURITY =
            "Custom Actuator security detected; Boot backs off when a SecurityFilterChain is supplied, and Security 6 removed WebSecurityConfigurerAdapter; regression-test every endpoint and dispatcher type";
    static final String MICROMETER =
            "Micrometer customization detected; Boot 3.5 uses Micrometer 1.15 and changed observations/exporters; verify meter names, tags, filters, histograms, tracing correlation, and duplicate binders";
    static final String JVM_INFO =
            "JvmInfoMetrics is auto-configured in Boot 3; remove or condition the manual bean to prevent duplicate meter registration";
    static final String JACKSON =
            "A custom Actuator endpoint and Jackson customization coexist; Boot 3 uses an isolated endpoint ObjectMapper unless management.endpoints.jackson.isolated-object-mapper=false";
    static final String JAKARTA =
            "A remaining Java EE javax API needs an explicit Jakarta decision; do not globally rewrite Java SE javax packages such as annotation.processing, crypto, sql, naming, or transaction.xa";
    static final String CLOUD_FOUNDRY =
            "EndpointExposure.CLOUD_FOUNDRY is deprecated in Boot 3.4; use WEB only after verifying platform-specific endpoint exposure behavior";

    @Override
    public String getDisplayName() {
        return "Find Spring Boot Actuator 3.5 source risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact http exchange, custom endpoint, health, security, Micrometer, JSON, Jakarta, " +
               "and endpoint-exposure source boundaries.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (SpringBootActuatorSupport.generated(compilationUnit.getSourcePath())) return compilationUnit;
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import i = super.visitImport(anImport, ctx);
                String type = i.getQualid().printTrimmed(getCursor()).replace(".*", "");
                if (TRACE_PREFIXES.stream().anyMatch(type::startsWith)) {
                    return SpringBootActuatorSupport.mark(i, HTTP_EXCHANGES);
                }
                if (ENDPOINT_TYPES.contains(type)) {
                    return SpringBootActuatorSupport.mark(i, CUSTOM_ENDPOINT);
                }
                if (HEALTH_TYPES.contains(type)) {
                    return SpringBootActuatorSupport.mark(i, HEALTH);
                }
                if (SECURITY_TYPES.contains(type)) {
                    return SpringBootActuatorSupport.mark(i, SECURITY);
                }
                if ("io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics".equals(type)) {
                    return SpringBootActuatorSupport.mark(i, JVM_INFO);
                }
                if (type.startsWith("io.micrometer.")) {
                    return SpringBootActuatorSupport.mark(i, MICROMETER);
                }
                if (actuatorCompilationUnit() &&
                    ("com.fasterxml.jackson.databind.ObjectMapper".equals(type) ||
                     type.startsWith("com.fasterxml.jackson.databind.module."))) {
                    return SpringBootActuatorSupport.mark(i, JACKSON);
                }
                if (remainingJakartaCandidate(type)) {
                    return SpringBootActuatorSupport.mark(i, JAKARTA);
                }
                return i;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                String type = TypeUtils.asFullyQualified(a.getType()) == null ? "" :
                        TypeUtils.asFullyQualified(a.getType()).getFullyQualifiedName();
                if ("org.springframework.boot.actuate.endpoint.annotation.Endpoint".equals(type) &&
                    a.getArguments() != null && a.getArguments().stream()
                            .anyMatch(argument -> argument.printTrimmed(getCursor())
                                    .startsWith("enableByDefault"))) {
                    return SpringBootActuatorSupport.mark(a, CUSTOM_ENDPOINT);
                }
                return a;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);
                if ("CLOUD_FOUNDRY".equals(f.getSimpleName()) &&
                    f.getTarget().printTrimmed(getCursor()).endsWith("EndpointExposure")) {
                    return SpringBootActuatorSupport.mark(f, CLOUD_FOUNDRY);
                }
                return f;
            }

            private boolean actuatorCompilationUnit() {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                return cu != null && cu.getImports().stream()
                        .map(anImport -> anImport.getQualid().printTrimmed(getCursor()))
                        .anyMatch(name -> name.startsWith("org.springframework.boot.actuate."));
            }
        };
    }

    private static boolean remainingJakartaCandidate(String type) {
        if (type.startsWith("javax.annotation.")) {
            return !type.startsWith("javax.annotation.processing.");
        }
        if (type.startsWith("javax.transaction.")) {
            return !type.startsWith("javax.transaction.xa.");
        }
        return type.startsWith("javax.ejb.") || type.startsWith("javax.enterprise.") ||
               type.startsWith("javax.faces.") || type.startsWith("javax.interceptor.") ||
               type.startsWith("javax.jms.") || type.startsWith("javax.json.") ||
               type.startsWith("javax.resource.") || type.startsWith("javax.websocket.");
    }
}
