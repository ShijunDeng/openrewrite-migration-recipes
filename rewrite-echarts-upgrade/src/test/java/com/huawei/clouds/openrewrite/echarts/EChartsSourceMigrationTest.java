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

import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class EChartsSourceMigrationTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.echarts.MigrateDeterministicEChartsSourceTo6";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "normalizes static module {0}")
    @MethodSource("staticImports")
    void normalizesOnlyExactStaticLegacyEntries(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                typescript(before, after, source -> source.path("src/entry.ts")));
    }

    static Stream<Arguments> staticImports() {
        return Stream.of(
                Arguments.of("import * as charts from 'echarts/lib/echarts';\n", "import * as charts from 'echarts';\n"),
                Arguments.of("import charts from \"echarts/lib/echarts.js\";\n", "import charts from \"echarts\";\n"),
                Arguments.of("import { init } from 'echarts/src/echarts';\n", "import { init } from 'echarts';\n"),
                Arguments.of("import type charts from 'echarts/src/echarts.ts';\n", "import type charts from 'echarts';\n"),
                Arguments.of("import 'echarts/lib/echarts';\n", "import 'echarts';\n"),
                Arguments.of("import theme from 'echarts/src/theme/light';\n", "import theme from 'echarts/theme/rainbow.js';\n"),
                Arguments.of("import \"echarts/src/theme/light.ts\";\n", "import \"echarts/theme/rainbow.js\";\n")
        );
    }

    @ParameterizedTest(name = "renames proven type {0}")
    @MethodSource("provenTypes")
    void renamesOnlyTypesOnProvenEChartsBindings(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                typescript(before, after, source -> source.path("src/options.ts")));
    }

    static Stream<Arguments> provenTypes() {
        return Stream.of(
                Arguments.of("import * as echarts from 'echarts';\ntype O = echarts.EChartOption;\n",
                        "import * as echarts from 'echarts';\ntype O = echarts.EChartsOption;\n"),
                Arguments.of("import * as charts from 'echarts/lib/echarts';\nconst o: charts.EChartOption = {};\n",
                        "import * as charts from 'echarts';\nconst o: charts.EChartsOption = {};\n"),
                Arguments.of("import charts from 'echarts';\ntype O = charts.EChartOption;\n",
                        "import charts from 'echarts';\ntype O = charts.EChartsOption;\n"),
                Arguments.of("import charts from 'echarts/src/echarts.ts';\nfunction draw(o: charts.EChartOption) {}\n",
                        "import charts from 'echarts';\nfunction draw(o: charts.EChartsOption) {}\n")
        );
    }

    @ParameterizedTest(name = "leaves unsafe source untouched {index}")
    @ValueSource(strings = {
            "const charts = local; type O = charts.EChartOption;\n",
            "import * as charts from '@company/echarts'; type O = charts.EChartOption;\n",
            "import * as charts from 'echarts'; const charts = local; type O = charts.EChartOption;\n",
            "import { EChartOption } from 'echarts'; type O = EChartOption;\n",
            "import * as charts from 'echarts'; type O = charts.EChartOptionText;\n",
            "import * as charts from 'echarts'; const key = 'EChartOption';\n",
            "import * as charts from 'echarts'; // charts.EChartOption\nconst x = 1;\n",
            "const x = import('echarts/lib/echarts');\n",
            "const x = require('echarts/src/echarts.ts');\n",
            "const x = import(`echarts/src/theme/light`);\n",
            "import x from 'echarts/lib/custom';\n",
            "import x from 'echarts/src/chart/bar';\n",
            "import x from 'echarts/theme/light';\n",
            "import x from 'echarts/theme/dark.js';\n",
            "import x from 'echarts/core';\n",
            "import x from 'echarts/charts';\n",
            "import * as charts from 'echarts/charts'; type O = charts.EChartOption;\n",
            "import x from 'echarts/components';\n",
            "import x from 'echarts/renderers';\n",
            "import x from 'echarts/features';\n",
            "import x from 'echarts/types/dist/shared';\n",
            "import x from 'echarts-extension-amap';\n",
            "import x from '@covalent/echarts';\n",
            "const docs = \"import x from 'echarts/lib/echarts'\";\n",
            "// import x from 'echarts/src/theme/light';\nconst x = 1;\n"
    })
    void preservesDynamicLoadersCommentsStringsPublicAndUnknownEntries(String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                typescript(source, input -> input.path("src/safe.ts")));
    }

    @Test
    void migratesReducedRealWaldurEntry() {
        // waldur/waldur-homeport@2726280ccadf38a4b13eda1e353b5364f5b82d83, src/echarts/index.ts.
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), typescript(
                "import * as echarts from 'echarts/lib/echarts';\nimport 'echarts/lib/component/legend';\nimport 'echarts/lib/chart/bar';\nexport default echarts;\n",
                "import * as echarts from 'echarts';\nimport 'echarts/lib/component/legend';\nimport 'echarts/lib/chart/bar';\nexport default echarts;\n",
                source -> source.path("src/echarts/index.ts")
        ));
    }

    @Test
    void migrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                javascript("import * as charts from 'echarts/lib/echarts.js';\n",
                        "import * as charts from 'echarts';\n", source -> source.path("src/idempotent.js")));
    }

    @Test
    void ignoresGeneratedSources() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                typescript("import * as charts from 'echarts/lib/echarts';\ntype O = charts.EChartOption;\n",
                        source -> source.path("dist/generated.ts")));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.echarts")
                .scanYamlResources().build();
    }
}
