package com.huawei.clouds.openrewrite.bson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Deterministic BSON 3.x/4.x source migrations that preserve documented behavior. */
public final class MigrateBson5Java extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic MongoDB BSON 5 Java APIs";
    }

    @Override
    public String getDescription() {
        return "Migrate removed ObjectId aliases and record annotation packages, then replace legacy JsonWriterSettings constructors with behavior-preserving builders.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectSourcesOnly(new ChangeMethodName(
                        "org.bson.types.ObjectId getTimeSecond()", "getTimestamp", null, null)),
                projectSourcesOnly(new ChangeMethodName(
                        "org.bson.types.ObjectId toStringMongod()", "toHexString", null, null)),
                new MigrateObjectIdGetTime(),
                projectSourcesOnly(new ChangeType(
                        "org.bson.codecs.record.annotations.BsonId",
                        "org.bson.codecs.pojo.annotations.BsonId", null)),
                projectSourcesOnly(new ChangeType(
                        "org.bson.codecs.record.annotations.BsonProperty",
                        "org.bson.codecs.pojo.annotations.BsonProperty", null)),
                projectSourcesOnly(new ChangeType(
                        "org.bson.codecs.record.annotations.BsonRepresentation",
                        "org.bson.codecs.pojo.annotations.BsonRepresentation", null)),
                new MigrateJsonWriterSettingsConstructors());
    }

    private static Recipe projectSourcesOnly(Recipe delegate) {
        return new Recipe() {
            @Override
            public String getDisplayName() {
                return delegate.getDisplayName();
            }

            @Override
            public String getDescription() {
                return delegate.getDescription();
            }

            @Override
            public TreeVisitor<?, ExecutionContext> getVisitor() {
                return Preconditions.check(new TreeVisitor<Tree, ExecutionContext>() {
                    @Override
                    public Tree visit(Tree tree, ExecutionContext ctx) {
                        return tree instanceof SourceFile source && !BsonSupport.generated(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
