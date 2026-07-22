package com.huawei.clouds.openrewrite.vuerouter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class VueRouterSourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksVue2InstallConstructionAndLegacyOptions() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJavaScriptRisks()),
                javascript(
                        """
                        import Vue from 'vue';
                        import Router from 'vue-router';
                        Vue.use(Router);
                        const router = new Router({ mode: 'history', base: '/docs/', fallback: false, routes });
                        """,
                        source -> source.path("src/router.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("default Router class was removed"));
                                    assertTrue(printed.contains("Vue.use(Router) is a Vue 2 global install"));
                                    assertTrue(printed.contains("new Router/New VueRouter was removed"));
                                    assertTrue(printed.contains("mode was replaced"));
                                    assertTrue(printed.contains("base moved"));
                                    assertTrue(printed.contains("fallback was removed"));
                                })
                )
        );
    }

    @Test
    void marksRemovedReadyMatchAndMatchedComponentsApis() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJavaScriptRisks()),
                typescript(
                        """
                        import { createRouter, createWebHistory } from 'vue-router';
                        const router = createRouter({ history: createWebHistory(), routes });
                        router.onReady(done, failed);
                        router.match('/users');
                        router.getMatchedComponents();
                        """,
                        source -> source.path("src/ssr.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("isReady() returning a Promise"));
                                    assertTrue(printed.contains("migrate to resolve"));
                                    assertTrue(printed.contains("currentRoute.value.matched"));
                                })
                )
        );
    }

    @Test
    void marksCurrentRouteRefRemovedAppAndResolveShape() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJavaScriptRisks()),
                typescript(
                        """
                        import { createRouter } from 'vue-router';
                        const router = createRouter(options);
                        const query = router.currentRoute.query;
                        const app = router.app;
                        const route = router.resolve(url).route;
                        """,
                        source -> source.path("src/state.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("currentRoute is a ref"));
                                    assertTrue(printed.contains("router.app was removed"));
                                    assertTrue(printed.contains("resolve(...).route changed"));
                                })
                )
        );
    }

    @Test
    void marksNavigationCallbacksAndNextGuards() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJavaScriptRisks()),
                typescript(
                        """
                        import { createRouter } from 'vue-router';
                        const router = createRouter(options);
                        router.push('/home', complete, failed);
                        router.replace('/login', complete);
                        router.beforeEach((to, from, next) => next());
                        router.beforeResolve((to, from, next) => next('/login'));
                        """,
                        source -> source.path("src/navigation.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("removed push/replace completion"));
                                    assertTrue(printed.contains("deprecates the navigation guard next callback"));
                                })
                )
        );
    }

    @Test
    void marksScrollCustomRoutesAndHistoryState() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJavaScriptRisks()),
                typescript(
                        """
                        import { createRouter } from 'vue-router';
                        const router = createRouter({
                          routes: [{ path: '/files/*', component: Files }, { path: '/:id(\\d+)?', component: User }],
                          scrollBehavior: () => ({ left: 0, top: 0 })
                        });
                        history.replaceState({ user: true }, '');
                        const state = history.state;
                        """,
                        source -> source.path("src/routes.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("path-to-regexp syntax"));
                                    assertTrue(printed.contains("scroll behavior changed"));
                                    assertTrue(printed.contains("merge existing state"));
                                })
                )
        );
    }

    @Test
    void marksRemainingUnpluginAndDeepImports() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJavaScriptRisks()),
                typescript(
                        """
                        import runtime from 'unplugin-vue-router/runtime';
                        import { createRouter } from 'vue-router/src/router';
                        """,
                        source -> source.path("src/loading.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("not deterministically mapped"));
                                    assertTrue(printed.contains("bypass public conditional exports"));
                                })
                )
        );
    }

    @Test
    void marksExperimental503Changes() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJavaScriptRisks()),
                typescript(
                        """
                        import { miss, NavigationResult, selectNavigationResult, NAVIGATION_RESULTS_KEY, MatchMiss } from 'vue-router/experimental';
                        const result = new NavigationResult(to);
                        const options = { selectNavigationResult };
                        throw miss();
                        """,
                        source -> source.path("src/loaders.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("NavigationResult(to) is deprecated"));
                                    assertTrue(printed.contains("removed or internalized"));
                                    assertTrue(printed.contains("miss() now throws internally"));
                                    assertTrue(printed.contains("selectNavigationResult was removed"));
                                })
                )
        );
    }

    @Test
    void modernRouterWithoutNextOrCustomSyntaxIsNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJavaScriptRisks()),
                typescript(
                        """
                        import { createRouter, createWebHistory, useRoute } from 'vue-router';
                        const router = createRouter({ history: createWebHistory(), routes: [{ path: '/users/:id', component: User }] });
                        router.beforeEach((to) => to.meta.allowed ? true : '/login');
                        const route = useRoute();
                        """,
                        source -> source.path("src/modern.ts").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void unrelatedRouterLikeApisAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJavaScriptRisks()),
                typescript("const router = localStateMachine();\nrouter.onReady(done);\nconst value = history.state;\n",
                        source -> source.path("src/unrelated.ts").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void sourceRiskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJavaScriptRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import Router from 'vue-router';\nconst router = new Router({ mode: 'hash' });\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual))
        );
    }
}
