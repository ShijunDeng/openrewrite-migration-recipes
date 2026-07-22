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

/** Places review markers on package and Angular configuration values that require coordinated choices. */
public final class FindAngular20JsonRisks extends Recipe {
    private static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> ANGULAR_PACKAGE_GROUP = Set.of(
            "@angular/animations", "@angular/bazel", "@angular/common", "@angular/compiler",
            "@angular/compiler-cli", "@angular/elements", "@angular/forms", "@angular/language-service",
            "@angular/localize", "@angular/platform-browser", "@angular/platform-browser-dynamic",
            "@angular/platform-server", "@angular/router", "@angular/service-worker", "@angular/upgrade"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular 20 package and configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks exact JSON members that need lockstep Angular packages, supported tooling, lockfile " +
               "regeneration, or explicit build/SSR decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(file)) {
                    Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                    if (!containsAngularCore(document.getValue())) {
                        return visited;
                    }
                    return inspectPackageJson(visited);
                }
                if ("angular.json".equals(file) || "workspace.json".equals(file)) {
                    return inspectAngularWorkspace(visited);
                }
                if (file.startsWith("tsconfig") && file.endsWith(".json")) {
                    Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                    if (!containsKey(document.getValue(), "angularCompilerOptions")) {
                        return visited;
                    }
                    return inspectTsConfig(visited);
                }
                return visited;
            }

            private Json.Member inspectPackageJson(Json.Member member) {
                String name = UpgradeSelectedAngularCoreDependency.key(member);
                if (!(member.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration)) {
                    return member;
                }
                String parent = parentMemberKey();

                if (DEPENDENCY_SECTIONS.contains(parent) && "@angular/core".equals(name) &&
                    !UpgradeSelectedAngularCoreDependency.TARGET_VERSION.equals(declaration) &&
                    !UpgradeSelectedAngularCoreDependency.SOURCE_VERSIONS.contains(declaration)) {
                    return markValue(member, "@angular/core declaration is a range, protocol, variable, unlisted, or already-newer version; select the intended constraint and regenerate this package manager's lockfile");
                }
                if (DEPENDENCY_SECTIONS.contains(parent) && ANGULAR_PACKAGE_GROUP.contains(name) &&
                    !UpgradeSelectedAngularCoreDependency.TARGET_VERSION.equals(declaration)) {
                    return markValue(member, "Angular's ng-update package group must be upgraded in lockstep with @angular/core 20.3.26");
                }
                if (DEPENDENCY_SECTIONS.contains(parent) && "typescript".equals(name)) {
                    return markValue(member, "Angular compiler-cli 20.3.26 requires TypeScript >=5.8 and <6.0; align compiler, editor, builders, and CI");
                }
                if (DEPENDENCY_SECTIONS.contains(parent) && "zone.js".equals(name)) {
                    return markValue(member, "Angular core 20.3.26 supports optional zone.js ~0.15.0; choose zoned or zoneless operation and retest async/change-detection behavior");
                }
                if (DEPENDENCY_SECTIONS.contains(parent) && "rxjs".equals(name)) {
                    return markValue(member, "Angular core 20.3.26 supports RxJS ^6.5.3 or ^7.4.0; verify the chosen RxJS major and scheduler/error semantics");
                }
                if ("engines".equals(parent) && "node".equals(name)) {
                    return markValue(member, "Angular 20.3.26 requires Node ^20.19.0, ^22.12.0, or >=24.0.0 across local, CI, image, and deployment toolchains");
                }
                if ("scripts".equals(parent) && declaration.contains("ngcc")) {
                    return markValue(member, "Angular 20 no longer supports View Engine/ngcc; remove this script only after replacing or upgrading every View Engine dependency");
                }
                if ("@angular/core".equals(name) && isCentralVersionOwner()) {
                    return markValue(member, "Central package-manager ownership detected; update the workspace catalog/override and every consumer atomically instead of rewriting one package.json declaration");
                }
                return member;
            }

            private Json.Member inspectAngularWorkspace(Json.Member member) {
                String name = UpgradeSelectedAngularCoreDependency.key(member);
                if ("builder".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    literal.getValue() instanceof String declaration &&
                    !declaration.startsWith("@angular-devkit/build-angular:") &&
                    !declaration.startsWith("@angular/build:")) {
                    return markValue(member, "Custom Angular builder detected; verify an Angular 20-compatible builder release and its option schema before changing workspace configuration");
                }
                if ("defaultProject".equals(name)) {
                    return markValue(member, "Legacy defaultProject workspace behavior changed across Angular CLI majors; choose explicit project arguments in scripts and CI");
                }
                if ("server".equals(name) || "ssr".equals(name) || "prerender".equals(name)) {
                    return SearchResult.found(member, "SSR/prerender configuration must be migrated with platform-server, bootstrap context, hydration, and deployment adapter decisions");
                }
                return member;
            }

            private Json.Member inspectTsConfig(Json.Member member) {
                String name = UpgradeSelectedAngularCoreDependency.key(member);
                if ("enableIvy".equals(name) || "compilationMode".equals(name)) {
                    return SearchResult.found(member, "Angular 20 is Ivy-only; remove legacy compiler switches after verifying partial compilation for publishable libraries");
                }
                if ("moduleResolution".equals(name) || "target".equals(name) || "module".equals(name)) {
                    return SearchResult.found(member, "Angular 20/TypeScript 5.8 may change emitted modules and resolution; align this option with the selected builder and Node/browser targets");
                }
                return member;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.withValue(SearchResult.found(member.getValue(), message));
            }

            private String parentMemberKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member
                        ? UpgradeSelectedAngularCoreDependency.key((Json.Member) parent.getValue()) : "";
            }

            private boolean isCentralVersionOwner() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof Json.Member ancestor) {
                        String key = UpgradeSelectedAngularCoreDependency.key(ancestor);
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

    private static boolean containsAngularCore(Json tree) {
        if (tree instanceof Json.Member member) {
            return "@angular/core".equals(UpgradeSelectedAngularCoreDependency.key(member)) ||
                   containsAngularCore(member.getValue());
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindAngular20JsonRisks::containsAngularCore);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindAngular20JsonRisks::containsAngularCore);
        }
        return false;
    }

    private static boolean containsKey(Json tree, String wanted) {
        if (tree instanceof Json.Member member) {
            return wanted.equals(UpgradeSelectedAngularCoreDependency.key(member)) ||
                   containsKey(member.getValue(), wanted);
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
