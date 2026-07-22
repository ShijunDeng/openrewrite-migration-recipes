package com.huawei.clouds.openrewrite.ojdbc8;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.marker.Markers;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fallback marker for module descriptors that javac cannot attribute after the old module disappears. */
public final class FindOjdbcModuleTextMigrationRisks extends Recipe {
    private static final Pattern OLD_REQUIRE = Pattern.compile("(?m)\\brequires\\s+(?:static\\s+|transitive\\s+)*ojdbc8\\s*;");
    private static final String MESSAGE =
            "ojdbc8 19.x had the derived JPMS module name ojdbc8, but 21.x and 23.26.1 declare Automatic-Module-Name com.oracle.database.jdbc; update requires and verify no duplicate ojdbc8/ojdbc11 modules or split packages";

    @Override public String getDisplayName() { return "Find old Oracle JDBC module name in unattributed descriptors"; }
    @Override public String getDescription() {
        return "Mark an old requires ojdbc8 directive when module-info.java cannot be attributed because the old module is absent.";
    }

    @Override
    public PlainTextVisitor<ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!UpgradeSelectedOjdbc8Dependency.isProjectPath(visited.getSourcePath()) ||
                    visited.getSourcePath().getFileName() == null ||
                    !"module-info.java".equals(visited.getSourcePath().getFileName().toString()) ||
                    !OLD_REQUIRE.matcher(visited.getText()).find()) return visited;
                return markDirectives(visited);
            }
        };
    }

    private static PlainText markDirectives(PlainText text) {
        String source = text.getText();
        Matcher matcher = OLD_REQUIRE.matcher(source);
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) snippets.add(snippet(source.substring(cursor, matcher.start())));
            snippets.add(SearchResult.found(snippet(matcher.group()), MESSAGE));
            cursor = matcher.end();
        }
        if (snippets.isEmpty()) return text;
        if (cursor < source.length()) snippets.add(snippet(source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private static PlainText.Snippet snippet(String text) {
        return new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, text);
    }
}
