package com.huawei.clouds.openrewrite.ngxecharts;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Remove only obsolete literal inputs from proven ngx-echarts host elements. */
public final class MigrateDeterministicNgxEchartsTemplates extends Recipe {
    private static final Pattern START_TAG = Pattern.compile("(?s)<(?![!/])[^>]+>");
    private static final Pattern ECHARTS_HOST = Pattern.compile(
            "(?i)^<\\s*echarts(?:\\s|/?>)|(?<![-:\\w])(?:\\[)?echarts(?:\\])?(?=\\s|=|/?>)");
    private static final Pattern LITERAL_DETECT_EVENT_CHANGES = Pattern.compile(
            "(?i)\\s+(?:\\[)?detectEventChanges(?:\\])?\\s*=\\s*(['\"])(?:true|false)\\1");

    @Override
    public String getDisplayName() {
        return "Remove deterministic obsolete ngx-echarts template inputs";
    }

    @Override
    public String getDescription() {
        return "Remove literal true/false detectEventChanges bindings only from elements proven to host the " +
               "ngx-echarts directive; dynamic expressions and unrelated components remain for review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!visited.getSourcePath().toString().toLowerCase(Locale.ROOT).endsWith(".html")) return visited;
                String migrated = migrate(visited.getText());
                return migrated.equals(visited.getText()) ? visited : visited.withText(migrated);
            }
        };
    }

    private static String migrate(String source) {
        boolean[] visible = NgxEchartsPlainTextSupport.visiblePositions(source, true);
        Matcher tag = START_TAG.matcher(source);
        StringBuffer migrated = new StringBuffer();
        while (tag.find()) {
            String replacement = tag.group();
            if (visible[tag.start()] && isEchartsHost(replacement)) {
                replacement = removeLiteralDetectEventChanges(replacement);
            }
            tag.appendReplacement(migrated, Matcher.quoteReplacement(replacement));
        }
        tag.appendTail(migrated);
        return migrated.toString();
    }

    static boolean isEchartsHost(String tag) {
        boolean[] outsideAttributeValues = outsideQuotedValues(tag);
        Matcher host = ECHARTS_HOST.matcher(tag);
        while (host.find()) {
            if (outsideAttributeValues[host.start()]) return true;
        }
        return false;
    }

    private static String removeLiteralDetectEventChanges(String tag) {
        boolean[] outsideAttributeValues = outsideQuotedValues(tag);
        Matcher attribute = LITERAL_DETECT_EVENT_CHANGES.matcher(tag);
        StringBuffer migrated = new StringBuffer();
        while (attribute.find()) {
            attribute.appendReplacement(migrated, outsideAttributeValues[attribute.start()]
                    ? "" : Matcher.quoteReplacement(attribute.group()));
        }
        attribute.appendTail(migrated);
        return migrated.toString();
    }

    private static boolean[] outsideQuotedValues(String tag) {
        boolean[] outside = new boolean[tag.length() + 1];
        char quote = '\0';
        for (int index = 0; index < tag.length(); index++) {
            char current = tag.charAt(index);
            outside[index] = quote == '\0';
            if (quote != '\0' && current == '\\') {
                index++;
            } else if (quote != '\0' && current == quote) {
                quote = '\0';
            } else if (quote == '\0' && (current == '\'' || current == '"')) {
                quote = current;
            }
        }
        outside[tag.length()] = quote == '\0';
        return outside;
    }
}
