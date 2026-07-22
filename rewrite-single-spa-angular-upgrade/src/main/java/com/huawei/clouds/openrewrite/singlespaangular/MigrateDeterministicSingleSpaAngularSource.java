package com.huawei.clouds.openrewrite.singlespaangular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.List;

/** Normalize only historical physical entries with an unambiguous target public entry. */
public final class MigrateDeterministicSingleSpaAngularSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic single-spa-angular source imports";
    }

    @Override
    public String getDescription() {
        return "Normalizes known historical static imports and direct literal require calls to the 9.2.0 public root, parcel, or stable webpack entry.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean active;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean previous = active;
                active = SingleSpaAngularSupport.projectPath(cu.getSourcePath());
                JS.CompilationUnit visited = active ? super.visitJsCompilationUnit(cu, ctx) : cu;
                active = previous;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!active || !(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                String module = SingleSpaAngularSupport.moduleName(visited);
                String replacement = SingleSpaAngularSupport.migratedModule(module);
                return module.equals(replacement) ? visited :
                        visited.withModuleSpecifier(SingleSpaAngularSupport.replaceString(literal, replacement));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!active) return visited;
                String module = SingleSpaAngularSupport.requireModule(visited);
                String replacement = SingleSpaAngularSupport.migratedModule(module);
                if (module.equals(replacement) || !(visited.getArguments().get(0) instanceof J.Literal literal)) {
                    return visited;
                }
                return visited.withArguments(List.of(SingleSpaAngularSupport.replaceString(literal, replacement)));
            }
        };
    }
}
