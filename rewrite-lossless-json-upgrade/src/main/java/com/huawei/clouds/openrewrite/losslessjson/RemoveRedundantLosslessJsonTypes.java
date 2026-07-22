package com.huawei.clouds.openrewrite.losslessjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Removes a single ordinary @types/lossless-json devDependency when 4.0.1 owns its declarations. */
public final class RemoveRedundantLosslessJsonTypes extends Recipe {
    private static final Pattern REGISTRY_SEMVER = Pattern.compile(
            "^[~^<>= ]*v?\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?(?:[ <>=~^|].*)?$");

    @Override
    public String getDisplayName() {
        return "Remove redundant lossless-json DefinitelyTyped dependency";
    }

    @Override
    public String getDescription() {
        return "Removes one ordinary @types/lossless-json devDependency only when the same package.json has a " +
               "direct lossless-json 4.0.1 dependency and no second @types owner or protocol reference.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean safe;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!LosslessJsonManifestSupport.isPackageJson(document.getSourcePath())) {
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
                if (!safe || !isDirectDevDependenciesObject()) {
                    return visited;
                }
                List<Json> retained = new ArrayList<>(visited.getMembers().size());
                for (Json json : visited.getMembers()) {
                    if (!(json instanceof Json.Member member) ||
                        !LosslessJsonManifestSupport.TYPES_PACKAGE.equals(LosslessJsonManifestSupport.key(member))) {
                        retained.add(json);
                    }
                }
                return retained.size() == visited.getMembers().size() ? visited : visited.withMembers(retained);
            }

            private boolean isDirectDevDependenciesObject() {
                org.openrewrite.Cursor section = getCursor().getParent();
                org.openrewrite.Cursor root = section == null ? null : section.getParent();
                org.openrewrite.Cursor document = root == null ? null : root.getParent();
                return section != null && section.getValue() instanceof Json.Member member &&
                       "devDependencies".equals(LosslessJsonManifestSupport.key(member)) &&
                       document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static boolean safeToRemove(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) {
            return false;
        }
        boolean targetPresent = false;
        boolean removableTypes = false;
        for (Json json : root.getMembers()) {
            if (!(json instanceof Json.Member section) || !(section.getValue() instanceof Json.JsonObject values)) {
                continue;
            }
            String sectionName = LosslessJsonManifestSupport.key(section);
            for (Json entry : values.getMembers()) {
                if (!(entry instanceof Json.Member member)) {
                    continue;
                }
                String key = LosslessJsonManifestSupport.key(member);
                String value = LosslessJsonManifestSupport.stringValue(member);
                if (LosslessJsonManifestSupport.DEPENDENCY_SECTIONS.contains(sectionName) &&
                    LosslessJsonManifestSupport.PACKAGE.equals(key) &&
                    LosslessJsonManifestSupport.TARGET_VERSION.equals(value)) {
                    targetPresent = true;
                }
                if (LosslessJsonManifestSupport.TYPES_PACKAGE.equals(key)) {
                    removableTypes = "devDependencies".equals(sectionName) && value != null &&
                                     REGISTRY_SEMVER.matcher(value).matches();
                }
            }
        }
        return targetPresent && countTypesOwners(root) == 1 && removableTypes;
    }

    private static int countTypesOwners(Json json) {
        if (json instanceof Json.Member member) {
            return (LosslessJsonManifestSupport.TYPES_PACKAGE.equals(LosslessJsonManifestSupport.key(member)) ? 1 : 0) +
                   countTypesOwners((Json) member.getValue());
        }
        if (json instanceof Json.JsonObject object) {
            int count = 0;
            for (Json member : object.getMembers()) {
                count += countTypesOwners(member);
            }
            return count;
        }
        if (json instanceof Json.Array array) {
            int count = 0;
            for (Json value : array.getValues()) {
                count += countTypesOwners(value);
            }
            return count;
        }
        return 0;
    }
}
