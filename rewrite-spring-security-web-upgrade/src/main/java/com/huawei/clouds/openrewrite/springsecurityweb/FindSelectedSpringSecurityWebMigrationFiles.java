package com.huawei.clouds.openrewrite.springsecurityweb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

/** Release-aware source/configuration precondition for official migration leaves. */
public final class FindSelectedSpringSecurityWebMigrationFiles extends Recipe {
    @Option(displayName = "Target release",
            description = "Spring Security release whose migration leaves are being selected.",
            example = "6.0")
    private final String targetRelease;

    @JsonCreator
    public FindSelectedSpringSecurityWebMigrationFiles(
            @JsonProperty("targetRelease") String targetRelease) {
        this.targetRelease = targetRelease;
    }

    public String getTargetRelease() {
        return targetRelease;
    }

    @Override
    public String getDisplayName() {
        return "Find selected Spring Security Web projects that predate a release";
    }

    @Override
    public String getDescription() {
        return "Select authored files only when their exact source version predates the " +
               "configured Spring Security release.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringSecurityWebUpgradeSupport.generated(source.getSourcePath())) return tree;
                boolean selected = source.getMarkers()
                        .findFirst(SpringSecurityWebProjectMarker.class)
                        .map(SpringSecurityWebProjectMarker::getSourceVersion)
                        .map(version -> SpringSecurityWebUpgradeSupport.requiresMigrationTo(
                                version, targetRelease))
                        .orElse(false);
                return selected ? SearchResult.found(source) : tree;
            }
        };
    }
}
