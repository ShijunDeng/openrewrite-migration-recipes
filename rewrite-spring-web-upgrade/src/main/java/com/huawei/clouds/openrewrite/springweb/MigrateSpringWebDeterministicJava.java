package com.huawei.clouds.openrewrite.springweb;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.tree.J;

import java.util.List;

/** Deterministic, type-attributed source migrations required by Spring Web 6.2. */
public final class MigrateSpringWebDeterministicJava extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Spring Web 6 Java APIs";
    }

    @Override
    public String getDescription() {
        return "Migrate Spring Web-facing Java EE packages to Jakarta EE, move exact replacement types, and " +
               "rename the removed ContentCachingResponseWrapper status alias using type attribution.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        List<Recipe> delegates = List.of(
                new ChangePackage("javax.servlet", "jakarta.servlet", true),
                new ChangePackage("javax.validation", "jakarta.validation", true),
                new ChangePackage("javax.xml.bind", "jakarta.xml.bind", true),
                new ChangePackage("javax.json", "jakarta.json", true),
                new ChangePackage("javax.activation", "jakarta.activation", true),
                new ChangeType(
                        "org.springframework.http.client.reactive.ReactorResourceFactory",
                        "org.springframework.http.client.ReactorResourceFactory",
                        false),
                new ChangeMethodName(
                        "org.springframework.web.util.ContentCachingResponseWrapper getStatusCode()",
                        "getStatus",
                        false,
                        false)
        );
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit) || !(tree instanceof SourceFile source) ||
                    SpringWebSupport.generated(source.getSourcePath())) return tree;
                Tree migrated = tree;
                for (Recipe delegate : delegates) migrated = delegate.getVisitor().visitNonNull(migrated, ctx);
                return migrated;
            }
        };
    }
}
