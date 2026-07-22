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

/** Adds precise snippet markers to Angular HTML constructs whose migration needs template semantics. */
public final class FindAngularCommonTemplateRisks extends Recipe {
    private static final List<RiskPattern> RISKS = List.of(
            new RiskPattern(Pattern.compile("\\*ng(?:If|For|SwitchCase|SwitchDefault)\\b|\\[ngSwitch\\]"),
                    "Structural control-flow directives are deprecated; migrate to @if/@for/@switch and choose a stable @for track expression"),
            new RiskPattern(Pattern.compile("\\*ngTemplateOutlet\\b"),
                    "NgTemplateOutlet context is strictly typed; align every context property with TemplateRef<T>"),
            new RiskPattern(Pattern.compile("\\|\\s*async\\b"),
                    "AsyncPipe now reports unhandled subscription/promise errors directly to ErrorHandler; test the intended error owner"),
            new RiskPattern(Pattern.compile("\\bdate\\s*:\\s*(['\"])[^'\"]*Y[^'\"]*\\1"),
                    "Angular 20 rejects a week-year Y format without week number w; choose calendar-year y or add the intended week field"),
            new RiskPattern(Pattern.compile("\\[ngSrc\\]|\\bngSrc\\b"),
                    "NgOptimizedImage needs verified dimensions, loader/CDN, priority, preconnect, SSR preload, and hydration behavior"),
            new RiskPattern(Pattern.compile("\\bng-reflect-[A-Za-z0-9_-]+"),
                    "Angular 20 no longer emits ng-reflect attributes by default; replace this selector/assertion with public DOM behavior or a harness")
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Common 20 template migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks deprecated control flow, strict template outlets, AsyncPipe errors, suspicious date formats, " +
               "optimized images, and ng-reflect dependencies at exact HTML snippets.";
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
                return markMatches(visited);
            }
        };
    }

    private static PlainText markMatches(PlainText text) {
        String source = text.getText();
        List<RiskMatch> matches = new ArrayList<>();
        for (RiskPattern risk : RISKS) {
            Matcher matcher = risk.pattern.matcher(source);
            while (matcher.find()) {
                if (risk.message.startsWith("Angular 20 rejects") && matcher.group().contains("w")) {
                    continue;
                }
                matches.add(new RiskMatch(matcher.start(), matcher.end(), risk.message));
            }
        }
        matches.sort(Comparator.comparingInt(RiskMatch::start).thenComparingInt(match -> -match.end));
        List<RiskMatch> selected = new ArrayList<>();
        int end = -1;
        for (RiskMatch match : matches) {
            if (match.start >= end) {
                selected.add(match);
                end = match.end;
            }
        }
        if (selected.isEmpty()) return text;

        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (RiskMatch match : selected) {
            if (match.start > cursor) {
                snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source.substring(cursor, match.start)));
            }
            PlainText.Snippet risky = new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start, match.end));
            snippets.add(SearchResult.found(risky, match.message));
            cursor = match.end;
        }
        if (cursor < source.length()) {
            snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        }
        return text.withText("").withSnippets(snippets);
    }

    private record RiskPattern(Pattern pattern, String message) {
    }

    private record RiskMatch(int start, int end, String message) {
    }
}
