package com.huawei.clouds.openrewrite.curatorframework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Runs behavior-preserving Curator listener migrations only in maintained project sources. */
public final class MigrateCuratorDeterministicJava extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Curator listener APIs";
    }

    @Override
    public String getDescription() {
        return "Replace removed ListenerContainer construction and types with StandardListenerManager while leaving " +
               "generated and installed source trees untouched.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectSourcesOnly(new ReplaceListenerContainerConstruction()),
                projectSourcesOnly(new ChangeType(
                        "org.apache.curator.framework.listen.ListenerContainer",
                        "org.apache.curator.framework.listen.StandardListenerManager", null))
        );
    }

    private static Recipe projectSourcesOnly(Recipe delegate) {
        return new Recipe() {
            @Override
            public String getDisplayName() {
                return delegate.getDisplayName();
            }

            @Override
            public String getDescription() {
                return delegate.getDescription();
            }

            @Override
            public TreeVisitor<?, ExecutionContext> getVisitor() {
                return Preconditions.check(new TreeVisitor<Tree, ExecutionContext>() {
                    @Override
                    public Tree visit(Tree tree, ExecutionContext ctx) {
                        return tree instanceof SourceFile source &&
                               !UpgradeSelectedCuratorFrameworkDependency.generated(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
