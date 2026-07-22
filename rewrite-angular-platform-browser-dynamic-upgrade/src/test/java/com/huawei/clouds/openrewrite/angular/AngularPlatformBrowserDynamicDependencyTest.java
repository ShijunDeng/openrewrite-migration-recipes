package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class AngularPlatformBrowserDynamicDependencyTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.angular.UpgradeAngularPlatformBrowserDynamicTo20_3_26";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "upgrades visible declaration {0}")
    @MethodSource("selectedDeclarations")
    void upgradesEveryVisibleExactCaretAndTildeDeclaration(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"20.3.26\"}}",
                source -> source.path("package.json")));
    }

    static Stream<Arguments> selectedDeclarations() {
        return Stream.of("10.0.14", "10.2.5", "11.2.14", "12.2.10", "12.2.13",
                        "12.2.14", "12.2.16", "12.2.17", "13.1.3", "13.2.6")
                .flatMap(version -> Stream.of(version, "^" + version, "~" + version))
                .map(Arguments::of);
    }

    @Test
    void upgradesEveryDirectDependencySection() {
        rewriteRun(json(
                """
                {"dependencies":{"@angular/platform-browser-dynamic":"10.0.14"},"devDependencies":{"@angular/platform-browser-dynamic":"^11.2.14"},"peerDependencies":{"@angular/platform-browser-dynamic":"~12.2.17"},"optionalDependencies":{"@angular/platform-browser-dynamic":"13.2.6"}}
                """,
                """
                {"dependencies":{"@angular/platform-browser-dynamic":"20.3.26"},"devDependencies":{"@angular/platform-browser-dynamic":"20.3.26"},"peerDependencies":{"@angular/platform-browser-dynamic":"20.3.26"},"optionalDependencies":{"@angular/platform-browser-dynamic":"20.3.26"}}
                """,
                source -> source.path("package.json")));
    }

    @Test
    void upgradesPinnedRealRepositoryDeclarations() {
        rewriteRun(
                // apache/nifi 59cff970ca8b98ee51ae4418cf4de6830fa28c37.
                json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"11.2.14\"}}",
                        "{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"20.3.26\"}}",
                        source -> source.path("apache/nifi/nifi-registry-web-ui/package.json")),
                // HybridShivam/pokedex-angular-app a39ca00439e160069ea711ee98326288f9a1443e.
                json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"~10.2.5\"}}",
                        "{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"20.3.26\"}}",
                        source -> source.path("HybridShivam/pokedex-angular-app/package.json")),
                // elastic/apm-agent-rum-js 997138a38ab3253072e710a97343dea240447b7c.
                json("{\"devDependencies\":{\"@angular/platform-browser-dynamic\":\"^12.2.17\"}}",
                        "{\"devDependencies\":{\"@angular/platform-browser-dynamic\":\"20.3.26\"}}",
                        source -> source.path("elastic/apm-agent-rum-js/package.json"))
        );
    }

    @Test
    void leavesComplexProtocolsDecoratedAndUnlistedDeclarationsUntouched() {
        rewriteRun(
                pkg("ranges/package.json", ">=10 <14"), pkg("or/package.json", "10.0.14 || 13.2.6"),
                pkg("hyphen/package.json", "10.0.14 - 13.2.6"), pkg("workspace/package.json", "workspace:^12.2.17"),
                pkg("alias/package.json", "npm:@angular/platform-browser-dynamic@12.2.17"),
                pkg("file/package.json", "file:../dynamic"), pkg("tag/package.json", "latest"),
                pkg("variable/package.json", "${ANGULAR_VERSION}"), pkg("v/package.json", "v12.2.17"),
                pkg("equals/package.json", "=12.2.17"), pkg("pre/package.json", "12.2.17-next.0"),
                pkg("build/package.json", "12.2.17+vendor.1"), pkg("unlisted/package.json", "14.0.0"),
                pkg("newer/package.json", "21.0.0"), pkg("target/package.json", "20.3.26")
        );
    }

    @Test
    void leavesMetadataLockfilesOtherJsonAndSimilarPackagesUntouched() {
        rewriteRun(
                json("{\"overrides\":{\"@angular/platform-browser-dynamic\":\"12.2.17\"},\"peerDependenciesMeta\":{\"@angular/platform-browser-dynamic\":{\"optional\":true}}}", source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"12.2.17\"}}}}", source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"12.2.17\"}}", source -> source.path("fixtures/data.json")),
                json("{\"dependencies\":{\"@angular/platform-browser\":\"12.2.17\",\"@company/platform-browser-dynamic\":\"12.2.17\"}}", source -> source.path("package.json"))
        );
    }

    @Test
    void upgradeIsIdempotentAndDiscoverable() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"12.2.17\"}}",
                        "{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"20.3.26\"}}",
                        source -> source.path("package.json")));
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(UPGRADE);
        assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()));
        assertEquals(UPGRADE, recipe.getName());
    }

    private static org.openrewrite.test.SourceSpecs pkg(String path, String declaration) {
        return json("{\"dependencies\":{\"@angular/platform-browser-dynamic\":\"" + declaration + "\"}}",
                source -> source.path(path));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources().build();
    }
}
