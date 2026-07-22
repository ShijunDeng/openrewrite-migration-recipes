package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

/** Moves removed/deprecated common exports when the import is isolated and therefore unambiguous. */
public final class MigrateDeterministicAngularCommonSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular Common imports";
    }

    @Override
    public String getDescription() {
        return "Moves a standalone DOCUMENT import to @angular/core and a standalone XhrFactory import " +
               "from @angular/common/http to @angular/common using TypeScript AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String target = targetModule(visited);
                if (target == null || !(visited.getModuleSpecifier() instanceof J.Literal literal)) {
                    return visited;
                }
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"") ? "\"" : "'";
                return visited.withModuleSpecifier(literal.withValue(target).withValueSource(quote + target + quote));
            }
        };
    }

    private static String targetModule(JS.Import declaration) {
        String module = moduleName(declaration);
        if ("@angular/common".equals(module) && importsOnly(declaration, "DOCUMENT")) {
            return "@angular/core";
        }
        if ("@angular/common/http".equals(module) && importsOnly(declaration, "XhrFactory")) {
            return "@angular/common";
        }
        return null;
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static boolean importsOnly(JS.Import declaration, String importedName) {
        JS.ImportClause clause = declaration.getImportClause();
        return clause != null && clause.getName() == null &&
               clause.getNamedBindings() instanceof JS.NamedImports named && named.getElements().size() == 1 &&
               importedName.equals(importedName(named.getElements().get(0)));
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
