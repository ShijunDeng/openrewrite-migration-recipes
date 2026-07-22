package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/** Applies deterministic Flyway Java API migrations. */
public final class MigrateFlywayJavaApi extends Recipe {
    private static final String FLYWAY = "org.flywaydb.core.Flyway";
    private static final String EVENT = "org.flywaydb.core.api.callback.Event";

    @Override
    public String getDisplayName() {
        return "Migrate deterministic Flyway Java APIs";
    }

    @Override
    public String getDescription() {
        return "Preserve legacy Flyway migrate counts through MigrateResult and replace the deprecated create-schema callback enum constant.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(projectSource(), new JavaVisitor<ExecutionContext>() {
            private final JavaTemplate migrationsExecuted = JavaTemplate.builder("#{any()}.migrationsExecuted").build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J visited = super.visitMethodInvocation(method, ctx);
                if (!(visited instanceof J.MethodInvocation m)) {
                    return visited;
                }
                if (!isLegacyMigrateCount(m) || getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                    return m;
                }
                JavaType.Method methodType = m.getMethodType();
                J.MethodInvocation typed = m.withMethodType(methodType.withReturnType(
                        JavaType.ShallowClass.build("org.flywaydb.core.api.output.MigrateResult")));
                return migrationsExecuted.apply(getCursor(), m.getCoordinates().replace(), typed);
            }

            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J visited = super.visitFieldAccess(fieldAccess, ctx);
                if (!(visited instanceof J.FieldAccess f)) {
                    return visited;
                }
                if ("CREATE_SCHEMA".equals(f.getSimpleName()) && TypeUtils.isOfClassType(f.getTarget().getType(), EVENT)) {
                    J.Identifier name = f.getName().withSimpleName("BEFORE_CREATE_SCHEMA");
                    if (name.getFieldType() != null) {
                        name = name.withFieldType(name.getFieldType().withName("BEFORE_CREATE_SCHEMA"));
                    }
                    return f.withName(name);
                }
                return f;
            }
        });
    }

    static TreeVisitor<Tree, ExecutionContext> projectSource() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                return tree instanceof SourceFile source && FlywayVersions.isProjectPath(source.getSourcePath())
                        ? SearchResult.found(tree) : tree;
            }
        };
    }

    private static boolean isLegacyMigrateCount(J.MethodInvocation method) {
        return "migrate".equals(method.getSimpleName()) &&
               (method.getArguments().isEmpty() || method.getArguments().stream().allMatch(J.Empty.class::isInstance)) &&
               method.getMethodType() != null && method.getMethodType().getReturnType() == JavaType.Primitive.Int &&
               TypeUtils.isAssignableTo(FLYWAY, method.getMethodType().getDeclaringType());
    }
}
