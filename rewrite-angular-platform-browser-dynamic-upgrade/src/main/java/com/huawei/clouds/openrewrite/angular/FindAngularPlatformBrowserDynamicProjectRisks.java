package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Mark dependency, toolchain, AOT, Ivy, builder, and SSR project boundaries. */
public final class FindAngularPlatformBrowserDynamicProjectRisks extends Recipe {
    private static final Set<String> ANGULAR_PEERS = Set.of(
            "@angular/common", "@angular/compiler", "@angular/core", "@angular/platform-browser");

    @Override
    public String getDisplayName() {
        return "Find Angular platform-browser-dynamic project migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved declarations, Angular peers, incompatible Node/TypeScript, ngcc, disabled AOT/Ivy/strict templates, custom builders, and SSR targets.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean relevantPackage;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean old = relevantPackage;
                relevantPackage = isPackageJson(document.getSourcePath()) && containsManagedPackage(document.getValue());
                Json.Document visited = super.visitDocument(document, ctx);
                relevantPackage = old;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                String file = document.getSourcePath().getFileName() == null ? "" :
                        document.getSourcePath().getFileName().toString();
                String name = UpgradeSelectedAngularPlatformBrowserDynamicDependency.key(visited);
                String value = stringValue(visited);
                String parent = parentKey();
                if ("package.json".equals(file) && relevantPackage) {
                    if (!directSection().isEmpty() && UpgradeSelectedAngularPlatformBrowserDynamicDependency.PACKAGE.equals(name) &&
                        !UpgradeSelectedAngularPlatformBrowserDynamicDependency.TARGET.equals(value)) {
                        return markValue(visited, "Unlisted, complex, protocol, alias, tag, or dynamic platform-browser-dynamic declaration was not changed; resolve its owner before selecting 20.3.26");
                    }
                    if (!directSection().isEmpty() && ANGULAR_PEERS.contains(name) &&
                        !UpgradeSelectedAngularPlatformBrowserDynamicDependency.TARGET.equals(value)) {
                        return markValue(visited, "platform-browser-dynamic requires core/common/compiler/platform-browser at the exact same 20.3.26 patch");
                    }
                    if (!directSection().isEmpty() && "typescript".equals(name) && !supportedTypeScript(value)) {
                        return markValue(visited, "Angular 20 requires TypeScript >=5.8 and <6.0 across compiler, editor, tests, and CI");
                    }
                    if ("engines".equals(parent) && "node".equals(name) && !supportedNode(value)) {
                        return markValue(visited, "Angular 20.3.26 requires Node ^20.19.0, ^22.12.0, or >=24.0.0 in every build/runtime environment");
                    }
                    if ("scripts".equals(parent) && value != null && value.contains("ngcc")) {
                        return markValue(visited, "ngcc/View Engine processing is obsolete under Angular 20; remove the script after replacing non-Ivy libraries and validating package exports");
                    }
                }
                if (("angular.json".equals(file) || "workspace.json".equals(file))) {
                    if (("aot".equals(name) || "buildOptimizer".equals(name)) && Boolean.FALSE.equals(literalValue(visited))) {
                        return SearchResult.found(visited, "AOT/build optimization is disabled; enable the Angular 20 production compiler before removing browser-dynamic JIT providers");
                    }
                    if ("builder".equals(name) && value != null &&
                        !value.startsWith("@angular-devkit/build-angular:") && !value.startsWith("@angular/build:")) {
                        return markValue(visited, "Custom builder detected; prove Angular 20 AOT, template resources, lazy chunks, CSP and SSR behavior before removing JIT support");
                    }
                    if ("server".equals(name) || "ssr".equals(name) || "prerender".equals(name)) {
                        return SearchResult.found(visited, "SSR/prerender must use request-scoped platform/bootstrap context and identical precompiled client/server templates before hydration");
                    }
                }
                if (("tsconfig.json".equals(file) || file.startsWith("tsconfig.")) &&
                    "angularCompilerOptions".equals(parent) &&
                    (("enableIvy".equals(name) || "fullTemplateTypeCheck".equals(name) || "strictTemplates".equals(name)) &&
                     Boolean.FALSE.equals(literalValue(visited)))) {
                    return SearchResult.found(visited, "Legacy compiler/template checking is disabled; enable Ivy and strict template checks and fix diagnostics before removing runtime JIT assumptions");
                }
                return visited;
            }

            private String directSection() {
                Cursor object = getCursor().getParent();
                Cursor section = object == null ? null : object.getParent();
                Cursor root = section == null ? null : section.getParent();
                Cursor document = root == null ? null : root.getParent();
                if (section != null && section.getValue() instanceof Json.Member sectionMember &&
                    UpgradeSelectedAngularPlatformBrowserDynamicDependency.SECTIONS.contains(
                            UpgradeSelectedAngularPlatformBrowserDynamicDependency.key(sectionMember)) &&
                    document != null && document.getValue() instanceof Json.Document) {
                    return UpgradeSelectedAngularPlatformBrowserDynamicDependency.key(sectionMember);
                }
                return "";
            }

            private String parentKey() {
                Cursor object = getCursor().getParent();
                Cursor parent = object == null ? null : object.getParent();
                return parent != null && parent.getValue() instanceof Json.Member parentMember
                        ? UpgradeSelectedAngularPlatformBrowserDynamicDependency.key(parentMember) : "";
            }

            private Json.Member markValue(Json.Member value, String message) {
                return value.getValue() instanceof Json.Literal
                        ? value.withValue(SearchResult.found(value.getValue(), message)) : SearchResult.found(value, message);
            }
        };
    }

    private static boolean isPackageJson(Path path) {
        return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    private static boolean containsManagedPackage(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) {
            return false;
        }
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member section) ||
                !UpgradeSelectedAngularPlatformBrowserDynamicDependency.SECTIONS.contains(
                        UpgradeSelectedAngularPlatformBrowserDynamicDependency.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject dependencies)) {
                continue;
            }
            if (dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                    UpgradeSelectedAngularPlatformBrowserDynamicDependency.PACKAGE.equals(
                            UpgradeSelectedAngularPlatformBrowserDynamicDependency.key(dependency)))) {
                return true;
            }
        }
        return false;
    }

    private static String stringValue(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : null;
    }

    private static Object literalValue(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal ? literal.getValue() : null;
    }

    private static boolean supportedTypeScript(String declaration) {
        int[] version = scalar(declaration);
        return version != null && version[0] == 5 && version[1] >= 8;
    }

    private static boolean supportedNode(String declaration) {
        if ("^20.19.0 || ^22.12.0 || >=24.0.0".equals(declaration)) {
            return true;
        }
        int[] version = scalar(declaration);
        return version != null && ((version[0] == 20 && version[1] >= 19) ||
                                   (version[0] == 22 && version[1] >= 12) || version[0] >= 24);
    }

    private static int[] scalar(String declaration) {
        if (declaration == null) {
            return null;
        }
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) {
            return null;
        }
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
