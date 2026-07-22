package com.huawei.clouds.openrewrite.angulararchitectsmodulefederation;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Preserve the source project's classic-webpack choice without guessing a new federation stack. */
public final class MigrateSelectedClassicWebpackCompanions extends Recipe {
    private static final Set<String> BUILD_PLUS_SOURCES = Set.of("15.0.0", "16.0.0");
    private static final Pattern SAFE_COMMAND = Pattern.compile("^[A-Za-z0-9_@./:=\\- ']+$");
    private static final Pattern NG_ADD = Pattern.compile(
            "^(?:(?:npx|pnpm exec|yarn dlx) +)?ng +add +@angular-architects/module-federation(?: +.*)?$");
    private static final Pattern GENERATE = Pattern.compile(
            "^(?:(?:npx|pnpm exec|yarn dlx) +)?(?:ng|nx) +(?>generate|g) +@angular-architects/module-federation:(?:config|init)(?<tail>(?: +.*)?)$");

    @Override
    public String getDisplayName() {
        return "Migrate selected classic webpack Module Federation companions";
    }

    @Override
    public String getDescription() {
        return "For a manifest that owns a selected source declaration, align only the generated direct runtime and " +
               "ngx-build-plus companions and make static ng-add/init scripts explicitly retain classic webpack.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private String selectedSource;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!ModuleFederationSupport.packageJson(document.getSourcePath())) return document;
                String old = selectedSource;
                selectedSource = selectedSource(document);
                Json.Document visited = super.visitDocument(document, ctx);
                selectedSource = old;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (selectedSource == null) return visited;
                String section = ModuleFederationSupport.directSection(getCursor());
                String key = ModuleFederationSupport.key(visited);
                String value = ModuleFederationSupport.string(visited);
                if (ModuleFederationSupport.SECTIONS.contains(section)) {
                    if (ModuleFederationSupport.RUNTIME.equals(key) && selectedCompanion(value, selectedSource)) {
                        return ModuleFederationSupport.replaceString(visited, ModuleFederationSupport.TARGET);
                    }
                    if ("ngx-build-plus".equals(key) && selectedBuildPlus(value, selectedSource)) {
                        return ModuleFederationSupport.replaceString(visited, ModuleFederationSupport.TARGET);
                    }
                    return visited;
                }
                if ("scripts".equals(section) && value != null) {
                    String migrated = migrateCommand(value);
                    if (!value.equals(migrated)) return ModuleFederationSupport.replaceString(visited, migrated);
                }
                return visited;
            }
        };
    }

    private static String selectedSource(Json.Document document) {
        Set<String> sources = new HashSet<>();
        new JsonIsoVisitor<Set<String>>() {
            @Override
            public Json.Member visitMember(Json.Member member, Set<String> accumulator) {
                Json.Member visited = super.visitMember(member, accumulator);
                String value = ModuleFederationSupport.string(visited);
                if (ModuleFederationSupport.PACKAGE.equals(ModuleFederationSupport.key(visited)) &&
                    ModuleFederationSupport.SECTIONS.contains(ModuleFederationSupport.directSection(getCursor())) &&
                    ModuleFederationSupport.selected(value)) {
                    accumulator.add(normalized(value));
                }
                return visited;
            }
        }.visit(document, sources);
        return sources.size() == 1 ? sources.iterator().next() : null;
    }

    private static boolean selectedCompanion(String declaration, String source) {
        return declaration != null && declaration.matches("[~^]?\\d+[.]\\d+[.]\\d+") &&
               source.equals(normalized(declaration));
    }

    private static boolean selectedBuildPlus(String declaration, String source) {
        if (declaration == null) return false;
        String expected = source.startsWith("15.") ? "15.0.0" : source.startsWith("16.") ? "16.0.0" : "";
        return BUILD_PLUS_SOURCES.contains(expected) && expected.equals(normalized(declaration)) &&
               declaration.matches("[~^]?\\d+[.]\\d+[.]\\d+");
    }

    private static String normalized(String declaration) {
        return declaration != null && (declaration.startsWith("^") || declaration.startsWith("~"))
                ? declaration.substring(1) : declaration;
    }

    static String migrateCommand(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && Character.isWhitespace(value.charAt(start))) start++;
        while (end > start && Character.isWhitespace(value.charAt(end - 1))) end--;
        String command = value.substring(start, end);
        if (!SAFE_COMMAND.matcher(command).matches() || command.contains("--stack") ||
            command.contains("native-federation") || command.contains("rspack")) return value;
        String migrated = command;
        if (NG_ADD.matcher(command).matches()) {
            migrated = command + " --stack module-federation-webpack";
        }
        var matcher = GENERATE.matcher(command);
        if (matcher.matches()) {
            migrated = command.replaceFirst(":(?:config|init)(?= |$)", ":init-webpack");
        }
        return migrated.equals(command) ? value : value.substring(0, start) + migrated + value.substring(end);
    }
}
