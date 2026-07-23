package com.huawei.clouds.openrewrite.fastglob;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;

import java.util.Set;

final class FastGlobJavaScriptSupport {
    static final Set<String> METHODS = Set.of(
            "sync", "stream", "glob", "globSync", "globStream", "async", "generateTasks",
            "isDynamicPattern", "escapePath", "convertPathToPattern");

    private FastGlobJavaScriptSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return stringLiteral(declaration.getModuleSpecifier());
    }

    static boolean isFastGlobModule(String module) {
        return module != null && (FastGlobSupport.PACKAGE.equals(module) ||
                                  module.startsWith(FastGlobSupport.PACKAGE + "/"));
    }

    static String stringLiteral(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value ? value : null;
    }

    static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess field) return field.getSimpleName();
        return "";
    }

    static String propertyName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.Literal literal && literal.getValue() instanceof String value) return value;
        return "";
    }

    static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        return expression instanceof JS.Alias alias ? alias.getPropertyName().getSimpleName() : "";
    }

    static String localName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return "";
    }

    static String namespaceAlias(Expression bindings) {
        if (bindings instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return null;
    }

    static String requireModule(J.MethodInvocation invocation) {
        if (invocation.getSelect() == null && "require".equals(invocation.getSimpleName()) &&
            invocation.getArguments().size() == 1) return stringLiteral(invocation.getArguments().get(0));
        return null;
    }
}
