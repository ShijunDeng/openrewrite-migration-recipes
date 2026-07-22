package com.huawei.clouds.openrewrite.uuid;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Remove a single ordinary @types/uuid devDependency when uuid owns its declarations. */
public final class RemoveRedundantUuidTypes extends Recipe {
    private static final Pattern REGISTRY_VERSION = Pattern.compile(
            "^[~^]?\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$");

    @Override
    public String getDisplayName() {
        return "Remove redundant uuid DefinitelyTyped dependency";
    }

    @Override
    public String getDescription() {
        return "Remove one ordinary @types/uuid devDependency only when the same package.json has direct uuid " +
               "13.0.2 ownership and no duplicate or protocol-based @types declaration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean safe;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!UuidManifestSupport.isPackageJson(document.getSourcePath())) {
                    return document;
                }
                boolean previous = safe;
                safe = safeToRemove(document);
                Json.Document visited = super.visitDocument(document, ctx);
                safe = previous;
                return visited;
            }

            @Override
            public Json.JsonObject visitObject(Json.JsonObject object, ExecutionContext ctx) {
                Json.JsonObject visited = super.visitObject(object, ctx);
                if (!safe || !isRootDevDependencies()) {
                    return visited;
                }
                List<Json> retained = new ArrayList<>(visited.getMembers().size());
                for (Json json : visited.getMembers()) {
                    if (!(json instanceof Json.Member member) ||
                        !UuidManifestSupport.TYPES_PACKAGE.equals(UuidManifestSupport.key(member))) {
                        retained.add(json);
                    }
                }
                return retained.size() == visited.getMembers().size() ? visited : visited.withMembers(retained);
            }

            private boolean isRootDevDependencies() {
                org.openrewrite.Cursor section = UuidManifestSupport.parentTree(getCursor());
                org.openrewrite.Cursor root = UuidManifestSupport.parentTree(section);
                org.openrewrite.Cursor document = UuidManifestSupport.parentTree(root);
                return section != null && section.getValue() instanceof Json.Member member &&
                       "devDependencies".equals(UuidManifestSupport.key(member)) &&
                       document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static boolean safeToRemove(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) {
            return false;
        }
        boolean target = false;
        int typesCount = 0;
        boolean removable = false;
        for (Json json : root.getMembers()) {
            if (!(json instanceof Json.Member section) || !(section.getValue() instanceof Json.JsonObject entries)) {
                continue;
            }
            String sectionName = UuidManifestSupport.key(section);
            for (Json jsonEntry : entries.getMembers()) {
                if (!(jsonEntry instanceof Json.Member entry)) {
                    continue;
                }
                String key = UuidManifestSupport.key(entry);
                String value = UuidManifestSupport.stringValue(entry);
                if (UuidManifestSupport.DEPENDENCY_SECTIONS.contains(sectionName) &&
                    UuidManifestSupport.PACKAGE.equals(key) && UuidManifestSupport.TARGET_VERSION.equals(value)) {
                    target = true;
                }
                if (UuidManifestSupport.TYPES_PACKAGE.equals(key)) {
                    typesCount++;
                    removable = "devDependencies".equals(sectionName) && value != null &&
                                REGISTRY_VERSION.matcher(value).matches();
                }
            }
        }
        return target && typesCount == 1 && removable;
    }
}
