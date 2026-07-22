package com.huawei.clouds.openrewrite.losslessjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks lossless-json 4.x type, parser, numeric, serialization, and module-boundary risks. */
public final class FindLosslessJsonJavaScriptRisks extends Recipe {
    private static final Set<String> DEPRECATED_TYPES = Set.of(
            "JavaScriptValue", "JavaScriptObject", "JavaScriptArray", "JavaScriptPrimitive",
            "JSONValue", "JSONObject", "JSONArray", "JSONPrimitive");
    private static final Set<String> CALLBACK_TYPES = Set.of(
            "Reviver", "NumberParser", "Replacer", "NumberStringifier");
    private static final Set<String> NUMERIC_APIS = Set.of(
            "LosslessNumber", "parseLosslessNumber", "parseNumberAndBigInt", "toLosslessNumber",
            "isLosslessNumber", "isSafeNumber", "toSafeNumberOrThrow", "getUnsafeNumberReason");
    private static final Pattern JSON_KEY = Pattern.compile("\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"\\s*:");

    @Override
    public String getDisplayName() {
        return "Find lossless-json JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks deprecated types, unknown parse results, callbacks, numeric models, stringify behavior, " +
               "Date revival, duplicate keys, CommonJS, default imports, and deep imports at exact source nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> parseNames = Set.of();
            private Set<String> stringifyNames = Set.of();
            private Set<String> reviveDateNames = Set.of();
            private Set<String> parseBigIntNames = Set.of();
            private Set<String> configNames = Set.of();
            private Set<String> losslessNumberNames = Set.of();
            private Set<String> namespaces = Set.of();
            private Set<String> constructedLosslessNumbers = Set.of();
            private Set<String> declaredNames = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> oldParse = parseNames;
                Set<String> oldStringify = stringifyNames;
                Set<String> oldReviveDate = reviveDateNames;
                Set<String> oldParseBigInt = parseBigIntNames;
                Set<String> oldConfig = configNames;
                Set<String> oldLosslessNumber = losslessNumberNames;
                Set<String> oldNamespaces = namespaces;
                Set<String> oldConstructed = constructedLosslessNumbers;
                Set<String> oldDeclared = declaredNames;
                parseNames = new HashSet<>();
                stringifyNames = new HashSet<>();
                reviveDateNames = new HashSet<>();
                parseBigIntNames = new HashSet<>();
                configNames = new HashSet<>();
                losslessNumberNames = new HashSet<>();
                namespaces = new HashSet<>();
                constructedLosslessNumbers = new HashSet<>();
                declaredNames = findDeclaredNames(cu);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                parseNames = oldParse;
                stringifyNames = oldStringify;
                reviveDateNames = oldReviveDate;
                parseBigIntNames = oldParseBigInt;
                configNames = oldConfig;
                losslessNumberNames = oldLosslessNumber;
                namespaces = oldNamespaces;
                constructedLosslessNumbers = oldConstructed;
                declaredNames = oldDeclared;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = moduleName(visited);
                if (!isLosslessModule(module)) {
                    return visited;
                }
                if (!LosslessJsonManifestSupport.PACKAGE.equals(module)) {
                    return SearchResult.found(visited,
                            "Deep lossless-json imports bypass the 4.0.1 public exports map; import named APIs from the package root");
                }
                JS.ImportClause clause = visited.getImportClause();
                if (clause == null) {
                    return visited;
                }
                if (clause.getName() != null) {
                    return SearchResult.found(visited,
                            "lossless-json 4.0.1 exposes named public exports, not a default export; choose explicit named imports and verify ESM interop");
                }
                collectNamed(clause, "parse", parseNames);
                collectNamed(clause, "stringify", stringifyNames);
                collectNamed(clause, "reviveDate", reviveDateNames);
                collectNamed(clause, "parseNumberAndBigInt", parseBigIntNames);
                collectNamed(clause, "config", configNames);
                collectNamed(clause, "LosslessNumber", losslessNumberNames);
                String namespace = namespaceAlias(clause.getNamedBindings());
                if (namespace != null) {
                    namespaces.add(namespace);
                }
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                if (declaration == null || !LosslessJsonManifestSupport.PACKAGE.equals(moduleName(declaration))) {
                    return visited;
                }
                String imported = importedName(visited);
                if (DEPRECATED_TYPES.contains(imported)) {
                    return SearchResult.found(visited,
                            imported + " is deprecated in 4.x; replace the recursive assumption with unknown and narrow it using a schema, guard, Record, or array at the business boundary");
                }
                if (CALLBACK_TYPES.contains(imported)) {
                    return SearchResult.found(visited,
                            imported + " now accepts or returns unknown at its value boundary; update callback annotations and preserve reviver deletion/order or number-parser semantics");
                }
                if (NUMERIC_APIS.contains(imported)) {
                    return SearchResult.found(visited,
                            imported + " participates in lossless number, bigint, overflow, underflow, or serialization behavior; verify every conversion and downstream parser explicitly");
                }
                if ("config".equals(imported)) {
                    return SearchResult.found(visited,
                            "config is deprecated and circularRefs support was removed; eliminate the configuration after redesigning cyclic data");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String name = expressionName(visited.getFunction());
                if (("require".equals(name) || "import".equals(name)) && !visited.getArguments().isEmpty()) {
                    String module = stringLiteral(visited.getArguments().get(0));
                    if (isLosslessModule(module)) {
                        String message = LosslessJsonManifestSupport.PACKAGE.equals(module)
                                ? "CommonJS/dynamic loading must resolve the 4.0.1 conditional public export; verify destructuring, mocks, bundler, SSR, and runtime module mode"
                                : "Deep lossless-json loading bypasses the 4.0.1 exports map; use the public package root";
                        return SearchResult.found(visited, message);
                    }
                }
                if (unshadowed(parseNames, name)) {
                    return markParse(visited);
                }
                if (unshadowed(stringifyNames, name)) {
                    return markStringify(visited);
                }
                if (unshadowed(reviveDateNames, name)) {
                    return SearchResult.found(visited,
                            "reviveDate converts matching ISO strings into Date objects and is off by default; verify DTO, timezone, cache-key, and reserialization semantics");
                }
                if (unshadowed(parseBigIntNames, name)) {
                    return SearchResult.found(visited,
                            "parseNumberAndBigInt returns bigint for every integer and number for non-integers; verify arithmetic, schemas, storage, and serialization consumers");
                }
                if (unshadowed(configNames, name)) {
                    return SearchResult.found(visited,
                            "config is deprecated and cannot restore removed circularRefs behavior; redesign cycles before deleting this call");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null && unshadowed(parseNames, visited.getSimpleName())) {
                    return markParse(visited);
                }
                if (visited.getSelect() == null && unshadowed(stringifyNames, visited.getSimpleName())) {
                    return markStringify(visited);
                }
                if (visited.getSelect() == null && unshadowed(reviveDateNames, visited.getSimpleName())) {
                    return SearchResult.found(visited,
                            "reviveDate converts matching ISO strings into Date objects and is off by default; verify DTO, timezone, cache-key, and reserialization semantics");
                }
                if (visited.getSelect() == null && unshadowed(parseBigIntNames, visited.getSimpleName())) {
                    return SearchResult.found(visited,
                            "parseNumberAndBigInt returns bigint for every integer and number for non-integers; verify arithmetic, schemas, storage, and serialization consumers");
                }
                if (visited.getSelect() == null && unshadowed(configNames, visited.getSimpleName())) {
                    return SearchResult.found(visited,
                            "config is deprecated and cannot restore removed circularRefs behavior; redesign cycles before deleting this call");
                }
                if (visited.getSelect() == null &&
                    ("require".equals(visited.getSimpleName()) || "import".equals(visited.getSimpleName())) &&
                    !visited.getArguments().isEmpty()) {
                    String module = stringLiteral(visited.getArguments().get(0));
                    if (isLosslessModule(module)) {
                        String message = LosslessJsonManifestSupport.PACKAGE.equals(module)
                                ? "CommonJS/dynamic loading must resolve the 4.0.1 conditional public export; verify destructuring, mocks, bundler, SSR, and runtime module mode"
                                : "Deep lossless-json loading bypasses the 4.0.1 exports map; use the public package root";
                        return SearchResult.found(visited, message);
                    }
                }
                if (isNamespaceCall(visited, "parse")) {
                    return markParse(visited);
                }
                if (isNamespaceCall(visited, "stringify")) {
                    return markStringify(visited);
                }
                if (isNamespaceCall(visited, "parseNumberAndBigInt")) {
                    return SearchResult.found(visited,
                            "parseNumberAndBigInt changes all integer values to bigint; verify arithmetic, schemas, storage, structured clone, and serialization");
                }
                if ("valueOf".equals(visited.getSimpleName()) &&
                    visited.getSelect() instanceof J.Identifier identifier &&
                    constructedLosslessNumbers.contains(identifier.getSimpleName())) {
                    return SearchResult.found(visited,
                            "LosslessNumber.valueOf may return number or bigint and throws on overflow/underflow; choose an explicit safe conversion policy");
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (visited.getClazz() instanceof J.Identifier identifier &&
                    unshadowed(losslessNumberNames, identifier.getSimpleName())) {
                    return SearchResult.found(visited,
                            "LosslessNumber preserves numeric text; verify valueOf number/bigint/throw paths and stringify interoperability before converting it");
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                if (visited.getInitializer() instanceof J.NewClass created &&
                    created.getClazz() instanceof J.Identifier identifier &&
                    unshadowed(losslessNumberNames, identifier.getSimpleName())) {
                    constructedLosslessNumbers.add(visited.getSimpleName());
                }
                return visited;
            }

            private boolean isNamespaceCall(J.MethodInvocation invocation, String method) {
                return method.equals(invocation.getSimpleName()) &&
                       invocation.getSelect() instanceof J.Identifier identifier &&
                       unshadowed(namespaces, identifier.getSimpleName());
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

            private <T extends J> T markParse(T call) {
                java.util.List<Expression> arguments = call instanceof JS.FunctionCall function
                        ? function.getArguments() : ((J.MethodInvocation) call).getArguments();
                if (!arguments.isEmpty() && duplicateKey(stringLiteral(arguments.get(0)))) {
                    return SearchResult.found(call,
                            "lossless-json rejects duplicate keys with unequal values instead of last-value-wins; define and test the input-boundary policy");
                }
                if (arguments.size() >= 3 && !isUndefinedLike(arguments.get(2))) {
                    return SearchResult.found(call,
                            "parse returns unknown and this custom NumberParser controls every integer, decimal, exponent, negative zero, overflow, and underflow value; validate and narrow the result");
                }
                if (arguments.size() >= 2 && !isUndefinedLike(arguments.get(1))) {
                    return SearchResult.found(call,
                            "parse returns unknown and this Reviver runs leaf-to-root; returning undefined deletes a property, so validate the narrowed result and callback behavior");
                }
                return SearchResult.found(call,
                        "parse returns unknown and numbers default to LosslessNumber; validate or guard the result before property/iteration use and verify numeric conversions");
            }

            private <T extends J> T markStringify(T call) {
                java.util.List<Expression> arguments = call instanceof JS.FunctionCall function
                        ? function.getArguments() : ((J.MethodInvocation) call).getArguments();
                return SearchResult.found(call, arguments.size() >= 4
                        ? "Custom numberStringifiers must emit valid unquoted JSON numbers; distinguish them from replacer strings and test bigint/LosslessNumber consumers"
                        : "stringify can emit bigint and LosslessNumber values as unquoted JSON numbers; verify every downstream parser preserves their precision");
            }
        };
    }

    private static void collectNamed(JS.ImportClause clause, String imported, Set<String> aliases) {
        if (!(clause.getNamedBindings() instanceof JS.NamedImports named)) {
            return;
        }
        for (JS.ImportSpecifier specifier : named.getElements()) {
            if (imported.equals(importedName(specifier))) {
                Expression expression = specifier.getSpecifier();
                if (expression instanceof J.Identifier identifier) {
                    aliases.add(identifier.getSimpleName());
                } else if (expression instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
                    aliases.add(identifier.getSimpleName());
                }
            }
        }
    }

    private static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof JS.Alias alias) {
            return alias.getPropertyName().getSimpleName();
        }
        return "";
    }

    private static String namespaceAlias(Expression bindings) {
        if (bindings instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return null;
    }

    private static String moduleName(JS.Import declaration) {
        return stringLiteral(declaration.getModuleSpecifier());
    }

    private static boolean isLosslessModule(String module) {
        return module != null && (LosslessJsonManifestSupport.PACKAGE.equals(module) ||
                                  module.startsWith(LosslessJsonManifestSupport.PACKAGE + "/"));
    }

    private static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof J.FieldAccess field) {
            return field.getSimpleName();
        }
        return "";
    }

    private static String stringLiteral(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : null;
    }

    private static boolean isUndefinedLike(Expression expression) {
        return expression instanceof J.Empty ||
               expression instanceof J.Identifier identifier && "undefined".equals(identifier.getSimpleName()) ||
               expression instanceof J.Literal literal && literal.getValue() == null;
    }

    private static boolean duplicateKey(String json) {
        if (json == null) {
            return false;
        }
        Matcher matcher = JSON_KEY.matcher(json);
        Set<String> keys = new HashSet<>();
        while (matcher.find()) {
            if (!keys.add(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }
}
