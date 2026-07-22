package com.huawei.clouds.openrewrite.angulararchitectsmodulefederation;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class ModuleFederationSupport {
    static final String PACKAGE = "@angular-architects/module-federation";
    static final String RUNTIME = "@angular-architects/module-federation-runtime";
    static final Set<String> SOURCES = Set.of("15.0.3", "16.0.4");
    static final String TARGET = "20.0.0";
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", ".pnpm", ".yarn", ".npm", "bower_components", "target", "build", "dist",
            "out", ".next", ".angular", ".nx", "coverage", "generated", "vendor", ".git", ".idea",
            ".gradle", ".mvn", ".m2", ".cache", ".turbo", ".output", "tmp", "temp",
            "storybook-static", "reports", "test-results"
    );

    private ModuleFederationSupport() {
    }

    static boolean projectPath(Path path) {
        Path normalized = path.normalize();
        for (int i = 0; i < Math.max(0, normalized.getNameCount() - 1); i++) {
            Path part = normalized.getName(i);
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(value) || value.startsWith("generated") || value.startsWith("install")) {
                return false;
            }
        }
        return true;
    }

    static boolean packageJson(Path path) {
        return projectPath(path) && path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) return String.valueOf(literal.getValue());
        if (member.getKey() instanceof Json.Identifier identifier) return identifier.getName();
        return "";
    }

    static String string(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String rootIdentifier(Expression expression) {
        Expression current = expression;
        while (current instanceof J.FieldAccess field) current = field.getTarget();
        return current instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static boolean packageModule(String module) {
        return PACKAGE.equals(module) || module != null && module.startsWith(PACKAGE + "/");
    }

    static boolean containsPackageReference(String source) {
        int from = 0;
        while (source != null && (from = source.indexOf(PACKAGE, from)) >= 0) {
            int before = from - 1;
            int after = from + PACKAGE.length();
            boolean leftBoundary = before < 0 || !isPackageNameCharacter(source.charAt(before));
            boolean rightBoundary = after >= source.length() || !isPackageNameCharacter(source.charAt(after));
            if (leftBoundary && rightBoundary) return true;
            from = after;
        }
        return false;
    }

    private static boolean isPackageNameCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '-' || value == '_' || value == '.';
    }

    static String directSection(Cursor cursor) {
        Cursor object = cursor.getParentTreeCursor();
        Cursor section = object == null ? null : object.getParentTreeCursor();
        if (section == null || !(section.getValue() instanceof Json.Member member)) return "";
        Cursor rootObject = section.getParentTreeCursor();
        if (rootObject == null || !(rootObject.getValue() instanceof Json.JsonObject)) return "";
        Cursor document = rootObject.getParentTreeCursor();
        return document != null && document.getValue() instanceof Json.Document ? key(member) : "";
    }

    static boolean overrideOrResolutionOwner(Cursor cursor) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null; current = current.getParentTreeCursor()) {
            Object value = current.getValue();
            if (value instanceof Json.Member member) {
                String key = key(member);
                if ("overrides".equals(key) || "resolutions".equals(key)) return true;
            }
            if (value instanceof Json.Document) break;
        }
        return false;
    }

    static boolean selected(String declaration) {
        if (declaration == null) return false;
        String normalized = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return SOURCES.contains(normalized) && declaration.matches("[~^]?\\d+[.]\\d+[.]\\d+");
    }

    static boolean target(String declaration) {
        if (declaration == null) return false;
        String normalized = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return TARGET.equals(normalized) && declaration.matches("[~^]?\\d+[.]\\d+[.]\\d+");
    }

    static Json.Member replaceString(Json.Member member, String value) {
        if (!(member.getValue() instanceof Json.Literal literal)) return member;
        return member.withValue(literal.withValue(value).withSource("\"" + escape(value) + "\""));
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
