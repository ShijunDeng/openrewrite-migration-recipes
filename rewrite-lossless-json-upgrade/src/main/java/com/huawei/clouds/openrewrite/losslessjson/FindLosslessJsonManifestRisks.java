package com.huawei.clouds.openrewrite.losslessjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

/** Marks package.json declarations that need an explicit package-manager or runtime decision. */
public final class FindLosslessJsonManifestRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find lossless-json manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks non-literal dependency ownership, unsafe DefinitelyTyped declarations, Node 16, and " +
               "module-mode values at their exact package.json values.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean losslessManifest;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!LosslessJsonManifestSupport.isPackageJson(document.getSourcePath())) {
                    return document;
                }
                boolean previous = losslessManifest;
                losslessManifest = containsLosslessJson(document);
                Json.Document visited = super.visitDocument(document, ctx);
                losslessManifest = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!losslessManifest) {
                    return visited;
                }
                String key = LosslessJsonManifestSupport.key(visited);
                String value = LosslessJsonManifestSupport.stringValue(visited);
                String section = LosslessJsonManifestSupport.directDependencySection(getCursor());
                if (!section.isEmpty() && LosslessJsonManifestSupport.PACKAGE.equals(key) &&
                    !LosslessJsonManifestSupport.TARGET_VERSION.equals(value)) {
                    return markValue(visited, "Strict migration skipped this lossless-json range, dynamic tag, protocol, alias, variable, or unlisted version; select and lock an intended 4.x constraint explicitly");
                }
                if (LosslessJsonManifestSupport.TYPES_PACKAGE.equals(key)) {
                    return markValue(visited, "@types/lossless-json remains because ownership is duplicated, non-registry, or outside devDependencies; 4.0.1 has built-in declarations, so resolve the TypeScript owner explicitly");
                }
                if ("@types/node".equals(key) && !section.isEmpty() && value != null && node16(value)) {
                    return markValue(visited, "lossless-json 3 dropped official Node.js 16 support; align runtime, CI, container, and @types/node on a supported Node release");
                }
                if ("node".equals(key) && "engines".equals(parentKey()) && value != null && node16(value)) {
                    return markValue(visited, "lossless-json 3 dropped official Node.js 16 support; update every runtime and CI image, not only this engines declaration");
                }
                if ("type".equals(key) && value != null && ("module".equals(value) || "commonjs".equals(value)) &&
                    getCursor().getParent() != null && getCursor().getParent().getParent() != null &&
                    getCursor().getParent().getParent().getValue() instanceof Json.Document) {
                    return markValue(visited, "4.0.1 resolves its public root through conditional import/require exports; verify this project module mode, test runner, bundler, SSR, and mocks against the public root entry");
                }
                return visited;
            }

            private String parentKey() {
                org.openrewrite.Cursor object = getCursor().getParent();
                org.openrewrite.Cursor parent = object == null ? null : object.getParent();
                return parent != null && parent.getValue() instanceof Json.Member member
                        ? LosslessJsonManifestSupport.key(member) : "";
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }
        };
    }

    private static boolean containsLosslessJson(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) {
            return false;
        }
        for (Json json : root.getMembers()) {
            if (!(json instanceof Json.Member section) ||
                !LosslessJsonManifestSupport.DEPENDENCY_SECTIONS.contains(LosslessJsonManifestSupport.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject values)) {
                continue;
            }
            for (Json value : values.getMembers()) {
                if (value instanceof Json.Member member &&
                    LosslessJsonManifestSupport.PACKAGE.equals(LosslessJsonManifestSupport.key(member))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean node16(String declaration) {
        String compact = declaration.replace(" ", "").toLowerCase();
        return compact.matches(".*(?:^|[^0-9])v?16(?:[^0-9]|$).*") ||
               compact.startsWith("<18") || compact.contains("<18.0");
    }
}
