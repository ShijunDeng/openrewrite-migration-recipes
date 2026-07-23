package com.huawei.clouds.openrewrite.guava;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** File precondition backed by the exact pre-upgrade project marker. */
public final class FindSelectedGuavaSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected Guava projects";
    }

    @Override
    public String getDescription() {
        return "Select non-generated files only when their nearest build root owns one exact, " +
               "non-conflicting workbook Guava source version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    !UpgradeSelectedGuavaDependency.isProjectPath(source.getSourcePath())) {
                    return tree;
                }
                return source.getMarkers().findFirst(GuavaProjectMarker.class).isPresent()
                        ? SearchResult.found(source) : tree;
            }
        };
    }
}
