package com.huawei.clouds.openrewrite.echarts;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Apply only source changes with a public, one-to-one ECharts 6 equivalent. */
public final class RewriteDeterministicEChartsSource extends Recipe {
    private static final Set<String> FULL_BUILD = Set.of(
            "echarts/lib/echarts", "echarts/lib/echarts.js",
            "echarts/src/echarts", "echarts/src/echarts.ts"
    );
    private static final Set<String> LIGHT_THEME = Set.of(
            "echarts/src/theme/light", "echarts/src/theme/light.ts"
    );

    @Override
    public String getDisplayName() {
        return "Migrate deterministic ECharts 6 source constructs";
    }

    @Override
    public String getDescription() {
        return "Normalize exact static full-build and v5 light-theme imports to public ECharts 6 exports, and " +
               "rename EChartOption only on an unshadowed namespace/default binding imported from ECharts.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> namespaces = Set.of();
            private Map<String, Integer> declarations = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!EChartsSupport.isProjectPath(cu.getSourcePath())) return cu;
                Set<String> oldNamespaces = namespaces;
                Map<String, Integer> oldDeclarations = declarations;
                namespaces = new HashSet<>();
                declarations = EChartsSupport.declarationCounts(cu, ctx);
                collectBindings(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                namespaces = oldNamespaces;
                declarations = oldDeclarations;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = EChartsSupport.moduleName(visited);
                String target = FULL_BUILD.contains(module) ? EChartsSupport.PACKAGE :
                        LIGHT_THEME.contains(module) ? "echarts/theme/rainbow.js" : null;
                if (target == null || !(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                        ? "\"" : "'";
                return visited.withModuleSpecifier(literal.withValue(target).withValueSource(quote + target + quote));
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if (!"EChartOption".equals(visited.getSimpleName()) ||
                    !(visited.getTarget() instanceof J.Identifier identifier) ||
                    !namespaces.contains(identifier.getSimpleName()) ||
                    declarations.getOrDefault(identifier.getSimpleName(), 0) != 0) return visited;
                return visited.withName(visited.getName().withSimpleName("EChartsOption"));
            }

            private void collectBindings(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (!EChartsSupport.isFullApiModule(EChartsSupport.moduleName(visited))) return visited;
                        String namespace = EChartsSupport.namespaceBinding(visited);
                        if (namespace != null) namespaces.add(namespace);
                        String defaultName = EChartsSupport.defaultBinding(visited);
                        if (defaultName != null) namespaces.add(defaultName);
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
