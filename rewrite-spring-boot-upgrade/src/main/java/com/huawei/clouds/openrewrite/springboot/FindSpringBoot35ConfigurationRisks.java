package com.huawei.clouds.openrewrite.springboot;

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

/** Structured markers for Spring Boot defaults that cannot be preserved by a key rename alone. */
public final class FindSpringBoot35ConfigurationRisks extends Recipe {
    static final String CONFIG_DATA =
            "Config Data/profile processing is application-order sensitive; verify imports, activation, multi-document precedence, mounted secrets, and bootstrap behavior";
    static final String CIRCULAR =
            "Circular references are prohibited by default; remove the bean cycle rather than relying permanently on spring.main.allow-circular-references";
    static final String WEB =
            "Web routing/resource/forwarded-header behavior is configured; verify PathPattern, trailing slash, 404/ProblemDetail, proxy headers, and static resources on Spring Framework 6.2";
    static final String HEADER =
            "server.max-http-request-header-size limits request headers only; response header limits require a container-specific customizer";
    static final String SHUTDOWN =
            "Graceful shutdown configuration detected; verify lifecycle phases, readiness, timeout, in-flight work, and orchestrator termination timing";
    static final String LOGGING =
            "Custom logging date format or Logback configuration detected; verify ISO-8601 defaults, correlation IDs, structured logging, and rollover behavior";
    static final String SQL =
            "SQL initialization or open-in-view behavior is configured; verify initialization order, deferred repositories, transaction boundaries, and production defaults";
    static final String VIRTUAL_THREADS =
            "Virtual threads require a compatible JDK and may change thread-pool tuning, ThreadLocal assumptions, pinning, metrics, and graceful shutdown";
    static final String ACTUATOR =
            "Actuator access/exposure configuration detected; Boot 3.4/3.5 separates access from exposure and changed sensitive endpoint defaults";

    @Override
    public String getDisplayName() {
        return "Find Spring Boot 3.5 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact Config Data, bean-cycle, web, shutdown, logging, SQL, virtual-thread, and Actuator " +
               "nodes whose behavior requires application-owned regression testing.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringBootSupport.generated(source.getSourcePath())) return tree;
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
                String message = message(normalize(e.getKey()));
                return message == null ? e : SpringBootSupport.mark(e, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ec);
                if (e.getValue() instanceof Yaml.Mapping) return e;
                String message = message(normalize(path()));
                return message == null ? e : SpringBootSupport.mark(e, message);
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

    static String message(String key) {
        if (key.startsWith("spring.config.import") ||
            key.startsWith("spring.config.activate.") ||
            "spring.profiles".equals(key) ||
            key.startsWith("spring.profiles.")) return CONFIG_DATA;
        if ("spring.main.allow-circular-references".equals(key)) return CIRCULAR;
        if (key.startsWith("spring.mvc.") || key.startsWith("spring.web.resources.") ||
            "server.forward-headers-strategy".equals(key)) return WEB;
        if ("server.max-http-request-header-size".equals(key) ||
            "server.max-http-header-size".equals(key)) return HEADER;
        if ("server.shutdown".equals(key) ||
            key.startsWith("spring.lifecycle.timeout-per-shutdown-phase")) return SHUTDOWN;
        if (key.startsWith("logging.pattern.") || key.startsWith("logging.structured.") ||
            key.startsWith("logging.logback.rollingpolicy.")) return LOGGING;
        if (key.startsWith("spring.sql.init.") || key.startsWith("spring.datasource.initialization-") ||
            "spring.jpa.open-in-view".equals(key) ||
            "spring.data.jpa.repositories.bootstrap-mode".equals(key)) return SQL;
        if ("spring.threads.virtual.enabled".equals(key)) return VIRTUAL_THREADS;
        if (key.startsWith("management.endpoint.") || key.startsWith("management.endpoints.")) return ACTUATOR;
        return null;
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
