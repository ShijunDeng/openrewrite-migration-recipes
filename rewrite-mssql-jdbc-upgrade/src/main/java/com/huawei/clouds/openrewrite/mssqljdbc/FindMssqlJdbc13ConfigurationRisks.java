package com.huawei.clouds.openrewrite.mssqljdbc;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Locale;
import java.util.Set;

/** Format-aware markers for SQL Server JDBC connection and native-library configuration. */
public final class FindMssqlJdbc13ConfigurationRisks extends Recipe {
    private static final Set<String> CONNECTION_KEYS = Set.of(
            "encrypt", "trustservercertificate", "truststore", "truststorepassword",
            "hostnameincertificate", "servercertificate", "sslprotocol", "logintimeout", "sockettimeout",
            "authentication", "authenticationscheme", "aadsecureprincipalid", "aadsecureprincipalsecret",
            "accesstokencallbackclass", "msiclientid", "columnencryptionsetting", "enclaveattestationurl",
            "enclaveattestationprotocol", "keyvaultproviderclientid", "keyvaultproviderclientkey",
            "vectortypesupport", "quotedidentifier", "concatnullyieldsnull"
    );
    private static final String URL_MESSAGE =
            "SQL Server JDBC URL detected; retest 13.2 TLS/encrypt/certificate policy, 30-second login timeout, Entra authentication, failover and pooling";
    private static final String PROPERTY_MESSAGE =
            "SQL Server JDBC connection property detected; review its 13.2 TLS, timeout, authentication, Always Encrypted, vector or pooled-session semantics";
    private static final String NATIVE_MESSAGE =
            "Native SQL Server authentication library detected; deploy the matching 13.2 OS/architecture binary and retest Kerberos, NTLM and integrated security";

    @Override
    public String getDisplayName() {
        return "Find Microsoft SQL Server JDBC 13.2 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark complete SQL Server JDBC URLs, context-owned connection settings, and native authentication libraries in parsed configuration formats.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !UpgradeSelectedMssqlJdbcDependency.isProjectPath(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) return markProperties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return markYaml(yaml, ctx);
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(
                        source.getSourcePath().getFileName().toString())) return markXml(xml, ctx);
                if (tree instanceof PlainText text && isSupportedText(source)) return markText(text);
                return tree;
            }
        };
    }

    private static Properties.File markProperties(Properties.File source, ExecutionContext ctx) {
        boolean sqlContext = hasPropertiesSqlContext(source, ctx);
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext executionContext) {
                Properties.Entry visited = super.visitEntry(entry, executionContext);
                String value = visited.getValue().getText();
                if (hasJdbcUrl(value)) return mark(visited, URL_MESSAGE);
                if (hasNativeLibrary(value)) return mark(visited, NATIVE_MESSAGE);
                return sqlContext && isQualifiedConnectionKey(visited.getKey()) ?
                        mark(visited, PROPERTY_MESSAGE) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents markYaml(Yaml.Documents source, ExecutionContext ctx) {
        boolean sqlContext = hasYamlSqlContext(source, ctx);
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry,
                                                         ExecutionContext executionContext) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, executionContext);
                String value = visited.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue() : "";
                if (hasJdbcUrl(value)) return mark(visited, URL_MESSAGE);
                if (hasNativeLibrary(value)) return mark(visited, NATIVE_MESSAGE);
                return sqlContext && isConnectionKey(visited.getKey().getValue()) && hasStructuredOwner(getCursor()) ?
                        mark(visited, PROPERTY_MESSAGE) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document markXml(Xml.Document source, ExecutionContext ctx) {
        boolean sqlContext = hasXmlSqlContext(source, ctx);
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                Xml.Tag visited = super.visitTag(tag, executionContext);
                String value = visited.getValue().orElse("");
                if (hasJdbcUrl(value)) return mark(visited, URL_MESSAGE);
                if (hasNativeLibrary(value)) return mark(visited, NATIVE_MESSAGE);
                return sqlContext && isConnectionKey(visited.getName()) && hasStructuredOwner(getCursor()) ?
                        mark(visited, PROPERTY_MESSAGE) : visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                Xml.Attribute visited = super.visitAttribute(attribute, executionContext);
                String value = visited.getValueAsString();
                if (hasJdbcUrl(value)) return mark(visited, URL_MESSAGE);
                if (hasNativeLibrary(value)) return mark(visited, NATIVE_MESSAGE);
                return sqlContext && isConnectionKey(visited.getKeyAsString()) && hasStructuredOwner(getCursor()) ?
                        mark(visited, PROPERTY_MESSAGE) : visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static PlainText markText(PlainText source) {
        String value = source.getText();
        if (hasJdbcUrl(value)) return mark(source, URL_MESSAGE);
        return hasNativeLibrary(value) ? mark(source, NATIVE_MESSAGE) : source;
    }

    private static boolean hasSqlContext(String value) {
        return hasJdbcUrl(value) || hasNativeLibrary(value);
    }

    private static boolean hasPropertiesSqlContext(Properties.File source, ExecutionContext ctx) {
        boolean[] found = {false};
        new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext executionContext) {
                if (hasSqlContext(entry.getValue().getText())) found[0] = true;
                return found[0] ? entry : super.visitEntry(entry, executionContext);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean hasYamlSqlContext(Yaml.Documents source, ExecutionContext ctx) {
        boolean[] found = {false};
        new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext executionContext) {
                if (hasSqlContext(scalar.getValue())) found[0] = true;
                return found[0] ? scalar : super.visitScalar(scalar, executionContext);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean hasXmlSqlContext(Xml.Document source, ExecutionContext ctx) {
        boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                if (hasSqlContext(charData.getText())) found[0] = true;
                return found[0] ? charData : super.visitCharData(charData, executionContext);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                if (hasSqlContext(attribute.getValueAsString())) found[0] = true;
                return found[0] ? attribute : super.visitAttribute(attribute, executionContext);
            }
        }.visit(source, ctx);
        return found[0];
    }

    private static boolean hasJdbcUrl(String value) {
        return value.toLowerCase(Locale.ROOT).contains("jdbc:sqlserver:");
    }

    private static boolean hasNativeLibrary(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("mssql-jdbc_auth") || lower.contains("sqljdbc_auth");
    }

    private static boolean isConnectionKey(String value) {
        String key = value;
        int dot = key.lastIndexOf('.');
        if (dot >= 0) key = key.substring(dot + 1);
        return CONNECTION_KEYS.contains(key.replace("-", "").replace("_", "")
                .toLowerCase(Locale.ROOT));
    }

    private static boolean isQualifiedConnectionKey(String value) {
        if (!isConnectionKey(value)) return false;
        int dot = value.lastIndexOf('.');
        return dot > 0 && isOwnerName(value.substring(0, dot));
    }

    private static boolean hasStructuredOwner(Cursor cursor) {
        return cursor.getPathAsStream().anyMatch(value ->
                value instanceof Yaml.Mapping.Entry entry && isOwnerName(entry.getKey().getValue()) ||
                value instanceof Xml.Tag tag && isOwnerName(tag.getName()));
    }

    private static boolean isOwnerName(String value) {
        String normalized = value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        return normalized.contains("datasource") || normalized.contains("sqlserver") ||
               normalized.contains("mssql") || normalized.contains("jdbc");
    }

    private static boolean isSupportedText(SourceFile source) {
        String name = source.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".conf") || name.endsWith(".env") || name.endsWith(".json") ||
               name.endsWith(".sh") || name.endsWith(".bat") || name.endsWith(".ps1") ||
               name.startsWith("dockerfile");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
