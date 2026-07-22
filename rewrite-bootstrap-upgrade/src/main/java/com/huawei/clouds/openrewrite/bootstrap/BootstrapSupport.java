package com.huawei.clouds.openrewrite.bootstrap;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class BootstrapSupport {
    static final String PACKAGE = "bootstrap";
    static final String TARGET = "5.3.6";
    static final Set<String> SOURCES = Set.of("3.4.1", "4.6.0", "4.6.1", "4.6.2", "5.2.0", "5.2.3");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    static final Set<String> PLUGINS = Set.of(
            "Alert", "Button", "Carousel", "Collapse", "Dropdown", "Modal", "Offcanvas",
            "Popover", "ScrollSpy", "Tab", "Toast", "Tooltip");
    static final Set<String> JQUERY_PLUGINS = Set.of(
            "alert", "button", "carousel", "collapse", "dropdown", "modal", "popover",
            "scrollspy", "tab", "toast", "tooltip");
    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", "vendor", "dist", "build", "out", "generated", "install", ".next",
            ".nuxt", ".cache", ".mvn", ".m2", ".yarn", "coverage", "target");

    private BootstrapSupport() {
    }

    static boolean isProjectPath(Path path) {
        for (Path component : path) {
            if (EXCLUDED.contains(component.toString().toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    static boolean isPackageJson(Path path) {
        return isProjectPath(path) && path.getFileName() != null &&
               "package.json".equals(path.getFileName().toString());
    }

    static boolean isLockfile(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return "package-lock.json".equals(name) || "npm-shrinkwrap.json".equals(name) ||
               "yarn.lock".equals(name) || "pnpm-lock.yaml".equals(name) || "pnpm-lock.yml".equals(name);
    }

    static boolean isJsonConfig(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("webpack") || name.startsWith("vite") || name.startsWith("rollup") ||
               name.startsWith("parcel") || name.startsWith("browserslist") || name.startsWith("postcss");
    }

    static boolean isExecutableConfig(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("webpack.config") || name.startsWith("vite.config") ||
               name.startsWith("rollup.config") || name.startsWith("postcss.config") ||
               name.startsWith("karma.conf") || name.startsWith("jest.config");
    }

    static boolean isMarkup(Path path) {
        if (!isProjectPath(path)) return false;
        String value = path.toString().toLowerCase(Locale.ROOT);
        return value.endsWith(".html") || value.endsWith(".htm") || value.endsWith(".vue") ||
               value.endsWith(".svelte") || value.endsWith(".hbs") || value.endsWith(".handlebars") ||
               value.endsWith(".mustache") || value.endsWith(".twig") || value.endsWith(".jsp") ||
               value.endsWith(".php");
    }

    static boolean isStyle(Path path) {
        if (!isProjectPath(path)) return false;
        String value = path.toString().toLowerCase(Locale.ROOT);
        return value.endsWith(".css") || value.endsWith(".scss") || value.endsWith(".sass") ||
               value.endsWith(".less");
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal && literal.getValue() instanceof String value) return value;
        return member.getKey() instanceof Json.Identifier identifier ? identifier.getName() : "";
    }

    static String stringValue(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }

    static String directSection(Cursor cursor) {
        Cursor object = cursor.getParent();
        Cursor section = object == null ? null : object.getParent();
        Cursor root = section == null ? null : section.getParent();
        Cursor document = root == null ? null : root.getParent();
        if (section != null && section.getValue() instanceof Json.Member member &&
            document != null && document.getValue() instanceof Json.Document) {
            String name = key(member);
            return SECTIONS.contains(name) ? name : "";
        }
        return "";
    }

    static boolean selected(String declaration) {
        if (declaration == null) return false;
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return SOURCES.contains(candidate) &&
               (candidate.equals(declaration) || ("^" + candidate).equals(declaration) ||
                ("~" + candidate).equals(declaration));
    }

    static String targetDeclaration(String declaration) {
        return declaration.startsWith("^") ? "^" + TARGET :
               declaration.startsWith("~") ? "~" + TARGET : TARGET;
    }

    static boolean target(String declaration) {
        return TARGET.equals(declaration) || ("^" + TARGET).equals(declaration) ||
               ("~" + TARGET).equals(declaration);
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal &&
               literal.getValue() instanceof String value ? value : "";
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

    static String propertyName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.Literal literal && literal.getValue() != null) return literal.getValue().toString();
        return "";
    }

    static boolean bootstrapModule(String module) {
        return PACKAGE.equals(module) || module.startsWith("bootstrap/js/") || module.startsWith("bootstrap/dist/");
    }
}
