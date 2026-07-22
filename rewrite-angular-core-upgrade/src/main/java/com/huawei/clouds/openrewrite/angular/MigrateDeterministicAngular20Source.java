package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Set;

/** Reproduces the unambiguous subset of Angular's v20 core migrations on TypeScript AST nodes. */
public final class MigrateDeterministicAngular20Source extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular 20 TypeScript constructs";
    }

    @Override
    public String getDescription() {
        return "Renames imported TestBed.get references to TestBed.inject and moves a standalone DOCUMENT " +
               "import from @angular/common to @angular/core using TypeScript AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> testBedAliases = Set.of();
            private Set<String> shadowedAliases = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> previousTestBeds = testBedAliases;
                Set<String> previousShadowed = shadowedAliases;
                testBedAliases = new HashSet<>();
                shadowedAliases = findDeclaredNames(cu);
                JS.CompilationUnit migrated = super.visitJsCompilationUnit(cu, ctx);
                testBedAliases = previousTestBeds;
                shadowedAliases = previousShadowed;
                return migrated;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = moduleName(visited);
                if ("@angular/core/testing".equals(module)) {
                    String alias = importedAlias(visited, "TestBed");
                    if (alias != null) {
                        testBedAliases.add(alias);
                    }
                }
                if ("@angular/common".equals(module) && importsOnly(visited, "DOCUMENT") &&
                    visited.getModuleSpecifier() instanceof J.Literal literal) {
                    String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"") ? "\"" : "'";
                    return visited.withModuleSpecifier(literal.withValue("@angular/core")
                            .withValueSource(quote + "@angular/core" + quote));
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if ("get".equals(visited.getSimpleName()) && isUnshadowedTestBed(visited.getSelect())) {
                    return visited.withName(visited.getName().withSimpleName("inject"));
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if ("get".equals(visited.getSimpleName()) && isUnshadowedTestBed(visited.getTarget())) {
                    return visited.withName(visited.getName().withSimpleName("inject"));
                }
                return visited;
            }

            private boolean isUnshadowedTestBed(Expression select) {
                if (!(select instanceof J.Identifier identifier)) {
                    return false;
                }
                String name = identifier.getSimpleName();
                return testBedAliases.contains(name) && !shadowedAliases.contains(name);
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

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static boolean importsOnly(JS.Import declaration, String importedName) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || clause.getName() != null || !(clause.getNamedBindings() instanceof JS.NamedImports named) ||
            named.getElements().size() != 1) {
            return false;
        }
        return importedName.equals(importedName(named.getElements().get(0)));
    }

    static String importedAlias(JS.Import declaration, String importedName) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) {
            return null;
        }
        for (JS.ImportSpecifier element : named.getElements()) {
            if (importedName.equals(importedName(element))) {
                Expression specifier = element.getSpecifier();
                if (specifier instanceof J.Identifier identifier) {
                    return identifier.getSimpleName();
                }
                if (specifier instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
                    return identifier.getSimpleName();
                }
            }
        }
        return null;
    }

    static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof JS.Alias alias) {
            return alias.getPropertyName().getSimpleName();
        }
        return "";
    }
}
