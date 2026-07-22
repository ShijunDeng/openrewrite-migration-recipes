package com.huawei.clouds.openrewrite.reactrouterdom;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;

/** Removes the obsolete DefinitelyTyped package only after react-router-dom 6.30.4 is present. */
public final class MigrateReactRouterDomPackageJson extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove obsolete react-router-dom v4/v5 type declarations";
    }

    @Override
    public String getDescription() {
        return "Removes direct @types/react-router-dom declarations from a package that directly declares " +
               "react-router-dom 6.30.4, which publishes its own TypeScript declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson() || !"@types/react-router-dom".equals(UpgradeSelectedReactRouterDomDependency.key(visited)) ||
                    !isDirectDependency()) {
                    return visited;
                }
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                return directlyTargetsV6(document.getValue()) ? null : visited;
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }

            private boolean isDirectDependency() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor section = object == null ? null : object.getParentTreeCursor();
                Cursor rootObject = section == null ? null : section.getParentTreeCursor();
                Cursor document = rootObject == null ? null : rootObject.getParentTreeCursor();
                return section != null && section.getValue() instanceof Json.Member &&
                       UpgradeSelectedReactRouterDomDependency.SECTIONS.contains(
                               UpgradeSelectedReactRouterDomDependency.key((Json.Member) section.getValue())) &&
                       document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    static boolean directlyTargetsV6(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        for (JsonValue value : directValues(root)) {
            if ("react-router-dom".equals(value.name) &&
                UpgradeSelectedReactRouterDomDependency.TARGET_VERSION.equals(value.value)) return true;
        }
        return false;
    }

    private static java.util.List<JsonValue> directValues(Json.JsonObject root) {
        java.util.List<JsonValue> values = new java.util.ArrayList<>();
        for (Json memberValue : root.getMembers()) {
            if (!(memberValue instanceof Json.Member section) ||
                !UpgradeSelectedReactRouterDomDependency.SECTIONS.contains(
                        UpgradeSelectedReactRouterDomDependency.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject dependencies)) continue;
            for (Json dependencyValue : dependencies.getMembers()) {
                if (dependencyValue instanceof Json.Member dependency &&
                    dependency.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String declaration) {
                    values.add(new JsonValue(UpgradeSelectedReactRouterDomDependency.key(dependency), declaration));
                }
            }
        }
        return values;
    }

    private static final class JsonValue {
        private final String name;
        private final String value;

        private JsonValue(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
