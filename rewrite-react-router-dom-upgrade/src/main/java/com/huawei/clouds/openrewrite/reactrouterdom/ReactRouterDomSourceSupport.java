package com.huawei.clouds.openrewrite.reactrouterdom;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.javascript.tree.JS;

/** Small syntax helpers shared by React Router DOM source recipes. */
final class ReactRouterDomSourceSupport {
    private ReactRouterDomSourceSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String importedAlias(JS.Import declaration, String importedName) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return null;
        for (JS.ImportSpecifier element : named.getElements()) {
            Expression specifier = element.getSpecifier();
            if (specifier instanceof J.Identifier identifier && importedName.equals(identifier.getSimpleName())) {
                return identifier.getSimpleName();
            }
            if (specifier instanceof JS.Alias alias && importedName.equals(alias.getPropertyName().getSimpleName()) &&
                alias.getAlias() instanceof J.Identifier identifier) {
                return identifier.getSimpleName();
            }
        }
        return null;
    }

    static int namedImportCount(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        return clause != null && clause.getNamedBindings() instanceof JS.NamedImports named
                ? named.getElements().size() : 0;
    }

    static boolean hasDefaultImport(JS.Import declaration) {
        return declaration.getImportClause() != null && declaration.getImportClause().getName() != null;
    }

    static String expressionName(Object expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess access) return access.getSimpleName();
        if (expression instanceof J.Literal literal) return String.valueOf(literal.getValue());
        if (expression instanceof JS.TypeTreeExpression type) return expressionName(type.getExpression());
        return "";
    }

    static NameTree rename(NameTree tree, String replacement) {
        if (tree instanceof J.Identifier identifier) return identifier.withSimpleName(replacement);
        return tree;
    }
}
