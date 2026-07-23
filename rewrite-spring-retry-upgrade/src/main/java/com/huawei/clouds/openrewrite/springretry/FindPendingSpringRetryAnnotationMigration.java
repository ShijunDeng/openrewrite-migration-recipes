package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/** Source-file precondition that allows the guarded annotation migration once per run. */
public final class FindPendingSpringRetryAnnotationMigration extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find pending Spring Retry annotation migration";
    }

    @Override
    public String getDescription() {
        return "Match a Java source file until its guarded official annotation migration has completed.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return cu.getMarkers()
                        .findFirst(SpringRetryAnnotationConflictHandledMarker.class)
                        .isPresent() ? cu : SearchResult.found(cu);
            }
        };
    }
}
