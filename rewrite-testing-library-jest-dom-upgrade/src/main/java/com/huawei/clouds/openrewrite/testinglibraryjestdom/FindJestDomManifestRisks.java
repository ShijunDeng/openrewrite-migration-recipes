package com.huawei.clouds.openrewrite.testinglibraryjestdom;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks manifest decisions that cannot be selected from dependency syntax alone. */
public final class FindJestDomManifestRisks extends Recipe {
    private static final Set<String> RUNNER_PACKAGES = Set.of("vitest", "@jest/globals");

    @Override
    public String getDisplayName() {
        return "Find @testing-library/jest-dom 6 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks non-selected declarations, package-manager overrides, obsolete external types, runner-specific " +
               "entry decisions, Node <14 engines, injectGlobals false, and JavaScript TypeScript setup leaves.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean active;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean old = active;
                active = JestDomSupport.isPackageJson(document.getSourcePath()) && hasDirectJestDom(document, ctx);
                Json.Document visited = JestDomSupport.isPackageJson(document.getSourcePath()) ?
                        super.visitDocument(document, ctx) : document;
                active = old;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String key = JestDomSupport.key(visited);
                String section = JestDomSupport.directDependencySection(getCursor());
                String declaration = JestDomSupport.stringValue(visited);
                if (JestDomSupport.PACKAGE.equals(key) && !section.isEmpty() &&
                    !JestDomSupport.target(declaration)) {
                    return visited.withValue(SearchResult.found(visited.getValue(),
                            "This direct jest-dom declaration is not the exact workbook target after strict migration; resolve ranges, protocols, aliases, catalogs, forks, and unlisted versions deliberately"));
                }
                if (JestDomSupport.overrideSelector(key) && section.isEmpty() &&
                    JestDomSupport.underPackageManagerOverride(getCursor())) {
                    return SearchResult.found(visited,
                            "This root package-manager override can select jest-dom independently of the direct declaration; align the resolved graph and regenerate the owning lockfile");
                }
                if (!active) return visited;
                if (!section.isEmpty() && "@types/testing-library__jest-dom".equals(key)) {
                    return visited.withValue(SearchResult.found(visited.getValue(),
                            "jest-dom 6 ships runner-specific local types; remove this external v5 types package after verifying Jest, @jest/globals, Vitest, Bun, and tsconfig augmentation ownership"));
                }
                if (!section.isEmpty() && RUNNER_PACKAGES.contains(key)) {
                    return visited.withValue(SearchResult.found(visited.getValue(),
                            "This runner requires an intentional jest-dom side-effect entry: use /vitest for Vitest or /jest-globals when Jest injectGlobals is false, and include the TypeScript setup file in tsconfig"));
                }
                if ("node".equals(key) && JestDomSupport.underRootSection(getCursor(), "engines") &&
                    declaration != null && !clearlySupportsNode14(declaration)) {
                    return visited.withValue(SearchResult.found(visited.getValue(),
                            "jest-dom 6 drops Node versions below 14; align engines, CI images, test workers, and package-manager runtime before enabling 6.9.1"));
                }
                if ("injectGlobals".equals(key) && JestDomSupport.underRootSection(getCursor(), "jest") &&
                    visited.getValue() instanceof Json.Literal literal && Boolean.FALSE.equals(literal.getValue())) {
                    return visited.withValue(SearchResult.found(visited.getValue(),
                            "Jest injectGlobals=false requires @testing-library/jest-dom/jest-globals in the included setup file instead of the root side-effect entry"));
                }
                return visited;
            }

            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (!active || !(visited.getValue() instanceof String value) || !javaScriptSetup(value)) return visited;
                boolean jestSetup = JestDomSupport.underRootSection(getCursor(), "jest") &&
                                    JestDomSupport.underMember(getCursor(), "setupFilesAfterEnv");
                boolean vitestSetup = JestDomSupport.underRootSection(getCursor(), "vitest") &&
                                      JestDomSupport.underMember(getCursor(), "setupFiles");
                return jestSetup || vitestSetup ? SearchResult.found(visited,
                        "jest-dom 6 bundles TypeScript declarations only when this setup leaf is TypeScript and included by tsconfig; rename or include it deliberately if the project relies on global matcher types") : visited;
            }

            private boolean hasDirectJestDom(Json.Document document, ExecutionContext ctx) {
                boolean[] found = {false};
                new JsonIsoVisitor<ExecutionContext>() {
                    @Override
                    public Json.Member visitMember(Json.Member member, ExecutionContext scanCtx) {
                        Json.Member visited = super.visitMember(member, scanCtx);
                        if (JestDomSupport.PACKAGE.equals(JestDomSupport.key(visited)) &&
                            !JestDomSupport.directDependencySection(getCursor()).isEmpty()) found[0] = true;
                        return visited;
                    }
                }.visit(document, ctx);
                return found[0];
            }

            private boolean clearlySupportsNode14(String declaration) {
                String normalized = declaration.trim();
                if (normalized.contains("||")) {
                    return java.util.Arrays.stream(normalized.split("[|][|]"))
                            .map(String::trim).allMatch(this::clearlySupportsNode14);
                }
                if (normalized.startsWith("<") || normalized.startsWith("=")) return false;
                Matcher first = Pattern.compile("^(?:>=|\\^|~)?\\s*(\\d+)").matcher(normalized);
                return first.find() && Integer.parseInt(first.group(1)) >= 14;
            }

            private boolean javaScriptSetup(String value) {
                String lower = value.toLowerCase(java.util.Locale.ROOT);
                return lower.contains("setup") && (lower.endsWith(".js") || lower.endsWith(".jsx") ||
                                                   lower.endsWith(".cjs") || lower.endsWith(".mjs"));
            }
        };
    }
}
