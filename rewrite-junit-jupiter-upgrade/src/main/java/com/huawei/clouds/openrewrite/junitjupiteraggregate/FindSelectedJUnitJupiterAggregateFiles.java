package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** File precondition backed by the pre-upgrade project marker. */
public final class FindSelectedJUnitJupiterAggregateFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected JUnit Jupiter aggregate projects";
    }

    @Override
    public String getDescription() {
        return "Select non-generated files only when their nearest build root proved one exact, " +
               "non-conflicting workbook JUnit Jupiter aggregate source version before dependency edits.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedJUnitJupiterDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                return source.getMarkers().findFirst(JUnitJupiterAggregateProjectMarker.class).isPresent()
                        ? SearchResult.found(source) : tree;
            }
        };
    }
}
