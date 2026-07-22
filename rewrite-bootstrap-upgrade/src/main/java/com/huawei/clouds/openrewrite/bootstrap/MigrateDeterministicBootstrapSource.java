package com.huawei.clouds.openrewrite.bootstrap;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies documented JavaScript changes only when imported Bootstrap ownership is proven. */
public final class MigrateDeterministicBootstrapSource extends Recipe {
    private static final Set<String> OPTION_PLUGINS = Set.of("Tooltip", "Popover");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Bootstrap 5 JavaScript and TypeScript";
    }

    @Override
    public String getDescription() {
        return "Removes obsolete side-effect util imports, renames owned _getInstance calls, and renames Tooltip/Popover whiteList options to allowList.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> pluginAliases = Map.of();
            private Map<String, Integer> declarations = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!BootstrapSupport.isProjectPath(cu.getSourcePath())) return cu;
                boolean removesLeadingUtil = !cu.getStatements().isEmpty() &&
                        cu.getStatements().get(0) instanceof JS.Import first && obsoleteSideEffectUtil(first);
                Map<String, String> oldAliases = pluginAliases;
                Map<String, Integer> oldDeclarations = declarations;
                pluginAliases = new HashMap<>();
                declarations = inventory(cu, ctx);
                scanImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                if (removesLeadingUtil && !visited.getStatements().isEmpty()) {
                    List<Statement> statements = new ArrayList<>(visited.getStatements());
                    statements.set(0, (Statement) statements.get(0).withPrefix(Space.EMPTY));
                    visited = visited.withStatements(statements);
                }
                pluginAliases = oldAliases;
                declarations = oldDeclarations;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = BootstrapSupport.moduleName(visited);
                if (obsoleteSideEffectUtil(visited)) return null;
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if ("_getInstance".equals(visited.getSimpleName()) && ownedPlugin(visited.getSelect(), null)) {
                    return visited.withName(visited.getName().withSimpleName("getInstance"));
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!"whiteList".equals(BootstrapSupport.propertyName(visited.getName())) ||
                    !insideOwnedOptions(property, OPTION_PLUGINS) || hasSibling(property, "allowList")) return visited;
                return visited.withName(rename(visited.getName(), "allowList"));
            }

            private boolean insideOwnedOptions(JS.PropertyAssignment property, Set<String> plugins) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(s -> s.getId().equals(property.getId())) ||
                    object.getBody().getStatements().stream().anyMatch(JS.Spread.class::isInstance)) return false;
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.NewClass outer && outer.getClazz() != null &&
                        outer.getArguments().stream().anyMatch(arg -> arg.getId().equals(object.getId()))) {
                        return ownedPlugin(outer.getClazz(), plugins);
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean ownedPlugin(org.openrewrite.Tree expression, Set<String> allowed) {
                if (!(expression instanceof J.Identifier identifier)) return false;
                String imported = pluginAliases.get(identifier.getSimpleName());
                return imported != null && (allowed == null || allowed.contains(imported)) &&
                       declarations.getOrDefault(identifier.getSimpleName(), 0) == 0;
            }

            private Expression rename(Expression name, String replacement) {
                if (name instanceof J.Identifier identifier) return identifier.withSimpleName(replacement);
                if (name instanceof J.Literal literal) {
                    String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                            ? "\"" : "'";
                    return literal.withValue(replacement).withValueSource(quote + replacement + quote);
                }
                return name;
            }

            private boolean hasSibling(JS.PropertyAssignment property, String name) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                return object != null && object.getBody() != null && object.getBody().getStatements().stream()
                        .filter(statement -> !statement.getId().equals(property.getId()))
                        .filter(JS.PropertyAssignment.class::isInstance)
                        .map(JS.PropertyAssignment.class::cast)
                        .anyMatch(sibling -> name.equals(BootstrapSupport.propertyName(sibling.getName())));
            }

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = BootstrapSupport.moduleName(visited);
                        if (BootstrapSupport.PACKAGE.equals(module)) {
                            JS.ImportClause clause = visited.getImportClause();
                            if (clause != null && clause.getNamedBindings() instanceof JS.NamedImports named) {
                                for (JS.ImportSpecifier specifier : named.getElements()) {
                                    String imported = BootstrapSupport.importedName(specifier);
                                    if (BootstrapSupport.PLUGINS.contains(imported)) {
                                        pluginAliases.put(BootstrapSupport.localName(specifier), imported);
                                    }
                                }
                            }
                        } else if (module.startsWith("bootstrap/js/dist/")) {
                            String tail = module.substring("bootstrap/js/dist/".length()).replace(".js", "");
                            String plugin = Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
                            JS.ImportClause clause = visited.getImportClause();
                            if (BootstrapSupport.PLUGINS.contains(plugin) && clause != null && clause.getName() != null) {
                                pluginAliases.put(clause.getName().getSimpleName(), plugin);
                            }
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean obsoleteSideEffectUtil(JS.Import declaration) {
                String module = BootstrapSupport.moduleName(declaration);
                return ("bootstrap/js/dist/util".equals(module) || "bootstrap/js/dist/util.js".equals(module)) &&
                       declaration.getImportClause() == null;
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
