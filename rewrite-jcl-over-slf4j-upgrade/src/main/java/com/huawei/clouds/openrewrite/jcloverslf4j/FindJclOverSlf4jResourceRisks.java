package com.huawei.clouds.openrewrite.jcloverslf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks provider services, Commons Logging discovery files, version catalogs, and runtime configuration. */
public final class FindJclOverSlf4jResourceRisks extends Recipe {
    static final String SERVICE_MESSAGE =
            "SLF4J 2 loads providers through this service descriptor; verify exactly one implementation of the 2.0 SLF4JServiceProvider contract survives shading, JPMS, native-image, test, and production packaging";
    static final String JCL_CONFIG_MESSAGE =
            "jcl-over-slf4j ignores Commons Logging factory/service/TCCL/diagnostics discovery and always delegates to SLF4J; migrate this setting to the selected SLF4J 2 provider or delete it after testing";
    static final String CATALOG_MESSAGE =
            "This catalog/configuration owns jcl-over-slf4j outside direct dependency literals; select 2.0.17 here, align SLF4J API/provider aliases, and inspect every consumer before regenerating locks";

    private static final Pattern JCL_CONFIG = Pattern.compile(
            "(?s).*(?:org[.]apache[.]commons[.]logging[.](?:LogFactory|Log|diagnostics[.]dest)|use_tccl|commons-logging[.]properties).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROVIDER_CONFIG = Pattern.compile(
            "(?s).*(?:slf4j[.]provider|org[.]slf4j[.]impl[.]Static(?:Logger|MDC|Marker)Binder).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROVIDER_LINE = Pattern.compile(
            "(?m)^[ \\t]*([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+)(?=[ \\t]*(?:#|$))");

    @Override
    public String getDisplayName() {
        return "Find JCL-over-SLF4J service and configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks SLF4J provider service files, ignored Commons Logging discovery, explicit provider/binder configuration, and Gradle version catalogs.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !AbstractSelectedJclDependencyRecipe.isProjectPath(source.getSourcePath())) {
                    return tree;
                }
                if (tree instanceof PlainText text) return markText(text);
                if (tree instanceof Xml.Document document && !isPom(document.getSourcePath())) {
                    return markXml(document, ctx);
                }
                return tree;
            }
        };
    }

    private PlainText markText(PlainText text) {
        String value = text.getText();
        String path = normalized(text.getSourcePath());
        if (path.endsWith("meta-inf/services/org.slf4j.spi.slf4jserviceprovider")) {
            return markProviderLines(text);
        }
        boolean catalog = path.endsWith("gradle/libs.versions.toml");
        boolean jclFile = path.endsWith("meta-inf/services/org.apache.commons.logging.logfactory") ||
                          path.endsWith("commons-logging.properties");
        if (!catalog && !jclFile && !JCL_CONFIG.matcher(value).matches() &&
            !PROVIDER_CONFIG.matcher(value).matches()) return text;
        return markRiskLines(text, catalog, jclFile);
    }

    private static PlainText markProviderLines(PlainText text) {
        String source = text.getText();
        Matcher matcher = PROVIDER_LINE.matcher(source);
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start(1) > cursor) snippets.add(snippet(source.substring(cursor, matcher.start(1))));
            snippets.add(SearchResult.found(snippet(matcher.group(1)), SERVICE_MESSAGE));
            cursor = matcher.end(1);
        }
        return withSnippets(text, source, snippets, cursor);
    }

    private static PlainText markRiskLines(PlainText text, boolean catalog, boolean jclFile) {
        String source = text.getText();
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int start = 0;
        while (start < source.length()) {
            int newline = source.indexOf('\n', start);
            int end = newline < 0 ? source.length() : newline;
            String line = source.substring(start, end);
            String trimmed = line.trim();
            String message = null;
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!") &&
                !trimmed.startsWith("//")) {
                if (catalog && catalogOwner(line)) message = CATALOG_MESSAGE;
                else if (PROVIDER_CONFIG.matcher(line).matches()) message = SERVICE_MESSAGE;
                else if (jclFile || JCL_CONFIG.matcher(line).matches()) message = JCL_CONFIG_MESSAGE;
            }
            snippets.add(message == null ? snippet(line) : SearchResult.found(snippet(line), message));
            if (newline >= 0) snippets.add(snippet("\n"));
            start = newline < 0 ? source.length() : newline + 1;
        }
        return snippets.stream().anyMatch(value -> !value.getMarkers().findAll(SearchResult.class).isEmpty())
                ? text.withText("").withSnippets(snippets) : text;
    }

    private static boolean catalogOwner(String line) {
        return line.contains("org.slf4j:jcl-over-slf4j") ||
               (line.matches(".*group\\s*=\\s*[\"']org[.]slf4j[\"'].*") &&
                line.matches(".*name\\s*=\\s*[\"']jcl-over-slf4j[\"'].*"));
    }

    private static PlainText.Snippet snippet(String text) {
        return new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, text);
    }

    private static PlainText withSnippets(PlainText text, String source, List<PlainText.Snippet> snippets, int cursor) {
        if (snippets.isEmpty()) return text;
        if (cursor < source.length()) snippets.add(snippet(source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private Xml.Document markXml(Xml.Document document, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                Xml.CharData visited = super.visitCharData(charData, executionContext);
                String message = message(visited.getText());
                return message == null ? visited : SearchResult.found(visited, message);
            }

            @Override
            public Xml.Attribute visitAttribute(Xml.Attribute attribute, ExecutionContext executionContext) {
                Xml.Attribute visited = super.visitAttribute(attribute, executionContext);
                String message = message(visited.getValueAsString());
                return message == null ? visited : SearchResult.found(visited, message);
            }

            private String message(String value) {
                if (JCL_CONFIG.matcher(value).matches()) return JCL_CONFIG_MESSAGE;
                if (PROVIDER_CONFIG.matcher(value).matches()) return SERVICE_MESSAGE;
                return null;
            }
        }.visitNonNull(document, ctx);
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static boolean isPom(Path path) {
        return path.getFileName() != null && "pom.xml".equals(path.getFileName().toString());
    }
}
