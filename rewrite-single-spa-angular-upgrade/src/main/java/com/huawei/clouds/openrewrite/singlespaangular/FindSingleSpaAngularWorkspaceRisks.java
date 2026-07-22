package com.huawei.clouds.openrewrite.singlespaangular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.Set;

/** Mark Angular workspace choices that require project and deployment intent. */
public final class FindSingleSpaAngularWorkspaceRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find single-spa-angular workspace migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks legacy builders, main/browser conflicts, custom webpack ownership, SystemJS output, and serve/deployment options in relevant Angular workspaces.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean active;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean previous = active;
                String source = document.printAll();
                active = SingleSpaAngularSupport.workspaceJson(document.getSourcePath()) &&
                         (source.contains("single-spa-angular:") || source.contains("customWebpackConfig") ||
                          source.contains("extraWebpackConfig") || source.contains("singleSpa"));
                Json.Document visited = active ? super.visitDocument(document, ctx) : document;
                active = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!active) return visited;
                String key = SingleSpaAngularSupport.key(visited);
                String value = SingleSpaAngularSupport.string(visited);
                if ("builder".equals(key) && value != null &&
                    targetScope() &&
                    Set.of("single-spa-angular:build", "single-spa-angular:dev-server").contains(value)) {
                    return SingleSpaAngularSupport.mark(visited,
                            "This pre-Angular-8 single-spa-angular builder is legacy; migrate to the Angular browser/custom-webpack builder and verify build plus serve targets");
                }
                if ("builder".equals(key) && targetScope() &&
                    "@angular-devkit/build-angular:application".equals(value)) {
                    return SingleSpaAngularSupport.mark(visited,
                            "The Angular application builder is not supported for the documented SystemJS setup; AUTO only converts relevant conflict-free build targets, so resolve main/browser collisions and target intent here");
                }
                if (Set.of("customWebpackConfig", "extraWebpackConfig", "libraryTarget", "excludeAngularDependencies",
                           "outputPath", "deployUrl", "browserTarget", "buildTarget").contains(key) &&
                    targetScope()) {
                    return SingleSpaAngularSupport.mark(visited,
                            "Review this build/deployment owner for the final Angular builder, SystemJS library output, externals, shared Angular, serve wiring, and emitted artifact path");
                }
                return visited;
            }

            private boolean targetScope() {
                java.util.List<String> owners = new java.util.ArrayList<>();
                for (Cursor current = getCursor().getParentTreeCursor(); current != null;
                     current = current.getParentTreeCursor()) {
                    if (current.getValue() instanceof Json.Member owner) {
                        owners.add(SingleSpaAngularSupport.key(owner));
                    }
                    if (current.getValue() instanceof Json.Document) break;
                }
                for (int i = 0; i + 1 < owners.size(); i++) {
                    if (Set.of("build", "serve").contains(owners.get(i)) &&
                        Set.of("architect", "targets").contains(owners.get(i + 1))) return true;
                }
                return false;
            }
        };
    }
}
