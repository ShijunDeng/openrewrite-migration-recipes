package com.huawei.clouds.openrewrite.reactresizable;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.javascript.tree.JSX;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark only React Resizable-owned source constructs affected by the documented 1.x-to-3.x boundary. */
public final class FindReactResizableSourceRisks extends Recipe {
    private static final String HANDLE =
            "react-resizable 3 passes a DOM ref as the custom handle callback's second argument and requires it on the returned handle element; forward/attach that exact ref or DraggableCore can fail with 'not mounted on DragStart'";
    private static final String COMPONENT_HANDLE =
            "A custom React handle element must accept the injected handleAxis prop and forward the injected ref to its underlying DOM element; verify React.forwardRef/class innerRef wiring and prop forwarding";
    private static final String STOP =
            "react-resizable 3.1 uses the last onResize size for onResizeStop instead of recalculating from potentially stale batched React state; verify persisted size, analytics, snapping, controlled rerenders, and callback order";
    private static final String ASPECT =
            "react-resizable 3 rewrote lockAspectRatio constraint math; regression-test every north/west/corner handle with min/max constraints, controlled sizes, transformScale, rounding, and boundary slack";
    private static final String DRAGGABLE =
            "draggableOpts is passed to react-draggable, which moves from ^4.0.3 to ^4.5.0 in the target; verify nodeRef/offsetParent, scale, grid, bounds, cancellation, touch/mouse selection, and callback ownership";
    private static final String CALLBACK =
            "This controlled resize callback participates in React 18 batching and target callback-size fixes; verify state updates do not feed stale width/height back, event persistence assumptions, and start/resize/stop ordering";
    private static final String CONSTRAINT =
            "Target constraint/handle calculations changed across 3.0.x; verify axis, handle direction, min/max clamping, aspect ratio, transform scale, CSS cursor/placement, and pixel rounding";
    private static final String SIZE =
            "react-resizable 3.0.5 conditionally requires width and height whenever resizing is enabled; supply both controlled numeric dimensions and verify they track callback size";
    private static final String DEEP =
            "react-resizable 3.1.3 publishes a files whitelist and documents only the root API plus css/styles.css; this private/deep import may be absent or unstable, so use named Resizable/ResizableBox root exports";
    private static final String COMMON_JS =
            "This CommonJS react-resizable root import cannot prove named Resizable/ResizableBox binding ownership; migrate it to documented named root exports and audit every custom handle ref, callback, size, and constraint consumer";
    private static final String IDENTIFIER = "[A-Za-z_$][A-Za-z0-9_$]*";
    private static final Pattern MODERN_HANDLE = Pattern.compile(
            "(?s)\\bhandle\\s*=\\s*\\{\\s*\\(\\s*" + IDENTIFIER + "\\s*,\\s*(" + IDENTIFIER +
            ")\\s*\\)\\s*=>");

    @Override
    public String getDisplayName() {
        return "Find react-resizable 3 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact custom handle ref, controlled callback, constraint, width/height, react-draggable, " +
               "private import, CSS handle, and CommonJS boundaries owned by imported react-resizable components.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> aliases = Set.of();
            private Map<String, Integer> declarations = Map.of();
            private boolean ownedFile;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!ReactResizableSupport.isProjectPath(cu.getSourcePath())) return cu;
                Set<String> oldAliases = aliases;
                Map<String, Integer> oldDeclarations = declarations;
                boolean oldOwned = ownedFile;
                aliases = new HashSet<>();
                declarations = new HashMap<>();
                ownedFile = false;
                scan(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                aliases = oldAliases;
                declarations = oldDeclarations;
                ownedFile = oldOwned;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = ReactResizableSupport.moduleName(visited);
                if (module.startsWith(ReactResizableSupport.PACKAGE + "/") &&
                    !"react-resizable/css/styles.css".equals(module)) return mark(visited, DEEP);
                if (ReactResizableSupport.PACKAGE.equals(module)) {
                    JS.ImportClause clause = visited.getImportClause();
                    if (clause == null || clause.getName() != null ||
                        !(clause.getNamedBindings() instanceof JS.NamedImports)) {
                        return mark(visited, "react-resizable exposes named Resizable and ResizableBox APIs; inventory this default/namespace/side-effect import and move consumers to documented named root exports");
                    }
                }
                return visited;
            }

            @Override
            public JSX.Tag visitJsxTag(JSX.Tag tag, ExecutionContext ctx) {
                JSX.Tag visited = super.visitJsxTag(tag, ctx);
                String component = expressionName(visited.getOpenName());
                if (!ownedComponent(component)) return visited;
                Set<String> attributes = new HashSet<>();
                boolean disabled = false;
                for (JSX attribute : visited.getAttributes()) {
                    if (attribute instanceof JSX.Attribute named) {
                        String key = expressionName(named.getKey());
                        attributes.add(key);
                        if ("axis".equals(key) && named.printTrimmed(getCursor()).matches("(?s).*['\"]none['\"].*")) {
                            disabled = true;
                        }
                    }
                }
                return !disabled && (!attributes.contains("width") || !attributes.contains("height"))
                        ? mark(visited, SIZE) : visited;
            }

            @Override
            public JSX.Attribute visitJsxAttribute(JSX.Attribute attribute, ExecutionContext ctx) {
                JSX.Attribute visited = super.visitJsxAttribute(attribute, ctx);
                JSX.Tag tag = getCursor().firstEnclosing(JSX.Tag.class);
                if (tag == null || !ownedComponent(expressionName(tag.getOpenName()))) return visited;
                String name = expressionName(visited.getKey());
                String printed = visited.printTrimmed(getCursor());
                if ("handle".equals(name)) {
                    if (isModernHandle(printed) || nativeElementHandle(printed)) return visited;
                    if (printed.matches("(?s).*<\\s*[A-Z_$].*")) return mark(visited, COMPONENT_HANDLE);
                    return mark(visited, HANDLE);
                }
                if ("onResizeStop".equals(name)) return mark(visited, STOP);
                if ("lockAspectRatio".equals(name)) return mark(visited, ASPECT);
                if ("draggableOpts".equals(name)) return mark(visited, DRAGGABLE);
                if (Set.of("onResize", "onResizeStart").contains(name)) return mark(visited, CALLBACK);
                if (Set.of("axis", "resizeHandles", "minConstraints", "maxConstraints", "transformScale", "handleSize")
                        .contains(name)) return mark(visited, CONSTRAINT);
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null && "require".equals(visited.getSimpleName()) &&
                    !visited.getArguments().isEmpty() && visited.getArguments().get(0) instanceof J.Literal literal &&
                    literal.getValue() instanceof String module) {
                    if (module.startsWith(ReactResizableSupport.PACKAGE + "/")) return mark(visited, DEEP);
                    if (ReactResizableSupport.PACKAGE.equals(module)) return mark(visited, COMMON_JS);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (ownedFile && visited.getValue() instanceof String value &&
                    value.contains("react-resizable-handle")) {
                    return mark(visited, "Custom react-resizable handle classes are a CSS and hit-target contract; verify css/styles.css loading, all axis suffixes, cursor/position, focus accessibility, touch area, transforms, and application overrides");
                }
                return visited;
            }

            private void scan(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = ReactResizableSupport.moduleName(visited);
                        if (module.equals(ReactResizableSupport.PACKAGE) ||
                            module.startsWith(ReactResizableSupport.PACKAGE + "/")) ownedFile = true;
                        if (module.equals(ReactResizableSupport.PACKAGE) &&
                            !visited.printTrimmed(getCursor()).startsWith("import type ")) {
                            for (String api : Set.of("Resizable", "ResizableBox")) {
                                String alias = ReactResizableSupport.importedAlias(visited, api);
                                if (alias != null) aliases.add(alias);
                            }
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                                      ExecutionContext scanCtx) {
                        J.MethodDeclaration visited = super.visitMethodDeclaration(method, scanCtx);
                        declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                                    ExecutionContext scanCtx) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                        declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                                   ExecutionContext scanCtx) {
                        JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, scanCtx);
                        declarations.merge(visited.getName().getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean ownedComponent(String name) {
                return aliases.contains(name) && declarations.getOrDefault(name, 0) == 0;
            }
        };
    }

    private static boolean isModernHandle(String source) {
        Matcher matcher = MODERN_HANDLE.matcher(source);
        if (!matcher.find()) return false;
        String ref = matcher.group(1);
        return Pattern.compile("(?s)\\bref\\s*=\\s*\\{\\s*" + Pattern.quote(ref) + "\\s*}")
                .matcher(source.substring(matcher.end())).find();
    }

    private static boolean nativeElementHandle(String source) {
        return source.matches("(?s).*handle\\s*=\\s*\\{\\s*<\\s*[a-z][A-Za-z0-9:_-]*(?:\\s|/|>).*" );
    }

    private static String expressionName(Object expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        // A member expression such as UI.Resizable is not owned by a named root import of Resizable.
        if (expression instanceof J.FieldAccess) return "";
        if (expression instanceof J.Literal literal) return String.valueOf(literal.getValue());
        if (expression instanceof JS.TypeTreeExpression type) return expressionName(type.getExpression());
        return "";
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
