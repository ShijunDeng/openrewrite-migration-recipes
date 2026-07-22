package com.huawei.clouds.openrewrite.echarts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.json.Assertions.json;

class EChartsDependencyUpgradeTest implements RewriteTest {
    private static final String RECIPE = "com.huawei.clouds.openrewrite.echarts.UpgradeEChartsTo6_1_0";

    @ParameterizedTest(name = "upgrades {0} {1}")
    @MethodSource("selectedDeclarations")
    void upgradesOnlyEveryVisibleSpreadsheetDeclaration(String section, String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), json(
                "{\"" + section + "\":{\"echarts\":\"" + declaration + "\"}}",
                "{\"" + section + "\":{\"echarts\":\"6.1.0\"}}",
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest(name = "preserves unsupported {0}")
    @ValueSource(strings = {
            "4.0.0", "4.7.0", "4.8.1", "4.10.0", "5.0.0", "5.0.1", "5.0.3", "5.1.0",
            "5.1.1", "5.1.2", "5.2.0", "5.2.3", "5.3.2", "5.3.4", "5.4.2", "5.4.3",
            "5.5.0", "5.5.1", "5.6.0", "5.7.0", "6.0.0", "6.0.1", "6.1.0", "^6.1.0",
            "~6.1.0", "7.0.0", "5.x", "5.4", "*", "latest", "next", "5.4.1-beta.0",
            ">=5.4.1", ">=5.4.1 <6", "^5.4.1 || ^6.0.0", "4.9.0 - 5.4.1", "=4.9.0",
            "v4.9.0", "^v4.9.0", "~v5.4.1"
    })
    void preservesUnlistedComplexAndNonCanonicalDeclarations(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), json(
                "{\"dependencies\":{\"echarts\":\"" + declaration + "\"}}",
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest(name = "preserves external {0}")
    @ValueSource(strings = {
            "workspace:^5.4.1", "workspace:*", "npm:echarts@5.4.1", "file:../echarts", "link:../echarts",
            "portal:../echarts", "git+https://github.com/apache/echarts.git#5.4.1",
            "github:apache/echarts#5.4.1", "https://example.test/echarts.tgz", "catalog:charts",
            "${echarts.version}", "{{echartsVersion}}"
    })
    void preservesProtocolsAliasesCatalogsAndDynamicDeclarations(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), json(
                "{\"dependencies\":{\"echarts\":\"" + declaration + "\"}}",
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest(name = "preserves non-direct section {0}")
    @ValueSource(strings = {"overrides", "resolutions", "pnpm", "bundledDependencies", "engines", "scripts"})
    void preservesNonDirectDependencySections(String section) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), json(
                "{\"" + section + "\":{\"echarts\":\"5.4.1\"}}",
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest(name = "preserves non-manifest {0}")
    @ValueSource(strings = {"package-lock.json", "npm-shrinkwrap.json", "bower.json", "config/package.json.txt", "dependencies.json"})
    void preservesLockfilesAndOtherJson(String path) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                json("{\"dependencies\":{\"echarts\":\"5.4.1\"}}", source -> source.path(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dist/package.json", "build/package.json", "generated/package.json",
            "node_modules/example/package.json", ".angular/cache/package.json"})
    void preservesGeneratedAndInstalledManifests(String path) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)),
                json("{\"dependencies\":{\"echarts\":\"5.4.1\"}}", source -> source.path(path)));
    }

    @Test
    void upgradesReducedRealAntSimpleProManifest() {
        // lgf196/ant-simple-pro@f6613195ab949b067afa57cdf885373d8c6cc58e, vue/package.json.
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), json(
                "{\"dependencies\":{\"dayjs\":\"^1.10.6\",\"echarts\":\"^4.9.0\",\"element-resize-detector\":\"^1.2.3\"}}",
                "{\"dependencies\":{\"dayjs\":\"^1.10.6\",\"echarts\":\"6.1.0\",\"element-resize-detector\":\"^1.2.3\"}}",
                source -> source.path("vue/package.json")
        ));
    }

    @Test
    void upgradesReducedRealEChartsFiveManifest() {
        // apache/echarts@de46c55144e695240305d38e8ea874e03e323506, package.json.
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), json(
                "{\"name\":\"echarts\",\"devDependencies\":{\"typescript\":\"4.1.3\"},\"peerDependencies\":{\"echarts\":\"5.0.2\"}}",
                "{\"name\":\"echarts\",\"devDependencies\":{\"typescript\":\"4.1.3\"},\"peerDependencies\":{\"echarts\":\"6.1.0\"}}",
                source -> source.path("fixtures/package.json")
        ));
    }

    @Test
    void preservesSimilarPackageNestedAndNonScalarMembers() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECIPE)), json(
                "{\"dependencies\":{\"echarts-gl\":\"5.4.1\",\"echarts\":{\"version\":\"5.4.1\"}},\"metadata\":{\"dependencies\":{\"echarts\":\"5.4.1\"}}}",
                source -> source.path("package.json")
        ));
    }

    private static Stream<Arguments> selectedDeclarations() {
        return Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                .flatMap(section -> Stream.of(
                                "4.8.0", "4.9.0", "5.0.2", "5.2.1", "5.2.2",
                                "5.3.0", "5.3.1", "5.3.3", "5.4.0", "5.4.1")
                        .flatMap(version -> Stream.of(version, "^" + version, "~" + version)
                                .map(declaration -> Arguments.of(section, declaration))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.echarts")
                .scanYamlResources().build();
    }
}
