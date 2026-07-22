package com.huawei.clouds.openrewrite.chalk;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class ChalkJavaScriptRiskTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindChalk5JavaScriptRisks());
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksCommonJsRequireAtCall() {
        risk("const chalk = require('chalk');\nconsole.log(chalk.green('ok'));\n", "pure ESM");
    }

    @Test
    void marksDeepCommonJsRequire() {
        risk("const template = require('chalk/templates');\n", "package root");
    }

    @Test
    void marksDynamicRootImportForInteropReview() {
        risk("async function load() { return import('chalk'); }\n", "Dynamic import can bridge");
    }

    @Test
    void marksDeepEsmImport() {
        risk("import template from 'chalk/templates.js';\nconsole.log(template);\n", "exports only the package root");
    }

    @Test
    void marksNamespaceImport() {
        risk("import * as chalk from 'chalk';\nconsole.log(chalk.red('x'));\n", "namespace-as-call/style interop");
    }

    @Test
    void marksRootTaggedTemplate() {
        risk("import chalk from 'chalk';\nconst message = chalk`CPU: {red ${cpu}%}`;\n", "chalk-template");
    }

    @Test
    void marksStyledTaggedTemplate() {
        risk("import paint from 'chalk';\nconst message = paint.red`Failure: {bold ${reason}}`;\n", "interpolation escaping");
    }

    @ParameterizedTest
    @ValueSource(strings = {"keyword", "hsl", "hsv", "hwb", "ansi", "bgKeyword", "bgHsl", "bgHsv", "bgHwb", "bgAnsi"})
    void marksEveryRemovedColorModel(String method) {
        String arguments = switch (method) {
            case "keyword", "bgKeyword" -> "'orange'";
            case "ansi", "bgAnsi" -> "31";
            default -> "120, 50, 50";
        };
        risk("import chalk from 'chalk';\nconsole.log(chalk." + method + "(" + arguments + ")('x'));\n", "removed keyword/hsl/hsv/hwb/ansi");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Instance", "constructor", "stderr", "supportsColor", "enabled", "template"})
    void marksRemovedOrMovedDefaultExportMembers(String member) {
        risk("import chalk from 'chalk';\nconsole.log(chalk." + member + ");\n", switch (member) {
            case "Instance" -> "named Chalk constructor";
            case "constructor" -> "enabled options";
            case "stderr" -> "chalkStderr";
            case "supportsColor" -> "named supportsColor";
            case "enabled" -> "level > 0";
            default -> "chalk-template";
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"Level", "Options", "ColorSupport", "Modifiers", "ForegroundColor", "BackgroundColor", "Color"})
    void marksLegacyNamespaceTypes(String type) {
        typeRisk("import chalk from 'chalk';\nlet value: chalk." + type + ";\n", "namespace type changed");
    }

    @Test
    void marksLegacyTypeFromTypeOnlyDefaultImport() {
        typeRisk("import type chalk from 'chalk';\nlet value: chalk.Level;\n", "namespace type changed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"modifiers", "foregroundColors", "backgroundColors", "colors", "Modifiers", "ForegroundColor", "BackgroundColor", "Color"})
    void marksDeprecatedNamedExports(String imported) {
        typeRisk("import {" + imported + "} from 'chalk';\nconsole.log(" + imported + ");\n", "deprecated in 5.6.2");
    }

    @Test
    void modernTargetApiIsNoOp() {
        rewriteRun(javascript("""
                import chalk, {Chalk, chalkStderr, supportsColor, supportsColorStderr, colorNames} from 'chalk';
                const plain = new Chalk({level: 0});
                console.log(chalk.rgb(1, 2, 3).overline('ok'), chalkStderr.red('error'), supportsColor?.level, supportsColorStderr?.level, colorNames, plain('x'));
                """));
    }

    @Test
    void ordinaryTemplateInterpolationIsNoOp() {
        rewriteRun(javascript("import chalk from 'chalk';\nconsole.log(`CPU: ${chalk.red(cpu)}%`);\n"));
    }

    @Test
    void unrelatedMethodsAndTagsAreNoOp() {
        rewriteRun(javascript("const paint = {keyword: value => value};\npaint.keyword('red');\nhtml`<b>${value}</b>`;\n"));
    }

    @Test
    void shadowedDefaultImportUseIsConservativelyIgnored() {
        rewriteRun(javascript("import chalk from 'chalk';\nfunction format(chalk) { return chalk.keyword('red'); }\n"));
    }

    @Test
    void functionAndClassShadowedImportsAreConservativelyIgnored() {
        rewriteRun(
                javascript("import paint from 'chalk';\nfunction outer(){ function paint(){} return paint.keyword('red'); }\n",
                        source -> source.path("function-shadow.js")),
                javascript("import paint from 'chalk';\nfunction outer(){ class paint{}; return paint.keyword('red'); }\n",
                        source -> source.path("class-shadow.js")));
    }

    @Test
    void generatedSourceIsExcluded() {
        rewriteRun(javascript("const chalk = require('chalk');\n", source -> source.path("dist/cli.js")));
    }

    private void risk(String before, String expected) {
        rewriteRun(javascript(before, source -> source.path("src/fixture.js").after(actual -> actual)
                .afterRecipe(cu -> assertTrue(cu.printAll().contains(expected), cu.printAll()))));
    }

    private void typeRisk(String before, String expected) {
        rewriteRun(typescript(before, source -> source.path("src/fixture.ts").after(actual -> actual)
                .afterRecipe(cu -> assertTrue(cu.printAll().contains(expected), cu.printAll()))));
    }
}
