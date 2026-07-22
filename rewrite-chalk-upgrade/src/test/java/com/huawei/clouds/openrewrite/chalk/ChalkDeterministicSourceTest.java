package com.huawei.clouds.openrewrite.chalk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class ChalkDeterministicSourceTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateChalk5DeterministicSource());
    }

    @Test
    void migratesExplicitDisableAssignment() {
        rewriteRun(javascript("import chalk from 'chalk';\nchalk.enabled = false;\n",
                "import chalk from 'chalk';\nchalk.level = 0;\n"));
    }

    @Test
    void migratesExplicitDisableOnStyledBuilder() {
        rewriteRun(javascript("import paint from 'chalk';\npaint.red.enabled = false;\n",
                "import paint from 'chalk';\npaint.red.level = 0;\n"));
    }

    @Test
    void migratesNamedChalkConstructorOption() {
        rewriteRun(typescript("import {Chalk} from 'chalk';\nconst colors = new Chalk({enabled: false});\n",
                "import {Chalk} from 'chalk';\nconst colors = new Chalk({level: 0});\n"));
    }

    @Test
    void migratesAliasedChalkConstructorAndQuotedOption() {
        rewriteRun(javascript("import {Chalk as CustomChalk} from 'chalk';\nconst colors = new CustomChalk({'enabled': false});\n",
                "import {Chalk as CustomChalk} from 'chalk';\nconst colors = new CustomChalk({'level': 0});\n"));
    }

    @Test
    void preservesDoubleQuotedOptionKey() {
        rewriteRun(javascript("import {Chalk} from 'chalk';\nconst colors = new Chalk({\"enabled\": false});\n",
                "import {Chalk} from 'chalk';\nconst colors = new Chalk({\"level\": 0});\n"));
    }

    @Test
    void migratesSeveralDeterministicSitesInOneCycle() {
        rewriteRun(javascript("import chalk, {Chalk} from 'chalk';\nchalk.enabled = false;\nconst plain = new Chalk({enabled: false});\n",
                "import chalk, {Chalk} from 'chalk';\nchalk.level = 0;\nconst plain = new Chalk({level: 0});\n"));
    }

    @Test
    void leavesTrueAndDynamicPoliciesForReview() {
        rewriteRun(javascript("import chalk, {Chalk} from 'chalk';\nchalk.enabled = true;\nchalk.enabled = process.stdout.isTTY;\nnew Chalk({enabled: flag});\n"));
    }

    @Test
    void leavesConflictingAndSpreadConstructorOptionsForReview() {
        rewriteRun(javascript("import {Chalk} from 'chalk';\nnew Chalk({enabled: false, level: 2});\nnew Chalk({...policy, enabled: false});\n"));
    }

    @Test
    void leavesTypeOnlyImportsUntouched() {
        rewriteRun(typescript("import type chalk from 'chalk';\nimport type {Chalk} from 'chalk';\n// @ts-ignore intentional invalid runtime migration boundary\nchalk.enabled = false;\n// @ts-ignore intentional invalid runtime migration boundary\nnew Chalk({enabled: false});\n"));
    }

    @Test
    void leavesUnrelatedEnabledProperties() {
        rewriteRun(javascript("const feature = {enabled: false};\nfeature.enabled = false;\n"));
    }

    @Test
    void leavesShadowedBinding() {
        rewriteRun(javascript("import chalk from 'chalk';\nfunction disable(chalk) { chalk.enabled = false; }\n"));
    }

    @Test
    void leavesFunctionAndClassShadowedAliases() {
        rewriteRun(javascript("import paint from 'chalk';\nfunction outer(){ function paint(){} paint.enabled = false; }\n",
                        source -> source.path("function-shadow.js")),
                javascript("import {Chalk as Paint} from 'chalk';\nfunction outer(){ class Paint{}; new Paint({enabled:false}); }\n",
                        source -> source.path("class-shadow.js")));
    }

    @Test
    void doesNotPartiallyRewriteCommonJsLoading() {
        rewriteRun(javascript("const chalk = require('chalk');\nchalk.enabled = false;\n"));
    }

    @Test
    void excludesGeneratedSource() {
        rewriteRun(javascript("import chalk from 'chalk';\nchalk.enabled = false;\n",
                source -> source.path("generated/logger.js")));
    }

    @Test
    void isIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                javascript("import chalk from 'chalk';\nchalk.enabled = false;\n",
                        "import chalk from 'chalk';\nchalk.level = 0;\n"));
    }
}
