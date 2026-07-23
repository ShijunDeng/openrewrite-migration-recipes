package com.huawei.clouds.openrewrite.jsoup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;

/** Migrates the public jsoup safety policy type without touching generated sources. */
public final class MigrateWhitelistToSafelist extends Recipe {
    private final Recipe delegate = new ChangeType(
            "org.jsoup.safety.Whitelist",
            "org.jsoup.safety.Safelist",
            true);

    @Override
    public String getDisplayName() {
        return "Migrate jsoup Whitelist to Safelist";
    }

    @Override
    public String getDescription() {
        return "Changes the renamed public jsoup safety policy type in project-owned sources while excluding generated and installation trees.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                return tree instanceof SourceFile source && !JsoupSupport.generated(source.getSourcePath())
                        ? SearchResult.found(tree) : tree;
            }
        }, delegate.getVisitor());
    }
}
