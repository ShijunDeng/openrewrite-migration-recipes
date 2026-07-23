package com.huawei.clouds.openrewrite.ws;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class WsEventSchedulingMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void preservesNamedServerLiteralOptionsForExact816() {
        rewriteRun(spec -> spec.recipe(new PreserveWs816EventScheduling()),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path("package.json")),
                typescript(
                        "import { WebSocketServer } from 'ws';\nconst server = new WebSocketServer({ port: 8080 });\n",
                        "import { WebSocketServer } from 'ws';\nconst server = new WebSocketServer({ port: 8080, allowSynchronousEvents: false });\n",
                        source -> source.path("src/server.ts")));
    }

    @Test
    void preservesDefaultClientSecondArgumentOptions() {
        rewriteRun(spec -> spec.recipe(new PreserveWs816EventScheduling()),
                json("{\"dependencies\":{\"ws\":\"^8.16.0\"}}", source -> source.path("package.json")),
                typescript(
                        "import WebSocket from 'ws';\nconst client = new WebSocket(url, { followRedirects: true });\n",
                        "import WebSocket from 'ws';\nconst client = new WebSocket(url, { followRedirects: true, allowSynchronousEvents: false });\n",
                        source -> source.path("src/client.ts")));
    }

    @Test
    void preservesClientThirdArgumentOptionsAndMultilineStyle() {
        rewriteRun(spec -> spec.recipe(new PreserveWs816EventScheduling()),
                json("{\"devDependencies\":{\"ws\":\"~8.16.0\"}}", source -> source.path("package.json")),
                typescript(
                        """
                        import { WebSocket as Client } from 'ws';
                        const client = new Client(url, ['graphql-ws'], {
                          maxPayload: 1024,
                        });
                        """,
                        """
                        import { WebSocket as Client } from 'ws';
                        const client = new Client(url, ['graphql-ws'], {
                          maxPayload: 1024,
                          allowSynchronousEvents: false
                        });
                        """, source -> source.path("src/graphql.ts")));
    }

    @Test
    void preservesNamespaceAndCommonJsServerOptions() {
        rewriteRun(spec -> spec.recipe(new PreserveWs816EventScheduling()),
                json("{\"optionalDependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path("package.json")),
                typescript(
                        "import * as ws from 'ws';\nnew ws.WebSocketServer({ noServer: true });\n",
                        "import * as ws from 'ws';\nnew ws.WebSocketServer({ noServer: true, allowSynchronousEvents: false });\n",
                        source -> source.path("src/namespace.ts")),
                typescript(
                        "const WebSocket = require('ws');\nnew WebSocket.Server({ port: 0 });\n",
                        "const WebSocket = require('ws');\nnew WebSocket.Server({ port: 0, allowSynchronousEvents: false });\n",
                        source -> source.path("scripts/server.cjs")));
    }

    @Test
    void preservesEmptyLiteralOptions() {
        rewriteRun(spec -> spec.recipe(new PreserveWs816EventScheduling()),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path("package.json")),
                typescript("import { WebSocketServer } from 'ws';\nnew WebSocketServer({});\n",
                        "import { WebSocketServer } from 'ws';\nnew WebSocketServer({ allowSynchronousEvents: false });\n",
                        source -> source.path("src/empty.ts")));
    }

    @Test
    void doesNotOverrideExplicitOrderingOrDynamicOptions() {
        rewriteRun(spec -> spec.recipe(new PreserveWs816EventScheduling()),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path("package.json")),
                typescript(
                        """
                        import WebSocket, { WebSocketServer } from 'ws';
                        new WebSocket(url, { allowSynchronousEvents: true });
                        new WebSocketServer({ allowSynchronousEvents: false, port: 0 });
                        new WebSocket(url, options);
                        new WebSocketServer(options);
                        """, source -> source.path("src/noop.ts")));
    }

    @Test
    void doesNotChangeOtherWorkbookSourceDefaults() {
        rewriteRun(spec -> spec.recipe(new PreserveWs816EventScheduling()),
                json("{\"dependencies\":{\"ws\":\"8.5.0\"}}", source -> source.path("old/package.json")),
                typescript("import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 1 });",
                        source -> source.path("old/server.ts")),
                json("{\"dependencies\":{\"ws\":\"8.18.3\"}}", source -> source.path("modern/package.json")),
                typescript("import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 2 });",
                        source -> source.path("modern/server.ts")),
                json("{\"dependencies\":{\"ws\":\"8.20.0\"}}", source -> source.path("latest/package.json")),
                typescript("import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 3 });",
                        source -> source.path("latest/server.ts")));
    }

    @Test
    void selectsNearestWorkspaceManifest() {
        rewriteRun(spec -> spec.recipe(new PreserveWs816EventScheduling()),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"ws\":\"8.18.3\"}}", source -> source.path("packages/new/package.json")),
                typescript("import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 1 });",
                        "import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 1, allowSynchronousEvents: false });",
                        source -> source.path("src/root.ts")),
                typescript("import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 2 });",
                        source -> source.path("packages/new/src/server.ts")));
    }

    @Test
    void skipsConflictingDeclarationsAndExcludedTrees() {
        rewriteRun(spec -> spec.recipe(new PreserveWs816EventScheduling()),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"},\"devDependencies\":{\"ws\":\"8.18.3\"}}",
                        source -> source.path("conflict/package.json")),
                typescript("import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 1 });",
                        source -> source.path("conflict/src/server.ts")),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path("safe/package.json")),
                typescript("import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 2 });",
                        source -> source.path("safe/vendor/server.ts")));
    }

    @Test
    void migrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new PreserveWs816EventScheduling()).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"ws\":\"8.16.0\"}}", source -> source.path("package.json")),
                typescript("import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 8080 });",
                        "import { WebSocketServer } from 'ws'; new WebSocketServer({ port: 8080, allowSynchronousEvents: false });",
                        source -> source.path("src/server.ts")));
    }
}
