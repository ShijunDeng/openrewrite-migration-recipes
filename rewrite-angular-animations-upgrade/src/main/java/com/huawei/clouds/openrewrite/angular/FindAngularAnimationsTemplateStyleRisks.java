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

/** Mark legacy Angular template triggers and native CSS animation lifecycle boundaries. */
public final class FindAngularAnimationsTemplateStyleRisks extends Recipe {
    private static final List<Risk> TEMPLATE_RISKS = List.of(
            new Risk(Pattern.compile("\\(\\s*@[A-Za-z][\\w-]*\\.(?:start|done)\\s*\\)"),
                    "Legacy animation callback depends on start/done ordering and AnimationEvent shape; preserve business side effects, focus and DOM teardown"),
            new Risk(Pattern.compile("\\[\\s*@\\.disabled\\s*\\]|(?<![\\w-])@\\.disabled"),
                    "Legacy @.disabled inheritance does not map directly to native CSS; design reduced-motion and subtree disable behavior explicitly"),
            new Risk(Pattern.compile("\\[\\s*@[A-Za-z][\\w-]*\\s*\\]|(?<=\\s)@[A-Za-z][\\w-]*(?=\\s*=)"),
                    "Legacy animation trigger binding must be migrated with its complete state/transition DSL and DOM enter/leave lifecycle"),
            new Risk(Pattern.compile("\\(\\s*animate\\.(?:enter|leave)\\s*\\)"),
                    "Native animate callback must call animationComplete for custom functions and handle cancellation, errors and element removal"),
            new Risk(Pattern.compile("\\[\\s*animate\\.(?:enter|leave)\\s*\\]|(?<=\\s)animate\\.(?:enter|leave)(?=\\s*=)"),
                    "Native animate.enter/leave class binding lasts only for the animation lifecycle; verify longest timeline, class cleanup, SSR and hydration")
    );
    private static final List<Risk> STYLE_RISKS = List.of(
            new Risk(Pattern.compile("@starting-style\\b"),
                    "@starting-style defines transition entry state; verify browser support, cascade specificity and server-rendered initial styles"),
            new Risk(Pattern.compile("@keyframes\\s+[-_A-Za-z][-_A-Za-z0-9]*"),
                    "CSS keyframes replacement needs visual checks for easing, fill mode, interruption, composition and final style"),
            new Risk(Pattern.compile("\\bprefers-reduced-motion\\b"),
                    "Reduced-motion media handling must shorten/disable all related timelines without delaying DOM removal or business callbacks"),
            new Risk(Pattern.compile("(?<![-\\w])animation(?:-[-\\w]+)?(?=\\s*:)"),
                    "Native CSS animation timing determines Angular enter/leave class cleanup and leave-node removal; verify the longest animation"),
            new Risk(Pattern.compile("(?<![-\\w])transition(?:-[-\\w]+)?(?=\\s*:)"),
                    "Native CSS transition needs explicit starting/final styles and interruption behavior for Angular enter/leave lifecycle"),
            new Risk(Pattern.compile("::view-transition-(?:old|new|group|image-pair)\\([^)]*\\)"),
                    "View Transition pseudo-element animation spans navigation/document state; verify fallback, reduced motion, SSR and concurrent navigation")
    );

    @Override
    public String getDisplayName() {
        return "Find Angular 20 animation template and style risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact legacy trigger/callback/disable bindings, native animate.enter/leave constructs and CSS " +
               "timeline/reduced-motion boundaries while ignoring comments and unrelated file types.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (path.endsWith(".html")) {
                    return mark(visited, TEMPLATE_RISKS, true);
                }
                if (path.endsWith(".css") || path.endsWith(".scss") || path.endsWith(".sass") ||
                    path.endsWith(".less")) {
                    return mark(visited, STYLE_RISKS, false);
                }
                return visited;
            }
        };
    }

    private static PlainText mark(PlainText text, List<Risk> risks, boolean html) {
        String source = text.getText();
        boolean[] visible = visiblePositions(source, html);
        List<Match> matches = new ArrayList<>();
        for (Risk risk : risks) {
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
        if (filtered.isEmpty()) return text;

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

    private static boolean[] visiblePositions(String source, boolean html) {
        boolean[] visible = new boolean[source.length() + 1];
        boolean blockComment = false;
        boolean lineComment = false;
        String start = html ? "<!--" : "/*";
        String end = html ? "-->" : "*/";
        for (int i = 0; i < source.length(); i++) {
            if (!blockComment && !lineComment && source.startsWith(start, i)) blockComment = true;
            if (!html && !blockComment && !lineComment && source.startsWith("//", i) && linePrefixWhitespace(source, i)) {
                lineComment = true;
            }
            visible[i] = !blockComment && !lineComment;
            if (blockComment && source.startsWith(end, i)) {
                for (int j = 0; j < end.length() && i + j < source.length(); j++) visible[i + j] = false;
                i += end.length() - 1;
                blockComment = false;
            } else if (lineComment && (source.charAt(i) == '\n' || source.charAt(i) == '\r')) {
                lineComment = false;
            }
        }
        visible[source.length()] = !blockComment && !lineComment;
        return visible;
    }

    private static boolean linePrefixWhitespace(String source, int position) {
        for (int i = position - 1; i >= 0 && source.charAt(i) != '\n' && source.charAt(i) != '\r'; i--) {
            if (!Character.isWhitespace(source.charAt(i))) return false;
        }
        return true;
    }

    private record Risk(Pattern pattern, String message) {
    }

    private record Match(int start, int end, String message) {
    }
}
