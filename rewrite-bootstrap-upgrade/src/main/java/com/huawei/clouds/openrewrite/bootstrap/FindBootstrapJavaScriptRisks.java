package com.huawei.clouds.openrewrite.bootstrap;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Marks jQuery, removed/internal API, plugin option, event, Popper, and executable-config decisions. */
public final class FindBootstrapJavaScriptRisks extends Recipe {
    private static final Set<String> POPPER_PLUGINS = Set.of("Dropdown", "Tooltip", "Popover");

    @Override
    public String getDisplayName() {
        return "Find Bootstrap 5 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks owned jQuery plugins, removed util/internal APIs, old data selectors, changed plugin options, Popper integrations, and physical build aliases.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean bootstrapFile;
            private boolean config;
            private Set<String> jqueryAliases = Set.of();
            private Map<String, String> pluginAliases = Map.of();
            private Map<String, Integer> declarations = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!BootstrapSupport.isProjectPath(cu.getSourcePath())) return cu;
                boolean oldBootstrap = bootstrapFile;
                boolean oldConfig = config;
                Set<String> oldJquery = jqueryAliases;
                Map<String, String> oldPlugins = pluginAliases;
                Map<String, Integer> oldDeclarations = declarations;
                bootstrapFile = false;
                config = BootstrapSupport.isExecutableConfig(cu.getSourcePath());
                jqueryAliases = new HashSet<>();
                pluginAliases = new HashMap<>();
                declarations = inventory(cu, ctx);
                scanImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                bootstrapFile = oldBootstrap;
                config = oldConfig;
                jqueryAliases = oldJquery;
                pluginAliases = oldPlugins;
                declarations = oldDeclarations;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = BootstrapSupport.moduleName(visited);
                if (("bootstrap/js/dist/util".equals(module) || "bootstrap/js/dist/util.js".equals(module)) &&
                    visited.getImportClause() != null) {
                    return SearchResult.found(visited,
                            "Bootstrap 5 removed util.js and integrated helpers into plugins; replace imported helper contracts with public platform or component APIs and test transitions and selectors");
                }
                if (module.startsWith("bootstrap/js/src/")) {
                    return SearchResult.found(visited,
                            "This import binds to unpublished Bootstrap source internals; use bootstrap or a documented bootstrap/js/dist component and verify ESM, Popper, tree-shaking, and updates");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (bootstrapFile && BootstrapSupport.JQUERY_PLUGINS.contains(visited.getSimpleName()) &&
                    jqueryObject(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "Bootstrap 5 dropped jQuery plugins; instantiate the matching Bootstrap class, translate commands/config/events, retain unrelated jQuery deliberately, and dispose instances on teardown");
                }
                if (bootstrapFile && Set.of("on", "one", "off", "trigger").contains(visited.getSimpleName()) &&
                    jqueryObject(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "Bootstrap 5 emits native CustomEvent-based lifecycle events; verify namespace, target/currentTarget, relatedTarget, cancellation, delegation, and listener cleanup");
                }
                if ("_getInstance".equals(visited.getSimpleName()) && ownedPlugin(visited.getSelect(), null)) {
                    return SearchResult.found(visited,
                            "Bootstrap 5 removed the underscore from public static methods; deterministic migration handles imported plugins, otherwise replace with getInstance and verify null/disposal behavior");
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String plugin = importedPlugin(visited.getClazz());
                if (plugin != null && POPPER_PLUGINS.contains(plugin)) {
                    return SearchResult.found(visited,
                            plugin + " uses Popper 2 and changed placement, boundary, fallback, offset, event, and disposal behavior; verify overflow containers, RTL, keyboard/touch, and production bundles");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = BootstrapSupport.propertyName(visited.getName());
                String plugin = enclosingPlugin(property);
                if ("Dropdown".equals(plugin) && "flip".equals(name)) {
                    return SearchResult.found(visited,
                            "Bootstrap 5 removed Dropdown flip in favor of Popper fallbackPlacements/modifiers; choose explicit overflow behavior and test every placement");
                }
                if ("ScrollSpy".equals(plugin) && "offset".equals(name)) {
                    return SearchResult.found(visited,
                            "Bootstrap 5.2 rewrote ScrollSpy with IntersectionObserver and deprecated offset; select rootMargin/threshold behavior and test nested scrolling and activation");
                }
                if (plugin != null && POPPER_PLUGINS.contains(plugin) &&
                    Set.of("boundary", "fallbackPlacement", "whiteList").contains(name)) {
                    return SearchResult.found(visited,
                            "This Bootstrap 4 option changed under Bootstrap 5 and Popper 2; migrate to the target option contract and verify placement plus sanitized content security");
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value)) return visited;
                if (bootstrapFile && value.matches(".*\\[data-(?:toggle|target|dismiss|ride|spy)(?:[=\\]].*)?")) {
                    return SearchResult.found(visited,
                            "This selector targets a Bootstrap 3/4 data attribute; change only Bootstrap-owned selectors to data-bs-* and verify delegation and dynamically generated markup");
                }
                if (config && (value.contains("bootstrap/js/src/") || value.contains("bootstrap/dist/js/"))) {
                    return SearchResult.found(visited,
                            "This executable build alias pins Bootstrap internals or a physical bundle; verify public entry, ESM/UMD, Popper inclusion, tree-shaking, tests, and production chunks");
                }
                return visited;
            }

            private boolean jqueryObject(Expression expression) {
                if (!(expression instanceof J.MethodInvocation call)) return false;
                return jqueryAliases.contains(call.getSimpleName()) &&
                       declarations.getOrDefault(call.getSimpleName(), 0) == 0;
            }

            private boolean ownedPlugin(org.openrewrite.Tree expression, Set<String> allowed) {
                String imported = importedPlugin(expression);
                return imported != null && (allowed == null || allowed.contains(imported));
            }

            private String importedPlugin(org.openrewrite.Tree expression) {
                if (!(expression instanceof J.Identifier identifier) ||
                    declarations.getOrDefault(identifier.getSimpleName(), 0) != 0) return null;
                return pluginAliases.get(identifier.getSimpleName());
            }

            private String enclosingPlugin(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(s -> s.getId().equals(property.getId()))) return null;
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.NewClass outer && outer.getClazz() != null &&
                        outer.getArguments().stream().anyMatch(arg -> arg.getId().equals(object.getId()))) {
                        return importedPlugin(outer.getClazz());
                    }
                    cursor = cursor.getParent();
                }
                return null;
            }

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = BootstrapSupport.moduleName(visited);
                        JS.ImportClause clause = visited.getImportClause();
                        if (BootstrapSupport.bootstrapModule(module)) bootstrapFile = true;
                        if ("jquery".equals(module) && clause != null) {
                            if (clause.getName() != null) jqueryAliases.add(clause.getName().getSimpleName());
                        }
                        if (BootstrapSupport.PACKAGE.equals(module) && clause != null &&
                            clause.getNamedBindings() instanceof JS.NamedImports named) {
                            for (JS.ImportSpecifier specifier : named.getElements()) {
                                String imported = BootstrapSupport.importedName(specifier);
                                if (BootstrapSupport.PLUGINS.contains(imported)) {
                                    pluginAliases.put(BootstrapSupport.localName(specifier), imported);
                                }
                            }
                        } else if (module.startsWith("bootstrap/js/dist/") && clause != null && clause.getName() != null) {
                            String tail = module.substring("bootstrap/js/dist/".length()).replace(".js", "");
                            if (!tail.isEmpty()) {
                                String plugin = Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
                                if (BootstrapSupport.PLUGINS.contains(plugin)) {
                                    pluginAliases.put(clause.getName().getSimpleName(), plugin);
                                }
                            }
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private Map<String, Integer> inventory(JS.CompilationUnit cu, ExecutionContext ctx) {
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
