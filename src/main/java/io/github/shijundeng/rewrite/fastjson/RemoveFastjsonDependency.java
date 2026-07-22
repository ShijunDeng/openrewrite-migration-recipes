package io.github.shijundeng.rewrite.fastjson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/**
 * Removes Fastjson in the same recipe cycle, but only when every Fastjson source
 * use found before migration is covered by this plugin.
 */
final class RemoveFastjsonDependency extends ScanningRecipe<RemoveFastjsonDependency.Accumulator> {
    private static final String FASTJSON_PREFIX = "com.alibaba.fastjson";
    private static final Set<String> MIGRATABLE_TYPES = Set.of(
            "com.alibaba.fastjson.JSON",
            "com.alibaba.fastjson.JSONObject",
            "com.alibaba.fastjson.JSONArray",
            "com.alibaba.fastjson.TypeReference",
            "com.alibaba.fastjson.annotation.JSONField"
    );

    @Override
    public String getDisplayName() {
        return "Remove Fastjson after a complete migration";
    }

    @Override
    public String getDescription() {
        return "Remove the Fastjson Maven or Gradle dependency only when all Fastjson source uses are supported by this migration.";
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (methodType != null && isFastjson(methodType.getDeclaringType()) &&
                    !MigrateFastjsonApi.isSupported(methodType, m.getArguments().size())) {
                    acc.unsupported = true;
                }
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getType());
                if (type != null && isFastjson(type)) {
                    boolean migratableContainer =
                            ("com.alibaba.fastjson.JSONObject".equals(type.getFullyQualifiedName()) ||
                             "com.alibaba.fastjson.JSONArray".equals(type.getFullyQualifiedName())) &&
                            (n.getArguments().isEmpty() || n.getArguments().size() == 1);
                    boolean migratableTypeReference =
                            "com.alibaba.fastjson.TypeReference".equals(type.getFullyQualifiedName());
                    if (!migratableContainer && !migratableTypeReference) {
                        acc.unsupported = true;
                    }
                }
                return n;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);
                if (isFastjson(f.getTarget().getType()) && !"class".equals(f.getSimpleName())) {
                    acc.unsupported = true;
                }
                return f;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(a.getType());
                if (type != null && "com.alibaba.fastjson.annotation.JSONField".equals(type.getFullyQualifiedName()) &&
                    !MigrateJsonFieldAnnotation.isSupported(a)) {
                    acc.unsupported = true;
                }
                return a;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(i.getType());
                if (type != null && isFastjson(type) && !MIGRATABLE_TYPES.contains(type.getFullyQualifiedName())) {
                    acc.unsupported = true;
                }
                return i;
            }

            private boolean isFastjson(JavaType type) {
                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
                return fullyQualified != null &&
                       (fullyQualified.getFullyQualifiedName().equals(FASTJSON_PREFIX) ||
                        fullyQualified.getFullyQualifiedName().startsWith(FASTJSON_PREFIX + "."));
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        if (acc.unsupported) {
            return TreeVisitor.noop();
        }
        return new TreeVisitor<Tree, ExecutionContext>() {
            final TreeVisitor<?, ExecutionContext> maven =
                    new org.openrewrite.maven.RemoveDependency("com.alibaba", "fastjson", null).getVisitor();
            final TreeVisitor<?, ExecutionContext> gradle =
                    new org.openrewrite.gradle.RemoveDependency("com.alibaba", "fastjson", null).getVisitor();

            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                Tree result = tree;
                if (maven.isAcceptable((SourceFile) result, ctx)) {
                    result = maven.visitNonNull(result, ctx);
                }
                if (result instanceof SourceFile && gradle.isAcceptable((SourceFile) result, ctx)) {
                    result = gradle.visitNonNull(result, ctx);
                }
                return result;
            }
        };
    }

    static final class Accumulator {
        boolean unsupported;
    }
}
