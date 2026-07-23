package com.huawei.clouds.openrewrite.junitjupiter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** File precondition backed by the pre-upgrade project marker. */
public final class FindSelectedJUnitJupiterSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected JUnit Jupiter projects";
    }

    @Override
    public String getDescription() {
        return "Select non-generated files only when their nearest build root proved one exact, " +
               "non-conflicting workbook JUnit Jupiter API source version before dependency edits.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedJUnitJupiterApiDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                return source.getMarkers().findFirst(JUnitJupiterProjectMarker.class).isPresent()
                        ? SearchResult.found(source) : tree;
            }
        };
    }
}
