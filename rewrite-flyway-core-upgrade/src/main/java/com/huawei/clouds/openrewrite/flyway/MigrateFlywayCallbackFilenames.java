package com.huawei.clouds.openrewrite.flyway;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;

import java.nio.file.Path;

/** Renames the deprecated createSchema callback event file to beforeCreateSchema. */
public final class MigrateFlywayCallbackFilenames extends Recipe {
    @Override
    public String getDisplayName() {
        return "Rename Flyway createSchema callback files";
    }

    @Override
    public String getDescription() {
        return "Rename createSchema callback scripts to the equivalent beforeCreateSchema event name.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                Path path = source.getSourcePath();
                if (!FlywayVersions.isProjectPath(path)) return tree;
                String normalizedPath = path.toString().replace('\\', '/');
                if (!(normalizedPath.contains("/db/callback/") || normalizedPath.startsWith("db/callback/") ||
                      normalizedPath.contains("/flyway/callback/") || normalizedPath.startsWith("flyway/callback/"))) {
                    return tree;
                }
                String name = path.getFileName().toString();
                if (!(name.equals("createSchema.sql") || name.startsWith("createSchema__"))) {
                    return tree;
                }
                return source.withSourcePath(path.resolveSibling("beforeCreateSchema" + name.substring("createSchema".length())));
            }
        };
    }
}
