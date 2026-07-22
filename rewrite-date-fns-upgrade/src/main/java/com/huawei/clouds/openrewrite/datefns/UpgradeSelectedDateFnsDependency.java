package com.huawei.clouds.openrewrite.datefns;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/** Upgrade only direct date-fns declarations anchored to versions visible in the spreadsheet. */
public final class UpgradeSelectedDateFnsDependency extends Recipe {
    private static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    private static final Set<String> SOURCE_VERSIONS = Set.of(
            "2.23.0", "2.25.0", "2.28.0", "2.29.3", "2.30.0");

    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected date-fns declarations to 4.1.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only selected scalar date-fns declarations in direct package.json dependency sections, " +
               "without changing compound ranges, prereleases, protocols, aliases, lockfiles, or newer versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !"date-fns".equals(key(visited)) ||
                    !(visited.getValue() instanceof Json.Literal value) ||
                    !(value.getValue() instanceof String declaration) || !selected(declaration)) {
                    return visited;
                }
                Cursor objectCursor = getCursor().getParentTreeCursor();
                Cursor sectionCursor = objectCursor == null ? null : objectCursor.getParentTreeCursor();
                if (sectionCursor == null || !(sectionCursor.getValue() instanceof Json.Member section) ||
                    !SECTIONS.contains(key(section))) {
                    return visited;
                }
                return visited.withValue(value.withSource("\"4.1.0\"").withValue("4.1.0"));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }
        };
    }

    private static boolean selected(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~") || candidate.startsWith("=")) {
            candidate = candidate.substring(1);
        }
        if (candidate.startsWith("v")) {
            candidate = candidate.substring(1);
        }
        return SOURCE_VERSIONS.contains(candidate) &&
               declaration.matches("(?:[~^=]?|[~^=]?v)" + Pattern.quote(candidate));
    }

    private static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        return member.getKey() instanceof Json.Identifier identifier ? identifier.getName() : "";
    }
}
