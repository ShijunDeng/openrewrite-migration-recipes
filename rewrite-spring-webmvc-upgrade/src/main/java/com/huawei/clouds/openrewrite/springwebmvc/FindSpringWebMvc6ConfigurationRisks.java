package com.huawei.clouds.openrewrite.springwebmvc;

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

/** Structured configuration markers for Spring Web MVC 6.2 behavior boundaries. */
public final class FindSpringWebMvc6ConfigurationRisks extends Recipe {
    private static final Set<String> EXACT_KEYS = Set.of(
            "spring.mvc.pathmatch.matching-strategy",
            "spring.mvc.throw-exception-if-no-handler-found",
            "spring.mvc.problemdetails.enabled",
            "spring.mvc.servlet.path",
            "spring.mvc.static-path-pattern",
            "spring.web.resources.add-mappings",
            "spring.web.resources.chain.enabled",
            "spring.mvc.contentnegotiation.favor-parameter",
            "spring.mvc.contentnegotiation.parameter-name"
    );
    private static final String MVC_NAMESPACE = "http://www.springframework.org/schema/mvc";
    private static final String TILES_PACKAGE = "org.springframework.web.servlet.view.tiles3.";
    static final String CONFIG =
            "Spring MVC 6 route, 404/ProblemDetail, servlet-path, static-resource, or content-negotiation configuration detected; regression-test the complete HTTP contract";
    static final String XML =
            "Legacy Spring MVC XML configuration detected; verify PathPattern/trailing slash, namespace schema, Jakarta Servlet, resource, exception, and view behavior on 6.2";
    static final String TILES =
            "Spring Framework 6 removed Tiles 3 integration and its MVC namespace element; replace this view stack deliberately";
    static final String JAKARTA =
            "This descriptor names a javax Servlet/Validation type; Spring MVC 6 requires the corresponding Jakarta API and compatible descriptor/container";

    @Override
    public String getDisplayName() {
        return "Find Spring Web MVC 6.2 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact Spring MVC properties, YAML paths, XML namespace settings, removed Tiles declarations, " +
               "and javax descriptor types while ignoring unrelated text and generated files.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || UpgradeSelectedSpringWebMvcDependency.generated(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(
                        source.getSourcePath().getFileName().toString())) return xml(xml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry e = super.visitEntry(entry, ec);
                return riskyKey(e.getKey()) ? mark(e, CONFIG) : e;
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ec);
                if (!(e.getValue() instanceof Yaml.Scalar)) return e;
                return riskyKey(path()) ? mark(e, CONFIG) : e;
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
                if (value.startsWith(TILES_PACKAGE)) return mark(t, TILES);
                if (value.startsWith("javax.servlet.") || value.startsWith("javax.validation.")) return mark(t, JAKARTA);
                if (mvcElement(getCursor(), t)) return mark(t, XML);
                return t;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute a = super.visitAttribute(attribute, ec);
                String value = a.getValueAsString();
                if (value.startsWith(TILES_PACKAGE)) return mark(a, TILES);
                if (value.startsWith("javax.servlet.") || value.startsWith("javax.validation.")) return mark(a, JAKARTA);
                return a;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean mvcElement(Cursor cursor, Xml.Tag tag) {
        String name = tag.getName();
        int separator = name.indexOf(':');
        String prefix = separator < 0 ? "" : name.substring(0, separator);
        String declaration = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag scope) {
                for (Xml.Attribute attribute : scope.getAttributes()) {
                    if (declaration.equals(attribute.getKeyAsString())) {
                        return MVC_NAMESPACE.equals(attribute.getValueAsString());
                    }
                }
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    static boolean riskyKey(String raw) {
        String key = raw.toLowerCase(Locale.ROOT).replace('_', '-');
        return EXACT_KEYS.contains(key) || key.startsWith("spring.mvc.contentnegotiation.media-types.") ||
               key.startsWith("spring.web.resources.static-locations") ||
               key.startsWith("spring.mvc.format.");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
