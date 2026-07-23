package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** File-level build precondition inside an exactly selected project root. */
public final class FindSelectedSpringRetryBuildFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find selected Spring Retry build files";
    }

    @Override
    public String getDescription() {
        return "Select only pom.xml, build.gradle and build.gradle.kts files carrying the exact pre-upgrade " +
               "Spring Retry project marker.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringRetrySupport.generated(source.getSourcePath()) ||
                    source.getMarkers().findFirst(SpringRetryProjectMarker.class).isEmpty()) {
                    return tree;
                }
                String file = source.getSourcePath().getFileName().toString();
                return "pom.xml".equals(file) || "build.gradle".equals(file) ||
                       "build.gradle.kts".equals(file)
                        ? SearchResult.found(source) : tree;
            }
        };
    }
}
