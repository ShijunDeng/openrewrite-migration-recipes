package com.huawei.clouds.openrewrite.junitjupiter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/**
 * Repairs the nested-type metadata left stale by the official syntax migration.
 *
 * <p>This visitor deliberately changes no source text or imports. It can be
 * removed once the pinned upstream {@code MigrateMethodOrdererAlphanumeric}
 * updates the renamed identifier's type attribution.</p>
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
        return "Update the in-memory nested-type attribution after the official " +
               "MethodOrderer.Alphanumeric syntax migration without changing source text.";
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
                JavaType.FullyQualified target = JavaType.ShallowClass.build(METHOD_NAME);
                return visited.withType(target);
            }
        };
    }
}
