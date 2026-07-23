package com.huawei.clouds.openrewrite.fastglob;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;

class FastGlobSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void normalizesDefaultAsyncCallIgnoreString() {
        rewriteRun(spec -> spec.recipe(new NormalizeFastGlobIgnoreOption()),
                typescript(
                        "import fg from 'fast-glob';\nconst files = await fg('**/*.ts', { ignore: '**/*.spec.ts' });\n",
                        "import fg from 'fast-glob';\nconst files = await fg('**/*.ts', { ignore: ['**/*.spec.ts'] });\n",
                        source -> source.path("src/find.ts")));
    }

    @Test
    void normalizesDefaultSyncAndStreamCalls() {
        rewriteRun(spec -> spec.recipe(new NormalizeFastGlobIgnoreOption()),
                typescript(
                        """
                        import fastGlob from "fast-glob";
                        fastGlob.sync('**/*.js', { ignore: 'vendor/**', dot: true });
                        fastGlob.stream('**/*.md', { ignore: 'generated/**' });
                        """,
                        """
                        import fastGlob from "fast-glob";
                        fastGlob.sync('**/*.js', { ignore: ['vendor/**'], dot: true });
                        fastGlob.stream('**/*.md', { ignore: ['generated/**'] });
                        """, source -> source.path("src/modes.ts")));
    }

    @Test
    void normalizesNamedAliasedAndNamespaceCalls() {
        rewriteRun(spec -> spec.recipe(new NormalizeFastGlobIgnoreOption()),
                typescript(
                        """
                        import { sync as findSync } from 'fast-glob';
                        import * as fg from 'fast-glob';
                        findSync('*', { ignore: '.git' });
                        fg.stream('*', { ignore: 'node_modules' });
                        """,
                        """
                        import { sync as findSync } from 'fast-glob';
                        import * as fg from 'fast-glob';
                        findSync('*', { ignore: ['.git'] });
                        fg.stream('*', { ignore: ['node_modules'] });
                        """, source -> source.path("src/imports.ts")));
    }

    @Test
    void normalizesCommonJsBinding() {
        rewriteRun(spec -> spec.recipe(new NormalizeFastGlobIgnoreOption()),
                typescript(
                        "const fg = require('fast-glob');\nfg.sync('*', { ignore: 'tmp/**' });\n",
                        "const fg = require('fast-glob');\nfg.sync('*', { ignore: ['tmp/**'] });\n",
                        source -> source.path("scripts/build.cjs")));
    }

    @Test
    void leavesArraysDynamicDetachedNestedAndUnrelatedOptions() {
        rewriteRun(spec -> spec.recipe(new NormalizeFastGlobIgnoreOption()),
                typescript(
                        """
                        import fg from 'fast-glob';
                        fg('*', { ignore: ['dist/**'] });
                        fg('*', { ignore: ignored });
                        const options = { ignore: 'dist/**' };
                        fg('*', options);
                        fg({ ignore: 'dist/**' });
                        fg.generateTasks({ ignore: 'dist/**' });
                        fg('*', { nested: { ignore: 'dist/**' } });
                        other('*', { ignore: 'dist/**' });
                        """, source -> source.path("src/noop.ts")));
    }

    @Test
    void leavesDeepImportAndExcludedSourceTrees() {
        rewriteRun(spec -> spec.recipe(new NormalizeFastGlobIgnoreOption()),
                typescript("import type fg from 'fast-glob'; declare const runtime: typeof fg; runtime('*', { ignore: 'tmp/**' });",
                        source -> source.path("src/types.ts")),
                typescript("import fg from 'fast-glob/out'; fg('*', { ignore: 'tmp/**' });",
                        source -> source.path("src/deep.ts")),
                typescript("import fg from 'fast-glob'; fg('*', { ignore: 'tmp/**' });",
                        source -> source.path("generated/client.ts")),
                typescript("import fg from 'fast-glob'; fg('*', { ignore: 'tmp/**' });",
                        source -> source.path("vendor/tool.ts")),
                typescript("import fg from 'fast-glob'; fg('*', { ignore: 'tmp/**' });",
                        source -> source.path("dist/bundle.ts")));
    }

    @Test
    void deterministicMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new NormalizeFastGlobIgnoreOption())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import fg from 'fast-glob';\nfg('*', { ignore: 'tmp/**' });\n",
                        "import fg from 'fast-glob';\nfg('*', { ignore: ['tmp/**'] });\n",
                        source -> source.path("src/idempotent.ts")));
    }
}
