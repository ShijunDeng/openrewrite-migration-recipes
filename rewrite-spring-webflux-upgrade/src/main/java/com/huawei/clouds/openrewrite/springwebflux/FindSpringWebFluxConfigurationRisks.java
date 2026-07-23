package com.huawei.clouds.openrewrite.springwebflux;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
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

/** Structured configuration markers for Spring WebFlux behavior boundaries. */
public final class FindSpringWebFluxConfigurationRisks extends Recipe {
    private static final Set<String> EXACT_KEYS = Set.of(
            "spring.webflux.base-path",
            "spring.webflux.static-path-pattern",
            "spring.webflux.problemdetails.enabled",
            "spring.web.resources.add-mappings",
            "spring.web.resources.chain.enabled",
            "spring.codec.max-in-memory-size",
            "spring.codec.log-request-details",
            "server.http2.enabled",
            "management.observations.http.server.requests.name"
    );
    private static final Set<String> EXACT_TYPES = Set.of(
            "org.springframework.http.client.reactive.ReactorResourceFactory",
            "org.springframework.web.filter.reactive.ServerHttpObservationFilter",
            "org.springframework.web.reactive.resource.WebJarsResourceResolver"
    );

    static final String CONFIG =
            "Spring WebFlux route, ProblemDetail, codec, static-resource, observability, HTTP/2, or Netty configuration detected; regression-test the complete reactive HTTP contract";
    static final String XML =
            "This XML bean names a moved or behavior-sensitive Spring WebFlux type; apply the documented replacement and verify lifecycle, resource, and observability behavior";

    @Override
    public String getDisplayName() {
        return "Find Spring WebFlux 6.2 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact Spring WebFlux properties/YAML paths and XML bean types while ignoring POMs, " +
               "lookalike keys, arbitrary text, and generated outputs.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringWebFluxSupport.generated(source.getSourcePath())) return tree;
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
                return riskyKey(e.getKey()) ? SpringWebFluxSupport.mark(e, CONFIG) : e;
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ec);
                return e.getValue() instanceof Yaml.Scalar && riskyKey(path())
                        ? SpringWebFluxSupport.mark(e, CONFIG) : e;
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
                Xml.Tag t = super.visitTag(tag, ec);
                return t.getValue().map(String::trim).filter(EXACT_TYPES::contains).isPresent()
                        ? SpringWebFluxSupport.mark(t, XML) : t;
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext ec) {
                Xml.Attribute a = super.visitAttribute(attribute, ec);
                return EXACT_TYPES.contains(a.getValueAsString().trim())
                        ? SpringWebFluxSupport.mark(a, XML) : a;
            }
        }.visitNonNull(source, ctx);
    }

    static boolean riskyKey(String raw) {
        String key = raw.toLowerCase(Locale.ROOT).replace('_', '-');
        return EXACT_KEYS.contains(key) ||
               key.startsWith("spring.web.resources.static-locations") ||
               key.startsWith("spring.web.resources.chain.strategy.") ||
               key.startsWith("spring.webflux.multipart.") ||
               key.startsWith("server.netty.") ||
               key.startsWith("management.observations.http.server.requests.");
    }
}
