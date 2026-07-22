package com.huawei.clouds.openrewrite.vuerouter;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class VueRouterManifestAndConfigTest implements RewriteTest {
    @Test
    void removesUniqueMergedDependencyForTargetRouter() {
        rewriteRun(
                spec -> spec.recipe(new RemoveMergedUnpluginVueRouterDependency()),
                json(
                        """
                        {"dependencies":{"vue-router":"5.0.3"},"devDependencies":{"typescript":"5.9.3","unplugin-vue-router":"^0.19.0"}}
                        """,
                        """
                        {"dependencies":{"vue-router":"5.0.3"},"devDependencies":{"typescript":"5.9.3"}}
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void keepsEmptyDependencyObject() {
        rewriteRun(
                spec -> spec.recipe(new RemoveMergedUnpluginVueRouterDependency()),
                json("{\"dependencies\":{\"vue-router\":\"5.0.3\"},\"devDependencies\":{\"unplugin-vue-router\":\"0.19.0\"}}",
                        "{\"dependencies\":{\"vue-router\":\"5.0.3\"},\"devDependencies\":{}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void duplicateNestedProtocolAndPeerOwnersAreNotRemoved() {
        rewriteRun(
                spec -> spec.recipe(new RemoveMergedUnpluginVueRouterDependency()),
                json("{\"dependencies\":{\"vue-router\":\"5.0.3\",\"unplugin-vue-router\":\"0.19.0\"},\"pnpm\":{\"overrides\":{\"unplugin-vue-router\":\"0.18.0\"}}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"vue-router\":\"5.0.3\",\"unplugin-vue-router\":\"workspace:*\"}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"vue-router\":\"5.0.3\"},\"peerDependencies\":{\"unplugin-vue-router\":\"0.19.0\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void marksSkippedRouterVue2AndOldPeersPrecisely() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterManifestRisks()),
                json(
                        """
                        {"dependencies":{"vue":"^2.7.14","vue-router":">=3.6.5 <5","vue-server-renderer":"2.7.14","vue-template-compiler":"2.7.14","pinia":"^2.1.7","@pinia/colada":"0.18.0"},"devDependencies":{"@vue/compiler-sfc":"3.4.5","@vitejs/plugin-vue2":"2.3.1"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("Strict migration skipped this complex range"));
                                    assertTrue(printed.contains("requires Vue ^3.5.0"));
                                    assertTrue(printed.contains("belongs to the Vue 2 toolchain"));
                                    assertTrue(printed.contains("requires @vue/compiler-sfc ^3.5.17"));
                                    assertTrue(printed.contains("declares Pinia ^3.0.4"));
                                    assertTrue(printed.contains("require @pinia/colada >=0.21.2"));
                                })
                )
        );
    }

    @Test
    void marksUnresolvedUnpluginOwner() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterManifestRisks()),
                json("{\"dependencies\":{\"vue\":\"^3.5.17\",\"vue-router\":\"5.0.3\"},\"peerDependencies\":{\"unplugin-vue-router\":\"^0.19.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("unplugin-vue-router remains"))))
        );
    }

    @Test
    void alignedVue35ManifestIsNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterManifestRisks()),
                json("{\"dependencies\":{\"vue\":\"^3.5.17\",\"vue-router\":\"^5.0.3\",\"pinia\":\"^3.0.4\",\"@vue/compiler-sfc\":\"^3.5.17\",\"@pinia/colada\":\"^0.21.2\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void migratesOfficialVolarPathsAndRemovesClientReference() {
        rewriteRun(
                spec -> spec.recipe(new MigrateVueRouterJsonConfig()),
                json(
                        """
                        {"include":["src/**/*.ts","unplugin-vue-router/client"],"vueCompilerOptions":{"plugins":["unplugin-vue-router/volar/sfc-route-blocks","unplugin-vue-router/volar/sfc-typed-router"]}}
                        """,
                        """
                        {"include":["src/**/*.ts"],"vueCompilerOptions":{"plugins":["vue-router/volar/sfc-route-blocks","vue-router/volar/sfc-typed-router"]}}
                        """,
                        source -> source.path("tsconfig.json")
                )
        );
    }

    @Test
    void marksCustomUnpluginPathsAfterDeterministicConfigMigration() {
        rewriteRun(
                spec -> spec.recipe(new FindVueRouterJsonConfigRisks()),
                json(
                        """
                        {"compilerOptions":{"paths":{"unplugin-vue-router/runtime":["../src/runtime.ts"],"unplugin-vue-router/types":["../src/types.ts"]}}}
                        """,
                        source -> source.path("tsconfig.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("custom unplugin-vue-router path"));
                                    assertTrue(printed.indexOf("custom unplugin-vue-router path") !=
                                               printed.lastIndexOf("custom unplugin-vue-router path"));
                                })
                )
        );
    }

    @Test
    void ordinaryJsonAndNonConfigJsonAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new MigrateVueRouterJsonConfig()),
                json("{\"plugins\":[\"unplugin-vue-router/volar/sfc-typed-router\"]}",
                        source -> source.path("settings.json"))
        );
    }

    @Test
    void doesNotDeleteClientStringFromUnrelatedTsconfigArrays() {
        rewriteRun(
                spec -> spec.recipe(new MigrateVueRouterJsonConfig()),
                json("{\"exclude\":[\"unplugin-vue-router/client\"],\"custom\":[\"unplugin-vue-router/client\"],\"compilerOptions\":{\"types\":[\"unplugin-vue-router/client\"]}}",
                        "{\"exclude\":[\"unplugin-vue-router/client\"],\"custom\":[\"unplugin-vue-router/client\"],\"compilerOptions\":{\"types\":[]}}",
                        source -> source.path("tsconfig.json"))
        );
    }

    @Test
    void jsonMigrationAndMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipes(new MigrateVueRouterJsonConfig(), new FindVueRouterJsonConfigRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"vueCompilerOptions\":{\"plugins\":[\"unplugin-vue-router/volar/sfc-typed-router\"]},\"compilerOptions\":{\"paths\":{\"unplugin-vue-router/runtime\":[\"runtime.ts\"]}}}",
                        source -> source.path("tsconfig.json").after(actual -> actual))
        );
    }
}
