package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/**
 * Scope official building blocks to user-owned source files.
 *
 * <p>OpenRewrite's generic Jakarta and dependency recipes intentionally do not
 * impose this repository's generated/cache path contract.</p>
 */
public final class IsTomcatNonGeneratedSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Check for a user-owned Tomcat migration source";
    }

    @Override
    public String getDescription() {
        return "Use as a precondition so official migration building blocks skip generated and cache trees.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source &&
                    !UpgradeSelectedTomcatCatalinaDependency.generated(source.getSourcePath())) {
                    return SearchResult.found(tree);
                }
                return tree;
            }
        };
    }
}
