package com.huawei.clouds.openrewrite.springweb;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.spring.framework.MigrateClientHttpResponseGetRawStatusCodeMethod;
import org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetRawStatusCodeMethod;
import org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetStatusCodeMethod;
import org.openrewrite.java.spring.framework.MigrateUtf8MediaTypes;
import org.openrewrite.java.tree.J;

import java.util.List;

/**
 * Apply narrowly selected, upstream rewrite-spring recipes while retaining this
 * module's generated-source exclusion.
 */
public final class ApplyOfficialSpringWebMigrations extends Recipe {
    @Override
    public String getDisplayName() {
        return "Apply official Spring Web source migrations";
    }

    @Override
    public String getDescription() {
        return "Reuse exact rewrite-spring recipes for removed media type and status-code APIs without " +
               "activating the broad Spring Framework upgrade aggregates.";
    }

    static List<Recipe> officialRecipes() {
        return List.of(
                new MigrateUtf8MediaTypes(),
                new MigrateResponseStatusExceptionGetRawStatusCodeMethod(),
                new MigrateResponseStatusExceptionGetStatusCodeMethod(),
                new MigrateClientHttpResponseGetRawStatusCodeMethod()
        );
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        List<Recipe> delegates = officialRecipes();
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit) || !(tree instanceof SourceFile source) ||
                    SpringWebSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                Tree migrated = tree;
                for (Recipe delegate : delegates) {
                    migrated = delegate.getVisitor().visitNonNull(migrated, ctx);
                }
                return migrated;
            }
        };
    }
}
