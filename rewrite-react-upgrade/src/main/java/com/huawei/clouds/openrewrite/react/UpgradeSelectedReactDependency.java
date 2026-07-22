package com.huawei.clouds.openrewrite.react;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/** Upgrade only scalar React versions explicitly selected by the migration spreadsheet. */
public final class UpgradeSelectedReactDependency extends Recipe {
    private static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> VERSIONS = Set.of(
            "16.6.1", "16.14.0", "17.0.2", "18.2.0", "19.0.0"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected React declarations to 19.2.7";
    }

    @Override
    public String getDescription() {
        return "Upgrade only exact, caret, or tilde scalar React declarations for spreadsheet-listed versions " +
               "in direct package.json dependency sections; preserve ranges, protocols, aliases and metadata.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !"react".equals(key(visited)) ||
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
                return visited.withValue(value.withSource("\"19.2.7\"").withValue("19.2.7"));
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

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        if (member.getKey() instanceof Json.Identifier identifier) {
            return identifier.getName();
        }
        return "";
    }
}
