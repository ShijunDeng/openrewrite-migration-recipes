package com.huawei.clouds.openrewrite.jettyhttp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Per-source precondition backed by the nearest build-root selection scan. */
public final class FindSelectedJettyHttpProjectFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected Jetty HTTP projects";
    }

    @Override
    public String getDescription() {
        return "Select non-generated files only when their nearest Maven or Gradle build root " +
               "unambiguously owned one exact workbook-approved Jetty HTTP source version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    JettyHttpSupport.generated(source.getSourcePath())) return tree;
                return source.getMarkers().findFirst(JettyHttpProjectMarker.class).isPresent()
                        ? SearchResult.found(source)
                        : tree;
            }
        };
    }
}
