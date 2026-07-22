package com.huawei.clouds.openrewrite.vuerouter;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.json.tree.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Applies exact configuration path changes documented for the Vue Router 5 merge. */
public final class MigrateVueRouterJsonConfig extends Recipe {
    private static final Map<String, String> REPLACEMENTS = Map.of(
            "unplugin-vue-router/volar/sfc-typed-router", "vue-router/volar/sfc-typed-router",
            "unplugin-vue-router/volar/sfc-route-blocks", "vue-router/volar/sfc-route-blocks");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Vue Router 5 JSON configuration";
    }

    @Override
    public String getDescription() {
        return "Rewrites the two documented Volar plugin paths and removes the obsolete exact " +
               "unplugin-vue-router/client type reference from tsconfig/jsconfig arrays.";
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
                if (!config || !(visited.getValue() instanceof String value)) {
                    return visited;
                }
                String replacement = REPLACEMENTS.get(value);
                if (replacement == null) {
                    return visited;
                }
                String quote = visited.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(replacement).withSource(quote + replacement + quote);
            }

            @Override
            public Json.Array visitArray(Json.Array array, ExecutionContext ctx) {
                Json.Array visited = super.visitArray(array, ctx);
                if (!config || !isClientTypeReferenceArray()) {
                    return visited;
                }
                List<JsonRightPadded<JsonValue>> values = visited.getPadding().getValues();
                List<JsonRightPadded<JsonValue>> retained = new ArrayList<>(values.size());
                for (JsonRightPadded<JsonValue> padded : values) {
                    JsonValue value = padded.getElement();
                    if (!(value instanceof Json.Literal literal) ||
                        !"unplugin-vue-router/client".equals(literal.getValue())) {
                        retained.add(padded);
                    }
                }
                return retained.size() == values.size() ? visited : visited.getPadding().withValues(retained);
            }

            private boolean isClientTypeReferenceArray() {
                Cursor memberCursor = getCursor().getParent();
                if (memberCursor == null || !(memberCursor.getValue() instanceof Json.Member member)) {
                    return false;
                }
                String key = VueRouterManifestSupport.key(member);
                Cursor objectCursor = memberCursor.getParent();
                Cursor ownerCursor = objectCursor == null ? null : objectCursor.getParent();
                if ("include".equals(key)) {
                    return ownerCursor != null && ownerCursor.getValue() instanceof Json.Document;
                }
                if (!"types".equals(key) || ownerCursor == null ||
                    !(ownerCursor.getValue() instanceof Json.Member owner) ||
                    !"compilerOptions".equals(VueRouterManifestSupport.key(owner))) {
                    return false;
                }
                Cursor rootObject = ownerCursor.getParent();
                Cursor document = rootObject == null ? null : rootObject.getParent();
                return document != null && document.getValue() instanceof Json.Document;
            }
        };
    }
}
