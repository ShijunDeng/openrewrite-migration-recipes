package com.huawei.clouds.openrewrite.reactredux;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class ReactReduxAutomaticMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void removesBundledExternalTypesAcrossDirectSectionsOnlyWhenTargetIsPresent() {
        rewriteRun(spec -> spec.recipe(new MigrateReactReduxPackageConfiguration()), json(
                """
                {"dependencies":{"react-redux":"^9.3.0","@types/react-redux":"^7.1.9"},"devDependencies":{"@types/react-redux":"7.1.34"},"overrides":{"@types/react-redux":"7.1.34"},"tool":{"dependencies":{"@types/react-redux":"7.1.9"}}}
                """,
                """
                {"dependencies":{"react-redux":"^9.3.0"},"devDependencies":{},"overrides":{"@types/react-redux":"7.1.34"},"tool":{"dependencies":{"@types/react-redux":"7.1.9"}}}
                """, source -> source.path("package.json")));
    }

    @Test
    void keepsExternalTypesWhenReactReduxWasNotStrictlyUpgraded() {
        rewriteRun(spec -> spec.recipe(new MigrateReactReduxPackageConfiguration()),
                json("{\"dependencies\":{\"react-redux\":\">=7.2.2 <9\"},\"devDependencies\":{\"@types/react-redux\":\"^7.1.9\"}}",
                        source -> source.path("package.json")),
                json("{\"devDependencies\":{\"@types/react-redux\":\"^7.1.9\"}}",
                        source -> source.path("packages/types/package.json")));
    }

    @Test
    void normalizesExactV8BrowserAliasesOnlyForTargetPackage() {
        rewriteRun(spec -> spec.recipe(new MigrateReactReduxPackageConfiguration()),
                json("{\"dependencies\":{\"react-redux\":\"9.3.0\"},\"browser\":{\"react-redux\":\"react-redux/next\",\"other\":\"react-redux/es/hooks/useSelector\"}}",
                        "{\"dependencies\":{\"react-redux\":\"9.3.0\"},\"browser\":{\"react-redux\":\"react-redux\",\"other\":\"react-redux/es/hooks/useSelector\"}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"react-redux\":\"8.1.0\"},\"browser\":{\"react-redux\":\"react-redux/next\"}}",
                        source -> source.path("packages/legacy/package.json")));
    }

    @ParameterizedTest(name = "aggregate module {0}")
    @MethodSource("aggregateModules")
    void migratesPublishedAggregateAndReact18Entries(String before) {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactReduxSource()),
                typescript("import { Provider } from '" + before + "';\nexport { useSelector } from '" + before + "';\n",
                        "import { Provider } from 'react-redux';\nexport { useSelector } from 'react-redux';\n",
                        source -> source.path("src/entry.ts")));
    }

    static Stream<Arguments> aggregateModules() {
        return Stream.of(
                "react-redux/es", "react-redux/es/index", "react-redux/es/index.js",
                "react-redux/es/exports", "react-redux/es/exports.js",
                "react-redux/lib", "react-redux/lib/index", "react-redux/lib/index.js",
                "react-redux/lib/exports", "react-redux/src", "react-redux/src/index",
                "react-redux/src/exports.js", "react-redux/next", "react-redux/next.js",
                "react-redux/es/next", "react-redux/lib/next.js", "react-redux/src/next"
        ).map(Arguments::of);
    }

    @Test
    void migratesDirectRequireAndDynamicImportsButNotComputedLoaders() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactReduxSource()), typescript(
                """
                const a = require('react-redux/lib/index.js');
                const b = import("react-redux/es/next.js");
                const suffix = '/lib/index.js';
                const c = require('react-redux' + suffix);
                """,
                """
                const a = require('react-redux');
                const b = import("react-redux");
                const suffix = '/lib/index.js';
                const c = require('react-redux' + suffix);
                """, source -> source.path("src/loaders.ts")));
    }

    @Test
    void preservesLocalBindingWhenSelectingOfficialLegacyConnectAlias() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactReduxSource()), typescript(
                "import { connect as reduxConnect, Provider } from 'react-redux';\nexport const Wrapped = reduxConnect(mapState)(View);\n",
                "import { legacy_connect as reduxConnect, Provider } from 'react-redux';\nexport const Wrapped = reduxConnect(mapState)(View);\n",
                source -> source.path("src/connected.tsx")));
    }

    @Test
    void doesNotRewriteUnaliasedTypeOnlyForeignOrAlreadyLegacyImports() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactReduxSource()),
                typescript("import { connect, legacy_connect } from 'react-redux';\nexport const A=connect(a)(C); export const B=legacy_connect(b)(D);\n",
                        source -> source.path("src/unaliased.tsx")),
                typescript("import type { connect as ConnectType } from 'react-redux';\ntype C = typeof ConnectType;\n",
                        source -> source.path("src/types.ts")),
                typescript("import { connect as reduxConnect } from '@company/react-redux';\nreduxConnect(a)(C);\n",
                        source -> source.path("src/foreign.ts")));
    }

    @Test
    void normalizesExactAliasesInExecutableBuildConfigsOnly() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactReduxSource()),
                typescript("export default {resolve:{alias:{'react-redux':'react-redux/next'}}};\n",
                        "export default {resolve:{alias:{'react-redux':'react-redux'}}};\n",
                        source -> source.path("vite.config.ts")),
                typescript("export default {note:'react-redux/next',resolve:{alias:{other:'react-redux/next'}}};\n",
                        source -> source.path("webpack.config.ts")),
                typescript("export const note = 'react-redux/next';\n", source -> source.path("src/note.ts")),
                typescript("export default {note:'react-redux/es/hooks/useSelector'};\n",
                        source -> source.path("rollup.config.ts")));
    }

    @Test
    void parentFiltersAreLowercaseAndLeafFilesRemainEligible() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactReduxSource()),
                typescript("import {Provider} from 'react-redux/next';\n", source -> source.path("GENERATED-client/a.ts")),
                typescript("import {Provider} from 'react-redux/next';\n", source -> source.path("Installer/cache/a.ts")),
                typescript("import {Provider} from 'react-redux/next';\n", source -> source.path(".Next/server/a.ts")),
                typescript("import {Provider} from 'react-redux/next';\n",
                        "import {Provider} from 'react-redux';\n", source -> source.path("src/install.ts")),
                typescript("import {Provider} from 'react-redux/lib';\n",
                        "import {Provider} from 'react-redux';\n", source -> source.path("src/generated.ts")));
    }

    @Test
    void migratesPinnedHyperAliasedConnectButLeavesPrivateTypeForMarker() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactReduxSource()), typescript(
                """
                import {connect as reduxConnect} from 'react-redux';
                import type {ConnectOptions} from 'react-redux/es/components/connect';
                export function connect(d: ConnectOptions = {}) { return reduxConnect(a, b, null, d)(View); }
                """,
                """
                import {legacy_connect as reduxConnect} from 'react-redux';
                import type {ConnectOptions} from 'react-redux/es/components/connect';
                export function connect(d: ConnectOptions = {}) { return reduxConnect(a, b, null, d)(View); }
                """, source -> source.path("fixtures/vercel-hyper/lib/utils/plugins.ts")));
    }

    @Test
    void modernTypedHooksFromPinnedReactNativeRepoRemainValidNoOps() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactReduxSource()), typescript(
                """
                import { AppDispatch, RootState } from '@src/store'
                import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux'
                export const useAppDispatch = () => useDispatch<AppDispatch>()
                export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector
                """, source -> source.path("fixtures/funnyzak-react-native-v2ex/src/hooks/index.ts")));
    }

    @Test
    void recommendedRecipeCombinesDependencyTypesAndSourceAutoChanges() {
        rewriteRun(spec -> spec.recipe(ReactReduxDependencyTest.environment().activateRecipes(ReactReduxDependencyTest.MIGRATE)),
                json("{\"dependencies\":{\"react\":\"^18.2.0\",\"redux\":\"^5.0.1\",\"react-redux\":\"^8.0.5\"},\"devDependencies\":{\"@types/react\":\"^18.2.25\",\"@types/react-redux\":\"^7.1.34\",\"typescript\":\"^5.4.0\"}}",
                        "{\"dependencies\":{\"react\":\"^18.2.0\",\"redux\":\"^5.0.1\",\"react-redux\":\"^9.3.0\"},\"devDependencies\":{\"@types/react\":\"^18.2.25\",\"typescript\":\"^5.4.0\"}}",
                        source -> source.path("package.json")),
                typescript("import {Provider} from 'react-redux/next';\nexport const App=()=> <Provider store={store}><View/></Provider>;\n",
                        "import {Provider} from 'react-redux';\nexport const App=()=> <Provider store={store}><View/></Provider>;\n",
                        source -> source.path("src/App.tsx")));
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactReduxSource())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import {connect as rrConnect} from 'react-redux/es/index.js';\nrrConnect(a)(C);\n",
                        "import {legacy_connect as rrConnect} from 'react-redux';\nrrConnect(a)(C);\n",
                        source -> source.path("src/idempotent.ts")));
    }

    @Test
    void packageConfigurationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateReactReduxPackageConfiguration())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"react-redux\":\"9.3.0\"},\"devDependencies\":{\"@types/react-redux\":\"7.1.34\"},\"browser\":{\"react-redux\":\"react-redux/next\"}}",
                        "{\"dependencies\":{\"react-redux\":\"9.3.0\"},\"devDependencies\":{},\"browser\":{\"react-redux\":\"react-redux\"}}",
                        source -> source.path("package.json")));
    }
}
