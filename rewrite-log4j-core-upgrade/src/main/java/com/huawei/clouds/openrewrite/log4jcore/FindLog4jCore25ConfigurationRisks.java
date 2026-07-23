package com.huawei.clouds.openrewrite.log4jcore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Cursor;
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

import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Locate Log4j configuration behavior changes that cannot be selected without runtime policy. */
public final class FindLog4jCore25ConfigurationRisks extends Recipe {
    static final String EXCEPTION =
            "Pattern Layout 2.25 changed the implicit exception converter from extended to plain and normalized " +
            "leading newlines; make the converter explicit and approve exact stack-trace snapshots";
    static final String ANSI =
            "The {ansi} exception-converter option was removed in 2.25; redesign styling with supported pattern " +
            "converters and test redirected/non-TTY output instead of silently dropping the option";
    static final String DATE =
            "Log4j 2.25 replaced its default instant formatter; compare this date pattern, locale and zone, especially " +
            "n/x directives, or temporarily set log4j2.instantFormatter=legacy";
    static final String PACKAGES =
            "Runtime package scanning is deprecated and warns in 2.24+; remove this packages setting only after every " +
            "custom plugin JAR contains and preserves Log4j2Plugins.dat";
    static final String STATUS =
            "The root status attribute is deprecated in 2.24; move policy to log4j2.statusLoggerLevel before Log4j " +
            "initialization and verify bootstrap diagnostics plus override precedence";
    static final String JNDI =
            "This configuration uses JNDI, which is disabled by default and restricted to java: names; explicitly " +
            "enable only the required capability and validate the trusted local resource";
    static final String SCRIPT =
            "This configuration executes a script; explicitly allow only required engines with " +
            "log4j2.scriptEnableLanguages and verify script ordering, sandboxing and error behavior";
    static final String LOOKUPS =
            "Lookup recursion/message interpolation changed after 2.13; verify untrusted MDC/message data cannot " +
            "recurse or disclose secrets and remove reliance on the ignored global formatMsgNoLookups switch";
    static final String OSGI_JPMS =
            "Log4j artifacts became named JPMS modules and OSGi symbolic names/package versions changed; regenerate " +
            "and resolve this metadata, including reflection and service/plugin visibility";

    private static final Pattern THROWABLE = Pattern.compile(
            "%(?:ex|exception|throwable|xEx|rEx|rootException)(?:\\{|\\b)");
    private static final Pattern EXCEPTION_ANSI = Pattern.compile(
            "%(?:ex|exception|throwable|xEx|rEx|rootException)\\{[^}]*ansi[^}]*}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_DIRECTIVE = Pattern.compile(
            "%(?:d|date)\\{[^}]+}");
    private static final Pattern STRUCTURED_PATTERN = Pattern.compile(
            "(?im)(?:\\\"pattern\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"|^\\s*pattern\\s*:\\s*[\\\"']?([^\\r\\n\\\"']+))");
    private static final Pattern STRUCTURED_SCRIPT = Pattern.compile(
            "(?im)(?:\\\"(?:Script|ScriptFile|ScriptRef)\\\"\\s*:|^\\s*(?:Script|ScriptFile|ScriptRef)\\s*:|" +
            "\\\"type\\\"\\s*:\\s*\\\"(?:Script|ScriptFile|ScriptRef)\\\"|^\\s*type\\s*:\\s*(?:Script|ScriptFile|ScriptRef)\\s*$)");
    private static final Pattern STRUCTURED_PACKAGES = Pattern.compile(
            "(?im)(?:\\\"packages\\\"\\s*:|^\\s*packages\\s*:)");
    private static final Pattern STRUCTURED_STATUS = Pattern.compile(
            "(?im)(?:\\\"status\\\"\\s*:|^\\s*status\\s*:)");
    private static final Pattern ALWAYS_FALSE = Pattern.compile(
            "(?im)(?:\\\"alwaysWriteExceptions\\\"\\s*:\\s*(?:false|\\\"false\\\")|" +
            "^\\s*alwaysWriteExceptions\\s*:\\s*[\\\"']?false[\\\"']?\\s*$)");
    private static final Set<String> LOOKUP_KEYS = Set.of(
            "log4j2.formatMsgNoLookups", "log4j.formatMsgNoLookups");

    @Override
    public String getDisplayName() {
        return "Find Apache Log4j Core 2.25 configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark Pattern Layout, plugin scanning, status logger, JNDI, scripts, lookups and module/bundle metadata decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    Log4jCoreSupport.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && Log4jCoreSupport.log4jXml(xml)) return xml(xml, ctx);
                if (tree instanceof Properties.File properties &&
                    Log4jCoreSupport.log4jProperties(source.getSourcePath())) return properties(properties, ctx);
                if (tree instanceof PlainText text) return plain(text, file);
                return tree;
            }
        };
    }

    private static Xml.Document xml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                String name = visited.getName();
                if (Set.of("Script", "ScriptFile", "ScriptRef").contains(name)) {
                    return mark(visited, SCRIPT);
                }
                if ("PatternLayout".equals(name)) {
                    boolean always = !"false".equalsIgnoreCase(
                            attribute(visited, "alwaysWriteExceptions", "true"));
                    String pattern = attribute(visited, "pattern",
                            visited.getChildValue("Pattern").orElse("%m%n"));
                    if (always && !THROWABLE.matcher(pattern).find()) {
                        visited = mark(visited, EXCEPTION);
                    }
                }
                return visited;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                String key = visited.getKeyAsString();
                String value = visited.getValueAsString();
                Xml.Attribute result = visited;
                if ("packages".equals(key) && parentTag(getCursor(), "Configuration")) {
                    result = mark(result, PACKAGES);
                }
                if ("status".equals(key) && parentTag(getCursor(), "Configuration")) {
                    result = mark(result, STATUS);
                }
                return markValue(result, value);
            }

            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext ec) {
                Xml.CharData visited = super.visitCharData(charData, ec);
                return markValue(visited, visited.getText());
            }
        }.visitNonNull(source, ctx);
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        Set<String> disabledLayouts = new HashSet<>();
        source.getContent().stream().filter(Properties.Entry.class::isInstance)
                .map(Properties.Entry.class::cast)
                .filter(entry -> entry.getKey().endsWith(".layout.alwaysWriteExceptions") &&
                                 "false".equalsIgnoreCase(entry.getValue().getText().trim()))
                .map(entry -> entry.getKey().substring(0,
                        entry.getKey().length() - ".alwaysWriteExceptions".length()))
                .forEach(disabledLayouts::add);
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                String key = visited.getKey();
                String value = visited.getValue().getText();
                Properties.Entry result = visited;
                if ("packages".equals(key) || "log4j.plugin.packages".equals(key)) {
                    result = mark(result, PACKAGES);
                }
                if ("status".equals(key)) result = mark(result, STATUS);
                if (LOOKUP_KEYS.contains(key)) result = mark(result, LOOKUPS);
                if (key.endsWith(".layout.pattern")) {
                    String layout = key.substring(0, key.length() - ".pattern".length());
                    if (!disabledLayouts.contains(layout) && !THROWABLE.matcher(value).find()) {
                        result = mark(result, EXCEPTION);
                    }
                }
                return markValue(result, value);
            }
        }.visitNonNull(source, ctx);
    }

    private static PlainText plain(PlainText source, String file) {
        PlainText result = source;
        String text = source.getText();
        if (("bnd.bnd".equals(file) || "MANIFEST.MF".equals(file)) &&
            text.contains("org.apache.logging.log4j")) result = mark(result, OSGI_JPMS);
        if (Log4jCoreSupport.log4jStructuredText(source.getSourcePath())) {
            Matcher pattern = STRUCTURED_PATTERN.matcher(text);
            boolean always = !ALWAYS_FALSE.matcher(text).find();
            while (always && pattern.find()) {
                String value = pattern.group(1) == null ? pattern.group(2) : pattern.group(1);
                if (!THROWABLE.matcher(value).find()) {
                    result = mark(result, EXCEPTION);
                    break;
                }
            }
            if (STRUCTURED_SCRIPT.matcher(text).find()) result = mark(result, SCRIPT);
            if (STRUCTURED_PACKAGES.matcher(text).find()) result = mark(result, PACKAGES);
            if (STRUCTURED_STATUS.matcher(text).find()) result = mark(result, STATUS);
            if (text.contains("${jndi:") || text.contains("$${jndi:")) result = mark(result, JNDI);
            if (text.contains("%m{lookups}") || text.contains("%msg{lookups}") ||
                text.contains("%message{lookups}") || text.contains("$${ctx:")) result = mark(result, LOOKUPS);
            if (EXCEPTION_ANSI.matcher(text).find()) result = mark(result, ANSI);
            if (DATE_DIRECTIVE.matcher(text).find()) result = mark(result, DATE);
        }
        return result;
    }

    private static <T extends Tree> T markValue(T tree, String value) {
        T result = tree;
        if (value.contains("${jndi:") || value.contains("$${jndi:")) result = mark(result, JNDI);
        if (value.contains("%m{lookups}") || value.contains("%msg{lookups}") ||
            value.contains("%message{lookups}") || value.contains("$${ctx:")) result = mark(result, LOOKUPS);
        if (EXCEPTION_ANSI.matcher(value).find()) result = mark(result, ANSI);
        if (DATE_DIRECTIVE.matcher(value).find()) result = mark(result, DATE);
        return result;
    }

    private static String attribute(Xml.Tag tag, String key, String fallback) {
        return tag.getAttributes().stream().filter(value -> key.equals(value.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse(fallback);
    }

    private static boolean parentTag(Cursor cursor, String name) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof Xml.Tag tag && name.equals(tag.getName());
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
