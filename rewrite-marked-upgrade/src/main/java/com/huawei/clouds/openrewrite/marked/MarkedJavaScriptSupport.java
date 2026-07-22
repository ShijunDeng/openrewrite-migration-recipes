package com.huawei.clouds.openrewrite.marked;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;

import java.util.Set;

final class MarkedJavaScriptSupport {
    private MarkedJavaScriptSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return stringLiteral(declaration.getModuleSpecifier());
    }

    static boolean isMarkedModule(String module) {
        return module != null && (MarkedManifestSupport.PACKAGE.equals(module) ||
                                  module.startsWith(MarkedManifestSupport.PACKAGE + "/"));
    }

    static void collectNamed(JS.ImportClause clause, String imported, Set<String> aliases) {
        if (!(clause.getNamedBindings() instanceof JS.NamedImports named)) {
            return;
        }
        for (JS.ImportSpecifier specifier : named.getElements()) {
            if (imported.equals(importedName(specifier))) {
                Expression expression = specifier.getSpecifier();
                if (expression instanceof J.Identifier identifier) {
                    aliases.add(identifier.getSimpleName());
                } else if (expression instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
                    aliases.add(identifier.getSimpleName());
                }
            }
        }
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

    static String namespaceAlias(Expression bindings) {
        if (bindings instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return null;
    }

    static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof J.FieldAccess field) {
            return field.getSimpleName();
        }
        return "";
    }

    static String propertyName(Expression expression) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof J.Literal literal && literal.getValue() instanceof String) {
            return (String) literal.getValue();
        }
        return "";
    }

    static String stringLiteral(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : null;
    }
}
