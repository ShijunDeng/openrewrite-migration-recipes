package com.huawei.clouds.openrewrite.marked;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;

class MarkedSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void removesTrueTransitionFlagFromNamedMarkedUse() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarked17Source()),
                typescript(
                        """
                        import { marked } from 'marked';
                        marked.use({ useNewRenderer: true, renderer });
                        """,
                        """
                        import { marked } from 'marked';
                        marked.use({ renderer });
                        """,
                        source -> source.path("src/renderer.ts")
                )
        );
    }

    @Test
    void removesTrueTransitionFlagFromAliasedUseImport() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarked17Source()),
                typescript(
                        "import { use as configureMarked } from 'marked';\nconfigureMarked({ renderer, useNewRenderer: true });\n",
                        "import { use as configureMarked } from 'marked';\nconfigureMarked({ renderer });\n",
                        source -> source.path("src/alias.ts")
                )
        );
    }

    @Test
    void removesOnlyPropertyAndKeepsEmptyObject() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarked17Source()),
                typescript(
                        "import { use } from 'marked';\nuse({ useNewRenderer: true });\n",
                        "import { use } from 'marked';\nuse({ });\n",
                        source -> source.path("src/empty.ts")
                )
        );
    }

    @Test
    void leavesFalseAndDynamicFlagsForRiskRecipe() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarked17Source()),
                typescript(
                        """
                        import { marked } from 'marked';
                        marked.use({ useNewRenderer: false, renderer });
                        marked.use({ useNewRenderer: enabled, renderer });
                        """,
                        source -> source.path("src/unsafe.ts")
                )
        );
    }

    @Test
    void leavesDetachedAndUnrelatedProperties() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarked17Source()),
                typescript(
                        """
                        import { marked } from 'marked';
                        const options = { useNewRenderer: true, renderer };
                        unrelated.use({ useNewRenderer: true });
                        """,
                        source -> source.path("src/unrelated.ts")
                )
        );
    }

    @Test
    void conservativelyLeavesShadowedMarkedName() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarked17Source()),
                typescript(
                        """
                        import { marked } from 'marked';
                        function configure(marked: LocalMarked) {
                          marked.use({ useNewRenderer: true, renderer });
                        }
                        """,
                        source -> source.path("src/shadowed.ts")
                )
        );
    }

    @Test
    void deterministicMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarked17Source())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import { marked } from 'marked';\nmarked.use({ useNewRenderer: true, renderer });\n",
                        "import { marked } from 'marked';\nmarked.use({ renderer });\n",
                        source -> source.path("src/idempotent.ts")
                )
        );
    }
}
