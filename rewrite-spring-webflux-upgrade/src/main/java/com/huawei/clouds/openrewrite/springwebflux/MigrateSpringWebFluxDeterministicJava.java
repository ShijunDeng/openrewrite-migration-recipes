package com.huawei.clouds.openrewrite.springwebflux;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.tree.J;

import java.util.List;

/**
 * Type-attributed, source-compatible API moves whose target implementations are
 * documented aliases of the old Spring WebFlux APIs.
 */
public final class MigrateSpringWebFluxDeterministicJava extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Spring WebFlux 6 Java APIs";
    }

    @Override
    public String getDescription() {
        return "Move ReactorResourceFactory to its 6.1 package and replace the removed " +
               "ServerWebExchangeContextFilter.get(Context) alias with getExchange(ContextView), using type attribution.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        List<Recipe> delegates = List.of(
                new ChangeType(
                        "org.springframework.http.client.reactive.ReactorResourceFactory",
                        "org.springframework.http.client.ReactorResourceFactory",
                        false),
                new ChangeMethodName(
                        "org.springframework.web.filter.reactive.ServerWebExchangeContextFilter " +
                        "get(reactor.util.context.Context)",
                        "getExchange",
                        false,
                        false)
        );
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit) ||
                    !(tree instanceof SourceFile source) ||
                    SpringWebFluxSupport.generated(source.getSourcePath())) return tree;
                Tree migrated = tree;
                for (Recipe delegate : delegates) {
                    migrated = delegate.getVisitor().visitNonNull(migrated, ctx);
                }
                return migrated;
            }
        };
    }
}
