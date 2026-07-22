package com.huawei.clouds.openrewrite.marked;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

/** Marks manifest declarations that require a package-manager, Node, or ESM decision. */
public final class FindMarked17ManifestRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Marked 17 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped dependency ownership, unsafe DefinitelyTyped ownership, Node versions below 20, " +
               "and CommonJS package mode at exact package.json values.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean markedManifest;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!MarkedManifestSupport.isPackageJson(document.getSourcePath())) {
                    return document;
                }
                boolean previous = markedManifest;
                markedManifest = containsMarked(document);
                Json.Document visited = super.visitDocument(document, ctx);
                markedManifest = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!markedManifest) {
                    return visited;
                }
                String key = MarkedManifestSupport.key(visited);
                String value = MarkedManifestSupport.stringValue(visited);
                String section = MarkedManifestSupport.directDependencySection(getCursor());
                if (!section.isEmpty() && MarkedManifestSupport.PACKAGE.equals(key) &&
                    !MarkedManifestSupport.TARGET_VERSION.equals(value)) {
                    return markValue(visited, "Strict migration skipped this complex range, protocol, dynamic tag, alias, variable, decorated, or unlisted Marked version; select a tested 17.x constraint and regenerate the lockfile");
                }
                if (MarkedManifestSupport.TYPES_PACKAGE.equals(key)) {
                    return markValue(visited, "@types/marked remains because ownership is duplicated, nested, non-registry, or outside devDependencies; Marked 17 has built-in types, so resolve the declaration owner explicitly");
                }
                if ("node".equals(key) && "engines".equals(parentKey()) && value != null && belowNode20(value)) {
                    return markValue(visited, "Marked 17.0.6 requires Node >=20; update local, CI, container, serverless, Electron, and production runtimes together");
                }
                if ("@types/node".equals(key) && !section.isEmpty() && value != null && belowNode20(value)) {
                    return markValue(visited, "Marked 17.0.6 requires Node >=20; align @types/node with the runtime, test runner, bundler, and CI image");
                }
                if ("type".equals(key) && "commonjs".equals(value) && isRootMember()) {
                    return markValue(visited, "Marked 16 removed its CommonJS build and Marked 17 is ESM-only; migrate imports, test transforms, bundler, SSR, and CLI wrappers deliberately");
                }
                return visited;
            }

            private String parentKey() {
                org.openrewrite.Cursor object = getCursor().getParent();
                org.openrewrite.Cursor parent = object == null ? null : object.getParent();
                return parent != null && parent.getValue() instanceof Json.Member member
                        ? MarkedManifestSupport.key(member) : "";
            }

            private boolean isRootMember() {
                return getCursor().getParent() != null && getCursor().getParent().getParent() != null &&
                       getCursor().getParent().getParent().getValue() instanceof Json.Document;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }
        };
    }

    private static boolean containsMarked(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) {
            return false;
        }
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member section) ||
                !MarkedManifestSupport.DEPENDENCY_SECTIONS.contains(MarkedManifestSupport.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject entries)) {
                continue;
            }
            for (Json entry : entries.getMembers()) {
                if (entry instanceof Json.Member member &&
                    MarkedManifestSupport.PACKAGE.equals(MarkedManifestSupport.key(member))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean belowNode20(String declaration) {
        String compact = declaration.replace(" ", "").toLowerCase();
        if (compact.startsWith(">=20") || compact.startsWith("^20") || compact.startsWith("~20") ||
            compact.matches("2[0-9](?:\\..*)?")) {
            return false;
        }
        return compact.matches(".*(?:^|[^0-9])(?:v?(?:1[0-9]|[0-9]))(?:[^0-9]|$).*") ||
               compact.startsWith(">=12") || compact.startsWith(">=14") || compact.startsWith(">=16") ||
               compact.startsWith(">=18") || compact.startsWith("<20");
    }
}
