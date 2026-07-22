package com.huawei.clouds.openrewrite.marked;

import org.openrewrite.Cursor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

final class MarkedManifestSupport {
    static final String PACKAGE = "marked";
    static final String TYPES_PACKAGE = "@types/marked";
    static final String TARGET_VERSION = "17.0.6";
    static final Set<String> SOURCE_VERSIONS = Set.of(
            "4.0.10", "4.0.12", "4.0.17", "4.1.0", "4.2.3",
            "4.2.12", "4.3.0", "5.1.0", "5.1.1");
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");

    private MarkedManifestSupport() {
    }

    static boolean isPackageJson(Path path) {
        return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal && literal.getValue() instanceof String) {
            return (String) literal.getValue();
        }
        if (member.getKey() instanceof Json.Identifier identifier) {
            return identifier.getName();
        }
        return "";
    }

    static String stringValue(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : null;
    }

    static String directDependencySection(Cursor memberCursor) {
        Cursor object = memberCursor.getParent();
        Cursor section = object == null ? null : object.getParent();
        Cursor rootObject = section == null ? null : section.getParent();
        Cursor document = rootObject == null ? null : rootObject.getParent();
        if (section != null && section.getValue() instanceof Json.Member sectionMember &&
            document != null && document.getValue() instanceof Json.Document) {
            String name = key(sectionMember);
            return DEPENDENCY_SECTIONS.contains(name) ? name : "";
        }
        return "";
    }

    static String selectedVersion(String declaration) {
        if (declaration == null) {
            return null;
        }
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) {
            candidate = candidate.substring(1);
        }
        return SOURCE_VERSIONS.contains(candidate) &&
               (declaration.equals(candidate) || declaration.equals("^" + candidate) || declaration.equals("~" + candidate))
                ? candidate : null;
    }
}
