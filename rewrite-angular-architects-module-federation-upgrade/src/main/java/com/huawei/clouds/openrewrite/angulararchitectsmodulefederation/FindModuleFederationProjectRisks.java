package com.huawei.clouds.openrewrite.angulararchitectsmodulefederation;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark project graph and Angular builder decisions that cannot be inferred safely. */
public final class FindModuleFederationProjectRisks extends Recipe {
    private static final Pattern VERSION = Pattern.compile("[~^]?(\\d+)(?:[.](\\d+))?(?:[.](\\d+))?");
    private static final Set<String> ANGULAR_20 = Set.of(
            "@angular/core", "@angular/common", "@angular/compiler", "@angular/compiler-cli",
            "@angular/platform-browser", "@angular/platform-browser-dynamic", "@angular/router",
            "@angular/cli", "@angular-devkit/build-angular"
    );

    @Override
    public String getDisplayName() {
        return "Find Module Federation 20 project and builder risks";
    }

    @Override
    public String getDescription() {
        return "Mark package ownership, Angular 20 alignment, classic webpack companions, runtime peers, Nx builders, " +
               "ApplicationBuilder choices, manifests, assets, and target configuration requiring manual review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean relevant;
            private String fileName;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                Path path = document.getSourcePath();
                if (!ModuleFederationSupport.projectPath(path)) return document;
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                boolean packageJson = "package.json".equals(name) &&
                                      ModuleFederationSupport.containsPackageReference(document.printAll());
                boolean config = Set.of("angular.json", "workspace.json", "project.json", "mf.manifest.json",
                                        "module-federation.manifest.json", "tsconfig.json", "tsconfig.base.json")
                                    .contains(name) && federationConfig(document.printAll(), name);
                if (!packageJson && !config) return document;
                boolean oldRelevant = relevant;
                String oldName = fileName;
                relevant = true;
                fileName = name;
                Json.Document visited = super.visitDocument(document, ctx);
                relevant = oldRelevant;
                fileName = oldName;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!relevant) return visited;
                String key = ModuleFederationSupport.key(visited);
                String value = ModuleFederationSupport.string(visited);
                String section = ModuleFederationSupport.directSection(getCursor());

                if ("package.json".equals(fileName)) {
                    if (ModuleFederationSupport.PACKAGE.equals(key)) {
                        if (ModuleFederationSupport.overrideOrResolutionOwner(getCursor())) {
                            return mark(visited, "This override/resolution independently owns Module Federation; update the true owner and regenerate exactly one selected lockfile");
                        }
                        if ("peerDependencies".equals(section)) {
                            return mark(visited, "This Module Federation declaration is a published peer contract; validate its Angular 20 host range and consumer install behavior instead of treating the exact AUTO result as an application-only dependency bump");
                        }
                        if ("optionalDependencies".equals(section)) {
                            return mark(visited, "This Module Federation declaration owns an optional install/runtime path; verify fallback behavior and every package-manager platform after the selected upgrade");
                        }
                        if (ModuleFederationSupport.SECTIONS.contains(section) &&
                            !ModuleFederationSupport.selected(value) && !ModuleFederationSupport.target(value)) {
                            return mark(visited, "This declaration is outside workbook versions or uses a dynamic/range/protocol/workspace owner; strict AUTO deliberately leaves it unchanged");
                        }
                    }
                    if ("scripts".equals(section) && value != null &&
                        ModuleFederationSupport.containsPackageReference(value) &&
                        value.matches(".*\\b(?:ng|nx)\\s+(?:add|g|generate)\\b.*")) {
                        String lower = value.toLowerCase();
                        if (lower.contains("module-federation-webpack") || lower.contains(":init-webpack")) {
                            return mark(visited, "This script explicitly owns the classic-webpack stack; keep Angular 20, ngx-build-plus/@nx webpack builders, dev-server and production webpack config aligned");
                        }
                        if (lower.contains("rspack") || lower.contains("rsbuild")) {
                            return mark(visited, "This script explicitly selects the experimental rspack/rsbuild stack; verify target schema, builder/config format, runtime peers and production support before rollout");
                        }
                        if (lower.contains("native-federation-esbuild")) {
                            return mark(visited, "This script selects Native Federation/esbuild, which is a different package/runtime architecture; migrate config, manifest, bootstrap and deployment as an explicit project");
                        }
                        return mark(visited, "This schematic command still requires an explicit target stack or was too dynamic/chained for AUTO; choose classic webpack, rspack/rsbuild or Native Federation deliberately");
                    }
                    if (!ModuleFederationSupport.SECTIONS.contains(section)) return visited;
                    if (ANGULAR_20.contains(key) && !majorEquals(value, 20)) {
                        return mark(visited, "Module Federation 20 belongs to the Angular 20 line; run Angular CLI migrations by major and align CLI, compiler, runtime, Node, TypeScript and RxJS before federation verification");
                    }
                    if (ModuleFederationSupport.RUNTIME.equals(key) && !majorEquals(value, 20)) {
                        return mark(visited, "The explicitly owned runtime must align to 20.0.0; review root versus /runtime imports and avoid duplicate runtime copies");
                    }
                    if ("ngx-build-plus".equals(key) && !majorEquals(value, 20)) {
                        return mark(visited, "Classic webpack on Angular 20 requires ngx-build-plus ^20; this value is not the exact generated companion AUTO can safely replace");
                    }
                    if (Set.of("@module-federation/enhanced", "@module-federation/runtime-core").contains(key)) {
                        return mark(visited, "The 20 runtime declares Module Federation enhanced/runtime-core peers; align this explicit owner and test enhanced versus classic runtime isolation");
                    }
                    if (key.startsWith("@nrwl/") || Set.of("nx", "@nx/angular").contains(key)) {
                        return mark(visited, "Nx 15/16 builder names and project configuration require the Nx migration chain before Angular 20; verify @nx/angular webpack builder selection and serve targets");
                    }
                    if (Set.of("rxjs", "typescript", "zone.js").contains(key)) {
                        return mark(visited, "Validate this tool/runtime version against Angular 20's supported matrix and every host/remote; do not infer it from the federation package alone");
                    }
                    return visited;
                }

                if (Set.of("angular.json", "workspace.json").contains(fileName) && !federationBranch()) {
                    return visited;
                }

                if ("builder".equals(key) && value != null) {
                    if (value.contains(":application")) {
                        return mark(visited, "ApplicationBuilder/esbuild cannot consume the existing webpack config directly; explicitly choose Native Federation or an intentional classic-webpack downgrade");
                    }
                    if (value.startsWith("ngx-build-plus:") || value.startsWith("@nrwl/angular:") ||
                        value.startsWith("@nx/angular:")) {
                        return mark(visited, "Review this build/serve builder after Angular and Nx migration; preserve matching webpack config ownership and production target wiring");
                    }
                }
                if (Set.of("extraWebpackConfig", "customWebpackConfig", "publicHost", "browserTarget", "buildTarget")
                       .contains(key)) {
                    return mark(visited, "Review this classic webpack target option under Angular 20, including dev-server URL, configuration-specific config, target renames, and file existence");
                }
                if ("assets".equals(key) || "outputPath".equals(key) || "main".equals(key) || "browser".equals(key)) {
                    return mark(visited, "Angular 17-20 builder migrations changed browser/main, output and public assets conventions; verify manifest deployment paths without moving them automatically");
                }
                if ("target".equals(key) && value != null && !supportedTarget(value)) {
                    return mark(visited, "The target schematic raises old TypeScript targets to at least ES2020; align the final target with Angular 20 and all host/remote browsers");
                }
                if ((key.toLowerCase().contains("remote") || key.toLowerCase().contains("manifest")) && value != null) {
                    return mark(visited, "Validate this cross-deployment contract: remote name, entry URL, module/script format, exposed contract, base href, CORS and cache behavior for the deployment-owned manifest entry");
                }
                return visited;
            }

            private Json.Member mark(Json.Member member, String message) {
                return ModuleFederationSupport.mark(member, message);
            }

            private boolean federationBranch() {
                org.openrewrite.Cursor current = getCursor();
                while (current != null) {
                    if (current.getValue() instanceof Json.Member owner) {
                        if ("projects".equals(ModuleFederationSupport.key(owner))) return false;
                        if (federationConfig(owner.printTrimmed(current), fileName)) return true;
                    }
                    if (current.getParent() == null) break;
                    current = current.getParentTreeCursor();
                }
                // Flat/single-project fixtures and workspace files have no projects boundary.
                return true;
            }
        };
    }

    private static boolean federationConfig(String source, String name) {
        if (name.startsWith("tsconfig")) return ModuleFederationSupport.containsPackageReference(source) || source.contains("remoteEntry");
        return source.contains("module-federation") || source.contains("ngx-build-plus") || source.contains("remoteEntry") ||
               source.contains("mf.manifest") || source.contains("extraWebpackConfig") || source.contains("customWebpackConfig");
    }

    private static boolean majorEquals(String value, int expected) {
        Matcher matcher = VERSION.matcher(value == null ? "" : value.trim());
        return matcher.matches() && Integer.parseInt(matcher.group(1)) == expected;
    }

    private static boolean supportedTarget(String value) {
        return value != null && value.trim().toLowerCase().matches("es(?:20(?:2[0-9]|3[0-9])|next)");
    }
}
