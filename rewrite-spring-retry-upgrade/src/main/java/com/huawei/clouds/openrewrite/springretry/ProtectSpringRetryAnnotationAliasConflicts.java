package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.tree.Space.EMPTY;
import static org.openrewrite.java.tree.Space.SINGLE_SPACE;

/**
 * Protects one conflicting Spring Retry annotation while the official attribute
 * rename recipes continue to process safe annotations in the same source file.
 */
public final class ProtectSpringRetryAnnotationAliasConflicts extends Recipe {
    private static final String CONFLICT_FOUND =
            ProtectSpringRetryAnnotationAliasConflicts.class.getName() + ".conflictFound";
    static final String CONFLICT =
            "Spring Retry 注解同时声明互斥的旧别名或新旧属性；已阻断该注解的自动属性重命名，请人工合并";

    private static final String PROTECTED_PREFIX = "__openrewrite_spring_retry_protected_";
    private static final String PROTECTED_IMPLICIT_VALUE = PROTECTED_PREFIX + "implicit_value";
    static final String IMPLICIT_VALUE = "<implicit-value>";
    private static final Set<String> OLD_ALIASES = Set.of("value", "include", "exclude");
    private static final AnnotationMatcher RETRYABLE =
            new AnnotationMatcher("@org.springframework.retry.annotation.Retryable");
    private static final AnnotationMatcher CIRCUIT_BREAKER =
            new AnnotationMatcher("@org.springframework.retry.annotation.CircuitBreaker");

    @Override
    public String getDisplayName() {
        return "Protect conflicting Spring Retry annotation aliases";
    }

    @Override
    public String getDescription() {
        return "Mark an individual @Retryable or @CircuitBreaker whose old exception aliases cannot be " +
               "safely renamed, and shield only that annotation from the official rename leaves.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit visited = super.visitCompilationUnit(cu, ctx);
                return Boolean.TRUE.equals(getCursor().pollMessage(CONFLICT_FOUND))
                        ? visited.withMarkers(visited.getMarkers().setByType(
                                new SpringRetryAnnotationConflictHandledMarker(randomId())))
                        : visited;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                if (!supported(visited) || !conflicting(visited)) {
                    return visited;
                }
                getCursor().putMessageOnFirstEnclosing(
                        J.CompilationUnit.class, CONFLICT_FOUND, Boolean.TRUE);

                Map<UUID, String> protectedAssignments = new HashMap<>();
                J.Annotation protectedAnnotation = visited.withArguments(ListUtils.map(
                        visited.getArguments(), argument -> {
                    if (argument instanceof J.Assignment assignment &&
                        assignment.getVariable() instanceof J.Identifier identifier &&
                        OLD_ALIASES.contains(identifier.getSimpleName())) {
                        protectedAssignments.put(assignment.getId(), identifier.getSimpleName());
                        return assignment.withVariable(identifier.withSimpleName(
                                PROTECTED_PREFIX + identifier.getSimpleName()));
                    }
                    if (!(argument instanceof J.Assignment) && !(argument instanceof J.Empty)) {
                        UUID assignmentId = randomId();
                        protectedAssignments.put(assignmentId, IMPLICIT_VALUE);
                        J.Identifier name = new J.Identifier(
                                randomId(), argument.getPrefix(), Markers.EMPTY, emptyList(),
                                PROTECTED_IMPLICIT_VALUE, argument.getType(), null);
                        return new J.Assignment(
                                assignmentId, EMPTY, argument.getMarkers(), name,
                                new JLeftPadded<>(
                                        SINGLE_SPACE, argument.withPrefix(SINGLE_SPACE), Markers.EMPTY),
                                argument.getType());
                    }
                    return argument;
                }));
                J.Annotation marked = markConflict(protectedAnnotation);
                return marked.withMarkers(marked.getMarkers().setByType(
                        new SpringRetryAnnotationProtectionMarker(
                                randomId(), protectedAssignments)));
            }
        };
    }

    static boolean supported(J.Annotation annotation) {
        return RETRYABLE.matches(annotation) || CIRCUIT_BREAKER.matches(annotation);
    }

    private static J.Annotation markConflict(J.Annotation annotation) {
        return annotation.getMarkers().findAll(SearchResult.class).stream()
                .map(SearchResult::getDescription)
                .anyMatch(description -> description != null && description.contains(CONFLICT))
                ? annotation : SearchResult.found(annotation, CONFLICT);
    }

    private static boolean conflicting(J.Annotation annotation) {
        Set<String> attributes = new HashSet<>();
        if (annotation.getArguments() != null) {
            for (Expression argument : annotation.getArguments()) {
                if (argument instanceof J.Assignment assignment &&
                    assignment.getVariable() instanceof J.Identifier identifier) {
                    attributes.add(identifier.getSimpleName());
                } else if (!(argument instanceof J.Empty)) {
                    attributes.add("value");
                }
            }
        }
        boolean positiveConflict =
                attributes.contains("value") && attributes.contains("include") ||
                attributes.contains("retryFor") &&
                (attributes.contains("value") || attributes.contains("include"));
        boolean negativeConflict =
                attributes.contains("exclude") && attributes.contains("noRetryFor");
        return positiveConflict || negativeConflict;
    }
}
