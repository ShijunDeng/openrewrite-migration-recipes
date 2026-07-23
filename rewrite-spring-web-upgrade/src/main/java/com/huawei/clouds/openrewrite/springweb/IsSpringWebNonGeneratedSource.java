package com.huawei.clouds.openrewrite.springweb;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Scope official and core migrations to application-owned source files. */
public final class IsSpringWebNonGeneratedSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Check for an application-owned Spring Web source";
    }

    @Override
    public String getDescription() {
        return "Use as a precondition so exact Spring Web source migrations skip generated and cache trees.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source &&
                    !SpringWebSupport.generated(source.getSourcePath())) {
                    return SearchResult.found(tree);
                }
                return tree;
            }
        };
    }
}
