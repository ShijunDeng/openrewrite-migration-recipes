package com.huawei.clouds.openrewrite.jakartawrs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.marker.SearchResult;

/** Migrates the Javax REST package only in project-owned Java source files. */
public final class MigrateJavaxWsRsPackage extends Recipe {
    private final Recipe delegate = new ChangePackage("javax.ws.rs", "jakarta.ws.rs", true);

    @Override
    public String getDisplayName() {
        return "Migrate the Javax REST package";
    }

    @Override
    public String getDescription() {
        return "Recursively changes javax.ws.rs types to jakarta.ws.rs while excluding generated and installation trees.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                return tree instanceof SourceFile source &&
                       !UpgradeSelectedJakartaWsRsApiDependency.generated(source.getSourcePath())
                        ? SearchResult.found(tree) : tree;
            }
        }, delegate.getVisitor());
    }
}
