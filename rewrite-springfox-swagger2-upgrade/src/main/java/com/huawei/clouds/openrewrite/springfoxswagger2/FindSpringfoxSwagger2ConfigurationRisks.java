package com.huawei.clouds.openrewrite.springfoxswagger2;

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

/** Mark exact Springfox configuration contracts that cannot be validated against an absent target. */
public final class FindSpringfoxSwagger2ConfigurationRisks extends Recipe {
    private static final Set<String> ENDPOINTS = Set.of(
            "/v2/api-docs", "/v3/api-docs", "/swagger-resources", "/swagger-ui.html", "/swagger-ui/",
            "/webjars/**", "/swagger-resources/**"
    );
    private static final String SPRINGFOX_MESSAGE =
            "This Springfox configuration contract cannot be validated against io.springfox:springfox-swagger2:1.1.2 because that target is unpublished. Correct the coordinate, then verify groups, paths, media types, models, security, UI behavior, and generated OpenAPI output";
    private static final String PATH_MATCH_MESSAGE =
            "ant_path_matcher is a compatibility workaround commonly coupled to Springfox and Spring Boot path matching; the unpublished target has no verifiable Spring compatibility. Correct the target, remove this workaround only when justified, and regression-test every MVC route";
    private static final String ENDPOINT_MESSAGE =
            "This configured Springfox documentation endpoint is an externally visible routing/security contract; the unpublished target has no endpoint behavior to inspect. Correct the target, then test authentication, CSRF/CORS, proxy prefixes, context paths, and production exposure";

    @Override
    public String getDisplayName() {
        return "Find Springfox Swagger 2 configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark Springfox documentation properties, the exact ant_path_matcher workaround, and known " +
               "documentation endpoints in parsed properties, YAML, and non-POM XML.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedSpringfoxSwagger2Dependency.excluded(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) {
                    return new PropertiesIsoVisitor<ExecutionContext>() {
                        @Override
                        public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                            Properties.Entry visited = super.visitEntry(entry, ec);
                            String key = visited.getKey();
                            String value = visited.getValue().getText().trim();
                            String message = message(key, value);
                            return message == null ? visited : mark(visited, message);
                        }
                    }.visitNonNull(properties, ctx);
                }
                if (tree instanceof Yaml.Documents yaml) {
                    return new YamlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                            Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ec);
                            String value = visited.getValue() instanceof Yaml.Scalar scalar ? scalar.getValue().trim() : "";
                            String message = message(propertyPath(), value);
                            return message == null ? visited : mark(visited, message);
                        }

                        private String propertyPath() {
                            List<String> keys = new ArrayList<>();
                            getCursor().getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                                    .map(Yaml.Mapping.Entry.class::cast)
                                    .forEach(mapping -> keys.add(mapping.getKey().getValue()));
                            Collections.reverse(keys);
                            return String.join(".", keys);
                        }
                    }.visitNonNull(yaml, ctx);
                }
                String fileName = source.getSourcePath().getFileName().toString();
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag visited = super.visitTag(tag, ec);
                            String value = visited.getValue().map(String::trim).orElse("");
                            String message = message(visited.getName(), value);
                            return message == null ? visited : mark(visited, message);
                        }
                    }.visitNonNull(xml, ctx);
                }
                return tree;
            }
        };
    }

    private static String message(String key, String value) {
        if (key != null && key.startsWith("springfox.documentation.")) {
            return endpoint(value) ? SPRINGFOX_MESSAGE + ". " + ENDPOINT_MESSAGE : SPRINGFOX_MESSAGE;
        }
        if ("spring.mvc.pathmatch.matching-strategy".equals(key) &&
            "ant_path_matcher".equals(value)) return PATH_MATCH_MESSAGE;
        if (key == null) return null;
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        return normalizedKey.contains("springfox") && endpoint(value)
                ? ENDPOINT_MESSAGE : null;
    }

    private static boolean endpoint(String value) {
        if (ENDPOINTS.contains(value)) return true;
        for (String endpoint : ENDPOINTS) {
            if (endpoint.contains("**")) continue;
            int index = value.indexOf(endpoint);
            while (index >= 0) {
                int end = index + endpoint.length();
                if (endpoint.endsWith("/") || end == value.length() ||
                    "?/#".indexOf(value.charAt(end)) >= 0) return true;
                index = value.indexOf(endpoint, index + 1);
            }
        }
        return false;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
