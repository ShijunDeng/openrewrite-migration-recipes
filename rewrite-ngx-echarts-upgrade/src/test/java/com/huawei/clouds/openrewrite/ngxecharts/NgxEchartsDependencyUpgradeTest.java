package com.huawei.clouds.openrewrite.ngxecharts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.json.Assertions.json;

class NgxEchartsDependencyUpgradeTest implements RewriteTest {
    private static final String STRICT =
            "com.huawei.clouds.openrewrite.ngxecharts.UpgradeNgxEchartsTo20_0_2";

    @ParameterizedTest(name = "upgrades XLSX declaration {0}")
    @MethodSource("selectedDeclarations")
    void upgradesEveryVisibleVersionWithSupportedScalarPrefix(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(STRICT)),
                json("{\"dependencies\":{\"ngx-echarts\":\"" + declaration + "\"}}",
                        "{\"dependencies\":{\"ngx-echarts\":\"20.0.2\"}}",
                        source -> source.path("package.json")));
    }

    static Stream<String> selectedDeclarations() {
        return UpgradeSelectedNgxEchartsDependency.SOURCES.stream()
                .sorted()
                .flatMap(version -> Stream.of(version, "^" + version, "~" + version));
    }

    @Test
    void upgradesAllDirectDependencySectionsAndWorkspaceManifests() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(STRICT)),
                json("{\"dependencies\":{\"ngx-echarts\":\"5.2.2\"},\"devDependencies\":{\"ngx-echarts\":\"^6.0.1\"},\"peerDependencies\":{\"ngx-echarts\":\"~14.0.0\"},\"optionalDependencies\":{\"ngx-echarts\":\"15.0.3\"}}",
                        "{\"dependencies\":{\"ngx-echarts\":\"20.0.2\"},\"devDependencies\":{\"ngx-echarts\":\"20.0.2\"},\"peerDependencies\":{\"ngx-echarts\":\"20.0.2\"},\"optionalDependencies\":{\"ngx-echarts\":\"20.0.2\"}}",
                        source -> source.path("package.json")),
                json("{\"name\":\"@example/charts\",\"peerDependencies\":{\"ngx-echarts\":\"^16.0.0\"}}",
                        "{\"name\":\"@example/charts\",\"peerDependencies\":{\"ngx-echarts\":\"20.0.2\"}}",
                        source -> source.path("packages/charts/package.json")));
    }

    @Test
    void upgradesThreeFixedRealRepositoryManifestsWithoutChangingPeers() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(STRICT)),
                // damoqiongqiu/NiceFish@4454db9074a614ec9cdf3661cc5a05273d393b11
                json("{\"dependencies\":{\"@angular/core\":\"16.2.0\",\"echarts\":\"5.4.2\",\"ngx-echarts\":\"15.0.3\",\"rxjs\":\"6.5.3\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"16.2.0\",\"echarts\":\"5.4.2\",\"ngx-echarts\":\"20.0.2\",\"rxjs\":\"6.5.3\"}}",
                        source -> source.path("nicefish/package.json")),
                // careydevelopment/careydevelopmentcrm@7d6f44b88e3fcbb54673b896c2f68d48a9f58dd4
                json("{\"dependencies\":{\"@angular/core\":\"~11.1.1\",\"echarts\":\"^5.0.2\",\"ngx-echarts\":\"^6.0.1\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"~11.1.1\",\"echarts\":\"^5.0.2\",\"ngx-echarts\":\"20.0.2\"}}",
                        source -> source.path("carey/package.json")),
                // uilibrary/matx-angular@6b16bbe0efa9c387e6d21141981fbfe01a8043e4
                json("{\"dependencies\":{\"@angular/core\":\"^14.2.0\",\"echarts\":\"^5.3.3\",\"ngx-echarts\":\"^14.0.0\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"^14.2.0\",\"echarts\":\"^5.3.3\",\"ngx-echarts\":\"20.0.2\"}}",
                        source -> source.path("matx/package.json")));
    }

    @ParameterizedTest(name = "leaves unsupported npm declaration {0}")
    @ValueSource(strings = {
            ">=14.0.0 <20", "14.0.0 || 15.0.3", "14.0.0 - 16.0.0", "14.x", "*",
            "workspace:^16.0.0", "workspace:*", "npm:@example/charts@16.0.0",
            "github:xieziyu/ngx-echarts#v16.0.0", "git+ssh://git@github.com/xieziyu/ngx-echarts.git#v16.0.0",
            "file:../ngx-echarts", "link:../ngx-echarts", "https://example.test/ngx-echarts.tgz",
            "latest", "next", "$chartsVersion", "${chartsVersion}", "v16.0.0", "=16.0.0",
            "16.0.0-beta.1", "16.0.0+build.1", " 16.0.0", "16.0.0 "
    })
    void leavesComplexDynamicAndProtocolDeclarationsUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(STRICT)),
                json("{\"dependencies\":{\"ngx-echarts\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "does not invent unlisted version {0}")
    @ValueSource(strings = {
            "4.2.2", "5.2.1", "6.0.0", "7.0.0", "8.0.0", "13.0.0", "15.0.1",
            "16.2.0", "17.2.0", "18.0.0", "19.0.0", "20.0.0", "20.0.1",
            "20.0.2", "20.0.3", "21.0.0", "22.0.0"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(STRICT)),
                json("{\"dependencies\":{\"ngx-echarts\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void leavesMetadataNestedOwnersLockfilesOtherJsonAndSimilarPackagesUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(STRICT)),
                json("{\"overrides\":{\"ngx-echarts\":\"16.0.0\"},\"resolutions\":{\"ngx-echarts\":\"15.0.3\"},\"catalog\":{\"ngx-echarts\":\"14.0.0\"}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"ngx-echarts\":\"16.0.0\"}},\"node_modules/ngx-echarts\":{\"version\":\"16.0.0\"}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"ngx-echarts\":\"16.0.0\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                json("{\"dependencies\":{\"@example/ngx-echarts\":\"16.0.0\",\"ngx-echarts-testing\":\"16.0.0\",\"echarts\":\"5.4.2\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(STRICT))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"ngx-echarts\":\"~15.0.2\"}}",
                        "{\"dependencies\":{\"ngx-echarts\":\"20.0.2\"}}",
                        source -> source.path("package.json")));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxecharts")
                .scanYamlResources().build();
    }
}
