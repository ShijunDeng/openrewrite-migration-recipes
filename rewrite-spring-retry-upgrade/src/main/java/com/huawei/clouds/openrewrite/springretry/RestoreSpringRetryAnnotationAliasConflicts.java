package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/** Restores aliases shielded from the official annotation rename recipes. */
public final class RestoreSpringRetryAnnotationAliasConflicts extends Recipe {
    @Override
    public String getDisplayName() {
        return "Restore protected Spring Retry annotation aliases";
    }

    @Override
    public String getDescription() {
        return "Restore the original aliases on conflict-marked @Retryable and @CircuitBreaker annotations " +
               "after safe annotations have passed through the official rename leaves.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                if (!ProtectSpringRetryAnnotationAliasConflicts.supported(visited)) {
                    return visited;
                }
                SpringRetryAnnotationProtectionMarker protection = visited.getMarkers()
                        .findFirst(SpringRetryAnnotationProtectionMarker.class)
                        .orElse(null);
                if (protection == null) return visited;
                J.Annotation restored = visited.withArguments(ListUtils.map(visited.getArguments(), argument -> {
                    if (!(argument instanceof J.Assignment assignment) ||
                        !(assignment.getVariable() instanceof J.Identifier identifier)) {
                        return argument;
                    }
                    String original = protection.getProtectedAssignments().get(assignment.getId());
                    if (ProtectSpringRetryAnnotationAliasConflicts.IMPLICIT_VALUE.equals(original)) {
                        return assignment.getAssignment().withPrefix(identifier.getPrefix());
                    }
                    return original == null ? argument :
                            assignment.withVariable(identifier.withSimpleName(original));
                }));
                return restored.withMarkers(restored.getMarkers()
                        .removeByType(SpringRetryAnnotationProtectionMarker.class));
            }
        };
    }
}
