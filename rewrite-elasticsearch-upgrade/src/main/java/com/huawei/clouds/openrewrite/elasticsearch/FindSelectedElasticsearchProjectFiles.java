package com.huawei.clouds.openrewrite.elasticsearch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Project-level precondition tied to the exact pre-upgrade source coordinate. */
public final class FindSelectedElasticsearchProjectFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected Testcontainers Elasticsearch projects";
    }

    @Override
    public String getDescription() {
        return "Select only non-generated files whose nearest build root was scanned before dependency edits " +
               "and exclusively owned org.testcontainers:elasticsearch:1.17.6.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    ElasticsearchUpgradeSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                return source.getMarkers().findFirst(ElasticsearchProjectMarker.class).isPresent()
                        ? SearchResult.found(source) : tree;
            }
        };
    }
}
