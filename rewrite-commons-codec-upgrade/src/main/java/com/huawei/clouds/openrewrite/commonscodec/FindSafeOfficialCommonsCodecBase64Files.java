package com.huawei.clouds.openrewrite.commonscodec;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * Limits the official Apache Base64 recipe to source files where every Codec
 * Base64 use is a statically qualified, semantically equivalent encode call.
 */
public final class FindSafeOfficialCommonsCodecBase64Files extends Recipe {
    private static final String BASE64 = "org.apache.commons.codec.binary.Base64";
    private static final MethodMatcher ENCODE_STRING =
            new MethodMatcher(BASE64 + " encodeBase64String(byte[])");
    private static final MethodMatcher ENCODE_BYTES =
            new MethodMatcher(BASE64 + " encodeBase64(byte[])");
    private static final MethodMatcher ENCODE_URL_BYTES =
            new MethodMatcher(BASE64 + " encodeBase64URLSafe(byte[])");
    private static final MethodMatcher ENCODE_URL_STRING =
            new MethodMatcher(BASE64 + " encodeBase64URLSafeString(byte[])");

    @Override
    public String getDisplayName() {
        return "Find semantically safe Apache Commons Codec Base64 encoding files";
    }

    @Override
    public String getDescription() {
        return "Select Java files containing only official-recipe-supported Base64 encoding calls with " +
               "statically proven non-null, bounded inputs; decoder, streaming, instance, static-import, " +
               "nullable, chunked, MIME, and mixed-use files remain untouched.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(
                    J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (UpgradeSelectedCommonsCodecDependency.generated(
                        compilationUnit.getSourcePath())) {
                    return compilationUnit;
                }
                Eligibility eligibility = new Eligibility();
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Import visitImport(J.Import anImport, ExecutionContext ec) {
                        J.Import visited = super.visitImport(anImport, ec);
                        if (isStaticBase64Import(visited, getCursor())) {
                            eligibility.unsafe = true;
                        }
                        return visited;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(
                            J.MethodInvocation invocation, ExecutionContext ec) {
                        J.MethodInvocation visited =
                                super.visitMethodInvocation(invocation, ec);
                        JavaType.Method method = visited.getMethodType();
                        if (method == null || !BASE64.equals(owner(method.getDeclaringType()))) {
                            return visited;
                        }
                        if (safeInvocation(visited)) {
                            eligibility.safe = true;
                        } else {
                            eligibility.unsafe = true;
                        }
                        return visited;
                    }

                    @Override
                    public J.Identifier visitIdentifier(
                            J.Identifier identifier, ExecutionContext ec) {
                        J.Identifier visited = super.visitIdentifier(identifier, ec);
                        if (inImport(getCursor())) {
                            return visited;
                        }
                        JavaType.Variable fieldType = visited.getFieldType();
                        if (fieldType != null &&
                            BASE64.equals(owner(fieldType.getOwner()))) {
                            eligibility.unsafe = true;
                            return visited;
                        }
                        if (!BASE64.equals(owner(visited.getType()))) {
                            return visited;
                        }
                        Cursor parent = getCursor().getParentTreeCursor();
                        if (parent.getValue() instanceof J.MethodInvocation invocation &&
                            invocation.getSelect() == identifier &&
                            safeInvocation(invocation)) {
                            return visited;
                        }
                        eligibility.unsafe = true;
                        return visited;
                    }
                }.visitNonNull(compilationUnit, ctx);
                return eligibility.safe && !eligibility.unsafe
                        ? SearchResult.found(compilationUnit) : compilationUnit;
            }
        };
    }

    private static boolean isStaticBase64Import(J.Import anImport, Cursor cursor) {
        if (!anImport.isStatic()) {
            return false;
        }
        if (BASE64.equals(owner(anImport.getQualid().getTarget().getType()))) {
            return true;
        }
        String imported = anImport.getQualid().printTrimmed(cursor);
        return imported.startsWith(BASE64 + ".");
    }

    private static boolean safeInvocation(J.MethodInvocation invocation) {
        if (!(invocation.getSelect() instanceof J.Identifier select) ||
            select.getFieldType() != null ||
            !BASE64.equals(owner(select.getType())) ||
            invocation.getArguments().size() != 1 ||
            !provablyNonNullBoundedBytes(invocation.getArguments().get(0))) {
            return false;
        }
        return ENCODE_STRING.matches(invocation) ||
               ENCODE_BYTES.matches(invocation) ||
               ENCODE_URL_BYTES.matches(invocation) ||
               ENCODE_URL_STRING.matches(invocation);
    }

    private static boolean provablyNonNullBoundedBytes(Expression expression) {
        Expression unwrapped = expression;
        while (unwrapped instanceof J.Parentheses<?> parentheses &&
               parentheses.getTree() instanceof Expression nested) {
            unwrapped = nested;
        }
        if (unwrapped instanceof J.NewArray newArray) {
            return newArray.getInitializer() != null;
        }
        if (!(unwrapped instanceof J.MethodInvocation invocation) ||
            !"getBytes".equals(invocation.getSimpleName()) ||
            !(invocation.getSelect() instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String)) {
            return false;
        }
        JavaType.Method method = invocation.getMethodType();
        return method != null && "java.lang.String".equals(owner(method.getDeclaringType()));
    }

    private static boolean inImport(Cursor cursor) {
        for (Cursor current = cursor; current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof J.Import) return true;
            if (current.getValue() instanceof J.CompilationUnit) return false;
        }
        return false;
    }

    private static String owner(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static final class Eligibility {
        private boolean safe;
        private boolean unsafe;
    }
}
