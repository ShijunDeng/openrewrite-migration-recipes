package com.huawei.clouds.openrewrite.reactresizable;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.regex.Pattern;

/** Upgrade only the exact dependency declaration selected by the workbook. */
public final class UpgradeSelectedReactResizableDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade workbook-selected react-resizable to 3.1.3";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact, caret, or tilde react-resizable 1.11.1 declarations in direct package.json " +
               "dependency sections; preserve ranges, protocols, aliases, catalogs, metadata, and lockfiles.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !ReactResizableSupport.PACKAGE.equals(ReactResizableSupport.jsonKey(visited)) ||
                    !selected(ReactResizableSupport.jsonString(visited)) || !(visited.getValue() instanceof Json.Literal value)) {
                    return visited;
                }
                Cursor object = getCursor().getParentTreeCursor();
                Cursor sectionCursor = object == null ? null : object.getParentTreeCursor();
                if (sectionCursor == null || !(sectionCursor.getValue() instanceof Json.Member section) ||
                    !ReactResizableSupport.SECTIONS.contains(ReactResizableSupport.jsonKey(section)) ||
                    !rootSection(sectionCursor)) return visited;
                return visited.withValue(value.withSource("\"" + ReactResizableSupport.TARGET + "\"")
                        .withValue(ReactResizableSupport.TARGET));
            }

            private boolean rootSection(Cursor section) {
                Cursor rootObject = section.getParentTreeCursor();
                Cursor document = rootObject == null ? null : rootObject.getParentTreeCursor();
                return document != null && document.getValue() instanceof Json.Document;
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return ReactResizableSupport.isProjectPath(path) && path.getFileName() != null &&
                       "package.json".equals(path.getFileName().toString());
            }
        };
    }

    private static boolean selected(String declaration) {
        if (declaration == null) return false;
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return ReactResizableSupport.SOURCE.equals(candidate) &&
               declaration.matches("[~^]?" + Pattern.quote(candidate));
    }
}
