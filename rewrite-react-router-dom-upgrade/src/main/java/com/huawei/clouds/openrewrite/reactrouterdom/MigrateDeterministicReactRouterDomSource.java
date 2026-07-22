package com.huawei.clouds.openrewrite.reactrouterdom;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.javascript.tree.JSX;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Applies only source changes whose v5-to-v6 mapping is explicit and semantics-preserving. */
public final class MigrateDeterministicReactRouterDomSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic React Router DOM source APIs";
    }

    @Override
    public String getDescription() {
        return "Renames NavLink exact to end and moves a sole StaticRouter named import to " +
               "react-router-dom/server, following the fixed v6.30.4 upgrade guide.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> aliases = Map.of();
            private Set<String> declaredNames = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, String> previous = aliases;
                Set<String> previousDeclared = declaredNames;
                aliases = new HashMap<>();
                declaredNames = findDeclaredNames(cu);
                collectImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                aliases = previous;
                declaredNames = previousDeclared;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!"react-router-dom".equals(ReactRouterDomSourceSupport.moduleName(visited)) ||
                    ReactRouterDomSourceSupport.importedAlias(visited, "StaticRouter") == null ||
                    ReactRouterDomSourceSupport.namedImportCount(visited) != 1 ||
                    ReactRouterDomSourceSupport.hasDefaultImport(visited) ||
                    !(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                return visited.withModuleSpecifier(literal.withValue("react-router-dom/server")
                        .withValueSource(quote(literal) + "react-router-dom/server" + quote(literal)));
            }

            @Override
            public JSX.Attribute visitJsxAttribute(JSX.Attribute attribute, ExecutionContext ctx) {
                JSX.Attribute visited = super.visitJsxAttribute(attribute, ctx);
                JSX.Tag tag = getCursor().firstEnclosing(JSX.Tag.class);
                String local = tag == null ? "" : ReactRouterDomSourceSupport.expressionName(tag.getOpenName());
                if (tag == null || declaredNames.contains(local) ||
                    !"exact".equals(ReactRouterDomSourceSupport.expressionName(visited.getKey())) ||
                    !"NavLink".equals(aliases.get(local))) {
                    return visited;
                }
                return visited.withKey(ReactRouterDomSourceSupport.rename(visited.getKey(), "end"));
            }

            private void collectImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if ("react-router-dom".equals(ReactRouterDomSourceSupport.moduleName(visited))) {
                            for (String api : new String[]{"NavLink", "StaticRouter"}) {
                                String alias = ReactRouterDomSourceSupport.importedAlias(visited, api);
                                if (alias != null) aliases.put(alias, api);
                            }
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private Set<String> findDeclaredNames(JS.CompilationUnit cu) {
                Set<String> names = new HashSet<>();
                new JavaScriptIsoVisitor<Set<String>>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, Set<String> accumulator) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, accumulator);
                        accumulator.add(visited.getSimpleName());
                        return visited;
                    }
                }.visit(cu, names);
                return names;
            }
        };
    }

    private static String quote(J.Literal literal) {
        return literal.getValueSource() != null && literal.getValueSource().startsWith("'") ? "'" : "\"";
    }
}
