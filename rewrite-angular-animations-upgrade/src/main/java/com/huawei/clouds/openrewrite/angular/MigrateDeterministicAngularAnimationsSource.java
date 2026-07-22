package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Apply animation migrations whose binding and runtime semantics are deterministic. */
public final class MigrateDeterministicAngularAnimationsSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular 20 animation source";
    }

    @Override
    public String getDescription() {
        return "Move sole ANIMATION_MODULE_TYPE imports to @angular/core and remove redundant false/animations " +
               "configuration only from calls proven to belong to Angular animation imports.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> existingCoreBindings = Set.of();
            private Set<String> browserModules = Set.of();
            private Set<String> asyncProviders = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> oldCore = existingCoreBindings;
                Set<String> oldModules = browserModules;
                Set<String> oldAsync = asyncProviders;
                existingCoreBindings = importedBindings(cu, "@angular/core");
                browserModules = new HashSet<>();
                asyncProviders = new HashSet<>();
                collectAliases(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                existingCoreBindings = oldCore;
                browserModules = oldModules;
                asyncProviders = oldAsync;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!"@angular/platform-browser/animations".equals(moduleName(visited)) ||
                    !importsOnly(visited, "ANIMATION_MODULE_TYPE")) {
                    return visited;
                }
                String local = importedAlias(visited, "ANIMATION_MODULE_TYPE");
                if (local == null || existingCoreBindings.contains(local) ||
                    !(visited.getModuleSpecifier() instanceof J.Literal literal)) {
                    return visited;
                }
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                        ? "\"" : "'";
                return visited.withModuleSpecifier(literal.withValue("@angular/core")
                        .withValueSource(quote + "@angular/core" + quote));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null && asyncProviders.contains(visited.getSimpleName()) &&
                    hasSoleAnimationsLiteral(visited.getArguments())) {
                    return visited.withArguments(List.of());
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (asyncProviders.contains(expressionName(visited.getFunction())) &&
                    hasSoleAnimationsLiteral(visited.getArguments())) {
                    return visited.withArguments(List.of());
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if ("disableAnimations".equals(expressionName(visited.getName())) &&
                    visited.getInitializer() instanceof J.Literal literal && Boolean.FALSE.equals(literal.getValue()) &&
                    isOwnedBrowserAnimationsWithConfig()) {
                    return null;
                }
                return visited;
            }

            private boolean isOwnedBrowserAnimationsWithConfig() {
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                return invocation != null && object != null && object.getClazz() == null &&
                       "withConfig".equals(invocation.getSimpleName()) &&
                       invocation.getSelect() instanceof J.Identifier identifier &&
                       browserModules.contains(identifier.getSimpleName()) &&
                       invocation.getArguments().stream().anyMatch(argument -> argument.getId().equals(object.getId()));
            }

            private void collectAliases(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if ("@angular/platform-browser/animations".equals(moduleName(visited))) {
                            addAlias(visited, "BrowserAnimationsModule", browserModules);
                        }
                        if ("@angular/platform-browser/animations/async".equals(moduleName(visited))) {
                            addAlias(visited, "provideAnimationsAsync", asyncProviders);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }

    private static boolean hasSoleAnimationsLiteral(List<Expression> arguments) {
        return arguments.size() == 1 && arguments.get(0) instanceof J.Literal literal &&
               "animations".equals(literal.getValue());
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static boolean importsOnly(JS.Import declaration, String wanted) {
        JS.ImportClause clause = declaration.getImportClause();
        return clause != null && clause.getName() == null && clause.getNamedBindings() instanceof JS.NamedImports named &&
               named.getElements().size() == 1 && wanted.equals(importedName(named.getElements().get(0)));
    }

    static boolean importsAny(JS.Import declaration, Set<String> candidates) {
        JS.ImportClause clause = declaration.getImportClause();
        return clause != null && clause.getNamedBindings() instanceof JS.NamedImports named &&
               named.getElements().stream().anyMatch(element -> candidates.contains(importedName(element)));
    }

    static String importedAlias(JS.Import declaration, String wanted) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) {
            return null;
        }
        for (JS.ImportSpecifier element : named.getElements()) {
            if (wanted.equals(importedName(element))) {
                if (element.getSpecifier() instanceof J.Identifier identifier) {
                    return identifier.getSimpleName();
                }
                if (element.getSpecifier() instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
                    return identifier.getSimpleName();
                }
            }
        }
        return null;
    }

    static String importedName(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (specifier.getSpecifier() instanceof JS.Alias alias) {
            return alias.getPropertyName().getSimpleName();
        }
        return "";
    }

    static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof J.FieldAccess access) {
            return access.getSimpleName();
        }
        if (expression instanceof J.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        return "";
    }

    private static Set<String> importedBindings(JS.CompilationUnit cu, String module) {
        Set<String> names = new HashSet<>();
        new JavaScriptIsoVisitor<Set<String>>() {
            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, Set<String> accumulator) {
                JS.Import visited = super.visitImportDeclaration(declaration, accumulator);
                if (module.equals(moduleName(visited)) && visited.getImportClause() != null &&
                    visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named) {
                    for (JS.ImportSpecifier element : named.getElements()) {
                        String imported = importedName(element);
                        String local = importedAlias(visited, imported);
                        if (local != null) names.add(local);
                    }
                }
                return visited;
            }
        }.visit(cu, names);
        return names;
    }

    private static void addAlias(JS.Import declaration, String imported, Set<String> aliases) {
        String alias = importedAlias(declaration, imported);
        if (alias != null) aliases.add(alias);
    }
}
