package com.huawei.clouds.openrewrite.reactredux;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ReactReduxSupport {
    static final String PACKAGE = "react-redux";
    static final String TYPES_PACKAGE = "@types/react-redux";
    static final String TARGET = "9.3.0";
    static final Set<String> SOURCES = Set.of("7.2.2", "7.2.4", "7.2.8", "7.2.9", "8.0.2", "8.0.5");
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    static final Set<String> PUBLIC_ENTRIES = Set.of(PACKAGE, PACKAGE + "/alternate-renderers", PACKAGE + "/package.json");
    private static final Map<String, String> AGGREGATE_ENTRIES = Map.ofEntries(
            Map.entry(PACKAGE + "/es", PACKAGE),
            Map.entry(PACKAGE + "/es/index", PACKAGE),
            Map.entry(PACKAGE + "/es/index.js", PACKAGE),
            Map.entry(PACKAGE + "/es/exports", PACKAGE),
            Map.entry(PACKAGE + "/es/exports.js", PACKAGE),
            Map.entry(PACKAGE + "/lib", PACKAGE),
            Map.entry(PACKAGE + "/lib/index", PACKAGE),
            Map.entry(PACKAGE + "/lib/index.js", PACKAGE),
            Map.entry(PACKAGE + "/lib/exports", PACKAGE),
            Map.entry(PACKAGE + "/lib/exports.js", PACKAGE),
            Map.entry(PACKAGE + "/src", PACKAGE),
            Map.entry(PACKAGE + "/src/index", PACKAGE),
            Map.entry(PACKAGE + "/src/index.js", PACKAGE),
            Map.entry(PACKAGE + "/src/exports", PACKAGE),
            Map.entry(PACKAGE + "/src/exports.js", PACKAGE),
            Map.entry(PACKAGE + "/next", PACKAGE),
            Map.entry(PACKAGE + "/next.js", PACKAGE),
            Map.entry(PACKAGE + "/es/next", PACKAGE),
            Map.entry(PACKAGE + "/es/next.js", PACKAGE),
            Map.entry(PACKAGE + "/lib/next", PACKAGE),
            Map.entry(PACKAGE + "/lib/next.js", PACKAGE),
            Map.entry(PACKAGE + "/src/next", PACKAGE),
            Map.entry(PACKAGE + "/src/next.js", PACKAGE));
    private static final Set<String> CENTRAL_SECTIONS = Set.of(
            "overrides", "resolutions", "catalog", "catalogs", "packageExtensions");
    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", "bower_components", "vendor", "target", "build", "dist", "out", "coverage",
            "tmp", "temp", ".git", ".idea", ".vscode", ".cache", ".next", ".nuxt", ".angular",
            ".yarn", ".pnpm", ".pnpm-store", ".npm", ".turbo", ".nx", ".parcel-cache", ".vite",
            ".webpack", ".rollup.cache", ".eslintcache", ".gradle", ".mvn", ".m2", ".output",
            "storybook-static", "reports", "test-results");

    private ReactReduxSupport() {
    }

    static boolean isProjectPath(Path path) {
        Path normalized = path.normalize();
        for (int index = 0; index < normalized.getNameCount() - 1; index++) {
            String name = normalized.getName(index).toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(name) || name.startsWith("generated") || name.startsWith("install")) return false;
        }
        return true;
    }

    static boolean isPackageJson(Path path) {
        return isProjectPath(path) && path.getFileName() != null &&
               "package.json".equals(path.getFileName().toString().toLowerCase(Locale.ROOT));
    }

    static boolean isExecutableConfig(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String leaf = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return leaf.startsWith("webpack.config") || leaf.startsWith("vite.config") ||
               leaf.startsWith("rollup.config") || leaf.startsWith("rspack.config") ||
               leaf.startsWith("next.config") || leaf.startsWith("craco.config");
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

    static boolean underRootSection(Cursor cursor, String... sectionNames) {
        Set<String> names = Set.of(sectionNames);
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (current.getValue() instanceof Json.Member member && names.contains(key(member)) && isRootMember(current)) {
                return true;
            }
        }
        return false;
    }

    static boolean underMember(Cursor cursor, String memberName) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (current.getValue() instanceof Json.Member member && memberName.equals(key(member))) return true;
        }
        return false;
    }

    static boolean underCentralOwnership(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (!(current.getValue() instanceof Json.Member member)) continue;
            String name = key(member);
            if (CENTRAL_SECTIONS.contains(name) && isRootMember(current)) return true;
            if (Set.of("overrides", "packageExtensions").contains(name)) {
                Cursor object = current.getParent();
                Cursor pnpm = object == null ? null : object.getParent();
                if (pnpm != null && pnpm.getValue() instanceof Json.Member pnpmMember &&
                    "pnpm".equals(key(pnpmMember)) && isRootMember(pnpm)) return true;
            }
        }
        return false;
    }

    private static boolean isRootMember(Cursor member) {
        Cursor object = member.getParent();
        Cursor document = object == null ? null : object.getParent();
        return document != null && document.getValue() instanceof Json.Document;
    }

    static boolean packageSelector(String key) {
        if (PACKAGE.equals(key) || key.startsWith(PACKAGE + "@")) return true;
        int parent = key.lastIndexOf('>');
        if (parent >= 0) {
            String target = key.substring(parent + 1);
            if (PACKAGE.equals(target) || target.startsWith(PACKAGE + "@")) return true;
        }
        return key.endsWith("/" + PACKAGE) || key.contains("/" + PACKAGE + "@");
    }

    static boolean selected(String declaration) {
        if (declaration == null) return false;
        String base = declaration.startsWith("^") || declaration.startsWith("~") ? declaration.substring(1) : declaration;
        return (declaration.equals(base) || declaration.equals("^" + base) || declaration.equals("~" + base)) &&
               SOURCES.contains(base);
    }

    static boolean target(String declaration) {
        return TARGET.equals(declaration) || ("^" + TARGET).equals(declaration) || ("~" + TARGET).equals(declaration);
    }

    static String targetDeclaration(String declaration) {
        return declaration.startsWith("^") ? "^" + TARGET :
               declaration.startsWith("~") ? "~" + TARGET : TARGET;
    }

    static String migratedModule(String module) {
        return AGGREGATE_ENTRIES.getOrDefault(module, module);
    }

    static boolean isAggregateEntry(String module) {
        return AGGREGATE_ENTRIES.containsKey(module);
    }

    static boolean isPackageModule(String module) {
        return PACKAGE.equals(module) || module.startsWith(PACKAGE + "/");
    }

    static String moduleName(JS.Import declaration) {
        return stringLiteral(declaration.getModuleSpecifier());
    }

    static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
    }

    static String localBinding(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return "";
    }

    static String importedAlias(JS.Import declaration, String importedName) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return null;
        for (JS.ImportSpecifier specifier : named.getElements()) {
            if (importedName.equals(importedName(specifier))) return localBinding(specifier);
        }
        return null;
    }

    static String expressionName(Object expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess field) return field.getSimpleName();
        if (expression instanceof J.Literal literal && literal.getValue() instanceof String value) return value;
        if (expression instanceof JS.Alias alias) return expressionName(alias.getAlias());
        if (expression instanceof JS.TypeTreeExpression type) return expressionName(type.getExpression());
        return "";
    }

    static String propertyName(JS.PropertyAssignment property) {
        return expressionName(property.getName());
    }

    static String stringLiteral(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value ? value : "";
    }

    static String requireModule(J.MethodInvocation invocation) {
        if (invocation.getSelect() != null || !"require".equals(invocation.getSimpleName()) ||
            invocation.getArguments().size() != 1) return "";
        return stringLiteral(invocation.getArguments().get(0));
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
}
