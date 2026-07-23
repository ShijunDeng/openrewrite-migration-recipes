package com.huawei.clouds.openrewrite.elementplusiconsvue;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ElementPlusIconsVueSupport {
    static final String PACKAGE = "@element-plus/icons-vue";
    static final String TARGET = "2.3.2";
    static final Set<String> SOURCES = Set.of("0.2.4", "2.0.10", "2.1.0");
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    static final Set<String> V024_ICON_SLUGS = loadV024Icons();
    static final Map<String, String> DETERMINISTIC_ENTRIES = Map.ofEntries(
            Map.entry(PACKAGE + "/dist/es/index.mjs", PACKAGE),
            Map.entry(PACKAGE + "/dist/es/index.js", PACKAGE),
            Map.entry(PACKAGE + "/dist/lib/index.js", PACKAGE),
            Map.entry(PACKAGE + "/dist/index.js", PACKAGE),
            Map.entry(PACKAGE + "/dist/index.cjs", PACKAGE),
            Map.entry(PACKAGE + "/dist/global", PACKAGE + "/global"),
            Map.entry(PACKAGE + "/dist/global.js", PACKAGE + "/global"),
            Map.entry(PACKAGE + "/dist/global.cjs", PACKAGE + "/global"));
    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", "bower_components", "vendor", "target", "build", "dist", "out", "coverage",
            "tmp", "temp", "install", "generated", "generated-sources", ".git", ".idea", ".vscode",
            ".cache", ".next", ".nuxt", ".yarn", ".pnpm", ".pnpm-store", ".npm", ".turbo", ".nx",
            ".parcel-cache", ".vite", ".gradle", ".mvn", ".m2", ".angular", ".output",
            "storybook-static", "reports", "test-results");

    private ElementPlusIconsVueSupport() {
    }

    static boolean isProjectPath(Path path) {
        Path normalized = path.normalize();
        for (int i = 0; i < normalized.getNameCount() - 1; i++) {
            String part = normalized.getName(i).toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(part) || part.startsWith("generated") || part.startsWith("install")) return false;
        }
        return true;
    }

    static boolean isPackageJson(Path path) {
        return isProjectPath(path) && path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal && literal.getValue() instanceof String value) return value;
        return member.getKey() instanceof Json.Identifier identifier ? identifier.getName() : "";
    }

    static String stringValue(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }

    static String directDependencySection(Cursor cursor) {
        Cursor object = cursor.getParent();
        Cursor section = object == null ? null : object.getParent();
        Cursor root = section == null ? null : section.getParent();
        Cursor document = root == null ? null : root.getParent();
        if (section != null && section.getValue() instanceof Json.Member member &&
            document != null && document.getValue() instanceof Json.Document &&
            DEPENDENCY_SECTIONS.contains(key(member))) return key(member);
        return "";
    }

    static boolean selected(String declaration) {
        if (declaration == null) return false;
        String scalar = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return SOURCES.contains(scalar) && (declaration.equals(scalar) || declaration.length() == scalar.length() + 1);
    }

    static String targetDeclaration(String declaration) {
        return declaration.startsWith("^") ? "^" + TARGET : declaration.startsWith("~") ? "~" + TARGET : TARGET;
    }

    static String moduleName(JS.Import declaration) {
        return stringLiteral(declaration.getModuleSpecifier());
    }

    static String stringLiteral(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value ? value : "";
    }

    static J.Literal replaceString(J.Literal literal, String replacement) {
        String source = literal.getValueSource();
        String quote = source != null && source.startsWith("\"") ? "\"" :
                       source != null && source.startsWith("`") ? "`" : "'";
        return literal.withValue(replacement).withValueSource(quote + replacement + quote);
    }

    static Json.Literal replaceJsonString(Json.Literal literal, String replacement) {
        String quote = literal.getSource().startsWith("'") ? "'" : "\"";
        return literal.withValue(replacement).withSource(quote + replacement + quote);
    }

    static boolean packageReference(String module) {
        return PACKAGE.equals(module) || module.startsWith(PACKAGE + "/");
    }

    static boolean legacyComponentEntry(String module) {
        return module.matches("^" + java.util.regex.Pattern.quote(PACKAGE) +
                              "/dist/(?:es|lib)/[a-z0-9-]+(?:\\.(?:mjs|js|cjs))?$") &&
               !DETERMINISTIC_ENTRIES.containsKey(module);
    }

    static String knownLegacyIconSlug(String module) {
        if (!legacyComponentEntry(module)) return "";
        String leaf = module.substring(module.lastIndexOf('/') + 1).replaceFirst("\\.(?:mjs|js|cjs)$", "");
        return V024_ICON_SLUGS.contains(leaf) ? leaf : "";
    }

    static String exportedIconName(String slug) {
        StringBuilder name = new StringBuilder();
        for (String part : slug.split("-")) {
            if (!part.isEmpty()) name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    private static Set<String> loadV024Icons() {
        try (InputStream stream = ElementPlusIconsVueSupport.class.getResourceAsStream(
                "/element-plus-icons-vue-0.2.4-icons.txt")) {
            if (stream == null) throw new IllegalStateException("Missing immutable 0.2.4 icon export inventory");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().filter(line -> !line.isBlank()).collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load immutable 0.2.4 icon export inventory", exception);
        }
    }
}
