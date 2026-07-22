package com.huawei.clouds.openrewrite.ngxecharts;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark ngx-echarts template behavior that cannot be inferred from syntax alone. */
public final class FindNgxEchartsTemplateRisks extends Recipe {
    private static final Pattern START_TAG = Pattern.compile("(?s)<(?![!/])[^>]+>");
    private static final List<NgxEchartsPlainTextSupport.Risk> RISKS = List.of(
            risk("(?i)\\[detectEventChanges\\](?=\\s*=)",
                    "Dynamic detectEventChanges was removed; delete the binding after preserving the intended Angular-zone/change-detection behavior explicitly"),
            risk("(?i)\\[(?:options|merge|theme|initOpts|loading|autoResize|loadingOpts)\\](?=\\s*=)",
                    "Chart input behavior crosses nullable/signal, refresh, merge and resize changes; verify strict template types, stable dimensions, renderer/ResizeObserver, SSR hydration, reinitialization and setOption semantics"),
            risk("(?i)\\(chart(?:Init|Finished|Rendered|Click|SelectChanged|BrushSelected|DataZoom)\\)(?=\\s*=)",
                    "Chart event typing and lifecycle timing changed; verify handler types, zone re-entry, teardown and event ordering")
    );

    @Override
    public String getDisplayName() {
        return "Find ngx-echarts 20 template compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact removed/dynamic inputs, behavior-sensitive chart inputs/events and directive hosts while " +
               "ignoring HTML comments and unrelated file types.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!visited.getSourcePath().toString().toLowerCase(Locale.ROOT).endsWith(".html")) return visited;
                return NgxEchartsPlainTextSupport.mark(visited, RISKS, true, hostPositions(visited.getText()));
            }
        };
    }

    private static boolean[] hostPositions(String source) {
        boolean[] host = new boolean[source.length() + 1];
        boolean[] visible = NgxEchartsPlainTextSupport.visiblePositions(source, true);
        Matcher tag = START_TAG.matcher(source);
        while (tag.find()) {
            if (visible[tag.start()] && MigrateDeterministicNgxEchartsTemplates.isEchartsHost(tag.group())) {
                Arrays.fill(host, tag.start(), tag.end(), true);
            }
        }
        return host;
    }

    private static NgxEchartsPlainTextSupport.Risk risk(String pattern, String message) {
        return new NgxEchartsPlainTextSupport.Risk(Pattern.compile(pattern, Pattern.MULTILINE), message);
    }
}
