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

/** Mark package and workspace choices that constrain removal of the legacy animation engine. */
public final class FindAngularAnimationsJsonRisks extends Recipe {
    private static final Set<String> ANGULAR_GROUP = Set.of(
            "@angular/common", "@angular/compiler", "@angular/compiler-cli", "@angular/core",
            "@angular/elements", "@angular/forms", "@angular/language-service", "@angular/localize",
            "@angular/platform-browser", "@angular/platform-browser-dynamic", "@angular/platform-server",
            "@angular/router", "@angular/service-worker", "@angular/upgrade"
    );
    private static final Set<String> TOOLING = Set.of(
            "@angular/cli", "@angular-devkit/build-angular", "@angular-devkit/core",
            "@angular-devkit/schematics", "@schematics/angular"
    );
    private static final Set<String> THIRD_PARTY = Set.of(
            "@angular/material", "@angular/cdk", "primeng", "ngx-bootstrap", "ngx-toastr",
            "@ng-bootstrap/ng-bootstrap", "ng-zorro-antd", "@swimlane/ngx-charts"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular 20 animations project risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved animation declarations, package-group/toolchain alignment, third-party consumers, " +
               "legacy polyfills and browser/server builder choices at exact JSON members.";
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
                    return containsDirectAnimations(document.getValue()) ? inspectPackage(visited) : visited;
                }
                if ("angular.json".equals(file) || "workspace.json".equals(file)) {
                    return inspectWorkspace(visited);
                }
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                String name = UpgradeSelectedAngularAnimationsDependency.key(member);
                String parent = parentKey();
                boolean dependency = UpgradeSelectedAngularAnimationsDependency.SECTIONS.contains(parent);
                if (dependency && "@angular/animations".equals(name)) {
                    if (shouldMarkUnresolved(member)) {
                        return mark(member, unresolvedMessage(member));
                    }
                    return mark(member,
                            "@angular/animations is deprecated since Angular 20.2 and intended for removal in v23; keep 20.3.26 only as a transition while legacy/third-party consumers remain");
                }
                if (dependency && ANGULAR_GROUP.contains(name) && !isTarget(member)) {
                    return mark(member, "Angular's ng-update package group must be upgraded in lockstep to 20.3.26");
                }
                if (dependency && TOOLING.contains(name) && !atLeastMajor(member, 20)) {
                    return mark(member,
                            "Align Angular CLI/build/schematics tooling with Angular 20.3 and run official migrations in major-version order before animation removal");
                }
                if (dependency && THIRD_PARTY.contains(name)) {
                    return mark(member,
                            "Third-party UI package may still require the legacy Angular animation renderer; verify its peer dependencies and native-animation release before removing providers/package");
                }
                if (dependency && "typescript".equals(name) && !supportedTypeScript(member)) {
                    return mark(member,
                            "Angular 20.3 compiler tooling requires TypeScript >=5.8 and <6.0; align CSS/template compilation, editor and CI");
                }
                if (dependency && "zone.js".equals(name)) {
                    return mark(member,
                            "Animation completion and change detection timing differs in zoned/zoneless applications; align zone.js strategy and retest callbacks");
                }
                if (dependency && "web-animations-js".equals(name)) {
                    return mark(member,
                            "Legacy Web Animations polyfill needs an explicit browser-support decision; native Angular enter/leave should use supported CSS/Web Animations without obsolete global patches");
                }
                if ("engines".equals(parent) && "node".equals(name) && !supportedNode(member)) {
                    return mark(member,
                            "Angular 20.3 requires Node ^20.19.0, ^22.12.0, or >=24 across local, CI, image and build runtimes");
                }
                return member;
            }

            private Json.Member inspectWorkspace(Json.Member member) {
                String name = UpgradeSelectedAngularAnimationsDependency.key(member);
                if ("polyfills".equals(name) && containsString(member.getValue(), "web-animations-js")) {
                    return mark(member,
                            "Workspace still loads web-animations-js; verify supported browsers and remove the obsolete global animation polyfill deliberately");
                }
                if ("builder".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    literal.getValue() instanceof String builder &&
                    !builder.startsWith("@angular-devkit/build-angular:") && !builder.startsWith("@angular/build:")) {
                    return mark(member,
                            "Custom builder controls CSS processing; verify Angular 20 templates, @starting-style, keyframes, source maps and SSR style output");
                }
                if ("server".equals(name) || "ssr".equals(name) || "prerender".equals(name)) {
                    return mark(member,
                            "SSR/prerender does not run browser animations; preserve usable initial DOM/styles and verify hydration before client animation enhancement");
                }
                return member;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member enclosing
                        ? UpgradeSelectedAngularAnimationsDependency.key(enclosing) : "";
            }
        };
    }

    private static boolean shouldMarkUnresolved(Json.Member member) {
        return !(member.getValue() instanceof Json.Literal literal) ||
               !(literal.getValue() instanceof String declaration) ||
               !UpgradeSelectedAngularAnimationsDependency.TARGET.equals(declaration);
    }

    private static String unresolvedMessage(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String declaration)) {
            return "Non-string @angular/animations declaration was not changed; resolve it to a scalar before selecting the 20.3.26 transition release";
        }
        if (declaration.contains(":") || declaration.contains("${") ||
            declaration.matches("(?:latest|next|catalog.*)")) {
            return "Protocol, alias, tag or dynamic @angular/animations declaration was not changed; resolve its owner and lockstep Angular target";
        }
        if (declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+")) {
            return "Unlisted or non-target @angular/animations scalar was not changed; run supported major migrations before selecting the 20.3.26 transition release";
        }
        return "Complex @angular/animations range was not changed; select it only after proving the supported Angular and third-party package matrix";
    }

    private static boolean isTarget(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal &&
               UpgradeSelectedAngularAnimationsDependency.TARGET.equals(literal.getValue());
    }

    private static Json.Member mark(Json.Member member, String message) {
        return SearchResult.found(member, message);
    }

    private static boolean containsDirectAnimations(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        return root.getMembers().stream().anyMatch(value -> value instanceof Json.Member section &&
                UpgradeSelectedAngularAnimationsDependency.SECTIONS.contains(
                        UpgradeSelectedAngularAnimationsDependency.key(section)) &&
                section.getValue() instanceof Json.JsonObject dependencies &&
                dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                        "@angular/animations".equals(UpgradeSelectedAngularAnimationsDependency.key(dependency))));
    }

    private static boolean containsString(Json tree, String wanted) {
        if (tree instanceof Json.Literal literal) {
            return wanted.equals(literal.getValue());
        }
        if (tree instanceof Json.Member member) {
            return containsString(member.getValue(), wanted);
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(value -> containsString(value, wanted));
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(value -> containsString(value, wanted));
        }
        return false;
    }

    private static boolean supportedTypeScript(Json.Member member) {
        int[] version = scalar(member);
        return version != null && version[0] == 5 && version[1] >= 8;
    }

    private static boolean supportedNode(Json.Member member) {
        if (member.getValue() instanceof Json.Literal literal &&
            "^20.19.0 || ^22.12.0 || >=24.0.0".equals(literal.getValue())) return true;
        int[] version = scalar(member);
        return version != null && ((version[0] == 20 && version[1] >= 19) ||
                (version[0] == 22 && version[1] >= 12) || version[0] >= 24);
    }

    private static boolean atLeastMajor(Json.Member member, int major) {
        int[] version = scalar(member);
        return version != null && version[0] >= major;
    }

    private static int[] scalar(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) ||
            !(literal.getValue() instanceof String declaration)) return null;
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
