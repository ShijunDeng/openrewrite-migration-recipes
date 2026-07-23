package com.huawei.clouds.openrewrite.ws;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

/** Reduced fixtures from immutable official and real-repository revisions documented in README.md. */
class WsRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED = "com.huawei.clouds.openrewrite.ws.MigrateWsTo8_21_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(WsDependencyUpgradeTest.environment().activateRecipes(RECOMMENDED));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesWorkbook816AndOfficialServerLiteral() {
        rewriteRun(
                json("{\"dependencies\":{\"ws\":\"^8.16.0\"}}", "{\"dependencies\":{\"ws\":\"^8.21.0\"}}",
                        source -> source.path("package.json")),
                typescript(
                        "import { WebSocketServer } from 'ws';\nconst wss = new WebSocketServer({ server });\n",
                        source -> source.path("examples/server-stats/index.js").after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("allowSynchronousEvents: false"));
                            assertTrue(printed.contains("Review ws server authentication"));
                        })));
    }

    @Test
    void evaluatesOfficialSslClientAndTlsBoundary() {
        rewriteRun(
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", "{\"dependencies\":{\"ws\":\"8.21.0\"}}",
                        source -> source.path("package.json")),
                typescript(
                        """
                        import { WebSocket } from 'ws';
                        const ws = new WebSocket(`wss://localhost:${server.address().port}`, {
                          rejectUnauthorized: false
                        });
                        """, source -> source.path("examples/ssl.js").after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("allowSynchronousEvents: false"));
                            assertTrue(printed.contains("TLS trust"));
                        })));
    }

    @Test
    void evaluatesGraphqlWsPinnedServerFixture() {
        rewriteRun(
                json("{\"devDependencies\":{\"ws\":\"~8.16.0\"}}", "{\"devDependencies\":{\"ws\":\"~8.21.0\"}}",
                        source -> source.path("package.json")),
                typescript(
                        """
                        import type ws from 'ws';
                        import { WebSocketServer } from 'ws';
                        const server = new WebSocketServer({ port, path });
                        const sockets = new Set<ws>();
                        """, source -> source.path("tests/utils/tservers.ts").after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("allowSynchronousEvents: false"));
                            assertTrue(printed.contains("path routing"));
                            assertFalse(printed.contains("declare const"));
                        })));
    }

    @Test
    void evaluatesWebpackDevServerPinnedDynamicOptionsWithoutUnsafeRewrite() {
        rewriteRun(
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", "{\"dependencies\":{\"ws\":\"8.21.0\"}}",
                        source -> source.path("package.json")),
                typescript(
                        """
                        import { WebSocketServer as WsServer } from "ws";
                        const options = {
                          ...server.options.webSocketServer.options,
                          clientTracking: false,
                        };
                        const implementation = new WsServer(options);
                        """, source -> source.path("lib/servers/WebsocketServer.js").after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertFalse(printed.contains("allowSynchronousEvents: false"));
                            assertTrue(printed.contains("Review ws server authentication"));
                        })));
    }

    @Test
    void evaluatesTargetPerMessageDeflateFixtureAndNewPartLimits() {
        rewriteRun(
                json("{\"dependencies\":{\"ws\":\"8.20.0\"}}", "{\"dependencies\":{\"ws\":\"8.21.0\"}}",
                        source -> source.path("package.json")),
                typescript(
                        """
                        import { PerMessageDeflate, Receiver } from 'ws';
                        const extension = new PerMessageDeflate({ isServer: false, maxPayload: 25 });
                        const receiver = new Receiver({ maxBufferedChunks: 2, maxFragments: 2 });
                        """, source -> source.path("test/receiver.test.js").after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("PerMessageDeflate"));
                            assertTrue(printed.contains("WS_ERR_TOO_MANY_BUFFERED_PARTS"));
                            assertFalse(printed.contains("allowSynchronousEvents: false"));
                        })));
    }

    @Test
    void publicRecipesAreDiscoverableValidAndRecommendedRecipeIsIdempotent() {
        var environment = WsDependencyUpgradeTest.environment();
        var strict = environment.activateRecipes(WsDependencyUpgradeTest.STRICT);
        var recommended = environment.activateRecipes(RECOMMENDED);
        assertTrue(strict.validate().isValid(), () -> strict.validate().failures().toString());
        assertTrue(recommended.validate().isValid(), () -> recommended.validate().failures().toString());
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> RECOMMENDED.equals(recipe.getName())));

        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", "{\"dependencies\":{\"ws\":\"8.21.0\"}}",
                        source -> source.path("package.json")),
                typescript("import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 8080 });",
                        source -> source.path("src/server.ts").after(actual -> actual).afterRecipe(cu ->
                                assertTrue(cu.printAll().contains("allowSynchronousEvents: false")))));
    }
}
