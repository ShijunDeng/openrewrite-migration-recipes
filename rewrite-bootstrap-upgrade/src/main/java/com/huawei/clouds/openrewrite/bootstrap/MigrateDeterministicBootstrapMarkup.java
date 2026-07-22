package com.huawei.clouds.openrewrite.bootstrap;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies only Bootstrap 5 markup renames documented as direct equivalents. */
public final class MigrateDeterministicBootstrapMarkup extends Recipe {
    private static final Pattern TAG = Pattern.compile("<(?!!--)[^>]+>", Pattern.DOTALL);
    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile(
            "(?<![:\\w-])(class|className)(\\s*=\\s*)(['\"])(.*?)\\3", Pattern.DOTALL);
    private static final Pattern BOOTSTRAP_EVIDENCE = Pattern.compile(
            "(?is)(?:bootstrap(?:\\.min)?\\.(?:css|js)|(?<![:\\w-])class(?:Name)?\\s*=\\s*['\"][^'\"]*(?:\\bbtn(?:-\\w+)?\\b|\\bnavbar(?:-\\w+)?\\b|\\bmodal(?:-\\w+)?\\b|\\bdropdown(?:-\\w+)?\\b|\\bcollapse\\b|\\bcarousel(?:-\\w+)?\\b|\\balert(?:-\\w+)?\\b|\\bcard(?:-\\w+)?\\b|\\bcontainer(?:-fluid)?\\b|\\brow\\b|\\bcol(?:-\\w+)?\\b|\\bform-control\\b|\\binput-group\\b|\\bbadge\\b)[^'\"]*['\"]|data-toggle\\s*=\\s*['\"](?:modal|collapse|dropdown|tab|pill|tooltip|popover|button|carousel)['\"])" );
    private static final Pattern OWNED_TAG = Pattern.compile(
            "(?is)(?:data-(?:toggle|dismiss)\\s*=\\s*['\"](?:modal|collapse|dropdown|tab|pill|tooltip|popover|button|alert|toast|carousel)['\"]|data-ride\\s*=\\s*['\"]carousel['\"]|data-spy\\s*=\\s*['\"]scroll['\"]|class\\s*=\\s*['\"][^'\"]*\\b(?:carousel|collapse|dropdown|modal|alert|toast|nav|btn)(?:[-_\\w]*)\\b[^'\"]*['\"])" );
    private static final Pattern DATA_ATTRIBUTE = Pattern.compile(
            "(?i)(?<![\\w-])data-(toggle|target|dismiss|parent|ride|slide|slide-to|spy|offset|interval|keyboard|backdrop|placement|content|title|trigger|container|delay|html|sanitize|boundary|reference|display|touch|wrap|pause)(?=\\s*=)");
    private static final Pattern SPACING = Pattern.compile("^(m|p)([lr])(-(sm|md|lg|xl|xxl))?-(0|1|2|3|4|5|auto)$");
    private static final Pattern RESPONSIVE_DIRECTION = Pattern.compile(
            "^(float|text)(-(sm|md|lg|xl|xxl))?-(left|right)$");
    private static final Map<String, String> DIRECT = Map.ofEntries(
            Map.entry("float-left", "float-start"), Map.entry("float-right", "float-end"),
            Map.entry("text-left", "text-start"), Map.entry("text-right", "text-end"),
            Map.entry("border-left", "border-start"), Map.entry("border-right", "border-end"),
            Map.entry("rounded-left", "rounded-start"), Map.entry("rounded-right", "rounded-end"),
            Map.entry("sr-only", "visually-hidden"),
            Map.entry("sr-only-focusable", "visually-hidden-focusable"),
            Map.entry("text-monospace", "font-monospace"), Map.entry("font-italic", "fst-italic"),
            Map.entry("font-weight-bold", "fw-bold"), Map.entry("font-weight-bolder", "fw-bolder"),
            Map.entry("font-weight-normal", "fw-normal"), Map.entry("font-weight-light", "fw-light"),
            Map.entry("font-weight-lighter", "fw-lighter"), Map.entry("no-gutters", "g-0"),
            Map.entry("custom-select", "form-select"), Map.entry("custom-range", "form-range"),
            Map.entry("badge-pill", "rounded-pill"), Map.entry("embed-responsive", "ratio"),
            Map.entry("embed-responsive-21by9", "ratio-21x9"),
            Map.entry("embed-responsive-16by9", "ratio-16x9"),
            Map.entry("embed-responsive-4by3", "ratio-4x3"),
            Map.entry("embed-responsive-1by1", "ratio-1x1"));

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Bootstrap 5 markup";
    }

    @Override
    public String getDescription() {
        return "Renames proven Bootstrap JavaScript data attributes and direct-equivalent utility, form, screen-reader, gutter, badge, and ratio class tokens in static markup.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!BootstrapSupport.isMarkup(visited.getSourcePath()) ||
                    !BOOTSTRAP_EVIDENCE.matcher(visited.getText()).find()) return visited;
                String migrated = rewriteTags(visited.getText());
                return migrated.equals(visited.getText()) ? visited : visited.withText(migrated);
            }
        };
    }

    private static String rewriteTags(String source) {
        boolean[] comments = htmlComments(source);
        Matcher matcher = TAG.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            if (comments[matcher.start()]) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String tag = matcher.group();
            String rewritten = rewriteClasses(tag);
            if (OWNED_TAG.matcher(tag).find()) rewritten = renameDataAttributes(rewritten);
            matcher.appendReplacement(result, Matcher.quoteReplacement(rewritten));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String rewriteClasses(String tag) {
        Matcher matcher = CLASS_ATTRIBUTE.matcher(tag);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(4);
            String[] tokens = value.trim().isEmpty() ? new String[0] : value.trim().split("\\s+");
            List<String> migrated = new ArrayList<>();
            for (String token : tokens) {
                String replacement = migrateClass(token);
                if (!replacement.isEmpty()) migrated.add(replacement);
            }
            String rewritten = String.join(" ", migrated);
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2) + matcher.group(3) + rewritten + matcher.group(3)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String migrateClass(String token) {
        if ("embed-responsive-item".equals(token)) return "";
        String direct = DIRECT.get(token);
        if (direct != null) return direct;
        Matcher direction = RESPONSIVE_DIRECTION.matcher(token);
        if (direction.matches()) {
            return direction.group(1) + (direction.group(2) == null ? "" : direction.group(2)) + "-" +
                   ("left".equals(direction.group(4)) ? "start" : "end");
        }
        Matcher spacing = SPACING.matcher(token);
        if (spacing.matches()) {
            if ("p".equals(spacing.group(1)) && "auto".equals(spacing.group(5))) return token;
            return spacing.group(1) + ("l".equals(spacing.group(2)) ? "s" : "e") +
                   (spacing.group(3) == null ? "" : spacing.group(3)) + "-" + spacing.group(5);
        }
        return token;
    }

    private static String renameDataAttributes(String tag) {
        Matcher matcher = DATA_ATTRIBUTE.matcher(tag);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, "data-bs-" + matcher.group(1).toLowerCase(Locale.ROOT));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean[] htmlComments(String source) {
        boolean[] comments = new boolean[source.length() + 1];
        boolean comment = false;
        for (int i = 0; i < source.length(); i++) {
            if (!comment && source.startsWith("<!--", i)) comment = true;
            comments[i] = comment;
            if (comment && source.startsWith("-->", i)) {
                for (int j = 0; j < 3 && i + j < source.length(); j++) comments[i + j] = true;
                i += 2;
                comment = false;
            }
        }
        return comments;
    }
}
