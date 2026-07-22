package com.huawei.clouds.openrewrite.guava;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Map;

/** Marks Guava compatibility choices whose correct replacement depends on application semantics. */
public final class FindGuavaMigrationRisks extends Recipe {
    private static final String GWT_RPC_PROPERTY = "guava.gwt.emergency_reenable_rpc";
    private static final Map<String, String> METHOD_RISKS = Map.ofEntries(
            risk("com.google.common.base.Predicates", "assignableFrom",
                    "Predicates.assignableFrom was removed; replace it with an application-reviewed Class::isAssignableFrom predicate"),
            risk("com.google.common.util.concurrent.Futures", "dereference",
                    "Futures.dereference was removed; review cancellation and exception propagation before replacing it with transformAsync"),
            risk("com.google.common.graph.Graphs", "equivalent",
                    "Graphs.equivalent was removed; choose graph equality semantics explicitly"),
            risk("com.google.common.io.Files", "fileTreeTraverser",
                    "Files.fileTreeTraverser was removed; choose MoreFiles.fileTraverser or Files.walk and review symlink/error handling"),
            risk("com.google.common.io.MoreFiles", "directoryTreeTraverser",
                    "MoreFiles.directoryTreeTraverser was removed; migrate to fileTraverser and review traversal error handling"),
            risk("com.google.common.io.Files", "createTempDir",
                    "Files.createTempDir is deprecated and changed security/error behavior; migrate with explicit IOException and permissions handling"),
            risk("com.google.common.hash.Hashing", "murmur3_32",
                    "murmur3_32 is deprecated; changing to murmur3_32_fixed can change persisted, partitioning, or interoperability hashes")
    );
    private static final Map<String, String> TYPE_RISKS = Map.of(
            "com.google.common.collect.BinaryTreeTraverser",
            "BinaryTreeTraverser was removed; migrate the traversal contract to Traverser after reviewing graph/tree semantics",
            "com.google.common.util.concurrent.CheckedFuture",
            "CheckedFuture was removed; define exception conversion explicitly at the application boundary"
    );

    @Override
    public String getDisplayName() {
        return "Find Guava 33 migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed or behavior-sensitive Guava APIs that cannot be changed safely from syntax alone.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (methodType == null) {
                    return m;
                }
                JavaType.FullyQualified declaringType = TypeUtils.asFullyQualified(methodType.getDeclaringType());
                if (declaringType == null) {
                    return m;
                }
                String message = METHOD_RISKS.get(key(declaringType.getFullyQualifiedName(), methodType.getName()));
                return message == null ? m : SearchResult.found(m, message);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = super.visitIdentifier(identifier, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(id.getType());
                String message = type == null || getCursor().firstEnclosing(J.Import.class) != null ||
                                 !id.getSimpleName().equals(type.getClassName())
                        ? null : TYPE_RISKS.get(type.getFullyQualifiedName());
                return message == null ? id : SearchResult.found(id, message);
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                return GWT_RPC_PROPERTY.equals(l.getValue())
                        ? SearchResult.found(l, "Guava GWT-RPC support was removed; this emergency property no longer restores it")
                        : l;
            }
        };
    }

    private static Map.Entry<String, String> risk(String owner, String method, String message) {
        return Map.entry(key(owner, method), message);
    }

    private static String key(String owner, String method) {
        return owner + "#" + method;
    }
}
