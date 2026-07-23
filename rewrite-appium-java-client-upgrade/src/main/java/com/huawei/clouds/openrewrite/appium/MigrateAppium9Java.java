package com.huawei.clouds.openrewrite.appium;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Deterministic Java migrations backed by the fixed official v7-to-v8 and v8-to-v9 guides. */
public final class MigrateAppium9Java extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Appium Java Client 9 APIs";
    }

    @Override
    public String getDescription() {
        return "Migrate MobileBy locators, removed selected-driver find shortcuts, MobileElement types and setValue, " +
               "and obsolete driver generics in maintained Java sources.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectSourcesOnly(new ChangeMethodName(
                        "io.appium.java_client.MobileBy AndroidUIAutomator(java.lang.String)",
                        "androidUIAutomator", null, null)),
                projectSourcesOnly(new ChangeMethodName(
                        "io.appium.java_client.MobileBy AccessibilityId(java.lang.String)",
                        "accessibilityId", null, null)),
                projectSourcesOnly(new ChangeMethodName(
                        "io.appium.java_client.MobileBy AndroidViewTag(java.lang.String)",
                        "androidViewTag", null, null)),
                projectSourcesOnly(new ChangeType(
                        "io.appium.java_client.MobileBy", "io.appium.java_client.AppiumBy", null)),
                projectSourcesOnly(new MigrateAppiumDriverFindShortcuts()),
                projectSourcesOnly(new ChangeMethodName(
                        "io.appium.java_client.MobileElement setValue(java.lang.String)",
                        "sendKeys", null, null)),
                projectSourcesOnly(new ChangeType(
                        "io.appium.java_client.android.AndroidElement", "org.openqa.selenium.WebElement", null)),
                projectSourcesOnly(new ChangeType(
                        "io.appium.java_client.ios.IOSElement", "org.openqa.selenium.WebElement", null)),
                projectSourcesOnly(new ChangeType(
                        "io.appium.java_client.MobileElement", "org.openqa.selenium.WebElement", null)),
                projectSourcesOnly(new RemoveAppiumDriverTypeArguments())
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
                               !UpgradeSelectedAppiumDependency.generated(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
