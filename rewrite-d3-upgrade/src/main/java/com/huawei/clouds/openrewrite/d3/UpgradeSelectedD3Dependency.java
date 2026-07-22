package com.huawei.clouds.openrewrite.d3;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/** Upgrade only scalar D3 versions explicitly selected by the migration spreadsheet. */
public final class UpgradeSelectedD3Dependency extends Recipe {
    private static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> VERSIONS = Set.of(
            "5.16.0", "6.6.2", "6.7.0", "7.1.1", "7.8.2", "7.8.4", "7.8.5"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected D3 declarations to 7.9.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact, caret, or tilde scalar D3 declarations for spreadsheet-listed versions in " +
               "direct package.json dependency sections; preserve ranges, protocols, aliases and nested metadata.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !"d3".equals(key(visited)) ||
                    !(visited.getValue() instanceof Json.Literal value) ||
                    !(value.getValue() instanceof String declaration) || !isSelected(declaration)) {
                    return visited;
                }
                Cursor objectCursor = getCursor().getParentTreeCursor();
                Cursor sectionCursor = objectCursor == null ? null : objectCursor.getParentTreeCursor();
                if (sectionCursor == null || !(sectionCursor.getValue() instanceof Json.Member section) ||
                    !SECTIONS.contains(key(section))) {
                    return visited;
                }
                return visited.withValue(value.withSource("\"7.9.0\"").withValue("7.9.0"));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }
        };
    }

    private static boolean isSelected(String declaration) {
        String candidate = declaration.startsWith("^") || declaration.startsWith("~") ?
                declaration.substring(1) : declaration;
        return VERSIONS.contains(candidate) && declaration.matches("[~^]?" + Pattern.quote(candidate));
    }

    private static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        if (member.getKey() instanceof Json.Identifier identifier) {
            return identifier.getName();
        }
        return "";
    }
}
