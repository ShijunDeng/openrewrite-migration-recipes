package com.huawei.clouds.openrewrite.log4jcore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Move deprecated/misspelled LoggerConfig filter builder methods to the 2.25 setter. */
public final class MigrateLoggerConfigFilterBuilders extends Recipe {
    private static final String FILTER = "org.apache.logging.log4j.core.Filter";
    private static final String LOGGER_BUILDER =
            "org.apache.logging.log4j.core.config.LoggerConfig.Builder";
    private static final String ROOT_LOGGER_BUILDER =
            "org.apache.logging.log4j.core.config.LoggerConfig.RootLogger.Builder";
    private static final List<Recipe> MIGRATIONS = List.of(
            new ChangeMethodName(
                    LOGGER_BUILDER + " withtFilter(" + FILTER + ")",
                    "setFilter",
                    false,
                    true),
            new ChangeMethodName(
                    LOGGER_BUILDER + " withFilter(" + FILTER + ")",
                    "setFilter",
                    false,
                    true),
            new ChangeMethodName(
                    ROOT_LOGGER_BUILDER + " withtFilter(" + FILTER + ")",
                    "setFilter",
                    false,
                    true)
    );

    static List<Recipe> officialCoreRecipes() {
        return MIGRATIONS;
    }

    @Override
    public String getDisplayName() {
        return "Migrate Log4j LoggerConfig filter builders";
    }

    @Override
    public String getDescription() {
        return "Rename the misspelled/deprecated LoggerConfig builder filter methods to setFilter while preserving the receiver and filter expression.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return MIGRATIONS.stream()
                .map(MigrateLoggerConfigFilterBuilders::projectSourcesOnly)
                .toList();
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
                               !Log4jCoreSupport.generated(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
