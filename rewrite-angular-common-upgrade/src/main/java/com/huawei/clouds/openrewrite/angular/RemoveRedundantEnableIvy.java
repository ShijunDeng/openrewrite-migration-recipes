package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;

/** Removes only the now-redundant positive Ivy switch; false remains a migration error marker. */
public final class RemoveRedundantEnableIvy extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove redundant enableIvy true";
    }

    @Override
    public String getDescription() {
        return "Removes angularCompilerOptions.enableIvy when it is exactly true because Angular 20 is Ivy-only.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.JsonObject visitObject(Json.JsonObject object, ExecutionContext ctx) {
                Json.JsonObject visited = super.visitObject(object, ctx);
                if (!isAngularCompilerOptionsInTsConfig()) {
                    return visited;
                }
                return visited.withMembers(ListUtils.map(visited.getMembers(), value -> {
                    if (value instanceof Json.Member member && "enableIvy".equals(UpgradeSelectedAngularCommonDependency.key(member)) &&
                        member.getValue() instanceof Json.Literal literal && Boolean.TRUE.equals(literal.getValue())) {
                        return null;
                    }
                    return value;
                }));
            }

            private boolean isAngularCompilerOptionsInTsConfig() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                Cursor member = nextTree(getCursor());
                Cursor rootObject = nextTree(member);
                Cursor document = nextTree(rootObject);
                return file.startsWith("tsconfig") && file.endsWith(".json") && member != null &&
                       member.getValue() instanceof Json.Member && document != null &&
                       document.getValue() instanceof Json.Document &&
                       "angularCompilerOptions".equals(UpgradeSelectedAngularCommonDependency.key((Json.Member) member.getValue()));
            }

            private Cursor nextTree(Cursor cursor) {
                Cursor parent = cursor == null ? null : cursor.getParent();
                while (parent != null && !(parent.getValue() instanceof Tree)) {
                    parent = parent.getParent();
                }
                return parent;
            }
        };
    }
}
