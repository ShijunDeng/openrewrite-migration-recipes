package com.huawei.clouds.openrewrite.testinglibraryjestdom;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class JestDomSupport {
    static final String PACKAGE = "@testing-library/jest-dom";
    static final String SOURCE = "5.17.0";
    static final String TARGET = "6.9.1";
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    static final Set<String> PUBLIC_ENTRIES = Set.of(
            PACKAGE, PACKAGE + "/jest-globals", PACKAGE + "/matchers", PACKAGE + "/vitest",
            PACKAGE + "/package.json");
    static final Set<String> LEGACY_SIDE_EFFECT_ENTRIES = Set.of(
            PACKAGE + "/extend-expect", PACKAGE + "/extend-expect.js",
            PACKAGE + "/dist/extend-expect", PACKAGE + "/dist/extend-expect.js",
            PACKAGE + "/dist/index", PACKAGE + "/dist/index.js");
    private static final Map<String, String> LEGACY_ENTRIES = Map.ofEntries(
            Map.entry(PACKAGE + "/extend-expect", PACKAGE),
            Map.entry(PACKAGE + "/extend-expect.js", PACKAGE),
            Map.entry(PACKAGE + "/dist/extend-expect", PACKAGE),
            Map.entry(PACKAGE + "/dist/extend-expect.js", PACKAGE),
            Map.entry(PACKAGE + "/dist/index", PACKAGE),
            Map.entry(PACKAGE + "/dist/index.js", PACKAGE),
            Map.entry(PACKAGE + "/matchers.js", PACKAGE + "/matchers"),
            Map.entry(PACKAGE + "/dist/matchers", PACKAGE + "/matchers"),
            Map.entry(PACKAGE + "/dist/matchers.js", PACKAGE + "/matchers"),
            Map.entry(PACKAGE + "/jest-globals.js", PACKAGE + "/jest-globals"),
            Map.entry(PACKAGE + "/vitest.js", PACKAGE + "/vitest"));
    private static final Set<String> OVERRIDE_SECTIONS = Set.of("overrides", "resolutions");
    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", "bower_components", "vendor", "target", "build", "dist", "out", "coverage",
            "tmp", "temp", "install", "generated", "generated-sources", ".git", ".idea", ".vscode",
            ".cache", ".next", ".nuxt", ".yarn", ".pnpm", ".pnpm-store", ".npm", ".turbo", ".nx",
            ".parcel-cache", ".vite", ".gradle", ".mvn", ".m2", ".angular", ".output",
            "storybook-static", "reports", "test-results");

    private JestDomSupport() {
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
        return isProjectPath(path) && path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    static boolean isExecutableConfig(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("jest.config") || name.startsWith("vitest.config");
    }

    static boolean isJavaScriptSetupLeaf(Path path) {
        if (path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.contains("setup") && (name.endsWith(".js") || name.endsWith(".jsx") ||
                                           name.endsWith(".cjs") || name.endsWith(".mjs"));
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

    static boolean underRootSection(Cursor cursor, String sectionName) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (current.getValue() instanceof Json.Member member && sectionName.equals(key(member)) &&
                isRootMember(current)) return true;
        }
        return false;
    }

    static boolean underMember(Cursor cursor, String memberName) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (current.getValue() instanceof Json.Member member && memberName.equals(key(member))) return true;
        }
        return false;
    }

    static boolean underPackageManagerOverride(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (!(current.getValue() instanceof Json.Member member) || !OVERRIDE_SECTIONS.contains(key(member))) {
                continue;
            }
            if (isRootMember(current)) return true;
            Cursor object = current.getParent();
            Cursor pnpm = object == null ? null : object.getParent();
            if ("overrides".equals(key(member)) && pnpm != null && pnpm.getValue() instanceof Json.Member pnpmMember &&
                "pnpm".equals(key(pnpmMember)) && isRootMember(pnpm)) return true;
        }
        return false;
    }

    private static boolean isRootMember(Cursor member) {
        Cursor object = member.getParent();
        Cursor document = object == null ? null : object.getParent();
        return document != null && document.getValue() instanceof Json.Document;
    }

    static boolean overrideSelector(String key) {
        if (PACKAGE.equals(key) || key.startsWith(PACKAGE + "@")) return true;
        int parent = key.lastIndexOf('>');
        if (parent >= 0) {
            String target = key.substring(parent + 1);
            if (PACKAGE.equals(target) || target.startsWith(PACKAGE + "@")) return true;
        }
        return key.endsWith("/" + PACKAGE) || key.contains("/" + PACKAGE + "@");
    }

    static boolean selected(String declaration) {
        return SOURCE.equals(declaration) || ("^" + SOURCE).equals(declaration) ||
               ("~" + SOURCE).equals(declaration);
    }

    static boolean target(String declaration) {
        return TARGET.equals(declaration) || ("^" + TARGET).equals(declaration) ||
               ("~" + TARGET).equals(declaration);
    }

    static String targetDeclaration(String declaration) {
        return declaration.startsWith("^") ? "^" + TARGET :
               declaration.startsWith("~") ? "~" + TARGET : TARGET;
    }

    static String migratedModule(String module) {
        return LEGACY_ENTRIES.getOrDefault(module, module);
    }

    static boolean isPackageModule(String module) {
        return PACKAGE.equals(module) || module.startsWith(PACKAGE + "/");
    }

    static String moduleName(JS.Import declaration) {
        return stringLiteral(declaration.getModuleSpecifier());
    }

    static boolean runtimeImport(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null) return true;
        if (clause.isTypeOnly()) return false;
        if (clause.getName() != null) return true;
        if (clause.getNamedBindings() instanceof JS.NamedImports named) {
            return named.getElements().stream().anyMatch(specifier -> !specifier.getImportType());
        }
        return clause.getNamedBindings() != null;
    }

    static String propertyName(JS.PropertyAssignment property) {
        Expression name = property.getName();
        if (name instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (name instanceof J.Literal literal && literal.getValue() instanceof String value) return value;
        return "";
    }

    static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
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
