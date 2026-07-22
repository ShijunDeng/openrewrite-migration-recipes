package com.huawei.clouds.openrewrite.mssqljdbc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies Microsoft's exact 12.2 driver authentication value rename in typed setters and complete JDBC URLs. */
public final class MigrateDefaultAzureCredentialAuthentication extends Recipe {
    private static final String OLD_VALUE = "DefaultAzureCredential";
    private static final String NEW_VALUE = "ActiveDirectoryDefault";
    private static final Pattern URL_AUTH = Pattern.compile(
            "(?i)(authentication\\s*=\\s*)DefaultAzureCredential(?=\\s*;|\\s*[\\\"'}]|\\s*,|$)");

    @Override
    public String getDisplayName() {
        return "Rename SQL Server DefaultAzureCredential authentication";
    }

    @Override
    public String getDescription() {
        return "Replace the SQL Server JDBC authentication value DefaultAzureCredential with its official " +
               "12.2+ name ActiveDirectoryDefault in complete JDBC URLs and typed SQLServerDataSource setters.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || source.getSourcePath().getFileName() == null ||
                    !UpgradeSelectedMssqlJdbcDependency.isProjectPath(source.getSourcePath())) return tree;
                if (tree instanceof J.CompilationUnit java) return migrateJava(java, ctx);
                if (tree instanceof Properties.File properties) return migrateProperties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return migrateYaml(yaml, ctx);
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(
                        source.getSourcePath().getFileName().toString())) return migrateXml(xml, ctx);
                if (tree instanceof PlainText text && isSupportedText(source)) return migrateText(text);
                return tree;
            }
        };
    }

    private static J.CompilationUnit migrateJava(J.CompilationUnit source, ExecutionContext ctx) {
        return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                J.Literal visited = super.visitLiteral(literal, executionContext);
                if (!(visited.getValue() instanceof String value)) return visited;
                String migrated = migrateUrl(value);
                if (!migrated.equals(value)) return replace(visited, value, migrated);
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                if (OLD_VALUE.equalsIgnoreCase(value) && invocation != null &&
                    "setAuthentication".equals(invocation.getSimpleName()) &&
                    isMicrosoftSqlServerMethod(invocation.getMethodType())) {
                    return replace(visited, value, NEW_VALUE);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static Properties.File migrateProperties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext executionContext) {
                Properties.Entry visited = super.visitEntry(entry, executionContext);
                String value = visited.getValue().getText();
                String migrated = migrateUrl(value);
                return migrated.equals(value) ? visited : visited.withValue(visited.getValue().withText(migrated));
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents migrateYaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext executionContext) {
                Yaml.Scalar visited = super.visitScalar(scalar, executionContext);
                String migrated = migrateUrl(visited.getValue());
                return migrated.equals(visited.getValue()) ? visited : visited.withValue(migrated);
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document migrateXml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                Xml.CharData visited = super.visitCharData(charData, executionContext);
                String migrated = migrateUrl(visited.getText());
                return migrated.equals(visited.getText()) ? visited : visited.withText(migrated);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                Xml.Attribute visited = super.visitAttribute(attribute, executionContext);
                String value = visited.getValueAsString();
                String migrated = migrateUrl(value);
                return migrated.equals(value) ? visited :
                        visited.withValue(visited.getValue().withValue(migrated));
            }
        }.visitNonNull(source, ctx);
    }

    private static PlainText migrateText(PlainText source) {
        String migrated = migrateUrl(source.getText());
        return migrated.equals(source.getText()) ? source : source.withText(migrated);
    }

    private static String migrateUrl(String value) {
        int url = value.toLowerCase(Locale.ROOT).indexOf("jdbc:sqlserver:");
        if (url < 0) return value;
        String tail = value.substring(url);
        Matcher matcher = URL_AUTH.matcher(tail);
        return matcher.find() ? value.substring(0, url) + matcher.replaceAll("$1" + NEW_VALUE) : value;
    }

    private static boolean isSupportedText(SourceFile source) {
        String name = source.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".conf") || name.endsWith(".env") || name.endsWith(".json");
    }

    private static boolean isMicrosoftSqlServerMethod(JavaType.Method method) {
        JavaType.FullyQualified owner = method == null ? null : TypeUtils.asFullyQualified(method.getDeclaringType());
        return owner != null && owner.getFullyQualifiedName().startsWith("com.microsoft.sqlserver.jdbc.");
    }

    private static J.Literal replace(J.Literal literal, String oldValue, String newValue) {
        String source = literal.getValueSource();
        return literal.withValue(newValue).withValueSource(
                source == null ? null : source.replace(oldValue, newValue));
    }
}
