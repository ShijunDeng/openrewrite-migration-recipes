package com.huawei.clouds.openrewrite.fastglob;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.Markers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Normalizes a legacy runtime-supported ignore string to the documented string-array contract. */
public final class NormalizeFastGlobIgnoreOption extends Recipe {
    @Override
    public String getDisplayName() {
        return "Normalize deterministic fast-glob ignore options";
    }

    @Override
    public String getDescription() {
        return "Wraps a direct string-literal ignore option in an array only inside an object literal passed " +
               "directly to an imported or required fast-glob call.";
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
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!"ignore".equals(FastGlobJavaScriptSupport.propertyName(visited.getName())) ||
                    !(visited.getInitializer() instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String) || !insideOwnedDirectOptions(property)) return visited;
                J.NewArray array = new J.NewArray(
                        Tree.randomId(), literal.getPrefix(), Markers.EMPTY, null, List.of(),
                        JContainer.build(Space.EMPTY,
                                List.of(JRightPadded.build(literal.withPrefix(Space.EMPTY))), Markers.EMPTY), null);
                return visited.withInitializer(array);
            }

            private boolean insideOwnedDirectOptions(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement ->
                            statement.getId().equals(property.getId()))) return false;
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    Object value = cursor.getValue();
                    if (value instanceof J.MethodInvocation call) {
                        return isSecondArgument(call.getArguments(), object) &&
                               ownedMethod(call);
                    }
                    if (value instanceof JS.FunctionCall call) {
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
