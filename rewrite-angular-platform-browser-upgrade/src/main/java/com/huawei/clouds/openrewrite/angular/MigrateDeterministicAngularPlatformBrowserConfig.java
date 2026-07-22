package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.List;

/** Remove the obsolete enableIvy=true switch from Angular TypeScript configuration. */
public final class MigrateDeterministicAngularPlatformBrowserConfig extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove deterministic obsolete Angular compiler configuration";
    }

    @Override
    public String getDescription() {
        return "Remove enableIvy=true only when it is a direct angularCompilerOptions member in tsconfig JSON; " +
               "Angular 20 is Ivy-only, while enableIvy=false remains marked for manual migration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.JsonObject visitObject(Json.JsonObject object, ExecutionContext ctx) {
                Json.JsonObject visited = super.visitObject(object, ctx);
                org.openrewrite.Cursor parentCursor = nextTree(getCursor());
                org.openrewrite.Cursor rootCursor = nextTree(parentCursor);
                org.openrewrite.Cursor documentCursor = nextTree(rootCursor);
                if (!isTsConfig() || parentCursor == null || !(parentCursor.getValue() instanceof Json.Member parent) ||
                    !"angularCompilerOptions".equals(UpgradeSelectedAngularPlatformBrowserDependency.key(parent)) ||
                    documentCursor == null || !(documentCursor.getValue() instanceof Json.Document)) {
                    return visited;
                }
                List<Json> kept = visited.getMembers().stream()
                        .filter(value -> !(value instanceof Json.Member member) || !isEnableIvyTrue(member))
                        .toList();
                return kept.size() == visited.getMembers().size() ? visited : visited.withMembers(kept);
            }

            private org.openrewrite.Cursor nextTree(org.openrewrite.Cursor cursor) {
                org.openrewrite.Cursor parent = cursor == null ? null : cursor.getParent();
                while (parent != null && !(parent.getValue() instanceof Tree)) {
                    parent = parent.getParent();
                }
                return parent;
            }

            private boolean isTsConfig() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                return file.startsWith("tsconfig") && file.endsWith(".json");
            }
        };
    }

    private static boolean isEnableIvyTrue(Json.Member member) {
        return "enableIvy".equals(UpgradeSelectedAngularPlatformBrowserDependency.key(member)) &&
               member.getValue() instanceof Json.Literal literal && Boolean.TRUE.equals(literal.getValue());
    }
}
