package com.huawei.clouds.openrewrite.chalk;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

/** Integration fixtures extracted from fixed public commits and the target release contract. */
class ChalkRecommendedRecipeTest implements RewriteTest {
    private static final String VUESAX_COMMIT = "3f03427ca3a66110c17d78c9b12093176b8683d1";
    private static final String WPPCONNECT_COMMIT = "b467df8a5e0b00bbd794b5afc40c7fb1836e70a0";
    private static final String WEBOS_COMMIT = "fa3a0d95450902eaac49cf1014405745559b2231";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(ChalkDependencyUpgradeTest.environment().activateRecipes(ChalkDependencyUpgradeTest.MIGRATE));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void upgradesRealVuesaxCaretDependencyAndMarksOwners() {
        assertTrue(VUESAX_COMMIT.startsWith("3f03427"));
        rewriteRun(json("""
                        {
                          "scripts": {"postinstall": "node postinstall.js"},
                          "dependencies": {
                            "chalk": "^2.4.2",
                            "vue": "^2.6.9"
                          }
                        }
                        """, source -> source.path("package.json").after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("^5.6.2"), printed);
                            assertTrue(printed.contains("pure ESM"), printed);
                            assertTrue(printed.contains("align local, CI, container"), printed);
                        })));
    }

    @Test
    void upgradesRealWppconnectTildeDependency() {
        assertTrue(WPPCONNECT_COMMIT.startsWith("b467df8"));
        rewriteRun(json("""
                        {
                          "dependencies": {
                            "boxen": "^5.1.2",
                            "chalk": "~4.1.2",
                            "execa": "^5.1.1"
                          }
                        }
                        """, source -> source.path("package.json").after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("~5.6.2"), document.printAll()))));
    }

    @Test
    void upgradesRealWebOsExactDependencyWithoutInventingModuleMode() {
        assertTrue(WEBOS_COMMIT.startsWith("fa3a0d9"));
        rewriteRun(json("""
                        {
                          "private": true,
                          "engines": {"node": ">=20"},
                          "dependencies": {
                            "bootstrap": "5.3.3",
                            "chalk": "5.3.0",
                            "crypto-browserify": "3.12.0"
                          }
                        }
                        """, source -> source.path("package.json").after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("\"chalk\""), printed);
                            assertTrue(printed.contains("\"5.6.2\""), printed);
                            assertTrue(printed.contains("not explicitly type=module"), printed);
                        })));
    }

    @Test
    void marksRealNightwatchCommonJsAndInstanceFixture() {
        rewriteRun(javascript("""
                        const chalk = require('chalk');

                        class ChalkColors {
                          constructor() {
                            this.instance = new chalk.Instance();
                            this.origLevel = this.instance.level;
                          }
                          disable() {
                            this.prevLevel = this.instance.level;
                            this.instance.level = 0;
                          }
                        }
                        module.exports = new ChalkColors();
                        """, source -> source.path("lib/utils/chalkColors.js").after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("pure ESM"), printed);
                            assertTrue(printed.contains("named Chalk constructor"), printed);
                        })));
    }

    @Test
    void marksRealContentfulDynamicEnabledPolicyWithoutGuessing() {
        rewriteRun(javascript("""
                        const chalk = require('chalk')
                        chalk.enabled = process.env.NODE_ENV !== 'test'
                        module.exports.successStyle = chalk.green
                        module.exports.errorStyle = chalk.red
                        """, source -> source.path("lib/utils/styles.js").after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("pure ESM"), printed);
                            assertTrue(printed.contains("level > 0"), printed);
                        })));
    }

    @Test
    void appliesAutomaticAndReviewStepsTogetherInOneCycle() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("""
                        {"type":"module","engines":{"node":">=18"},"dependencies":{"chalk":"2.4.2"}}
                        """, """
                        {"type":"module","engines":{"node":">=18"},"dependencies":{"chalk":"5.6.2"}}
                        """, source -> source.path("package.json")),
                javascript("import chalk from 'chalk';\nchalk.enabled = false;\nconsole.log(chalk.keyword('orange')('warning'));\n",
                        source -> source.path("src/cli.js").after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("chalk.level = 0"), printed);
                            assertTrue(printed.contains("removed keyword/hsl/hsv/hwb/ansi"), printed);
                        })));
    }

    @Test
    void marksCompilerAndContainerOwnersWithSourceRisk() {
        rewriteRun(
                json("{\"compilerOptions\":{\"module\":\"commonjs\",\"moduleResolution\":\"node\"}}",
                        source -> source.path("tsconfig.json").after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("cannot load it synchronously"), printed);
                            assertTrue(printed.contains("exports map"), printed);
                        })),
                text("FROM node:10-alpine\n", source -> source.path("Dockerfile").after(actual -> actual)
                        .afterRecipe(file -> assertTrue(file.printAll().contains("declares Node"), file.printAll()))),
                javascript("import chalk from 'chalk';\nconsole.log(chalk`{red fail}`);\n",
                        source -> source.path("src/report.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("chalk-template"), cu.printAll())))
        );
    }

    @Test
    void targetProjectIsNoOpAndRecommendedRecipeIsDiscoverable() {
        rewriteRun(
                json("{\"type\":\"module\",\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"chalk\":\"5.6.2\"},\"devDependencies\":{\"typescript\":\"^5.9.0\"}}",
                        source -> source.path("package.json")),
                json("{\"compilerOptions\":{\"module\":\"NodeNext\",\"moduleResolution\":\"NodeNext\"}}",
                        source -> source.path("tsconfig.json")),
                javascript("import chalk, {Chalk, chalkStderr, supportsColor} from 'chalk';\nconst plain = new Chalk({level: 0});\nconsole.log(chalk.green('ok'), chalkStderr.red('err'), supportsColor?.level, plain('x'));\n",
                        source -> source.path("src/cli.js")),
                text("FROM node:20-alpine\n", source -> source.path("Dockerfile"))
        );
        Recipe recipe = ChalkDependencyUpgradeTest.environment().activateRecipes(ChalkDependencyUpgradeTest.MIGRATE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(ChalkDependencyUpgradeTest.environment().listRecipes().stream()
                .anyMatch(candidate -> ChalkDependencyUpgradeTest.MIGRATE.equals(candidate.getName())));
    }
}
