package com.huawei.clouds.openrewrite.jettyhttp;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Structured and text markers for Jetty 12 start, XML, EE and HTTP configuration. */
public final class FindJettyHttpConfigurationRisks extends Recipe {
    static final String MODULES =
            "Jetty 12 start mechanism/module graph changed; regenerate $JETTY_BASE with the target start.jar, " +
            "replace legacy --module/--add-to-start usage, diff --list-config, and do not copy Jetty 9/11 start.d or etc files blindly";
    static final String EE =
            "Jetty 12 requires an explicit ee8/ee9/ee10 deployment environment; migrate deploy/webapp/servlet/JSP/" +
            "WebSocket module names together and set each application environment when multiple deployers are enabled";
    static final String XML_CLASS =
            "Jetty XML/reflection names an API moved or removed in Jetty 12 (including Handler$Sequence and " +
            "org.eclipse.jetty.http.content); update the exact class/member and validate XML construction on Java 17";
    static final String COMPLIANCE =
            "Jetty HTTP/URI/cookie/multipart compliance or parser setting affects malformed-input acceptance and " +
            "request-smuggling defenses; choose an explicit Jetty 12 mode and regression-test every retained violation";
    static final String LIMITS =
            "Jetty connector/header/form/request limit or timeout is deployment-sensitive; revalidate defaults, " +
            "memory budget, 413/431 behavior, slow clients, trailers and proxy/load-balancer limits on 12.0.34";
    static final String LOGGING =
            "Jetty 12 logging is SLF4J based and start modules/resources differ; remove legacy jetty-logging/log.class " +
            "assumptions, select one provider, verify request logging and protect secrets/control characters";
    static final String DEPLOY =
            "Jetty deployment scan, context XML, temp/work/base/home or hot-reload setting crosses the new EE " +
            "environment and classloader model; validate ownership, permissions, rollback, duplicate deployment and restart behavior";

    @Override
    public String getDisplayName() {
        return "Find Jetty 12 configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark structured Properties/YAML/XML and Jetty start text for module, EE environment, moved class, " +
               "compliance, limits, logging and deployment decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || JettyHttpSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(file)) return xml(xml, ctx);
                if (tree instanceof PlainText text) return plain(text);
                return tree;
            }
        };
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                String message = message(visited.getKey(), visited.getValue().getText());
                return message == null ? visited : JettyHttpSupport.mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ec);
                if (!(visited.getValue() instanceof Yaml.Scalar scalar)) return visited;
                String message = message(path(), scalar.getValue());
                return message == null ? visited : JettyHttpSupport.mark(visited, message);
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

    private static Xml.Document xml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                String text = visited.getName() + " " + visited.getValue().orElse("") + " " +
                              visited.getAttributes().stream().map(Xml.Attribute::getValueAsString)
                                      .reduce("", (left, right) -> left + " " + right);
                String message = textMessage(text);
                if (message == null) {
                    String name = visited.getAttributes().stream()
                            .filter(attribute -> "name".equals(attribute.getKeyAsString()))
                            .map(Xml.Attribute::getValueAsString).findFirst().orElse(visited.getName());
                    message = message(name, visited.getValue().orElse(""));
                }
                return message == null ? visited : JettyHttpSupport.mark(visited, message);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute visited = super.visitAttribute(attribute, ec);
                String message = textMessage(visited.getValueAsString());
                return message == null ? visited : JettyHttpSupport.mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static PlainText plain(PlainText source) {
        String value = source.getText();
        Set<String> messages = new LinkedHashSet<>();
        if (containsAny(value, "--module=", "--add-to-start", "--add-to-startd", "start.jar",
                        "start.ini", "start.d/")) messages.add(MODULES);
        if (containsAny(value, "--module=servlet", "--module=webapp", "--module=deploy",
                        ",servlet", ",webapp", ",deploy", "javax.servlet", "jakarta.servlet",
                        "jetty-servlet", "jetty-webapp")) {
            messages.add(EE);
        }
        String moved = textMessage(value);
        if (moved != null) messages.add(moved);
        if (containsAny(lower(value), "compliance", "ambiguous", "rfc7230", "legacy")) {
            messages.add(COMPLIANCE);
        }
        if (containsAny(lower(value), "headersize", "formcontent", "maxrequest", "idletimeout",
                        "outputbuffersize", "requestheadersize", "responseheadersize")) {
            messages.add(LIMITS);
        }
        if (containsAny(lower(value), "jetty.logging", "jetty-logging", "requestlog", "slf4j")) {
            messages.add(LOGGING);
        }
        return messages.isEmpty() ? source : JettyHttpSupport.mark(source, String.join(" | ", messages));
    }

    static String message(String rawKey, String rawValue) {
        String key = canonical(rawKey);
        String value = rawValue == null ? "" : rawValue.trim();
        boolean jettyKey = key.contains("jetty");
        String moved = textMessage(value);
        if (moved != null) return moved;
        if (jettyKey && (key.contains("module") || key.contains("jettyhome") || key.contains("jettybase"))) {
            return MODULES;
        }
        if ("environment".equals(key) || jettyKey && (key.contains("webapp") || key.contains("servlet") ||
            key.contains("deploy") && !key.contains("scan"))) return EE;
        if (jettyKey && (key.contains("compliance") || key.contains("ambiguous") ||
                         key.contains("badmessage"))) {
            return COMPLIANCE;
        }
        if (jettyKey && (key.contains("headersize") || key.contains("formcontent") || key.contains("maxrequest") ||
            key.contains("idletimeout") || key.contains("outputbuffersize") || key.contains("maxform") ||
            key.contains("port") || key.contains("host") || key.contains("forwarded"))) return LIMITS;
        if (jettyKey && (key.contains("logging") || key.contains("requestlog") || key.contains("slf4j"))) {
            return LOGGING;
        }
        if (jettyKey && (key.contains("deploy") || key.contains("scaninterval") ||
                         key.contains("contextpath") || key.contains("tempdirectory") ||
                         key.contains("workdir") || key.contains("webapps"))) return DEPLOY;
        return null;
    }

    private static String textMessage(String text) {
        if (containsAny(text,
                "org.eclipse.jetty.server.handler.HandlerCollection",
                "org.eclipse.jetty.server.handler.HandlerList",
                "org.eclipse.jetty.server.handler.RequestLogHandler",
                "org.eclipse.jetty.http.HttpContent",
                "org.eclipse.jetty.http.ResourceHttpContent",
                "org.eclipse.jetty.http.PrecompressedHttpContent")) return XML_CLASS;
        return null;
    }

    private static String canonical(String raw) {
        return lower(raw).replace("_", "").replace("-", "").replace(".", "");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }
}
