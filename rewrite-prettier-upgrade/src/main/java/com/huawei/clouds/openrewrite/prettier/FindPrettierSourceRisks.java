package com.huawei.clouds.openrewrite.prettier;

import org.openrewrite.Cursor;
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
import java.util.UUID;

/** Marks Prettier 3 source contracts that cannot be changed without caller or plugin semantics. */
public final class FindPrettierSourceRisks extends Recipe {
    private static final Set<String> SYNC_REMOVED = Set.of("resolveConfig", "resolveConfigFile", "getFileInfo");
    private static final Set<String> REMOVED_DOC_APIS = Set.of(
            "concat", "getDocParts", "propagateBreaks", "cleanDoc", "getDocType", "printDocToDebug");
    private static final String ASYNC =
            "Prettier 3 made this public API asynchronous; await/return its Promise and propagate async through comparisons, writes, callbacks, tests, and errors";
    private static final String SYNC =
            "Prettier 3 removed this .sync API; migrate the complete caller to the asynchronous API and preserve config/file-info error and control-flow behavior";
    private static final String LAYOUT =
            "Prettier 3 reorganized physical parser, ESM, and bin files; select a target 3.6.2 public export and verify import/require, bundler, mock, and runtime resolution";
    private static final String PLUGIN =
            "Review this Prettier plugin contract for 3.x: parsers/preprocess may be async, embed has a new signature, parse no longer receives the parsers map, print must return a Doc, and plugins load as ESM with explicit paths";
    private static final String DOC =
            "This Prettier doc/plugin API was removed or changed in 3.x (including builders.concat and internal debug helpers); migrate to public Doc builders and verify printed output snapshots";

    @Override
    public String getDisplayName() {
        return "Find Prettier 3 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks import-owned asynchronous APIs, removed sync/doc APIs, physical entries, and plugin contracts that require application decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> namedApis = Map.of();
            private Set<String> namespaces = Set.of();
            private Set<String> commonJsNamespaces = Set.of();
            private Map<String, Integer> declarations = Map.of();
            private Set<UUID> pluginObjects = Set.of();
            private boolean ownsPrettier;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!PrettierSupport.projectPath(cu.getSourcePath())) return cu;
                Map<String, String> oldNamed = namedApis;
                Set<String> oldNamespaces = namespaces;
                Set<String> oldCommonJs = commonJsNamespaces;
                Map<String, Integer> oldDeclarations = declarations;
                Set<UUID> oldPluginObjects = pluginObjects;
                boolean oldOwns = ownsPrettier;
                namedApis = new HashMap<>();
                namespaces = new HashSet<>();
                commonJsNamespaces = new HashSet<>();
                declarations = inventory(cu, ctx);
                pluginObjects = inventoryPluginObjects(cu, ctx);
                ownsPrettier = false;
                scanOwnership(cu, ctx);
                JS.CompilationUnit visited = ownsPrettier || !pluginObjects.isEmpty() || hasExportedPluginBindings(cu, ctx)
                        ? super.visitJsCompilationUnit(cu, ctx) : cu;
                namedApis = oldNamed;
                namespaces = oldNamespaces;
                commonJsNamespaces = oldCommonJs;
                declarations = oldDeclarations;
                pluginObjects = oldPluginObjects;
                ownsPrettier = oldOwns;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = PrettierSupport.moduleName(visited);
                if (!physicalEntry(module) || !(visited.getModuleSpecifier() instanceof J.Literal literal)) {
                    return visited;
                }
                return visited.withModuleSpecifier(PrettierSupport.mark(literal, LAYOUT));
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String module) || !physicalEntry(module)) return visited;
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                return invocation != null && module.equals(PrettierSupport.requireModule(invocation))
                        ? PrettierSupport.mark(visited, LAYOUT) : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String api = ownedApi(visited);
                if (api != null && PrettierSupport.ASYNC_APIS.contains(api)) {
                    return PrettierSupport.mark(visited, ASYNC + " (" + api + ")");
                }
                if ("sync".equals(visited.getSimpleName()) && visited.getSelect() instanceof J.FieldAccess owner &&
                    SYNC_REMOVED.contains(owner.getSimpleName()) && ownedNamespace(owner.getTarget())) {
                    return PrettierSupport.mark(visited, SYNC + " (" + owner.getSimpleName() + ".sync)");
                }
                if (REMOVED_DOC_APIS.contains(visited.getSimpleName()) && prettierDocChain(visited.getSelect())) {
                    return PrettierSupport.mark(visited, DOC + " (" + visited.getSimpleName() + ")");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String local = functionName(visited.getFunction());
                String api = declarations.getOrDefault(local, 0) == 0 ? namedApis.get(local) : null;
                return api != null && PrettierSupport.ASYNC_APIS.contains(api)
                        ? PrettierSupport.mark(visited, ASYNC + " (" + api + ")") : visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = PrettierSupport.propertyName(visited.getName());
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || !pluginObjects.contains(object.getId()) || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(property.getId()))) {
                    return visited;
                }
                return Set.of("parsers", "printers").contains(name)
                        ? PrettierSupport.mark(visited, PLUGIN + " (" + name + ")") : visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                if (Set.of("parsers", "printers").contains(visited.getSimpleName()) &&
                    exported(getCursor())) {
                    return PrettierSupport.mark(visited, PLUGIN + " (" + visited.getSimpleName() + ")");
                }
                return visited;
            }

            private String ownedApi(J.MethodInvocation invocation) {
                if (invocation.getSelect() == null) {
                    String local = invocation.getSimpleName();
                    return declarations.getOrDefault(local, 0) == 0 ? namedApis.get(local) : null;
                }
                if (ownedNamespace(invocation.getSelect())) return invocation.getSimpleName();
                return null;
            }

            private boolean ownedNamespace(Expression expression) {
                if (!(expression instanceof J.Identifier identifier)) return false;
                String name = identifier.getSimpleName();
                return namespaces.contains(name) && declarations.getOrDefault(name, 0) == 0 ||
                       commonJsNamespaces.contains(name) && declarations.getOrDefault(name, 0) == 1;
            }

            private boolean prettierDocChain(Expression expression) {
                if (expression instanceof J.Identifier identifier) return ownedNamespace(identifier);
                if (!(expression instanceof J.FieldAccess field)) return false;
                String printed = field.toString().replaceAll("\\s+", "");
                String root = rootIdentifier(field);
                return root != null && ownedNamespaceName(root) &&
                       (printed.contains(".doc.") || printed.endsWith(".doc") || printed.contains(".builders."));
            }

            private String rootIdentifier(Expression expression) {
                if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
                if (expression instanceof J.FieldAccess field) return rootIdentifier(field.getTarget());
                return null;
            }

            private boolean ownedNamespaceName(String name) {
                return namespaces.contains(name) && declarations.getOrDefault(name, 0) == 0 ||
                       commonJsNamespaces.contains(name) && declarations.getOrDefault(name, 0) == 1;
            }

            private void scanOwnership(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = PrettierSupport.moduleName(visited);
                        if (!PrettierSupport.prettierModule(module)) return visited;
                        ownsPrettier = true;
                        if (!PrettierSupport.PACKAGE.equals(module)) return visited;
                        JS.ImportClause clause = visited.getImportClause();
                        if (clause == null) return visited;
                        if (clause.getName() != null) namespaces.add(clause.getName().getSimpleName());
                        if (clause.getNamedBindings() instanceof JS.NamedImports named) {
                            for (JS.ImportSpecifier specifier : named.getElements()) {
                                namedApis.put(PrettierSupport.localName(specifier),
                                        PrettierSupport.importedName(specifier));
                            }
                        } else if (clause.getNamedBindings() instanceof JS.Alias alias &&
                                   alias.getAlias() instanceof J.Identifier identifier) {
                            namespaces.add(identifier.getSimpleName());
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        String module = requireModule(visited.getInitializer());
                        if (PrettierSupport.prettierModule(module)) {
                            ownsPrettier = true;
                            if (PrettierSupport.PACKAGE.equals(module)) {
                                commonJsNamespaces.add(visited.getSimpleName());
                            }
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private String requireModule(Expression initializer) {
                if (initializer instanceof J.MethodInvocation invocation) {
                    return PrettierSupport.requireModule(invocation);
                }
                if (initializer instanceof JS.FunctionCall call && "require".equals(functionName(call.getFunction())) &&
                    call.getArguments().size() == 1 && call.getArguments().get(0) instanceof J.Literal literal &&
                    literal.getValue() instanceof String module) return module;
                return "";
            }

            private String functionName(Expression expression) {
                if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
                if (expression instanceof J.FieldAccess field) return field.getSimpleName();
                return "";
            }

            private Map<String, Integer> inventory(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, Integer> names = new HashMap<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        names.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
                return names;
            }

            private Set<UUID> inventoryPluginObjects(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<UUID> objects = new HashSet<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.ExportDeclaration visitExportDeclaration(JS.ExportDeclaration declaration,
                                                                         ExecutionContext scanCtx) {
                        JS.ExportDeclaration visited = super.visitExportDeclaration(declaration, scanCtx);
                        if (visited.getExportClause() instanceof J.NewClass object && object.getClazz() == null) {
                            objects.add(object.getId());
                        }
                        return visited;
                    }

                    @Override
                    public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext scanCtx) {
                        J.Assignment visited = super.visitAssignment(assignment, scanCtx);
                        if (moduleExports(visited.getVariable()) && visited.getAssignment() instanceof J.NewClass object &&
                            object.getClazz() == null) objects.add(object.getId());
                        return visited;
                    }
                }.visit(cu, ctx);
                return objects;
            }

            private boolean hasExportedPluginBindings(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean[] found = {false};
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (Set.of("parsers", "printers").contains(visited.getSimpleName()) &&
                            exported(getCursor())) found[0] = true;
                        return visited;
                    }
                }.visit(cu, ctx);
                return found[0];
            }

            private boolean moduleExports(Expression expression) {
                return expression instanceof J.FieldAccess field && "exports".equals(field.getSimpleName()) &&
                       field.getTarget() instanceof J.Identifier identifier &&
                       "module".equals(identifier.getSimpleName());
            }

            private boolean exported(Cursor cursor) {
                J.VariableDeclarations declarations = cursor.firstEnclosing(J.VariableDeclarations.class);
                return declarations != null && declarations.getModifiers().stream()
                        .anyMatch(modifier -> "export".equals(modifier.getKeyword()));
            }
        };
    }

    private static boolean physicalEntry(String module) {
        return module != null && (module.startsWith("prettier/parser-") || module.startsWith("prettier/esm/") ||
                                  "prettier/bin-prettier.js".equals(module));
    }
}
