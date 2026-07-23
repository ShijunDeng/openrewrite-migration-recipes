package com.huawei.clouds.openrewrite.commonscodec;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

/** Excludes generated/cache trees before invoking Java migration leaves. */
public final class FindAuthoredCommonsCodecJavaFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored Apache Commons Codec Java sources";
    }

    @Override
    public String getDescription() {
        return "Select only Java source files outside generated, build, cache, vendor, and report directories.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    !(tree instanceof JavaSourceFile) ||
                    UpgradeSelectedCommonsCodecDependency.generated(source.getSourcePath())) {
                    return tree;
                }
                return SearchResult.found(source);
            }
        };
    }
}
