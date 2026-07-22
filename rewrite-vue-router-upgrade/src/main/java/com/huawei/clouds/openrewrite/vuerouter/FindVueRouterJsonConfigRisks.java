package com.huawei.clouds.openrewrite.vuerouter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

/** Marks unplugin path aliases/configuration that cannot be mechanically retargeted. */
public final class FindVueRouterJsonConfigRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Vue Router 5 JSON configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks remaining unplugin-vue-router strings in tsconfig/jsconfig after deterministic Volar and " +
               "client-reference migration so custom paths and generated declarations are reviewed explicitly.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean config;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                String name = document.getSourcePath().getFileName() == null ? "" :
                        document.getSourcePath().getFileName().toString();
                boolean previous = config;
                config = name.startsWith("tsconfig") || name.startsWith("jsconfig");
                Json.Document visited = config ? super.visitDocument(document, ctx) : document;
                config = previous;
                return visited;
            }

            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (config && visited.getValue() instanceof String value && value.contains("unplugin-vue-router")) {
                    return SearchResult.found(visited,
                            "This custom unplugin-vue-router path has no context-free replacement; map runtime/types/data-loader aliases to Vue Router 5 exports and regenerate route-map declarations");
                }
                return visited;
            }
        };
    }
}
