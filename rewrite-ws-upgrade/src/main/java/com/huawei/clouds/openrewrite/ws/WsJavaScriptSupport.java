package com.huawei.clouds.openrewrite.ws;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;

final class WsJavaScriptSupport {
    private WsJavaScriptSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return stringLiteral(declaration.getModuleSpecifier());
    }

    static boolean isWsModule(String module) {
        return module != null && (WsSupport.PACKAGE.equals(module) || module.startsWith(WsSupport.PACKAGE + "/"));
    }

    static String stringLiteral(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value ? value : null;
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
