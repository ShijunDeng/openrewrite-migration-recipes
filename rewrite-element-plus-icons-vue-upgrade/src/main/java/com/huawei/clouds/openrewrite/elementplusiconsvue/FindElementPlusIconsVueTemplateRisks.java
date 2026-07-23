package com.huawei.clouds.openrewrite.elementplusiconsvue;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark exact Vue markup/style constructs that require component and visual migration. */
public final class FindElementPlusIconsVueTemplateRisks extends Recipe {
    private static final List<Risk> MARKUP = List.of(
            risk("<i\\b[^>]*\\bclass\\s*=\\s*(['\"])[^'\"]*el-icon-[a-z0-9-]+[^'\"]*\\1[^>]*>",
                    "Element Plus 2 removed font-class icons; import the matching Vue icon component, wrap it in el-icon, and verify size/color/alignment/accessibility"),
            risk("<(?:el-[a-z0-9-]+)\\b[^>]*\\bicon\\s*=\\s*(['\"])el-icon-[a-z0-9-]+\\1[^>]*>",
                    "This legacy string icon prop must become a bound imported component; select the correct icon and verify the owning Element Plus component API"),
            risk("<el-icon\\b[^>]*>\\s*<component\\b[^>]*\\s:is\\s*=\\s*(['\"])[^'\"]+\\1[^>]*>",
                    "Dynamic icon component selection needs explicit component references or a deliberate registry; verify casing, fallback, SSR serialization, and hydration"));
    private static final List<Risk> STYLES = List.of(
            risk("(?<![A-Za-z0-9_-])\\.el-icon-[a-z0-9-]+(?:\\b|(?=[.:#\\s>+~\\[]))",
                    "This selector targets the removed Element Plus font-icon class contract; move styling to el-icon/the imported SVG component and verify visual states"));

    @Override
    public String getDisplayName() {
        return "Find Element Plus icon template and style risks";
    }

    @Override
    public String getDescription() {
        return "Marks legacy font-icon tags/props/selectors and dynamic component rendering as exact snippets while ignoring comments and generated/install/cache paths.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!ElementPlusIconsVueSupport.isProjectPath(visited.getSourcePath())) return visited;
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (path.endsWith(".vue") || path.endsWith(".html") || path.endsWith(".htm")) {
                    return mark(visited, MARKUP, true);
                }
                if (path.endsWith(".css") || path.endsWith(".scss") || path.endsWith(".sass") || path.endsWith(".less")) {
                    return mark(visited, STYLES, false);
                }
                return visited;
            }
        };
    }

    private static PlainText mark(PlainText text, List<Risk> risks, boolean markup) {
        String source = text.getText();
        boolean[] visible = visible(source, markup);
        List<Match> matches = new ArrayList<>();
        for (Risk risk : risks) {
            Matcher matcher = risk.pattern.matcher(source);
            while (matcher.find()) if (visible[matcher.start()]) {
                matches.add(new Match(matcher.start(), matcher.end(), risk.message));
            }
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(match -> -match.end()));
        List<Match> filtered = new ArrayList<>();
        int end = -1;
        for (Match match : matches) if (match.start >= end) {
            filtered.add(match);
            end = match.end;
        }
        if (filtered.isEmpty()) return text;
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : filtered) {
            if (cursor < match.start) snippets.add(new PlainText.Snippet(
                    Tree.randomId(), Markers.EMPTY, source.substring(cursor, match.start)));
            snippets.add(SearchResult.found(new PlainText.Snippet(
                    Tree.randomId(), Markers.EMPTY, source.substring(match.start, match.end)), match.message));
            cursor = match.end;
        }
        if (cursor < source.length()) snippets.add(new PlainText.Snippet(
                Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private static boolean[] visible(String source, boolean markup) {
        boolean[] result = new boolean[source.length() + 1];
        String open = markup ? "<!--" : "/*";
        String close = markup ? "-->" : "*/";
        boolean block = false;
        boolean line = false;
        for (int i = 0; i < source.length(); i++) {
            if (!block && !line && source.startsWith(open, i)) block = true;
            if (!markup && !block && !line && source.startsWith("//", i)) line = true;
            result[i] = !block && !line;
            if (block && source.startsWith(close, i)) {
                for (int j = 0; j < close.length() && i + j < source.length(); j++) result[i + j] = false;
                i += close.length() - 1;
                block = false;
            } else if (line && (source.charAt(i) == '\n' || source.charAt(i) == '\r')) line = false;
        }
        result[source.length()] = !block && !line;
        return result;
    }

    private static Risk risk(String expression, String message) {
        return new Risk(Pattern.compile(expression, Pattern.CASE_INSENSITIVE), message);
    }

    private record Risk(Pattern pattern, String message) { }
    private record Match(int start, int end, String message) { }
}
