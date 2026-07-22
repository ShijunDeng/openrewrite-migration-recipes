package com.huawei.clouds.openrewrite.ngxecharts;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.test.SourceSpecs.text;

class NgxEchartsAutomaticMigrationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.ngxecharts.MigrateDeterministicNgxEcharts20";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "normalizes public deep import {0}")
    @MethodSource("publicDeepImports")
    void movesProvenPublicNamedImportsToPackageRoot(String path, String names) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTO)),
                typescript("import { " + names + " } from '" + path + "';\n",
                        "import { " + names + " } from 'ngx-echarts';\n",
                        source -> source.path("src/chart.ts")));
    }

    static Stream<Arguments> publicDeepImports() {
        return Stream.of(
                Arguments.of("ngx-echarts/lib/ngx-echarts.directive", "NgxEchartsDirective, ThemeOption"),
                Arguments.of("ngx-echarts/lib/ngx-echarts.module.js", "NgxEchartsModule"),
                Arguments.of("ngx-echarts/lib/config", "NgxEchartsConfig, NGX_ECHARTS_CONFIG"),
                Arguments.of("ngx-echarts/lib/provide.js", "provideEchartsCore as provideCharts"),
                Arguments.of("ngx-echarts/public-api", "NgxEchartsDirective as ChartsDirective")
        );
    }

    @Test
    void preservesQuoteStyleWhenNormalizingPublicImport() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTO)),
                typescript("import { NgxEchartsModule } from \"ngx-echarts/lib/ngx-echarts.module\";\n",
                        "import { NgxEchartsModule } from \"ngx-echarts\";\n",
                        source -> source.path("src/module.ts")));
    }

    @Test
    void leavesUnknownDefaultNamespaceAndThirdPartyImportsUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTO)),
                typescript("import { InternalThing } from 'ngx-echarts/lib/internal';\n",
                        source -> source.path("src/internal.ts")),
                typescript("import * as directive from 'ngx-echarts/lib/ngx-echarts.directive';\n",
                        source -> source.path("src/namespace.ts")),
                typescript("import Charts from 'ngx-echarts/public-api';\n",
                        source -> source.path("src/default.ts")),
                typescript("import { NgxEchartsDirective } from '@company/ngx-echarts/lib/ngx-echarts.directive';\n",
                        source -> source.path("src/company.ts")));
    }

    @Test
    void removesOnlyLiteralObsoleteInputFromProvenChartHosts() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTO)),
                text("<div echarts [detectEventChanges]=\"true\" [options]=\"options\"></div>\n" +
                     "<echarts detectEventChanges='false'></echarts>\n",
                        "<div echarts [options]=\"options\"></div>\n" +
                        "<echarts></echarts>\n",
                        source -> source.path("src/chart.component.html")));
    }

    @Test
    void leavesDynamicCommentsAndUnrelatedSameNamedInputsUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTO)),
                text("<!-- <div echarts [detectEventChanges]=\"true\"></div> -->\n" +
                     "<div echarts [detectEventChanges]=\"changePolicy\"></div>\n" +
                     "<company-chart [detectEventChanges]=\"true\"></company-chart>\n" +
                     "<div class=\"echarts sample\" [detectEventChanges]=\"true\"></div>\n" +
                     "<div echarts title='sample [detectEventChanges]=\"true\"'></div>\n",
                        source -> source.path("src/boundary.component.html")));
    }

    @Test
    void automaticMigrationsAreIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTO))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { NgxEchartsDirective } from 'ngx-echarts/lib/ngx-echarts.directive';\n",
                        "import { NgxEchartsDirective } from 'ngx-echarts';\n",
                        source -> source.path("src/chart.ts")),
                text("<div echarts [detectEventChanges]=\"false\"></div>",
                        "<div echarts></div>",
                        source -> source.path("src/chart.html")));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxecharts")
                .scanYamlResources().build();
    }
}
