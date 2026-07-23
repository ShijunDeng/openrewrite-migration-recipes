package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.tree.JavaSourceFile;

/** Deterministic package moves for Platform types retained by JUnit 6. */
public final class MigrateJUnit6PlatformTypes extends Recipe {
    static final String OLD_PRECONDITION = "org.junit.platform.commons.util.PreconditionViolationException";
    static final String NEW_PRECONDITION = "org.junit.platform.commons.PreconditionViolationException";
    static final String OLD_BLACKLISTED = "org.junit.platform.commons.util.BlacklistedExceptions";
    static final String NEW_UNRECOVERABLE = "org.junit.platform.commons.util.UnrecoverableExceptions";

    @Override
    public String getDisplayName() {
        return "Migrate retained JUnit Platform types to their maintained packages";
    }

    @Override
    public String getDescription() {
        return "Move the removed util.PreconditionViolationException compatibility type to its maintained " +
               "org.junit.platform.commons package and move BlacklistedExceptions to UnrecoverableExceptions " +
               "before renaming its maintained rethrow method.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> changePrecondition =
                new ChangeType(OLD_PRECONDITION, NEW_PRECONDITION, true).getVisitor();
        TreeVisitor<?, ExecutionContext> changeBlacklisted =
                new ChangeType(OLD_BLACKLISTED, NEW_UNRECOVERABLE, true).getVisitor();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !(tree instanceof JavaSourceFile) ||
                    UpgradeSelectedJUnitJupiterDependency.generated(source.getSourcePath())) return tree;
                Tree migrated = changePrecondition.visit(tree, ctx);
                if (migrated == null) migrated = tree;
                Tree migratedAgain = changeBlacklisted.visit(migrated, ctx);
                return migratedAgain == null ? migrated : migratedAgain;
            }
        };
    }
}
