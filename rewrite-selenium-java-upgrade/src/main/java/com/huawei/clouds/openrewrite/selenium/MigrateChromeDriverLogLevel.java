package com.huawei.clouds.openrewrite.selenium;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;

/** Migrates the moved Chromium log-level enum only in project-owned source files. */
public final class MigrateChromeDriverLogLevel extends Recipe {
    private final Recipe delegate = new ChangeType(
            "org.openqa.selenium.chrome.ChromeDriverLogLevel",
            "org.openqa.selenium.chromium.ChromiumDriverLogLevel",
            true);

    @Override
    public String getDisplayName() {
        return "Migrate Selenium ChromeDriverLogLevel";
    }

    @Override
    public String getDescription() {
        return "Changes the removed Chrome log-level enum to its Chromium replacement while excluding generated and installation trees.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                return tree instanceof SourceFile source &&
                       !UpgradeSelectedSeleniumDependency.generated(source.getSourcePath())
                        ? SearchResult.found(tree) : tree;
            }
        }, delegate.getVisitor());
    }
}
