package com.huawei.clouds.openrewrite.ws;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.marker.Comma;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.java.marker.TrailingComma;
import org.openrewrite.marker.Markers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Preserves the 8.16.0 asynchronous event-dispatch default where ownership is provable. */
public final class PreserveWs816EventScheduling extends ScanningRecipe<PreserveWs816EventScheduling.Projects> {
    enum VersionState { WS_8_16, OTHER, CONFLICT }

    static final class Projects {
        final Map<Path, VersionState> roots = new HashMap<>();

        void record(Path root, VersionState state) {
            roots.merge(root, state, (left, right) -> left == right ? left : VersionState.CONFLICT);
        }

        boolean isWs816Source(Path source) {
            Path nearest = null;
            VersionState state = null;
            for (Map.Entry<Path, VersionState> entry : roots.entrySet()) {
                Path root = entry.getKey();
                boolean matches = root.toString().isEmpty() || source.startsWith(root);
                if (matches && (nearest == null || root.getNameCount() > nearest.getNameCount())) {
                    nearest = root;
                    state = entry.getValue();
                }
            }
            return state == VersionState.WS_8_16;
        }
    }

    @Override
    public String getDisplayName() {
        return "Preserve ws 8.16.0 message event scheduling";
    }

    @Override
    public String getDescription() {
        return "For projects proven to declare workbook source 8.16.0, adds allowSynchronousEvents: false to direct literal WebSocket/WebSocketServer options so 8.21.0 preserves the old default.";
    }

    @Override
    public Projects getInitialValue(ExecutionContext ctx) {
        return new Projects();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Projects projects) {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!WsSupport.isPackageJson(document.getSourcePath()) ||
                    !WsSupport.PACKAGE.equals(WsSupport.key(visited)) ||
                    WsSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String version = visited.getValue().getMarkers().findFirst(OriginalWsVersion.class)
                        .map(OriginalWsVersion::getVersion)
                        .orElseGet(() -> WsSupport.normalizedVersion(WsSupport.stringValue(visited)));
                if (!WsSupport.SOURCE_VERSIONS.contains(version)) return visited;
                Path parent = document.getSourcePath().getParent();
                projects.record(parent == null ? Path.of("") : parent,
                        WsSupport.EVENT_DEFAULT_SOURCE.equals(version) ? VersionState.WS_8_16 : VersionState.OTHER);
                return visited;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Projects projects) {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> clients = Set.of();
            private Set<String> servers = Set.of();
            private Set<String> namespaces = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!WsSupport.isSource(cu.getSourcePath()) || !projects.isWs816Source(cu.getSourcePath())) return cu;
                Set<String> oldClients = clients;
                Set<String> oldServers = servers;
                Set<String> oldNamespaces = namespaces;
                clients = new HashSet<>();
                servers = new HashSet<>();
                namespaces = new HashSet<>();
                scanBindings(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                clients = oldClients;
                servers = oldServers;
                namespaces = oldNamespaces;
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                Kind kind = constructorKind(visited.getClazz());
                if (kind == Kind.NONE) return visited;
                J.NewClass options = optionsObject(visited.getArguments(), kind);
                if (options == null || hasAllowSynchronousEvents(options)) return visited;
                J.NewClass migrated = addFalseOption(options);
                return visited.withArguments(ListUtils.map(visited.getArguments(), argument ->
                        argument.getId().equals(options.getId()) ? migrated : argument));
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
                                if ("WebSocket".equals(imported)) clients.add(WsJavaScriptSupport.localName(specifier));
                                if ("WebSocketServer".equals(imported)) servers.add(WsJavaScriptSupport.localName(specifier));
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

            private Kind constructorKind(org.openrewrite.java.tree.TypeTree clazz) {
                if (clazz instanceof J.Identifier identifier) {
                    if (clients.contains(identifier.getSimpleName())) return Kind.CLIENT;
                    if (servers.contains(identifier.getSimpleName())) return Kind.SERVER;
                }
                if (clazz instanceof J.FieldAccess access && access.getTarget() instanceof J.Identifier owner) {
                    String name = access.getSimpleName();
                    if (namespaces.contains(owner.getSimpleName()) && "WebSocket".equals(name)) return Kind.CLIENT;
                    if (namespaces.contains(owner.getSimpleName()) &&
                        ("WebSocketServer".equals(name) || "Server".equals(name))) return Kind.SERVER;
                }
                return Kind.NONE;
            }

            private J.NewClass optionsObject(List<Expression> arguments, Kind kind) {
                if (kind == Kind.SERVER) return objectAt(arguments, 0);
                J.NewClass second = objectAt(arguments, 1);
                return second != null ? second : objectAt(arguments, 2);
            }

            private J.NewClass objectAt(List<Expression> arguments, int index) {
                if (arguments.size() <= index || !(arguments.get(index) instanceof J.NewClass object) ||
                    object.getClazz() != null || object.getBody() == null) return null;
                return object;
            }

            private boolean hasAllowSynchronousEvents(J.NewClass object) {
                return object.getBody().getStatements().stream().anyMatch(statement ->
                        statement instanceof JS.PropertyAssignment property &&
                        "allowSynchronousEvents".equals(WsJavaScriptSupport.propertyName(property.getName())));
            }

            private J.NewClass addFalseOption(J.NewClass object) {
                J.Block body = object.getBody();
                List<JRightPadded<Statement>> statements = new ArrayList<>(body.getPadding().getStatements());
                Space prefix = statements.isEmpty() ? Space.SINGLE_SPACE : statements.get(0).getElement().getPrefix();
                if (!statements.isEmpty()) {
                    int last = statements.size() - 1;
                    JRightPadded<Statement> previous = statements.get(last);
                    statements.set(last, previous.withMarkers(previous.getMarkers()
                            .removeByType(Comma.class).removeByType(TrailingComma.class)));
                }
                J.Identifier name = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(),
                        "allowSynchronousEvents", null, null);
                J.Literal value = new J.Literal(Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
                        false, "false", List.of(), JavaType.Primitive.Boolean);
                statements.add(JRightPadded.build(new JS.PropertyAssignment(Tree.randomId(), prefix, Markers.EMPTY,
                        JRightPadded.build(name), JS.PropertyAssignment.AssigmentToken.Colon, value)));
                J.Block migrated = body.getPadding().withStatements(statements);
                if (body.getStatements().isEmpty() && body.getEnd().isEmpty()) migrated = migrated.withEnd(Space.SINGLE_SPACE);
                return object.withBody(migrated);
            }
        };
    }

    enum Kind { CLIENT, SERVER, NONE }
}
