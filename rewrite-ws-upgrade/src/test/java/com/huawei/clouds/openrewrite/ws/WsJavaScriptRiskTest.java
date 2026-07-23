package com.huawei.clouds.openrewrite.ws;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;

class WsJavaScriptRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest
    @CsvSource({
            "allowSynchronousEvents: false,same-tick message/ping/pong",
            "perMessageDeflate: true,catastrophic memory fragmentation",
            "maxPayload: 1048576,retained-part limits",
            "maxBufferedChunks: 1024,WS_ERR_TOO_MANY_BUFFERED_PARTS",
            "maxFragments: 512,WS_ERR_TOO_MANY_BUFFERED_PARTS",
            "autoPong: false,application owns every pong",
            "closeTimeout: 5000,graceful-close timeout",
            "followRedirects: true,confidential-header boundaries",
            "maxRedirects: 3,confidential-header boundaries",
            "rejectUnauthorized: false,TLS trust",
            "skipUTF8Validation: true,protocol/security validation",
            "generateMask: mask,random mask strength",
            "protocolVersion: 13,supported protocol 8/13 response"
    })
    void marksExactClientOptions(String option, String message) {
        assertMarked("import WebSocket from 'ws';\nnew WebSocket(url, { " + option + " });", message);
    }

    @ParameterizedTest
    @CsvSource({
            "verifyClient: verify,authentication and subprotocol",
            "handleProtocols: choose,authentication and subprotocol",
            "noServer: true,handleUpgrade transfers",
            "server: httpServer,handleUpgrade transfers",
            "path: '/socket',path routing",
            "clientTracking: false,connection ownership",
            "perMessageDeflate: { threshold: 1024 },catastrophic memory fragmentation",
            "maxFragments: 1024,WS_ERR_TOO_MANY_BUFFERED_PARTS",
            "closeTimeout: 1000,graceful-close timeout"
    })
    void marksExactServerOptions(String option, String message) {
        assertMarked("import { WebSocketServer } from 'ws';\nnew WebSocketServer({ " + option + " });", message);
    }

    @Test
    void marksDefaultNamedAliasedNamespaceAndCommonJsConstructors() {
        assertMarked(
                """
                import WebSocket, { WebSocket as Client, WebSocketServer as Server } from 'ws';
                import * as ws from 'ws';
                const Ws = require('ws');
                new WebSocket(url);
                new Client(url);
                new Server({ port: 0 });
                new ws.WebSocket(url);
                new ws.WebSocketServer({ noServer: true });
                new Ws.Server({ port: 0 });
                """, "Review ws client handshake");
    }

    @Test
    void marksDeepImportsButAcceptsDocumentedRootAndPackageJson() {
        rewriteRun(spec -> spec.recipe(new FindWsJavaScriptRisks()),
                typescript("import Receiver from 'ws/lib/receiver.js';", source -> source.path("src/deep.ts").after(actual -> actual)
                        .afterRecipe(cu -> assertTrue(cu.printAll().contains("outside the target exports contract")))),
                typescript("const Sender = require('ws/lib/sender');", source -> source.path("src/deep.cjs").after(actual -> actual)
                        .afterRecipe(cu -> assertTrue(cu.printAll().contains("outside the target exports map")))),
                typescript("import pkg from 'ws/package.json'; export default pkg.version;", source -> source.path("src/pkg.ts")
                        .afterRecipe(cu -> assertFalse(cu.printAll().contains("exports contract")))));
    }

    @Test
    void marksPerMessageDeflateAndLowLevelConstructors() {
        assertMarked(
                """
                import { PerMessageDeflate, Receiver, Sender } from 'ws';
                new PerMessageDeflate({}, true, 25);
                new Receiver({ maxPayload: 25 });
                new Sender(socket);
                """, "positional parameters into its options object");
    }

    @Test
    void marksNamedNamespaceAndMethodStreamCalls() {
        assertMarked(
                """
                import { createWebSocketStream as stream } from 'ws';
                import * as ws from 'ws';
                stream(socket, { highWaterMark: 1024 });
                ws.createWebSocketStream(socket);
                """, "highWaterMark");
    }

    @Test
    void marksOwnedClientMessageCloseSendHeartbeatAndHandshakeListeners() {
        assertMarked(
                """
                import WebSocket from 'ws';
                const socket = new WebSocket(url);
                socket.on('message', onMessage);
                socket.once('close', onClose);
                socket.on('redirect', onRedirect);
                socket.send(payload, callback);
                socket.ping();
                socket.pong();
                socket.close(1000, reason);
                """, "string or Uint8Array/Buffer");
    }

    @Test
    void marksOwnedServerHandleUpgrade() {
        assertMarked(
                """
                import { WebSocketServer } from 'ws';
                const wss = new WebSocketServer({ noServer: true });
                wss.handleUpgrade(request, socket, head, onSocket);
                """, "callback once for arbitrary Duplex streams");
    }

    @Test
    void doesNotMarkUnrelatedConstructorsOptionsCallsOrShadowNames() {
        rewriteRun(spec -> spec.recipe(new FindWsJavaScriptRisks()), typescript(
                """
                class WebSocketServer { constructor(options: unknown) {} }
                const socket = otherFactory();
                new WebSocketServer({ perMessageDeflate: true, maxPayload: 1 });
                socket.on('message', listener);
                socket.close(1000, reason);
                createWebSocketStream(socket);
                """, source -> source.path("src/unrelated.ts").afterRecipe(cu ->
                        assertFalse(cu.printAll().contains("~~")))));
    }

    @Test
    void ignoresTypeOnlyImportsAndExcludedTrees() {
        rewriteRun(spec -> spec.recipe(new FindWsJavaScriptRisks()),
                typescript("import type WebSocket from 'ws'; declare const C: typeof WebSocket; new C(url);",
                        source -> source.path("src/types.ts").afterRecipe(cu -> assertFalse(cu.printAll().contains("Review ws")))),
                typescript("import WebSocket from 'ws'; new WebSocket(url);", source -> source.path("vendor/client.ts")),
                typescript("import WebSocket from 'ws'; new WebSocket(url);", source -> source.path("dist/client.js")),
                typescript("import WebSocket from 'ws'; new WebSocket(url);", source -> source.path("generated-api/client.ts")));
    }

    private void assertMarked(String source, String message) {
        rewriteRun(spec -> spec.recipe(new FindWsJavaScriptRisks()), typescript(source,
                spec -> spec.path("src/case.ts").after(actual -> actual)
                        .afterRecipe(cu -> assertTrue(cu.printAll().contains(message), cu::printAll))));
    }
}
