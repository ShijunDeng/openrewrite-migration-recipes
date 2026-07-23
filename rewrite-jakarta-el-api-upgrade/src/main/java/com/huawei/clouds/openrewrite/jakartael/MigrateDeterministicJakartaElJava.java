package com.huawei.clouds.openrewrite.jakartael;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Runs deterministic Java API changes before the namespace itself is migrated. */
public final class MigrateDeterministicJakartaElJava extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Jakarta EL 6 Java APIs";
    }

    @Override
    public String getDescription() {
        return "Correct the removed MethodExpression isParmetersProvided spelling before migrating javax.el types to jakarta.el.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(projectSourcesOnly(new ChangeMethodName(
                "javax.el.MethodExpression isParmetersProvided()", "isParametersProvided", true, null)));
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
                               !UpgradeSelectedJakartaElApiDependency.generated(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
