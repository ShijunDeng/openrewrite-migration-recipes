package com.huawei.clouds.openrewrite.singlespaangular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Mark source contracts whose correct replacement depends on application architecture. */
public final class FindSingleSpaAngularSourceRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find single-spa-angular 9.2.0 source risks";
    }

    @Override
    public String getDescription() {
        return "Marks physical/internal entries, CommonJS/ESM boundaries, lifecycle bootstrap options, webpack integration, and shared-Angular production-mode choices using owned identifiers.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean active;
            private boolean ownsSingleSpaAngular;
            private boolean angularEnableProdMode;
            private Set<String> lifecycleAliases = Set.of();
            private Set<String> webpackAliases = Set.of();
            private Map<String, Integer> declarations = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean oldActive = active;
                boolean oldOwns = ownsSingleSpaAngular;
                boolean oldProd = angularEnableProdMode;
                Set<String> oldLifecycle = lifecycleAliases;
                Set<String> oldWebpack = webpackAliases;
                Map<String, Integer> oldDeclarations = declarations;
                active = SingleSpaAngularSupport.projectPath(cu.getSourcePath());
                ownsSingleSpaAngular = false;
                angularEnableProdMode = false;
                lifecycleAliases = new HashSet<>();
                webpackAliases = new HashSet<>();
                declarations = inventoryDeclarations(cu, ctx);
                if (active) scanOwnership(cu, ctx);
                JS.CompilationUnit visited = active ? super.visitJsCompilationUnit(cu, ctx) : cu;
                active = oldActive;
                ownsSingleSpaAngular = oldOwns;
                angularEnableProdMode = oldProd;
                lifecycleAliases = oldLifecycle;
                webpackAliases = oldWebpack;
                declarations = oldDeclarations;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (active && ownsSingleSpaAngular && angularEnableProdMode &&
                    "@angular/core".equals(SingleSpaAngularSupport.moduleName(visited)) &&
                    SingleSpaAngularSupport.importedAlias(visited, "enableProdMode") != null) {
                    return SingleSpaAngularSupport.mark(visited,
                            "When Angular is shared, prefer enableProdMode from single-spa-angular; verify production mode is enabled once for every bootstrap architecture");
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!active || !(visited.getValue() instanceof String module) ||
                    !SingleSpaAngularSupport.packageModule(module)) return visited;
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                boolean directRequire = invocation != null && module.equals(SingleSpaAngularSupport.requireModule(invocation));
                if (directRequire && !"single-spa-angular/lib/webpack".equals(module)) {
                    return SingleSpaAngularSupport.mark(visited,
                            "The target root/parcel/elements/internals entries are ESM; migrate this CommonJS require owner to ESM and verify the bundler instead of synthesizing interop");
                }
                if (SingleSpaAngularSupport.unknownPhysicalModule(module)) {
                    return SingleSpaAngularSupport.mark(visited,
                            "This imports an unpublished or unstable single-spa-angular physical path; select a documented root, parcel, elements, internals, or webpack public entry and verify its target export");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!active || visited.getSelect() != null) return visited;
                String name = visited.getSimpleName();
                if (lifecycleAliases.contains(name) && declarations.getOrDefault(name, 0) == 0) {
                    return SingleSpaAngularSupport.mark(visited,
                            "Review singleSpaAngular lifecycle options across Angular majors: bootstrapModule/bootstrapApplication, template and DOM ownership, Router/NavigationStart, NgZone/noop zone, extra providers, update, and emitted output");
                }
                if (webpackAliases.contains(name) && declarations.getOrDefault(name, 0) <= 1) {
                    if (visited.getArguments().size() < 2) {
                        return SingleSpaAngularSupport.mark(visited,
                                "singleSpaAngularWebpack requires both config and options; pass the builder options and then validate SystemJS output, externals, styles, and production serving");
                    }
                    return SingleSpaAngularSupport.mark(visited,
                            "Review this webpack adapter call for the final Angular/custom-webpack major, SystemJS library output, excludeAngularDependencies, externals, styles, and production artifact path");
                }
                return visited;
            }

            private void scanOwnership(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = SingleSpaAngularSupport.moduleName(visited);
                        if (SingleSpaAngularSupport.packageModule(module)) ownsSingleSpaAngular = true;
                        String lifecycle = SingleSpaAngularSupport.importedAlias(visited, "singleSpaAngular");
                        if (lifecycle != null && SingleSpaAngularSupport.PACKAGE.equals(module)) lifecycleAliases.add(lifecycle);
                        if ("single-spa-angular/lib/webpack".equals(module) && visited.getImportClause() != null &&
                            visited.getImportClause().getName() != null) {
                            webpackAliases.add(visited.getImportClause().getName().getSimpleName());
                        }
                        if ("@angular/core".equals(module) &&
                            SingleSpaAngularSupport.importedAlias(visited, "enableProdMode") != null) {
                            angularEnableProdMode = true;
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        Expression initializer = visited.getInitializer();
                        if (initializer instanceof J.FieldAccess field && "default".equals(field.getSimpleName()) &&
                            field.getTarget() instanceof J.MethodInvocation require &&
                            "single-spa-angular/lib/webpack".equals(SingleSpaAngularSupport.requireModule(require))) {
                            ownsSingleSpaAngular = true;
                            webpackAliases.add(visited.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private Map<String, Integer> inventoryDeclarations(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, Integer> result = new HashMap<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        result.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
                return result;
            }
        };
    }
}
