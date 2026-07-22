package com.huawei.clouds.openrewrite.singlespaangular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonKey;

import java.util.Set;

/** Apply the documented Angular 17+ SystemJS builder conversion only when it is conflict-free. */
public final class MigrateSingleSpaAngularWorkspace extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic single-spa-angular workspace configuration";
    }

    @Override
    public String getDescription() {
        return "Changes the precise Angular application builder to browser and its sibling browser entry to main, unless a main/browser collision exists.";
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
                if (!active || !"build".equals(SingleSpaAngularSupport.key(visited)) ||
                    !directTargetMember() || !(visited.getValue() instanceof Json.JsonObject build) ||
                    !hasDirectString(build, "builder", "@angular-devkit/build-angular:application") ||
                    hasMainBrowserCollision(build)) return visited;
                Json.JsonObject migrated = build.withMembers(build.getMembers().stream().map(value -> {
                    if (!(value instanceof Json.Member child)) return value;
                    if ("builder".equals(SingleSpaAngularSupport.key(child))) {
                        return SingleSpaAngularSupport.replaceString(child,
                                "@angular-devkit/build-angular:browser");
                    }
                    if (!"options".equals(SingleSpaAngularSupport.key(child)) ||
                        !(child.getValue() instanceof Json.JsonObject options) || hasDirectKey(options, "main")) {
                        return child;
                    }
                    return child.withValue(options.withMembers(options.getMembers().stream().map(option ->
                            option instanceof Json.Member optionMember &&
                            "browser".equals(SingleSpaAngularSupport.key(optionMember))
                                    ? optionMember.withKey(renameKey(optionMember.getKey(), "main")) : option
                    ).toList()));
                }).toList());
                return visited.withValue(migrated);
            }

            private boolean directTargetMember() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor container = object == null ? null : object.getParentTreeCursor();
                return container != null && container.getValue() instanceof Json.Member owner &&
                       Set.of("architect", "targets").contains(SingleSpaAngularSupport.key(owner));
            }

            private boolean hasMainBrowserCollision(Json.JsonObject build) {
                for (Json value : build.getMembers()) {
                    if (value instanceof Json.Member candidate && "options".equals(SingleSpaAngularSupport.key(candidate)) &&
                        candidate.getValue() instanceof Json.JsonObject options) {
                        return hasDirectKey(options, "main") && hasDirectKey(options, "browser");
                    }
                }
                return false;
            }

            private boolean hasDirectKey(Json.JsonObject object, String key) {
                return object.getMembers().stream().anyMatch(value -> value instanceof Json.Member member &&
                        key.equals(SingleSpaAngularSupport.key(member)));
            }

            private boolean hasDirectString(Json.JsonObject object, String key, String value) {
                return object.getMembers().stream().anyMatch(candidate -> candidate instanceof Json.Member member &&
                        key.equals(SingleSpaAngularSupport.key(member)) &&
                        value.equals(SingleSpaAngularSupport.string(member)));
            }
        };
    }

    private static JsonKey renameKey(JsonKey key, String replacement) {
        if (key instanceof Json.Identifier identifier) return identifier.withName(replacement);
        if (key instanceof Json.Literal literal) {
            String quote = literal.getSource().startsWith("'") ? "'" : "\"";
            return literal.withValue(replacement).withSource(quote + replacement + quote);
        }
        return key;
    }
}
