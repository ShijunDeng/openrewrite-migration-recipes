package com.huawei.clouds.openrewrite.elementplusiconsvue;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Mark source boundaries whose correct change depends on application ownership or runtime behavior. */
public final class FindElementPlusIconsVueSourceRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find @element-plus/icons-vue 2.3.2 source risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed deep component layouts, default/namespace imports, global plugin registration, " +
               "manual component registration, dynamic resolution, CommonJS, SSR, and tree-shaking boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> iconBindings = Set.of();
            private Set<String> namespaceBindings = Set.of();
            private Set<String> globalBindings = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!ElementPlusIconsVueSupport.isProjectPath(cu.getSourcePath())) return cu;
                Set<String> previousIcons = iconBindings;
                Set<String> previousNamespaces = namespaceBindings;
                Set<String> previousGlobals = globalBindings;
                iconBindings = new HashSet<>();
                namespaceBindings = new HashSet<>();
                globalBindings = new HashSet<>();
                scan(cu, ctx);
                removeShadowedBindings(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                iconBindings = previousIcons;
                namespaceBindings = previousNamespaces;
                globalBindings = previousGlobals;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = ElementPlusIconsVueSupport.moduleName(visited);
                if (!ElementPlusIconsVueSupport.packageReference(module)) return visited;
                String printed = visited.printTrimmed(getCursor());
                if (ElementPlusIconsVueSupport.legacyComponentEntry(module)) {
                    return SearchResult.found(visited, "The 0.2.x dist/es and dist/lib per-icon layout is absent in 2.3.2; replace this default deep import with the matching named root export while preserving its local alias and lazy-loading intent");
                }
                if ((ElementPlusIconsVueSupport.PACKAGE.equals(module) ||
                     ElementPlusIconsVueSupport.PACKAGE.equals(
                             ElementPlusIconsVueSupport.DETERMINISTIC_ENTRIES.get(module))) &&
                    visited.getImportClause() != null && visited.getImportClause().getName() != null) {
                    return SearchResult.found(visited, "@element-plus/icons-vue aggregate entries have no default icon export; choose named icon exports or the explicit /global plugin");
                }
                if (ElementPlusIconsVueSupport.PACKAGE.equals(module) && printed.startsWith("import *")) {
                    return SearchResult.found(visited, "Namespace-importing every icon creates a bundle/tree-shaking and SSR boundary; keep it only for deliberate manual global registration, otherwise import used icons by name");
                }
                if ((ElementPlusIconsVueSupport.PACKAGE + "/global").equals(module) || module.contains("/dist/global")) {
                    return SearchResult.found(visited, "The global plugin registers every icon; verify prefix collisions, component names, bundle budget, production tree shaking, SSR isolation, and hydration before retaining it");
                }
                if (module.startsWith(ElementPlusIconsVueSupport.PACKAGE + "/") &&
                    !ElementPlusIconsVueSupport.DETERMINISTIC_ENTRIES.containsKey(module)) {
                    return SearchResult.found(visited, "This package-internal or unknown subpath is not a stable 2.3.2 icon entry; use named root exports or the documented /global plugin");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null && "require".equals(visited.getSimpleName()) &&
                    visited.getArguments().size() == 1) {
                    String module = ElementPlusIconsVueSupport.stringLiteral(visited.getArguments().get(0));
                    if (ElementPlusIconsVueSupport.packageReference(module)) {
                        return SearchResult.found(visited, "Verify CommonJS interop against the 2.3.2 conditional exports; prefer a static ESM named import unless the runtime boundary is intentionally CommonJS");
                    }
                }
                String select = visited.getSelect() == null ? "" : visited.getSelect().toString().trim();
                if ("component".equals(visited.getSimpleName()) && visited.getArguments().size() >= 2 &&
                    iconBindings.contains(visited.getArguments().get(1).toString().trim())) {
                    return SearchResult.found(visited, "Manual icon registration is application-owned; verify the registered string, duplicate/collision policy, SSR app isolation, hydration, and template references");
                }
                if ("use".equals(visited.getSimpleName()) && !visited.getArguments().isEmpty() &&
                    globalBindings.contains(visited.getArguments().get(0).toString().trim())) {
                    return SearchResult.found(visited, "Verify /global's default ElIcon prefix or explicit prefix option, registration order, bundle size, SSR app isolation, and hydration");
                }
                if ("entries".equals(visited.getSimpleName()) && "Object".equals(select) &&
                    !visited.getArguments().isEmpty() &&
                    namespaceBindings.contains(visited.getArguments().get(0).toString().trim())) {
                    return SearchResult.found(visited, "Manual all-icon enumeration retains the whole namespace; choose explicit local registration or /global with an intentional prefix and bundle budget");
                }
                if ("resolveComponent".equals(visited.getSimpleName()) && !visited.getArguments().isEmpty()) {
                    String name = ElementPlusIconsVueSupport.stringLiteral(visited.getArguments().get(0));
                    if (name.startsWith("ElIcon") || name.startsWith("el-icon-")) {
                        return SearchResult.found(visited, "String-based icon resolution depends on registration prefix/casing and SSR hydration; replace with a direct component reference when possible");
                    }
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (!"import".equals(visited.getFunction().toString().trim()) || visited.getArguments().size() != 1) {
                    return visited;
                }
                String module = ElementPlusIconsVueSupport.stringLiteral(visited.getArguments().get(0));
                return ElementPlusIconsVueSupport.packageReference(module)
                        ? SearchResult.found(visited, "Dynamic icon loading must target a published 2.3.2 export and preserve chunking, error handling, SSR execution, preload, and hydration behavior")
                        : visited;
            }

            private void scan(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = ElementPlusIconsVueSupport.moduleName(visited);
                        if (!ElementPlusIconsVueSupport.packageReference(module) || visited.getImportClause() == null) {
                            return visited;
                        }
                        JS.ImportClause clause = visited.getImportClause();
                        if (clause.getNamedBindings() instanceof JS.NamedImports named) {
                            for (JS.ImportSpecifier specifier : named.getElements()) {
                                if (specifier.getSpecifier() instanceof J.Identifier identifier) {
                                    iconBindings.add(identifier.getSimpleName());
                                } else if (specifier.getSpecifier() instanceof JS.Alias alias &&
                                           alias.getAlias() instanceof J.Identifier local) {
                                    iconBindings.add(local.getSimpleName());
                                }
                            }
                        }
                        String printed = visited.printTrimmed(getCursor());
                        if (printed.startsWith("import *")) {
                            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\bas\\s+([A-Za-z_$][\\w$]*)").matcher(printed);
                            if (matcher.find()) namespaceBindings.add(matcher.group(1));
                        }
                        if ((ElementPlusIconsVueSupport.PACKAGE + "/global").equals(module) || module.contains("/dist/global")) {
                            if (clause.getName() != null) globalBindings.add(clause.getName().getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private void removeShadowedBindings(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> owned = new HashSet<>(iconBindings);
                owned.addAll(namespaceBindings);
                owned.addAll(globalBindings);
                Set<String> shadowed = new HashSet<>();
                String source = cu.printAll();
                for (String local : owned) {
                    if (java.util.regex.Pattern.compile(
                            "(?m)\\b(?:class|interface|enum|type|const|let|var|function)\\s+" +
                            java.util.regex.Pattern.quote(local) + "\\b").matcher(source).find()) {
                        shadowed.add(local);
                    }
                }
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (owned.contains(visited.getSimpleName())) shadowed.add(visited.getSimpleName());
                        return visited;
                    }
                }.visit(cu, ctx);
                iconBindings.removeAll(shadowed);
                namespaceBindings.removeAll(shadowed);
                globalBindings.removeAll(shadowed);
            }
        };
    }
}
