package com.huawei.clouds.openrewrite.springboot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;

import java.util.Objects;

/**
 * Release-aware project precondition. A Boot release migration runs only when
 * the exact source version captured before dependency edits predates it.
 */
public final class FindSelectedSpringBootMigrationSourceFiles extends Recipe {
    @Option(displayName = "Target release",
            description = "Spring Boot release whose migration leaves are being selected, for example 3.0.",
            example = "3.0")
    private final String targetRelease;

    @JsonCreator
    public FindSelectedSpringBootMigrationSourceFiles(
            @JsonProperty("targetRelease") String targetRelease) {
        this.targetRelease = Objects.requireNonNull(
                targetRelease, "targetRelease is required");
    }

    public String getTargetRelease() {
        return targetRelease;
    }

    @Override
    public String getDisplayName() {
        return "Find selected Spring Boot projects that predate a release";
    }

    @Override
    public String getDescription() {
        return "Select authored files only when their nearest build root carried one exact source " +
               "version before upgrade and that version predates the configured release.";
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
                        .map(SpringBootProjectMarker::getSourceVersion)
                        .map(version -> SpringBootSupport.requiresMigrationTo(
                                version, targetRelease))
                        .orElse(false);
                return selected ? SearchResult.found(source) : tree;
            }
        };
    }
}
