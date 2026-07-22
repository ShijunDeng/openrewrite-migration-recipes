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

/** Mark Angular package, builder, SSR and compiler configuration requiring lockstep migration. */
public final class FindAngularPlatformBrowserJsonRisks extends Recipe {
    private static final Set<String> ANGULAR_GROUP = Set.of(
            "@angular/animations", "@angular/common", "@angular/compiler", "@angular/compiler-cli",
            "@angular/core", "@angular/elements", "@angular/forms", "@angular/language-service",
            "@angular/localize", "@angular/platform-browser-dynamic", "@angular/platform-server",
            "@angular/router", "@angular/service-worker", "@angular/upgrade"
    );
    private static final Set<String> TOOLING = Set.of(
            "@angular/cli", "@angular-devkit/build-angular", "@angular-devkit/core",
            "@angular-devkit/schematics", "@schematics/angular"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular 20 platform-browser project risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved platform-browser declarations, lockstep Angular/tooling packages, runtime " +
               "requirements, builders, SSR targets and compiler options at exact JSON members.";
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
                    return containsManagedPackage(document.getValue()) ? inspectPackage(visited) : visited;
                }
                if ("angular.json".equals(file) || "workspace.json".equals(file)) {
                    return inspectWorkspace(visited);
                }
                if (file.startsWith("tsconfig") && file.endsWith(".json")) {
                    Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                    return containsKey(document.getValue(), "angularCompilerOptions") ? inspectTsConfig(visited) : visited;
                }
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                String name = UpgradeSelectedAngularPlatformBrowserDependency.key(member);
                String parent = parentKey();
                boolean dependency = UpgradeSelectedAngularPlatformBrowserDependency.SECTIONS.contains(parent);
                if (dependency && "@angular/platform-browser".equals(name) && shouldMarkUnresolved(member)) {
                    return mark(member, unresolvedMessage(member));
                }
                if (dependency && "@angular/platform-browser-dynamic".equals(name)) {
                    return mark(member, "platform-browser-dynamic is deprecated in Angular 20; use platformBrowser for AOT or explicitly retain @angular/compiler for proven JIT");
                }
                if (dependency && ANGULAR_GROUP.contains(name) && !isTarget(member)) {
                    return mark(member, "Angular's ng-update package group must be upgraded in lockstep to 20.3.26");
                }
                if (dependency && TOOLING.contains(name) && !supportedAngularTooling(member)) {
                    return mark(member, "Align Angular CLI/build/schematics tooling with Angular 20.3 and run every official migration in major-version order");
                }
                if (dependency && "typescript".equals(name) && !supportedTypeScript(member)) {
                    return mark(member, "Angular 20.3 compiler tooling requires TypeScript >=5.8 and <6.0; align editor, builder and CI");
                }
                if (dependency && "zone.js".equals(name) && !supportedZone(member)) {
                    return mark(member, "Choose zoned or zoneless operation deliberately; align zone.js ~0.15 when retained and retest async/change detection");
                }
                if (dependency && "rxjs".equals(name) && !supportedRxJs(member)) {
                    return mark(member, "Verify the Angular 20-compatible RxJS line and scheduler/error semantics across application and libraries");
                }
                if (dependency && "hammerjs".equals(name)) {
                    return mark(member, "Angular 20 deprecates built-in HammerJS integration; replace gesture ownership before removing this dependency");
                }
                if ("engines".equals(parent) && "node".equals(name) && !supportedNode(member)) {
                    return mark(member, "Angular 20.3 requires Node ^20.19.0, ^22.12.0, or >=24 across local, CI, image and deployment runtimes");
                }
                if ("scripts".equals(parent) && member.getValue() instanceof Json.Literal literal &&
                    literal.getValue() instanceof String command && command.contains("ngcc")) {
                    return mark(member, "Angular 20 is Ivy-only and has no ngcc; remove this command after replacing all View Engine dependencies");
                }
                return member;
            }

            private Json.Member inspectWorkspace(Json.Member member) {
                String name = UpgradeSelectedAngularPlatformBrowserDependency.key(member);
                if ("builder".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    literal.getValue() instanceof String builder &&
                    !builder.startsWith("@angular-devkit/build-angular:") && !builder.startsWith("@angular/build:")) {
                    return mark(member, "Custom builder detected; verify an Angular 20-compatible release and option schema before migrating browser/SSR targets");
                }
                if ("browserTarget".equals(name)) {
                    return mark(member, "Legacy browserTarget option changed across CLI builders; migrate it with the owning serve/extract-i18n/prerender builder schema");
                }
                if ("server".equals(name) || "ssr".equals(name) || "prerender".equals(name)) {
                    return mark(member, "SSR/prerender target must be migrated with platform-server, per-request BootstrapContext, hydration and deployment adapter choices");
                }
                if ("defaultProject".equals(name)) {
                    return mark(member, "Legacy defaultProject behavior changed; pass explicit project names in scripts and CI before removing this setting");
                }
                return member;
            }

            private Json.Member inspectTsConfig(Json.Member member) {
                String name = UpgradeSelectedAngularPlatformBrowserDependency.key(member);
                if ("enableIvy".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Boolean.FALSE.equals(literal.getValue())) {
                    return mark(member, "Angular 20 is Ivy-only; enableIvy=false requires upgrading or replacing every View Engine dependency before removal");
                }
                if ("fullTemplateTypeCheck".equals(name)) {
                    return mark(member, "Legacy fullTemplateTypeCheck was superseded by strictTemplates; choose the strictness migration and fix resulting template diagnostics");
                }
                if ("strictTemplates".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Boolean.FALSE.equals(literal.getValue())) {
                    return mark(member, "Strict template checking is disabled; enable it deliberately and resolve DOM/event/input type errors before Angular 20 rollout");
                }
                if ("compilationMode".equals(name)) {
                    return mark(member, "Verify full versus partial Ivy compilation based on whether this tsconfig builds an application or publishable library");
                }
                if ("moduleResolution".equals(name) || "target".equals(name) || "module".equals(name)) {
                    return mark(member, "Angular 20/TypeScript 5.8 module emit or resolution must align with the selected browser/server builder and runtime");
                }
                return member;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member member
                        ? UpgradeSelectedAngularPlatformBrowserDependency.key(member) : "";
            }
        };
    }

    private static boolean shouldMarkUnresolved(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String declaration)) {
            return true;
        }
        return !UpgradeSelectedAngularPlatformBrowserDependency.TARGET.equals(declaration);
    }

    private static String unresolvedMessage(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String declaration)) {
            return "Non-string @angular/platform-browser declaration was not changed; resolve it to a scalar before selecting 20.3.26";
        }
        if (declaration.contains(":") || declaration.contains("${") || declaration.matches("(?:latest|next|catalog.*)")) {
            return "Protocol, alias, tag or dynamic @angular/platform-browser declaration was not changed; resolve its owner and lockstep Angular target";
        }
        if (declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+")) {
            return "Unlisted @angular/platform-browser scalar version was not changed; confirm it is in migration scope before selecting 20.3.26";
        }
        return "Complex @angular/platform-browser range was not changed; select it only after proving the supported Angular package matrix";
    }

    private static boolean isTarget(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal &&
               UpgradeSelectedAngularPlatformBrowserDependency.TARGET.equals(literal.getValue());
    }

    private static Json.Member mark(Json.Member member, String message) {
        return SearchResult.found(member, message);
    }

    private static boolean containsManagedPackage(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) {
            return false;
        }
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member section) ||
                !UpgradeSelectedAngularPlatformBrowserDependency.SECTIONS.contains(
                        UpgradeSelectedAngularPlatformBrowserDependency.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject dependencies)) {
                continue;
            }
            if (dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                    "@angular/platform-browser".equals(UpgradeSelectedAngularPlatformBrowserDependency.key(dependency)))) {
                return true;
            }
        }
        return false;
    }

    private static String declaration(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : null;
    }

    private static boolean supportedAngularTooling(Json.Member member) {
        String declaration = declaration(member);
        return declaration != null && declaration.matches("[~^]?20\\.3\\.\\d+");
    }

    private static boolean supportedTypeScript(Json.Member member) {
        int[] version = scalar(member);
        return version != null && version[0] == 5 && version[1] >= 8;
    }

    private static boolean supportedZone(Json.Member member) {
        int[] version = scalar(member);
        return version != null && version[0] == 0 && version[1] == 15;
    }

    private static boolean supportedRxJs(Json.Member member) {
        String declaration = declaration(member);
        if ("^6.5.3 || ^7.4.0".equals(declaration)) {
            return true;
        }
        int[] version = scalar(member);
        return version != null && ((version[0] == 6 && (version[1] > 5 || version[1] == 5 && version[2] >= 3)) ||
                                   (version[0] == 7 && version[1] >= 4));
    }

    private static boolean supportedNode(Json.Member member) {
        String declaration = declaration(member);
        if ("^20.19.0 || ^22.12.0 || >=24.0.0".equals(declaration)) {
            return true;
        }
        int[] version = scalar(member);
        return version != null && ((version[0] == 20 && version[1] >= 19) ||
                                   (version[0] == 22 && version[1] >= 12) || version[0] >= 24);
    }

    private static int[] scalar(Json.Member member) {
        String declaration = declaration(member);
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

    private static boolean containsKey(Json tree, String wanted) {
        if (tree instanceof Json.Member member) {
            return wanted.equals(UpgradeSelectedAngularPlatformBrowserDependency.key(member)) ||
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
