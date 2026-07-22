package com.huawei.clouds.openrewrite.datefns;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rewrite date-fns v2 imports whose v3/v4 public replacement is one-to-one. */
public final class RewriteDeterministicDateFnsSource extends Recipe {
    private static final Pattern SOURCE_EXTENSION = Pattern.compile(".*[.](?:[cm]?[jt]sx?)$");
    private static final Pattern DEFAULT_FUNCTION_IMPORT = Pattern.compile(
            "\\bimport(?<space1>[ \\t]+)(?<symbol>[A-Za-z_$][A-Za-z0-9_$]*)(?<space2>[ \\t]+)" +
            "from(?<space3>[ \\t]*)(?<quote>[\"'])date-fns/\\k<symbol>(?:/index(?:[.]js)?)?\\k<quote>");
    private static final Pattern COMMON_JS_FUNCTION = Pattern.compile(
            "\\b(?<kind>const|let|var)(?<space1>[ \\t]+)(?<symbol>[A-Za-z_$][A-Za-z0-9_$]*)" +
            "(?<equals>[ \\t]*=[ \\t]*)require(?<open>[ \\t]*\\([ \\t]*)(?<quote>[\"'])" +
            "date-fns/\\k<symbol>(?:/index(?:[.]js)?)?\\k<quote>(?<close>[ \\t]*\\))");
    private static final Pattern PUBLIC_INDEX_PATH = Pattern.compile(
            "(?<prefix>\\bfrom[ \\t]*|\\bimport[ \\t]*(?:\\([ \\t]*)?|\\brequire[ \\t]*\\([ \\t]*)" +
            "(?<quote>[\"'])(?<path>date-fns/(?!_lib/)[A-Za-z0-9_$/-]+)/index(?:[.]js)?\\k<quote>");

    @Override
    public String getDisplayName() {
        return "Rewrite deterministic date-fns v3/v4 source imports";
    }

    @Override
    public String getDescription() {
        return "Convert exact default function subpath imports and CommonJS assignments to named exports and " +
               "remove legacy public /index.js suffixes without changing comments, strings, aliases, or _lib imports.";
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
                String migrated = replaceCodeMatches(visited.getText(), DEFAULT_FUNCTION_IMPORT, matcher ->
                        "import" + matcher.group("space1") + "{ " + matcher.group("symbol") + " }" +
                        matcher.group("space2") + "from" + matcher.group("space3") + matcher.group("quote") +
                        "date-fns/" + matcher.group("symbol") + matcher.group("quote"));
                migrated = replaceCodeMatches(migrated, COMMON_JS_FUNCTION, matcher ->
                        matcher.group("kind") + matcher.group("space1") + "{ " + matcher.group("symbol") + " }" +
                        matcher.group("equals") + "require" + matcher.group("open") + matcher.group("quote") +
                        "date-fns/" + matcher.group("symbol") + matcher.group("quote") + matcher.group("close"));
                migrated = replaceCodeMatches(migrated, PUBLIC_INDEX_PATH, matcher ->
                        matcher.group("prefix") + matcher.group("quote") + matcher.group("path") + matcher.group("quote"));
                return visited.withText(migrated);
            }
        };
    }

    private static String replaceCodeMatches(String source, Pattern pattern, Function<Matcher, String> replacement) {
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

    private enum State {
        CODE('\0'), SINGLE_QUOTE('\''), DOUBLE_QUOTE('"'), TEMPLATE('`'), LINE_COMMENT('\0'), BLOCK_COMMENT('\0');

        private final char terminator;

        State(char terminator) {
            this.terminator = terminator;
        }
    }
}
