package com.huawei.clouds.openrewrite.logbackcore;

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
import java.util.regex.Pattern;

/** Locate configuration, rolling, listener, lifecycle, and packaging decisions. */
public final class FindLogbackCore1534ConfigurationRisks extends Recipe {
    static final String GROOVY =
            "Groovy/Gaffer Logback configuration was removed for security reasons; convert this file to owned XML " +
            "or another supported model and prohibit dynamic code execution in logging configuration";
    static final String JNDI =
            "insertFromJNDI is restricted to the java: namespace after CVE-2021-42550 hardening; verify the exact " +
            "container binding, reject remote schemes, protect the configuration file and test missing values";
    static final String JORAN =
            "This custom or nested Joran rule crosses the model-pipeline rewrite; verify ordering, unreferenced " +
            "appender instantiation, conditional branches, custom component allow-listing and reconfiguration";
    static final String CONDITIONAL =
            "Target 1.5.34 still uses Janino-backed evaluator/if expressions and predates the definitive " +
            "CVE-2026-13006 fix in 1.5.37; replace or security-review every expression and test both branches";
    static final String ROLLING =
            "Rolling/compression accounting changed, SizeAndTimeBasedFNATP was renamed, and checkIncrement is now " +
            "ineffective; verify exact rollover, restart index, compressed-size cap, maxHistory and deletion behavior";
    static final String XZ =
            "XZ archiving requires optional org.tukaani:xz; without it Logback falls back to GZ—verify produced " +
            "suffix/content, retention tooling, JPMS readability and rollback consumers";
    static final String PRUDENT =
            "Prudent mode constrains file/rolling/compression behavior and cross-process locking; validate the final " +
            "active file, network filesystem, restart, multi-process and failure-recovery matrix";
    static final String STATUS =
            "Status-listener installation and status servlet lifecycle cross reset/reconfigure and Jakarta changes; " +
            "verify one listener, output target, duplicate diagnostics, clear authorization and container shutdown";
    static final String LIFECYCLE =
            "Shutdown hook or servlet-container lifecycle ownership can double-stop or leak the Logback context; " +
            "verify container-managed shutdown, delay, asynchronous queues, scan tasks and restart/redeploy";
    static final String DATABASE =
            "This configuration references removed in-core database components; select and version the separate " +
            "logback-db line or another sink, then migrate schema, credentials, batching and retry behavior";
    static final String SERIALIZATION =
            "This socket/receiver configuration relies on Java serialization or removed receiver components; replace " +
            "the transport and validate authentication, framing, allow-lists and CVE-2026-10532 Proxy rejection";
    static final String JAKARTA =
            "This optional servlet/SMTP component moved from javax.* to jakarta.*; align the servlet container or mail " +
            "provider and verify class loading, JPMS/OSGi resolution, lifecycle and delivery";
    static final String SCAN =
            "Configuration scanning reloads writable input; protect ownership/permissions, constrain URL schemes, and " +
            "test changed/deleted/reappearing includes plus rollback after a malformed update";
    static final String VARIABLE_ESCAPE =
            "Escape sequences in the value attribute of variable/property elements are no longer interpreted as Java " +
            "string escapes; verify the exact resulting characters and downstream path/pattern semantics";
    static final String XML_SECURITY =
            "Joran no longer resolves external XML entities; inline or securely replace this DTD/entity dependency " +
            "and verify parsing with network access disabled";
    static final String PACKAGING =
            "Logback Core 1.5.34 is a Java 11 explicit JPMS, multi-release and OSGi artifact with optional modules; " +
            "verify module-info, services/resources, shade/fat-JAR merging, version discovery and native-image metadata";
    static final String TEMPLATE =
            "This text/template contains a legacy Logback class name but cannot be structurally rewritten safely; " +
            "migrate the template owner and render/parse every variant before deployment";

    private static final Pattern JAVA_ESCAPE = Pattern.compile(".*\\\\(?:[btnfr'\"\\\\]|u[0-9a-fA-F]{4}).*");
    private static final Set<String> ROLLING_TAGS = Set.of(
            "rollingPolicy", "timeBasedFileNamingAndTriggeringPolicy", "fileNamePattern",
            "maxFileSize", "maxHistory", "totalSizeCap", "cleanHistoryOnStart", "checkIncrement");

    @Override
    public String getDisplayName() {
        return "Find Logback Core 1.5.34 configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark Groovy/JNDI/Joran/conditional, rolling/compression, status/listener, servlet/mail lifecycle, " +
               "serialization, scan, XML entity, variable escape and JPMS/OSGi packaging boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedLogbackCoreDependency.generated(source.getSourcePath())) return tree;
                String file = source.getSourcePath().getFileName().toString();
                String lower = file.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".groovy") && lower.contains("logback")) return mark(tree, GROOVY);
                if ("module-info.java".equals(file) && source.printAll().contains("ch.qos.logback")) {
                    return mark(tree, PACKAGING);
                }
                if (tree instanceof Xml.Document xml &&
                    (FindOwnedLogbackMigrationSources.logbackConfiguration(xml) ||
                     "web.xml".equalsIgnoreCase(file))) return xml(xml, ctx);
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                if (tree instanceof PlainText text) return plain(text, file);
                return tree;
            }
        };
    }

    private static Xml.Document xml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.DocTypeDecl visitDocTypeDecl(Xml.DocTypeDecl docTypeDecl, ExecutionContext ec) {
                return mark(super.visitDocTypeDecl(docTypeDecl, ec), XML_SECURITY);
            }

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                String name = visited.getName();
                String className = attribute(visited, "class");
                String value = visited.getValue().map(String::trim).orElse("");

                if ("insertFromJNDI".equals(name)) return mark(visited, JNDI);
                if ("newRule".equals(name) || "appender".equals(name) && nestedIn(getCursor(), "appender")) {
                    return mark(visited, JORAN);
                }
                if ("if".equals(name) || "evaluator".equals(name) &&
                    className != null && className.contains("Janino")) return mark(visited, CONDITIONAL);
                if ("configuration".equals(name) && "true".equalsIgnoreCase(attribute(visited, "scan")) ||
                    "include".equals(name) && httpInclude(visited)) return mark(visited, SCAN);
                if (("variable".equals(name) || "property".equals(name)) &&
                    escapeValue(visited)) return mark(visited, VARIABLE_ESCAPE);
                if ("statusListener".equals(name)) return mark(visited, STATUS);
                if ("shutdownHook".equals(name) ||
                    className != null && className.contains("LogbackServletContextListener")) {
                    return mark(visited, LIFECYCLE);
                }
                if (className != null && (className.contains(".db.") || className.contains("DBAppender"))) {
                    return mark(visited, DATABASE);
                }
                if ("receiver".equals(name) || className != null &&
                    (className.contains("SocketReceiver") || className.contains("ServerSocketAppender"))) {
                    return mark(visited, SERIALIZATION);
                }
                if (className != null && (className.contains("SMTPAppender") ||
                    className.contains("ViewStatusMessagesServlet") ||
                    className.contains("LogbackServletContainerInitializer"))) return mark(visited, JAKARTA);
                if ("servlet-class".equals(name) && (value.contains("ViewStatusMessagesServlet") ||
                    value.contains("LogbackServlet"))) return mark(visited, JAKARTA);
                if ("prudent".equals(name) && "true".equalsIgnoreCase(value)) return mark(visited, PRUDENT);
                if ("fileNamePattern".equals(name) && value.toLowerCase(Locale.ROOT).endsWith(".xz")) {
                    return mark(visited, XZ);
                }
                if (ROLLING_TAGS.contains(name) && rollingRisk(visited, className, value)) {
                    return mark(visited, ROLLING);
                }
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                String key = visited.getKey();
                String value = visited.getValue().getText().trim();
                String message = propertyMessage(key, value);
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ec);
                String value = visited.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue() : "";
                String message = propertyMessage(visited.getKey().getValue(), value);
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static PlainText plain(PlainText source, String file) {
        String text = source.getText();
        String lower = file.toLowerCase(Locale.ROOT);
        PlainText result = source;
        if ((lower.endsWith(".xml.j2") || lower.endsWith(".xml.tmpl") || lower.endsWith(".xml.template") ||
             lower.endsWith(".xml.erb")) &&
            (text.contains("ch.qos.logback.core.hook.DelayingShutdownHook") ||
             text.contains("ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP"))) {
            result = mark(result, TEMPLATE);
        }
        if (("MANIFEST.MF".equals(file) || "bnd.bnd".equals(file)) &&
            (text.contains("ch.qos.logback") || text.contains("Multi-Release") ||
             text.contains("Automatic-Module-Name"))) result = mark(result, PACKAGING);
        return result;
    }

    private static String propertyMessage(String key, String value) {
        String normalized = key.trim();
        if ("logback.statusListenerClass".equals(normalized)) return STATUS;
        if ("logback.configurationFile".equals(normalized) && value.toLowerCase(Locale.ROOT).endsWith(".groovy")) {
            return GROOVY;
        }
        if ("logback.disableServletContainerInitializer".equals(normalized)) return LIFECYCLE;
        if (normalized.startsWith("logging.logback.rollingpolicy.")) return ROLLING;
        return null;
    }

    private static boolean rollingRisk(Xml.Tag tag, String className, String value) {
        if ("checkIncrement".equals(tag.getName()) || "totalSizeCap".equals(tag.getName()) ||
            "cleanHistoryOnStart".equals(tag.getName())) return true;
        if ("fileNamePattern".equals(tag.getName())) {
            String lower = value.toLowerCase(Locale.ROOT);
            return lower.endsWith(".zip") || lower.endsWith(".gz") || lower.endsWith(".xz");
        }
        return className != null && (className.contains("SizeAndTimeBased") ||
                                    className.contains("TimeBasedRollingPolicy"));
    }

    private static boolean escapeValue(Xml.Tag tag) {
        String candidate = attribute(tag, "value");
        return candidate != null && JAVA_ESCAPE.matcher(candidate).matches();
    }

    private static boolean httpInclude(Xml.Tag tag) {
        String url = attribute(tag, "url");
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static boolean nestedIn(Cursor cursor, String tagName) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && tagName.equals(tag.getName())) return true;
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static String attribute(Xml.Tag tag, String name) {
        return tag.getAttributes().stream().filter(attribute -> name.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
