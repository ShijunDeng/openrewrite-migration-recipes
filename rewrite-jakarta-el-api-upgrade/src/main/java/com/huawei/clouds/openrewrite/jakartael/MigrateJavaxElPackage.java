package com.huawei.clouds.openrewrite.jakartael;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.marker.SearchResult;

/** Migrates the Javax EL package only in maintained project Java sources. */
public final class MigrateJavaxElPackage extends Recipe {
    private final Recipe delegate = new ChangePackage("javax.el", "jakarta.el", true);

    @Override
    public String getDisplayName() {
        return "Migrate the Javax EL package";
    }

    @Override
    public String getDescription() {
        return "Recursively changes javax.el types to jakarta.el while excluding generated, cache, and installation trees.";
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
}
