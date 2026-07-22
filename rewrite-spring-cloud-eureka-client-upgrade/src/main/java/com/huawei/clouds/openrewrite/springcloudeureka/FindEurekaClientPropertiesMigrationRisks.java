package com.huawei.clouds.openrewrite.springcloudeureka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.Locale;

/** Precise review markers for Eureka properties configuration. */
public final class FindEurekaClientPropertiesMigrationRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Eureka Client 4.2 properties risks";
    }

    @Override
    public String getDescription() {
        return "Mark bootstrap, registration/health, TLS/auth, zone/region, lease/heartbeat, refresh, network " +
               "identity, HTTP transport, and native/AOT-sensitive Eureka properties.";
    }

    @Override
    public PropertiesIsoVisitor<ExecutionContext> getVisitor() {
        return new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                Properties.Entry e = super.visitEntry(entry, ctx);
                String key = e.getKey().toLowerCase(Locale.ROOT);
                String value = e.getValue().getText().trim();
                Properties.File file = getCursor().firstEnclosing(Properties.File.class);
                String path = file == null ? "" :
                        file.getSourcePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                String message = risk(key, value, path.contains("bootstrap"));
                return message == null ? e : SearchResult.found(e, message);
            }
        };
    }

    static String risk(String key, String value, boolean bootstrapFile) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "-");
        String lowerValue = value.toLowerCase(Locale.ROOT);
        if (bootstrapFile && (normalized.startsWith("eureka.") || normalized.startsWith("spring.cloud.config."))) {
            if (normalized.contains("healthcheck.enabled")) {
                return "Do not enable Eureka healthcheck in bootstrap configuration; it can register UNKNOWN before Actuator is ready";
            }
            return "Legacy bootstrap configuration detected; choose Config Data or explicitly add and test spring-cloud-starter-bootstrap";
        }
        if (normalized.equals("spring.cloud.bootstrap.enabled") || normalized.equals("spring.config.import") ||
            normalized.startsWith("spring.cloud.config.discovery.")) {
            return "Review bootstrap versus Config Data ordering, discovery-first Config Client service ID, credentials and startup network calls";
        }
        if (normalized.contains("serviceurl") || normalized.contains("service-url") ||
            normalized.contains("tls.") || normalized.contains("proxy-")) {
            if (lowerValue.contains("@") || normalized.contains("tls.") || lowerValue.startsWith("https:")) {
                return "Review Eureka TLS/auth/proxy secrets, certificate stores, hostname verification and externally reachable registry URLs";
            }
            return "Verify defaultZone casing, /eureka/ path, region/zone fallback, DNS/proxy reachability and multi-server behavior";
        }
        if (containsAny(normalized, "register-with-eureka", "fetch-registry", "healthcheck.enabled",
                "status-page-url", "health-check-url", "home-page-url", "auto-registration")) {
            return "Verify registration/fetch flags, Actuator health propagation and registry status/health/home URLs during startup and shutdown";
        }
        if (containsAny(normalized, "availability-zones", ".region", "prefer-same-zone-eureka", "metadata-map.zone")) {
            return "Verify Eureka region/zone metadata and fallback against Spring Cloud LoadBalancer zone selection";
        }
        if (containsAny(normalized, "lease-renewal", "lease-expiration", "registry-fetch-interval",
                "heartbeat-executor", "cache-refresh-executor")) {
            return "Retest heartbeat, lease expiration, registry fetch, eviction and self-preservation; sample test intervals are unsafe production defaults";
        }
        if (normalized.equals("eureka.client.refresh.enable") || normalized.equals("spring.cloud.refresh.enabled")) {
            return "Refresh can temporarily unregister Eureka; AOT/native requires spring.cloud.refresh.enabled=false";
        }
        if (containsAny(normalized, "prefer-ip-address", "ip-address", ".hostname", "instance-id",
                "default-address-resolution-order")) {
            return "Verify advertised hostname/IP/instance ID from every interface, container, NAT and IPv4/IPv6 environment";
        }
        if (containsAny(normalized, "restclient.enabled", "webclient.enabled", "jersey.enabled",
                "rest-template-timeout", "restclient.timeout")) {
            return "Select one supported Eureka HTTP transport and retest TLS, proxy, connection pool and timeout behavior";
        }
        if ((normalized.equals("server.port") && "0".equals(value.trim())) || normalized.contains("aot") ||
            normalized.contains("native")) {
            return "Eureka native/AOT does not support a random client port; keep build-time service IDs/config and runtime values consistent";
        }
        return null;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
