package com.huawei.clouds.openrewrite.ngxecharts;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Mark package and workspace choices that constrain an ngx-echarts 20 upgrade. */
public final class FindNgxEchartsJsonRisks extends Recipe {
    private static final Set<String> ANGULAR_GROUP = Set.of(
            "@angular/animations", "@angular/common", "@angular/compiler", "@angular/compiler-cli",
            "@angular/core", "@angular/forms", "@angular/platform-browser",
            "@angular/platform-browser-dynamic", "@angular/platform-server", "@angular/router"
    );
    private static final Set<String> TOOLING = Set.of(
            "@angular/cli", "@angular-devkit/build-angular", "@angular/build",
            "@angular-devkit/schematics", "@schematics/angular"
    );

    @Override
    public String getDisplayName() {
        return "Find ngx-echarts 20 project compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved declarations, Angular/toolchain/Node peers, unsupported ECharts selections, " +
               "legacy ResizeObserver polyfills, SSR packages and workspace global scripts at exact JSON members.";
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
                    return containsDirectNgxEcharts(document.getValue()) ? inspectPackage(visited) : visited;
                }
                if (("angular.json".equals(file) || "workspace.json".equals(file)) &&
                    "scripts".equals(UpgradeSelectedNgxEchartsDependency.key(visited)) &&
                    containsString(visited.getValue(), "echarts")) {
                    return mark(visited, "Global ECharts scripts bypass the custom core provider and bundler exports; remove or reconcile them with the registered renderer/components before production and SSR builds");
                }
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                String name = UpgradeSelectedNgxEchartsDependency.key(member);
                String parent = parentKey();
                boolean dependency = UpgradeSelectedNgxEchartsDependency.SECTIONS.contains(parent);
                if (dependency && UpgradeSelectedNgxEchartsDependency.PACKAGE.equals(name)) {
                    return UpgradeSelectedNgxEchartsDependency.TARGET.equals(stringValue(member))
                            ? member : mark(member, unresolvedMessage(member));
                }
                if (dependency && ANGULAR_GROUP.contains(name) && !atLeastMajor(member, 20)) {
                    return mark(member, "ngx-echarts 20 requires Angular core >=20; align the complete Angular package group and run official migrations in major-version order");
                }
                if (dependency && TOOLING.contains(name) && !atLeastMajor(member, 20)) {
                    return mark(member, "Align Angular CLI/build/schematics with Angular 20 before compiling ngx-echarts' modern signal/output implementation");
                }
                if (dependency && "typescript".equals(name) && !supportedTypeScript(member)) {
                    return mark(member, "Angular 20.2 tooling requires TypeScript >=5.8 and <6.0; align editor, test and production compilation");
                }
                if (dependency && "echarts".equals(name) && !atLeastMajor(member, 5)) {
                    return mark(member, "ngx-echarts 20 requires ECharts >=5; select an owned ECharts 5/6 release and validate its public exports, option types and renderer registration");
                }
                if (dependency && "echarts-gl".equals(name)) {
                    return mark(member, "ECharts GL must match the selected ECharts custom build and bundler exports; verify 3D registration, production tree shaking and SSR exclusion");
                }
                if (dependency && Set.of("@juggle/resize-observer", "resize-observer-polyfill").contains(name)) {
                    return mark(member, "ResizeObserver is no longer an ngx-echarts peer; keep a polyfill only for an explicit browser/JSDOM matrix and install it before directive initialization");
                }
                if (dependency && Set.of("@angular/ssr", "@nguniversal/express-engine").contains(name)) {
                    return mark(member, "ngx-echarts touches window, ResizeObserver and rendering APIs; defer chart creation to the browser and verify hydration-safe host markup");
                }
                if ("engines".equals(parent) && "node".equals(name) && !supportedNode(member)) {
                    return mark(member, "Angular 20 requires Node 20.19+, 22.12+ or 24+ across local, CI, containers and SSR runtimes");
                }
                return member;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member enclosing
                        ? UpgradeSelectedNgxEchartsDependency.key(enclosing) : "";
            }
        };
    }

    private static String unresolvedMessage(Json.Member member) {
        String declaration = stringValue(member);
        if (declaration == null) return "Non-string ngx-echarts declaration was not changed; resolve it to an owned scalar target";
        if (declaration.contains(":") || declaration.contains("${") ||
            declaration.matches("(?:latest|next|catalog.*)")) {
            return "Protocol, alias, tag or dynamic ngx-echarts declaration was not changed; update its owning catalog/workspace deliberately";
        }
        if (declaration.matches("[~^]?\\d+\\.\\d+\\.\\d+")) {
            return "Unlisted or non-target ngx-echarts scalar was not changed; follow supported Angular-major migrations before selecting 20.0.2";
        }
        return "Complex ngx-echarts range was not changed; resolve the Angular/ECharts compatibility matrix manually";
    }

    private static boolean containsDirectNgxEcharts(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        return root.getMembers().stream().anyMatch(value -> value instanceof Json.Member section &&
                UpgradeSelectedNgxEchartsDependency.SECTIONS.contains(UpgradeSelectedNgxEchartsDependency.key(section)) &&
                section.getValue() instanceof Json.JsonObject dependencies &&
                dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                        UpgradeSelectedNgxEchartsDependency.PACKAGE.equals(
                                UpgradeSelectedNgxEchartsDependency.key(dependency))));
    }

    private static boolean containsString(Json tree, String wanted) {
        if (tree instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            return value.contains(wanted);
        }
        if (tree instanceof Json.Member member) return containsString(member.getValue(), wanted);
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(value -> containsString(value, wanted));
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(value -> containsString(value, wanted));
        }
        return false;
    }

    private static String stringValue(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }

    private static boolean atLeastMajor(Json.Member member, int minimum) {
        int[] version = scalar(member);
        return version != null && version[0] >= minimum;
    }

    private static boolean supportedTypeScript(Json.Member member) {
        int[] version = scalar(member);
        return version != null && version[0] == 5 && version[1] >= 8;
    }

    private static boolean supportedNode(Json.Member member) {
        int[] version = scalar(member);
        return version != null && ((version[0] == 20 && version[1] >= 19) ||
                (version[0] == 22 && version[1] >= 12) || version[0] >= 24);
    }

    private static int[] scalar(Json.Member member) {
        String declaration = stringValue(member);
        if (declaration == null) return null;
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static Json.Member mark(Json.Member member, String message) {
        return SearchResult.found(member, message);
    }
}
