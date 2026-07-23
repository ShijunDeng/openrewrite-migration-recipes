package com.huawei.clouds.openrewrite.junitjupiteraggregate;

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
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Map;

/** Find removed or newly strict JUnit Jupiter configuration. */
public final class FindJUnitJupiter6ConfigurationRisks extends Recipe {
    static final String TEMPDIR =
            "JUnit 6 removed junit.jupiter.tempdir.scope; choose the supported TempDir lifecycle explicitly and verify " +
            "cleanup, sharing, and parallel-test isolation before deleting this key";
    static final String LOCALE =
            "JUnit 6 removed junit.jupiter.params.arguments.conversion.locale.format and always uses IETF BCP 47 via " +
            "Locale.forLanguageTag; update locale data and assertions before deleting this key";
    static final String ENUM =
            "JUnit 6 fails discovery/execution for an invalid enum configuration value instead of falling back; verify this " +
            "value against the 6.0.1 constants before rollout";
    static final String LEGACY_REFLECTION =
            "JUnit 6 removed junit.platform.reflection.search.useLegacySemantics and always follows Java visibility and " +
            "override rules; remove this key only after verifying inherited/hidden fields and methods";
    static final String PLATFORM_ENUM =
            "JUnit 6 fails discovery for an invalid Platform issue phase/severity value instead of falling back; verify " +
            "this value against the 6.0.1 constants before rollout";
    private static final Map<String, String> KEYS = Map.ofEntries(
            Map.entry("junit.jupiter.tempdir.scope", TEMPDIR),
            Map.entry("junit.jupiter.params.arguments.conversion.locale.format", LOCALE),
            Map.entry("junit.jupiter.execution.parallel.mode.default", ENUM),
            Map.entry("junit.jupiter.execution.parallel.mode.classes.default", ENUM),
            Map.entry("junit.jupiter.execution.timeout.mode", ENUM),
            Map.entry("junit.jupiter.execution.timeout.thread.mode.default", ENUM),
            Map.entry("junit.jupiter.extensions.testinstantiation.extensioncontextscope.default", ENUM),
            Map.entry("junit.jupiter.tempdir.cleanup.mode.default", ENUM),
            Map.entry("junit.jupiter.testinstance.lifecycle.default", ENUM),
            Map.entry("junit.platform.reflection.search.useLegacySemantics", LEGACY_REFLECTION),
            Map.entry("junit.platform.discovery.issue.failure.phase", PLATFORM_ENUM),
            Map.entry("junit.platform.discovery.issue.severity.critical", PLATFORM_ENUM));

    @Override
    public String getDisplayName() {
        return "Find JUnit Jupiter 6 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed and newly strict JUnit Jupiter configuration entries in parsed properties, YAML, and XML.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedJUnitJupiterDependency.generated(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) {
                    return new PropertiesIsoVisitor<ExecutionContext>() {
                        @Override
                        public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext p) {
                            Properties.Entry visited = super.visitEntry(entry, p);
                            String message = KEYS.get(visited.getKey());
                            return message == null ? visited : mark(visited, message);
                        }
                    }.visitNonNull(properties, ctx);
                }
                if (tree instanceof Yaml.Documents yaml) {
                    return new YamlIsoVisitor<ExecutionContext>() {
                        private final Map<String, JsonPathMatcher> matchers = KEYS.keySet().stream()
                                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                        key -> key, key -> new JsonPathMatcher("$." + key)));

                        @Override
                        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext p) {
                            Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, p);
                            String literalMessage = KEYS.get(visited.getKey().getValue());
                            if (literalMessage != null) return mark(visited, literalMessage);
                            for (Map.Entry<String, JsonPathMatcher> matcher : matchers.entrySet()) {
                                if (matcher.getValue().matches(getCursor())) {
                                    return mark(visited, KEYS.get(matcher.getKey()));
                                }
                            }
                            return visited;
                        }
                    }.visitNonNull(yaml, ctx);
                }
                if (tree instanceof Xml.Document xml &&
                    "pom.xml".equals(source.getSourcePath().getFileName().toString())) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext p) {
                            Xml.Tag visited = super.visitTag(tag, p);
                            String message = KEYS.get(visited.getName());
                            return message == null ? visited : mark(visited, message);
                        }
                    }.visitNonNull(xml, ctx);
                }
                return tree;
            }
        };
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
