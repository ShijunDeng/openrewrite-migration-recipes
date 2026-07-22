package com.huawei.clouds.openrewrite.vuerouter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.javascript.Assertions.typescript;

class VueRouterSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest
    @MethodSource("officialImportMappings")
    void migratesOfficialUnpluginEntryPoints(String before, String after) {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueRouterSource()),
                typescript(before, after, source -> source.path("src/migration.ts"))
        );
    }

    @Test
    void migratesExactCatchAllRouteRecords() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueRouterSource()),
                typescript(
                        """
                        import { createRouter, createWebHistory } from 'vue-router';
                        const router = createRouter({
                          history: createWebHistory(),
                          routes: [
                            { path: '*', component: NotFound },
                            { path: '/*', redirect: '/404' }
                          ]
                        });
                        """,
                        """
                        import { createRouter, createWebHistory } from 'vue-router';
                        const router = createRouter({
                          history: createWebHistory(),
                          routes: [
                            { path: '/:pathMatch(.*)*', component: NotFound },
                            { path: '/:pathMatch(.*)*', redirect: '/404' }
                          ]
                        });
                        """,
                        source -> source.path("src/router.ts")
                )
        );
    }

    @Test
    void migratesScrollCoordinatesInsideRouterScrollBehavior() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueRouterSource()),
                typescript(
                        """
                        import Router from 'vue-router';
                        const router = new Router({
                          scrollBehavior(to, from, saved) {
                            return saved || { x: 0, 'y': 120 };
                          }
                        });
                        """,
                        """
                        import Router from 'vue-router';
                        const router = new Router({
                          scrollBehavior(to, from, saved) {
                            return saved || { left: 0, 'top': 120 };
                          }
                        });
                        """,
                        source -> source.path("src/router.ts")
                )
        );
    }

    @Test
    void leavesNonRoutePathsAndOrdinaryCoordinatesUntouched() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueRouterSource()),
                typescript(
                        """
                        import { createRouter } from 'vue-router';
                        const glob = { path: '*', label: 'all files' };
                        const point = { x: 1, y: 2 };
                        const route = { path: '/users/:id', component: User };
                        const iconRegistry = { path: '*', component: Icon };
                        """,
                        source -> source.path("src/unrelated.ts")
                )
        );
    }

    @Test
    void leavesUnknownUnpluginSubpathsForRiskRecipe() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueRouterSource()),
                typescript("import runtime from 'unplugin-vue-router/runtime';\n",
                        source -> source.path("src/runtime.ts"))
        );
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicVueRouterSource())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import VueRouter from 'unplugin-vue-router/vite';\nexport default VueRouter();\n",
                        "import VueRouter from 'vue-router/vite';\nexport default VueRouter();\n",
                        source -> source.path("vite.config.ts"))
        );
    }

    static Stream<Arguments> officialImportMappings() {
        return Stream.of(
                Arguments.of("import VueRouter from 'unplugin-vue-router/vite';\n",
                        "import VueRouter from 'vue-router/vite';\n"),
                Arguments.of("import type { Options, EditableTreeNode } from 'unplugin-vue-router';\n",
                        "import type { Options, EditableTreeNode } from 'vue-router/unplugin';\n"),
                Arguments.of("import { DataLoaderPlugin } from 'unplugin-vue-router/data-loaders';\n",
                        "import { DataLoaderPlugin } from 'vue-router/experimental';\n"),
                Arguments.of("import { defineBasicLoader } from 'unplugin-vue-router/data-loaders/basic';\n",
                        "import { defineBasicLoader } from 'vue-router/experimental';\n"),
                Arguments.of("import { defineColadaLoader } from \"unplugin-vue-router/data-loaders/pinia-colada\";\n",
                        "import { defineColadaLoader } from \"vue-router/experimental/pinia-colada\";\n")
        );
    }
}
