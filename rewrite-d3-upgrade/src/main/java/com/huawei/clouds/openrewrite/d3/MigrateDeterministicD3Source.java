package com.huawei.clouds.openrewrite.d3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Set;
import java.util.regex.Pattern;

/** Apply the two one-to-one D3 6 array API renames at explicit D3 namespace calls. */
public final class MigrateDeterministicD3Source extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic D3 array API calls";
    }

    @Override
    public String getDescription() {
        return "Rename explicit D3 namespace histogram() calls to bin() and scan() calls to leastIndex() in " +
               "JavaScript/TypeScript code, excluding comments, strings, templates and regex literals.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!D3SourceText.isSupported(visited)) {
                    return visited;
                }
                String source = visited.getText();
                Set<String> aliases = D3SourceText.namespaceAliases(source);
                Pattern calls = D3SourceText.memberCall(aliases, "histogram|scan");
                String migrated = D3SourceText.replaceCodeMatches(source, calls, match ->
                        match.group("alias") + match.group("dot") +
                        ("histogram".equals(match.group("method")) ? "bin" : "leastIndex"));
                return visited.withText(migrated);
            }
        };
    }
}
