package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Applies deterministic Flyway property namespace and location migrations. */
public final class MigrateFlywayProperties extends Recipe {
    private static final Map<String, String> EXACT_KEYS = new LinkedHashMap<>();
    private static final Map<String, String> PREFIXES = new LinkedHashMap<>();

    static {
        EXACT_KEYS.put("flyway.check.reportFilename", "flyway.reportFilename");
        EXACT_KEYS.put("flyway.oracleKerberosConfigFile", "flyway.kerberosConfigFile");
        EXACT_KEYS.put("spring.flyway.oracle-kerberos-config-file", "spring.flyway.kerberos-config-file");
        EXACT_KEYS.put("flyway.plugins.clean", "flyway.sqlserver.clean.mode");
        EXACT_KEYS.put("flyway.plugins.clean.schemas.exclude", "flyway.sqlserver.clean.schemas.exclude");
        PREFIXES.put("flyway.plugins.vault.", "flyway.vault.");
        PREFIXES.put("flyway.plugins.dapr.", "flyway.dapr.");
        PREFIXES.put("flyway.plugins.gcsm.", "flyway.gcsm.");
    }

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Flyway properties";
    }

    @Override
    public String getDescription() {
        return "Rename removed/deprecated Flyway configuration namespaces and make unprefixed Flyway migration locations explicitly classpath-based.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Properties.File file) || !(tree instanceof SourceFile source) ||
                    !FlywayVersions.isProjectPath(source.getSourcePath())) return tree;
                return new PropertiesIsoVisitor<ExecutionContext>() {
                    @Override
                    public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                        Properties.Entry e = super.visitEntry(entry, p);
                        String key = migrateKey(e.getKey());
                        if (!key.equals(e.getKey())) e = e.withKey(key);
                        if ("flyway.locations".equals(key) || "spring.flyway.locations".equals(key)) {
                            String value = e.getValue().getText();
                            String normalized = normalizeLocations(value);
                            if (!normalized.equals(value)) e = e.withValue(e.getValue().withText(normalized));
                        }
                        return e;
                    }
                }.visitNonNull(file, ctx);
            }
        };
    }

    static String migrateKey(String key) {
        String exact = EXACT_KEYS.get(key);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> prefix : PREFIXES.entrySet()) {
            if (key.startsWith(prefix.getKey())) {
                return prefix.getValue() + key.substring(prefix.getKey().length());
            }
        }
        return key;
    }

    static String normalizeLocations(String locations) {
        return Arrays.stream(locations.split(",", -1)).map(String::trim).map(location ->
                location.isEmpty() || location.contains(":") || location.contains("${")
                        ? location : "classpath:" + location).collect(Collectors.joining(","));
    }
}
