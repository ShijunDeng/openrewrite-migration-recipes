package com.huawei.clouds.openrewrite.bootstrap;

import org.openrewrite.Tree;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BootstrapPlainTextSupport {
    private BootstrapPlainTextSupport() {
    }

    static PlainText mark(PlainText text, List<Risk> risks, boolean markup) {
        String source = text.getText();
        boolean[] comments = commentPositions(source, markup);
        List<Match> matches = new ArrayList<>();
        for (Risk risk : risks) {
            Matcher matcher = risk.pattern().matcher(source);
            while (matcher.find()) if (!comments[matcher.start()]) {
                matches.add(new Match(matcher.start(), matcher.end(), risk.message()));
            }
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparingInt(match -> -match.end()));
        List<Match> retained = new ArrayList<>();
        int end = -1;
        for (Match match : matches) if (match.start() >= end) {
            retained.add(match);
            end = match.end();
        }
        if (retained.isEmpty()) return text;
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : retained) {
            if (cursor < match.start()) snippets.add(new PlainText.Snippet(
                    Tree.randomId(), Markers.EMPTY, source.substring(cursor, match.start())));
            snippets.add(SearchResult.found(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start(), match.end())), match.message()));
            cursor = match.end();
        }
        if (cursor < source.length()) snippets.add(new PlainText.Snippet(
                Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private static boolean[] commentPositions(String source, boolean markup) {
        boolean[] comments = new boolean[source.length() + 1];
        State state = State.CODE;
        for (int i = 0; i < source.length(); i++) {
            comments[i] = state != State.CODE;
            if (state == State.CODE && markup && source.startsWith("<!--", i)) {
                for (int j = 0; j < 4 && i + j < source.length(); j++) comments[i + j] = true;
                i += 3;
                state = State.BLOCK;
            } else if (state == State.CODE && !markup && source.startsWith("/*", i)) {
                comments[i] = comments[i + 1] = true;
                i++;
                state = State.BLOCK;
            } else if (state == State.CODE && !markup && source.startsWith("//", i)) {
                comments[i] = comments[i + 1] = true;
                i++;
                state = State.LINE;
            } else if (state == State.BLOCK && markup && source.startsWith("-->", i)) {
                for (int j = 0; j < 3 && i + j < source.length(); j++) comments[i + j] = true;
                i += 2;
                state = State.CODE;
            } else if (state == State.BLOCK && !markup && source.startsWith("*/", i)) {
                comments[i] = comments[i + 1] = true;
                i++;
                state = State.CODE;
            } else if (state == State.LINE && (source.charAt(i) == '\n' || source.charAt(i) == '\r')) {
                state = State.CODE;
            }
        }
        return comments;
    }

    static Risk risk(String expression, String message) {
        return new Risk(Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE), message);
    }

    record Risk(Pattern pattern, String message) {
    }

    private record Match(int start, int end, String message) {
    }

    private enum State { CODE, BLOCK, LINE }
}
