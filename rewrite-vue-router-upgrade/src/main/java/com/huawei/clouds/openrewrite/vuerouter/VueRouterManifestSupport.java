package com.huawei.clouds.openrewrite.vuerouter;

import org.openrewrite.Cursor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

final class VueRouterManifestSupport {
    static final String PACKAGE = "vue-router";
    static final String UNPLUGIN = "unplugin-vue-router";
    static final String TARGET = "5.0.3";
    static final Set<String> SOURCES = Set.of(
            "3.0.6", "3.1.6", "3.4.3", "3.5.1", "3.5.2",
            "3.5.3", "3.5.4", "3.6.2", "3.6.5", "4.0.12");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");

    private VueRouterManifestSupport() {
    }

    static boolean isPackageJson(Path path) {
        return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            return value;
        }
        return member.getKey() instanceof Json.Identifier identifier ? identifier.getName() : "";
    }

    static String stringValue(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }

    static String directSection(Cursor memberCursor) {
        Cursor object = memberCursor.getParent();
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

    static String selectedVersion(String declaration) {
        if (declaration == null) {
            return null;
        }
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return SOURCES.contains(candidate) &&
               (declaration.equals(candidate) || declaration.equals("^" + candidate) || declaration.equals("~" + candidate))
                ? candidate : null;
    }

    static boolean targetDeclaration(String declaration) {
        return TARGET.equals(declaration) || ("^" + TARGET).equals(declaration) || ("~" + TARGET).equals(declaration);
    }
}
