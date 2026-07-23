package com.huawei.clouds.openrewrite.ws;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Marks exact ws protocol, event, resource, compression, security, and stream compatibility boundaries. */
public final class FindWsJavaScriptRisks extends Recipe {
    private static final Set<String> INTERNAL_CLASSES = Set.of("Receiver", "Sender", "PerMessageDeflate");

    @Override
    public String getDisplayName() {
        return "Find ws 8.21.0 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks only ws-owned constructors, options, public internals, streams, event handlers, close/send calls, and unsupported deep imports with precise 8.21.0 review work.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> clients = Set.of();
            private Set<String> servers = Set.of();
            private Set<String> namespaces = Set.of();
            private Set<String> streams = Set.of();
            private Set<String> internals = Set.of();
            private Set<String> clientVariables = Set.of();
            private Set<String> serverVariables = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!WsSupport.isSource(cu.getSourcePath())) return cu;
                Set<String> oldClients = clients;
                Set<String> oldServers = servers;
                Set<String> oldNamespaces = namespaces;
                Set<String> oldStreams = streams;
                Set<String> oldInternals = internals;
                Set<String> oldClientVariables = clientVariables;
                Set<String> oldServerVariables = serverVariables;
                clients = new HashSet<>();
                servers = new HashSet<>();
                namespaces = new HashSet<>();
                streams = new HashSet<>();
                internals = new HashSet<>();
                clientVariables = new HashSet<>();
                serverVariables = new HashSet<>();
                scanBindings(cu, ctx);
                scanVariables(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                clients = oldClients;
                servers = oldServers;
                namespaces = oldNamespaces;
                streams = oldStreams;
                internals = oldInternals;
                clientVariables = oldClientVariables;
                serverVariables = oldServerVariables;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = WsJavaScriptSupport.moduleName(visited);
                if (WsJavaScriptSupport.isWsModule(module) && !WsSupport.PACKAGE.equals(module) &&
                    !"ws/package.json".equals(module)) {
                    return SearchResult.found(visited,
                            "This ws deep import is outside the target exports contract; use package-root exports (including newly public PerMessageDeflate/extension/subprotocol where applicable) and verify CJS/ESM interop");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String required = WsJavaScriptSupport.requireModule(visited);
                if (WsJavaScriptSupport.isWsModule(required) && !WsSupport.PACKAGE.equals(required) &&
                    !"ws/package.json".equals(required)) {
                    return SearchResult.found(visited,
                            "This require reaches ws internals outside the target exports map; migrate to a documented package-root export and test both require/import consumers");
                }
                if (isStreamCall(visited)) {
                    return SearchResult.found(visited,
                            "createWebSocketStream couples WebSocket close/error to Node stream end/error; verify object/binary modes, highWaterMark, backpressure, half-open shutdown, destroy, and listener cleanup");
                }
                if (!(visited.getSelect() instanceof J.Identifier owner)) return visited;
                String variable = owner.getSimpleName();
                if (clientVariables.contains(variable)) {
                    String risk = clientCallRisk(visited);
                    if (risk != null) return SearchResult.found(visited, risk);
                }
                if (serverVariables.contains(variable) && "handleUpgrade".equals(visited.getSimpleName())) {
                    return SearchResult.found(visited,
                            "handleUpgrade must authenticate before ownership transfer, reject duplicate upgrades, preserve unread head bytes, attach socket errors, and call the callback once for arbitrary Duplex streams");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                if (streams.contains(expressionName(visited.getFunction()))) {
                    return SearchResult.found(visited,
                            "createWebSocketStream couples WebSocket close/error to Node stream end/error; verify object/binary modes, highWaterMark, backpressure, half-open shutdown, destroy, and listener cleanup");
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                Kind kind = constructorKind(visited.getClazz());
                if (kind == Kind.CLIENT) {
                    return SearchResult.found(visited,
                            "Review ws client handshake/redirect credentials, TLS/proxy/createConnection behavior, event ordering, maxPayload/maxFragments/maxBufferedChunks, autoPong, compression memory, and close/error cleanup on 8.21.0");
                }
                if (kind == Kind.SERVER) {
                    return SearchResult.found(visited,
                            "Review ws server authentication/protocol selection, upgrade ownership, event ordering, payload/fragment limits, autoPong, compression memory, graceful close timeout, client tracking, and error cleanup on 8.21.0");
                }
                if (kind == Kind.INTERNAL) {
                    String name = typeName(visited.getClazz());
                    String message = "PerMessageDeflate".equals(name)
                            ? "8.20 converted PerMessageDeflate isServer/maxPayload positional parameters into its options object; use one options object and retest negotiation, thresholds, context takeover, concurrency, cleanup, and decompression limits"
                            : "This directly constructs a low-level ws " + name + "; verify its documented 8.21.0 constructor options, framing/masking/UTF-8 limits, backpressure, callbacks, and error codes";
                    return SearchResult.found(visited, message);
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                Kind kind = enclosingOptionsKind(property);
                if (kind != Kind.CLIENT && kind != Kind.SERVER && kind != Kind.INTERNAL) return visited;
                String risk = optionRisk(WsJavaScriptSupport.propertyName(visited.getName()), kind);
                return risk == null ? visited : SearchResult.found(visited, risk);
            }

            private String clientCallRisk(J.MethodInvocation call) {
                String method = call.getSimpleName();
                if ("close".equals(method)) {
                    return "ws 8.20.1 rejects close reasons other than string or Uint8Array/Buffer to prevent uninitialized-memory disclosure; verify UTF-8, 123-byte limit, codes, peer timeout, and close/error ordering";
                }
                if ("send".equals(method)) {
                    return "Verify send data type, binary/compress/fin/mask options, CONNECTING throws, callback errors, bufferedAmount/backpressure, Blob handling, and behavior after CLOSING/CLOSED";
                }
                if ("ping".equals(method) || "pong".equals(method)) {
                    return "Verify heartbeat ownership with autoPong, mask choice, payload limit, callback errors, liveness timeout, and terminate-vs-close behavior";
                }
                if ("on".equals(method) || "once".equals(method) || "addEventListener".equals(method)) {
                    String event = call.getArguments().isEmpty() ? null : WsJavaScriptSupport.stringLiteral(call.getArguments().get(0));
                    if ("message".equals(event) || "ping".equals(event) || "pong".equals(event)) {
                        return "message/ping/pong may be emitted multiple times synchronously by default; verify reentrancy, microtask assumptions, data/isBinary, Buffer-vs-ArrayBuffer/Blob, and listener exceptions";
                    }
                    if ("close".equals(event) || "error".equals(event)) {
                        return "Verify terminal event ordering, exactly-once cleanup, close code/reason Buffer decoding, abnormal termination, pending callbacks, and listener removal";
                    }
                    if ("redirect".equals(event) || "unexpected-response".equals(event) || "upgrade".equals(event)) {
                        return "Verify handshake listener ownership and credential/header policy; redirects can drop Authorization/Cookie on host or security-boundary changes and custom listeners alter default handling";
                    }
                }
                return null;
            }

            private String optionRisk(String option, Kind kind) {
                if ("allowSynchronousEvents".equals(option)) {
                    return "This option controls same-tick message/ping/pong reentrancy; 8.17 flipped the default to true, so preserve false explicitly where 8.16 ordering is required and test compressed and uncompressed frames";
                }
                if ("perMessageDeflate".equals(option)) {
                    return "permessage-deflate is client-default/server-opt-in and can cause catastrophic memory fragmentation; benchmark representative concurrency and verify thresholds, context takeover, window bits, zlib limits, cleanup, and maxPayload after inflation";
                }
                if (Set.of("maxPayload", "maxBufferedChunks", "maxFragments").contains(option)) {
                    return "8.21 adds retained-part limits and WS_ERR_TOO_MANY_BUFFERED_PARTS/1008; set and load-test payload, decompression, tiny-chunk, and fragment bounds without disabling the new defenses with zero unintentionally";
                }
                if ("autoPong".equals(option)) {
                    return "When autoPong is disabled the application owns every pong and heartbeat race; test masked client replies, duplicate pongs, liveness timers, backpressure, and termination";
                }
                if ("closeTimeout".equals(option)) {
                    return "8.19 made the 30-second graceful-close timeout configurable; test slow or malicious peers, timer cleanup, process shutdown, and whether close or terminate is operationally required";
                }
                if (Set.of("followRedirects", "maxRedirects", "headers", "finishRequest", "createConnection", "rejectUnauthorized", "origin", "auth").contains(option)) {
                    return "Review redirect, proxy/custom connection, TLS trust, origin, and confidential-header boundaries; preserve ws protections against leaking Authorization/Cookie across host or secure-to-insecure redirects";
                }
                if (Set.of("verifyClient", "handleProtocols", "noServer", "server", "port", "path").contains(option) && kind == Kind.SERVER) {
                    return "Review authentication and subprotocol selection before handleUpgrade transfers the socket; cover rejection status/headers, duplicate upgrades, path routing, async races, socket errors, and noServer shutdown";
                }
                if (Set.of("clientTracking", "skipUTF8Validation", "generateMask", "protocolVersion").contains(option)) {
                    return "This option changes connection ownership or protocol/security validation; verify RFC masking/UTF-8, random mask strength, supported protocol 8/13 response, client cleanup, and shutdown behavior";
                }
                return null;
            }

            private Kind enclosingOptionsKind(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(property.getId()))) {
                    return Kind.NONE;
                }
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.NewClass constructor && constructor.getClazz() != null) {
                        Kind kind = constructorKind(constructor.getClazz());
                        if (kind == Kind.CLIENT) {
                            return directArgument(constructor.getArguments(), object, 1, 2) ? kind : Kind.NONE;
                        }
                        if (kind == Kind.SERVER) return directArgument(constructor.getArguments(), object, 0) ? kind : Kind.NONE;
                        if (kind == Kind.INTERNAL) return directArgument(constructor.getArguments(), object, 0) ? kind : Kind.NONE;
                        return Kind.NONE;
                    }
                    cursor = cursor.getParent();
                }
                return Kind.NONE;
            }

            private boolean directArgument(List<Expression> args, J.NewClass object, int... indexes) {
                for (int index : indexes) {
                    if (args.size() > index && args.get(index).getId().equals(object.getId())) return true;
                }
                return false;
            }

            private boolean isStreamCall(J.MethodInvocation call) {
                if (call.getSelect() == null) return streams.contains(call.getSimpleName());
                return "createWebSocketStream".equals(call.getSimpleName()) &&
                       call.getSelect() instanceof J.Identifier owner && namespaces.contains(owner.getSimpleName());
            }

            private void scanBindings(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ignored) {
                        if (!WsSupport.PACKAGE.equals(WsJavaScriptSupport.moduleName(declaration)) ||
                            declaration.getImportClause() == null || declaration.getImportClause().isTypeOnly()) return declaration;
                        JS.ImportClause clause = declaration.getImportClause();
                        if (clause.getName() != null) clients.add(clause.getName().getSimpleName());
                        if (clause.getNamedBindings() instanceof JS.NamedImports named) {
                            for (JS.ImportSpecifier specifier : named.getElements()) {
                                if (specifier.getImportType()) continue;
                                String imported = WsJavaScriptSupport.importedName(specifier);
                                String local = WsJavaScriptSupport.localName(specifier);
                                if ("WebSocket".equals(imported)) clients.add(local);
                                if ("WebSocketServer".equals(imported)) servers.add(local);
                                if ("createWebSocketStream".equals(imported)) streams.add(local);
                                if (INTERNAL_CLASSES.contains(imported)) internals.add(local);
                            }
                        }
                        String namespace = WsJavaScriptSupport.namespaceAlias(clause.getNamedBindings());
                        if (namespace != null) namespaces.add(namespace);
                        return declaration;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext ignored) {
                        if (variable.getInitializer() instanceof J.MethodInvocation invocation &&
                            WsSupport.PACKAGE.equals(WsJavaScriptSupport.requireModule(invocation))) {
                            clients.add(variable.getSimpleName());
                            namespaces.add(variable.getSimpleName());
                        }
                        return variable;
                    }
                }.visit(cu, ctx);
            }

            private void scanVariables(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext ignored) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ignored);
                        if (visited.getInitializer() instanceof J.NewClass created) {
                            Kind kind = constructorKind(created.getClazz());
                            if (kind == Kind.CLIENT) clientVariables.add(visited.getSimpleName());
                            if (kind == Kind.SERVER) serverVariables.add(visited.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private Kind constructorKind(org.openrewrite.java.tree.TypeTree clazz) {
                String name = typeName(clazz);
                if (clazz instanceof J.Identifier) {
                    if (clients.contains(name)) return Kind.CLIENT;
                    if (servers.contains(name)) return Kind.SERVER;
                    if (internals.contains(name)) return Kind.INTERNAL;
                }
                if (clazz instanceof J.FieldAccess access && access.getTarget() instanceof J.Identifier owner &&
                    namespaces.contains(owner.getSimpleName())) {
                    if ("WebSocket".equals(name)) return Kind.CLIENT;
                    if ("WebSocketServer".equals(name) || "Server".equals(name)) return Kind.SERVER;
                    if (INTERNAL_CLASSES.contains(name)) return Kind.INTERNAL;
                }
                return Kind.NONE;
            }

            private String typeName(org.openrewrite.java.tree.TypeTree type) {
                if (type instanceof J.Identifier identifier) return identifier.getSimpleName();
                if (type instanceof J.FieldAccess access) return access.getSimpleName();
                return "";
            }

            private String expressionName(Expression expression) {
                if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
                if (expression instanceof J.FieldAccess access) return access.getSimpleName();
                return "";
            }
        };
    }

    enum Kind { CLIENT, SERVER, INTERNAL, NONE }
}
