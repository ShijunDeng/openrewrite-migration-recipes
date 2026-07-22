package com.huawei.clouds.openrewrite.bootstrap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class BootstrapJavaScriptRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksOwnedJqueryPluginCallsAndLeavesOtherPluginsAlone() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()),
                javascript("import $ from 'jquery';\nimport 'bootstrap';\n$('#dialog').modal('show');\n$('.tip').tooltip();\n$('.menu').applicationMenu();\n",
                        source -> source.path("src/legacy.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("dropped jQuery plugins"));
                                    assertTrue(printed.contains("applicationMenu"));
                                })));
    }

    @Test
    void marksOwnedJqueryLifecycleEvents() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()),
                javascript("import jQuery from 'jquery';\nimport 'bootstrap';\njQuery('#dialog').on('shown.bs.modal', handler);\njQuery('#dialog').off('shown.bs.modal', handler);\n",
                        source -> source.path("src/events.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("native CustomEvent")))));
    }

    @Test
    void marksNamedUtilAndSourceInternalImports() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()),
                typescript("import Util from 'bootstrap/js/dist/util';\nimport SelectorEngine from 'bootstrap/js/src/dom/selector-engine';\n",
                        source -> source.path("src/internals.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("removed util.js"));
                                    assertTrue(printed.contains("source internals"));
                                })));
    }

    @Test
    void marksPopperBackedConstructors() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()),
                typescript("import { Dropdown, Tooltip, Popover } from 'bootstrap';\nnew Dropdown(menu);\nnew Tooltip(button);\nnew Popover(button);\n",
                        source -> source.path("src/popper.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("uses Popper 2")))));
    }

    @Test
    void marksRemovedAndChangedOwnedOptions() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()),
                typescript("import { Dropdown, ScrollSpy, Tooltip } from 'bootstrap';\nnew Dropdown(menu, { flip: false, boundary: 'window', fallbackPlacement: 'flip' });\nnew ScrollSpy(document.body, { offset: 20 });\nnew Tooltip(button, { whiteList: rules });\n",
                        source -> source.path("src/options.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("removed Dropdown flip"));
                                    assertTrue(printed.contains("IntersectionObserver"));
                                    assertTrue(printed.contains("sanitized content security"));
                                })));
    }

    @Test
    void marksOldDataSelectorsOnlyInBootstrapIntegrationFile() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()),
                javascript("import 'bootstrap';\nconst toggles = document.querySelectorAll('[data-toggle=\"modal\"]');\n",
                        source -> source.path("src/selectors.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("data-bs-*")))),
                javascript("const toggles = document.querySelectorAll('[data-toggle=\"application\"]');\n",
                        source -> source.path("src/application.js")));
    }

    @Test
    void marksExecutableConfigPhysicalAlias() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()),
                typescript("export default { resolve: { alias: { bootstrap: 'bootstrap/dist/js/bootstrap.bundle.js', root: 'bootstrap' } } };\n",
                        source -> source.path("vite.config.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("physical bundle")))));
    }

    @Test
    void leavesLocalSameNamesAndJqueryWithoutBootstrapAlone() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()),
                typescript("const Tooltip = local;\nnew Tooltip(node, { boundary: 'window', whiteList: rules });\nModal._getInstance(node);\n",
                        source -> source.path("src/local.ts")),
                javascript("import $ from 'jquery';\n$('#dialog').modal();\n", source -> source.path("src/jquery-only.js")));
    }

    @Test
    void leavesShadowedImportedPluginAndJqueryAliasesAlone() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()),
                typescript("import $, { ajax } from 'jquery';\nimport { Tooltip } from 'bootstrap';\nfunction local(Tooltip: any, $: any) {\n  new Tooltip(node, { boundary: 'window' });\n  $('#dialog').modal();\n}\n",
                        source -> source.path("src/shadowed.ts")));
    }

    @Test
    void excludesInstalledGeneratedAndBuiltSource() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()),
                javascript("import $ from 'jquery';\nimport 'bootstrap';\n$('#dialog').modal();\n",
                        source -> source.path("node_modules/pkg/index.js")),
                typescript("import Util from 'bootstrap/js/dist/util';\n",
                        source -> source.path("generated/vendor.ts")),
                typescript("export default { alias: 'bootstrap/dist/js/bootstrap.js' };\n",
                        source -> source.path("dist/vite.config.ts")));
    }

    @Test
    void markerRecipeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindBootstrapJavaScriptRisks()).cycles(2),
                javascript("import $ from 'jquery';\nimport 'bootstrap';\n$('#dialog').modal();\n",
                        source -> source.path("src/idempotent.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("dropped jQuery plugins"));
                                    assertFalse(printed.contains("~~(~~("));
                                })));
    }
}
