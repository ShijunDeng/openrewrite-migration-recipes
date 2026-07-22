package com.huawei.clouds.openrewrite.marked;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Removes one ordinary @types/marked devDependency only when Marked 17.0.6 owns declarations. */
public final class RemoveRedundantMarkedTypes extends Recipe {
    private static final Pattern REGISTRY_SEMVER = Pattern.compile(
            "^[~^<>= ]*v?\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?(?:[ <>=~^|].*)?$");

    @Override
    public String getDisplayName() {
        return "Remove redundant Marked DefinitelyTyped dependency";
    }

    @Override
    public String getDescription() {
        return "Removes one ordinary @types/marked devDependency only when the same package.json has a direct " +
               "marked 17.0.6 declaration and no other direct or nested owner.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean safe;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!MarkedManifestSupport.isPackageJson(document.getSourcePath())) {
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
                for (Json value : visited.getMembers()) {
                    if (!(value instanceof Json.Member member) ||
                        !MarkedManifestSupport.TYPES_PACKAGE.equals(MarkedManifestSupport.key(member))) {
                        retained.add(value);
                    }
                }
                return retained.size() == visited.getMembers().size() ? visited : visited.withMembers(retained);
            }

            private boolean isDirectDevDependenciesObject() {
                org.openrewrite.Cursor section = getCursor().getParent();
                org.openrewrite.Cursor root = section == null ? null : section.getParent();
                org.openrewrite.Cursor document = root == null ? null : root.getParent();
                return section != null && section.getValue() instanceof Json.Member member &&
                       "devDependencies".equals(MarkedManifestSupport.key(member)) &&
                       document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static boolean safeToRemove(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) {
            return false;
        }
        boolean target = false;
        boolean removable = false;
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member section) || !(section.getValue() instanceof Json.JsonObject entries)) {
                continue;
            }
            String sectionName = MarkedManifestSupport.key(section);
            for (Json entry : entries.getMembers()) {
                if (!(entry instanceof Json.Member member)) {
                    continue;
                }
                String key = MarkedManifestSupport.key(member);
                String declaration = MarkedManifestSupport.stringValue(member);
                if (MarkedManifestSupport.DEPENDENCY_SECTIONS.contains(sectionName) &&
                    MarkedManifestSupport.PACKAGE.equals(key) && MarkedManifestSupport.TARGET_VERSION.equals(declaration)) {
                    target = true;
                }
                if ("devDependencies".equals(sectionName) && MarkedManifestSupport.TYPES_PACKAGE.equals(key) &&
                    declaration != null && REGISTRY_SEMVER.matcher(declaration).matches()) {
                    removable = true;
                }
            }
        }
        return target && removable && countTypesOwners(root) == 1;
    }

    private static int countTypesOwners(Json json) {
        if (json instanceof Json.Member member) {
            return (MarkedManifestSupport.TYPES_PACKAGE.equals(MarkedManifestSupport.key(member)) ? 1 : 0) +
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
