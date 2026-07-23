package com.huawei.clouds.openrewrite.commonscli;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Migrates the 1.5 automatic JPMS name only in real requires directives. */
public final class MigrateCommonsCliModuleName extends Recipe {
    private static final Pattern REQUIRES = Pattern.compile(
            "\\brequires\\s+(?:(?:static|transitive)\\s+)*(commons[.]cli)(?=\\s*;)");

    @Override
    public String getDisplayName() {
        return "Migrate the Commons CLI JPMS module name";
    }

    @Override
    public String getDescription() {
        return "Changes real module-info.java requires directives from the 1.5 automatic name commons.cli to the 1.9 explicit name org.apache.commons.cli while ignoring comments and literals.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    !"module-info.java".equals(source.getSourcePath().getFileName().toString()) ||
                    CommonsCliSupport.generated(source.getSourcePath())) return tree;
                PlainText plainText = PlainTextParser.convert(source);
                String migrated = migrate(plainText.getText());
                return migrated.equals(plainText.getText()) ? tree : plainText.withText(migrated);
            }
        };
    }

    static String migrate(String source) {
        String masked = maskCommentsAndLiterals(source);
        Matcher matcher = REQUIRES.matcher(masked);
        StringBuilder result = new StringBuilder(source.length() + 32);
        int copied = 0;
        boolean changed = false;
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            result.append(source, copied, start).append("org.apache.commons.cli");
            copied = end;
            changed = true;
        }
        return changed ? result.append(source, copied, source.length()).toString() : source;
    }

    private static String maskCommentsAndLiterals(String source) {
        char[] masked = source.toCharArray();
        int state = 0; // 0 code, 1 line comment, 2 block comment, 3 string, 4 char, 5 text block
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (state == 0) {
                if (current == '/' && next == '/') {
                    masked[i] = masked[i + 1] = ' ';
                    i++;
                    state = 1;
                } else if (current == '/' && next == '*') {
                    masked[i] = masked[i + 1] = ' ';
                    i++;
                    state = 2;
                } else if (current == '"' && i + 2 < source.length() && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                    masked[i] = masked[i + 1] = masked[i + 2] = ' ';
                    i += 2;
                    state = 5;
                } else if (current == '"') {
                    masked[i] = ' ';
                    state = 3;
                    escaped = false;
                } else if (current == '\'') {
                    masked[i] = ' ';
                    state = 4;
                    escaped = false;
                }
            } else if (state == 1) {
                if (current == '\n' || current == '\r') state = 0;
                else masked[i] = ' ';
            } else if (state == 2) {
                if (current == '*' && next == '/') {
                    masked[i] = masked[i + 1] = ' ';
                    i++;
                    state = 0;
                } else if (current != '\n' && current != '\r') masked[i] = ' ';
            } else if (state == 5) {
                if (current == '"' && i + 2 < source.length() && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                    masked[i] = masked[i + 1] = masked[i + 2] = ' ';
                    i += 2;
                    state = 0;
                } else if (current != '\n' && current != '\r') masked[i] = ' ';
            } else {
                if (current != '\n' && current != '\r') masked[i] = ' ';
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if ((state == 3 && current == '"') || (state == 4 && current == '\'')) state = 0;
            }
        }
        return new String(masked);
    }
}
