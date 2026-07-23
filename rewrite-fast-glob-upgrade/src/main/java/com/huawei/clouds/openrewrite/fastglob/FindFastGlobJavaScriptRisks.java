package com.huawei.clouds.openrewrite.fastglob;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Marks exact pattern, option, path, filesystem, and execution-mode compatibility boundaries. */
public final class FindFastGlobJavaScriptRisks extends Recipe {
    private static final Set<String> MATCH_OPTIONS = Set.of(
            "baseNameMatch", "braceExpansion", "caseSensitiveMatch", "dot", "extglob", "globstar");
    private static final Set<String> TRAVERSAL_OPTIONS = Set.of(
            "cwd", "deep", "followSymbolicLinks", "suppressErrors", "throwErrorOnBrokenSymbolicLink");
    private static final Set<String> OUTPUT_OPTIONS = Set.of(
            "absolute", "markDirectories", "objectMode", "onlyDirectories", "onlyFiles", "stats", "unique");

    @Override
    public String getDisplayName() {
        return "Find fast-glob 3.3.3 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks matching changes for negatives/dot/braces/baseNameMatch/duplicates/trailing slashes/absolute " +
               "patterns plus Windows paths, custom fs adapters, traversal, output, stream, and concurrency contracts.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> objects = Set.of();
            private Set<String> functions = Set.of();
            private Set<String> declared = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!FastGlobSupport.isSource(cu.getSourcePath())) return cu;
                Set<String> oldObjects = objects;
                Set<String> oldFunctions = functions;
                Set<String> oldDeclared = declared;
                objects = new HashSet<>();
                functions = new HashSet<>();
                declared = declarations(cu);
                scanBindings(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                objects = oldObjects;
                functions = oldFunctions;
                declared = oldDeclared;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = FastGlobJavaScriptSupport.moduleName(visited);
                if (FastGlobJavaScriptSupport.isFastGlobModule(module) && !FastGlobSupport.PACKAGE.equals(module)) {
                    return SearchResult.found(visited,
                            "This fast-glob deep distribution/type import is not a public package-root contract; use documented root exports and verify CJS/ESM interop and bundled declarations");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String required = FastGlobJavaScriptSupport.requireModule(visited);
                if (FastGlobJavaScriptSupport.isFastGlobModule(required) && !FastGlobSupport.PACKAGE.equals(required)) {
                    return SearchResult.found(visited,
                            "This require binds to fast-glob internals; switch to the public package root and verify CJS default/namespace behavior, bundling, and declarations");
                }
                if (!ownedMethod(visited)) return visited;
                String risk = callRisk(visited.getSimpleName(), visited.getArguments());
                return risk == null ? visited : SearchResult.found(visited, risk);
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (!ownedFunction(visited)) return visited;
                String risk = callRisk("async", visited.getArguments());
                return risk == null ? visited : SearchResult.found(visited, risk);
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!insideOwnedDirectOptions(property)) return visited;
                String name = FastGlobJavaScriptSupport.propertyName(visited.getName());
                if ("ignore".equals(name)) {
                    return SearchResult.found(visited,
                            "fast-glob 3.3.x changed negative matching (including dotfiles and absolute negatives); verify ignore pruning, hidden files, cwd/absolute paths, directory exclusions, and array-only public typing against a real fixture tree");
                }
                if ("fs".equals(name)) {
                    return SearchResult.found(visited,
                            "A custom fast-glob fs adapter must expose the required enumerable lstat/readdir methods and preserve callback errors, Dirent/stat shape, symlink behavior, concurrency, and platform paths");
                }
                if ("concurrency".equals(name)) {
                    return SearchResult.found(visited,
                            "fast-glob concurrency shares Node's libuv thread pool; benchmark representative trees and verify file/network/crypto work, resource limits, cancellation, and CI reproducibility");
                }
                if (MATCH_OPTIONS.contains(name)) {
                    return SearchResult.found(visited,
                            "This matching option intersects 3.3 fixes for baseNameMatch with slashes, brace expansion, negative dotfiles, and micromatch 4.0.8; snapshot exact include/exclude results on Linux and Windows");
                }
                if (TRAVERSAL_OPTIONS.contains(name)) {
                    return SearchResult.found(visited,
                            "This traversal option controls cwd depth, external/broken symlinks, permissions, and error suppression; test cycles, inaccessible paths, escape from cwd, and platform-specific paths on the target");
                }
                if (OUTPUT_OPTIONS.contains(name)) {
                    return SearchResult.found(visited,
                            "This output option changes deduplication, relative/absolute paths, trailing directory marks, files/directories, Dirent/stat objects, and downstream sorting; compare exact consumer-visible results");
                }
                return visited;
            }

            private String callRisk(String method, List<Expression> arguments) {
                if ("escapePath".equals(method) || "convertPathToPattern".equals(method)) {
                    return "fast-glob 3.3 added platform-specific path conversion and changed escaping for Windows brackets/brace expansion; test literal metacharacters, drive/UNC paths, separators, and cross-platform snapshots";
                }
                if ("stream".equals(method) || "globStream".equals(method)) {
                    return "fast-glob stream traversal requires explicit data/error/end/close, backpressure, early-destroy, symlink, permission, and custom-fs handling; verify no resource leak or partial-result assumption";
                }
                if (arguments.isEmpty()) return null;
                List<String> patterns = patterns(arguments.get(0));
                if (patterns.isEmpty()) return null;
                boolean negative = patterns.stream().anyMatch(pattern -> pattern.startsWith("!"));
                boolean absoluteNegative = patterns.stream().anyMatch(this::absoluteNegative);
                boolean braces = patterns.stream().anyMatch(pattern -> pattern.contains("{") || pattern.contains("}"));
                boolean windows = patterns.stream().anyMatch(pattern -> pattern.contains("\\") ||
                        pattern.matches("!?[A-Za-z]:[/\\\\].*") || pattern.startsWith("//?/") ||
                        pattern.startsWith("\\\\?\\"));
                boolean trailing = patterns.stream().anyMatch(pattern -> pattern.endsWith("/"));
                boolean duplicates = duplicatePatterns(patterns);
                List<String> reasons = new ArrayList<>();
                if (negative) reasons.add("negative patterns now match exclusions with dot:true, so hidden-file results can change");
                if (absoluteNegative) reasons.add("3.3.3 applies absolute negative patterns to full paths instead of entry-relative paths");
                if (braces) reasons.add("brace expansion and escaping were fixed, including duplicate slashes and post-expansion escapes");
                if (windows) reasons.add("backslashes are escapes, not separators; 3.3 adds convertPathToPattern and fixes bracket escaping on Windows");
                if (trailing) reasons.add("directory patterns ending in a slash now participate in corrected directory matching");
                if (duplicates) reasons.add("overlapping patterns with leading ./ are now deduplicated when unique is enabled");
                return reasons.isEmpty() ? null : String.join("; ", reasons) +
                       "; compare exact ordered results on a representative fixture tree";
            }

            private List<String> patterns(Expression expression) {
                List<String> values = new ArrayList<>();
                if (expression instanceof J.Literal literal && literal.getValue() instanceof String value) {
                    values.add(value);
                } else if (expression instanceof J.NewArray array && array.getInitializer() != null) {
                    for (Expression element : array.getInitializer()) {
                        if (element instanceof J.Literal literal && literal.getValue() instanceof String value) {
                            values.add(value);
                        }
                    }
                }
                return values;
            }

            private boolean absoluteNegative(String pattern) {
                if (!pattern.startsWith("!")) return false;
                String value = pattern.substring(1);
                return value.startsWith("/") || value.startsWith("//") ||
                       value.matches("[A-Za-z]:[/\\\\].*");
            }

            private boolean duplicatePatterns(List<String> patterns) {
                Set<String> normalized = new HashSet<>();
                for (String pattern : patterns) {
                    String value = pattern.startsWith("./") ? pattern.substring(2) : pattern;
                    if (!normalized.add(value)) return true;
                }
                return false;
            }

            private boolean insideOwnedDirectOptions(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement ->
                            statement.getId().equals(property.getId()))) return false;
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.MethodInvocation call) {
                        return isSecondArgument(call.getArguments(), object) &&
                               ownedMethod(call);
                    }
                    if (cursor.getValue() instanceof JS.FunctionCall call) {
                        return isSecondArgument(call.getArguments(), object) &&
                               ownedFunction(call);
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean isSecondArgument(List<Expression> arguments, J.NewClass object) {
                return arguments.size() >= 2 && arguments.get(1).getId().equals(object.getId());
            }

            private boolean ownedMethod(J.MethodInvocation call) {
                if (call.getSelect() == null) {
                    return unshadowed(objects, call.getSimpleName()) ||
                           unshadowed(functions, call.getSimpleName());
                }
                if (!FastGlobJavaScriptSupport.METHODS.contains(call.getSimpleName()) ||
                    !(call.getSelect() instanceof J.Identifier identifier)) return false;
                return unshadowed(objects, identifier.getSimpleName());
            }

            private boolean ownedFunction(JS.FunctionCall call) {
                String name = FastGlobJavaScriptSupport.expressionName(call.getFunction());
                return unshadowed(objects, name) || unshadowed(functions, name);
            }

            private boolean unshadowed(Set<String> bindings, String name) {
                return bindings.contains(name) && !declared.contains(name);
            }

            private void scanBindings(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ignored) {
                        if (!FastGlobSupport.PACKAGE.equals(FastGlobJavaScriptSupport.moduleName(declaration)) ||
                            declaration.getImportClause() == null) return declaration;
                        JS.ImportClause clause = declaration.getImportClause();
                        if (clause.isTypeOnly()) return declaration;
                        if (clause.getName() != null) objects.add(clause.getName().getSimpleName());
                        if (clause.getNamedBindings() instanceof JS.NamedImports named) {
                            for (JS.ImportSpecifier specifier : named.getElements()) {
                                if (!specifier.getImportType() && FastGlobJavaScriptSupport.METHODS.contains(
                                        FastGlobJavaScriptSupport.importedName(specifier))) {
                                    functions.add(FastGlobJavaScriptSupport.localName(specifier));
                                }
                            }
                        }
                        String namespace = FastGlobJavaScriptSupport.namespaceAlias(clause.getNamedBindings());
                        if (namespace != null) objects.add(namespace);
                        return declaration;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext ignored) {
                        if (variable.getInitializer() instanceof J.MethodInvocation invocation &&
                            FastGlobSupport.PACKAGE.equals(FastGlobJavaScriptSupport.requireModule(invocation))) {
                            objects.add(variable.getSimpleName());
                        }
                        return variable;
                    }
                }.visit(cu, ctx);
            }

            private Set<String> declarations(JS.CompilationUnit cu) {
                Set<String> names = new HashSet<>();
                new JavaScriptIsoVisitor<Set<String>>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, Set<String> accumulator) {
                        if (!(variable.getInitializer() instanceof J.MethodInvocation invocation) ||
                            !FastGlobSupport.PACKAGE.equals(FastGlobJavaScriptSupport.requireModule(invocation))) {
                            accumulator.add(variable.getSimpleName());
                        }
                        return super.visitVariable(variable, accumulator);
                    }
                }.visit(cu, names);
                return names;
            }
        };
    }
}
