package com.huawei.clouds.openrewrite.react;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Mark React UMD and inline-root usage in HTML resources. */
public final class FindReactResourceMigrationRisks extends Recipe {
    private static final List<ReactSourceText.RiskPattern> RISKS = List.of(
            new ReactSourceText.RiskPattern(
                    Pattern.compile("https?://(?:unpkg[.]com|cdn[.]jsdelivr[.]net/npm)/react(?:-dom)?@[^\"'<>\\s]+/(?:umd|dist)/[^\"'<>\\s]+"),
                    "React 19 removed UMD builds; use an ESM CDN or a build tool and update react/react-dom together"),
            new ReactSourceText.RiskPattern(
                    Pattern.compile("\\bReactDOM\\s*[.]\\s*(?:render|hydrate)(?=\\s*\\()"),
                    "React 19 removed ReactDOM.render/hydrate; move inline bootstrap code to createRoot/hydrateRoot and an ESM client entry")
    );

    @Override
    public String getDisplayName() {
        return "Find React 19 HTML resource migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed React UMD artifacts and inline legacy ReactDOM roots in HTML resources.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                if (!path.endsWith(".html") && !path.endsWith(".htm")) {
                    return visited;
                }
                return ReactSourceText.markAllMatches(visited, RISKS);
            }
        };
    }
}
