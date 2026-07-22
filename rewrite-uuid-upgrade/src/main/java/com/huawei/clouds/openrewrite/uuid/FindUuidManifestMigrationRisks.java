package com.huawei.clouds.openrewrite.uuid;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

/** Mark package.json declarations that require a uuid 13 runtime or dependency-ownership decision. */
public final class FindUuidManifestMigrationRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find uuid 13 package manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark skipped uuid constraints, remaining DefinitelyTyped ownership, old Node engines, CommonJS " +
               "package mode, and React Native dependencies at their exact package.json values.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean uuidManifest;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!UuidManifestSupport.isPackageJson(document.getSourcePath())) {
                    return document;
                }
                boolean previous = uuidManifest;
                uuidManifest = containsUuid(document);
                Json.Document visited = super.visitDocument(document, ctx);
                uuidManifest = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!uuidManifest) {
                    return visited;
                }
                String key = UuidManifestSupport.key(visited);
                String value = UuidManifestSupport.stringValue(visited);
                String section = UuidManifestSupport.directDependencySection(getCursor());
                if (!section.isEmpty() && UuidManifestSupport.PACKAGE.equals(key) &&
                    !UuidManifestSupport.TARGET_VERSION.equals(value)) {
                    return markValue(visited,
                            "Strict migration skipped this uuid range, prerelease, protocol, alias, dynamic tag, or unlisted version; select and lock the intended 13.0.2 constraint explicitly");
                }
                if (UuidManifestSupport.TYPES_PACKAGE.equals(key)) {
                    return markValue(visited,
                            "@types/uuid remains because ownership is duplicated, non-registry, or outside a removable devDependency; uuid 13 owns its declarations");
                }
                if ("node".equals(key) && "engines".equals(parentKey()) && value != null && oldNode(value)) {
                    return markValue(visited,
                            "uuid 13 no longer supports these old Node lines; update local, CI, container, serverless, and production runtimes to a supported release");
                }
                if ("type".equals(key) && "commonjs".equals(value) && isRootMember()) {
                    return markValue(visited,
                            "uuid 12+ is ESM-only while this package declares CommonJS; migrate/bundle the consuming boundary and verify tests plus published exports");
                }
                if ("main".equals(key) && value != null && value.endsWith(".cjs") && isRootMember()) {
                    return markValue(visited,
                            "This package publishes a CommonJS entry while uuid 13 is ESM-only; verify the bundling boundary and downstream consumers");
                }
                if (!section.isEmpty() && "react-native".equals(key)) {
                    return markValue(visited,
                            "React Native/Expo requires crypto.getRandomValues; ensure react-native-get-random-values loads before every direct or transitive uuid import");
                }
                return visited;
            }

            private String parentKey() {
                org.openrewrite.Cursor object = UuidManifestSupport.parentTree(getCursor());
                org.openrewrite.Cursor parent = UuidManifestSupport.parentTree(object);
                return parent != null && parent.getValue() instanceof Json.Member member
                        ? UuidManifestSupport.key(member) : "";
            }

            private boolean isRootMember() {
                org.openrewrite.Cursor object = UuidManifestSupport.parentTree(getCursor());
                org.openrewrite.Cursor document = UuidManifestSupport.parentTree(object);
                return document != null && document.getValue() instanceof Json.Document;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }
        };
    }

    private static boolean containsUuid(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) {
            return false;
        }
        for (Json json : root.getMembers()) {
            if (!(json instanceof Json.Member section) ||
                !UuidManifestSupport.DEPENDENCY_SECTIONS.contains(UuidManifestSupport.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject entries)) {
                continue;
            }
            for (Json entry : entries.getMembers()) {
                if (entry instanceof Json.Member member &&
                    UuidManifestSupport.PACKAGE.equals(UuidManifestSupport.key(member))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean oldNode(String declaration) {
        String compact = declaration.replace(" ", "").toLowerCase();
        return compact.matches(".*(?:^|[^0-9])v?(?:8|10|12|14|16)(?:[^0-9]|$).*") ||
               compact.startsWith("<18") || compact.contains("<18.0");
    }
}
