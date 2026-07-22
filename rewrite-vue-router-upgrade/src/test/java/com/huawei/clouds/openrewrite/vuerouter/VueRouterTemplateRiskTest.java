package com.huawei.clouds.openrewrite.vuerouter;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class VueRouterTemplateRiskTest implements RewriteTest {
    @Test
    void marksAllRemovedRouterLinkProps() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterTemplateRisks()),
                text("<router-link to=\"child\" append tag=\"span\" event=\"dblclick\" exact>Child</router-link>\n",
                        source -> source.path("src/Nav.vue").after(actual -> actual)
                                .afterRecipe(file -> assertTrue(file.printAll().contains("removed RouterLink append/event/tag/exact"))))
        );
    }

    @Test
    void marksTransitionAndKeepAliveOutsideRouterViewSlot() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterTemplateRisks()),
                text("<router-view><transition><keep-alive><component /></keep-alive></transition></router-view>\n",
                        source -> source.path("src/App.vue").after(actual -> actual)
                                .afterRecipe(file -> assertTrue(file.printAll().contains("must render inside RouterView's Component slot"))))
        );
    }

    @Test
    void marksDirectRouterViewContent() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterTemplateRisks()),
                text("<router-view>legacy slot text</router-view>\n",
                        source -> source.path("src/Layout.vue").after(actual -> actual)
                                .afterRecipe(file -> assertTrue(file.printAll().contains("Content passed directly to RouterView"))))
        );
    }

    @Test
    void marksLegacyRouterScriptInsideVueSfcPlainText() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterTemplateRisks()),
                text("<script>Vue.use(Router); const router = new Router(options);</script>\n",
                        source -> source.path("src/Legacy.vue").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("Vue.use(Router) is a Vue 2 global install"));
                                    assertTrue(printed.contains("class construction was removed"));
                                }))
        );
    }

    @Test
    void marksObsoleteDeclarationReference() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterTemplateRisks()),
                text("/// <reference types=\"unplugin-vue-router/client\" />\n",
                        source -> source.path("src/env.d.ts").after(actual -> actual)
                                .afterRecipe(file -> assertTrue(file.printAll().contains("Remove the obsolete"))))
        );
    }

    @Test
    void modernRouterViewSlotAndRouterLinkAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterTemplateRisks()),
                text(
                        """
                        <router-link to="/about" custom v-slot="{ navigate }"><button @click="navigate">About</button></router-link>
                        <router-view v-slot="{ Component }"><transition><component :is="Component" /></transition></router-view>
                        """,
                        source -> source.path("src/Modern.vue").afterRecipe(file ->
                                assertFalse(file.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void unrelatedHtmlPropsAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterTemplateRisks()),
                text("<a href=\"/\" exact data-event=\"click\">Home</a>\n",
                        source -> source.path("public/index.html"))
        );
    }

    @Test
    void commentedLegacyExamplesAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterTemplateRisks()),
                text("<!-- <router-link append>old</router-link> -->\n<script>// Vue.use(Router)\n/* new Router(options) */</script>\n",
                        source -> source.path("src/Comments.vue"))
        );
    }

    @Test
    void templateMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterTemplateRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("<router-link to=\"child\" append>Child</router-link>\n",
                        source -> source.path("src/Idempotent.vue").after(actual -> actual))
        );
    }
}
