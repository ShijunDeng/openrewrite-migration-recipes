package com.huawei.clouds.openrewrite.junrar;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/** Scope official Java search leaves away from generated/cache/install/report trees. */
public final class FindAuthoredJavaSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored Java source";
    }

    @Override
    public String getDescription() {
        return "Match Java source outside generated, cache, install and report directories.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit visited = super.visitCompilationUnit(cu, ctx);
                return JunrarSupport.generated(visited.getSourcePath()) ?
                        visited : SearchResult.found(visited);
            }
        };
    }
}
