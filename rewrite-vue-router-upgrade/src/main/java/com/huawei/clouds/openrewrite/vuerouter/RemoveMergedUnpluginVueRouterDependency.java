package com.huawei.clouds.openrewrite.vuerouter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Removes the package merged into Vue Router 5 when dependency ownership is unambiguous. */
public final class RemoveMergedUnpluginVueRouterDependency extends Recipe {
    private static final Set<String> REMOVABLE_SECTIONS = Set.of("dependencies", "devDependencies");
    private static final Pattern REGISTRY_SEMVER = Pattern.compile(
            "^[~^<>= ]*v?\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?(?:[ <>=~^|].*)?$");

    @Override
    public String getDisplayName() {
        return "Remove merged unplugin-vue-router dependency";
    }

    @Override
    public String getDescription() {
        return "Removes one ordinary unplugin-vue-router dependency/devDependency only when the same package.json " +
               "already selects Vue Router 5.0.3 and no duplicate or nested owner exists.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean safe;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!VueRouterManifestSupport.isPackageJson(document.getSourcePath())) {
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
                if (!safe || !isDirectRemovableSection()) {
                    return visited;
                }
                List<Json> retained = new ArrayList<>(visited.getMembers().size());
                for (Json value : visited.getMembers()) {
                    if (!(value instanceof Json.Member member) ||
                        !VueRouterManifestSupport.UNPLUGIN.equals(VueRouterManifestSupport.key(member))) {
                        retained.add(value);
                    }
                }
                return retained.size() == visited.getMembers().size() ? visited : visited.withMembers(retained);
            }

            private boolean isDirectRemovableSection() {
                org.openrewrite.Cursor section = getCursor().getParent();
                org.openrewrite.Cursor root = section == null ? null : section.getParent();
                org.openrewrite.Cursor document = root == null ? null : root.getParent();
                return section != null && section.getValue() instanceof Json.Member member &&
                       REMOVABLE_SECTIONS.contains(VueRouterManifestSupport.key(member)) &&
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
            String sectionName = VueRouterManifestSupport.key(section);
            for (Json entry : entries.getMembers()) {
                if (!(entry instanceof Json.Member member)) {
                    continue;
                }
                String key = VueRouterManifestSupport.key(member);
                String declaration = VueRouterManifestSupport.stringValue(member);
                if (VueRouterManifestSupport.SECTIONS.contains(sectionName) &&
                    VueRouterManifestSupport.PACKAGE.equals(key) &&
                    VueRouterManifestSupport.targetDeclaration(declaration)) {
                    target = true;
                }
                if (REMOVABLE_SECTIONS.contains(sectionName) && VueRouterManifestSupport.UNPLUGIN.equals(key) &&
                    declaration != null && REGISTRY_SEMVER.matcher(declaration).matches()) {
                    removable = true;
                }
            }
        }
        return target && removable && countOwners(root) == 1;
    }

    private static int countOwners(Json json) {
        if (json instanceof Json.Member member) {
            return (VueRouterManifestSupport.UNPLUGIN.equals(VueRouterManifestSupport.key(member)) ? 1 : 0) +
                   countOwners((Json) member.getValue());
        }
        if (json instanceof Json.JsonObject object) {
            int count = 0;
            for (Json value : object.getMembers()) {
                count += countOwners(value);
            }
            return count;
        }
        if (json instanceof Json.Array array) {
            int count = 0;
            for (Json value : array.getValues()) {
                count += countOwners(value);
            }
            return count;
        }
        return 0;
    }
}
