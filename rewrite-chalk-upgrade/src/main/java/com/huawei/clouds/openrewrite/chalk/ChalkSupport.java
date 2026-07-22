package com.huawei.clouds.openrewrite.chalk;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class ChalkSupport {
    static final String PACKAGE = "chalk";
    static final String TARGET_VERSION = "5.6.2";
    static final Set<String> SOURCE_VERSIONS = Set.of("2.4.2", "4.1.2", "5.3.0");
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    static final Set<String> REMOVED_COLOR_MODELS = Set.of(
            "keyword", "hsl", "hsv", "hwb", "ansi",
            "bgKeyword", "bgHsl", "bgHsv", "bgHwb", "bgAnsi");
    private static final Set<String> EXCLUDED = Set.of(
            "target", "build", "out", "dist", "generated", "install", "vendor", ".gradle", ".mvn",
            ".m2", ".idea", ".git", "node_modules", "bower_components", ".pnpm", ".yarn", ".npm",
            "coverage", ".next", ".nuxt", ".cache", ".turbo", ".nx", ".parcel-cache", ".vite");

    private ChalkSupport() {
    }

    static boolean isProjectPath(Path path) {
        Path normalized = path.normalize();
        for (int index = 0; index < normalized.getNameCount() - 1; index++) {
            String value = normalized.getName(index).toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(value) || value.startsWith("generated") || value.startsWith("install")) {
                return false;
            }
        }
        return true;
    }

    static boolean isPackageJson(Path path) {
        return isProjectPath(path) && path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    static String selectedDeclaration(String declaration) {
        if (declaration == null || declaration.isEmpty()) return null;
        String prefix = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(0, 1) : "";
        String version = prefix.isEmpty() ? declaration : declaration.substring(1);
        return SOURCE_VERSIONS.contains(version) && (prefix + version).equals(declaration) ? prefix : null;
    }

    static String jsonKey(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal && literal.getValue() instanceof String value) return value;
        if (member.getKey() instanceof Json.Identifier identifier) return identifier.getName();
        return "";
    }

    static String jsonString(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }

    static String directDependencySection(Cursor cursor) {
        Cursor object = cursor.getParent();
        Cursor section = object == null ? null : object.getParent();
        Cursor root = section == null ? null : section.getParent();
        Cursor document = root == null ? null : root.getParent();
        if (section != null && section.getValue() instanceof Json.Member member &&
            document != null && document.getValue() instanceof Json.Document) {
            String key = jsonKey(member);
            return DEPENDENCY_SECTIONS.contains(key) ? key : "";
        }
        return "";
    }

    static String moduleName(JS.Import declaration) {
        return stringLiteral(declaration.getModuleSpecifier());
    }

    static boolean isChalkModule(String module) {
        return module != null && (PACKAGE.equals(module) || module.startsWith(PACKAGE + "/"));
    }

    static String stringLiteral(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value ? value : null;
    }

    static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
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
        return bindings instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier
                ? identifier.getSimpleName() : null;
    }

    static String rootIdentifier(Expression expression) {
        Expression current = expression;
        while (current instanceof J.FieldAccess field) current = field.getTarget();
        return current instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess field) return field.getSimpleName();
        return "";
    }
}
