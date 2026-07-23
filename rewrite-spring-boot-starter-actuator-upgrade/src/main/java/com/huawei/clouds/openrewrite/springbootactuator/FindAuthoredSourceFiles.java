package com.huawei.clouds.openrewrite.springbootactuator;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/**
 * Limits official migration recipes to authored inputs when callers manually supply generated
 * output to OpenRewrite. Normal Maven and Gradle source discovery already excludes these paths.
 */
public final class FindAuthoredSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored source files";
    }

    @Override
    public String getDescription() {
        return "Find non-generated source files before applying official Spring migration recipes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source &&
                    !SpringBootActuatorSupport.generated(source.getSourcePath())) {
                    return SearchResult.found(source);
                }
                return tree;
            }
        };
    }
}
