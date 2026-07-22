package com.huawei.clouds.openrewrite.marked;

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

/** Marks Marked 17 API, renderer, tokenizer, async, sanitization, ESM, and token-model risks. */
public final class FindMarked17JavaScriptRisks extends Recipe {
    private static final Set<String> REMOVED_OPTIONS = Set.of(
            "highlight", "langPrefix", "mangle", "baseUrl", "smartypants", "xhtml",
            "headerIds", "headerPrefix", "sanitize", "sanitizer");
    private static final Set<String> EXTENSION_BOUNDARIES = Set.of(
            "renderer", "tokenizer", "walkTokens", "extensions", "hooks");
    private static final Set<String> CHANGED_API_IMPORTS = Set.of(
            "Renderer", "TextRenderer", "Tokenizer", "Lexer", "Parser", "Marked");
    private static final Set<String> CHANGED_TYPE_IMPORTS = Set.of(
            "MarkedOptions", "MarkedExtension", "Token", "Tokens", "TokensList",
            "RendererObject", "TokenizerObject", "RendererExtensionFunction", "TokenizerExtensionFunction");

    @Override
    public String getDisplayName() {
        return "Find Marked 17 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed callbacks/options, parser output, renderer/tokenizer/token changes, async contracts, " +
               "sanitization boundaries, ESM loading, deep imports, types, and shared-instance mutation.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> markedNames = Set.of();
            private Set<String> parseNames = Set.of();
            private Set<String> parseInlineNames = Set.of();
            private Set<String> useNames = Set.of();
            private Set<String> optionsNames = Set.of();
            private Set<String> lexerNames = Set.of();
            private Set<String> parserNames = Set.of();
            private Set<String> walkTokensNames = Set.of();
            private Set<String> rendererNames = Set.of();
            private Set<String> tokenizerNames = Set.of();
            private Set<String> markedClassNames = Set.of();
            private Set<String> lexerClassNames = Set.of();
            private Set<String> namespaces = Set.of();
            private Set<String> declaredNames = Set.of();
            private Set<String> rendererVariables = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> oldMarked = markedNames;
                Set<String> oldParse = parseNames;
                Set<String> oldParseInline = parseInlineNames;
                Set<String> oldUse = useNames;
                Set<String> oldOptions = optionsNames;
                Set<String> oldLexer = lexerNames;
                Set<String> oldParser = parserNames;
                Set<String> oldWalkTokens = walkTokensNames;
                Set<String> oldRenderer = rendererNames;
                Set<String> oldTokenizer = tokenizerNames;
                Set<String> oldMarkedClass = markedClassNames;
                Set<String> oldLexerClass = lexerClassNames;
                Set<String> oldNamespaces = namespaces;
                Set<String> oldDeclared = declaredNames;
                Set<String> oldRendererVariables = rendererVariables;
                markedNames = new HashSet<>();
                parseNames = new HashSet<>();
                parseInlineNames = new HashSet<>();
                useNames = new HashSet<>();
                optionsNames = new HashSet<>();
                lexerNames = new HashSet<>();
                parserNames = new HashSet<>();
                walkTokensNames = new HashSet<>();
                rendererNames = new HashSet<>();
                tokenizerNames = new HashSet<>();
                markedClassNames = new HashSet<>();
                lexerClassNames = new HashSet<>();
                namespaces = new HashSet<>();
                declaredNames = findDeclaredNames(cu);
                rendererVariables = new HashSet<>();
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                markedNames = oldMarked;
                parseNames = oldParse;
                parseInlineNames = oldParseInline;
                useNames = oldUse;
                optionsNames = oldOptions;
                lexerNames = oldLexer;
                parserNames = oldParser;
                walkTokensNames = oldWalkTokens;
                rendererNames = oldRenderer;
                tokenizerNames = oldTokenizer;
                markedClassNames = oldMarkedClass;
                lexerClassNames = oldLexerClass;
                namespaces = oldNamespaces;
                declaredNames = oldDeclared;
                rendererVariables = oldRendererVariables;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = MarkedJavaScriptSupport.moduleName(visited);
                if (!MarkedJavaScriptSupport.isMarkedModule(module)) {
                    return visited;
                }
                if (!MarkedManifestSupport.PACKAGE.equals(module)) {
                    return SearchResult.found(visited,
                            "Deep Marked imports are outside the 17.0.6 exports map; use named APIs from the public package root");
                }
                JS.ImportClause clause = visited.getImportClause();
                if (clause == null) {
                    return visited;
                }
                if (clause.getName() != null) {
                    return SearchResult.found(visited,
                            "Marked 17 has no default export and is ESM-only; replace interop-dependent default loading with explicit named imports and update tests/bundles");
                }
                collect(clause);
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                if (declaration == null || !MarkedManifestSupport.PACKAGE.equals(
                        MarkedJavaScriptSupport.moduleName(declaration))) {
                    return visited;
                }
                String imported = MarkedJavaScriptSupport.importedName(visited);
                if ("Slugger".equals(imported)) {
                    return SearchResult.found(visited,
                            "Slugger is no longer exported; use marked-gfm-heading-id or application-owned slug behavior and verify duplicate headings/links");
                }
                if (CHANGED_API_IMPORTS.contains(imported)) {
                    return SearchResult.found(visited,
                            imported + " crosses renderer, tokenizer, parser, token, or generic type changes; compile custom subclasses/extensions and compare corpus snapshots");
                }
                if (CHANGED_TYPE_IMPORTS.contains(imported)) {
                    return SearchResult.found(visited,
                            imported + " changed across Marked's TypeScript, token-object renderer, async, and list-token migrations; update the exact business boundary instead of casting");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!insideMarkedOptions(property)) {
                    return visited;
                }
                String name = MarkedJavaScriptSupport.propertyName(visited.getName());
                if (REMOVED_OPTIONS.contains(name)) {
                    String replacement = switch (name) {
                        case "highlight", "langPrefix" -> "marked-highlight";
                        case "mangle" -> "marked-mangle";
                        case "baseUrl" -> "marked-base-url";
                        case "smartypants" -> "marked-smartypants";
                        case "xhtml" -> "marked-xhtml";
                        case "headerIds", "headerPrefix" -> "marked-gfm-heading-id";
                        default -> "an output sanitizer such as DOMPurify or sanitize-html";
                    };
                    return SearchResult.found(visited,
                            name + " was removed in Marked 8; migrate to " + replacement + " and verify ordering, output, and security semantics");
                }
                if ("useNewRenderer".equals(name)) {
                    return SearchResult.found(visited,
                            "useNewRenderer was removed in Marked 14; true is deleted by the deterministic recipe, while false/dynamic requires a token-object renderer migration");
                }
                if (EXTENSION_BOUNDARIES.contains(name)) {
                    return SearchResult.found(visited,
                            name + " crosses token-object renderer, tokenizer/rules, list-token, escaping, async, and shared-instance changes; migrate against public extension APIs and snapshot real Markdown");
                }
                if ("async".equals(name)) {
                    return SearchResult.found(visited,
                            "Marked 14 enforces async extension contracts and parse may return a Promise; align this option with await/caller types and never force false over an async extension");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String method = visited.getSimpleName();
                if (isRootLoadingCall(visited)) {
                    return markLoading(visited);
                }
                if (isDirectAlias(visited, markedNames) || isDirectAlias(visited, parseNames) ||
                    isOwnedCall(visited, "parse") || isNamespaceMarkedCall(visited)) {
                    return markParse(visited);
                }
                if (isDirectAlias(visited, parseInlineNames) || isOwnedCall(visited, "parseInline")) {
                    return SearchResult.found(visited,
                            "parseInline output changed with CommonMark, escaping, and renderer token objects; Marked does not sanitize HTML, so compare corpus output and sanitize untrusted results");
                }
                if (isDirectAlias(visited, useNames) || isDirectAlias(visited, optionsNames) ||
                    isOwnedCall(visited, "use") || isOwnedCall(visited, "setOptions") ||
                    isOwnedCall(visited, "options")) {
                    return SearchResult.found(visited,
                            "Global Marked options/extensions are cumulative and shared; renderer/tokenizer/async semantics changed and concurrent parses can interfere, so prefer an isolated Marked instance when needed");
                }
                if (isDirectAlias(visited, lexerNames) || isOwnedCall(visited, "lexer")) {
                    return SearchResult.found(visited,
                            "Lexer/token shapes changed through CommonMark 0.31, escaping, and Marked 17 list/checkbox updates; update token consumers and AST snapshots");
                }
                if (isDirectAlias(visited, parserNames) || isOwnedCall(visited, "parser")) {
                    return SearchResult.found(visited,
                            "Parser and renderer now consume token objects and changed escaping/list behavior; verify custom parser output against real Markdown snapshots");
                }
                if (isDirectAlias(visited, walkTokensNames) || isOwnedCall(visited, "walkTokens")) {
                    return SearchResult.found(visited,
                            "walkTokens sees changed list, text, checkbox, and extension token shapes; update exhaustive switches, child traversal, mutation, and async return handling");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String name = MarkedJavaScriptSupport.expressionName(visited.getFunction());
                if (("require".equals(name) || "import".equals(name)) && !visited.getArguments().isEmpty() &&
                    MarkedJavaScriptSupport.isMarkedModule(MarkedJavaScriptSupport.stringLiteral(visited.getArguments().get(0)))) {
                    return markLoading(visited);
                }
                if (unshadowed(markedNames, name) || unshadowed(parseNames, name)) {
                    return markParse(visited);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String name = markedConstructorName(visited.getClazz());
                if (name == null) {
                    return visited;
                }
                if (unshadowed(rendererNames, name) || namespaceMember(visited.getClazz(), "Renderer")) {
                    return SearchResult.found(visited,
                            "Renderer methods now receive token objects and list/checkbox/escaping behavior changed; migrate every override and parse nested tokens with this.parser");
                }
                if (unshadowed(tokenizerNames, name) || namespaceMember(visited.getClazz(), "Tokenizer")) {
                    return SearchResult.found(visited,
                            "Tokenizer rules and emitted token shapes changed; use public extension APIs and regression-test precedence, raw consumption, child tokens, and escaping");
                }
                if (unshadowed(markedClassNames, name) || namespaceMember(visited.getClazz(), "Marked")) {
                    return SearchResult.found(visited,
                            "A Marked instance isolates cumulative extensions, but renderer/tokenizer/async hooks and token generics changed; verify instance concurrency and output snapshots");
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                if (visited.getInitializer() instanceof J.NewClass created &&
                    ((created.getClazz() instanceof J.Identifier identifier &&
                      unshadowed(rendererNames, identifier.getSimpleName())) ||
                     namespaceMember(created.getClazz(), "Renderer"))) {
                    rendererVariables.add(visited.getSimpleName());
                }
                return visited;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment visited = super.visitAssignment(assignment, ctx);
                if (visited.getVariable() instanceof J.FieldAccess field) {
                    if ("innerHTML".equals(field.getSimpleName()) && isMarkedParseExpression(visited.getAssignment())) {
                        return SearchResult.found(visited,
                                "Marked does not sanitize HTML; sanitize untrusted parse output with a maintained allowlist sanitizer before assigning innerHTML");
                    }
                    if (field.getTarget() instanceof J.Identifier identifier &&
                        rendererVariables.contains(identifier.getSimpleName())) {
                        return SearchResult.found(visited,
                                "This Renderer override must accept the Marked 17 token object and use this.parser for nested tokens; verify list, escaping, and fallback behavior");
                    }
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if ("rules".equals(visited.getSimpleName()) &&
                    ((visited.getTarget() instanceof J.Identifier identifier &&
                      unshadowed(lexerClassNames, identifier.getSimpleName())) ||
                     namespaceMember(visited.getTarget(), "Lexer"))) {
                    return SearchResult.found(visited,
                            "Lexer.rules internals changed and intermediate rules were removed; move to a public tokenizer/extension instead of mutating internal regex tables");
                }
                if (namespaceMember(visited, "Slugger")) {
                    return SearchResult.found(visited,
                            "Slugger is no longer exported; use marked-gfm-heading-id or application-owned slug behavior and verify duplicate headings/links");
                }
                if (namespaceMember(visited, CHANGED_TYPE_IMPORTS)) {
                    return SearchResult.found(visited,
                            visited.getSimpleName() + " changed across Marked's TypeScript, token-object renderer, async, and list-token migrations; update the exact business boundary instead of casting");
                }
                return visited;
            }

            private void collect(JS.ImportClause clause) {
                MarkedJavaScriptSupport.collectNamed(clause, "marked", markedNames);
                MarkedJavaScriptSupport.collectNamed(clause, "parse", parseNames);
                MarkedJavaScriptSupport.collectNamed(clause, "parseInline", parseInlineNames);
                MarkedJavaScriptSupport.collectNamed(clause, "use", useNames);
                MarkedJavaScriptSupport.collectNamed(clause, "setOptions", optionsNames);
                MarkedJavaScriptSupport.collectNamed(clause, "options", optionsNames);
                MarkedJavaScriptSupport.collectNamed(clause, "lexer", lexerNames);
                MarkedJavaScriptSupport.collectNamed(clause, "parser", parserNames);
                MarkedJavaScriptSupport.collectNamed(clause, "walkTokens", walkTokensNames);
                MarkedJavaScriptSupport.collectNamed(clause, "Renderer", rendererNames);
                MarkedJavaScriptSupport.collectNamed(clause, "Tokenizer", tokenizerNames);
                MarkedJavaScriptSupport.collectNamed(clause, "Marked", markedClassNames);
                MarkedJavaScriptSupport.collectNamed(clause, "Lexer", lexerClassNames);
                String namespace = MarkedJavaScriptSupport.namespaceAlias(clause.getNamedBindings());
                if (namespace != null) {
                    namespaces.add(namespace);
                }
            }

            private boolean isDirectAlias(J.MethodInvocation invocation, Set<String> aliases) {
                return invocation.getSelect() == null && unshadowed(aliases, invocation.getSimpleName());
            }

            private boolean isOwnedCall(J.MethodInvocation invocation, String method) {
                return method.equals(invocation.getSimpleName()) &&
                       isMarkedApiOwner(invocation.getSelect());
            }

            private boolean isNamespaceMarkedCall(J.MethodInvocation invocation) {
                return "marked".equals(invocation.getSimpleName()) &&
                       invocation.getSelect() instanceof J.Identifier identifier &&
                       unshadowed(namespaces, identifier.getSimpleName());
            }

            private boolean isMarkedApiOwner(Expression select) {
                if (select instanceof J.Identifier identifier) {
                    return unshadowed(markedNames, identifier.getSimpleName()) ||
                           unshadowed(namespaces, identifier.getSimpleName());
                }
                return namespaceMember(select, "marked");
            }

            private String markedConstructorName(org.openrewrite.java.tree.TypeTree clazz) {
                if (clazz instanceof J.Identifier identifier) {
                    return identifier.getSimpleName();
                }
                return clazz instanceof J.FieldAccess field ? field.getSimpleName() : null;
            }

            private boolean namespaceMember(org.openrewrite.Tree expression, String member) {
                return namespaceMember(expression, Set.of(member));
            }

            private boolean namespaceMember(org.openrewrite.Tree expression, Set<String> members) {
                if (!(expression instanceof J.FieldAccess field) || !members.contains(field.getSimpleName())) {
                    return false;
                }
                Expression target = field.getTarget();
                if (target instanceof J.Identifier identifier) {
                    return unshadowed(namespaces, identifier.getSimpleName());
                }
                return target instanceof J.FieldAccess nested && namespaceMember(nested, "marked");
            }

            private boolean isRootLoadingCall(J.MethodInvocation invocation) {
                return invocation.getSelect() == null &&
                       ("require".equals(invocation.getSimpleName()) || "import".equals(invocation.getSimpleName())) &&
                       !invocation.getArguments().isEmpty() &&
                       MarkedJavaScriptSupport.isMarkedModule(
                               MarkedJavaScriptSupport.stringLiteral(invocation.getArguments().get(0)));
            }

            private <T extends J> T markLoading(T call) {
                List<Expression> arguments = call instanceof J.MethodInvocation method
                        ? method.getArguments() : ((JS.FunctionCall) call).getArguments();
                String module = arguments.isEmpty() ? null : MarkedJavaScriptSupport.stringLiteral(arguments.get(0));
                return SearchResult.found(call, MarkedManifestSupport.PACKAGE.equals(module)
                        ? "Marked 16 removed the CommonJS build and 17.0.6 is ESM-only; migrate loading and verify Node 20, Jest/Vitest, bundler, SSR, mocks, and CLI execution"
                        : "Deep Marked loading is outside the 17.0.6 exports map; use named ESM APIs from the package root");
            }

            private <T extends J> T markParse(T call) {
                List<Expression> arguments = call instanceof J.MethodInvocation method
                        ? method.getArguments() : ((JS.FunctionCall) call).getArguments();
                if (arguments.size() >= 3 || arguments.size() >= 2 && isFunctionLike(arguments.get(1))) {
                    return SearchResult.found(call,
                            "Marked 5 removed the callback API; migrate error and completion control flow to Promise/await without dropping synchronous exceptions");
                }
                if (arguments.size() >= 2 && objectHasProperty(arguments.get(1), "async")) {
                    return SearchResult.found(call,
                            "This parse has an explicit async contract; Marked 14 rejects forcing async false over async extensions, so align await, caller types, hooks, and concurrency");
                }
                return SearchResult.found(call,
                        "Marked parse output changes across CommonMark, renderer, list token, and escaping releases; Marked does not sanitize HTML, so snapshot real Markdown and sanitize untrusted output");
            }

            private boolean insideMarkedOptions(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(property.getId()))) {
                    return false;
                }
                J.MethodInvocation invocation = getCursor().firstEnclosing(J.MethodInvocation.class);
                if (invocation == null || invocation.getArguments().stream().noneMatch(argument -> argument.getId().equals(object.getId()))) {
                    return false;
                }
                return isDirectAlias(invocation, markedNames) || isDirectAlias(invocation, parseNames) ||
                       isDirectAlias(invocation, useNames) || isDirectAlias(invocation, optionsNames) ||
                       isOwnedCall(invocation, "parse") || isOwnedCall(invocation, "use") ||
                       isOwnedCall(invocation, "setOptions") || isOwnedCall(invocation, "options");
            }

            private boolean isMarkedParseExpression(Expression expression) {
                if (expression instanceof J.MethodInvocation invocation) {
                    return isDirectAlias(invocation, markedNames) || isDirectAlias(invocation, parseNames) ||
                           isOwnedCall(invocation, "parse");
                }
                return false;
            }

            private boolean unshadowed(Set<String> importedNames, String name) {
                return importedNames.contains(name) && !declaredNames.contains(name);
            }

            private Set<String> findDeclaredNames(JS.CompilationUnit cu) {
                Set<String> names = new HashSet<>();
                new JavaScriptIsoVisitor<Set<String>>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, Set<String> accumulator) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, accumulator);
                        accumulator.add(visited.getSimpleName());
                        return visited;
                    }
                }.visit(cu, names);
                return names;
            }
        };
    }

    private static boolean objectHasProperty(Expression expression, String propertyName) {
        if (!(expression instanceof J.NewClass object) || object.getClazz() != null || object.getBody() == null) {
            return false;
        }
        return object.getBody().getStatements().stream().anyMatch(statement ->
                statement instanceof JS.PropertyAssignment property &&
                propertyName.equals(MarkedJavaScriptSupport.propertyName(property.getName())));
    }

    private static boolean isFunctionLike(Expression expression) {
        return expression instanceof J.Lambda || expression.getClass().getSimpleName().contains("Function");
    }

}
