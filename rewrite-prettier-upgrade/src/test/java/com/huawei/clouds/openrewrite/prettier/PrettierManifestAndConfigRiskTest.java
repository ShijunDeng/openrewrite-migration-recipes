package com.huawei.clouds.openrewrite.prettier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.yaml.Assertions.yaml;

class PrettierManifestAndConfigRiskTest implements RewriteTest {
    @ParameterizedTest(name = "unresolved owner {0}")
    @ValueSource(strings = {
            "1.19.1", "^1.19.1", "~2.8.8", ">=2", "1.x", "workspace:^2.8.8",
            "npm:@fork/prettier@2.8.8", "github:prettier/prettier#2.8.8", "latest", "next", "3.0.0"
    })
    void marksEveryNonTargetDirectOwner(String declaration) {
        rewriteRun(spec -> spec.recipe(new FindPrettierManifestRisks()),
                json("{\"devDependencies\":{\"prettier\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "not the exact workbook target"))));
    }

    @ParameterizedTest(name = "accepted target {0}")
    @ValueSource(strings = {"3.6.2", "^3.6.2", "~3.6.2"})
    void acceptsExactCaretAndTildeTargetOwners(String declaration) {
        rewriteRun(spec -> spec.recipe(new FindPrettierManifestRisks()),
                json("{\"devDependencies\":{\"prettier\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").afterRecipe(after ->
                                assertFalse(after.printAll().contains("not the exact workbook target"), after.printAll()))));
    }

    @ParameterizedTest(name = "Node engine {0}")
    @MethodSource("nodeEngines")
    void validatesPublishedNode14Baseline(String declaration, boolean marked) {
        String manifest = "{\"engines\":{\"node\":\"" + declaration +
                          "\"},\"devDependencies\":{\"prettier\":\"3.6.2\"}}";
        if (marked) {
            rewriteRun(spec -> spec.recipe(new FindPrettierManifestRisks()),
                    json(manifest, source -> source.path("package.json").after(actual -> actual)
                            .afterRecipe(after -> assertContains(after.printAll(), "requires Node >=14"))));
        } else {
            rewriteRun(spec -> spec.recipe(new FindPrettierManifestRisks()),
                    json(manifest, source -> source.path("package.json").afterRecipe(after ->
                            assertFalse(after.printAll().contains("requires Node >=14"), after.printAll()))));
        }
    }

    static Stream<Arguments> nodeEngines() {
        return Stream.of(
                Arguments.of("10", true), Arguments.of(">=10", true), Arguments.of("^12.0.0", true),
                Arguments.of("12 || 14", true), Arguments.of(">=12 <20", true), Arguments.of("*", true),
                Arguments.of("14", false), Arguments.of(">=14", false), Arguments.of("^16.0.0", false),
                Arguments.of("20.1.0", false));
    }

    @ParameterizedTest(name = "removed CLI {0}")
    @ValueSource(strings = {
            "prettier --loglevel warn .", "prettier --loglevel=debug .",
            "prettier --plugin-search-dir ./plugins .", "prettier --plugin-search-dir=plugins .",
            "prettier --no-plugin-search .", "node ./node_modules/prettier/bin-prettier.js --check ."
    })
    void marksRemovedOrRenamedCliOptions(String command) {
        rewriteRun(spec -> spec.recipe(new FindPrettierManifestRisks()),
                json("{\"scripts\":{\"format\":\"" + command + "\"},\"devDependencies\":{\"prettier\":\"3.6.2\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "removed/renamed 3.x CLI"))));
    }

    @ParameterizedTest(name = "safe CLI {0}")
    @ValueSource(strings = {
            "prettier --log-level warn .", "prettier --plugin ./plugin.mjs .", "prettier --check .",
            "notprettier --loglevel warn .", "echo prettier"
    })
    void leavesSupportedOrUnownedCliCommandsUnmarked(String command) {
        rewriteRun(spec -> spec.recipe(new FindPrettierManifestRisks()),
                json("{\"scripts\":{\"format\":\"" + command + "\"},\"devDependencies\":{\"prettier\":\"3.6.2\"}}",
                        source -> source.path("package.json").afterRecipe(after ->
                                assertFalse(after.printAll().contains("removed/renamed 3.x CLI"), after.printAll()))));
    }

    @ParameterizedTest(name = "plugin owner {0}")
    @ValueSource(strings = {
            "prettier-plugin-tailwindcss", "prettier-plugin-organize-imports",
            "@trivago/prettier-plugin-sort-imports", "@company/prettier-plugin-private"
    })
    void marksExplicitPluginOwners(String plugin) {
        rewriteRun(spec -> spec.recipe(new FindPrettierManifestRisks()),
                json("{\"devDependencies\":{\"prettier\":\"3.6.2\",\"" + plugin + "\":\"1.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "async parser/embed contract"))));
    }

    @Test
    void marksTypesAndOverrideOwnership() {
        rewriteRun(spec -> spec.recipe(new FindPrettierManifestRisks()),
                json("{\"devDependencies\":{\"prettier\":\"3.6.2\",\"@types/prettier\":\"2.7.3\"},\"overrides\":{\"prettier\":\"2.8.8\"},\"resolutions\":{\"prettier\":\"1.19.1\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "bundles its own TypeScript declarations");
                            assertContains(after.printAll(), "override/resolution independently owns");
                        })));
    }

    @Test
    void packageLookalikesOtherJsonAndExcludedParentsDoNotActivateRiskRecipe() {
        rewriteRun(spec -> spec.recipe(new FindPrettierManifestRisks()),
                json("{\"engines\":{\"node\":\"10\"},\"devDependencies\":{\"prettier-extra\":\"2.8.8\"}}", source -> source.path("package.json")),
                json("{\"devDependencies\":{\"prettier\":\"2.8.8\"}}", source -> source.path("fixture.json")),
                json("{\"devDependencies\":{\"prettier\":\"2.8.8\"}}", source -> source.path("generated/package.json")));
    }

    @Test
    void marksJsonConfigConflictRemovedSearchAndExplicitPlugins() {
        rewriteRun(spec -> spec.recipe(new FindPrettierConfigRisks()),
                json("{\"jsxBracketSameLine\":true,\"bracketSameLine\":false,\"pluginSearchDirs\":[\".\"],\"plugins\":[\"./plugin.js\"]}",
                        source -> source.path(".prettierrc").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "Both jsxBracketSameLine");
                            assertContains(printed, "pluginSearchDirs was removed");
                            assertContains(printed, "Review every explicit plugin");
                        })));
    }

    @Test
    void marksPackageJsonPrettierOwnerButNotNestedLookalikes() {
        rewriteRun(spec -> spec.recipe(new FindPrettierConfigRisks()),
                json("{\"prettier\":{\"pluginSearchDirs\":[\".\"]},\"tool\":{\"plugins\":[\"x\"]},\"nested\":{\"prettier\":{\"plugins\":[\"y\"]}}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "pluginSearchDirs was removed");
                            assertFalse(printed.contains("Review every explicit plugin"), printed);
                        })));
    }

    @Test
    void marksYamlAndExecutableConfigRisks() {
        rewriteRun(spec -> spec.recipe(new FindPrettierConfigRisks()),
                yaml("jsxBracketSameLine: true\nbracketSameLine: false\npluginSearchDirs:\n  - .\nplugins:\n  - ./plugin.mjs\n",
                        source -> source.path(".prettierrc.yaml").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "Both jsxBracketSameLine");
                            assertContains(after.printAll(), "pluginSearchDirs was removed");
                            assertContains(after.printAll(), "Review every explicit plugin");
                        })),
                javascript("module.exports = { jsxBracketSameLine: true, bracketSameLine: false, pluginSearchDirs: ['.'], plugins: ['./plugin.mjs'] };",
                        source -> source.path("prettier.config.cjs").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "Both jsxBracketSameLine");
                            assertContains(after.printAll(), "pluginSearchDirs was removed");
                            assertContains(after.printAll(), "Review every explicit plugin");
                        })));
    }

    @Test
    void autoRunsBeforeConfigMarkersInRecommendedRecipe() {
                rewriteRun(spec -> spec.recipe(PrettierDependencyTest.environment().activateRecipes(
                        "com.huawei.clouds.openrewrite.prettier.MigratePrettierTo3_6_2")),
                json("{\"devDependencies\":{\"prettier\":\"^2.8.8\"},\"prettier\":{\"jsxBracketSameLine\":true,\"plugins\":[\"./plugin.js\"]}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "^3.6.2");
                            assertContains(printed, "bracketSameLine");
                            assertFalse(printed.contains("jsxBracketSameLine"), printed);
                            assertContains(printed, "Review every explicit plugin");
                        })));
    }

    @Test
    void riskMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindPrettierManifestRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"devDependencies\":{\"prettier\":\">=2\"}}", source -> source.path("package.json")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            String message = "not the exact workbook target";
                            assertTrue(printed.indexOf(message) == printed.lastIndexOf(message), printed);
                        })));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
