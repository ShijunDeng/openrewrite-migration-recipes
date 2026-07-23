package com.huawei.clouds.openrewrite.orgjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks JSON-java behavior changes that require application policy or regression fixtures. */
public final class FindOrgJsonJavaRisks extends Recipe {
    private static final String PREFIX = "org.json.";
    private static final Set<String> NUMERIC_READS = Set.of(
            "getInt", "getLong", "getFloat", "getDouble", "getNumber", "getBigInteger", "getBigDecimal",
            "optInt", "optLong", "optFloat", "optDouble", "optNumber", "optBigInteger", "optBigDecimal",
            "stringToValue", "numberToString", "doubleToString");
    private static final Set<String> STRUCTURAL_WRITES = Set.of(
            "put", "putOnce", "putOpt", "putAll", "accumulate", "append", "increment", "wrap");
    private static final Set<String> SERIALIZATION = Set.of("toString", "write", "valueToString", "quote", "join");

    @Override
    public String getDisplayName() {
        return "Find JSON-java 20250107 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks parsing strictness, duplicate/null keys, numeric conversion, JSON Pointer, XML, reflection, cycles/depth, equality and serialization decisions changed through 20250107.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return OrgJsonSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = fqn(visited.getType());
                if (type.equals(PREFIX + "JSONObject") || type.equals(PREFIX + "JSONArray")) {
                    JavaType.Method constructor = visited.getConstructorType();
                    if (constructor != null && constructor.getParameterTypes().stream().anyMatch(FindOrgJsonJavaRisks::stringOrTokener)) {
                        return SearchResult.found(visited, "JSON text parsing changed for duplicate keys, strict-mode syntax, nesting depth and numeric conversion; replay accepted/rejected payloads and exact exception contracts");
                    }
                    if (constructor != null && constructor.getParameterTypes().stream().anyMatch(FindOrgJsonJavaRisks::mapCollectionOrObject)) {
                        return SearchResult.found(visited, "Object/Map/Collection wrapping changed for null or complex keys, cycles, maximum depth, bean reflection and annotations; test graph identity, inaccessible members and native/JPMS access");
                    }
                }
                if (type.equals(PREFIX + "JSONPointer")) {
                    return SearchResult.found(visited, "JSON Pointer token escaping, URI fragments, array indices and error behavior received a breaking fix; replay pointers containing ~, /, percent encoding, empty tokens and invalid indices");
                }
                if (type.equals(PREFIX + "JSONTokener")) {
                    return SearchResult.found(visited, "JSONTokener parsing and position/error reporting changed; verify UTF-8 InputStream decoding, close ownership, back/skip behavior, permissive syntax and nesting limits");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = visited.getMethodType();
                if (type == null || !fqn(type.getDeclaringType()).startsWith(PREFIX)) return visited;
                String owner = fqn(type.getDeclaringType());
                String name = visited.getSimpleName();
                if (NUMERIC_READS.contains(name)) {
                    return SearchResult.found(visited, "JSON-java numeric coercion changed for exponentials, leading zeros, precision and out-of-range values; assert exact Number subtype, default/failure behavior and serialized form");
                }
                if (Set.of("query", "optQuery", "queryFrom", "toURIFragment").contains(name)) {
                    return SearchResult.found(visited, "JSON Pointer behavior changed; verify escaped tokens, URI fragments, empty keys, arrays, invalid indices and query versus optQuery failure semantics");
                }
                if ((owner.equals(PREFIX + "XML") || owner.equals(PREFIX + "JSONML") || owner.equals(PREFIX + "XMLTokener")) &&
                    Set.of("toJSONObject", "toJSONArray", "toString", "nextMeta", "nextToken", "nextContent").contains(name)) {
                    return SearchResult.found(visited, "XML conversion is a data/security boundary: value coercion, xsi:nil/type, force-list, whitespace, CDATA names, empty tags and maximum depth changed; replay hostile and representative XML");
                }
                if ((owner.equals(PREFIX + "JSONObject") || owner.equals(PREFIX + "JSONArray")) && STRUCTURAL_WRITES.contains(name)) {
                    return SearchResult.found(visited, "JSON graph mutation/wrapping changed for non-finite numbers, nulls, complex Map keys, recursive cycles and maximum nesting depth; verify failures and resulting graph shape");
                }
                if ((owner.equals(PREFIX + "JSONObject") || owner.equals(PREFIX + "JSONArray")) && "similar".equals(name)) {
                    return SearchResult.found(visited, "similar() numeric and recursive comparison bugs were fixed; regression-test cross-Number equality, nested arrays/objects, null and cyclic inputs");
                }
                if (SERIALIZATION.contains(name) && (owner.equals(PREFIX + "JSONObject") || owner.equals(PREFIX + "JSONArray") || owner.equals(PREFIX + "JSONWriter"))) {
                    return SearchResult.found(visited, "Serialization details changed for numbers, JSONString implementations, escaping, indentation and invalid values; snapshot exact bytes when signatures, caches or protocols depend on them");
                }
                if (owner.equals(PREFIX + "JSONTokener") && Set.of("nextValue", "nextString", "nextTo", "skipTo", "syntaxError").contains(name)) {
                    return SearchResult.found(visited, "Low-level tokener behavior and error positions changed; verify permissive tokens, EOF/backtracking, escapes and exception messages before depending on parser internals");
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                if (implementsType(visited, PREFIX + "JSONString")) {
                    return SearchResult.found(visited, "Custom JSONString serialization detected; verify null/invalid output handling, escaping and fallback behavior in 20250107");
                }
                if (extendsType(visited, PREFIX + "JSONObject") || extendsType(visited, PREFIX + "JSONArray") ||
                    extendsType(visited, PREFIX + "JSONTokener")) {
                    return SearchResult.found(visited, "Subclass of a concrete JSON-java implementation detected; audit overridden/protected behavior, constructors and parser state against 20250107");
                }
                return visited;
            }
        };
    }

    private static boolean stringOrTokener(JavaType type) {
        return TypeUtils.isOfClassType(type, "java.lang.String") || TypeUtils.isAssignableTo(PREFIX + "JSONTokener", type);
    }

    private static boolean mapCollectionOrObject(JavaType type) {
        return TypeUtils.isAssignableTo("java.util.Map", type) || TypeUtils.isAssignableTo("java.util.Collection", type) ||
               TypeUtils.isOfClassType(type, "java.lang.Object");
    }

    private static boolean implementsType(J.ClassDeclaration declaration, String fqn) {
        return declaration.getImplements() != null && declaration.getImplements().stream()
                .anyMatch(type -> TypeUtils.isAssignableTo(fqn, type.getType()));
    }

    private static boolean extendsType(J.ClassDeclaration declaration, String fqn) {
        return declaration.getExtends() != null && TypeUtils.isAssignableTo(fqn, declaration.getExtends().getType());
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }
}
