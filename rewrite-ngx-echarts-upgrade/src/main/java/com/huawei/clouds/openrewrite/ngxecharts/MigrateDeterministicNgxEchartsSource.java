package com.huawei.clouds.openrewrite.ngxecharts;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.Set;

/** Normalize only imports whose target public export is proven. */
public final class MigrateDeterministicNgxEchartsSource extends Recipe {
    private static final Set<String> PUBLIC_DEEP_PATHS = Set.of(
            "ngx-echarts/lib/ngx-echarts.directive", "ngx-echarts/lib/ngx-echarts.directive.js",
            "ngx-echarts/lib/ngx-echarts.module", "ngx-echarts/lib/ngx-echarts.module.js",
            "ngx-echarts/lib/config", "ngx-echarts/lib/config.js",
            "ngx-echarts/lib/provide", "ngx-echarts/lib/provide.js",
            "ngx-echarts/public-api"
    );
    private static final Set<String> PUBLIC_EXPORTS = Set.of(
            "NgxEchartsDirective", "NgxEchartsModule", "NgxEchartsConfig", "NGX_ECHARTS_CONFIG",
            "ThemeOption", "provideEchartsCore"
    );

    @Override
    public String getDisplayName() {
        return "Normalize deterministic ngx-echarts public imports";
    }

    @Override
    public String getDescription() {
        return "Move named imports from known historical public implementation paths to the package root only " +
               "when every imported symbol is exported by ngx-echarts 20.0.2.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!PUBLIC_DEEP_PATHS.contains(moduleName(visited)) || !importsOnlyPublicSymbols(visited) ||
                    !(visited.getModuleSpecifier() instanceof J.Literal literal)) {
                    return visited;
                }
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                        ? "\"" : "'";
                return visited.withModuleSpecifier(literal.withValue(UpgradeSelectedNgxEchartsDependency.PACKAGE)
                        .withValueSource(quote + UpgradeSelectedNgxEchartsDependency.PACKAGE + quote));
            }
        };
    }

    private static boolean importsOnlyPublicSymbols(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || clause.getName() != null ||
            !(clause.getNamedBindings() instanceof JS.NamedImports named) || named.getElements().isEmpty()) {
            return false;
        }
        return named.getElements().stream().allMatch(element -> PUBLIC_EXPORTS.contains(importedName(element)));
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String importedName(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (specifier.getSpecifier() instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
    }

    static String importedAlias(JS.Import declaration, String wanted) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return null;
        for (JS.ImportSpecifier element : named.getElements()) {
            if (!wanted.equals(importedName(element))) continue;
            if (element.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
            if (element.getSpecifier() instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier id) {
                return id.getSimpleName();
            }
        }
        return null;
    }
}
