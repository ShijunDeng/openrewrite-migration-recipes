package com.huawei.clouds.openrewrite.uuid;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark uuid 8-13 source incompatibilities that require application or toolchain decisions. */
public final class FindUuidSourceMigrationRisks extends Recipe {
    private static final Pattern REQUIRE = Pattern.compile(
            "\\brequire\\s*\\(\\s*([\"'])uuid(?:/[^\"']+)?\\1\\s*\\)");
    private static final Pattern DEFAULT_IMPORT = Pattern.compile(
            "\\bimport\\s+[A-Za-z_$][\\w$]*\\s+from\\s*([\"'])uuid\\1");
    private static final Pattern REMAINING_DEEP_IMPORT = Pattern.compile(
            "\\b(?:from\\s*|import\\s*\\(\\s*)([\"'])uuid/(?!package[.]json)[^\"']+\\1");
    private static final Pattern LEGACY_BROWSER_BUILD = Pattern.compile(
            "\\b(?:src\\s*=|importScripts\\s*\\()\\s*([\"'])[^\"']*uuid(?:[.]min)?[.]js\\1");
    private static final Pattern NAMED_IMPORT = Pattern.compile(
            "\\bimport\\s*\\{(?<bindings>[^}]*)}\\s*from\\s*(?<quote>[\"'])uuid\\k<quote>");
    private static final Pattern BINDING = Pattern.compile(
            "(?:^|,)\\s*(?<function>v[13567])(?:\\s+as\\s+(?<alias>[A-Za-z_$][\\w$]*))?\\s*(?=,|$)");
    private static final Pattern NAMESPACE_IMPORT = Pattern.compile(
            "\\bimport\\s*\\*\\s*as\\s*(?<alias>[A-Za-z_$][\\w$]*)\\s*from\\s*(?<quote>[\"'])uuid\\k<quote>");
    private static final Pattern ROOT_IMPORT = Pattern.compile(
            "\\bimport(?:[^;\\n]*?from\\s*|\\s*)([\"'])uuid\\1");

    @Override
    public String getDisplayName() {
        return "Find uuid 13 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Precisely mark CommonJS, default/deep imports, removed browser builds, timestamp options, output " +
               "buffers, and React Native Web Crypto ordering that cannot be safely rewritten without context.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!UuidSourceText.isSupported(visited)) {
                    return visited;
                }
                String source = visited.getText();
                List<UuidSourceText.RiskPattern> risks = new ArrayList<>();
                risks.add(risk(REQUIRE,
                        "uuid 12+ is ESM-only; replace CommonJS loading and verify Node, Jest, SSR, bundler, and published package module mode"));
                risks.add(risk(DEFAULT_IMPORT,
                        "uuid 13 has named root exports only; choose the intended UUID function or constant instead of a default import"));
                risks.add(risk(REMAINING_DEEP_IMPORT,
                        "uuid 13 exports only its public root and package.json; replace this deep/internal import with an explicit root named export"));
                risks.add(risk(LEGACY_BROWSER_BUILD,
                        "The uuid minified UMD/browser build was removed; use the public ESM entry and verify Web Crypto plus browser bundling"));
                addImportedFunctionRisks(source, risks);
                addNamespaceRisks(source, risks);
                if (isReactNativePath(visited)) {
                    risks.add(risk(ROOT_IMPORT,
                            "React Native/Expo must load react-native-get-random-values before every direct or transitive uuid import"));
                }
                return UuidSourceText.markMatches(visited, risks);
            }
        };
    }

    private static void addImportedFunctionRisks(String source, List<UuidSourceText.RiskPattern> risks) {
        Map<String, Set<String>> aliases = new LinkedHashMap<>();
        Matcher imports = NAMED_IMPORT.matcher(source);
        while (imports.find()) {
            if (!UuidSourceText.isCodeMatch(source, imports.start())) {
                continue;
            }
            Matcher binding = BINDING.matcher(imports.group("bindings"));
            while (binding.find()) {
                String function = binding.group("function");
                String alias = binding.group("alias") == null ? function : binding.group("alias");
                aliases.computeIfAbsent(function, ignored -> new LinkedHashSet<>()).add(alias);
            }
        }
        addTimestampRisks(risks, aliases, "");
        addBufferRisks(risks, aliases, "");
    }

    private static void addNamespaceRisks(String source, List<UuidSourceText.RiskPattern> risks) {
        Set<String> namespaces = new LinkedHashSet<>();
        Matcher imports = NAMESPACE_IMPORT.matcher(source);
        while (imports.find()) {
            if (UuidSourceText.isCodeMatch(source, imports.start())) {
                namespaces.add(imports.group("alias"));
            }
        }
        Map<String, Set<String>> qualified = new LinkedHashMap<>();
        for (String function : List.of("v1", "v3", "v5", "v6", "v7")) {
            for (String namespace : namespaces) {
                qualified.computeIfAbsent(function, ignored -> new LinkedHashSet<>())
                        .add(namespace + "\\s*[.]\\s*" + function);
            }
        }
        addTimestampRisks(risks, qualified, "");
        addBufferRisks(risks, qualified, "");
    }

    private static void addTimestampRisks(List<UuidSourceText.RiskPattern> risks,
                                          Map<String, Set<String>> aliases, String ignored) {
        for (String function : List.of("v1", "v6", "v7")) {
            for (String alias : aliases.getOrDefault(function, Set.of())) {
                risks.add(risk(Pattern.compile("\\b" + regexAlias(alias) + "\\s*\\((?=\\s*[^)\\s])"),
                        "uuid 11 changed " + function + " options state semantics; verify uniqueness, monotonicity, fixed-time tests, random source, and clock sequence"));
            }
        }
    }

    private static void addBufferRisks(List<UuidSourceText.RiskPattern> risks,
                                       Map<String, Set<String>> aliases, String ignored) {
        for (String function : List.of("v3", "v5")) {
            for (String alias : aliases.getOrDefault(function, Set.of())) {
                risks.add(risk(Pattern.compile("\\b" + regexAlias(alias) +
                                "\\s*\\((?=[^;\\n)]*,[^;\\n)]*,)[^;\\n)]*\\)"),
                        "This " + function + " output-buffer overload crosses the fixed bounds-check behavior; test short buffers and invalid offsets expecting RangeError"));
            }
        }
        for (String alias : aliases.getOrDefault("v6", Set.of())) {
            risks.add(risk(Pattern.compile("\\b" + regexAlias(alias) +
                            "\\s*\\((?=[^;\\n)]*,)[^;\\n)]*\\)"),
                    "This v6 output-buffer overload crosses the fixed bounds-check behavior; test short buffers and invalid offsets expecting RangeError"));
        }
    }

    private static String regexAlias(String alias) {
        return alias.contains("\\s*") ? alias : Pattern.quote(alias);
    }

    private static boolean isReactNativePath(PlainText text) {
        String path = text.getSourcePath().toString().replace('\\', '/').toLowerCase();
        return path.contains(".native.") || path.contains("/react-native/");
    }

    private static UuidSourceText.RiskPattern risk(Pattern pattern, String message) {
        return new UuidSourceText.RiskPattern(pattern, message);
    }
}
