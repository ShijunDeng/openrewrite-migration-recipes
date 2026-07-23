package com.huawei.clouds.openrewrite.elementplusiconsvue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class ElementPlusIconsVueSourceTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "entry {0}")
    @MethodSource("staticEntries")
    void normalizesDeterministicStaticAggregateAndGlobalEntries(String before, String after) {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicElementPlusIconsVueEntries()),
                javascript(before, after, source -> source.path("src/icons.js")));
    }

    static Stream<Arguments> staticEntries() {
        return Stream.of(
                Arguments.of("import Search from '@element-plus/icons-vue/dist/es/search.mjs';\n",
                        "import { Search } from '@element-plus/icons-vue';\n"),
                Arguments.of("import Magnifier from \"@element-plus/icons-vue/dist/lib/search.js\";\n",
                        "import { Search as Magnifier } from \"@element-plus/icons-vue\";\n"),
                Arguments.of("import DCaret from '@element-plus/icons-vue/dist/es/d-caret.mjs';\n",
                        "import { DCaret } from '@element-plus/icons-vue';\n"),
                Arguments.of("import FirstAid from '@element-plus/icons-vue/dist/lib/first-aid-kit.js';\n",
                        "import { FirstAidKit as FirstAid } from '@element-plus/icons-vue';\n"),
                Arguments.of("import { Search } from '@element-plus/icons-vue/dist/es/index.mjs';\n",
                        "import { Search } from '@element-plus/icons-vue';\n"),
                Arguments.of("import * as Icons from \"@element-plus/icons-vue/dist/lib/index.js\";\n",
                        "import * as Icons from \"@element-plus/icons-vue\";\n"),
                Arguments.of("import Icons from '@element-plus/icons-vue/dist/global.cjs';\n",
                        "import Icons from '@element-plus/icons-vue/global';\n"),
                Arguments.of("import '@element-plus/icons-vue/dist/global.js';\n",
                        "import '@element-plus/icons-vue/global';\n")
        );
    }

    @ParameterizedTest(name = "loader {0}")
    @MethodSource("loaders")
    void normalizesDeterministicStaticRuntimeLoaders(String before, String after) {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicElementPlusIconsVueEntries()),
                javascript(before, after, source -> source.path("src/load.js")));
    }

    static Stream<Arguments> loaders() {
        return Stream.of(
                Arguments.of("const icons = import('@element-plus/icons-vue/dist/es/index.mjs');\n",
                        "const icons = import('@element-plus/icons-vue');\n"),
                Arguments.of("const plugin = import(\"@element-plus/icons-vue/dist/global.js\");\n",
                        "const plugin = import(\"@element-plus/icons-vue/global\");\n"),
                Arguments.of("const icons = require('@element-plus/icons-vue/dist/index.cjs');\n",
                        "const icons = require('@element-plus/icons-vue');\n"),
                Arguments.of("const plugin = require('@element-plus/icons-vue/dist/global.cjs');\n",
                        "const plugin = require('@element-plus/icons-vue/global');\n")
        );
    }

    @ParameterizedTest(name = "automatic NOOP {0}")
    @MethodSource("automaticNoOps")
    void protectsUnprovenAndModernSource(String source) {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicElementPlusIconsVueEntries()),
                typescript(source, input -> input.path("src/icons.ts")));
    }

    static Stream<String> automaticNoOps() {
        return Stream.of(
                "import Search from '@element-plus/icons-vue/dist/es/not-a-real-icon.mjs';\n",
                "import Search from '@element-plus/icons-vue';\n",
                "import { Search, Avatar as UserAvatar } from '@element-plus/icons-vue';\n",
                "import Icons from '@element-plus/icons-vue/global';\n",
                "const path = '@element-plus/icons-vue/dist/es/index.mjs';\n",
                "const icons = import(iconPath);\n",
                "const icons = require(iconPath);\n",
                "const other = require('@element-plus/icons-svg');\n",
                "export { Search } from '@element-plus/icons-vue/dist/index.js';\n"
        );
    }

    @Test
    void preservesDefaultAggregateImportBecauseRootHasNoDefault() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicElementPlusIconsVueEntries()),
                javascript("import Icons from '@element-plus/icons-vue/dist/es/index.mjs';\n",
                        source -> source.path("src/invalid-legacy.js")));
    }

    @Test
    void skipsGeneratedInstallAndCacheParentsButNotLeafNames() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicElementPlusIconsVueEntries()),
                javascript("import { Search } from '@element-plus/icons-vue/dist/es/index.mjs';\n",
                        source -> source.path("generated-client/icons.js")),
                javascript("import { Search } from '@element-plus/icons-vue/dist/es/index.mjs';\n",
                        source -> source.path("INSTALL-assets/icons.js")),
                javascript("import { Search } from '@element-plus/icons-vue/dist/es/index.mjs';\n",
                        source -> source.path(".vite/icons.js")),
                javascript("import { Search } from '@element-plus/icons-vue/dist/es/index.mjs';\n",
                        "import { Search } from '@element-plus/icons-vue';\n",
                        source -> source.path("src/generated.ts")));
    }

    @Test
    void pinnedRealNamedImportsRemainStable() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicElementPlusIconsVueEntries()),
                typescript("import { ChatRound, Avatar } from \"@element-plus/icons-vue\";\n",
                        source -> source.path("fixtures/song-will/OperationArea.ts")),
                typescript("import { Delete, Search, ArrowDown, ArrowUp } from '@element-plus/icons-vue';\n",
                        source -> source.path("fixtures/newPermissionBaseLocalMock/SearchForm.ts")));
    }

    @Test
    void deterministicMigrationConverges() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicElementPlusIconsVueEntries())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                javascript("import Icons from '@element-plus/icons-vue/dist/global.cjs';\n",
                        "import Icons from '@element-plus/icons-vue/global';\n",
                        source -> source.path("src/install.js")));
    }

    @Test
    void recommendedRecipeAppliesVersionAndProvenDeepImportMigrationTogether() {
        rewriteRun(spec -> spec.recipe(ElementPlusIconsVueDependencyTest.environment()
                        .activateRecipes(ElementPlusIconsVueDependencyTest.MIGRATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                org.openrewrite.json.Assertions.json(
                        "{\"dependencies\":{\"@element-plus/icons-vue\":\"^0.2.4\",\"vue\":\"^3.2.0\"}}",
                        "{\"dependencies\":{\"@element-plus/icons-vue\":\"^2.3.2\",\"vue\":\"^3.2.0\"}}",
                        source -> source.path("package.json")),
                typescript("import Magnifier from '@element-plus/icons-vue/dist/es/search.mjs';\n",
                        "import { Search as Magnifier } from '@element-plus/icons-vue';\n",
                        source -> source.path("src/SearchIcon.ts")));
    }
}
