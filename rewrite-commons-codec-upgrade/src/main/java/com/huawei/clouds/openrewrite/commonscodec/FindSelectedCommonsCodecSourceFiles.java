package com.huawei.clouds.openrewrite.commonscodec;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** File precondition backed by the pre-upgrade project marker. */
public final class FindSelectedCommonsCodecSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected Apache Commons Codec projects";
    }

    @Override
    public String getDescription() {
        return "Select non-generated files only when their nearest build root proved one exact, " +
               "non-conflicting workbook Apache Commons Codec source version before dependency edits.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedCommonsCodecDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                return source.getMarkers().findFirst(CommonsCodecProjectMarker.class).isPresent()
                        ? SearchResult.found(source) : tree;
            }
        };
    }
}
