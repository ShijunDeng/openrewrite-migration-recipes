package com.huawei.clouds.openrewrite.reactredux;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only direct workbook-selected react-redux npm declarations. */
public final class UpgradeSelectedReactReduxDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected react-redux dependencies to 9.3.0";
    }

    @Override
    public String getDescription() {
        return "Changes exact, caret, or tilde direct package.json declarations based at 7.2.2, 7.2.4, " +
               "7.2.8, 7.2.9, 8.0.2, or 8.0.5; ranges, protocols, aliases, overrides, and lockfiles stay untouched.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!ReactReduxSupport.isPackageJson(document.getSourcePath()) ||
                    !ReactReduxSupport.PACKAGE.equals(ReactReduxSupport.key(visited)) ||
                    ReactReduxSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String declaration = ReactReduxSupport.stringValue(visited);
                if (!ReactReduxSupport.selected(declaration) || !(visited.getValue() instanceof Json.Literal literal)) {
                    return visited;
                }
                return visited.withValue(ReactReduxSupport.replaceJsonString(
                        literal, ReactReduxSupport.targetDeclaration(declaration)));
            }
        };
    }
}
