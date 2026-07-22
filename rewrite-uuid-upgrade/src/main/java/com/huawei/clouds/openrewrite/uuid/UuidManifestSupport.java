package com.huawei.clouds.openrewrite.uuid;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

final class UuidManifestSupport {
    static final String PACKAGE = "uuid";
    static final String TYPES_PACKAGE = "@types/uuid";
    static final String TARGET_VERSION = "13.0.2";
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");

    private UuidManifestSupport() {
    }

    static boolean isPackageJson(Path path) {
        return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) {
            return String.valueOf(literal.getValue());
        }
        return member.getKey() instanceof Json.Identifier identifier ? identifier.getName() : "";
    }

    static String stringValue(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }

    static String directDependencySection(Cursor memberCursor) {
        Cursor object = parentTree(memberCursor);
        Cursor section = parentTree(object);
        Cursor root = parentTree(section);
        Cursor document = parentTree(root);
        if (section != null && section.getValue() instanceof Json.Member member &&
            document != null && document.getValue() instanceof Json.Document) {
            String name = key(member);
            return DEPENDENCY_SECTIONS.contains(name) ? name : "";
        }
        return "";
    }

    static Cursor parentTree(Cursor cursor) {
        Cursor parent = cursor == null ? null : cursor.getParent();
        while (parent != null && !(parent.getValue() instanceof Tree)) {
            parent = parent.getParent();
        }
        return parent;
    }
}
