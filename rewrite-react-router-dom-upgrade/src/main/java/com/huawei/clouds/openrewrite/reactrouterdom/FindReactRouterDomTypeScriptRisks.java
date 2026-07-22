package com.huawei.clouds.openrewrite.reactrouterdom;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.javascript.tree.JSX;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Marks exact React Router nodes whose v6 migration depends on application behavior. */
public final class FindReactRouterDomTypeScriptRisks extends Recipe {
    private static final Set<String> ROUTER_APIS = Set.of(
            "BrowserRouter", "HashRouter", "MemoryRouter", "Router", "Routes", "Switch", "Route", "Redirect",
            "Navigate", "Prompt", "Link", "NavLink", "StaticRouter", "Outlet", "RouterProvider",
            "useHistory", "useNavigate", "useRouteMatch", "useMatch", "withRouter", "matchPath", "generatePath",
            "useRoutes", "matchRoutes", "createRoutesFromElements", "createBrowserRouter", "createHashRouter",
            "createMemoryRouter", "useBlocker", "unstable_usePrompt", "useBeforeUnload", "redirect", "replace"
    );
    private static final Set<String> ROUTE_OBJECT_CALLS = Set.of(
            "useRoutes", "createBrowserRouter", "createHashRouter", "createMemoryRouter", "createRoutesFromElements"
    );
    private static final Set<String> ROUTE_OBJECT_KEYS = Set.of(
            "path", "index", "children", "element", "Component", "loader", "action", "errorElement", "ErrorBoundary",
            "lazy", "shouldRevalidate", "handle", "hydrateFallbackElement", "HydrateFallback"
    );

    @Override
    public String getDisplayName() {
        return "Find React Router DOM 6.30.4 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks legacy rendering, matching, navigation, links, blockers, SSR, integrations, and data-router " +
               "APIs at the imported JSX attribute, tag, call, property, or module path that requires a migration decision.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> importedApis = Map.of();
            private Set<String> historyVariables = Set.of();
            private Set<String> routerConfigApis = Set.of();
            private Set<String> connectedRouterApis = Set.of();
            private Set<String> declaredNames = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, String> oldApis = importedApis;
                Set<String> oldHistory = historyVariables;
                Set<String> oldConfig = routerConfigApis;
                Set<String> oldConnected = connectedRouterApis;
                Set<String> oldDeclared = declaredNames;
                importedApis = new HashMap<>();
                historyVariables = new HashSet<>();
                routerConfigApis = new HashSet<>();
                connectedRouterApis = new HashSet<>();
                declaredNames = findDeclaredNames(cu);
                collect(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                importedApis = oldApis;
                historyVariables = oldHistory;
                routerConfigApis = oldConfig;
                connectedRouterApis = oldConnected;
                declaredNames = oldDeclared;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = ReactRouterDomSourceSupport.moduleName(visited);
                if (module.startsWith("react-router-dom/") && !"react-router-dom/server".equals(module)) {
                    return visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                            "Private react-router-dom deep entry detected; use only the public react-router-dom or react-router-dom/server entry and verify CJS/ESM bundling"));
                }
                if (("react-router-dom".equals(module) || "react-router-dom/server".equals(module)) &&
                    visited.getImportClause() != null &&
                    (!(visited.getImportClause().getNamedBindings() instanceof JS.NamedImports) ||
                     ReactRouterDomSourceSupport.hasDefaultImport(visited))) {
                    return visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                            "Namespace/default React Router import requires manual inventory; convert uses to supported named public APIs before applying v6 source migrations"));
                }
                if ("react-router-dom".equals(module) &&
                    ReactRouterDomSourceSupport.importedAlias(visited, "StaticRouter") != null &&
                    ReactRouterDomSourceSupport.namedImportCount(visited) > 1) {
                    return markImportSpecifier(visited, "StaticRouter",
                            "StaticRouter moved to react-router-dom/server; split this mixed import and verify SSR status, redirects, basename, and hydration");
                }
                return visited;
            }

            @Override
            public JSX.Tag visitJsxTag(JSX.Tag tag, ExecutionContext ctx) {
                JSX.Tag visited = super.visitJsxTag(tag, ctx);
                String api = api(visited.getOpenName());
                if ("Switch".equals(api)) {
                    return SearchResult.found(visited, "Switch becomes Routes, but v6 uses best-match ranking and permits only Route/Fragment children; verify ordering, custom/private routes, nesting, and 404 behavior");
                }
                if ("Redirect".equals(api)) {
                    return SearchResult.found(visited, "Redirect requires Navigate, a Route element, server redirect, or data-router redirect; v5 defaults to replace while Navigate defaults to push");
                }
                if ("Prompt".equals(api)) {
                    return SearchResult.found(visited, "Prompt was removed; design useBlocker/unstable_usePrompt plus useBeforeUnload and test proceed/reset across repeated back/forward navigation");
                }
                if ("Routes".equals(api)) {
                    return SearchResult.found(visited, "Routes uses ranked relative matching and Route/Fragment children; verify nested Outlet rendering, descendant splats, index routes, 404s, and route order assumptions");
                }
                if ("RouterProvider".equals(api)) {
                    return SearchResult.found(visited, "RouterProvider enables data-router lifecycle; verify router creation outside React, hydration data, loaders/actions, errors, revalidation, fetchers, and future flags");
                }
                if ("StaticRouter".equals(api)) {
                    return SearchResult.found(visited, "StaticRouter SSR must resolve status, headers, redirects, basename, location, and client hydration consistently; data-router SSR uses the static handler/provider flow");
                }
                if ("ConnectedRouter".equals(api)) {
                    return SearchResult.found(visited, "ConnectedRouter owns external history and Redux router state; verify major-version compatibility or remove that integration before adopting v6 navigation");
                }
                return visited;
            }

            @Override
            public JSX.Attribute visitJsxAttribute(JSX.Attribute attribute, ExecutionContext ctx) {
                JSX.Attribute visited = super.visitJsxAttribute(attribute, ctx);
                JSX.Tag tag = getCursor().firstEnclosing(JSX.Tag.class);
                if (tag == null) return visited;
                String component = api(tag.getOpenName());
                if (component == null) return visited;
                String name = ReactRouterDomSourceSupport.expressionName(visited.getKey());
                if ("Route".equals(component)) {
                    if (Set.of("component", "render", "children").contains(name)) {
                        return SearchResult.found(visited, "Route " + name + " rendering changes to element/nested children; move route props to hooks, preserve custom props, and verify render/no-match behavior");
                    }
                    if ("exact".equals(name)) {
                        return SearchResult.found(visited, "Route exact is removed, but descendant Routes may require a trailing /*; verify index, partial, nested, and not-found behavior before removing it");
                    }
                    if ("strict".equals(name)) {
                        return SearchResult.found(visited, "Route strict was removed and trailing slashes are ignored by client matching; decide whether any distinction must move to the server");
                    }
                    if ("sensitive".equals(name)) {
                        return SearchResult.found(visited, "Route sensitivity moves to the containing Routes/caseSensitive model; verify mixed-case URL behavior before relocating this option");
                    }
                    if ("path".equals(name)) {
                        return SearchResult.found(visited, "Route path uses v6 ranked relative patterns; verify regexp/array paths, optional or splat syntax, descendant /*, basename, and nesting");
                    }
                    if (Set.of("loader", "action", "errorElement", "lazy", "Component", "hydrateFallbackElement").contains(name)) {
                        return SearchResult.found(visited, "This data-route field requires a data router and has loader/action/error/lazy/hydration lifecycle semantics; verify aborts, responses, revalidation, errors, and SSR");
                    }
                }
                if ("NavLink".equals(component) && Set.of("activeClassName", "activeStyle").contains(name)) {
                    return SearchResult.found(visited, "NavLink " + name + " was removed; convert className/style to an isActive callback while preserving existing inactive and pending styling");
                }
                if ("NavLink".equals(component) && "strict".equals(name)) {
                    return SearchResult.found(visited, "NavLink strict was removed; v6 ignores trailing slashes, so verify active-state behavior explicitly");
                }
                if (Set.of("Link", "NavLink").contains(component) && "component".equals(name)) {
                    return SearchResult.found(visited, "Link component was removed; build an accessible wrapper with useHref/useLinkClickHandler and preserve ref, target, state, and cancellation behavior");
                }
                if (Set.of("Link", "NavLink", "Navigate").contains(component) && "to".equals(name)) {
                    return SearchResult.found(visited, "Navigation destination uses v6 route-relative resolution; verify match.url interpolation, .., basename, splats, search/hash encoding, and object state migration");
                }
                if (Set.of("Link", "NavLink", "Navigate").contains(component) && "state".equals(name)) {
                    return SearchResult.found(visited, "Navigation state is history state, not durable URL data; verify reload/back-forward behavior and keep state separate from Link to objects");
                }
                if (Set.of("BrowserRouter", "HashRouter", "MemoryRouter", "Router", "StaticRouter").contains(component) &&
                    Set.of("basename", "future", "hydrationData", "history", "navigator", "location").contains(name)) {
                    return SearchResult.found(visited, "Router " + name + " changes routing/SSR integration boundaries; verify deep links, relative URLs, history ownership, hydration, and deployment base paths");
                }
                if ("ConnectedRouter".equals(component) && "history".equals(name)) {
                    return SearchResult.found(visited, "External history passed to ConnectedRouter must be removed or held at a compatible router major with Redux reducer/middleware behavior tested");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String local = visited.getSimpleName();
                if ("require".equals(local) && visited.getArguments().size() == 1 &&
                    visited.getArguments().get(0) instanceof J.Literal literal &&
                    literal.getValue() instanceof String module && module.startsWith("react-router-dom")) {
                    return visited.withArguments(java.util.List.of(SearchResult.found(literal,
                            "CommonJS React Router import requires manual inventory; use supported public react-router-dom or react-router-dom/server APIs and verify CJS/ESM resolution")));
                }
                String imported = declaredNames.contains(local) ? null : importedApis.get(local);
                if (imported == null && !declaredNames.contains(local) && routerConfigApis.contains(local)) {
                    imported = "react-router-config:" + local;
                }
                if (imported == null && !declaredNames.contains(local) && connectedRouterApis.contains(local)) {
                    imported = "connected-react-router:" + local;
                }
                if (imported != null) {
                    if (Set.of("useHistory", "useNavigate").contains(imported)) {
                        return SearchResult.found(visited, imported + " navigation must preserve push/replace/state/relative/numeric traversal and pending-navigation semantics; migrate history calls as one unit");
                    }
                    if (Set.of("useRouteMatch", "useMatch", "matchPath", "generatePath").contains(imported)) {
                        return SearchResult.found(visited, imported + " uses the v6 path-pattern algorithm/options or result shape; verify required pattern, argument order, end/caseSensitive, splats, encoding, and params");
                    }
                    if ("withRouter".equals(imported)) {
                        return SearchResult.found(visited, "withRouter was removed; inject useLocation/useNavigate/useParams/useMatch through a controlled wrapper and preserve rerenders and class-component props");
                    }
                    if (Set.of("useBlocker", "unstable_usePrompt", "useBeforeUnload").contains(imported)) {
                        return SearchResult.found(visited, imported + " needs an explicit blocker/unload state machine; test proceed/reset, repeated POP navigation, beforeunload, and browser differences");
                    }
                    if (Set.of("redirect", "replace").contains(imported)) {
                        return SearchResult.found(visited, imported + " response must be validated for external/protocol-relative/double-slash destinations, status, headers, and loader/action catch boundaries");
                    }
                    if (ROUTE_OBJECT_CALLS.contains(imported) || imported.startsWith("react-router-config:")) {
                        return SearchResult.found(visited, imported + " route configuration must be migrated as a coherent tree; verify ranking, nesting, Outlet, loaders/actions/errors/lazy, SSR, and 404 behavior");
                    }
                    if (imported.startsWith("connected-react-router:")) {
                        return SearchResult.found(visited, "connected-react-router action/history integration requires a compatible major or removal; verify Redux state, middleware, time travel, and navigation ordering");
                    }
                }
                if (visited.getSelect() instanceof J.Identifier identifier && historyVariables.contains(identifier.getSimpleName()) &&
                    Set.of("push", "replace", "go", "goBack", "goForward", "listen", "block").contains(visited.getSimpleName())) {
                    return SearchResult.found(visited, "history." + visited.getSimpleName() + " must move to useNavigate or a deliberate integration; preserve replace/state/traversal and review unsupported listen/block behavior");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = ReactRouterDomSourceSupport.expressionName(visited.getName());
                J.MethodInvocation owner = getCursor().firstEnclosing(J.MethodInvocation.class);
                String ownerApi = owner == null || declaredNames.contains(owner.getSimpleName()) ?
                        null : importedApis.get(owner.getSimpleName());
                if (owner != null && ROUTE_OBJECT_KEYS.contains(name) &&
                    ownerApi != null && ROUTE_OBJECT_CALLS.contains(ownerApi)) {
                    return SearchResult.found(visited, "Route object " + name + " participates in ranked nesting/data-router lifecycle; verify path hierarchy, rendering, abort/revalidation, errors, lazy modules, and SSR");
                }
                return visited;
            }

            private void collect(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = ReactRouterDomSourceSupport.moduleName(visited);
                        if ("react-router-dom".equals(module) || "react-router-dom/server".equals(module)) {
                            for (String api : ROUTER_APIS) {
                                String alias = ReactRouterDomSourceSupport.importedAlias(visited, api);
                                if (alias != null) importedApis.put(alias, api);
                            }
                        } else if ("react-router-config".equals(module)) {
                            collectAllAliases(visited, routerConfigApis);
                        } else if ("connected-react-router".equals(module)) {
                            collectAllAliases(visited, connectedRouterApis);
                            String alias = ReactRouterDomSourceSupport.importedAlias(visited, "ConnectedRouter");
                            if (alias != null) importedApis.put(alias, "ConnectedRouter");
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (visited.getInitializer() instanceof J.MethodInvocation call &&
                            !declaredNames.contains(call.getSimpleName()) &&
                            "useHistory".equals(importedApis.get(call.getSimpleName()))) {
                            historyVariables.add(visited.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private String api(Object expression) {
                String local = ReactRouterDomSourceSupport.expressionName(expression);
                return declaredNames.contains(local) ? null : importedApis.get(local);
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

    private static void collectAllAliases(JS.Import declaration, Set<String> aliases) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return;
        for (JS.ImportSpecifier element : named.getElements()) {
            Expression specifier = element.getSpecifier();
            if (specifier instanceof J.Identifier identifier) aliases.add(identifier.getSimpleName());
            if (specifier instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
                aliases.add(identifier.getSimpleName());
            }
        }
    }

    private static JS.Import markImportSpecifier(JS.Import declaration, String importedName, String message) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return declaration;
        java.util.List<JS.ImportSpecifier> elements = org.openrewrite.internal.ListUtils.map(named.getElements(), element -> {
            Expression specifier = element.getSpecifier();
            String name = specifier instanceof J.Identifier identifier ? identifier.getSimpleName() :
                    specifier instanceof JS.Alias alias ? alias.getPropertyName().getSimpleName() : "";
            return importedName.equals(name) ? SearchResult.found(element, message) : element;
        });
        return declaration.withImportClause(clause.withNamedBindings(named.withElements(elements)));
    }
}
