package com.huawei.clouds.openrewrite.echarts;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;

class EChartsSourceRiskTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.echarts.AuditECharts6SourceCompatibility";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "marks source risk {0}")
    @MethodSource("risks")
    void marksExactEChartsOwnedRiskNodes(String label, String source, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                typescript(source, input -> input.path("src/" + label + ".ts")
                        .after(actual -> actual)
                        .afterRecipe(after -> assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    static Stream<Arguments> risks() {
        String root = "import * as charts from 'echarts';\n";
        String option = root + "const option: charts.EChartsOption = ";
        return Stream.of(
                Arguments.of("src-chart", "import { BarChart } from 'echarts/src/chart/bar';\n", "not target public exports"),
                Arguments.of("src-theme", "import theme from 'echarts/src/theme/light';\n", "not target public exports"),
                Arguments.of("map", "import 'echarts/map/js/china';\n", "not target public exports"),
                Arguments.of("lib-chart", "import 'echarts/lib/chart/bar';\n", "Legacy lib chart/component"),
                Arguments.of("lib-component", "import 'echarts/lib/component/tooltip';\n", "Legacy lib chart/component"),
                Arguments.of("dynamic-src", root + "const module = import('echarts/src/chart/line');\n", "Dynamic require/import"),
                Arguments.of("dynamic-lib", root + "const module = require('echarts/lib/chart/line');\n", "Dynamic require/import"),
                Arguments.of("legacy-option", "const option: echarts.EChartOption = {};\n", "legacy namespace type"),
                Arguments.of("hover-animation", option + "{ series: [{ hoverAnimation: false }] };\n", "maps to emphasis.scale"),
                Arguments.of("hover-offset", option + "{ series: [{ hoverOffset: 8 }] };\n", "maps to emphasis.scaleSize"),
                Arguments.of("clip-overflow", option + "{ series: [{ clipOverflow: false }] };\n", "maps to clip"),
                Arguments.of("clockwise", option + "{ series: [{ clockWise: false }] };\n", "maps to clockwise"),
                Arguments.of("map-type", option + "{ series: [{ mapType: 'china' }] };\n", "maps to map"),
                Arguments.of("map-location", option + "{ series: [{ mapLocation: { x: 1 } }] };\n", "folded into the series"),
                Arguments.of("data-range", option + "{ dataRange: { min: 0, max: 1 } };\n", "maps to visualMap"),
                Arguments.of("graphic-position", option + "{ graphic: { position: [10, 20] } };\n", "graphic element transforms"),
                Arguments.of("graphic-scale", option + "{ graphic: { scale: [2, 2] } };\n", "graphic element transforms"),
                Arguments.of("graphic-origin", option + "{ graphic: { origin: [0, 0] } };\n", "graphic element transforms"),
                Arguments.of("geo-center", option + "{ geo: { center: ['33%', '50%'] } };\n", "percentage center basis"),
                Arguments.of("map-center", option + "{ series: [{ type: 'map', center: ['25%', '60%'] }] };\n", "percentage center basis"),
                Arguments.of("graph-center", option + "{ series: [{ type: 'graph', center: ['50%', '50%'] }] };\n", "percentage center basis"),
                Arguments.of("tree-center", option + "{ series: [{ type: 'tree', center: ['50%', '50%'] }] };\n", "percentage center basis"),
                Arguments.of("formatter-property", option + "{ tooltip: { valueFormatter: (value, index) => String(value) } };\n", "rawDataIndex rather than dataIndex"),
                Arguments.of("formatter-method", option + "{ tooltip: { valueFormatter(value, index) { return String(value); } } };\n", "rawDataIndex rather than dataIndex"),
                Arguments.of("x-start", option + "{ xAxis: { startValue: 2 } };\n", "no longer also treats axis.startValue as min"),
                Arguments.of("y-start", option + "{ yAxis: { startValue: 2 } };\n", "no longer also treats axis.startValue as min"),
                Arguments.of("radius-start", option + "{ radiusAxis: { startValue: 2 } };\n", "no longer also treats axis.startValue as min"),
                Arguments.of("angle-start", option + "{ angleAxis: { startValue: 2 } };\n", "no longer also treats axis.startValue as min"),
                Arguments.of("rich", option + "{ label: { color: 'red', rich: { a: {} } } };\n", "inherit plain label styles"),
                Arguments.of("bar", option + "{ series: [{ type: 'bar' }] };\n", "contains this series shape"),
                Arguments.of("pictorial", option + "{ series: [{ type: 'pictorialBar' }] };\n", "contains this series shape"),
                Arguments.of("candle", option + "{ series: [{ type: 'candlestick' }] };\n", "contains this series shape"),
                Arguments.of("boxplot", option + "{ series: [{ type: 'boxplot' }] };\n", "contains this series shape"),
                Arguments.of("init-namespace", root + "const chart = charts.init(container);\n", "changes the default theme"),
                Arguments.of("init-default", "import charts from 'echarts';\nconst chart = charts.init(container);\n", "changes the default theme")
        );
    }

    @ParameterizedTest(name = "does not mark lookalike {index}")
    @ValueSource(strings = {
            "const config = { hoverAnimation: false };\n",
            "const config = { clipOverflow: false };\n",
            "const config = { position: [1, 2] };\n",
            "const option = { graphic: { position: 1 } };\n",
            "import * as charts from 'echarts'; const option = { position: [1, 2] };\n",
            "import * as charts from 'echarts'; const option = { graphic: { position: { x: 1 } } };\n",
            "import * as charts from 'echarts'; const option = { center: ['50%', '50%'] };\n",
            "import * as charts from 'echarts'; const option = { geo: { center: [1, 2] } };\n",
            "import * as charts from 'echarts'; const option = { type: 'bar' };\n",
            "import * as charts from 'echarts'; const option = { legend: { type: 'bar' } };\n",
            "import * as charts from 'echarts'; const option = { startValue: 1 };\n",
            "import * as charts from 'echarts'; const option = { tooltip: { formatter: value => value } };\n",
            "import * as charts from 'echarts'; const config = { valueFormatter: value => value };\n",
            "import * as charts from 'echarts'; const config = { hoverAnimation: false, clipOverflow: false };\n",
            "import * as charts from 'echarts'; const option = { rich: { a: {} } };\n",
            "import * as charts from '@company/echarts'; charts.init(node);\n",
            "const charts = { init() {} }; charts.init(node);\n",
            "// import 'echarts/src/chart/bar';\nconst x = 1;\n",
            "const docs = 'echarts/lib/chart/bar';\n",
            "import * as charts from 'echarts'; const docs = 'echarts/lib/chart/bar';\n",
            "import 'echarts/core';\n",
            "import 'echarts/charts';\n",
            "import 'echarts/theme/v5.js';\n"
    })
    void leavesUnownedAndNonRiskSyntaxUnmarked(String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                typescript(source, input -> input.path("src/safe.ts")));
    }

    @Test
    void marksReducedRealAntSimpleProOptions() {
        // lgf196/ant-simple-pro@f6613195ab949b067afa57cdf885373d8c6cc58e,
        // vue/src/views/charts/components/pie-option.ts.
        String source = "const option: echarts.EChartOption = { series: [{ type: 'pie', hoverAnimation: false }] };\n";
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                typescript(source, input -> input.path("vue/src/views/charts/components/pie-option.ts")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("maps to emphasis.scale"), after.printAll()))));
    }

    @Test
    void marksReducedRealCovalentLineOptions() {
        // Teradata/covalent@812ab55b4fb701899404d54f5bada274a5c4520d,
        // libs/angular-echarts/line/src/line.component.ts.
        String source = "import { ITdSeries } from '@covalent/echarts/base';\n" +
                        "const option: ITdSeries = { type: 'line', clipOverflow: false, hoverAnimation: true };\n";
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                typescript(source, input -> input.path("libs/angular-echarts/line/src/line.component.ts")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("maps to clip"), after.printAll());
                            assertTrue(after.printAll().contains("maps to emphasis.scale"), after.printAll());
                        })));
    }

    @Test
    void marksOptionsPassedToAProvenChartInstance() {
        String source = "import * as charts from 'echarts';\n" +
                        "const chart = charts.init(container);\n" +
                        "chart.setOption({ series: [{ hoverAnimation: false }] });\n";
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                typescript(source, input -> input.path("src/set-option.ts").after(actual -> actual)
                        .afterRecipe(after -> assertTrue(after.printAll().contains("maps to emphasis.scale"),
                                after.printAll()))));
    }

    @Test
    void ignoresGeneratedSources() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                typescript("import * as charts from 'echarts';\nconst option: charts.EChartsOption = { hoverAnimation: false };\n",
                        input -> input.path("dist/generated.ts")));
    }

    @Test
    void sourceRiskMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import * as charts from 'echarts';\nconst option: charts.EChartsOption = { hoverAnimation: false };\n",
                        input -> input.path("src/idempotent-risk.ts").after(actual -> actual)));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.echarts")
                .scanYamlResources().build();
    }
}
