package com.huawei.clouds.openrewrite.echarts;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

/** Upgrades only scalar ECharts declarations explicitly visible in the supplied spreadsheet. */
public final class UpgradeSelectedEChartsDependency extends Recipe {
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    static final Set<String> VERSIONS = Set.of(
            "4.8.0", "4.9.0", "5.0.2", "5.2.1", "5.2.2", "5.3.0", "5.3.1", "5.3.3",
            "5.4.0", "5.4.1"
    );

    @Override
    public String getDisplayName() {
        return "Upgrade selected Apache ECharts declarations to 6.1.0";
    }

    @Override
    public String getDescription() {
        return "Upgrade only selected scalar ECharts releases in direct package.json dependency sections, " +
               "without changing complex ranges, protocols, aliases, lockfiles, nested metadata, or unlisted versions.";
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
                    !SECTIONS.contains(key(section)) || !isRootSection(sectionCursor)) {
                    return visited;
                }
                return visited.withValue(value.withSource("\"6.1.0\"").withValue("6.1.0"));
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return EChartsSupport.isProjectPath(path) && path.getFileName() != null &&
                       "package.json".equals(path.getFileName().toString());
            }

            private boolean isRootSection(Cursor sectionCursor) {
                Cursor rootObject = sectionCursor.getParentTreeCursor();
                Cursor document = rootObject == null ? null : rootObject.getParentTreeCursor();
                return document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static boolean isSelected(String declaration) {
        if (!declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+")) return false;
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return VERSIONS.contains(candidate);
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
