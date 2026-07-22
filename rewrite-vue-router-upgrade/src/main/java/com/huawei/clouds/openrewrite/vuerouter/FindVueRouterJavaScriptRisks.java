package com.huawei.clouds.openrewrite.vuerouter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Marks Vue Router 3-to-5 and experimental 5.0.3 source migration boundaries. */
public final class FindVueRouterJavaScriptRisks extends Recipe {
    private static final Set<String> REMOVED_METHODS = Set.of("onReady", "match", "getMatchedComponents");
    private static final Set<String> GUARDS = Set.of("beforeEach", "beforeResolve", "beforeEnter");

    @Override
    public String getDisplayName() {
        return "Find Vue Router 5 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks Vue Router 3 construction/options, removed instance APIs, callback navigation, ref route state, " +
               "guard next callbacks, route syntax, history-state, unplugin leftovers, and 5.0.3 experimental APIs.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean routerFile;
            private Set<String> defaultRouterAliases = Set.of();
            private Set<String> createRouterAliases = Set.of();
            private Set<String> routerVariables = Set.of();
            private Set<String> missAliases = Set.of();
            private Set<String> navigationResultAliases = Set.of();
            private Set<String> declaredNames = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean oldRouterFile = routerFile;
                Set<String> oldDefaults = defaultRouterAliases;
                Set<String> oldCreate = createRouterAliases;
                Set<String> oldVariables = routerVariables;
                Set<String> oldMiss = missAliases;
                Set<String> oldResult = navigationResultAliases;
                Set<String> oldDeclared = declaredNames;
                routerFile = false;
                defaultRouterAliases = new HashSet<>();
                createRouterAliases = new HashSet<>();
                routerVariables = new HashSet<>();
                missAliases = new HashSet<>();
                navigationResultAliases = new HashSet<>();
                declaredNames = findDeclaredNames(cu);
                scanImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                routerFile = oldRouterFile;
                defaultRouterAliases = oldDefaults;
                createRouterAliases = oldCreate;
                routerVariables = oldVariables;
                missAliases = oldMiss;
                navigationResultAliases = oldResult;
                declaredNames = oldDeclared;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = VueRouterJavaScriptSupport.moduleName(visited);
                if ("vue-router".equals(module) && visited.getImportClause() != null &&
                    visited.getImportClause().getName() != null) {
                    return SearchResult.found(visited,
                            "Vue Router 3's default Router class was removed; use named createRouter plus an explicit web, hash, or memory history and install it with app.use(router)");
                }
                if (module.equals("unplugin-vue-router") || module.startsWith("unplugin-vue-router/")) {
                    return SearchResult.found(visited,
                            "This unplugin-vue-router entry point was not deterministically mapped; Vue Router 5 merged the plugin, so select vue-router/vite, /unplugin, /experimental, /volar, or another documented export");
                }
                if (module.startsWith("vue-router/dist/") || module.startsWith("vue-router/src/")) {
                    return SearchResult.found(visited,
                            "Deep Vue Router implementation imports bypass public conditional exports; migrate to a documented package entry point and verify bundler/SSR resolution");
                }
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                if (declaration == null || !VueRouterJavaScriptSupport.moduleName(declaration)
                        .startsWith("vue-router/experimental")) {
                    return visited;
                }
                String imported = VueRouterJavaScriptSupport.importedName(visited);
                if ("NavigationResult".equals(imported)) {
                    return SearchResult.found(visited,
                            "new NavigationResult(to) is deprecated in Vue Router 5.0.3; use reroute(to) and verify loader navigation/error control flow");
                }
                if ("selectNavigationResult".equals(imported) || "NAVIGATION_RESULTS_KEY".equals(imported) ||
                    "MatchMiss".equals(imported)) {
                    return SearchResult.found(visited,
                            imported + " was removed or internalized in Vue Router 5.0.3; migrate to immediate reroute/miss behavior without result selection");
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                if (isRouterConstruction(visited.getInitializer())) {
                    routerVariables.add(visited.getSimpleName());
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (visited.getClazz() instanceof J.Identifier identifier &&
                    unshadowed(defaultRouterAliases, identifier.getSimpleName())) {
                    return SearchResult.found(visited,
                            "new Router/New VueRouter was removed; call createRouter with explicit history and migrate Vue.use to app.use after completing Vue 3.5 migration");
                }
                if (visited.getClazz() instanceof J.Identifier identifier &&
                    unshadowed(navigationResultAliases, identifier.getSimpleName())) {
                    return SearchResult.found(visited,
                            "NavigationResult construction is deprecated in Vue Router 5.0.3; return reroute(to) from the experimental loader boundary");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String method = visited.getSimpleName();
                if ("use".equals(method) && hasDefaultRouterArgument(visited.getArguments())) {
                    return SearchResult.found(visited,
                            "Vue.use(Router) is a Vue 2 global install; create a Vue 3 application and call app.use(router) after createRouter configuration");
                }
                if (REMOVED_METHODS.contains(method) && isRouterOwner(visited.getSelect())) {
                    String replacement = switch (method) {
                        case "onReady" -> "isReady() returning a Promise";
                        case "match" -> "resolve";
                        default -> "currentRoute.value.matched and each record's components";
                    };
                    return SearchResult.found(visited,
                            "router." + method + " was removed; migrate to " + replacement + " and verify SSR, redirects, async components, and error handling");
                }
                if (("push".equals(method) || "replace".equals(method)) && isRouterOwner(visited.getSelect()) &&
                    visited.getArguments().size() > 1) {
                    return SearchResult.found(visited,
                            "Vue Router 4 removed push/replace completion and failure callbacks; await the navigation Promise and classify navigation failures explicitly");
                }
                if (GUARDS.contains(method) && isRouterOwner(visited.getSelect()) && hasThreeParameterGuard(visited)) {
                    return SearchResult.found(visited,
                            "Vue Router 5.0.3 deprecates the navigation guard next callback; return a location, false, undefined, or throw while preserving every async/error branch");
                }
                if (unshadowed(missAliases, method) && visited.getSelect() == null) {
                    return SearchResult.found(visited,
                            "miss() now throws internally and returns never in Vue Router 5.0.3; remove throw miss(), prevent swallowed misses, and verify custom matcher control flow");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                String name = visited.getSimpleName();
                if ("currentRoute".equals(name) && isRouterOwner(visited.getTarget())) {
                    return SearchResult.found(visited,
                            "router.currentRoute is a ref in Vue Router 4/5; instance access requires currentRoute.value while useRoute() and this.$route do not");
                }
                if ("app".equals(name) && isRouterOwner(visited.getTarget())) {
                    return SearchResult.found(visited,
                            "router.app was removed because routers can serve multiple Vue applications; retain the Vue 3 app explicitly at the owning integration boundary");
                }
                if ("route".equals(name) && visited.getTarget() instanceof J.MethodInvocation resolved &&
                    "resolve".equals(resolved.getSimpleName()) && isRouterOwner(resolved.getSelect())) {
                    return SearchResult.found(visited,
                            "Vue Router 3 resolve(...).route changed; consume the normalized location returned by resolve and verify href, params, query, hash, and redirects");
                }
                if ("state".equals(name) && visited.getTarget() instanceof J.Identifier identifier &&
                    "history".equals(identifier.getSimpleName()) && routerFile) {
                    return SearchResult.found(visited,
                            "Vue Router stores scroll and previous-location data in history.state; merge existing state instead of overwriting it directly");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!routerFile) {
                    return visited;
                }
                String name = VueRouterJavaScriptSupport.propertyName(visited.getName());
                if (Set.of("mode", "base", "fallback").contains(name) && insideLegacyRouterOptions(property)) {
                    return SearchResult.found(visited, switch (name) {
                        case "mode" -> "Vue Router 3 mode was replaced by createWebHistory, createWebHashHistory, or createMemoryHistory; choose from deployment and SSR behavior";
                        case "base" -> "Vue Router 3 base moved into the selected history factory; verify subdirectory hosting, SSR, assets, and direct URLs";
                        default -> "Vue Router 3 fallback was removed; select an explicit history strategy and configure server fallback separately";
                    });
                }
                if ("scrollBehavior".equals(name)) {
                    return SearchResult.found(visited,
                            "Vue Router scroll behavior changed to ScrollToOptions and async navigation; verify left/top, saved positions, hashes, delays, forward/back, and hydration");
                }
                if ("selectNavigationResult".equals(name)) {
                    return SearchResult.found(visited,
                            "selectNavigationResult was removed in Vue Router 5.0.3 because reroute now acts immediately; remove selection logic and verify competing loaders");
                }
                if ("path".equals(name) && visited.getInitializer() instanceof J.Literal literal &&
                    literal.getValue() instanceof String path && isRouteRecord(property) &&
                    (path.contains("*") || hasUnnamedParameter(path))) {
                    return SearchResult.found(visited,
                            "This Vue Router 3 path uses removed catch-all or path-to-regexp syntax; name custom/repeatable/optional params and verify resolve/push encoding plus route ranking");
                }
                return visited;
            }

            private boolean hasDefaultRouterArgument(List<Expression> arguments) {
                return arguments.stream().anyMatch(argument -> argument instanceof J.Identifier identifier &&
                        unshadowed(defaultRouterAliases, identifier.getSimpleName()));
            }

            private boolean hasThreeParameterGuard(J.MethodInvocation invocation) {
                if (invocation.getArguments().isEmpty()) {
                    return false;
                }
                Expression first = invocation.getArguments().get(0);
                if (first instanceof J.Lambda lambda) {
                    return lambda.getParameters().getParameters().size() >= 3;
                }
                return first instanceof JS.ArrowFunction arrow &&
                       arrow.getLambda().getParameters().getParameters().size() >= 3;
            }

            private boolean isRouterConstruction(Expression expression) {
                if (expression instanceof J.NewClass created && created.getClazz() instanceof J.Identifier identifier) {
                    return unshadowed(defaultRouterAliases, identifier.getSimpleName());
                }
                return expression instanceof J.MethodInvocation invocation && invocation.getSelect() == null &&
                       unshadowed(createRouterAliases, invocation.getSimpleName());
            }

            private boolean isRouterOwner(Expression expression) {
                if (expression instanceof J.Identifier identifier) {
                    return routerVariables.contains(identifier.getSimpleName());
                }
                return expression instanceof J.FieldAccess field && "$router".equals(field.getSimpleName());
            }

            private boolean insideLegacyRouterOptions(JS.PropertyAssignment property) {
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.NewClass created &&
                        created.getClazz() instanceof J.Identifier identifier &&
                        unshadowed(defaultRouterAliases, identifier.getSimpleName())) {
                        return true;
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean isRouteRecord(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(property.getId()))) {
                    return false;
                }
                return object.getBody().getStatements().stream().anyMatch(statement ->
                        statement instanceof JS.PropertyAssignment sibling && Set.of(
                                "component", "components", "redirect", "children")
                                .contains(VueRouterJavaScriptSupport.propertyName(sibling.getName())));
            }

            private boolean hasUnnamedParameter(String path) {
                return path.matches(".*(?:^|/):(?:\\([^)]*\\)|[*+?]).*");
            }

            private boolean unshadowed(Set<String> imported, String name) {
                return imported.contains(name) && !declaredNames.contains(name);
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
                        if (module.startsWith("vue-router/experimental")) {
                            VueRouterJavaScriptSupport.collectNamed(visited, "miss", missAliases);
                            VueRouterJavaScriptSupport.collectNamed(visited, "NavigationResult", navigationResultAliases);
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
}
