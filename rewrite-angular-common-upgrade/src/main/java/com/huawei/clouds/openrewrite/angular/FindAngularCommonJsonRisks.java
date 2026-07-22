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

/** Marks package and Angular configuration values that require coordinated application choices. */
public final class FindAngularCommonJsonRisks extends Recipe {
    private static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> ANGULAR_PACKAGE_GROUP = Set.of(
            "@angular/animations", "@angular/core", "@angular/compiler", "@angular/compiler-cli",
            "@angular/forms", "@angular/platform-browser", "@angular/platform-browser-dynamic",
            "@angular/platform-server", "@angular/router", "@angular/service-worker"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Common 20 package and configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks exact JSON values that need lockstep Angular packages, supported tooling, lockfile " +
               "regeneration, Ivy/strict-template review, or builder/SSR decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                Path path = document.getSourcePath();
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(file)) {
                    return containsKey(document.getValue(), "@angular/common") ? inspectPackageJson(visited) : visited;
                }
                if ("angular.json".equals(file) || "workspace.json".equals(file)) {
                    return inspectWorkspace(visited);
                }
                if (file.startsWith("tsconfig") && file.endsWith(".json") &&
                    containsKey(document.getValue(), "angularCompilerOptions")) {
                    return inspectTsConfig(visited);
                }
                return visited;
            }

            private Json.Member inspectPackageJson(Json.Member member) {
                String name = UpgradeSelectedAngularCommonDependency.key(member);
                if (!(member.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration)) {
                    return member;
                }
                String parent = parentKey();
                if (DEPENDENCY_SECTIONS.contains(parent) && "@angular/common".equals(name) &&
                    !UpgradeSelectedAngularCommonDependency.TARGET_VERSION.equals(declaration) &&
                    !UpgradeSelectedAngularCommonDependency.SOURCE_VERSIONS.contains(declaration)) {
                    return markValue(member, "@angular/common declaration is a range, protocol, variable, unlisted, or newer version; select the intended constraint and regenerate this package manager's lockfile");
                }
                if (DEPENDENCY_SECTIONS.contains(parent) && ANGULAR_PACKAGE_GROUP.contains(name) &&
                    !UpgradeSelectedAngularCommonDependency.TARGET_VERSION.equals(declaration)) {
                    return markValue(member, "Angular framework packages must be aligned with @angular/common 20.3.26; run each major's official ng update migration before final alignment");
                }
                if (DEPENDENCY_SECTIONS.contains(parent) && "typescript".equals(name)) {
                    return markValue(member, "Angular compiler tooling 20.3.26 requires TypeScript >=5.8 and <6.0 across compiler, editor, builder, and CI");
                }
                if (DEPENDENCY_SECTIONS.contains(parent) && "rxjs".equals(name)) {
                    return markValue(member, "Angular Common 20.3.26 supports RxJS ^6.5.3 or ^7.4.0; verify the selected RxJS major and AsyncPipe error behavior");
                }
                if ("engines".equals(parent) && "node".equals(name)) {
                    return markValue(member, "Angular 20.3.26 requires Node ^20.19.0, ^22.12.0, or >=24.0.0 in development, CI, images, and deployment");
                }
                if ("scripts".equals(parent) && declaration.contains("ngcc")) {
                    return markValue(member, "Angular 20 is Ivy-only and no longer supports ngcc; replace every View Engine dependency before deleting this command");
                }
                if ("@angular/common".equals(name) && hasCentralOwner()) {
                    return markValue(member, "Central package-manager version ownership detected; update the catalog/override/resolution and all workspace consumers atomically");
                }
                return member;
            }

            private Json.Member inspectWorkspace(Json.Member member) {
                String name = UpgradeSelectedAngularCommonDependency.key(member);
                if ("builder".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    literal.getValue() instanceof String declaration &&
                    !declaration.startsWith("@angular-devkit/build-angular:") &&
                    !declaration.startsWith("@angular/build:")) {
                    return markValue(member, "Custom builder detected; select an Angular 20-compatible release and validate its workspace option schema");
                }
                if ("server".equals(name) || "ssr".equals(name) || "prerender".equals(name)) {
                    return SearchResult.found(member, "SSR/prerender uses common HTTP transfer cache, locale, DOCUMENT, and hydration behavior; review the complete deployment contract");
                }
                return member;
            }

            private Json.Member inspectTsConfig(Json.Member member) {
                String name = UpgradeSelectedAngularCommonDependency.key(member);
                if ("enableIvy".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Boolean.FALSE.equals(literal.getValue())) {
                    return SearchResult.found(member, "enableIvy false is incompatible with Angular 20; migrate every View Engine dependency before removing this switch");
                }
                if ("strictTemplates".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Boolean.FALSE.equals(literal.getValue())) {
                    return SearchResult.found(member, "Enable strictTemplates deliberately and fix NgTemplateOutlet context, pipe, nullability, and control-flow diagnostics");
                }
                return member;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.withValue(SearchResult.found(member.getValue(), message));
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member
                        ? UpgradeSelectedAngularCommonDependency.key((Json.Member) parent.getValue()) : "";
            }

            private boolean hasCentralOwner() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof Json.Member ancestor) {
                        String key = UpgradeSelectedAngularCommonDependency.key(ancestor);
                        if ("overrides".equals(key) || "resolutions".equals(key) || "catalog".equals(key) ||
                            "catalogs".equals(key) || "pnpm".equals(key)) {
                            return true;
                        }
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }
        };
    }

    private static boolean containsKey(Json tree, String wanted) {
        if (tree instanceof Json.Member member) {
            return wanted.equals(UpgradeSelectedAngularCommonDependency.key(member)) || containsKey(member.getValue(), wanted);
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(value -> containsKey(value, wanted));
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(value -> containsKey(value, wanted));
        }
        return false;
    }
}
