package com.huawei.clouds.openrewrite.springboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Per-source precondition for AUTO that is tied to a scanned exact source-version owner. */
public final class FindSelectedSpringBootSourceFiles extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find authored files in workbook-selected Spring Boot projects";
    }

    @Override
    public String getDescription() {
        return "Select only non-generated files whose nearest build root was scanned before the " +
               "upgrade and proved to own a non-conflicting exact Spring Boot source version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringBootSupport.generated(source.getSourcePath())) return tree;
                boolean selected = source.getMarkers()
                        .findFirst(SpringBootProjectMarker.class)
                        .map(SpringBootProjectMarker::isSelectedSource)
                        .orElse(false);
                return selected ? SearchResult.found(source) : tree;
            }
        };
    }
}
