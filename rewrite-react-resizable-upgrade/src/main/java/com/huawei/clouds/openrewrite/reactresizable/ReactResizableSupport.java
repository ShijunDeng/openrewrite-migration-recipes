package com.huawei.clouds.openrewrite.reactresizable;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class ReactResizableSupport {
    static final String PACKAGE = "react-resizable";
    static final String SOURCE = "1.11.1";
    static final String TARGET = "3.1.3";
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", ".pnpm", ".yarn", ".npm", "bower_components", "target", "build", "dist",
            "out", ".next", ".angular", ".cache", ".turbo", ".nx", ".parcel-cache", ".vite",
            "coverage", "generated", "vendor"
    );

    private ReactResizableSupport() {
    }

    static boolean isProjectPath(Path path) {
        for (Path part : path.normalize()) {
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(value) || value.startsWith("generated") || value.startsWith("install")) {
                return false;
            }
        }
        return true;
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
                alias.getAlias() instanceof J.Identifier identifier) return identifier.getSimpleName();
        }
        return null;
    }

    static String jsonKey(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) return String.valueOf(literal.getValue());
        if (member.getKey() instanceof Json.Identifier identifier) return identifier.getName();
        return "";
    }

    static String jsonString(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }
}
