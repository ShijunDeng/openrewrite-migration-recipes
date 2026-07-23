package com.huawei.clouds.openrewrite.commonscodec;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Map;
import java.util.Set;

/** Apply only replacements whose old implementation delegates directly to the documented replacement. */
public final class MigrateDeprecatedCommonsCodecApis extends Recipe {
    private static final Map<String, String> DIGEST_METHODS = Map.of(
            "getShaDigest", "getSha1Digest",
            "sha", "sha1",
            "shaHex", "sha1Hex");
    private static final Set<String> CHARSET_FIELDS = Set.of(
            "ISO_8859_1", "US_ASCII", "UTF_16", "UTF_16BE", "UTF_16LE", "UTF_8");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Apache Commons Codec APIs";
    }

    @Override
    public String getDescription() {
        return "Rename DigestUtils SHA-1 delegates and Base64.isArrayByteBase64(), and replace qualified " +
               "Commons Codec charset constants with the Java 8 StandardCharsets equivalents.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedCommonsCodecDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null) return visited;
                String owner = owner(method.getDeclaringType());
                String replacement = null;
                if ("org.apache.commons.codec.digest.DigestUtils".equals(owner)) {
                    replacement = DIGEST_METHODS.get(method.getName());
                } else if ("org.apache.commons.codec.binary.Base64".equals(owner) &&
                           "isArrayByteBase64".equals(method.getName())) {
                    replacement = "isBase64";
                }
                if (replacement == null) return visited;
                if (visited.getSelect() == null) {
                    maybeRemoveImport(owner + "." + method.getName());
                    maybeAddImport(owner, replacement);
                }
                JavaType.Method replacementType = method.withName(replacement);
                return visited.withName(visited.getName().withSimpleName(replacement).withType(replacementType))
                        .withMethodType(replacementType);
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                if (!CHARSET_FIELDS.contains(visited.getSimpleName()) ||
                    !"org.apache.commons.codec.Charsets".equals(owner(visited.getTarget().getType()))) return visited;
                maybeRemoveImport("org.apache.commons.codec.Charsets");
                maybeAddImport("java.nio.charset.StandardCharsets");
                return JavaTemplate.builder("StandardCharsets." + visited.getSimpleName())
                        .imports("java.nio.charset.StandardCharsets")
                        .build()
                        .apply(getCursor(), visited.getCoordinates().replace());
            }
        };
    }

    private static String owner(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }
}
