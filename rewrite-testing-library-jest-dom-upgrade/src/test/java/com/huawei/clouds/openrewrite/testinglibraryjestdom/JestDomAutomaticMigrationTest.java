package com.huawei.clouds.openrewrite.testinglibraryjestdom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class JestDomAutomaticMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void normalizesRemovedSideEffectAndMatchersEntries() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                typescript(
                        """
                        import '@testing-library/jest-dom/extend-expect';
                        import '@testing-library/jest-dom/dist/index.js';
                        import * as matchers from '@testing-library/jest-dom/dist/matchers.js';
                        export * from '@testing-library/jest-dom/matchers.js';
                        """,
                        """
                        import '@testing-library/jest-dom';
                        import '@testing-library/jest-dom';
                        import * as matchers from '@testing-library/jest-dom/matchers';
                        export * from '@testing-library/jest-dom/matchers';
                        """,
                        source -> source.path("src/test-setup.ts")
                )
        );
    }

    @Test
    void normalizesDynamicImportAndDirectCommonJsRequire() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                javascript(
                        """
                        import('@testing-library/jest-dom/extend-expect.js');
                        const matchers = require("@testing-library/jest-dom/dist/matchers");
                        require(`@testing-library/jest-dom/dist/extend-expect`);
                        """,
                        """
                        import('@testing-library/jest-dom');
                        const matchers = require("@testing-library/jest-dom/matchers");
                        require(`@testing-library/jest-dom`);
                        """,
                        source -> source.path("src/loaders.js")
                )
        );
    }

    @Test
    void leavesBoundReexportedAndConsumedLegacySideEffectEntriesForReview() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                typescript("""
                        import setup from '@testing-library/jest-dom/extend-expect';
                        export * from '@testing-library/jest-dom/dist/index.js';
                        const lazy = import('@testing-library/jest-dom/extend-expect.js');
                        const required = require('@testing-library/jest-dom/dist/extend-expect');
                        """, source -> source.path("test/ambiguous-legacy-usage.ts"))
        );
    }

    @Test
    void selectsVitestEntryFromSameFileRuntimeOwnership() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                typescript(
                        """
                        import {expect} from 'vitest';
                        import '@testing-library/jest-dom/extend-expect';
                        const lazySetup = import('@testing-library/jest-dom/dist/index.js');
                        """,
                        """
                        import {expect} from 'vitest';
                        import '@testing-library/jest-dom/vitest';
                        const lazySetup = import('@testing-library/jest-dom/dist/index.js');
                        """,
                        source -> source.path("test/vitest.setup.ts")
                )
        );
    }

    @Test
    void selectsJestGlobalsEntryFromSameFileRuntimeOwnership() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                typescript(
                        """
                        import {expect} from '@jest/globals';
                        import '@testing-library/jest-dom';
                        """,
                        """
                        import {expect} from '@jest/globals';
                        import '@testing-library/jest-dom/jest-globals';
                        """,
                        source -> source.path("test/jest.setup.ts")
                )
        );
    }

    @Test
    void ambiguousRunnerOwnershipDoesNotGuessPlatformEntry() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                typescript(
                        """
                        import {expect as vitestExpect} from 'vitest';
                        import {expect as jestExpect} from '@jest/globals';
                        import '@testing-library/jest-dom/extend-expect';
                        """,
                        """
                        import {expect as vitestExpect} from 'vitest';
                        import {expect as jestExpect} from '@jest/globals';
                        import '@testing-library/jest-dom';
                        """,
                        source -> source.path("test/ambiguous.setup.ts")
                ),
                typescript(
                        """
                        import {type ExpectStatic} from 'vitest';
                        import '@testing-library/jest-dom/extend-expect';
                        """,
                        """
                        import {type ExpectStatic} from 'vitest';
                        import '@testing-library/jest-dom';
                        """,
                        source -> source.path("test/type-only-runner.setup.ts")
                )
        );
    }

    @Test
    void migratesExactExecutableConfigEntries() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                javascript(
                        "module.exports = {setupFilesAfterEnv: ['@testing-library/jest-dom/dist/extend-expect']};\n",
                        "module.exports = {setupFilesAfterEnv: ['@testing-library/jest-dom']};\n",
                        source -> source.path("jest.config.js")),
                typescript(
                        "export default {resolve: {alias: {dom: '@testing-library/jest-dom'}}, setupFiles: ['@testing-library/jest-dom']};\n",
                        "export default {resolve: {alias: {dom: '@testing-library/jest-dom'}}, setupFiles: ['@testing-library/jest-dom/vitest']};\n",
                        source -> source.path("vitest.config.ts"))
        );
    }

    @Test
    void migratesRootPackageJsonRunnerConfiguration() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJestDomPackageConfiguration()),
                json(
                        """
                        {"jest":{"setupFilesAfterEnv":["@testing-library/jest-dom/dist/extend-expect.js"]},"vitest":{"setupFiles":["@testing-library/jest-dom","./local.setup.ts"]}}
                        """,
                        """
                        {"jest":{"setupFilesAfterEnv":["@testing-library/jest-dom"]},"vitest":{"setupFiles":["@testing-library/jest-dom/vitest","./local.setup.ts"]}}
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void leavesNestedLookalikeRunnerConfigurationUntouched() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJestDomPackageConfiguration()),
                json(
                        """
                        {"tool":{"jest":{"setupFilesAfterEnv":["@testing-library/jest-dom/extend-expect"]},"vitest":{"setupFiles":["@testing-library/jest-dom"]}}}
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void preservesPublishedEntriesAndUnknownDeepPaths() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                typescript(
                        """
                        import '@testing-library/jest-dom';
                        import '@testing-library/jest-dom/jest-globals';
                        import '@testing-library/jest-dom/vitest';
                        import * as matchers from '@testing-library/jest-dom/matchers';
                        import internal from '@testing-library/jest-dom/dist/to-be-visible';
                        """,
                        source -> source.path("src/public-and-private.ts")
                )
        );
    }

    @Test
    void doesNotRewriteCommentsProseOrSimilarPackages() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                javascript(
                        """
                        // import '@testing-library/jest-dom/extend-expect'
                        const docs = '@testing-library/jest-dom/extend-expect';
                        import '@company/testing-library-jest-dom/extend-expect';
                        """,
                        source -> source.path("src/docs.js")
                )
        );
    }

    @Test
    void acceptsInstallNamedLeafButExcludesArtifactParents() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                javascript("import '@testing-library/jest-dom/extend-expect';\n",
                        "import '@testing-library/jest-dom';\n",
                        source -> source.path("src/install.js")),
                javascript("import '@testing-library/jest-dom/extend-expect';\n",
                        source -> source.path("INSTALL-CACHE/test/setup.js")),
                javascript("import '@testing-library/jest-dom/extend-expect';\n",
                        source -> source.path("installation/test/setup.js")),
                javascript("import '@testing-library/jest-dom/extend-expect';\n",
                        source -> source.path("Node_Modules/tool/setup.js"))
        );
    }

    @Test
    void realAlibabaChatUiFixturePinnedByCommit() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                typescript(
                        """
                        /* eslint-disable import/no-extraneous-dependencies */
                        // https://github.com/testing-library/jest-dom
                        import '@testing-library/jest-dom/extend-expect';
                        """,
                        """
                        /* eslint-disable import/no-extraneous-dependencies */
                        // https://github.com/testing-library/jest-dom
                        import '@testing-library/jest-dom';
                        """,
                        source -> source.path("fixtures/alibaba-chatui/jest.setup.ts")
                )
        );
    }

    @Test
    void realNflJestConfigFixturePinnedByCommit() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries()),
                javascript(
                        """
                        module.exports = {
                          roots: ['<rootDir>/src'],
                          testEnvironment: "jsdom",
                          setupFilesAfterEnv: ['@testing-library/jest-dom/dist/extend-expect'],
                          moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node']
                        };
                        """,
                        """
                        module.exports = {
                          roots: ['<rootDir>/src'],
                          testEnvironment: "jsdom",
                          setupFilesAfterEnv: ['@testing-library/jest-dom'],
                          moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node']
                        };
                        """,
                        source -> source.path("fixtures/incarnate-nfl/jest.config.js")
                )
        );
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateRemovedJestDomEntries())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        """
                        import {expect} from 'vitest';
                        import '@testing-library/jest-dom/extend-expect';
                        import * as matchers from '@testing-library/jest-dom/dist/matchers.js';
                        """,
                        """
                        import {expect} from 'vitest';
                        import '@testing-library/jest-dom/vitest';
                        import * as matchers from '@testing-library/jest-dom/matchers';
                        """,
                        source -> source.path("test/idempotent.setup.ts")
                )
        );
    }

    @Test
    void packageConfigurationMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJestDomPackageConfiguration())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"vitest\":{\"setupFiles\":[\"@testing-library/jest-dom\"]}}",
                        "{\"vitest\":{\"setupFiles\":[\"@testing-library/jest-dom/vitest\"]}}",
                        source -> source.path("package.json"))
        );
    }
}
