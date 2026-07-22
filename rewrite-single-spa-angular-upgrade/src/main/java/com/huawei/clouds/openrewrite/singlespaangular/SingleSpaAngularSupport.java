package com.huawei.clouds.openrewrite.singlespaangular;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SingleSpaAngularSupport {
    static final String PACKAGE = "single-spa-angular";
    static final String TARGET = "9.2.0";
    static final Set<String> SOURCES = Set.of("4.3.1", "4.9.2", "5.0.2", "6.3.1", "7.1.0", "8.1.0");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    private static final Set<String> EXCLUDED_PARENTS = Set.of(
            "node_modules", ".pnpm", ".yarn", ".npm", "bower_components", "target", "build", "dist",
            "out", ".next", ".angular", ".nx", "coverage", "generated", "vendor", ".git", ".idea",
            ".gradle", ".mvn", ".m2", ".cache", ".turbo", ".output", "tmp", "temp",
            "storybook-static", "reports", "test-results");
    private static final Map<String, String> STATIC_MODULES = Map.ofEntries(
            Map.entry("single-spa-angular/src/public_api", PACKAGE),
            Map.entry("single-spa-angular/src/single-spa-angular", PACKAGE),
            Map.entry("single-spa-angular/lib/single-spa-angular", PACKAGE),
            Map.entry("single-spa-angular/src/extra-providers", PACKAGE),
            Map.entry("single-spa-angular/src/prod-mode", PACKAGE),
            Map.entry("single-spa-angular/src/parcel-lib", PACKAGE + "/parcel"),
            Map.entry("single-spa-angular/src/parcel-lib/index", PACKAGE + "/parcel"),
            Map.entry("single-spa-angular/lib/webpack/index", PACKAGE + "/lib/webpack"));

    private SingleSpaAngularSupport() {
    }

    /** Only parent directory components are filtered; an install.* leaf remains eligible. */
    static boolean projectPath(Path path) {
        Path parent = path.normalize().getParent();
        if (parent == null) return true;
        for (Path part : parent) {
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED_PARENTS.contains(value) || value.startsWith("generated") ||
                value.startsWith("install")) {
                return false;
            }
        }
        return true;
    }

    static boolean packageJson(Path path) {
        return projectPath(path) && path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    static boolean workspaceJson(Path path) {
        if (!projectPath(path) || path.getFileName() == null) return false;
        return Set.of("angular.json", "workspace.json", "project.json").contains(path.getFileName().toString());
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

    static String directSection(Cursor cursor) {
        Cursor object = cursor.getParentTreeCursor();
        Cursor owner = object == null ? null : object.getParentTreeCursor();
        if (owner == null || !(owner.getValue() instanceof Json.Member member)) return "";
        Cursor root = owner.getParentTreeCursor();
        Cursor document = root == null ? null : root.getParentTreeCursor();
        return root != null && root.getValue() instanceof Json.JsonObject &&
               document != null && document.getValue() instanceof Json.Document ? key(member) : "";
    }

    static boolean overrideOwner(Cursor cursor) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Json.Member member &&
                Set.of("overrides", "resolutions").contains(key(member))) return true;
            if (current.getValue() instanceof Json.Document) break;
        }
        return false;
    }

    static boolean selected(String declaration) {
        return normalizedSingleVersion(declaration, SOURCES);
    }

    static boolean target(String declaration) {
        return normalizedSingleVersion(declaration, Set.of(TARGET));
    }

    private static boolean normalizedSingleVersion(String declaration, Set<String> values) {
        if (declaration == null || !declaration.matches("[~^]?\\d+[.]\\d+[.]\\d+")) return false;
        String normalized = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return values.contains(normalized);
    }

    static String targetDeclaration(String declaration) {
        return declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(0, 1) + TARGET : TARGET;
    }

    static Json.Member replaceString(Json.Member member, String value) {
        if (!(member.getValue() instanceof Json.Literal literal)) return member;
        String quote = literal.getSource().startsWith("'") ? "'" : "\"";
        return member.withValue(literal.withValue(value).withSource(quote + value + quote));
    }

    static J.Literal replaceString(J.Literal literal, String value) {
        String source = literal.getValueSource();
        String quote = source != null && source.startsWith("\"") ? "\"" : "'";
        return literal.withValue(value).withValueSource(quote + value + quote);
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String migratedModule(String module) {
        return STATIC_MODULES.getOrDefault(module, module);
    }

    static boolean packageModule(String module) {
        return PACKAGE.equals(module) || module != null && module.startsWith(PACKAGE + "/");
    }

    static boolean containsPackageReference(String source) {
        int from = 0;
        while (source != null && (from = source.indexOf(PACKAGE, from)) >= 0) {
            int before = from - 1;
            int after = from + PACKAGE.length();
            boolean left = before < 0 || !packageNameCharacter(source.charAt(before));
            boolean right = after >= source.length() || !packageNameCharacter(source.charAt(after));
            if (left && right) return true;
            from = after;
        }
        return false;
    }

    private static boolean packageNameCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '-' || value == '_' || value == '.';
    }

    static boolean unknownPhysicalModule(String module) {
        return packageModule(module) && (module.contains("/src/") || module.contains("/lib/")) &&
               !"single-spa-angular/lib/webpack".equals(module);
    }

    static String requireModule(J.MethodInvocation invocation) {
        if (invocation.getSelect() != null || !"require".equals(invocation.getSimpleName()) ||
            invocation.getArguments().size() != 1 || !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String value)) return "";
        return value;
    }

    static String importedAlias(JS.Import declaration, String importedName) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return null;
        for (JS.ImportSpecifier element : named.getElements()) {
            Expression expression = element.getSpecifier();
            if (expression instanceof J.Identifier identifier && importedName.equals(identifier.getSimpleName())) {
                return identifier.getSimpleName();
            }
            if (expression instanceof JS.Alias alias && importedName.equals(alias.getPropertyName().getSimpleName()) &&
                alias.getAlias() instanceof J.Identifier identifier) return identifier.getSimpleName();
        }
        return null;
    }

    static String rootIdentifier(Expression expression) {
        Expression current = expression;
        while (current instanceof J.FieldAccess field) current = field.getTarget();
        return current instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
