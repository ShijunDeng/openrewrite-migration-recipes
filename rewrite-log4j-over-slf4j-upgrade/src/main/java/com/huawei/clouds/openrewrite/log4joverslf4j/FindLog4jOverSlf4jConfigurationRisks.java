package com.huawei.clouds.openrewrite.log4joverslf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.text.PlainText;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark legacy Log4j configuration and SLF4J provider service declarations. */
public final class FindLog4jOverSlf4jConfigurationRisks extends Recipe {
    private static final Pattern PROVIDER_LINE = Pattern.compile(
            "(?m)^[ \\t]*([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+)(?=[ \\t]*(?:#|$))");
    private static final String LOG4J_CONFIG_MESSAGE =
            "log4j-over-slf4j does not initialize Log4j 1 configuration; translate this file to the selected SLF4J 2 provider and verify appenders, format, thresholds, additivity, rotation, async delivery, reload, and secrets";
    private static final String PROVIDER_MESSAGE =
            "SLF4J 2 discovers providers with ServiceLoader (or slf4j.provider); verify this exact provider class implements SLF4JServiceProvider, matches the 2.0 API family, is visible to the runtime classloader/module layer, and is unique";
    private static final Set<String> LOCATION_KEYS = Set.of(
            "log4j.configuration", "logging.config", "log4jConfigLocation", "log4j.configurationFile"
    );

    @Override
    public String getDisplayName() {
        return "Find log4j-over-slf4j configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark legacy log4j.properties/log4j.xml files, exact configuration-location keys, slf4j.provider " +
               "properties, and SLF4JServiceProvider service descriptors outside generated/install trees.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedLog4jOverSlf4jDependency.excluded(source.getSourcePath())) return tree;
                Path path = source.getSourcePath().normalize();
                String fileName = path.getFileName().toString();
                if ("log4j.properties".equalsIgnoreCase(fileName) || "log4j.xml".equalsIgnoreCase(fileName)) {
                    return mark(tree, LOG4J_CONFIG_MESSAGE);
                }
                String normalized = path.toString().replace('\\', '/');
                if (normalized.endsWith("META-INF/services/org.slf4j.spi.SLF4JServiceProvider")) {
                    return tree instanceof PlainText text ? markProviderLines(text) : mark(tree, PROVIDER_MESSAGE);
                }
                if (tree instanceof Properties.File properties) {
                    return new PropertiesIsoVisitor<ExecutionContext>() {
                        @Override
                        public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                            Properties.Entry visited = super.visitEntry(entry, ec);
                            String key = visited.getKey();
                            String value = visited.getValue().getText().trim();
                            if ("slf4j.provider".equals(key)) return mark(visited, PROVIDER_MESSAGE);
                            return LOCATION_KEYS.contains(key) && legacyLocation(value)
                                    ? mark(visited, LOG4J_CONFIG_MESSAGE) : visited;
                        }
                    }.visitNonNull(properties, ctx);
                }
                if (tree instanceof Xml.Document xml && !"pom.xml".equals(fileName)) {
                    return new XmlIsoVisitor<ExecutionContext>() {
                        @Override
                        public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                            Xml.Tag visited = super.visitTag(tag, ec);
                            return LOCATION_KEYS.contains(visited.getName()) && visited.getValue().map(String::trim)
                                    .filter(FindLog4jOverSlf4jConfigurationRisks::legacyLocation).isPresent()
                                    ? mark(visited, LOG4J_CONFIG_MESSAGE) : visited;
                        }
                    }.visitNonNull(xml, ctx);
                }
                return tree;
            }
        };
    }

    private static boolean legacyLocation(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("log4j.properties") || lower.contains("log4j.xml");
    }

    private static PlainText markProviderLines(PlainText text) {
        String source = text.getText();
        Matcher matcher = PROVIDER_LINE.matcher(source);
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start(1) > cursor) {
                snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                        source.substring(cursor, matcher.start(1))));
            }
            snippets.add(SearchResult.found(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, matcher.group(1)),
                    PROVIDER_MESSAGE));
            cursor = matcher.end(1);
        }
        if (snippets.isEmpty()) return text;
        if (cursor < source.length()) {
            snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        }
        return text.withText("").withSnippets(snippets);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
