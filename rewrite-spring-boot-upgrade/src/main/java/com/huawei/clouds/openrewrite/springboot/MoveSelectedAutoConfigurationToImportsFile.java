package com.huawei.clouds.openrewrite.springboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.spring.boot2.MoveAutoConfigurationToImportsFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Exact source-version project guard around the official auto-configuration
 * scanner. Eligibility is completed before the official scanner is replayed,
 * so generation remains single-cycle even when source ordering is arbitrary.
 */
public final class MoveSelectedAutoConfigurationToImportsFile
        extends ScanningRecipe<MoveSelectedAutoConfigurationToImportsFile.State> {
    static final class State {
        private final MarkSelectedSpringBootProjects.Projects projects =
                new MarkSelectedSpringBootProjects.Projects();
        private final List<SourceFile> authoredSources = new ArrayList<>();
        private final Object officialAccumulator;

        State(Object officialAccumulator) {
            this.officialAccumulator = officialAccumulator;
        }
    }

    private final ScanningRecipe<Object> official = officialRecipe();

    @Override
    public String getDisplayName() {
        return "Move selected Spring Boot auto-configuration registrations";
    }

    @Override
    public String getDescription() {
        return "Delegate scanning, generation, and edits to the official recipe only for authored " +
               "files whose nearest build root proves an exact, non-conflicting source version " +
               "that predates Spring Boot 3.0.";
    }

    @Override
    public State getInitialValue(ExecutionContext ctx) {
        return new State(official.getInitialValue(ctx));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(State state) {
        TreeVisitor<?, ExecutionContext> eligibility =
                new MarkSelectedSpringBootProjects().getScanner(state.projects);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                Tree visited = eligibility.visit(tree, ctx);
                if (visited instanceof SourceFile source &&
                    !SpringBootSupport.generated(source.getSourcePath())) {
                    state.authoredSources.add(source);
                }
                return visited;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(
            State state, ExecutionContext ctx) {
        TreeVisitor<?, ExecutionContext> scanner =
                official.getScanner(state.officialAccumulator);
        for (SourceFile source : state.authoredSources) {
            if (eligible(state, source)) {
                scanner.visit(source, ctx);
            }
        }
        return official.generate(state.officialAccumulator, ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(State state) {
        TreeVisitor<?, ExecutionContext> delegate =
                official.getVisitor(state.officialAccumulator);
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringBootSupport.generated(source.getSourcePath()) ||
                    !eligible(state, source)) {
                    return tree;
                }
                return delegate.visit(tree, ctx);
            }
        };
    }

    private static boolean eligible(State state, SourceFile source) {
        String sourceVersion =
                state.projects.nearestSourceVersion(source.getSourcePath());
        return sourceVersion != null &&
               SpringBootSupport.requiresMigrationTo(sourceVersion, "3.0");
    }

    Recipe officialDelegate() {
        return official;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ScanningRecipe<Object> officialRecipe() {
        return (ScanningRecipe) new MoveAutoConfigurationToImportsFile(false);
    }
}
