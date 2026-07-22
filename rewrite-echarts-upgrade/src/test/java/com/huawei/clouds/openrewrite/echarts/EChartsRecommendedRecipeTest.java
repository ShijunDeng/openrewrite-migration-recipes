package com.huawei.clouds.openrewrite.echarts;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class EChartsRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED = "com.huawei.clouds.openrewrite.echarts.MigrateEChartsTo6_1_0";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void appliesDependencyAndDeterministicSourceMigrationTogether() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"echarts\":\"^5.3.3\"}}",
                        "{\"dependencies\":{\"echarts\":\"6.1.0\"}}", source -> source.path("package.json")),
                typescript("import * as charts from 'echarts/lib/echarts';\ntype O = charts.EChartOption;\n",
                        "import * as charts from 'echarts';\ntype O = charts.EChartsOption;\n",
                        source -> source.path("src/chart.ts")));
    }

    @Test
    void automaticChangesRemainSeparateFromManualMarkers() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"echarts\":\"5.4.1\",\"vue-echarts\":\"6.7.3\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("6.1.0"), after.printAll());
                            assertTrue(after.printAll().contains("independent compatibility matrix"), after.printAll());
                        })),
                typescript("import * as charts from 'echarts/lib/echarts';\nimport 'echarts/lib/chart/bar';\nconst option: charts.EChartsOption = { series: [{ type: 'bar' }] };\n",
                        source -> source.path("src/chart.ts").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("from 'echarts'"), after.printAll());
                            assertTrue(after.printAll().contains("Legacy lib chart/component"), after.printAll());
                            assertTrue(after.printAll().contains("contains this series shape"), after.printAll());
                        })));
    }

    @Test
    void unlistedDependencyIsMarkedButNotSilentlyChanged() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"echarts\":\"5.6.0\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("5.6.0"), after.printAll());
                            assertTrue(after.printAll().contains("scalar was not changed"), after.printAll());
                        })));
    }

    @Test
    void safeTargetProjectIsNoop() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"echarts\":\"6.1.0\"}}", source -> source.path("package.json")),
                typescript("import { init } from 'echarts';\nexport { init };\n", source -> source.path("src/public.ts")));
    }

    @Test
    void allPublicRecipesAreDiscoverableAndValid() {
        Environment environment = environment();
        String[] names = {
                RECOMMENDED,
                "com.huawei.clouds.openrewrite.echarts.UpgradeEChartsTo6_1_0",
                "com.huawei.clouds.openrewrite.echarts.MigrateDeterministicEChartsSourceTo6",
                "com.huawei.clouds.openrewrite.echarts.AuditECharts6SourceCompatibility",
                "com.huawei.clouds.openrewrite.echarts.AuditECharts6ProjectCompatibility",
                "com.huawei.clouds.openrewrite.echarts.AuditEChartsCompanionDependencies"
        };
        for (String name : names) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())), name);
            assertTrue(recipe.validate().isValid(), () -> name + ": " + recipe.validate().failures());
        }
    }

    @Test
    void documentedOpenRewriteYamlPatternRemainsComposable() {
        // Recipe activation and mixed source types mirror OpenRewrite's pinned rewrite repository examples:
        // openrewrite/rewrite@b3008cc4a1f0c43f562da16e5933a2a56d9bc568.
        Recipe recipe = environment().activateRecipes(RECOMMENDED);
        assertTrue(recipe.getRecipeList().size() >= 4);
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.echarts")
                .scanYamlResources().build();
    }
}
