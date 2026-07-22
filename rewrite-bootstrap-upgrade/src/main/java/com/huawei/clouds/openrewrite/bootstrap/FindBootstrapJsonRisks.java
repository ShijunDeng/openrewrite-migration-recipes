package com.huawei.clouds.openrewrite.bootstrap;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks package, lockfile, browserslist, and JSON build configuration decisions. */
public final class FindBootstrapJsonRisks extends Recipe {
    private static final Set<String> INTEGRATIONS = Set.of(
            "jquery", "popper.js", "@popperjs/core", "node-sass", "sass", "sass-loader",
            "react-bootstrap", "reactstrap", "ngx-bootstrap", "@ng-bootstrap/ng-bootstrap",
            "bootstrap-vue", "bootstrap-vue-next");

    @Override
    public String getDisplayName() {
        return "Find Bootstrap 5 manifest, lockfile, and JSON configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped constraints, jQuery and Popper changes, Sass toolchains, wrappers, legacy browsers, lockfile regeneration, and physical bundle aliases.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean manifest;
            private boolean bootstrapManifest;
            private boolean lockfile;
            private boolean config;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!BootstrapSupport.isProjectPath(document.getSourcePath())) return document;
                boolean oldManifest = manifest;
                boolean oldBootstrap = bootstrapManifest;
                boolean oldLock = lockfile;
                boolean oldConfig = config;
                manifest = BootstrapSupport.isPackageJson(document.getSourcePath());
                bootstrapManifest = manifest && containsBootstrap(document);
                lockfile = BootstrapSupport.isLockfile(document.getSourcePath());
                config = !manifest && BootstrapSupport.isJsonConfig(document.getSourcePath());
                Json.Document visited = super.visitDocument(document, ctx);
                manifest = oldManifest;
                bootstrapManifest = oldBootstrap;
                lockfile = oldLock;
                config = oldConfig;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String key = BootstrapSupport.key(visited);
                String value = BootstrapSupport.stringValue(visited);
                if (lockfile && (BootstrapSupport.PACKAGE.equals(key) ||
                                 ("node_modules/" + BootstrapSupport.PACKAGE).equals(key))) {
                    return mark(visited, "Regenerate this npm lockfile with the repository package manager; resolved artifacts, integrity hashes, peer snapshots, and optional metadata cannot be synthesized safely");
                }
                if (!bootstrapManifest) return visited;
                String section = BootstrapSupport.directSection(getCursor());
                if (!section.isEmpty() && BootstrapSupport.PACKAGE.equals(key) && !BootstrapSupport.target(value)) {
                    return mark(visited, "Strict migration skipped this complex, prerelease, dynamic, protocol, or unlisted Bootstrap declaration; select and test a 5.3.6 constraint");
                }
                if (!section.isEmpty() && INTEGRATIONS.contains(key)) return markIntegration(visited, key);
                return visited;
            }

            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value)) return visited;
                String lower = value.toLowerCase();
                if (bootstrapManifest && (lower.matches(".*(?:^|[, ]|and )ie(?: |>=?|<=?)?1[01](?:$|[, ]).*?") ||
                                          lower.contains("internet explorer"))) {
                    return SearchResult.found(visited,
                            "Bootstrap 5 dropped Internet Explorer and legacy Edge; align browserslist, transpilation, polyfills, CSS prefixes, testing, and support policy");
                }
                if (config && (lower.contains("bootstrap/js/src/") || lower.contains("bootstrap/dist/js/"))) {
                    return SearchResult.found(visited,
                            "This build alias pins a Bootstrap source or physical distribution path; verify package exports, ESM/UMD choice, Popper bundling, tree-shaking, and production chunks");
                }
                return visited;
            }

            private Json.Member markIntegration(Json.Member member, String dependency) {
                String message;
                if ("jquery".equals(dependency)) {
                    message = "Bootstrap 5 dropped jQuery; inventory non-Bootstrap jQuery consumers, replace Bootstrap plugin calls with native classes, and verify events and disposal before removal";
                } else if ("popper.js".equals(dependency)) {
                    message = "Bootstrap 5 uses Popper 2 (@popperjs/core) instead of popper.js v1; migrate direct Popper consumers and verify tooltip, popover, dropdown, boundary, and placement behavior";
                } else if ("@popperjs/core".equals(dependency)) {
                    message = "Bootstrap 5.3.6 peers on @popperjs/core ^2.11.8; verify the selected constraint, duplicate installations, bundle vs standalone Bootstrap entry, placements, and modifiers";
                } else if (Set.of("node-sass", "sass", "sass-loader").contains(dependency)) {
                    message = "Bootstrap 5 replaced Libsass with Dart Sass and changed Sass functions, variables, maps, import order, and deprecations; verify the entire custom CSS build and warnings";
                } else {
                    message = dependency + " owns Bootstrap markup, component lifecycle, peers, events, SSR/hydration, and styling; select a Bootstrap 5-compatible release and follow its migration guide";
                }
                return mark(member, message);
            }

            private Json.Member mark(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }

            private boolean containsBootstrap(Json.Document document) {
                if (!(document.getValue() instanceof Json.JsonObject root)) return false;
                for (Json value : root.getMembers()) {
                    if (!(value instanceof Json.Member section) ||
                        !BootstrapSupport.SECTIONS.contains(BootstrapSupport.key(section)) ||
                        !(section.getValue() instanceof Json.JsonObject dependencies)) continue;
                    for (Json dependency : dependencies.getMembers()) {
                        if (dependency instanceof Json.Member entry &&
                            BootstrapSupport.PACKAGE.equals(BootstrapSupport.key(entry))) return true;
                    }
                }
                return false;
            }
        };
    }
}
