package com.huawei.clouds.openrewrite.springsecurityweb;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Per-source precondition backed by exact nearest-build-root ownership. */
public final class FindSelectedSpringSecurityWebProjectFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected Spring Security Web projects";
    }

    @Override
    public String getDescription() {
        return "Select non-generated files only when their nearest Maven or Gradle build root " +
               "unambiguously owned one exact workbook-approved source version before upgrade.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringSecurityWebUpgradeSupport.generated(source.getSourcePath())) return tree;
                return source.getMarkers().findFirst(SpringSecurityWebProjectMarker.class).isPresent()
                        ? SearchResult.found(source)
                        : tree;
            }
        };
    }
}
