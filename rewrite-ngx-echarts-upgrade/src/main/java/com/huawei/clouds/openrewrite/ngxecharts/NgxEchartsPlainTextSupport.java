package com.huawei.clouds.openrewrite.ngxecharts;

import org.openrewrite.Tree;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NgxEchartsPlainTextSupport {
    private NgxEchartsPlainTextSupport() {
    }

    static PlainText mark(PlainText text, List<Risk> risks, boolean html) {
        boolean[] allowed = new boolean[text.getText().length() + 1];
        java.util.Arrays.fill(allowed, true);
        return mark(text, risks, html, allowed);
    }

    static PlainText mark(PlainText text, List<Risk> risks, boolean html, boolean[] allowed) {
        String source = text.getText();
        boolean[] visible = visiblePositions(source, html);
        List<Match> matches = new ArrayList<>();
        for (Risk risk : risks) {
            Matcher matcher = risk.pattern().matcher(source);
            while (matcher.find()) {
                if (visible[matcher.start()] && allowed[matcher.start()]) {
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
        if (retained.isEmpty()) return text;
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Match match : retained) {
            if (cursor < match.start()) {
                snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                        source.substring(cursor, match.start())));
            }
            snippets.add(SearchResult.found(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY,
                    source.substring(match.start(), match.end())), match.message()));
            cursor = match.end();
        }
        if (cursor < source.length()) {
            snippets.add(new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source.substring(cursor)));
        }
        return text.withText("").withSnippets(snippets);
    }

    static boolean[] visiblePositions(String source, boolean html) {
        boolean[] visible = new boolean[source.length() + 1];
        State state = State.CODE;
        char quote = '\0';
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            visible[index] = state == State.CODE;
            if (state == State.STRING) {
                if (current == '\\') index++;
                else if (current == quote) state = State.CODE;
            } else if (state == State.HTML && source.startsWith("-->", index)) {
                index += 2;
                state = State.CODE;
            } else if (state == State.BLOCK && current == '*' && next == '/') {
                index++;
                state = State.CODE;
            } else if (state == State.LINE && (current == '\n' || current == '\r')) {
                state = State.CODE;
            } else if (state == State.CODE && html && source.startsWith("<!--", index)) {
                visible[index] = false;
                index += 3;
                state = State.HTML;
            } else if (state == State.CODE && !html && current == '/' && next == '*') {
                visible[index] = false;
                index++;
                state = State.BLOCK;
            } else if (state == State.CODE && !html && current == '/' && next == '/') {
                visible[index] = false;
                index++;
                state = State.LINE;
            } else if (state == State.CODE && !html && (current == '\'' || current == '"')) {
                quote = current;
                state = State.STRING;
            }
        }
        visible[source.length()] = state == State.CODE;
        return visible;
    }

    record Risk(Pattern pattern, String message) {
    }

    private record Match(int start, int end, String message) {
    }

    private enum State {
        CODE, STRING, HTML, BLOCK, LINE
    }
}
