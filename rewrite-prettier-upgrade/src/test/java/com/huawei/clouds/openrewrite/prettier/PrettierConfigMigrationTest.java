package com.huawei.clouds.openrewrite.prettier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.yaml.Assertions.yaml;

class PrettierConfigMigrationTest implements RewriteTest {
    @ParameterizedTest(name = "JSON config {0}")
    @ValueSource(strings = {".prettierrc", ".prettierrc.json", "prettier.config.json"})
    void migratesDedicatedJsonRoot(String path) {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                json("{\"semi\":true,\"jsxBracketSameLine\":false,\"printWidth\":100}",
                        "{\"semi\":true,\"bracketSameLine\":false,\"printWidth\":100}",
                        source -> source.path(path)));
    }

    @ParameterizedTest(name = "YAML config {0}")
    @ValueSource(strings = {".prettierrc.yml", ".prettierrc.yaml", "prettier.config.yml", "prettier.config.yaml"})
    void migratesDedicatedYamlRoot(String path) {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                yaml("semi: true\njsxBracketSameLine: false\nprintWidth: 100\n",
                        "semi: true\nbracketSameLine: false\nprintWidth: 100\n",
                        source -> source.path(path)));
    }

    @ParameterizedTest(name = "CommonJS config {0}")
    @ValueSource(strings = {"prettier.config.js", "prettier.config.cjs", ".prettierrc.js", ".prettierrc.cjs"})
    void migratesCommonJsExportObject(String path) {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                javascript("module.exports = { semi: true, jsxBracketSameLine: false };",
                        "module.exports = { semi: true, bracketSameLine: false };",
                        source -> source.path(path)));
    }

    @ParameterizedTest(name = "ESM config {0}")
    @ValueSource(strings = {"prettier.config.mjs", "prettier.config.ts", "prettier.config.cts", "prettier.config.mts", ".prettierrc.mjs"})
    void migratesEsmDefaultExportObject(String path) {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                javascript("export default { semi: true, jsxBracketSameLine: false };",
                        "export default { semi: true, bracketSameLine: false };",
                        source -> source.path(path)));
    }

    @Test
    void migratesOnlyRootPrettierOwnerInPackageJson() {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                json("{\"prettier\":{\"jsxBracketSameLine\":true},\"tool\":{\"jsxBracketSameLine\":false},\"nested\":{\"prettier\":{\"jsxBracketSameLine\":false}}}",
                        "{\"prettier\":{\"bracketSameLine\":true},\"tool\":{\"jsxBracketSameLine\":false},\"nested\":{\"prettier\":{\"jsxBracketSameLine\":false}}}",
                        source -> source.path("package.json")));
    }

    @Test
    void preservesQuotedKeyStyleInJsonAndJavaScript() {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                json("{'jsxBracketSameLine': true}", "{'bracketSameLine': true}", source -> source.path(".prettierrc")),
                javascript("module.exports = { 'jsxBracketSameLine': true };",
                        "module.exports = { 'bracketSameLine': true };", source -> source.path("prettier.config.cjs")),
                javascript("export default { \"jsxBracketSameLine\": true };",
                        "export default { \"bracketSameLine\": true };", source -> source.path("prettier.config.mjs")));
    }

    @Test
    void conflictIsNeverAutoResolved() {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                json("{\"jsxBracketSameLine\":true,\"bracketSameLine\":false}", source -> source.path(".prettierrc")),
                yaml("jsxBracketSameLine: true\nbracketSameLine: false\n", source -> source.path(".prettierrc.yml")),
                javascript("module.exports = { jsxBracketSameLine: true, bracketSameLine: false };", source -> source.path("prettier.config.js")));
    }

    @Test
    void duplicateLegacyAndSpreadOwnershipRemainForReview() {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                json("{\"jsxBracketSameLine\":true,\"jsxBracketSameLine\":false}", source -> source.path(".prettierrc.json")),
                yaml("jsxBracketSameLine: true\njsxBracketSameLine: false\n", source -> source.path(".prettierrc.yaml")),
                javascript("module.exports = { ...base, jsxBracketSameLine: true };", source -> source.path("prettier.config.js")));
    }

    @Test
    void nestedLookalikesAndComputedOwnersAreUntouched() {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                json("{\"overrides\":[{\"options\":{\"jsxBracketSameLine\":true}}]}", source -> source.path(".prettierrc")),
                yaml("overrides:\n  - options:\n      jsxBracketSameLine: true\n", source -> source.path(".prettierrc.yml")),
                javascript("const key = 'jsxBracketSameLine';\nmodule.exports = { [key]: true };", source -> source.path("prettier.config.js")));
    }

    @Test
    void unrelatedFileNamesAndExcludedParentsAreUntouched() {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                json("{\"jsxBracketSameLine\":true}", source -> source.path("prettier-fixture.json")),
                json("{\"jsxBracketSameLine\":true}", source -> source.path("generated/.prettierrc")),
                yaml("jsxBracketSameLine: true\n", source -> source.path("install-cache/.prettierrc.yml")),
                javascript("module.exports = { jsxBracketSameLine: true };", source -> source.path("src/install.js")));
    }

    @Test
    void realPinnedRepositoryConfigurationsMigrate() {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()),
                // DevCloudFE/ng-devui@ef76c44e4a7489cfbc587056d947094d0a0c3d1e
                json("""
                        {
                          "tabWidth": 2,
                          "semi": true,
                          "singleQuote": true,
                          "jsxSingleQuote": true,
                          "bracketSpacing": true,
                          "jsxBracketSameLine": true,
                          "printWidth":140,
                          "endOfLine": "auto",
                          "proseWrap": "preserve",
                          "trailingComma": "es5",
                          "useTabs": false
                        }
                        """, """
                        {
                          "tabWidth": 2,
                          "semi": true,
                          "singleQuote": true,
                          "jsxSingleQuote": true,
                          "bracketSpacing": true,
                          "bracketSameLine": true,
                          "printWidth":140,
                          "endOfLine": "auto",
                          "proseWrap": "preserve",
                          "trailingComma": "es5",
                          "useTabs": false
                        }
                        """, source -> source.path("fixtures/ng-devui/.prettierrc")),
                // antvis/F2@fff56a0f6e0d6109a003bfb29b3376ab9865c731
                yaml("""
                        # 详细配置请移步：https://prettier.io/docs/en/configuration.html
                        ---
                        trailingComma: es5
                        singleQuote: true
                        proseWrap: never
                        endOfLine: lf
                        printWidth: 100
                        tabWidth: 2
                        useTabs: false
                        semi: true
                        bracketSpacing: true
                        jsxBracketSameLine: false
                        arrowParens: 'always'
                        """, """
                        # 详细配置请移步：https://prettier.io/docs/en/configuration.html
                        ---
                        trailingComma: es5
                        singleQuote: true
                        proseWrap: never
                        endOfLine: lf
                        printWidth: 100
                        tabWidth: 2
                        useTabs: false
                        semi: true
                        bracketSpacing: true
                        bracketSameLine: false
                        arrowParens: 'always'
                        """, source -> source.path("fixtures/f2/.prettierrc.yml")),
                // ShareDropio/sharedrop@2b6a9fe5e6cada786d1a1f319612537305b204c7
                javascript("""
                        // https://prettier.io/docs/en/options.html

                        module.exports = {
                          printWidth: 80,
                          tabWidth: 2,
                          useTabs: false,
                          semi: true,
                          singleQuote: true,
                          trailingComma: 'all',
                          bracketSpacing: true,
                          jsxBracketSameLine: false,
                          arrowParens: 'always',
                        };
                        """, """
                        // https://prettier.io/docs/en/options.html

                        module.exports = {
                          printWidth: 80,
                          tabWidth: 2,
                          useTabs: false,
                          semi: true,
                          singleQuote: true,
                          trailingComma: 'all',
                          bracketSpacing: true,
                          bracketSameLine: false,
                          arrowParens: 'always',
                        };
                        """, source -> source.path("fixtures/sharedrop/prettier.config.js")));
    }

    @Test
    void configMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigratePrettierConfigOption()).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"jsxBracketSameLine\":true}", "{\"bracketSameLine\":true}",
                        source -> source.path(".prettierrc.json")));
    }
}
