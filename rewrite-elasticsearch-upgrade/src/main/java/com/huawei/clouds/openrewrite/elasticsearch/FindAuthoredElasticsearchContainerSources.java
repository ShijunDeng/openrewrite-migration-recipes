package com.huawei.clouds.openrewrite.elasticsearch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/** Scope official source transformations to authored Java that mentions ElasticsearchContainer. */
public final class FindAuthoredElasticsearchContainerSources extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored ElasticsearchContainer Java sources";
    }

    @Override
    public String getDescription() {
        return "Select non-generated Java sources that refer to Testcontainers ElasticsearchContainer.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (ElasticsearchUpgradeSupport.generated(cu.getSourcePath())) return cu;
                J.CompilationUnit visited = super.visitCompilationUnit(cu, ctx);
                return visited.printAll().contains("ElasticsearchContainer")
                        ? SearchResult.found(visited) : visited;
            }
        };
    }
}
