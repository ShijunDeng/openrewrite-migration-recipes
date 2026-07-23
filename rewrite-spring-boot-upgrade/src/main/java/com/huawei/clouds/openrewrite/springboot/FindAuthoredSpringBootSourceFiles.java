package com.huawei.clouds.openrewrite.springboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Per-source precondition that keeps all AUTO transformations out of generated/install/cache trees. */
public final class FindAuthoredSpringBootSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored Spring Boot source files";
    }

    @Override
    public String getDescription() {
        return "Select non-generated project files so official source and configuration recipes cannot rewrite generated output.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source && !SpringBootSupport.generated(source.getSourcePath())) {
                    return SearchResult.found(source);
                }
                return tree;
            }
        };
    }
}
