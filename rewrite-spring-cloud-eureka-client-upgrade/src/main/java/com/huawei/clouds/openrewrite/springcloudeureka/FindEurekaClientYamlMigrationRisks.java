package com.huawei.clouds.openrewrite.springcloudeureka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Precise review markers for Eureka YAML configuration. */
public final class FindEurekaClientYamlMigrationRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Eureka Client 4.2 YAML risks";
    }

    @Override
    public String getDescription() {
        return "Mark bootstrap, registration/health, TLS/auth, zone/region, lease/heartbeat, refresh, network " +
               "identity, HTTP transport, and native/AOT-sensitive Eureka YAML entries.";
    }

    @Override
    public YamlIsoVisitor<ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                if (!(e.getValue() instanceof Yaml.Scalar scalar)) {
                    return e;
                }
                String property = propertyPath();
                String value = scalar.getValue();
                Yaml.Documents documents = getCursor().firstEnclosing(Yaml.Documents.class);
                String path = documents == null ? "" :
                        documents.getSourcePath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                String message = FindEurekaClientPropertiesMigrationRisks.risk(
                        property, value, path.contains("bootstrap"));
                return message == null ? e : SearchResult.found(e, message);
            }

            private String propertyPath() {
                List<String> keys = new ArrayList<>();
                getCursor().getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                        .map(Yaml.Mapping.Entry.class::cast)
                        .forEach(entry -> keys.add(entry.getKey().getValue()));
                Collections.reverse(keys);
                return String.join(".", keys);
            }
        };
    }
}
