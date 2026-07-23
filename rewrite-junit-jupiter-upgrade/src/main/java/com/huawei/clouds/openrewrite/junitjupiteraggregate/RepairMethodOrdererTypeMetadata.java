package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * Repairs the nested-type metadata left stale by the pinned official migration.
 *
 * <p>This visitor changes no source text or imports and can be removed when the
 * upstream {@code MigrateMethodOrdererAlphanumeric} updates the renamed
 * identifier's type attribution.</p>
 */
public final class RepairMethodOrdererTypeMetadata extends Recipe {
    private static final String ALPHANUMERIC =
            "org.junit.jupiter.api.MethodOrderer$Alphanumeric";
    private static final String METHOD_NAME =
            "org.junit.jupiter.api.MethodOrderer$MethodName";

    @Override
    public String getDisplayName() {
        return "Repair JUnit MethodOrderer type metadata";
    }

    @Override
    public String getDescription() {
        return "Update in-memory nested-type attribution after the official MethodOrderer.Alphanumeric " +
               "syntax migration without changing source text.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (!"MethodName".equals(visited.getSimpleName()) ||
                    !TypeUtils.isOfClassType(visited.getType(), ALPHANUMERIC)) {
                    return visited;
                }
                return visited.withType(JavaType.ShallowClass.build(METHOD_NAME));
            }
        };
    }
}
