package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Project-level precondition tied to the exact pre-upgrade 1.3.4 source version. */
public final class FindSelectedSpringRetryProjectFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected Spring Retry projects";
    }

    @Override
    public String getDescription() {
        return "Select only non-generated files whose nearest build root proved an exact, locally owned and " +
               "non-conflicting Spring Retry 1.3.4 declaration before any build edit.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringRetrySupport.generated(source.getSourcePath())) {
                    return tree;
                }
                return source.getMarkers().findFirst(SpringRetryProjectMarker.class).isPresent()
                        ? SearchResult.found(source) : tree;
            }
        };
    }
}
