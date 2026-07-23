package com.huawei.clouds.openrewrite.log4jcore;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Remove the message nolookups option removed after the 2.13 line. */
public final class MigrateRemovedNoLookupsOption extends Recipe {
    private static final String PATTERN_LAYOUT =
            "org.apache.logging.log4j.core.layout.PatternLayout";
    private static final Map<String, String> TOKENS = Map.of(
            "%message{nolookups}", "%message",
            "%msg{nolookups}", "%msg",
            "%m{nolookups}", "%m");
    private static final Pattern STRUCTURED_PATTERN = Pattern.compile(
            "(?im)(\\\"pattern\\\"\\s*:\\s*\\\")([^\\\"]*)(\\\")|(^\\s*pattern\\s*:\\s*)([^\\r\\n]+)");

    @Override
    public String getDisplayName() {
        return "Remove obsolete Log4j message nolookups options";
    }

    @Override
    public String getDescription() {
        return "Remove obsolete Pattern Layout nolookups message options because modern Log4j never evaluates message lookups unless the explicit lookups option is used.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    Log4jCoreSupport.generated(source.getSourcePath())) return tree;
                if (tree instanceof J.CompilationUnit java) return migrateJava(java, ctx);
                if (tree instanceof Xml.Document xml && Log4jCoreSupport.log4jXml(xml)) {
                    return migrateXml(xml, ctx);
                }
                if (tree instanceof Properties.File properties &&
                    Log4jCoreSupport.log4jProperties(source.getSourcePath())) {
                    return migrateProperties(properties, ctx);
                }
                if (tree instanceof PlainText text &&
                    Log4jCoreSupport.log4jStructuredText(source.getSourcePath())) {
                    String replacement = replaceStructuredPatterns(text.getText());
                    return replacement.equals(text.getText()) ? text : text.withText(replacement);
                }
                return tree;
            }
        };
    }

    private static J.CompilationUnit migrateJava(J.CompilationUnit source, ExecutionContext ctx) {
        return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ec) {
                J.Literal visited = super.visitLiteral(literal, ec);
                if (!(visited.getValue() instanceof String value) ||
                    !isJavaPatternLiteral(getCursor(), visited)) return visited;
                String replacement = replace(value);
                if (replacement.equals(value)) return visited;
                String source = visited.getValueSource();
                return visited.withValue(replacement)
                        .withValueSource(source == null ? null : replace(source));
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document migrateXml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                if (!parentTag(getCursor(), "Pattern") || !insideTag(getCursor(), "PatternLayout")) {
                    return visited;
                }
                String replacement = replace(visited.getText());
                return replacement.equals(visited.getText()) ? visited : visited.withText(replacement);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                if (!"pattern".equals(visited.getKeyAsString()) ||
                    !parentTag(getCursor(), "PatternLayout")) return visited;
                String value = visited.getValueAsString();
                String replacement = replace(value);
                return replacement.equals(value) ? visited :
                        visited.withValue(visited.getValue().withValue(replacement));
            }
        }.visitNonNull(source, ctx);
    }

    private static Properties.File migrateProperties(
            Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                if (!visited.getKey().endsWith(".layout.pattern")) return visited;
                String value = visited.getValue().getText();
                String replacement = replace(value);
                return replacement.equals(value) ? visited :
                        visited.withValue(visited.getValue().withText(replacement));
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean isJavaPatternLiteral(Cursor cursor, J.Literal literal) {
        Cursor parent = cursor.getParentTreeCursor();
        if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable variable) {
            return patternName(variable.getSimpleName());
        }
        if (parent.getValue() instanceof J.Assignment assignment) {
            return patternName(assignedName(assignment.getVariable()));
        }
        if (!(parent.getValue() instanceof J.MethodInvocation invocation)) return false;
        JavaType.Method method = invocation.getMethodType();
        JavaType.FullyQualified declaring = method == null ? null :
                TypeUtils.asFullyQualified(method.getDeclaringType());
        String owner = declaring == null ? "" : declaring.getFullyQualifiedName();
        if (("withPattern".equals(invocation.getSimpleName()) ||
             "setPattern".equals(invocation.getSimpleName())) &&
            (PATTERN_LAYOUT + "$Builder").equals(owner)) return true;
        return "createLayout".equals(invocation.getSimpleName()) &&
               PATTERN_LAYOUT.equals(owner) && !invocation.getArguments().isEmpty() &&
               invocation.getArguments().get(0) == literal;
    }

    private static String assignedName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess field) return field.getSimpleName();
        return "";
    }

    private static boolean patternName(String name) {
        return name.toLowerCase(Locale.ROOT).contains("pattern");
    }

    private static boolean parentTag(Cursor cursor, String name) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof Xml.Tag tag && name.equals(tag.getName());
    }

    private static boolean insideTag(Cursor cursor, String name) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && name.equals(tag.getName())) return true;
            if (current.getValue() instanceof Xml.Document) return false;
        }
        return false;
    }

    private static String replaceStructuredPatterns(String value) {
        Matcher matcher = STRUCTURED_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int valueGroup = matcher.group(2) == null ? 5 : 2;
            String replacement = replace(matcher.group(valueGroup));
            String match = matcher.group();
            int relativeStart = matcher.start(valueGroup) - matcher.start();
            String rewritten = match.substring(0, relativeStart) + replacement +
                               match.substring(relativeStart + matcher.group(valueGroup).length());
            matcher.appendReplacement(result, Matcher.quoteReplacement(rewritten));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static String replace(String value) {
        String result = value;
        for (Map.Entry<String, String> token : TOKENS.entrySet()) {
            result = result.replace(token.getKey(), token.getValue());
        }
        return result;
    }
}
