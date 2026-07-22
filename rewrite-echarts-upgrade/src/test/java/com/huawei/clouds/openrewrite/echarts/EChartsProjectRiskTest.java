package com.huawei.clouds.openrewrite.echarts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class EChartsProjectRiskTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.echarts.AuditECharts6ProjectCompatibility";

    @ParameterizedTest(name = "marks unresolved declaration {0}")
    @MethodSource("unresolvedDeclarations")
    void marksEveryKindOfUnresolvedDirectDeclaration(String declaration, String message) {
        assertJsonMarker("{\"dependencies\":{\"echarts\":\"" + declaration + "\"}}",
                "package.json", message);
    }

    static Stream<Arguments> unresolvedDeclarations() {
        return Stream.of(
                "4.7.0", "4.8.0", "^4.9.0", "~5.0.2", "5.1.0", "5.2.3", "5.3.2", "5.4.2",
                "5.4.3", "5.5.0", "5.6.0", "6.0.0", "^6.0.0", "^6.1.0", "~6.1.0", "7.0.0"
        ).map(value -> Arguments.of(value, "scalar was not changed"));
    }

    @ParameterizedTest(name = "marks dynamic declaration {0}")
    @ValueSource(strings = {
            "workspace:^5.4.1", "npm:echarts@5.4.1", "file:../echarts", "catalog:charts", "latest",
            "next", "${echarts.version}", "git+https://github.com/apache/echarts.git#5.4.1"
    })
    void marksProtocolAliasTagAndDynamicDeclarations(String declaration) {
        assertJsonMarker("{\"dependencies\":{\"echarts\":\"" + declaration + "\"}}",
                "package.json", "dynamic ECharts declaration");
    }

    @ParameterizedTest(name = "marks complex declaration {0}")
    @ValueSource(strings = {"5.x", "5.4", ">=5.4.1", ">=5.4.1 <6", "^5.4.1 || ^6.0.0", "4.9.0 - 5.4.1"})
    void marksComplexRanges(String declaration) {
        assertJsonMarker("{\"dependencies\":{\"echarts\":\"" + declaration + "\"}}",
                "package.json", "Complex ECharts range");
    }

    @ParameterizedTest(name = "marks companion {0} in {1}")
    @MethodSource("companions")
    void marksCompanionPackagesInAllDirectSections(String companion, String section) {
        String message = "@types/echarts".equals(companion) ? "publishes its own declarations" :
                "independent compatibility matrix";
        assertJsonMarker("{\"dependencies\":{\"echarts\":\"6.1.0\"},\"" + section + "\":{\"" +
                         companion + "\":\"1.0.0\"}}", "package.json", message);
    }

    static Stream<Arguments> companions() {
        return Stream.of("ngx-echarts", "echarts-for-react", "vue-echarts", "@types/echarts", "echarts-gl", "echarts-stat")
                .flatMap(name -> Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                        .map(section -> Arguments.of(name, section)));
    }

    @ParameterizedTest(name = "marks old tool {0}")
    @MethodSource("legacyTools")
    void marksLegacyToolingWithDirectECharts(String tool, String version) {
        assertJsonMarker("{\"dependencies\":{\"echarts\":\"6.1.0\"},\"devDependencies\":{\"" + tool +
                         "\":\"" + version + "\"}}", "package.json", "older toolchain");
    }

    static Stream<Arguments> legacyTools() {
        return Stream.of(
                Arguments.of("typescript", "4.6.4"), Arguments.of("webpack", "4.47.0"),
                Arguments.of("webpack-cli", "4.10.0"), Arguments.of("webpack-dev-server", "4.15.0"),
                Arguments.of("rollup", "2.79.2"), Arguments.of("vite", "2.9.18"),
                Arguments.of("parcel", "1.12.5"), Arguments.of("jest", "28.1.3"),
                Arguments.of("ts-jest", "28.0.8"), Arguments.of("babel-jest", "28.1.3")
        );
    }

    @ParameterizedTest(name = "marks physical config {0}")
    @MethodSource("physicalConfigs")
    void marksPhysicalSourceAndDistributionMappings(String path, String value) {
        assertJsonMarker("{\"compilerOptions\":{\"paths\":{\"echarts-owned\":[\"" + value + "\"]}}}",
                path, "pins src/lib/map internals");
    }

    static Stream<Arguments> physicalConfigs() {
        return Stream.of(
                Arguments.of("tsconfig.json", "node_modules/echarts/src/echarts.ts"),
                Arguments.of("tsconfig.app.json", "echarts/src/chart/bar"),
                Arguments.of("jsconfig.json", "echarts/lib/echarts"),
                Arguments.of("jest.config.json", "node_modules/echarts/lib/chart/line"),
                Arguments.of("webpack.config.json", "echarts/map/js/china"),
                Arguments.of("vite.config.json", "node_modules/echarts/map/json/china.json"),
                Arguments.of("rollup.config.json", "echarts/lib/component/tooltip"),
                Arguments.of("parcel.config.json", "echarts/src/theme/light")
        );
    }

    @ParameterizedTest(name = "marks legacy resolution {0}")
    @ValueSource(strings = {"classic", "node", "node10", "NODE", "Classic"})
    void marksLegacyResolutionOnlyWhenConfigReferencesECharts(String resolution) {
        assertJsonMarker("{\"compilerOptions\":{\"moduleResolution\":\"" + resolution +
                         "\",\"types\":[\"echarts\"]}}", "tsconfig.json", "legacy module resolution");
    }

    @ParameterizedTest(name = "does not mark safe project {index}")
    @ValueSource(strings = {
            "{\"dependencies\":{\"echarts\":\"6.1.0\"}}",
            "{\"dependencies\":{\"ngx-echarts\":\"20.0.0\"}}",
            "{\"devDependencies\":{\"@types/echarts\":\"4.9.22\"}}",
            "{\"overrides\":{\"echarts\":\"5.4.1\"}}",
            "{\"dependencies\":{\"echarts-gl\":\"2.0.9\"}}",
            "{\"dependencies\":{\"@company/echarts\":\"5.4.1\"}}",
            "{\"metadata\":{\"echarts\":\"5.4.1\"}}"
    })
    void leavesTargetsAndUnownedManifestsUnmarked(String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                json(source, input -> input.path("package.json")));
    }

    @Test
    void leavesUnrelatedTsconfigResolutionUnmarked() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                json("{\"compilerOptions\":{\"moduleResolution\":\"node\",\"types\":[\"jest\"]}}",
                        input -> input.path("tsconfig.json")));
    }

    @Test
    void leavesNestedDependencyLikeObjectsUnmarked() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                json("{\"dependencies\":{\"echarts\":\"6.1.0\"},\"metadata\":{\"dependencies\":{\"echarts-stat\":\"1.0.0\",\"typescript\":\"4.6.4\"}}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dist/package.json", "generated/package.json", "node_modules/pkg/package.json"})
    void leavesGeneratedManifestRisksUnmarked(String path) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                json("{\"dependencies\":{\"echarts\":\"5.6.0\",\"echarts-stat\":\"1.0.0\"}}",
                        source -> source.path(path)));
    }

    @Test
    void leavesGeneratedConfigurationRisksUnmarked() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                json("{\"compilerOptions\":{\"moduleResolution\":\"classic\",\"paths\":{\"e\":[\"echarts/src/echarts\"]}}}",
                        source -> source.path("build/tsconfig.generated.json")));
    }

    @Test
    void marksReducedRealCovalentCompanionManifest() {
        // Teradata/covalent@812ab55b4fb701899404d54f5bada274a5c4520d, package.json.
        assertJsonMarker("{\"dependencies\":{\"echarts\":\"6.1.0\",\"echarts-stat\":\"^1.2.0\"}}",
                "package.json", "independent compatibility matrix");
    }

    @Test
    void projectRiskMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"echarts\":\"5.6.0\",\"echarts-stat\":\"1.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)));
    }

    private void assertJsonMarker(String before, String path, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                json(before, source -> source.path(path).after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.echarts")
                .scanYamlResources().build();
    }
}
