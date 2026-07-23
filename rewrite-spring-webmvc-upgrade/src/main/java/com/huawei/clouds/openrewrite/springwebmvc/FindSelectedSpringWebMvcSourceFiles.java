package com.huawei.clouds.openrewrite.springwebmvc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Project-level precondition tied to an exact pre-upgrade workbook source version. */
public final class FindSelectedSpringWebMvcSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected Spring Web MVC projects";
    }

    @Override
    public String getDescription() {
        return "Select only non-generated files whose nearest build root was scanned before " +
               "dependency edits and proved one exact, non-conflicting Spring Web MVC source version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedSpringWebMvcDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                return source.getMarkers().findFirst(SpringWebMvcProjectMarker.class).isPresent()
                        ? SearchResult.found(source) : tree;
            }
        };
    }
}
