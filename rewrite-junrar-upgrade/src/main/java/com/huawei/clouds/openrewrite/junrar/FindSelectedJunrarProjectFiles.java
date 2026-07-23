package com.huawei.clouds.openrewrite.junrar;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Project-level precondition tied to an exact pre-upgrade workbook source version. */
public final class FindSelectedJunrarProjectFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find files in workbook-selected Junrar projects";
    }

    @Override
    public String getDescription() {
        return "Select only non-generated files whose nearest Maven or Gradle build root proved " +
               "one exact, non-conflicting Junrar 7.5.5 or 7.5.8 owner before dependency edits.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    JunrarSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                return JunrarSupport.selected(source) ? SearchResult.found(source) : tree;
            }
        };
    }
}
