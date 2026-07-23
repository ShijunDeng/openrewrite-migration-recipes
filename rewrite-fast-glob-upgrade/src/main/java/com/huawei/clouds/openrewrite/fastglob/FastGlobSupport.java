package com.huawei.clouds.openrewrite.fastglob;

import org.openrewrite.Cursor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class FastGlobSupport {
    static final String PACKAGE = "fast-glob";
    static final String SOURCE_VERSION = "3.2.2";
    static final String TARGET_VERSION = "3.3.3";
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    private static final Set<String> EXCLUDED_PARENTS = Set.of(
            "node_modules", "vendor", "dist", "build", "out", "target", ".next", ".nuxt",
            ".svelte-kit", ".angular", ".cache", ".yarn", ".pnpm", ".npm", "coverage",
            "reports", "test-results", "storybook-static");

    private FastGlobSupport() {
    }

    static boolean isProjectPath(Path path) {
        int index = 0;
        int count = path.getNameCount();
        for (Path component : path) {
            String name = component.toString().toLowerCase(Locale.ROOT);
            if (index++ < count - 1 && (EXCLUDED_PARENTS.contains(name) ||
                                       name.startsWith("generated") || name.startsWith("install"))) return false;
        }
        return true;
    }

    static boolean isPackageJson(Path path) {
        return isProjectPath(path) && path.getFileName() != null &&
               "package.json".equals(path.getFileName().toString());
    }

    static boolean isSource(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".mjs") ||
               name.endsWith(".cjs") || name.endsWith(".ts") || name.endsWith(".tsx");
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
            document != null && document.getValue() instanceof Json.Document) {
            String name = key(member);
            return DEPENDENCY_SECTIONS.contains(name) ? name : "";
        }
        return "";
    }

    static String replacement(String declaration) {
        if (declaration == null) return null;
        String operator = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(0, 1) : "";
        return (operator + SOURCE_VERSION).equals(declaration) ? operator + TARGET_VERSION : null;
    }
}
