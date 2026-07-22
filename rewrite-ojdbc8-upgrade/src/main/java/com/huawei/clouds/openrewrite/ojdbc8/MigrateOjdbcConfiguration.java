package com.huawei.clouds.openrewrite.ojdbc8;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Locale;
import java.util.regex.Pattern;

/** Update the exact legacy driver name and mark environment-owned Oracle connection configuration. */
public final class MigrateOjdbcConfiguration extends Recipe {
    private static final Pattern SID_URL = Pattern.compile("jdbc:oracle:thin:(?:[^@]*@)?[^/(;?]+:[0-9]+:[^/;?]+.*");
    private static final String URL_MESSAGE =
            "Validate this Oracle JDBC URL against the deployed 23.26.1 driver: service/SID or TNS resolution, failover/load balancing, timeouts, TCPS/DN checks, wallet material, escaping, and container paths are environment-owned";
    private static final String TNS_MESSAGE =
            "Validate TNS_ADMIN and tnsnames/LDAP/provider lookup with ojdbc8 23.26.1, including packaging, filesystem permissions, container/native-image paths, rotation, and module/classloader visibility";
    private static final String WALLET_MESSAGE =
            "Validate Oracle wallet/TLS configuration with aligned oraclepki, trust/key material, hostname and server-DN checks, secret handling, provider registration, rotation, and file permissions";
    private static final String UCP_MESSAGE =
            "Align UCP/ONS companions with ojdbc8 23.26.1 and verify pool lifecycle, validation, labeling, FAN, replay, planned draining, credentials, metrics, and shutdown ownership";

    @Override
    public String getDisplayName() {
        return "Migrate Oracle JDBC configuration";
    }

    @Override
    public String getDescription() {
        return "Replace only the exact deprecated Oracle driver implementation class name in parsed configuration, " +
               "and mark JDBC URL, TNS, wallet/TLS, and UCP settings that require deployment validation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    !UpgradeSelectedOjdbc8Dependency.isProjectPath(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) return migrateProperties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return migrateYaml(yaml, ctx);
                String name = source.getSourcePath().getFileName() == null ? "" :
                        source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(name)) return migrateXml(xml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File migrateProperties(Properties.File file, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                Properties.Entry visited = super.visitEntry(entry, p);
                String value = visited.getValue().getText();
                if (ReplaceLegacyOracleDriverString.OLD_DRIVER.equals(value.trim())) {
                    visited = visited.withValue(visited.getValue().withText(
                            value.replace(ReplaceLegacyOracleDriverString.OLD_DRIVER,
                                    ReplaceLegacyOracleDriverString.NEW_DRIVER)));
                    value = visited.getValue().getText();
                }
                String message = message(visited.getKey(), value);
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(file, ctx);
    }

    private static Yaml.Documents migrateYaml(Yaml.Documents documents, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext p) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, p);
                if (!(visited.getValue() instanceof Yaml.Scalar scalar)) return visited;
                String value = scalar.getValue();
                if (ReplaceLegacyOracleDriverString.OLD_DRIVER.equals(value.trim())) {
                    scalar = scalar.withValue(ReplaceLegacyOracleDriverString.NEW_DRIVER);
                    visited = visited.withValue(scalar);
                    value = scalar.getValue();
                }
                String message = message(visited.getKey().getValue(), value);
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(documents, ctx);
    }

    private static Xml.Document migrateXml(Xml.Document document, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                Xml.Tag visited = super.visitTag(tag, p);
                String value = visited.getValue().orElse("");
                if (ReplaceLegacyOracleDriverString.OLD_DRIVER.equals(value.trim())) {
                    visited = visited.withValue(ReplaceLegacyOracleDriverString.NEW_DRIVER);
                    value = ReplaceLegacyOracleDriverString.NEW_DRIVER;
                }
                String message = message(visited.getName(), value);
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext p) {
                Xml.Attribute visited = super.visitAttribute(attribute, p);
                String value = visited.getValueAsString();
                if (ReplaceLegacyOracleDriverString.OLD_DRIVER.equals(value.trim())) {
                    visited = visited.withValue(visited.getValue().withValue(ReplaceLegacyOracleDriverString.NEW_DRIVER));
                    value = ReplaceLegacyOracleDriverString.NEW_DRIVER;
                }
                String message = message(visited.getKeyAsString(), value);
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(document, ctx);
    }

    private static String message(String key, String value) {
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT).replace('_', '-');
        String normalizedValue = value == null ? "" : value.trim();
        String lowerValue = normalizedValue.toLowerCase(Locale.ROOT);
        if (normalizedValue.startsWith("jdbc:oracle:") &&
            (normalizedValue.startsWith("jdbc:oracle:oci:") || normalizedValue.contains("(DESCRIPTION=") ||
             normalizedValue.contains("(description=") || SID_URL.matcher(normalizedValue).matches() ||
             normalizedValue.startsWith("jdbc:oracle:thin:@") && !normalizedValue.startsWith("jdbc:oracle:thin:@//") ||
             lowerValue.contains("tns_admin") || lowerValue.contains("tcps") || lowerValue.contains("wallet"))) {
            return URL_MESSAGE;
        }
        if (normalizedKey.contains("tns-admin") || normalizedKey.contains("tnsnames") ||
            normalizedKey.startsWith("oracle.net.")) return TNS_MESSAGE;
        if (normalizedKey.contains("wallet") || normalizedKey.contains("truststore") ||
            normalizedKey.contains("keystore") || normalizedKey.contains("ssl-server-dn-match")) return WALLET_MESSAGE;
        if (normalizedKey.contains("ucp") || normalizedKey.contains("connectionfactoryclassname") ||
            normalizedKey.contains("fastconnectionfailoverenabled") || normalizedKey.contains("fan-enabled")) {
            return UCP_MESSAGE;
        }
        return null;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
