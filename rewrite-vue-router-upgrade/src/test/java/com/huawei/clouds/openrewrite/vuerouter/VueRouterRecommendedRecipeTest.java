package com.huawei.clouds.openrewrite.vuerouter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

/** End-to-end fixtures reduced from repositories pinned in README.md. */
class VueRouterRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.vuerouter.MigrateVueRouterTo5_0_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(VueRouterDependencyUpgradeTest.environment().activateRecipes(RECOMMENDED));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=3.6.5 <5", "3.0.6 || 4.0.12", "3.5.1 - 4.0.12", "3.x",
            "workspace:^3.6.5", "npm:@company/router@4.0.12", "github:vuejs/router#v4.0.12",
            "file:../router", "https://example.test/router.tgz", "latest", "$routerVersion", "4.2.5"
    })
    void recommendedRecipePreservesUnsafeDeclarationsAndMarksSelection(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"vue-router\":\"" + declaration + "\"}}",
                source -> source.path("package.json").after(actual -> actual)
                        .afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains(declaration));
                            assertTrue(printed.contains("Strict migration skipped this complex range"));
                            assertFalse(printed.contains("\"vue-router\":\"5.0.3\""));
                        })
        ));
    }

    @Test
    void migratesZwaveJsUiNpmGitHead() {
        rewriteRun(
                json(
                        """
                        {"name":"zwave-js-ui","dependencies":{"vue":"^2.7.14","vue-router":"^3.6.5","pinia":"^2.1.7"},"devDependencies":{"@vitejs/plugin-vue2":"^2.3.1","vue-template-compiler":"^2.7.16"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"vue-router\":\"5.0.3\""));
                                    assertTrue(printed.contains("requires Vue ^3.5.0"));
                                    assertTrue(printed.contains("belongs to the Vue 2 toolchain"));
                                })
                ),
                javascript(
                        """
                        import Vue from 'vue';
                        import Router from 'vue-router';
                        Vue.use(Router);
                        const router = new Router({ mode: 'hash', routes });
                        router.beforeEach(async (to, from, next) => {
                          if (to.matched.length === 0) return router.push('/error');
                          next();
                        });
                        export default router;
                        """,
                        source -> source.path("src/router/index.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("default Router class was removed"));
                                    assertTrue(printed.contains("deprecates the navigation guard next callback"));
                                })
                )
        );
    }

    @Test
    void migratesVueNavigationBarNpmGitHead() {
        rewriteRun(
                json(
                        """
                        {"name":"vue-navigation-bar","devDependencies":{"@vue/compiler-sfc":"^3.2.31","vue":"^3.2.31","vue-router":"^4.0.12","vue-template-compiler":"^2.6.11"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"vue-router\":\"5.0.3\""));
                                    assertTrue(printed.contains("requires Vue ^3.5.0"));
                                    assertTrue(printed.contains("requires @vue/compiler-sfc ^3.5.17"));
                                })
                ),
                javascript(
                        """
                        import { createApp } from 'vue';
                        import { createRouter, createWebHistory } from 'vue-router';
                        const router = createRouter({ history: createWebHistory(), routes: [{ path: '/', component: Home }] });
                        createApp(App).use(router).mount('#app');
                        """,
                        source -> source.path("example/main.js").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void marksVueElementAdminAtPinnedCommitWithoutInventingVersionCoverage() {
        rewriteRun(
                json(
                        """
                        {"name":"vue-element-admin","dependencies":{"vue":"2.6.10","vue-router":"3.0.2","vuex":"3.1.0"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"vue-router\":/*~~(Strict migration skipped"));
                                    assertTrue(printed.contains("requires Vue ^3.5.0"));
                                    assertFalse(printed.contains("\"vue-router\":\"5.0.3\""));
                                })
                ),
                javascript(
                        """
                        import Vue from 'vue';
                        import Router from 'vue-router';
                        Vue.use(Router);
                        export const constantRoutes = [{ path: '/404', component: NotFound }];
                        export default new Router({ scrollBehavior: () => ({ y: 0 }), routes: constantRoutes });
                        """,
                        source -> source.path("src/router/index.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("top: 0"));
                                    assertTrue(printed.contains("default Router class was removed"));
                                })
                )
        );
    }

    @Test
    void marksVueHackernewsSsrAtPinnedCommit() {
        rewriteRun(
                json(
                        """
                        {"name":"vue-hackernews-2.0","dependencies":{"vue":"^2.5.22","vue-router":"^3.0.1","vue-server-renderer":"^2.5.22"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                ),
                javascript(
                        """
                        import Vue from 'vue';
                        import Router from 'vue-router';
                        Vue.use(Router);
                        export function createRouter() {
                          return new Router({
                            mode: 'history', fallback: false,
                            scrollBehavior: () => ({ y: 0 }),
                            routes: [{ path: '/', redirect: '/top' }]
                          });
                        }
                        """,
                        source -> source.path("src/router/index.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("top: 0"));
                                    assertTrue(printed.contains("mode was replaced"));
                                    assertTrue(printed.contains("fallback was removed"));
                                })
                ),
                javascript(
                        """
                        import { createApp } from './app';
                        const { app, router } = createApp();
                        const { fullPath } = router.resolve(url).route;
                        router.push(url);
                        router.onReady(() => {
                          const matched = router.getMatchedComponents();
                          render(app, matched, router.currentRoute);
                        }, reject);
                        """,
                        source -> source.path("src/entry-server.js")
                )
        );
    }

    @Test
    void marksVueManageSystemModernButDeprecatedGuardAtPinnedCommit() {
        rewriteRun(
                json("{\"dependencies\":{\"pinia\":\"^2.1.7\",\"vue\":\"^3.4.5\",\"vue-router\":\"^4.2.5\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript(
                        """
                        import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router';
                        const routes: RouteRecordRaw[] = [{ path: '/:path(.*)', redirect: '/404' }];
                        const router = createRouter({ history: createWebHashHistory(), routes });
                        router.beforeEach((to, from, next) => to.meta.allowed ? next() : next('/login'));
                        export default router;
                        """,
                        source -> source.path("src/router/index.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("path-to-regexp syntax"));
                                    assertTrue(printed.contains("deprecates the navigation guard next callback"));
                                })
                )
        );
    }

    @Test
    void migratesUnpluginVueRouter019PinnedTag() {
        rewriteRun(
                json(
                        """
                        {"devDependencies":{"vue":"^3.5.17","vue-router":"^4.6.3","unplugin-vue-router":"0.19.0"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("Strict migration skipped"));
                                    assertTrue(printed.contains("unplugin-vue-router remains"));
                                })
                ),
                typescript(
                        "import { DataLoaderPlugin } from 'unplugin-vue-router/data-loaders';\napp.use(DataLoaderPlugin, { router });\n",
                        "import { DataLoaderPlugin } from 'vue-router/experimental';\napp.use(DataLoaderPlugin, { router });\n",
                        source -> source.path("playground/src/main.ts")
                ),
                json(
                        """
                        {"include":["./env.d.ts","unplugin-vue-router/client"],"vueCompilerOptions":{"plugins":["unplugin-vue-router/volar/sfc-route-blocks","unplugin-vue-router/volar/sfc-typed-router"]},"compilerOptions":{"paths":{"unplugin-vue-router/runtime":["../src/runtime.ts"]}}}
                        """,
                        source -> source.path("playground/tsconfig.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertFalse(printed.contains("unplugin-vue-router/client"));
                                    assertTrue(printed.contains("vue-router/volar/sfc-typed-router"));
                                    assertTrue(printed.contains("custom unplugin-vue-router path"));
                                })
                )
        );
    }

    @Test
    void officialVueRouter503TargetStateIsNoOp() {
        rewriteRun(
                json(
                        """
                        {"name":"vue-router","version":"5.0.3","type":"module","main":"index.cjs","module":"dist/vue-router.js","peerDependencies":{"@vue/compiler-sfc":"^3.5.17","pinia":"^3.0.4","vue":"^3.5.0"}}
                        """,
                        source -> source.path("packages/router/package.json")
                ),
                typescript(
                        """
                        import { createRouter, createWebHistory } from 'vue-router';
                        export const router = createRouter({ history: createWebHistory(), routes });
                        router.beforeEach((to) => to.meta.allowed ? true : '/login');
                        """,
                        source -> source.path("src/router.ts")
                )
        );
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossManifestSourceAndConfig() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"vue\":\"^3.5.17\",\"vue-router\":\"~4.0.12\"},\"devDependencies\":{\"unplugin-vue-router\":\"0.19.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript("import VueRouter from 'unplugin-vue-router/vite';\nexport default VueRouter();\n",
                        "import VueRouter from 'vue-router/vite';\nexport default VueRouter();\n",
                        source -> source.path("vite.config.ts")),
                json("{\"vueCompilerOptions\":{\"plugins\":[\"unplugin-vue-router/volar/sfc-typed-router\"]}}",
                        "{\"vueCompilerOptions\":{\"plugins\":[\"vue-router/volar/sfc-typed-router\"]}}",
                        source -> source.path("tsconfig.json"))
        );
    }

    @Test
    void recommendedRecipeIsDiscoverableAndValid() {
        Environment environment = VueRouterDependencyUpgradeTest.environment();
        Recipe recipe = environment.activateRecipes(RECOMMENDED);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECOMMENDED.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }
}
