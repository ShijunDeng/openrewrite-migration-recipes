package com.huawei.clouds.openrewrite.chalk;

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

/** Marks exact JavaScript/TypeScript constructs that cross Chalk 3/4/5 boundaries. */
public final class FindChalk5JavaScriptRisks extends Recipe {
    static final String ESM_MESSAGE =
            "Chalk 5.6.2 is pure ESM; replace CommonJS/import-equals loading and verify package type, TypeScript 4.7+ NodeNext settings, test transforms, bundler, CLI, SSR, Electron, and mocks";
    static final String TEMPLATE_MESSAGE =
            "Chalk 5 removed tagged-template parsing; migrate this exact tag to chalk-template or rewrite it as explicit Chalk calls while preserving interpolation escaping and nested styles";
    static final String COLOR_MODEL_MESSAGE =
            "Chalk 5 removed keyword/hsl/hsv/hwb/ansi color models; select an explicit supported rgb/hex/ansi256 conversion and snapshot terminal output at color levels 0-3";
    static final String DYNAMIC_IMPORT_MESSAGE =
            "Dynamic import can bridge a CommonJS caller to Chalk 5 ESM asynchronously; preserve Promise/await timing, error handling, startup ordering, mocks, bundler transforms, and the default/named export shape";

    private static final Set<String> LEGACY_MEMBERS = Set.of(
            "Instance", "constructor", "stderr", "supportsColor", "enabled", "template");
    private static final Set<String> LEGACY_TYPES = Set.of(
            "Level", "Options", "ColorSupport", "Modifiers", "ForegroundColor", "BackgroundColor", "Color");
    private static final Set<String> DEPRECATED_NAMED_EXPORTS = Set.of(
            "modifiers", "foregroundColors", "backgroundColors", "colors",
            "Modifiers", "ForegroundColor", "BackgroundColor", "Color");

    @Override
    public String getDisplayName() {
        return "Find Chalk 5 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks CommonJS/deep/namespace loading, removed default-export members, tagged templates, removed color models, legacy types, and deprecated named exports at their exact AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> instances = Set.of();
            private Set<String> namespaces = Set.of();
            private Set<String> requiredInstances = Set.of();
            private Set<String> typeInstances = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!ChalkSupport.isProjectPath(cu.getSourcePath())) return cu;
                Set<String> oldInstances = instances;
                Set<String> oldNamespaces = namespaces;
                Set<String> oldRequiredInstances = requiredInstances;
                Set<String> oldTypeInstances = typeInstances;
                Map<String, Integer> oldDeclarationCounts = declarationCounts;
                instances = new HashSet<>();
                namespaces = new HashSet<>();
                requiredInstances = new HashSet<>();
                typeInstances = new HashSet<>();
                declarationCounts = findDeclarationCounts(cu);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                instances = oldInstances;
                namespaces = oldNamespaces;
                requiredInstances = oldRequiredInstances;
                typeInstances = oldTypeInstances;
                declarationCounts = oldDeclarationCounts;
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                if (rootChalkLoading(visited.getInitializer())) {
                    requiredInstances.add(visited.getSimpleName());
                }
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = ChalkSupport.moduleName(visited);
                if (!ChalkSupport.isChalkModule(module)) return visited;
                if (!ChalkSupport.PACKAGE.equals(module)) {
                    return SearchResult.found(visited,
                            "Chalk 5 exports only the package root; replace this deep import with public default/named exports or own the chalk-template dependency explicitly");
                }
                JS.ImportClause clause = visited.getImportClause();
                if (clause == null) return visited;
                boolean typeOnly = clause.isTypeOnly();
                if (typeOnly) {
                    if (clause.getName() != null) typeInstances.add(clause.getName().getSimpleName());
                    String namespace = ChalkSupport.namespaceAlias(clause.getNamedBindings());
                    if (namespace != null) typeInstances.add(namespace);
                    return visited;
                }
                if (clause.getName() != null) instances.add(clause.getName().getSimpleName());
                String namespace = ChalkSupport.namespaceAlias(clause.getNamedBindings());
                if (namespace != null) {
                    namespaces.add(namespace);
                    return SearchResult.found(visited,
                            "Chalk 5 exposes an ESM default instance and named exports; namespace-as-call/style interop is not equivalent, so migrate each namespace use deliberately");
                }
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                if (declaration != null && ChalkSupport.PACKAGE.equals(ChalkSupport.moduleName(declaration)) &&
                    DEPRECATED_NAMED_EXPORTS.contains(ChalkSupport.importedName(visited))) {
                    return SearchResult.found(visited,
                            "This Chalk named export/type is deprecated in 5.6.2; migrate to modifierNames/foregroundColorNames/backgroundColorNames/colorNames and the corresponding *Name type before the next major");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (rootLoading(visited)) return markLoading(visited);
                if (ChalkSupport.REMOVED_COLOR_MODELS.contains(visited.getSimpleName()) && owned(visited.getSelect())) {
                    return SearchResult.found(visited, COLOR_MODEL_MESSAGE);
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String name = ChalkSupport.expressionName(visited.getFunction());
                if (("require".equals(name) || "import".equals(name)) && !visited.getArguments().isEmpty() &&
                    ChalkSupport.isChalkModule(ChalkSupport.stringLiteral(visited.getArguments().get(0)))) {
                    return markLoading(visited);
                }
                return visited;
            }

            @Override
            public JS.TaggedTemplateExpression visitTaggedTemplateExpression(
                    JS.TaggedTemplateExpression expression, ExecutionContext ctx) {
                JS.TaggedTemplateExpression visited = super.visitTaggedTemplateExpression(expression, ctx);
                return owned(visited.getTag()) ? SearchResult.found(visited, TEMPLATE_MESSAGE) : visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                boolean runtimeOwned = owned(visited);
                boolean typeOwned = ownedType(visited);
                if (!runtimeOwned && !typeOwned) return visited;
                String member = visited.getSimpleName();
                if (runtimeOwned && ChalkSupport.REMOVED_COLOR_MODELS.contains(member)) {
                    return SearchResult.found(visited, COLOR_MODEL_MESSAGE);
                }
                if (runtimeOwned && LEGACY_MEMBERS.contains(member)) {
                    String message = switch (member) {
                        case "Instance" -> "chalk.Instance moved off the default export; import the named Chalk constructor and verify instance level isolation";
                        case "constructor" -> "chalk.constructor was removed after Chalk 2; use the named Chalk constructor and migrate enabled options to level semantics";
                        case "stderr" -> "chalk.stderr moved to the named chalkStderr export; preserve independent stderr color detection";
                        case "supportsColor" -> "chalk.supportsColor moved to the named supportsColor export; preserve null handling and level checks";
                        case "enabled" -> "Chalk 3 removed enabled; use level > 0 for reads and level = 0 for explicit disablement without converting dynamic/true policy blindly";
                        default -> TEMPLATE_MESSAGE;
                    };
                    return SearchResult.found(visited, message);
                }
                if (LEGACY_TYPES.contains(member)) {
                    return SearchResult.found(visited,
                            "This Chalk 2/4 namespace type changed; use ChalkInstance, Options, ColorSupportLevel, ColorInfo, or the corresponding *Name type exported by 5.6.2");
                }
                return visited;
            }

            private boolean owned(Expression expression) {
                String root = ChalkSupport.rootIdentifier(expression);
                if (root == null) return false;
                int declarations = declarationCounts.getOrDefault(root, 0);
                if (requiredInstances.contains(root)) {
                    // The require binding itself is one declaration. Any second declaration/parameter is
                    // treated as a possible shadow, so the recipe stays deliberately conservative.
                    return declarations == 1;
                }
                return (instances.contains(root) || namespaces.contains(root)) && declarations == 0;
            }

            private boolean ownedType(Expression expression) {
                String root = ChalkSupport.rootIdentifier(expression);
                return root != null && typeInstances.contains(root) &&
                       declarationCounts.getOrDefault(root, 0) == 0;
            }

            private boolean rootLoading(J.MethodInvocation invocation) {
                return invocation.getSelect() == null &&
                       ("require".equals(invocation.getSimpleName()) || "import".equals(invocation.getSimpleName())) &&
                       !invocation.getArguments().isEmpty() &&
                       ChalkSupport.isChalkModule(ChalkSupport.stringLiteral(invocation.getArguments().get(0)));
            }

            private boolean rootChalkLoading(Expression expression) {
                if (expression instanceof J.MethodInvocation invocation) {
                    return rootLoading(invocation) &&
                           ChalkSupport.PACKAGE.equals(ChalkSupport.stringLiteral(invocation.getArguments().get(0)));
                }
                if (expression instanceof JS.FunctionCall call) {
                    String name = ChalkSupport.expressionName(call.getFunction());
                    return "require".equals(name) && !call.getArguments().isEmpty() &&
                           ChalkSupport.PACKAGE.equals(ChalkSupport.stringLiteral(call.getArguments().get(0)));
                }
                return false;
            }

            private <T extends J> T markLoading(T call) {
                String module = call instanceof J.MethodInvocation invocation
                        ? ChalkSupport.stringLiteral(invocation.getArguments().get(0))
                        : ChalkSupport.stringLiteral(((JS.FunctionCall) call).getArguments().get(0));
                String callName = call instanceof J.MethodInvocation invocation ? invocation.getSimpleName() :
                        ChalkSupport.expressionName(((JS.FunctionCall) call).getFunction());
                if ("import".equals(callName) && ChalkSupport.PACKAGE.equals(module)) {
                    return SearchResult.found(call, DYNAMIC_IMPORT_MESSAGE);
                }
                return SearchResult.found(call, ChalkSupport.PACKAGE.equals(module) ? ESM_MESSAGE :
                        "Chalk 5 exports only the package root; this deep CommonJS/dynamic load must move to public ESM exports or an explicit chalk-template dependency");
            }

            private Map<String, Integer> findDeclarationCounts(JS.CompilationUnit cu) {
                Map<String, Integer> counts = new HashMap<>();
                new JavaScriptIsoVisitor<Map<String, Integer>>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, Map<String, Integer> accumulator) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, accumulator);
                        accumulator.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                                      Map<String, Integer> accumulator) {
                        J.MethodDeclaration visited = super.visitMethodDeclaration(method, accumulator);
                        accumulator.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                                    Map<String, Integer> accumulator) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(declaration, accumulator);
                        accumulator.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                                   Map<String, Integer> accumulator) {
                        JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, accumulator);
                        accumulator.merge(visited.getName().getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, counts);
                return counts;
            }
        };
    }
}
