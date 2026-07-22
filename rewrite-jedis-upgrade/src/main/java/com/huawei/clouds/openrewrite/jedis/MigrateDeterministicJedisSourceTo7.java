package com.huawei.clouds.openrewrite.jedis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/** Applies deterministic Jedis source migrations only to maintained project sources. */
public final class MigrateDeterministicJedisSourceTo7 extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Jedis source constructs to version 7";
    }

    @Override
    public String getDescription() {
        return "Migrate one-to-one public Jedis type moves and literal host constructors while leaving generated " +
               "and installed source trees untouched.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                changeType("redis.clients.jedis.BinaryJedis", "redis.clients.jedis.Jedis"),
                changeType("redis.clients.jedis.BinaryJedisCluster", "redis.clients.jedis.JedisCluster"),
                changeType("redis.clients.jedis.BitOP", "redis.clients.jedis.args.BitOP"),
                changeType("redis.clients.jedis.GeoUnit", "redis.clients.jedis.args.GeoUnit"),
                changeType("redis.clients.jedis.ListPosition", "redis.clients.jedis.args.ListPosition"),
                changeType("redis.clients.jedis.BitPosParams", "redis.clients.jedis.params.BitPosParams"),
                changeType("redis.clients.jedis.ScanParams", "redis.clients.jedis.params.ScanParams"),
                changeType("redis.clients.jedis.SortingParams", "redis.clients.jedis.params.SortingParams"),
                changeType("redis.clients.jedis.ZParams", "redis.clients.jedis.params.ZParams"),
                changeType("redis.clients.jedis.AccessControlLogEntry", "redis.clients.jedis.resps.AccessControlLogEntry"),
                changeType("redis.clients.jedis.AccessControlUser", "redis.clients.jedis.resps.AccessControlUser"),
                changeType("redis.clients.jedis.GeoRadiusResponse", "redis.clients.jedis.resps.GeoRadiusResponse"),
                changeType("redis.clients.jedis.ScanResult", "redis.clients.jedis.resps.ScanResult"),
                changeType("redis.clients.jedis.Slowlog", "redis.clients.jedis.resps.Slowlog"),
                changeType("redis.clients.jedis.StreamConsumersInfo", "redis.clients.jedis.resps.StreamConsumersInfo"),
                changeType("redis.clients.jedis.StreamEntry", "redis.clients.jedis.resps.StreamEntry"),
                changeType("redis.clients.jedis.StreamGroupInfo", "redis.clients.jedis.resps.StreamGroupInfo"),
                changeType("redis.clients.jedis.StreamInfo", "redis.clients.jedis.resps.StreamInfo"),
                changeType("redis.clients.jedis.StreamPendingEntry", "redis.clients.jedis.resps.StreamPendingEntry"),
                changeType("redis.clients.jedis.StreamPendingSummary", "redis.clients.jedis.resps.StreamPendingSummary"),
                changeType("redis.clients.jedis.Tuple", "redis.clients.jedis.resps.Tuple"),
                changeType("redis.clients.jedis.PipelineBase", "redis.clients.jedis.AbstractPipeline"),
                changeType("redis.clients.jedis.TransactionBase", "redis.clients.jedis.AbstractTransaction"),
                projectSourcesOnly(new MigrateLiteralJedisHostConstructors())
        );
    }

    private static Recipe changeType(String before, String after) {
        return projectSourcesOnly(new ChangeType(before, after, null));
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
                        return tree instanceof SourceFile source &&
                               !UpgradeSelectedJedisDependency.generated(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
