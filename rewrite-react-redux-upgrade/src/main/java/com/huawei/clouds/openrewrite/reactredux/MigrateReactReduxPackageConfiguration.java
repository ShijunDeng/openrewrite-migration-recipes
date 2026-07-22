package com.huawei.clouds.openrewrite.reactredux;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Removes v7 external typings and normalizes the v8 React-18 entry only after the target is declared. */
public final class MigrateReactReduxPackageConfiguration extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic React Redux package configuration";
    }

    @Override
    public String getDescription() {
        return "Removes direct @types/react-redux declarations after React Redux 9.3.0 is directly declared and " +
               "normalizes an exact v8 /next browser alias to the v9 root entry.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean active;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!ReactReduxSupport.isPackageJson(document.getSourcePath())) return document;
                boolean previous = active;
                active = directlyTargetsV9(document, ctx);
                Json.Document visited = super.visitDocument(document, ctx);
                active = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!active || !ReactReduxSupport.TYPES_PACKAGE.equals(ReactReduxSupport.key(visited)) ||
                    ReactReduxSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                return null;
            }

            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (!active || !ReactReduxSupport.underRootSection(getCursor(), "browser") ||
                    !(visited.getValue() instanceof String module)) return visited;
                String replacement = ReactReduxSupport.migratedModule(module);
                return module.equals(replacement) ? visited : ReactReduxSupport.replaceJsonString(visited, replacement);
            }

            private boolean directlyTargetsV9(Json.Document document, ExecutionContext ctx) {
                boolean[] found = {false};
                new JsonIsoVisitor<ExecutionContext>() {
                    @Override
                    public Json.Member visitMember(Json.Member member, ExecutionContext scanCtx) {
                        Json.Member visited = super.visitMember(member, scanCtx);
                        if (ReactReduxSupport.PACKAGE.equals(ReactReduxSupport.key(visited)) &&
                            !ReactReduxSupport.directDependencySection(getCursor()).isEmpty() &&
                            ReactReduxSupport.target(ReactReduxSupport.stringValue(visited))) found[0] = true;
                        return visited;
                    }
                }.visit(document, ctx);
                return found[0];
            }
        };
    }
}
