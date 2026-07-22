package com.huawei.clouds.openrewrite.vuerouter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks manifest decisions that cannot be safely selected during the Vue Router 5 migration. */
public final class FindVueRouterManifestRisks extends Recipe {
    private static final Set<String> VUE2_PACKAGES = Set.of(
            "vue-template-compiler", "vue-server-renderer", "@vitejs/plugin-vue2");

    @Override
    public String getDisplayName() {
        return "Find Vue Router 5 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped Vue Router declarations, unresolved unplugin ownership, incompatible Vue peers, " +
               "and Vue 2-only compiler, SSR, or build packages at their exact manifest values.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean routerManifest;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!VueRouterManifestSupport.isPackageJson(document.getSourcePath())) {
                    return document;
                }
                boolean previous = routerManifest;
                routerManifest = containsRouter(document);
                Json.Document visited = super.visitDocument(document, ctx);
                routerManifest = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!routerManifest) {
                    return visited;
                }
                String section = VueRouterManifestSupport.directSection(getCursor());
                if (section.isEmpty()) {
                    return visited;
                }
                String key = VueRouterManifestSupport.key(visited);
                String value = VueRouterManifestSupport.stringValue(visited);
                if (VueRouterManifestSupport.PACKAGE.equals(key) &&
                    !VueRouterManifestSupport.targetDeclaration(value)) {
                    return markValue(visited, "Strict migration skipped this complex range, protocol, dynamic, decorated, or unlisted Vue Router declaration; select a tested 5.x constraint and regenerate the lockfile");
                }
                if (VueRouterManifestSupport.UNPLUGIN.equals(key)) {
                    return markValue(visited, "unplugin-vue-router remains because ownership is duplicated, nested, protocol-based, or outside dependencies/devDependencies; Vue Router 5 merged it, so migrate every entry point before removing its owner");
                }
                if ("vue".equals(key) && !supportsVue35(value)) {
                    return markValue(visited, "Vue Router 5.0.3 requires Vue ^3.5.0; complete Vue 2-to-3 or older Vue 3 migration across runtime, compiler, tests, SSR, and UI libraries first");
                }
                if (VUE2_PACKAGES.contains(key)) {
                    return markValue(visited, key + " belongs to the Vue 2 toolchain and is incompatible with Vue Router 5's Vue 3.5 peer; migrate the owning compiler, SSR, or bundler setup");
                }
                if ("@vue/compiler-sfc".equals(key) && !atLeast(value, 3, 5)) {
                    return markValue(visited, "Vue Router 5 file-based routing requires @vue/compiler-sfc ^3.5.17; align it with the exact Vue runtime and language tooling");
                }
                if ("pinia".equals(key) && !atLeast(value, 3, 0)) {
                    return markValue(visited, "Vue Router 5 file-based routing declares Pinia ^3.0.4; verify stores, SSR hydration, plugins, and data-loader integration before aligning it");
                }
                if ("@pinia/colada".equals(key) && !atLeast(value, 0, 21)) {
                    return markValue(visited, "Vue Router 5.0.3 experimental Pinia Colada loaders require @pinia/colada >=0.21.2; align loader contracts and error/reroute behavior");
                }
                return visited;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }
        };
    }

    private static boolean containsRouter(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) {
            return false;
        }
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member section) ||
                !VueRouterManifestSupport.SECTIONS.contains(VueRouterManifestSupport.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject entries)) {
                continue;
            }
            for (Json entry : entries.getMembers()) {
                if (entry instanceof Json.Member member &&
                    VueRouterManifestSupport.PACKAGE.equals(VueRouterManifestSupport.key(member))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean supportsVue35(String declaration) {
        return atLeast(declaration, 3, 5) && declaration != null && !declaration.startsWith(">");
    }

    private static boolean atLeast(String declaration, int major, int minor) {
        if (declaration == null) {
            return false;
        }
        String value = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        if (!value.matches("\\d+\\.\\d+(?:\\.\\d+)?")) {
            return false;
        }
        String[] parts = value.split("\\.");
        int actualMajor = Integer.parseInt(parts[0]);
        int actualMinor = Integer.parseInt(parts[1]);
        return actualMajor == major && actualMinor >= minor;
    }
}
