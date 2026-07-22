package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

/** Replace the deprecated browser-dynamic platform only when every binding use is understood. */
public final class MigrateDeterministicAngularPlatformBrowserDynamicSource extends Recipe {
    static final String DYNAMIC_MODULE = "@angular/platform-browser-dynamic";
    static final String BROWSER_MODULE = "@angular/platform-browser";

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular browser-dynamic bootstrap source";
    }

    @Override
    public String getDescription() {
        return "Replace a sole platformBrowserDynamic import with platformBrowser when an aliased binding is safe " +
               "or every unaliased local reference is a direct call.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private ImportPlan plan;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                ImportPlan previous = plan;
                plan = analyze(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                plan = previous;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (plan == null || !plan.safe || !DYNAMIC_MODULE.equals(moduleName(visited)) ||
                    visited.getImportClause() == null ||
                    !(visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named) ||
                    named.getElements().size() != 1) {
                    return visited;
                }
                JS.ImportSpecifier element = named.getElements().get(0);
                Expression specifier = element.getSpecifier();
                if (specifier instanceof J.Identifier identifier) {
                    element = element.withSpecifier(identifier.withSimpleName("platformBrowser"));
                } else if (specifier instanceof JS.Alias alias) {
                    element = element.withSpecifier(alias.withPropertyName(
                            alias.getPropertyName().withSimpleName("platformBrowser")));
                } else {
                    return visited;
                }
                JS.NamedImports migrated = named.withElements(java.util.List.of(element));
                J.Literal literal = (J.Literal) visited.getModuleSpecifier();
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"") ? "\"" : "'";
                return visited.withImportClause(visited.getImportClause().withNamedBindings(migrated))
                        .withModuleSpecifier(literal.withValue(BROWSER_MODULE)
                                .withValueSource(quote + BROWSER_MODULE + quote));
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (plan != null && plan.safe && !plan.aliased &&
                    visited.getFunction() instanceof J.Identifier identifier &&
                    "platformBrowserDynamic".equals(identifier.getSimpleName())) {
                    return visited.withFunction(identifier.withSimpleName("platformBrowser"));
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (plan != null && plan.safe && !plan.aliased && visited.getSelect() == null &&
                    "platformBrowserDynamic".equals(visited.getSimpleName())) {
                    return visited.withName(visited.getName().withSimpleName("platformBrowser"));
                }
                return visited;
            }
        };
    }

    private static ImportPlan analyze(JS.CompilationUnit cu, ExecutionContext ctx) {
        ImportPlan[] found = {null};
        new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                if (!DYNAMIC_MODULE.equals(moduleName(visited)) || visited.getImportClause() == null ||
                    visited.getImportClause().getName() != null ||
                    !(visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named) ||
                    named.getElements().size() != 1 ||
                    !"platformBrowserDynamic".equals(importedName(named.getElements().get(0)))) {
                    return visited;
                }
                Expression specifier = named.getElements().get(0).getSpecifier();
                boolean aliased = specifier instanceof JS.Alias;
                String local = aliased && ((JS.Alias) specifier).getAlias() instanceof J.Identifier identifier
                        ? identifier.getSimpleName() : "platformBrowserDynamic";
                found[0] = new ImportPlan(local, aliased, false);
                return visited;
            }
        }.visit(cu, ctx);
        if (found[0] == null) {
            return null;
        }
        int[] directCalls = {0};
        new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext scanCtx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, scanCtx);
                if (visited.getFunction() instanceof J.Identifier identifier &&
                    found[0].localName.equals(identifier.getSimpleName())) {
                    directCalls[0]++;
                }
                return visited;
            }


            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation,
                                                              ExecutionContext scanCtx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, scanCtx);
                if (visited.getSelect() == null &&
                    found[0].localName.equals(visited.getSimpleName())) {
                    directCalls[0]++;
                }
                return visited;
            }
        }.visit(cu, ctx);
        java.util.regex.Pattern binding = java.util.regex.Pattern.compile(
                "(?<![$\\w])" + java.util.regex.Pattern.quote(found[0].localName) + "(?![$\\w])");
        long occurrences = binding.matcher(cu.printAll()).results().count();
        return new ImportPlan(found[0].localName, found[0].aliased,
                directCalls[0] > 0 && occurrences == directCalls[0] + 1L);
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String importedName(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (specifier.getSpecifier() instanceof JS.Alias alias) {
            return alias.getPropertyName().getSimpleName();
        }
        return "";
    }

    private record ImportPlan(String localName, boolean aliased, boolean safe) {
    }
}
