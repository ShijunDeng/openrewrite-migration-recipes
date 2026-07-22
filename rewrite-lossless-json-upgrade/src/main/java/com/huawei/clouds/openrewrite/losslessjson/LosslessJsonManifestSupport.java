package com.huawei.clouds.openrewrite.losslessjson;

import org.openrewrite.Cursor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

final class LosslessJsonManifestSupport {
    static final String PACKAGE = "lossless-json";
    static final String TYPES_PACKAGE = "@types/lossless-json";
    static final String TARGET_VERSION = "4.0.1";
    static final Set<String> SOURCE_VERSIONS = Set.of("2.0.8", "2.0.11");
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");

    private LosslessJsonManifestSupport() {
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
            String sectionName = key(sectionMember);
            return DEPENDENCY_SECTIONS.contains(sectionName) ? sectionName : "";
        }
        return "";
    }
}
