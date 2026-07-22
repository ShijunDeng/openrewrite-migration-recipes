package com.huawei.clouds.openrewrite.echarts;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rewrites ECharts constructs whose target public equivalent is one-to-one. */
public final class RewriteDeterministicEChartsSource extends Recipe {
    private static final Pattern SOURCE_EXTENSION = Pattern.compile(".*\\.(?:[cm]?[jt]sx?)$");
    private static final Pattern MODULE = Pattern.compile(
            "(?<prefix>\\bfrom\\s*|\\bimport\\s*(?:\\(\\s*)?|\\brequire\\s*\\(\\s*)" +
            "(?<quote>[\"'])(?<path>echarts/(?:lib/echarts(?:[.]js)?|src/echarts(?:[.]ts)?|src/theme/light(?:[.]ts)?))\\k<quote>"
    );
    private static final Pattern LEGACY_OPTION_TYPE = Pattern.compile("\\becharts[.]EChartOption\\b");

    @Override
    public String getDisplayName() {
        return "Rewrite deterministic ECharts public source constructs";
    }

    @Override
    public String getDescription() {
        return "Move legacy full-build and light-theme module specifiers to exported ECharts 6 entries, and " +
               "rename the legacy namespace EChartOption type without changing comments or string content.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!SOURCE_EXTENSION.matcher(visited.getSourcePath().toString().toLowerCase(Locale.ROOT)).matches()) {
                    return visited;
                }
                String migrated = replaceCodeMatches(visited.getText(), MODULE, matcher -> {
                    String path = matcher.group("path");
                    String target = path.startsWith("echarts/src/theme/light") ?
                            "echarts/theme/rainbow.js" : "echarts";
                    return matcher.group("prefix") + matcher.group("quote") + target + matcher.group("quote");
                });
                migrated = replaceCodeMatches(migrated, LEGACY_OPTION_TYPE, matcher -> "echarts.EChartsOption");
                return visited.withText(migrated);
            }
        };
    }

    private static String replaceCodeMatches(String source, Pattern pattern, Replacement replacement) {
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
            }
        }
        code[source.length()] = state == State.CODE;
        return code;
    }

    @FunctionalInterface
    private interface Replacement {
        String apply(Matcher matcher);
    }

    private enum State {
        CODE('\0'), SINGLE_QUOTE('\''), DOUBLE_QUOTE('"'), TEMPLATE('`'), LINE_COMMENT('\0'), BLOCK_COMMENT('\0');

        private final char terminator;

        State(char terminator) {
            this.terminator = terminator;
        }
    }
}
