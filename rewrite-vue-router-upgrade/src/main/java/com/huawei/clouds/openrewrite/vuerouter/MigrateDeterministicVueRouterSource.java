package com.huawei.clouds.openrewrite.vuerouter;

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

/** Applies context-free official Vue Router 5 import and route-option migrations. */
public final class MigrateDeterministicVueRouterSource extends Recipe {
    private static final Map<String, String> IMPORTS = new HashMap<>();

    static {
        IMPORTS.put("unplugin-vue-router/vite", "vue-router/vite");
        IMPORTS.put("unplugin-vue-router", "vue-router/unplugin");
        IMPORTS.put("unplugin-vue-router/data-loaders", "vue-router/experimental");
        IMPORTS.put("unplugin-vue-router/data-loaders/basic", "vue-router/experimental");
        IMPORTS.put("unplugin-vue-router/data-loaders/pinia-colada", "vue-router/experimental/pinia-colada");
    }

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Vue Router 5 source constructs";
    }

    @Override
    public String getDescription() {
        return "Rewrites documented unplugin entry points, exact catch-all route records, and x/y scroll positions " +
               "whose Vue Router ownership can be established from imports and AST context.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean routerFile;
            private Set<String> defaultRouterAliases = Set.of();
            private Set<String> createRouterAliases = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean previousRouterFile = routerFile;
                Set<String> previousDefaults = defaultRouterAliases;
                Set<String> previousCreate = createRouterAliases;
                routerFile = false;
                defaultRouterAliases = new HashSet<>();
                createRouterAliases = new HashSet<>();
                scanImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                routerFile = previousRouterFile;
                defaultRouterAliases = previousDefaults;
                createRouterAliases = previousCreate;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String replacement = IMPORTS.get(VueRouterJavaScriptSupport.moduleName(visited));
                if (replacement == null || !(visited.getModuleSpecifier() instanceof J.Literal literal)) {
                    return visited;
                }
                String source = literal.getValueSource();
                String quote = source != null && source.startsWith("\"") ? "\"" : "'";
                return visited.withModuleSpecifier(literal.withValue(replacement)
                        .withValueSource(quote + replacement + quote));
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!routerFile) {
                    return visited;
                }
                String name = VueRouterJavaScriptSupport.propertyName(visited.getName());
                if ("path".equals(name) && visited.getInitializer() instanceof J.Literal literal &&
                    ("*".equals(literal.getValue()) || "/*".equals(literal.getValue())) && isRouteRecord(property)) {
                    String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                            ? "\"" : "'";
                    return visited.withInitializer(literal.withValue("/:pathMatch(.*)*")
                            .withValueSource(quote + "/:pathMatch(.*)*" + quote));
                }
                if (("x".equals(name) || "y".equals(name)) && insideScrollBehavior()) {
                    return visited.withName(renameProperty(visited.getName(), "x".equals(name) ? "left" : "top"));
                }
                return visited;
            }

            private Expression renameProperty(Expression name, String replacement) {
                if (name instanceof J.Identifier identifier) {
                    return identifier.withSimpleName(replacement);
                }
                if (name instanceof J.Literal literal) {
                    String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                            ? "\"" : "'";
                    return literal.withValue(replacement).withValueSource(quote + replacement + quote);
                }
                return name;
            }

            private boolean isRouteRecord(JS.PropertyAssignment path) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(path.getId()))) {
                    return false;
                }
                boolean routeShape = object.getBody().getStatements().stream().anyMatch(statement ->
                        statement instanceof JS.PropertyAssignment sibling && Set.of(
                                "component", "components", "redirect", "children")
                                .contains(VueRouterJavaScriptSupport.propertyName(sibling.getName())));
                if (!routeShape) {
                    return false;
                }
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.MethodInvocation invocation &&
                        invocation.getSelect() == null &&
                        createRouterAliases.contains(invocation.getSimpleName())) {
                        return true;
                    }
                    if (cursor.getValue() instanceof J.NewClass created &&
                        created.getClazz() instanceof J.Identifier identifier &&
                        defaultRouterAliases.contains(identifier.getSimpleName())) {
                        return true;
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean insideScrollBehavior() {
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof JS.PropertyAssignment ancestor &&
                        "scrollBehavior".equals(VueRouterJavaScriptSupport.propertyName(ancestor.getName()))) {
                        return true;
                    }
                    if (cursor.getValue() instanceof J.MethodDeclaration method &&
                        "scrollBehavior".equals(method.getSimpleName())) {
                        return true;
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = VueRouterJavaScriptSupport.moduleName(visited);
                        if (module.equals("vue-router") || module.startsWith("vue-router/") ||
                            module.equals("unplugin-vue-router") || module.startsWith("unplugin-vue-router/")) {
                            routerFile = true;
                        }
                        if ("vue-router".equals(module) && visited.getImportClause() != null) {
                            if (visited.getImportClause().getName() != null) {
                                defaultRouterAliases.add(visited.getImportClause().getName().getSimpleName());
                            }
                            VueRouterJavaScriptSupport.collectNamed(visited, "createRouter", createRouterAliases);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
