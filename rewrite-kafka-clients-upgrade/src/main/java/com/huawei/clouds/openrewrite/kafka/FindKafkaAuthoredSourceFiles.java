package com.huawei.clouds.openrewrite.kafka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/**
 * Limits official Core building-block recipes to maintained project inputs.
 */
public final class FindKafkaAuthoredSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored Kafka client migration sources";
    }

    @Override
    public String getDescription() {
        return "Find source files outside generated, build, IDE, and dependency directories before applying " +
               "official OpenRewrite Java and properties recipes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source &&
                    UpgradeSelectedKafkaClientsDependency.isProjectPath(source.getSourcePath())) {
                    return SearchResult.found(source);
                }
                return tree;
            }
        };
    }
}
