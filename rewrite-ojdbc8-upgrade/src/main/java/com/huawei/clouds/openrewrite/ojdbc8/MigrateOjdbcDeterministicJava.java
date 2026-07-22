package com.huawei.clouds.openrewrite.ojdbc8;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Run behavior-preserving Oracle JDBC rewrites in maintained sources only. */
public final class MigrateOjdbcDeterministicJava extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Oracle JDBC APIs";
    }

    @Override
    public String getDescription() {
        return "Move the deprecated driver implementation type to oracle.jdbc.OracleDriver, update exact class " +
               "strings, and rename only Oracle-documented equivalent methods.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectSourcesOnly(new ChangeType(
                        "oracle.jdbc.driver.OracleDriver", "oracle.jdbc.OracleDriver", null)),
                new ReplaceLegacyOracleDriverString(),
                new ReplaceEquivalentOracleJdbcMethods()
        );
    }

    private static Recipe projectSourcesOnly(Recipe delegate) {
        return new Recipe() {
            @Override public String getDisplayName() { return delegate.getDisplayName(); }
            @Override public String getDescription() { return delegate.getDescription(); }

            @Override
            public TreeVisitor<?, ExecutionContext> getVisitor() {
                return Preconditions.check(new TreeVisitor<Tree, ExecutionContext>() {
                    @Override
                    public Tree visit(Tree tree, ExecutionContext ctx) {
                        return tree instanceof SourceFile source &&
                               UpgradeSelectedOjdbc8Dependency.isProjectPath(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
