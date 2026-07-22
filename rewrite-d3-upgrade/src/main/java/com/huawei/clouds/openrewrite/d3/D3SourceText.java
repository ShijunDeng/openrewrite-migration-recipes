package com.huawei.clouds.openrewrite.d3;

import org.openrewrite.Tree;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class D3SourceText {
    private static final Pattern SOURCE_EXTENSION = Pattern.compile(".*\\.(?:[cm]?[jt]sx?)$");
    private static final Pattern NAMESPACE_IMPORT = Pattern.compile(
            "\\bimport\\s*\\*\\s*as\\s*(?<alias>[A-Za-z_$][\\w$]*)\\s*from\\s*(?<quote>[\"'])(?:d3|d3-array)\\k<quote>");
    private static final Pattern REQUIRE = Pattern.compile(
            "\\b(?:const|let|var)\\s+(?<alias>[A-Za-z_$][\\w$]*)\\s*=\\s*require\\(\\s*(?<quote>[\"'])d3\\k<quote>\\s*\\)");

    private D3SourceText() {
    }

    static boolean isSupported(PlainText text) {
        return SOURCE_EXTENSION.matcher(text.getSourcePath().toString().toLowerCase(Locale.ROOT)).matches();
    }

    static Set<String> namespaceAliases(String source) {
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add("d3");
        collectAliases(source, NAMESPACE_IMPORT, aliases);
        collectAliases(source, REQUIRE, aliases);
        return aliases;
    }

    private static void collectAliases(String source, Pattern pattern, Set<String> aliases) {
        boolean[] code = codePositions(source);
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            if (code[matcher.start()]) {
                aliases.add(matcher.group("alias"));
            }
        }
    }

    static String replaceCodeMatches(String source, Pattern pattern, Function<Matcher, String> replacement) {
        boolean[] code = codePositions(source);
        Matcher matcher = pattern.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        int last = 0;
        boolean changed = false;
        while (matcher.find()) {
            if (!code[matcher.start()]) {
                continue;
            }
            result.append(source, last, matcher.start()).append(replacement.apply(matcher));
            last = matcher.end();
            changed = true;
        }
        return changed ? result.append(source, last, source.length()).toString() : source;
    }

    static PlainText markMatches(PlainText text, List<RiskPattern> risks) {
        String source = text.getText();
        boolean[] code = codePositions(source);
        List<RiskMatch> matches = new ArrayList<>();
        for (RiskPattern risk : risks) {
            Matcher matcher = risk.pattern().matcher(source);
            while (matcher.find()) {
                if (code[matcher.start()]) {
                    matches.add(new RiskMatch(matcher.start(), matcher.end(), risk.message()));
                }
            }
        }
        matches.sort(Comparator.comparingInt(RiskMatch::start).thenComparingInt(match -> -match.end()));
        List<RiskMatch> nonOverlapping = new ArrayList<>();
        int end = -1;
        for (RiskMatch match : matches) {
            if (match.start() >= end) {
                nonOverlapping.add(match);
                end = match.end();
            }
        }
        if (nonOverlapping.isEmpty()) {
            return text;
        }
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (RiskMatch match : nonOverlapping) {
            if (match.start() > cursor) {
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

    static Pattern memberCall(Set<String> aliases, String methods) {
        return Pattern.compile(memberPrefix(aliases, methods) + "(?=\\s*\\()");
    }

    static Pattern member(Set<String> aliases, String properties) {
        return Pattern.compile(memberPrefix(aliases, properties) + "\\b");
    }

    private static String memberPrefix(Set<String> aliases, String members) {
        String joined = aliases.stream().map(Pattern::quote).reduce((left, right) -> left + "|" + right).orElse("d3");
        return "\\b(?<alias>" + joined + ")(?<dot>\\s*[.]\\s*)(?<method>" + members + ")";
    }

    private static boolean[] codePositions(String source) {
        boolean[] code = new boolean[source.length() + 1];
        State state = State.CODE;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            code[index] = state == State.CODE;
            if (state == State.LINE_COMMENT) {
                if (current == '\n' || current == '\r') {
                    state = State.CODE;
                }
            } else if (state == State.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    code[index + 1] = false;
                    index++;
                    state = State.CODE;
                }
            } else if (state != State.CODE) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == state.terminator) {
                    state = State.CODE;
                }
            } else if (current == '/' && next == '/') {
                code[index + 1] = false;
                index++;
                state = State.LINE_COMMENT;
            } else if (current == '/' && next == '*') {
                code[index + 1] = false;
                index++;
                state = State.BLOCK_COMMENT;
            } else if (current == '\'') {
                state = State.SINGLE_QUOTE;
            } else if (current == '"') {
                state = State.DOUBLE_QUOTE;
            } else if (current == '`') {
                state = State.TEMPLATE;
            } else if (current == '/' && looksLikeRegexStart(source, index)) {
                state = State.REGEX;
            }
        }
        code[source.length()] = state == State.CODE;
        return code;
    }

    private static boolean looksLikeRegexStart(String source, int slash) {
        int index = slash - 1;
        while (index >= 0 && Character.isWhitespace(source.charAt(index))) {
            index--;
        }
        return index < 0 || "=(:,![{;?".indexOf(source.charAt(index)) >= 0;
    }

    record RiskPattern(Pattern pattern, String message) {
    }

    private record RiskMatch(int start, int end, String message) {
    }

    private enum State {
        CODE('\0'), SINGLE_QUOTE('\''), DOUBLE_QUOTE('"'), TEMPLATE('`'), REGEX('/'),
        LINE_COMMENT('\0'), BLOCK_COMMENT('\0');

        private final char terminator;

        State(char terminator) {
            this.terminator = terminator;
        }
    }
}
