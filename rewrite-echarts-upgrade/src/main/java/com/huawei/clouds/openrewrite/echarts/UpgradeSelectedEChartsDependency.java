package com.huawei.clouds.openrewrite.echarts;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/** Upgrades only scalar ECharts declarations selected by the spreadsheet and its compressed release range. */
public final class UpgradeSelectedEChartsDependency extends Recipe {
    private static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> VERSIONS = Set.of(
            "4.8.0", "4.9.0", "5.0.2", "5.2.1", "5.2.2", "5.3.0", "5.3.1", "5.3.3",
            "5.4.0", "5.4.1", "5.4.2", "5.4.3", "5.5.0", "5.5.1", "5.6.0"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected Apache ECharts declarations to 6.1.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only selected scalar ECharts releases in direct package.json dependency sections, " +
               "without changing ranges, protocols, aliases, lockfiles, nested metadata, or newer versions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !"echarts".equals(key(visited)) ||
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
                return visited.withValue(value.withSource("\"6.1.0\"").withValue("6.1.0"));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }
        };
    }

    private static boolean isSelected(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~") || candidate.startsWith("=")) {
            candidate = candidate.substring(1);
        }
        if (candidate.startsWith("v")) {
            candidate = candidate.substring(1);
        }
        return VERSIONS.contains(candidate) && declaration.matches("(?:[~^=]?|\\^?v)" + Pattern.quote(candidate));
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
