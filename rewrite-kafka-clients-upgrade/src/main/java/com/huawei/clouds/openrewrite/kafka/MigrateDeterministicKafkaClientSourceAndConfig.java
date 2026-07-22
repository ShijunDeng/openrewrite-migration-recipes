package com.huawei.clouds.openrewrite.kafka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.ChangePropertyKey;
import org.openrewrite.properties.DeleteProperty;

import java.util.List;

/** Runs deterministic Kafka migrations only against maintained project sources and configuration. */
public final class MigrateDeterministicKafkaClientSourceAndConfig extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Kafka client 4.1 source and configuration";
    }

    @Override
    public String getDescription() {
        return "Apply one-to-one removed API, package, exception, and JMX property replacements while " +
               "leaving generated and installed trees untouched.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of(
                projectSourcesOnly(new ChangeMethodName(
                        "org.apache.kafka.clients.admin.DescribeTopicsResult values()", "topicNameValues", null, null)),
                projectSourcesOnly(new ChangeMethodName(
                        "org.apache.kafka.clients.admin.DescribeTopicsResult all()", "allTopicNames", null, null)),
                projectSourcesOnly(new ChangeMethodName(
                        "org.apache.kafka.clients.admin.DeleteTopicsResult values()", "topicNameValues", null, null)),
                projectSourcesOnly(new ChangeMethodName(
                        "org.apache.kafka.clients.consumer.MockConsumer setException(org.apache.kafka.common.KafkaException)",
                        "setPollException", null, null)),
                projectSourcesOnly(new ChangeMethodName(
                        "org.apache.kafka.clients.admin.UpdateFeaturesOptions dryRun(boolean)",
                        "validateOnly", null, null)),
                projectSourcesOnly(new ChangeType(
                        "org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerLoginCallbackHandler",
                        "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler", null)),
                projectSourcesOnly(new ChangeType(
                        "org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerValidatorCallbackHandler",
                        "org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallbackHandler", null)),
                projectSourcesOnly(new ChangeType(
                        "org.apache.kafka.common.errors.NotLeaderForPartitionException",
                        "org.apache.kafka.common.errors.NotLeaderOrFollowerException", null)),
                projectSourcesOnly(new ChangePropertyKey(
                        "metrics.jmx.blacklist", "metrics.jmx.exclude", false, false)),
                projectSourcesOnly(new ChangePropertyKey(
                        "metrics.jmx.whitelist", "metrics.jmx.include", false, false)),
                projectSourcesOnly(new DeleteProperty("auto.include.jmx.reporter", false))
        );
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
                               UpgradeSelectedKafkaClientsDependency.isProjectPath(source.getSourcePath())
                                ? SearchResult.found(tree) : tree;
                    }
                }, delegate.getVisitor());
            }
        };
    }
}
