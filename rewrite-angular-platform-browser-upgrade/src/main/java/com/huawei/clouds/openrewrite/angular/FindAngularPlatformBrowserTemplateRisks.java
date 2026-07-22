package com.huawei.clouds.openrewrite.angular;

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

/** Mark platform-browser hydration, debug, gesture and unsafe URL risks in Angular templates. */
public final class FindAngularPlatformBrowserTemplateRisks extends Recipe {
    private static final List<Risk> RISKS = List.of(
            new Risk(Pattern.compile("\\bngSkipHydration\\b"),
                    "ngSkipHydration disables reconciliation for this host; remove it only after fixing DOM mismatch/direct manipulation and validating event replay"),
            new Risk(Pattern.compile("\\bng-reflect-[A-Za-z0-9_-]+"),
                    "Angular 20 no longer emits ng-reflect attributes by default; replace this selector/assertion with roles, harnesses or stable data attributes"),
            new Risk(Pattern.compile("\\((?:tap|swipe|pan|pinch|press|rotate)[^)]*\\)"),
                    "Gesture event may rely on deprecated HammerJS integration; migrate to Pointer Events or a maintained gesture provider"),
            new Risk(Pattern.compile("\\b(?:src|href)\\s*=\\s*[\"']javascript:"),
                    "Unsafe javascript URL crosses Angular sanitization and CSP/Trusted Types boundaries; replace it with an event handler and trusted navigation")
    );

    @Override
    public String getDisplayName() {
        return "Find Angular 20 platform-browser template risks";
    }

    @Override
    public String getDescription() {
        return "Mark hydration opt-outs, removed debug attributes, Hammer-style gestures and unsafe URLs in HTML templates while ignoring comments.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!visited.getSourcePath().toString().toLowerCase(Locale.ROOT).endsWith(".html")) {
                    return visited;
                }
                return mark(visited);
            }
        };
    }

    private static PlainText mark(PlainText text) {
        String source = text.getText();
        boolean[] visible = visiblePositions(source);
        List<Match> matches = new ArrayList<>();
        for (Risk risk : RISKS) {
            Matcher matcher = risk.pattern().matcher(source);
            while (matcher.find()) {
                if (visible[matcher.start()]) {
                    matches.add(new Match(matcher.start(), matcher.end(), risk.message()));
                }
            }
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(match -> -match.end()));
        List<Match> filtered = new ArrayList<>();
        int lastEnd = -1;
        for (Match match : matches) {
            if (match.start() >= lastEnd) {
                filtered.add(match);
                lastEnd = match.end();
            }
        }
        if (filtered.isEmpty()) {
            return text;
        }
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : filtered) {
            if (cursor < match.start()) {
                snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                        source.substring(cursor, match.start())));
            }
            PlainText.Snippet risky = new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start(), match.end()));
            snippets.add(SearchResult.found(risky, match.message()));
            cursor = match.end();
        }
        if (cursor < source.length()) {
            snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        }
        return text.withText("").withSnippets(snippets);
    }

    private static boolean[] visiblePositions(String source) {
        boolean[] visible = new boolean[source.length() + 1];
        boolean comment = false;
        for (int i = 0; i < source.length(); i++) {
            if (!comment && source.startsWith("<!--", i)) {
                comment = true;
            }
            visible[i] = !comment;
            if (comment && source.startsWith("-->", i)) {
                visible[i] = false;
                if (i + 1 < source.length()) visible[i + 1] = false;
                if (i + 2 < source.length()) visible[i + 2] = false;
                i += 2;
                comment = false;
            }
        }
        visible[source.length()] = !comment;
        return visible;
    }

    private record Risk(Pattern pattern, String message) {
    }

    private record Match(int start, int end, String message) {
    }
}
