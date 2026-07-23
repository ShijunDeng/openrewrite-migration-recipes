package com.huawei.clouds.openrewrite.springweb;

import org.openrewrite.Cursor;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Structured configuration markers for Spring Web 6.2 behavior boundaries. */
public final class FindSpringWebConfigurationRisks extends Recipe {
    private static final Set<String> EXACT_KEYS = Set.of(
            "server.forward-headers-strategy",
            "spring.codec.max-in-memory-size",
            "spring.http.client.factory",
            "spring.http.client.connect-timeout",
            "spring.http.client.read-timeout",
            "spring.mvc.converters.preferred-json-mapper",
            "spring.mvc.format.date",
            "spring.mvc.format.date-time",
            "spring.mvc.format.time",
            "spring.mvc.log-request-details",
            "spring.mvc.problemdetails.enabled",
            "spring.mvc.publish-request-handled-events",
            "spring.mvc.servlet.load-on-startup",
            "spring.mvc.servlet.path",
            "spring.mvc.throw-exception-if-no-handler-found",
            "spring.web.locale",
            "spring.web.locale-resolver"
    );
    private static final Set<String> XML_PROPERTY_NAMES = Set.of(
            "bufferRequestBody", "outputStreaming", "readTimeout", "connectTimeout",
            "connectionRequestTimeout", "taskExecutor", "asyncRequestFactory",
            "defaultUriVariables", "parsePath", "encodingMode"
    );
    static final String CONFIG =
            "Spring Web 6.2 HTTP client, codec, forwarded-header, multipart, converter, locale, or error configuration detected; verify ownership and regression-test the complete HTTP contract";
    static final String XML_REMOVED =
            "This Spring XML bean/property references a removed or behavior-sensitive Spring Web API; replace it deliberately and test lifecycle, timeout, encoding, buffering, and error behavior";
    static final String JAKARTA =
            "This descriptor names a javax Servlet/Validation/JAXB/JSON/Activation type; Spring Web 6 requires the corresponding Jakarta API, provider, and runtime container";
    static final String HTTP_CLIENT =
            "This XML configuration uses Spring's Apache HTTP client integration; Spring 6 requires HttpClient 5 types and compatible pool/TLS/proxy/timeout configuration";
    static final String REMOTING =
            "This XML configuration uses removed Spring HTTP Invoker or Hessian remoting support; replace the protocol and serialization/security contract deliberately";
    static final String MULTIPART =
            "This configuration uses CommonsMultipartResolver or multipart limits; Spring 6 removed Commons FileUpload integration, so configure the Servlet container and retest limits, cleanup, encoding, and errors";

    @Override
    public String getDisplayName() {
        return "Find Spring Web 6.2 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact Spring Web properties/YAML paths and XML values for Jakarta, removed clients, remoting, " +
               "multipart, HttpClient 5, URI encoding, buffering, codecs, forwarded headers, and error behavior.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || SpringWebSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                if (tree instanceof Xml.Document xml &&
                    !"pom.xml".equals(source.getSourcePath().getFileName().toString())) return xml(xml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry e = super.visitEntry(entry, ec);
                return riskyKey(e.getKey()) ? mark(e, configurationMessage(e.getKey())) : e;
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ec);
                if (!(e.getValue() instanceof Yaml.Scalar)) return e;
                String path = path();
                return riskyKey(path) ? mark(e, configurationMessage(path)) : e;
            }

            private String path() {
                List<String> keys = new ArrayList<>();
                getCursor().getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                        .map(Yaml.Mapping.Entry.class::cast).forEach(e -> keys.add(e.getKey().getValue()));
                Collections.reverse(keys);
                return String.join(".", keys);
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document xml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag t = super.visitTag(tag, ec);
                String value = t.getValue().map(String::trim).orElse("");
                String message = xmlValueMessage(value);
                if (message != null) return mark(t, message);
                if ("property".equals(localName(t.getName())) &&
                    t.getAttributes().stream().anyMatch(attribute ->
                            "name".equals(attribute.getKeyAsString()) &&
                            XML_PROPERTY_NAMES.contains(attribute.getValueAsString())) &&
                    springWebBeanProperty(getCursor())) {
                    return mark(t, XML_REMOVED);
                }
                return t;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute a = super.visitAttribute(attribute, ec);
                String message = xmlValueMessage(a.getValueAsString());
                return message == null ? a : mark(a, message);
            }
        }.visitNonNull(source, ctx);
    }

    static boolean riskyKey(String raw) {
        String key = normalize(raw);
        return EXACT_KEYS.contains(key) ||
               key.startsWith("spring.servlet.multipart.") ||
               key.startsWith("spring.web.resources.") ||
               key.startsWith("spring.mvc.contentnegotiation.") ||
               key.startsWith("spring.jackson.") ||
               key.startsWith("spring.gson.") ||
               key.startsWith("spring.mvc.pathmatch.");
    }

    private static String configurationMessage(String raw) {
        String key = normalize(raw);
        return key.startsWith("spring.servlet.multipart.") ? MULTIPART : CONFIG;
    }

    private static String xmlValueMessage(String value) {
        if (value.startsWith("javax.servlet.") || value.startsWith("javax.validation.") ||
            value.startsWith("javax.xml.bind.") || value.startsWith("javax.json.") ||
            value.startsWith("javax.activation.")) return JAKARTA;
        if (value.startsWith("org.springframework.remoting.caucho.") ||
            value.startsWith("org.springframework.remoting.httpinvoker.")) return REMOTING;
        if (value.startsWith("org.springframework.web.multipart.commons.")) return MULTIPART;
        if (value.startsWith("org.springframework.http.client.HttpComponents") ||
            value.startsWith("org.apache.http.")) return HTTP_CLIENT;
        if (value.startsWith("org.springframework.http.client.Async") ||
            value.startsWith("org.springframework.web.client.AsyncRest") ||
            value.startsWith("org.springframework.web.util.DefaultUriTemplateHandler") ||
            value.startsWith("org.springframework.web.util.AbstractUriTemplateHandler")) return XML_REMOVED;
        return null;
    }

    private static boolean springWebBeanProperty(Cursor cursor) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (!(current.getValue() instanceof Xml.Tag tag)) continue;
            if (!"bean".equals(localName(tag.getName()))) continue;
            String type = tag.getAttributes().stream()
                    .filter(attribute -> "class".equals(attribute.getKeyAsString()))
                    .map(Xml.Attribute::getValueAsString)
                    .findFirst().orElse("");
            return type.startsWith("org.springframework.http.client.") ||
                   type.startsWith("org.springframework.web.client.") ||
                   type.startsWith("org.springframework.web.util.");
        }
        return false;
    }

    private static String normalize(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String localName(String raw) {
        int separator = raw.indexOf(':');
        return separator < 0 ? raw : raw.substring(separator + 1);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
