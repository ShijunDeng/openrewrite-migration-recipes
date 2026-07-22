package com.huawei.clouds.openrewrite.bootstrap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class BootstrapSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void removesOnlyObsoleteSideEffectUtilImport() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapSource()),
                javascript("import 'bootstrap/js/dist/util';\nimport 'bootstrap';\nconsole.log('ready');\n",
                        "import 'bootstrap';\nconsole.log('ready');\n", source -> source.path("src/app.js")));
    }

    @Test
    void renamesOwnedStaticGetInstanceForNamedAndDeepImports() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapSource()),
                typescript("import { Modal as BsModal } from 'bootstrap';\nimport Tooltip from 'bootstrap/js/dist/tooltip';\nconst modal = BsModal._getInstance(element);\nconst tip = Tooltip._getInstance(element);\n",
                        "import { Modal as BsModal } from 'bootstrap';\nimport Tooltip from 'bootstrap/js/dist/tooltip';\nconst modal = BsModal.getInstance(element);\nconst tip = Tooltip.getInstance(element);\n",
                        source -> source.path("src/plugins.ts")));
    }

    @Test
    void renamesOwnedTooltipAndPopoverWhiteListOptions() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapSource()),
                typescript("import { Tooltip, Popover as BsPopover } from 'bootstrap';\nnew Tooltip(button, { whiteList: rules, html: true });\nnew BsPopover(button, { 'whiteList': rules });\n",
                        "import { Tooltip, Popover as BsPopover } from 'bootstrap';\nnew Tooltip(button, { allowList: rules, html: true });\nnew BsPopover(button, { 'allowList': rules });\n",
                        source -> source.path("src/sanitize.ts")));
    }

    @Test
    void leavesConflictingSpreadAndDetachedOptionsAlone() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapSource()),
                typescript("import { Tooltip } from 'bootstrap';\nnew Tooltip(button, { ...defaults, whiteList: rules });\nnew Tooltip(button, { whiteList: oldRules, allowList: newRules });\nconst detached = { whiteList: rules };\n",
                        source -> source.path("src/conservative.ts")));
    }

    @Test
    void leavesNamedUtilImportsForReviewAndSimilarModulesAlone() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapSource()),
                typescript("import Util from 'bootstrap/js/dist/util';\nimport { Modal } from '@example/bootstrap';\nModal._getInstance(node);\n",
                        source -> source.path("src/review.ts")));
    }

    @Test
    void leavesUnownedSameNamesAndShadowedPluginAlone() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapSource()),
                typescript("const Modal = local;\nModal._getInstance(node);\nconst options = { whiteList: rules };\n",
                        source -> source.path("src/local.ts")),
                typescript("import { Modal } from 'bootstrap';\nfunction lookup(Modal: any) { return Modal._getInstance(node); }\n",
                        source -> source.path("src/shadowed.ts")));
    }

    @Test
    void excludesInstalledAndGeneratedSource() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapSource()),
                typescript("import { Modal } from 'bootstrap';\nModal._getInstance(node);\n",
                        source -> source.path("node_modules/pkg/index.ts")),
                typescript("import { Tooltip } from 'bootstrap';\nnew Tooltip(node, { whiteList: rules });\n",
                        source -> source.path("generated/client.ts")));
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicBootstrapSource())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { Modal, Tooltip } from 'bootstrap';\nModal._getInstance(node);\nnew Tooltip(node, { whiteList: rules });\n",
                        "import { Modal, Tooltip } from 'bootstrap';\nModal.getInstance(node);\nnew Tooltip(node, { allowList: rules });\n",
                        source -> source.path("src/idempotent.ts")));
    }
}
