package com.huawei.clouds.openrewrite.springsecuritycore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Project-level precondition tied to one exact workbook source version. */
public final class FindSelectedSpringSecurityCoreProjectFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected Spring Security Core projects";
    }

    @Override
    public String getDescription() {
        return "Select only non-generated files whose nearest build root proved one exact, non-conflicting " +
               "Spring Security Core workbook source version before dependency edits.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringSecurityCoreSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                return source.getMarkers().findFirst(SpringSecurityCoreProjectMarker.class).isPresent()
                        ? SearchResult.found(source) : tree;
            }
        };
    }
}
