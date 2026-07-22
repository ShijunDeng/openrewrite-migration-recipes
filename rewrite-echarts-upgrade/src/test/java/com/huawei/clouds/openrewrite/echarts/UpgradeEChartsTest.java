package com.huawei.clouds.openrewrite.echarts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeEChartsTest implements RewriteTest {
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.echarts.MigrateEChartsTo6_1_0";
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.echarts.UpgradeEChartsTo6_1_0";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.echarts.MigrateDeterministicEChartsSourceTo6";
    private static final String AUDIT_RECIPE =
            "com.huawei.clouds.openrewrite.echarts.AuditECharts6SourceCompatibility";
    private static final String COMPANION_RECIPE =
            "com.huawei.clouds.openrewrite.echarts.AuditEChartsCompanionDependencies";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(MIGRATION_RECIPE));
    }

    @ParameterizedTest(name = "upgrades {0} ECharts declaration {1}")
    @MethodSource("selectedDeclarations")
    void upgradesEverySelectedDeclaration(String section, String declaration) {
        rewriteRun(json(
                "{\"" + section + "\":{\"echarts\":\"" + declaration + "\"}}",
                "{\"" + section + "\":{\"echarts\":\"6.1.0\"}}",
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"=4.9.0", "v4.9.0", "^v4.9.0", "=5.4.3", "v5.4.3", "^v5.4.3"})
    void upgradesExplicitRegistryPrefixes(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"echarts\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"echarts\":\"6.1.0\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesAntSimpleProRealPackage() {
        // Reduced from lgf196/ant-simple-pro at f6613195ab949b067afa57cdf885373d8c6cc58e.
        // https://github.com/lgf196/ant-simple-pro/blob/f6613195ab949b067afa57cdf885373d8c6cc58e/vue/package.json
        rewriteRun(json(
                "{\"dependencies\":{\"dayjs\":\"^1.10.6\",\"echarts\":\"^4.9.0\",\"element-resize-detector\":\"^1.2.3\"}}",
                "{\"dependencies\":{\"dayjs\":\"^1.10.6\",\"echarts\":\"6.1.0\",\"element-resize-detector\":\"^1.2.3\"}}",
                source -> source.path("vue/package.json")
        ));
    }

    @Test
    void upgradesCovalentRealPackage() {
        // Reduced from Teradata/covalent at 812ab55b4fb701899404d54f5bada274a5c4520d.
        // https://github.com/Teradata/covalent/blob/812ab55b4fb701899404d54f5bada274a5c4520d/package.json
        rewriteRun(json(
                "{\"dependencies\":{\"echarts\":\"^5.4.3\",\"echarts-stat\":\"^1.2.0\"}}",
                "{\"dependencies\":{\"echarts\":\"6.1.0\",\"echarts-stat\":\"^1.2.0\"}}",
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest(name = "preserves unsupported declaration {0}")
    @ValueSource(strings = {
            "4.7.0", "5.0.0", "5.0.1", "5.1.0", "5.3.2", "5.7.0",
            "6.0.0", "6.1.0", "^6.1.0", "7.0.0", "5.x", ">=5.4.1 <6", "^5.4.1 || ^6.0.0",
            "5.4.3-beta.0", "latest"
    })
    void preservesUnsupportedDeclarations(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"echarts\":\"" + declaration + "\"}}",
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "workspace:^5.4.3", "npm:echarts@5.4.3", "file:../echarts",
            "git+https://github.com/apache/echarts.git#5.4.3", "https://example.test/echarts.tgz"
    })
    void preservesProtocolsAliasesAndExternalReferences(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"echarts\":\"" + declaration + "\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void preservesNestedMetadataNonScalarValuesLockfilesAndOtherJson() {
        rewriteRun(
                json(
                        "{\"overrides\":{\"echarts\":\"5.4.3\"},\"dependencies\":{\"echarts\":{\"version\":\"5.4.3\"}},\"devDependencies\":{\"echarts\":[\"5.4.3\"]}}",
                        source -> source.path("package.json")
                ),
                json("{\"dependencies\":{\"echarts\":\"5.4.3\"}}", source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"echarts\":\"5.4.3\"}}", source -> source.path("config/dependencies.json"))
        );
    }

    @Test
    void migratesWaldurLegacyFullBuildImport() {
        // Reduced from waldur/waldur-homeport at 2726280ccadf38a4b13eda1e353b5364f5b82d83.
        // https://github.com/waldur/waldur-homeport/blob/2726280ccadf38a4b13eda1e353b5364f5b82d83/src/echarts/index.ts
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import * as echarts from 'echarts/lib/echarts';
                        import 'echarts/lib/component/legend';
                        import 'echarts/lib/chart/bar';
                        export default echarts;
                        """,
                        """
                        import * as echarts from 'echarts';
                        import 'echarts/lib/component/legend';
                        import 'echarts/lib/chart/bar';
                        export default echarts;
                        """,
                        source -> source.path("src/echarts/index.ts")
                )
        );
    }

    @Test
    void migratesLegacyFullBuildDynamicRequireAndLightThemeImports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        const echarts = require("echarts/src/echarts.ts");
                        const lazy = import('echarts/lib/echarts.js');
                        import theme from 'echarts/src/theme/light';
                        """,
                        """
                        const echarts = require("echarts");
                        const lazy = import('echarts');
                        import theme from 'echarts/theme/rainbow.js';
                        """,
                        source -> source.path("src/chart.ts")
                )
        );
    }

    @Test
    void sourceMigrationPreservesDocumentationStringsCommentsAndUnrelatedPaths() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        // import value from 'echarts/lib/echarts';
                        const docs = "import value from 'echarts/src/echarts'";
                        import extension from 'echarts-extension-amap';
                        type Local = echarts.EChartOptionText;
                        """,
                        source -> source.path("src/docs.ts")
                )
        );
    }

    @Test
    void migratesAntSimpleProLegacyOptionTypeAndMarksHoverBehavior() {
        // Reduced from lgf196/ant-simple-pro at f6613195ab949b067afa57cdf885373d8c6cc58e.
        // https://github.com/lgf196/ant-simple-pro/blob/f6613195ab949b067afa57cdf885373d8c6cc58e/vue/src/views/charts/components/pie-option.ts
        rewriteRun(text(
                """
                const option: echarts.EChartOption = {
                  series: [{ type: 'pie', hoverAnimation: false }]
                };
                """,
                """
                const option: echarts.EChartsOption = {
                  series: [{ type: 'pie', ~~>hoverAnimation: false }]
                };
                """,
                source -> source.path("vue/src/views/charts/components/pie-option.ts")
        ));
    }

    @Test
    void marksCovalentDeprecatedLineOptionsWithoutGuessingNestedEmphasis() {
        // Reduced from Teradata/covalent at 812ab55b4fb701899404d54f5bada274a5c4520d.
        // https://github.com/Teradata/covalent/blob/812ab55b4fb701899404d54f5bada274a5c4520d/libs/angular-echarts/line/src/line.component.ts
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                text(
                        "import { ITdSeries } from '@covalent/echarts/base';\nconst option = { type: 'line', clipOverflow: this.clipOverflow, hoverAnimation: this.hoverAnimation };\n",
                        "import { ITdSeries } from '@covalent/echarts/base';\nconst option = { type: 'line', ~~>clipOverflow: this.clipOverflow, ~~>hoverAnimation: this.hoverAnimation };\n",
                        source -> source.path("libs/angular-echarts/line/src/line.component.ts")
                )
        );
    }

    @Test
    void marksECharts6DataIndexAxisBoundaryAndThemeReviewSites() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                text(
                        """
                        import * as echarts from 'echarts';
                        const option = {
                          tooltip: { valueFormatter(value, dataIndex) { return String(value); } },
                          yAxis: { startValue: 111 },
                          series: [{ type: 'bar', data: [1] }]
                        };
                        const chart = echarts.init(container);
                        """,
                        """
                        import * as echarts from 'echarts';
                        const option = {
                          tooltip: { ~~>valueFormatter(value, dataIndex) { return String(value); } },
                          yAxis: { ~~>startValue: 111 },
                          series: [{ ~~>type: 'bar', data: [1] }]
                        };
                        const chart = ~~>echarts.init(container);
                        """,
                        source -> source.path("src/report.ts")
                )
        );
    }

    @Test
    void marksGraphicArrayTransformsAndPercentageViewCenters() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                text(
                        "import * as echarts from 'echarts';\nconst option = { graphic: { position: [10, 20] }, geo: { center: ['33%', '50%'] } };\n",
                        "import * as echarts from 'echarts';\nconst option = { graphic: { ~~>position: [10, 20] }, geo: { ~~>center: ['33%', '50%'] } };\n",
                        source -> source.path("src/map.ts")
                )
        );
    }

    @Test
    void auditDoesNotMarkUnrelatedOptionLookalikes() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                text(
                        "const config = { hoverAnimation: false, startValue: 1, position: [0, 0] };\n",
                        source -> source.path("src/animation.ts")
                )
        );
    }

    @Test
    void marksCompanionWrappersAndCommunityTypings() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(COMPANION_RECIPE)),
                json(
                        "{\"dependencies\":{\"ngx-echarts\":\"14.0.0\",\"echarts-for-react\":\"3.0.2\"},\"devDependencies\":{\"@types/echarts\":\"4.9.22\"}}",
                        "{\"dependencies\":{/*~~>*/\"ngx-echarts\":\"14.0.0\",/*~~>*/\"echarts-for-react\":\"3.0.2\"},\"devDependencies\":{/*~~>*/\"@types/echarts\":\"4.9.22\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void discoversAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{MIGRATION_RECIPE, DEPENDENCY_RECIPE, SOURCE_RECIPE, AUDIT_RECIPE, COMPANION_RECIPE}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> name + ": " + recipe.validate().failures());
        }
    }

    private static Stream<Arguments> selectedDeclarations() {
        return Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                .flatMap(section -> selectedVersions()
                        .flatMap(version -> Stream.of("", "^", "~")
                                .map(prefix -> Arguments.of(section, prefix + version))));
    }

    private static Stream<String> selectedVersions() {
        return Stream.of(
                "4.8.0", "4.9.0", "5.0.2", "5.2.1", "5.2.2", "5.3.0", "5.3.1", "5.3.3",
                "5.4.0", "5.4.1", "5.4.2", "5.4.3", "5.5.0", "5.5.1", "5.6.0"
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.echarts")
                .scanYamlResources()
                .build();
    }
}
