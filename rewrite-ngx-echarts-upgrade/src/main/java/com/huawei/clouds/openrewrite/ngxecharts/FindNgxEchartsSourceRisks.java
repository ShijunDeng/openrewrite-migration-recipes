package com.huawei.clouds.openrewrite.ngxecharts;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Locate source constructs whose ngx-echarts 20 behavior needs an application decision. */
public final class FindNgxEchartsSourceRisks extends Recipe {
    private static final Set<String> REMOVED_EXPORTS = Set.of(
            "provideEcharts", "NgxEchartsCoreModule", "NgxEchartsService"
    );

    @Override
    public String getDisplayName() {
        return "Find ngx-echarts 20 source compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed providers/classes, unsupported ECharts full/deep imports, custom-core registration, " +
               "forRoot ownership and extension boundaries on binding-aware imports and calls.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> provideAliases = Set.of();
            private Set<String> moduleAliases = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> oldProviders = provideAliases;
                Set<String> oldModules = moduleAliases;
                provideAliases = new HashSet<>();
                moduleAliases = new HashSet<>();
                scanImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                provideAliases = oldProviders;
                moduleAliases = oldModules;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = MigrateDeterministicNgxEchartsSource.moduleName(visited);
                if (UpgradeSelectedNgxEchartsDependency.PACKAGE.equals(module)) {
                    Set<String> removed = importedNames(visited, REMOVED_EXPORTS);
                    if (!removed.isEmpty()) {
                        return SearchResult.found(visited, removed.contains("provideEcharts")
                                ? "provideEcharts was removed in ngx-echarts 19; choose and register an ECharts core build, then use provideEchartsCore({ echarts })"
                                : "NgxEchartsCoreModule/NgxEchartsService is absent from the target API; use NgxEchartsModule/NgxEchartsDirective and direct ECharts instance APIs");
                    }
                    return visited;
                }
                if (module.startsWith("ngx-echarts/")) {
                    return SearchResult.found(visited, "Unsupported ngx-echarts deep entry point remains; move only documented public exports to the package root and replace internal symbols deliberately");
                }
                if ("echarts".equals(module) || "echarts/index.js".equals(module) ||
                    "echarts/index".equals(module)) {
                    return SearchResult.found(visited, "Angular 19+ cannot consume the ECharts index.js full entry in this integration; build from echarts/core plus charts/components/features/renderers and register them with echarts.use");
                }
                if ("echarts/core".equals(module)) {
                    return SearchResult.found(visited, "Custom ECharts core must register every used chart/component/feature and at least CanvasRenderer or SVGRenderer before ngx-echarts initializes");
                }
                if (module.startsWith("echarts/src/") || "echarts/lib/echarts".equals(module)) {
                    return SearchResult.found(visited, "Legacy ECharts implementation entry point bypasses supported exports; migrate to echarts/core and explicit public subpaths with complete registration");
                }
                if (module.startsWith("echarts-gl")) {
                    return SearchResult.found(visited, "ECharts GL extension registration and bundler/SSR compatibility depend on the selected ECharts core build; verify production chunks and every 3D series/component");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null && provideAliases.contains(visited.getSimpleName())) {
                    return SearchResult.found(visited, "Replace removed provideEcharts only after constructing and registering the required ECharts core; then call provideEchartsCore({ echarts })");
                }
                if ("forRoot".equals(visited.getSimpleName()) &&
                    visited.getSelect() instanceof J.Identifier identifier &&
                    moduleAliases.contains(identifier.getSimpleName())) {
                    return SearchResult.found(visited, "NgxEchartsModule.forRoot remains supported, but its echarts value must be a compatible registered core/full loader; verify renderer, theme, locale, lazy loading and SSR ownership");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String name = expressionName(visited.getFunction());
                if (provideAliases.contains(name)) {
                    return SearchResult.found(visited, "Replace removed provideEcharts only after constructing and registering the required ECharts core; then call provideEchartsCore({ echarts })");
                }
                return visited;
            }

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (UpgradeSelectedNgxEchartsDependency.PACKAGE.equals(
                                MigrateDeterministicNgxEchartsSource.moduleName(visited))) {
                            addAlias(visited, "provideEcharts", provideAliases);
                            addAlias(visited, "NgxEchartsModule", moduleAliases);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }

    private static Set<String> importedNames(JS.Import declaration, Set<String> candidates) {
        Set<String> found = new HashSet<>();
        JS.ImportClause clause = declaration.getImportClause();
        if (clause != null && clause.getNamedBindings() instanceof JS.NamedImports named) {
            for (JS.ImportSpecifier element : named.getElements()) {
                String imported = MigrateDeterministicNgxEchartsSource.importedName(element);
                if (candidates.contains(imported)) found.add(imported);
            }
        }
        return found;
    }

    private static void addAlias(JS.Import declaration, String imported, Set<String> aliases) {
        String alias = MigrateDeterministicNgxEchartsSource.importedAlias(declaration, imported);
        if (alias != null) aliases.add(alias);
    }

    private static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess access) return access.getSimpleName();
        return "";
    }
}
