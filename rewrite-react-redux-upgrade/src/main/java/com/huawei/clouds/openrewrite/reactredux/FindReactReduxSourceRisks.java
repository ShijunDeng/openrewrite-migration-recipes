package com.huawei.clouds.openrewrite.reactredux;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.javascript.tree.JSX;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Marks source nodes at documented v8/v9 compatibility boundaries with import-proven ownership. */
public final class FindReactReduxSourceRisks extends Recipe {
    private static final Set<String> REMOVED = Set.of("connectAdvanced", "DefaultRootState", "RootStateOrAny");
    @Override
    public String getDisplayName() {
        return "Find React Redux 9 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks private entries, removed APIs and types, deprecated connect/batch use, removed pure/noopCheck " +
               "options, Provider hydration snapshots, and explicit React Server Component ownership.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean active;
            private boolean hydration;
            private boolean serverOwned;
            private boolean augmentation;
            private Set<String> providers = Set.of();
            private Set<String> connects = Set.of();
            private Set<String> selectors = Set.of();
            private Set<String> namespaces = Set.of();
            private Map<String, Integer> declarations = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!ReactReduxSupport.isProjectPath(cu.getSourcePath())) return cu;
                boolean oldActive = active;
                boolean oldHydration = hydration;
                boolean oldServer = serverOwned;
                boolean oldAugmentation = augmentation;
                Set<String> oldProviders = providers;
                Set<String> oldConnects = connects;
                Set<String> oldSelectors = selectors;
                Set<String> oldNamespaces = namespaces;
                Map<String, Integer> oldDeclarations = declarations;
                active = true;
                hydration = false;
                String source = cu.printAll();
                serverOwned = source.matches("(?s).*?(?m)^\\s*['\"]use server['\"]\\s*;?.*");
                augmentation = source.matches("(?s).*?declare\\s+module\\s+['\"]react-redux['\"].*");
                providers = new HashSet<>();
                connects = new HashSet<>();
                selectors = new HashSet<>();
                namespaces = new HashSet<>();
                declarations = new HashMap<>();
                scan(cu, ctx, source);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                active = oldActive;
                hydration = oldHydration;
                serverOwned = oldServer;
                augmentation = oldAugmentation;
                providers = oldProviders;
                connects = oldConnects;
                selectors = oldSelectors;
                namespaces = oldNamespaces;
                declarations = oldDeclarations;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!active) return visited;
                String module = ReactReduxSupport.moduleName(visited);
                if (!ReactReduxSupport.isPackageModule(module)) return visited;
                if (!ReactReduxSupport.PUBLIC_ENTRIES.contains(module)) {
                    return visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                            "React Redux 9 exports only the root and /alternate-renderers runtime entries; this v7/v8 private, aggregate, /next, source, lib, es, or dist entry must move to an exported API"));
                }
                if (serverOwned && !module.endsWith("/package.json")) {
                    visited = visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                            "React Redux 9's react-server condition intentionally throws because hooks and context cannot run in a React Server Component; move this ownership behind a 'use client' boundary"));
                }
                JS.ImportClause clause = visited.getImportClause();
                if (!ReactReduxSupport.PACKAGE.equals(module) || clause == null) return visited;
                if (clause.getName() != null) {
                    visited = visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                            "React Redux has no default export; replace this default import with explicit public named bindings before the v9 ESM/CJS package boundary"));
                }
                if (!(clause.getNamedBindings() instanceof JS.NamedImports named)) return visited;
                List<JS.ImportSpecifier> marked = ListUtils.map(named.getElements(), specifier -> {
                    String imported = ReactReduxSupport.importedName(specifier);
                    if (REMOVED.contains(imported)) {
                        return SearchResult.found(specifier, removedMessage(imported));
                    }
                    if ("connect".equals(imported)) {
                        return SearchResult.found(specifier,
                                "React Redux 9.3 deprecates connect; migrate the component to useSelector/useDispatch, or deliberately import legacy_connect as connect to retain identical HOC behavior without the type deprecation");
                    }
                    if ("legacy_connect".equals(imported)) {
                        return SearchResult.found(specifier,
                                "legacy_connect preserves connect behavior but remains the legacy HOC architecture; inventory mapState, mapDispatch, mergeProps, ownProps, equality options, refs, and static hoisting before a hooks migration");
                    }
                    if ("batch".equals(imported)) {
                        return SearchResult.found(specifier,
                                "React Redux 9 deprecates batch because React 18 batches ReactDOM and React Native updates automatically; verify non-React subscribers and scheduling before removing this call");
                    }
                    return specifier;
                });
                return visited.withImportClause(clause.withNamedBindings(named.withElements(marked)));
            }

            @Override
            public JS.ExportDeclaration visitExportDeclaration(JS.ExportDeclaration declaration,
                                                                 ExecutionContext ctx) {
                JS.ExportDeclaration visited = super.visitExportDeclaration(declaration, ctx);
                if (!active || !(visited.getModuleSpecifier() instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module) || !ReactReduxSupport.isPackageModule(module)) {
                    return visited;
                }
                if (!ReactReduxSupport.PUBLIC_ENTRIES.contains(module)) {
                    return visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                            "React Redux 9 does not export this deep re-export target; re-export public bindings from react-redux and verify type/value ownership"));
                }
                return serverOwned && !module.endsWith("/package.json") ?
                        visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                                "React Redux hooks/context cannot be re-exported from an explicit React Server Component boundary")) : visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (!active || !"import".equals(visited.getFunction().toString().trim())) return visited;
                return visited.withArguments(markLoaderArguments(visited.getArguments()));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!active) return visited;
                if (!ReactReduxSupport.requireModule(visited).isEmpty() ||
                    visited.getSelect() == null && "import".equals(visited.getSimpleName())) {
                    return visited.withArguments(markLoaderArguments(visited.getArguments()));
                }
                if (visited.getSelect() instanceof J.Identifier identifier &&
                    owned(namespaces, identifier.getSimpleName())) {
                    String api = visited.getSimpleName();
                    if (REMOVED.contains(api)) return SearchResult.found(visited, removedMessage(api));
                    if ("connect".equals(api) || "legacy_connect".equals(api)) {
                        return SearchResult.found(visited,
                                "This namespace-owned connect HOC is deprecated in 9.3; choose hooks or the explicit legacy_connect compatibility alias and test HOC semantics");
                    }
                    if ("batch".equals(api)) {
                        return SearchResult.found(visited,
                                "React Redux 9 batch is a deprecated no-op under React 18 automatic batching; verify scheduling before removal");
                    }
                }
                return visited;
            }

            @Override
            public JSX.Tag visitJsxTag(JSX.Tag tag, ExecutionContext ctx) {
                JSX.Tag visited = super.visitJsxTag(tag, ctx);
                String component = ReactReduxSupport.expressionName(visited.getOpenName());
                if (!hydration || !owned(providers, component)) return visited;
                boolean hasServerState = false;
                for (JSX attribute : visited.getAttributes()) {
                    if (attribute instanceof JSX.Attribute named &&
                        "serverState".equals(ReactReduxSupport.expressionName(named.getKey()))) {
                        hasServerState = true;
                    }
                }
                return hasServerState ? visited : SearchResult.found(visited,
                        "React 18 hydration with React Redux 8/9 should pass the serialized server snapshot as Provider serverState so useSyncExternalStore renders the same initial UI before store updates");
            }

            @Override
            public JSX.Attribute visitJsxAttribute(JSX.Attribute attribute, ExecutionContext ctx) {
                JSX.Attribute visited = super.visitJsxAttribute(attribute, ctx);
                if (!"noopCheck".equals(ReactReduxSupport.expressionName(visited.getKey()))) return visited;
                JSX.Tag tag = getCursor().firstEnclosing(JSX.Tag.class);
                String component = tag == null ? "" : ReactReduxSupport.expressionName(tag.getOpenName());
                return owned(providers, component) ? SearchResult.found(visited,
                        "Provider noopCheck was renamed to identityFunctionCheck in React Redux 9; preserve its never/once/always policy under the new prop") : visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = ReactReduxSupport.propertyName(visited);
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                J.MethodInvocation call = getCursor().firstEnclosing(J.MethodInvocation.class);
                if (object == null || call == null || object.getClazz() != null) return visited;
                if ("pure".equals(name) && owned(connects, call.getSimpleName()) && isArgument(call, object, 3)) {
                    return SearchResult.found(visited,
                            "React Redux 8 removed connect's pure option; immutable updates are required, so remove the flag only after testing external mutable inputs, equality callbacks, ownProps, and rerender behavior");
                }
                if ("noopCheck".equals(name) && owned(selectors, call.getSimpleName()) && isArgument(call, object, 1)) {
                    return SearchResult.found(visited,
                            "React Redux 9 renamed noopCheck to devModeChecks.identityFunctionCheck; preserve the frequency while nesting it under devModeChecks");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if (!(visited.getTarget() instanceof J.Identifier identifier) ||
                    !owned(namespaces, identifier.getSimpleName())) return visited;
                String api = visited.getSimpleName();
                if (REMOVED.contains(api)) return SearchResult.found(visited, removedMessage(api));
                if ("connect".equals(api) || "legacy_connect".equals(api)) {
                    return SearchResult.found(visited,
                            "This namespace-owned connect HOC is deprecated in 9.3; choose hooks or the explicit legacy_connect compatibility alias and test HOC semantics");
                }
                if ("batch".equals(api)) {
                    return SearchResult.found(visited,
                            "React Redux 9 batch is a deprecated no-op under React 18 automatic batching; verify scheduling before removal");
                }
                return visited;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (augmentation && "DefaultRootState".equals(visited.getSimpleName())) {
                    return SearchResult.found(visited,
                            "DefaultRootState module augmentation was removed in React Redux 8; export RootState from the store and type selectors/hooks explicitly instead");
                }
                return visited;
            }

            private void scan(JS.CompilationUnit cu, ExecutionContext ctx, String source) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = ReactReduxSupport.moduleName(visited);
                        if (ReactReduxSupport.PACKAGE.equals(module) || ReactReduxSupport.isAggregateEntry(module)) {
                            addAlias(visited, "Provider", providers);
                            addAlias(visited, "connect", connects);
                            addAlias(visited, "legacy_connect", connects);
                            addAlias(visited, "useSelector", selectors);
                            JS.ImportClause clause = visited.getImportClause();
                            if (clause != null && clause.getNamedBindings() != null &&
                                !(clause.getNamedBindings() instanceof JS.NamedImports)) {
                                String namespace = ReactReduxSupport.expressionName(clause.getNamedBindings());
                                if (!namespace.isEmpty()) namespaces.add(namespace);
                            }
                        }
                        if ("react-dom/client".equals(module) &&
                            ReactReduxSupport.importedAlias(visited, "hydrateRoot") != null) hydration = true;
                        if ("react-dom".equals(module) &&
                            (ReactReduxSupport.importedAlias(visited, "hydrate") != null ||
                             ReactReduxSupport.importedAlias(visited, "hydrateRoot") != null)) hydration = true;
                        JS.ImportClause clause = visited.getImportClause();
                        if ("react-dom".equals(module) && clause != null && clause.getName() != null &&
                            source.matches("(?s).*\\b" + Pattern.quote(clause.getName().getSimpleName()) +
                                           "\\s*\\.\\s*hydrate(?:Root)?\\s*\\(.*")) hydration = true;
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                                      ExecutionContext scanCtx) {
                        J.MethodDeclaration visited = super.visitMethodDeclaration(method, scanCtx);
                        declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                                    ExecutionContext scanCtx) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                        declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                                   ExecutionContext scanCtx) {
                        JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, scanCtx);
                        declarations.merge(visited.getName().getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private void addAlias(JS.Import declaration, String imported, Set<String> accumulator) {
                String alias = ReactReduxSupport.importedAlias(declaration, imported);
                if (alias != null && !alias.isEmpty()) accumulator.add(alias);
            }

            private boolean owned(Set<String> aliases, String name) {
                return aliases.contains(name) && declarations.getOrDefault(name, 0) == 0;
            }

            private boolean isArgument(J.MethodInvocation call, J.NewClass object, int index) {
                return call.getSelect() == null && call.getArguments().size() > index &&
                       call.getArguments().get(index).getId().equals(object.getId());
            }

            private List<Expression> markLoaderArguments(List<Expression> arguments) {
                if (arguments.size() != 1 || !(arguments.get(0) instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String module) || !ReactReduxSupport.isPackageModule(module)) {
                    return arguments;
                }
                String message = !ReactReduxSupport.PUBLIC_ENTRIES.contains(module) ?
                        "React Redux 9 package exports block this dynamic/CommonJS deep entry; load the public root or /alternate-renderers API" :
                        serverOwned && !module.endsWith("/package.json") ?
                                "React Redux hooks/context cannot be loaded from an explicit React Server Component boundary" : null;
                if (message == null) return arguments;
                List<Expression> marked = new ArrayList<>(arguments);
                marked.set(0, SearchResult.found(literal, message));
                return marked;
            }
        };
    }

    private static String removedMessage(String api) {
        return switch (api) {
            case "connectAdvanced" -> "connectAdvanced was removed in React Redux 8; redesign the custom selector factory with supported connect options or hooks and preserve subscription, ownProps, equality, ref, and static semantics";
            case "DefaultRootState" -> "DefaultRootState was removed in React Redux 8; export RootState from the store and use typed hooks or explicit connect state generics";
            case "RootStateOrAny" -> "RootStateOrAny depended on the removed global DefaultRootState fallback; replace it with an application-owned RootState or an explicit unknown boundary";
            default -> "Removed React Redux API";
        };
    }
}
