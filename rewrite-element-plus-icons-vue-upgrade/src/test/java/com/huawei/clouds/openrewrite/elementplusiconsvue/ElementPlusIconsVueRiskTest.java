package com.huawei.clouds.openrewrite.elementplusiconsvue;

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
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class ElementPlusIconsVueRiskTest implements RewriteTest {
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.elementplusiconsvue.AuditElementPlusIconsVue2_3_2";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "source marker {0}")
    @MethodSource("sourceRisks")
    void marksExactSourceBoundaries(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript(before, after, source -> source.path("src/icons.ts")));
    }

    static Stream<Arguments> sourceRisks() {
        return Stream.of(
                Arguments.of("import Search from '@element-plus/icons-vue/dist/es/search.mjs';\n",
                        "/*~~(The 0.2.x dist/es and dist/lib per-icon layout is absent in 2.3.2; replace this default deep import with the matching named root export while preserving its local alias and lazy-loading intent)~~>*/import Search from '@element-plus/icons-vue/dist/es/search.mjs';\n"),
                Arguments.of("import Icons from '@element-plus/icons-vue';\n",
                        "/*~~(@element-plus/icons-vue aggregate entries have no default icon export; choose named icon exports or the explicit /global plugin)~~>*/import Icons from '@element-plus/icons-vue';\n"),
                Arguments.of("import Icons from '@element-plus/icons-vue/dist/es/index.mjs';\n",
                        "/*~~(@element-plus/icons-vue aggregate entries have no default icon export; choose named icon exports or the explicit /global plugin)~~>*/import Icons from '@element-plus/icons-vue/dist/es/index.mjs';\n"),
                Arguments.of("import * as Icons from '@element-plus/icons-vue';\n",
                        "/*~~(Namespace-importing every icon creates a bundle/tree-shaking and SSR boundary; keep it only for deliberate manual global registration, otherwise import used icons by name)~~>*/import * as Icons from '@element-plus/icons-vue';\n"),
                Arguments.of("import Icons from '@element-plus/icons-vue/global';\n",
                        "/*~~(The global plugin registers every icon; verify prefix collisions, component names, bundle budget, production tree shaking, SSR isolation, and hydration before retaining it)~~>*/import Icons from '@element-plus/icons-vue/global';\n"),
                Arguments.of("const icons = require('@element-plus/icons-vue');\n",
                        "const icons = /*~~(Verify CommonJS interop against the 2.3.2 conditional exports; prefer a static ESM named import unless the runtime boundary is intentionally CommonJS)~~>*/require('@element-plus/icons-vue');\n"),
                Arguments.of("const icons = import('@element-plus/icons-vue/dist/es/search.mjs');\n",
                        "const icons = /*~~(Dynamic icon loading must target a published 2.3.2 export and preserve chunking, error handling, SSR execution, preload, and hydration behavior)~~>*/import('@element-plus/icons-vue/dist/es/search.mjs');\n"),
                Arguments.of("import { Search } from '@element-plus/icons-vue';\napp.component('Search', Search);\n",
                        "import { Search } from '@element-plus/icons-vue';\n/*~~(Manual icon registration is application-owned; verify the registered string, duplicate/collision policy, SSR app isolation, hydration, and template references)~~>*/app.component('Search', Search);\n"),
                Arguments.of("import Icons from '@element-plus/icons-vue/global';\napp.use(Icons, { prefix: '' });\n",
                        "/*~~(The global plugin registers every icon; verify prefix collisions, component names, bundle budget, production tree shaking, SSR isolation, and hydration before retaining it)~~>*/import Icons from '@element-plus/icons-vue/global';\n/*~~(Verify /global's default ElIcon prefix or explicit prefix option, registration order, bundle size, SSR app isolation, and hydration)~~>*/app.use(Icons, { prefix: '' });\n"),
                Arguments.of("const icon = resolveComponent('ElIconSearch');\n",
                        "const icon = /*~~(String-based icon resolution depends on registration prefix/casing and SSR hydration; replace with a direct component reference when possible)~~>*/resolveComponent('ElIconSearch');\n")
        );
    }

    @Test
    void leavesOrdinaryNamedImportsAndUnrelatedRegistrationUnmarked() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript("import { Search, Avatar as UserAvatar } from '@element-plus/icons-vue';\napp.component('Widget', Widget);\n",
                        source -> source.path("src/icons.ts")),
                typescript("import { Search } from '@element-plus/icons-vue';\nfunction install(Search: any) { app.component('Search', Search); }\n",
                        source -> source.path("src/shadowed.ts")));
    }

    @Test
    void shadowedGlobalAndNamespaceBindingsDoNotCreateExtraCallMarkers() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript("import GlobalIcons from '@element-plus/icons-vue/global';\nfunction install(GlobalIcons: any) { app.use(GlobalIcons); }\n",
                        source -> source.path("src/global-shadow.ts").after(actual -> actual).afterRecipe(after ->
                                org.junit.jupiter.api.Assertions.assertEquals(1,
                                        after.printAll().split("global plugin", -1).length - 1, after.printAll()))),
                typescript("import * as Icons from '@element-plus/icons-vue';\nfunction list(Icons: any) { return Object.entries(Icons); }\n",
                        source -> source.path("src/namespace-shadow.ts").after(actual -> actual).afterRecipe(after ->
                                org.junit.jupiter.api.Assertions.assertFalse(
                                        after.printAll().contains("Manual all-icon enumeration"), after.printAll()))));
    }

    @ParameterizedTest(name = "manifest marker {0}")
    @MethodSource("manifestRisks")
    void marksExactManifestRisks(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json(before, after, source -> source.path("package.json")));
    }

    static Stream<Arguments> manifestRisks() {
        return Stream.of(
                Arguments.of("{\"dependencies\":{\"@element-plus/icons-vue\":\">=0.2.4 <3\"}}",
                        "{\"dependencies\":{\"@element-plus/icons-vue\":/*~~(Complex @element-plus/icons-vue range, prerelease, or decorated scalar is intentionally not rewritten; select one target policy explicitly)~~>*/\">=0.2.4 <3\"}}"),
                Arguments.of("{\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\",\"vue\":\"^3.1.5\"}}",
                        "{\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\",\"vue\":/*~~(@element-plus/icons-vue 2.3.2 peers on Vue ^3.2.0; prove every supported install resolves Vue 3.2+ below Vue 4 and verify compiler/runtime alignment)~~>*/\"^3.1.5\"}}"),
                Arguments.of("{\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\",\"element-plus\":\"^2.2.0\"}}",
                        "{\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\",\"element-plus\":/*~~(Coordinate Element Plus icon props, legacy el-icon-* classes, component registration, SSR/hydration, and visual snapshots with the icons package upgrade)~~>*/\"^2.2.0\"}}"),
                Arguments.of("{\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\"},\"devDependencies\":{\"unplugin-icons\":\"^0.18.0\"}}",
                        "{\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\"},\"devDependencies\":{\"unplugin-icons\":/*~~(Review this icon/component auto-import plugin's resolver, generated declarations, include/exclude rules, SSR output, and tree-shaking against @element-plus/icons-vue 2.3.2)~~>*/\"^0.18.0\"}}"),
                Arguments.of("{\"sideEffects\":false,\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\"}}",
                        "{\"sideEffects\":/*~~(A sideEffects:false application can erase registration-only modules; verify explicit imports/global plugin bootstrap survive production tree shaking and SSR builds)~~>*/false,\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\"}}")
        );
    }

    @Test
    void supportedVueAndPlainNamedImportsAreClean() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\",\"vue\":\"^3.5.0\"}}",
                        source -> source.path("package.json")),
                javascript("import { Search } from '@element-plus/icons-vue';\n",
                        source -> source.path("src/search.js")));
    }

    @Test
    void marksCentralOwnerInsteadOfChangingIt() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"overrides\":{\"@element-plus/icons-vue\":\"0.2.4\"}}",
                        "{\"overrides\":{\"@element-plus/icons-vue\":/*~~(This @element-plus/icons-vue declaration is owned outside a root direct dependency section; update the workspace catalog, override, resolution, template, or other central owner deliberately)~~>*/\"0.2.4\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void standaloneAuditMarksASelectedOldVersionButRecommendedUpgradeClearsThatRisk() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)), json(
                "{\"dependencies\":{\"@element-plus/icons-vue\":\"^2.1.0\"}}",
                source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                        org.junit.jupiter.api.Assertions.assertTrue(
                                after.printAll().contains("run the strict upgrade to 2.3.2"), after.printAll()))));
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(ElementPlusIconsVueDependencyTest.MIGRATE)), json(
                "{\"dependencies\":{\"@element-plus/icons-vue\":\"^2.1.0\",\"vue\":\"^3.2.0\"}}",
                "{\"dependencies\":{\"@element-plus/icons-vue\":\"^2.3.2\",\"vue\":\"^3.2.0\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {">=3.2.0", "3.2.0 || 3.5.0", "^4.0.0", "~4.0.0", "latest"})
    void marksVueDeclarationsThatDoNotProveTheExactPeerDomain(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)), json(
                "{\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\",\"vue\":\"" + declaration + "\"}}",
                source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                        org.junit.jupiter.api.Assertions.assertTrue(after.printAll().contains("below Vue 4"), after.printAll()))));
    }

    @Test
    void marksQualifiedCentralSelectorsOnTheirValuesAndIgnoresTextualMentions() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"resolutions\":{\"app>@element-plus/icons-vue\":\"2.1.0\"},\"pnpm\":{\"overrides\":{\"@element-plus/icons-vue@2.0.10\":\"2.0.10\"}}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            org.junit.jupiter.api.Assertions.assertEquals(2, printed.split("owned outside a root direct dependency section", -1).length - 1, printed);
                            org.junit.jupiter.api.Assertions.assertTrue(printed.contains("~~>*/\"2.1.0\""), printed);
                            org.junit.jupiter.api.Assertions.assertTrue(printed.contains("~~>*/\"2.0.10\""), printed);
                        })),
                json("{\"description\":\"migrate @element-plus/icons-vue later\",\"dependencies\":{\"vue\":\"^3.1.0\"}}",
                        source -> source.path("apps/unmanaged/package.json")),
                json("{\"dependencies\":{\"@element-plus/icons-vue-extra\":\"2.1.0\",\"vue\":\"^3.1.0\"}}",
                        source -> source.path("apps/similar/package.json")));
    }

    @ParameterizedTest(name = "template marker {0}")
    @MethodSource("templateRisks")
    void marksExactTemplateAndStyleSnippets(String path, String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(before, after, source -> source.path(path)));
    }

    static Stream<Arguments> templateRisks() {
        return Stream.of(
                Arguments.of("src/App.vue", "<i class=\"el-icon-search toolbar\"></i>",
                        "~~(Element Plus 2 removed font-class icons; import the matching Vue icon component, wrap it in el-icon, and verify size/color/alignment/accessibility)~~><i class=\"el-icon-search toolbar\"></i>"),
                Arguments.of("src/App.vue", "<el-button icon=\"el-icon-search\">Go</el-button>",
                        "~~(This legacy string icon prop must become a bound imported component; select the correct icon and verify the owning Element Plus component API)~~><el-button icon=\"el-icon-search\">Go</el-button>"),
                Arguments.of("src/App.vue", "<el-icon><component :is=\"currentIcon\" /></el-icon>",
                        "~~(Dynamic icon component selection needs explicit component references or a deliberate registry; verify casing, fallback, SSR serialization, and hydration)~~><el-icon><component :is=\"currentIcon\" /></el-icon>"),
                Arguments.of("src/icons.scss", ".el-icon-search:hover { color: red; }",
                        "~~(This selector targets the removed Element Plus font-icon class contract; move styling to el-icon/the imported SVG component and verify visual states)~~>.el-icon-search:hover { color: red; }")
        );
    }

    @Test
    void ignoresCommentedTemplateRisksAndUnrelatedClasses() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text("<!-- <i class=\"el-icon-search\"></i> -->\n<span class=\"my-el-icon-search-result\"></span>",
                        source -> source.path("src/App.vue")),
                text("/* .el-icon-search {} */\n.my-el-icon-search-result {}", source -> source.path("src/app.css")),
                text("<component :is=\"page\" />", source -> source.path("src/PageHost.vue")));
    }

    @Test
    void auditIgnoresGeneratedInstallAndCacheParents() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                typescript("import Search from '@element-plus/icons-vue/dist/es/search.mjs';\n",
                        source -> source.path("generated-client/icons.ts")),
                text("<i class=\"el-icon-search\"></i>", source -> source.path("installation-output/App.vue")),
                text(".el-icon-search {}", source -> source.path(".cache/icons.css")),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\">=0.2.4\"}}",
                        source -> source.path(".pnpm/pkg/package.json")));
    }

    @Test
    void auditMarkersConvergeInTwoCycles() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import Icons from '@element-plus/icons-vue';\n",
                        source -> source.path("src/icons.ts").after(actual -> actual)),
                json("{\"dependencies\":{\"@element-plus/icons-vue\":\"2.3.2\",\"vue\":\"^3.1.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                text("<i class=\"el-icon-search\"></i>",
                        source -> source.path("src/App.vue").after(actual -> actual)));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.elementplusiconsvue")
                .scanYamlResources()
                .build();
    }
}
