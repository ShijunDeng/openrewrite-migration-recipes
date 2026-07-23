package com.huawei.clouds.openrewrite.springbootactuator;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Structured configuration markers for behavior-sensitive Actuator settings. */
public final class FindActuatorConfigurationRisks extends Recipe {
    static final String ACCESS =
            "Actuator access and exposure are separate in Boot 3.4+; verify access={none,read-only,unrestricted}, web/JMX exposure, authorization, and the max-permitted cap together";
    static final String MANAGEMENT_SERVER =
            "A separate management server is configured; verify port/address, SSL bundles, access log, base path, firewall, probes, and platform routing after Boot 3.5";
    static final String HEALTH =
            "Health/probe configuration detected; verify liveness/readiness groups, additional paths, contributor membership, detail roles, and platform startup semantics";
    static final String SANITIZATION =
            "env/configprops/quartz values are sanitized by default in Boot 3; review show-values and roles without weakening production secrecy";
    static final String METRICS =
            "Metrics exporter or binder configuration detected; verify the Boot 3 namespace, Micrometer 1.15 defaults, credentials, meter filters, histograms, and backend payloads";
    static final String PUSHGATEWAY =
            "Boot 3.5 replaced Prometheus Pushgateway base-url with address and requires a host:port value plus the new client dependency; this cannot be a key-only rewrite";
    static final String HEAPDUMP =
            "Boot 3.5 defaults heapdump access to none; exposing it now also requires explicit access and strong authorization, network, and data-handling controls";
    static final String JSON =
            "Actuator uses an isolated ObjectMapper by default in Boot 3; verify custom endpoint serialization before changing isolated-object-mapper";
    static final String REMOVED_OBSERVATIONS =
            "micrometer.observations.annotations.enabled was removed in Boot 3.5; use management.observations.annotations.enabled and verify annotation scanning behavior";
    static final String HTTP_EXCHANGES =
            "Boot 3 replaced the httptrace endpoint and HttpTrace model with httpexchanges; migrate the endpoint ID, repository, payload assertions, retention, and privacy controls together";
    static final String LEGACY_ENABLED =
            "This endpoint enabled property was not a literal boolean and could not be converted automatically; choose none, read-only, or unrestricted explicitly";
    static final String JMX =
            "Only health is exposed over JMX by default in Boot 3; verify include/exclude, object names, authentication, and remote connector hardening";

    @Override
    public String getDisplayName() {
        return "Find Spring Boot Actuator 3.5 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact access, exposure, management port, health, sanitization, metrics, heapdump, " +
               "JSON, observations, and JMX configuration nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringBootActuatorSupport.generated(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry e = super.visitEntry(entry, ec);
                String message = message(normalize(e.getKey()), e.getValue().getText());
                return message == null ? e : SpringBootActuatorSupport.mark(e, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ec);
                if (e.getValue() instanceof Yaml.Mapping) return e;
                String message = message(normalize(path()), e.getValue().printTrimmed(getCursor()));
                return message == null ? e : SpringBootActuatorSupport.mark(e, message);
            }

            private String path() {
                List<String> keys = new ArrayList<>();
                getCursor().getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                        .map(Yaml.Mapping.Entry.class::cast)
                        .forEach(entry -> keys.add(entry.getKey().getValue()));
                Collections.reverse(keys);
                return String.join(".", keys);
            }
        }.visitNonNull(source, ctx);
    }

    static String message(String key, String rawValue) {
        String value = rawValue == null ? "" : rawValue.toLowerCase(Locale.ROOT);
        if ("management.prometheus.metrics.export.pushgateway.base-url".equals(key)) return PUSHGATEWAY;
        if ("micrometer.observations.annotations.enabled".equals(key)) return REMOVED_OBSERVATIONS;
        if (key.startsWith("management.trace.http.") ||
            key.startsWith("management.endpoint.httptrace.")) return HTTP_EXCHANGES;
        if (key.matches("management\\.endpoint\\.[^.]+\\.enabled") ||
            "management.endpoints.enabled-by-default".equals(key)) return LEGACY_ENABLED;
        if (key.startsWith("management.server.")) return MANAGEMENT_SERVER;
        if (key.startsWith("management.endpoint.health.") ||
            key.startsWith("management.health.") ||
            key.startsWith("management.endpoint.readiness.") ||
            key.startsWith("management.endpoint.liveness.")) return HEALTH;
        if (key.matches("management\\.endpoint\\.(env|configprops|quartz)\\.show-values") ||
            key.matches("management\\.endpoint\\.(env|configprops|quartz)\\.roles")) return SANITIZATION;
        if ("management.endpoints.jackson.isolated-object-mapper".equals(key)) return JSON;
        if (key.startsWith("management.metrics.") ||
            key.matches("management\\.[^.]+\\.metrics\\.export(?:\\..+)?")) return METRICS;
        if (key.startsWith("management.endpoints.jmx.")) return JMX;
        if (key.matches("management\\.endpoint\\.[^.]+\\.access") ||
            key.startsWith("management.endpoints.access.") ||
            key.startsWith("management.endpoints.web.exposure.")) {
            if (key.endsWith(".access") || key.startsWith("management.endpoints.access.")) {
                String access = value.trim();
                if (!"none".equals(access) && !"read-only".equals(access) &&
                    !"unrestricted".equals(access)) {
                    return LEGACY_ENABLED;
                }
            }
            if ((key.endsWith(".include") || key.endsWith(".exclude")) && value.contains("heapdump")) {
                return HEAPDUMP;
            }
            return ACCESS;
        }
        return null;
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
