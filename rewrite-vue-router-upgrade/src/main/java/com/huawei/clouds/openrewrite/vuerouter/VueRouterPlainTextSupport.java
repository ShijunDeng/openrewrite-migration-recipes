package com.huawei.clouds.openrewrite.vuerouter;

import org.openrewrite.Tree;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VueRouterPlainTextSupport {
    private VueRouterPlainTextSupport() {
    }

    static boolean isTemplateOrDeclaration(PlainText text) {
        String path = text.getSourcePath().toString().toLowerCase(Locale.ROOT);
        return path.endsWith(".vue") || path.endsWith(".html") || path.endsWith(".d.ts");
    }

    static PlainText mark(PlainText text, List<Risk> risks) {
        String source = text.getText();
        boolean[] comment = commentPositions(source);
        boolean declaration = text.getSourcePath().toString().toLowerCase(Locale.ROOT).endsWith(".d.ts");
        List<Match> matches = new ArrayList<>();
        for (Risk risk : risks) {
            Matcher matcher = risk.pattern().matcher(source);
            while (matcher.find()) {
                if (declaration || !comment[matcher.start()]) {
                    matches.add(new Match(matcher.start(), matcher.end(), risk.message()));
                }
            }
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(match -> -match.end()));
        List<Match> retained = new ArrayList<>();
        int end = -1;
        for (Match match : matches) {
            if (match.start() >= end) {
                retained.add(match);
                end = match.end();
            }
        }
        if (retained.isEmpty()) {
            return text;
        }
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : retained) {
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

    private static boolean[] commentPositions(String source) {
        boolean[] comment = new boolean[source.length() + 1];
        CommentState state = CommentState.CODE;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            comment[index] = state != CommentState.CODE;
            if (state == CommentState.HTML && source.startsWith("-->", index)) {
                comment[index] = comment[index + 1] = comment[index + 2] = true;
                index += 2;
                state = CommentState.CODE;
            } else if (state == CommentState.BLOCK && current == '*' && next == '/') {
                comment[index + 1] = true;
                index++;
                state = CommentState.CODE;
            } else if (state == CommentState.LINE && (current == '\n' || current == '\r')) {
                state = CommentState.CODE;
            } else if (state == CommentState.CODE && source.startsWith("<!--", index)) {
                comment[index] = comment[index + 1] = comment[index + 2] = comment[index + 3] = true;
                index += 3;
                state = CommentState.HTML;
            } else if (state == CommentState.CODE && current == '/' && next == '*') {
                comment[index] = comment[index + 1] = true;
                index++;
                state = CommentState.BLOCK;
            } else if (state == CommentState.CODE && current == '/' && next == '/') {
                comment[index] = comment[index + 1] = true;
                index++;
                state = CommentState.LINE;
            }
        }
        comment[source.length()] = state != CommentState.CODE;
        return comment;
    }

    record Risk(Pattern pattern, String message) {
    }

    private record Match(int start, int end, String message) {
    }

    private enum CommentState {
        CODE, HTML, BLOCK, LINE
    }
}
