package com.huawei.clouds.openrewrite.zookeeper;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Limits official OpenRewrite building blocks to authored files supplied by callers. */
public final class FindAuthoredSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored ZooKeeper migration source files";
    }

    @Override
    public String getDescription() {
        return "Find source files outside generated, build, cache and installation directories before applying " +
               "official OpenRewrite ZooKeeper migration building blocks.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source &&
                    !ZooKeeperSupport.generated(source.getSourcePath())) return SearchResult.found(source);
                return tree;
            }
        };
    }
}
