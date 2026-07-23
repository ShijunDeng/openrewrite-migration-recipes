package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/**
 * Limits official building-block recipes to authored inputs when generated files are explicitly supplied.
 */
public final class FindAuthoredSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored Bouncy Castle provider migration sources";
    }

    @Override
    public String getDescription() {
        return "Find source files outside generated, build, cache, installation, and report directories before " +
               "applying official OpenRewrite Java recipes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source &&
                    !UpgradeSelectedBcProvDependency.generated(source.getSourcePath())) {
                    return SearchResult.found(source);
                }
                return tree;
            }
        };
    }
}
