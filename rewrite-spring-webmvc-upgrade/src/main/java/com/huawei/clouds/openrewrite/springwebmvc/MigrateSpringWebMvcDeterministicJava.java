package com.huawei.clouds.openrewrite.springwebmvc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.tree.J;

import java.util.List;

/** Deterministic source migrations required by Spring Web MVC 6.2. */
public final class MigrateSpringWebMvcDeterministicJava extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Spring Web MVC 6 Java APIs";
    }

    @Override
    public String getDescription() {
        return "Migrate type-attributed javax.servlet APIs to Jakarta Servlet and replace removed MVC adapter " +
               "superclasses only where inherited behavior is provably unused.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        List<Recipe> delegates = List.of(
                new ChangePackage("javax.servlet", "jakarta.servlet", true),
                new MigrateRemovedMvcAdapters()
        );
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit) || !(tree instanceof SourceFile source) ||
                    UpgradeSelectedSpringWebMvcDependency.generated(source.getSourcePath())) return tree;
                Tree migrated = tree;
                for (Recipe delegate : delegates) migrated = delegate.getVisitor().visitNonNull(migrated, ctx);
                return migrated;
            }
        };
    }
}
