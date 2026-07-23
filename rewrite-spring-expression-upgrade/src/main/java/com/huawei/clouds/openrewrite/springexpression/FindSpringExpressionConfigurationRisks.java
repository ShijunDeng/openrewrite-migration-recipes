package com.huawei.clouds.openrewrite.springexpression;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Structured configuration markers for the global SpEL defaults and embedded
 * expression strings that cannot be migrated without an application policy.
 */
public final class FindSpringExpressionConfigurationRisks extends Recipe {
    private static final String MAX_OPERATIONS = "spring.expression.maxOperations";
    private static final String COMPILER_MODE = "spring.expression.compiler.mode";
    private static final Pattern EXECUTABLE_EXPRESSION = Pattern.compile(
            "(?s).*#\\{.*(?:T\\s*\\(|new\\s+|@[A-Za-z_$]|[A-Za-z_$][\\w$]*\\s*\\(|" +
            "\\+\\+|--|(?<![=!<>])=(?!=)).*}.*");
    private static final Pattern MAX_OPERATIONS_ARGUMENT = Pattern.compile(
            "(?:-D)?spring[.]expression[.]maxOperations\\s*[=:]\\s*([^\\s\"']+)");
    private static final Pattern COMPILER_MODE_ARGUMENT = Pattern.compile(
            "(?:-D)?spring[.]expression[.]compiler[.]mode\\s*[=:]\\s*([^\\s\"']+)");

    static final String LIMIT =
            "Spring 6.2.19 reads spring.expression.maxOperations as a positive integer global default; " +
            "validate startup, choose the smallest measured limit, and prefer a per-parser seven-argument configuration";
    static final String INVALID_LIMIT =
            "spring.expression.maxOperations must be a positive integer in 6.2.19; this value can fail application startup";
    static final String COMPILER =
            "A process-wide SpEL compiler mode is configured; verify stable runtime types, public/module-visible " +
            "members, generated child ClassLoaders, and interpreted fallback before enabling MIXED or IMMEDIATE";
    static final String INVALID_COMPILER =
            "spring.expression.compiler.mode must be OFF, IMMEDIATE, or MIXED in 6.2.19; this value can fail SpEL initialization";
    static final String EMBEDDED =
            "An executable SpEL expression is embedded in configuration; verify its trust boundary, resolver " +
            "capabilities, side effects, parameter metadata, and the 10,000-operation evaluation limit";
    static final String JAKARTA =
            "An embedded SpEL expression names a javax type; Spring 6 uses Jakarta EE 9/10 namespaces, so migrate " +
            "the literal only after verifying that the referenced API moved to jakarta";

    @Override
    public String getDisplayName() {
        return "Find Spring Expression 6.2 configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact global max-operation/compiler properties and executable or javax-bearing SpEL " +
               "expressions in properties, YAML, and non-build XML configuration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringExpressionSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                if (tree instanceof Xml.Document xml) return xml(xml, ctx, "pom.xml".equals(file));
                if (tree instanceof PlainText text && plainConfiguration(file)) return plain(text);
                return tree;
            }
        };
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                String key = visited.getKey().trim();
                String value = visited.getValue().getText().trim();
                String message = propertyMessage(key, value);
                if (message != null) visited = SpringExpressionSupport.mark(visited, message);
                return markExpression(visited, value);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ec);
                if (!(visited.getValue() instanceof Yaml.Scalar scalar)) return visited;
                String value = scalar.getValue();
                String message = propertyMessage(path(getCursor()), value);
                if (message != null) visited = SpringExpressionSupport.mark(visited, message);
                return markExpression(visited, value);
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document xml(Xml.Document source, ExecutionContext ctx, boolean buildFile) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                String direct = propertyMessage(visited.getName(), visited.getValue().orElse(""));
                if (direct != null) visited = SpringExpressionSupport.mark(visited, direct);
                for (Xml.Attribute attribute : visited.getAttributes()) {
                    String value = attribute.getValueAsString();
                    visited = markArguments(visited, value);
                    if (buildFile) continue;
                    String message = expressionMessage(value);
                    if (message != null) visited = SpringExpressionSupport.mark(visited, message);
                }
                String value = visited.getValue().orElse("");
                visited = markArguments(visited, value);
                if (buildFile) return visited;
                String message = expressionMessage(value);
                return message == null ? visited : SpringExpressionSupport.mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    static String propertyMessage(String key, String value) {
        if (MAX_OPERATIONS.equals(key)) {
            try {
                return Integer.parseInt(value.trim()) > 0 ? LIMIT : INVALID_LIMIT;
            } catch (NumberFormatException invalid) {
                return INVALID_LIMIT;
            }
        }
        if (COMPILER_MODE.equals(key)) {
            String mode = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            return mode.equals("OFF") || mode.equals("IMMEDIATE") || mode.equals("MIXED")
                    ? COMPILER : INVALID_COMPILER;
        }
        return null;
    }

    private static PlainText plain(PlainText source) {
        return markArguments(source, source.getText());
    }

    private static <T extends Tree> T markArguments(T tree, String text) {
        T marked = tree;
        java.util.regex.Matcher maximum = MAX_OPERATIONS_ARGUMENT.matcher(text);
        while (maximum.find()) {
            marked = SpringExpressionSupport.mark(marked,
                    propertyMessage(MAX_OPERATIONS, maximum.group(1)));
        }
        java.util.regex.Matcher compiler = COMPILER_MODE_ARGUMENT.matcher(text);
        while (compiler.find()) {
            marked = SpringExpressionSupport.mark(marked,
                    propertyMessage(COMPILER_MODE, compiler.group(1)));
        }
        return marked;
    }

    private static <T extends Tree> T markExpression(T tree, String value) {
        String message = expressionMessage(value);
        return message == null ? tree : SpringExpressionSupport.mark(tree, message);
    }

    static String expressionMessage(String value) {
        if (value == null || !value.contains("#{")) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("t(javax.") || normalized.contains("new javax.")) return JAKARTA;
        return EXECUTABLE_EXPRESSION.matcher(value).matches() ? EMBEDDED : null;
    }

    private static String path(Cursor cursor) {
        List<String> keys = new ArrayList<>();
        cursor.getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                .map(Yaml.Mapping.Entry.class::cast)
                .forEach(entry -> keys.add(entry.getKey().getValue()));
        Collections.reverse(keys);
        return String.join(".", keys);
    }

    private static boolean plainConfiguration(String file) {
        String lower = file.toLowerCase(Locale.ROOT);
        return "dockerfile".equals(lower) || ".env".equals(lower) || lower.endsWith(".env") ||
               lower.endsWith(".sh") || lower.endsWith(".cmd") || lower.endsWith(".ps1") ||
               lower.endsWith(".options") || lower.endsWith(".conf") ||
               "jvm.config".equals(lower) || "jvm.options".equals(lower);
    }
}
