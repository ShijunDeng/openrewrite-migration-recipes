package com.huawei.clouds.openrewrite.kafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaParser;
import org.openrewrite.properties.ChangePropertyKey;
import org.openrewrite.properties.DeleteProperty;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class KafkaOfficialRecipeAuditTest implements RewriteTest {
    private static final String AUTOMATIC_RECIPE =
            "com.huawei.clouds.openrewrite.kafka.MigrateDeterministicKafkaClientSourceAndConfig";
    private static final String RECOMMENDED_RECIPE =
            "com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2";

    @Test
    void deterministicRuntimeTreeUsesOnlyFixedCoreBuildingBlocksWithExactOptions() {
        Recipe automatic = environment().activateRecipes(AUTOMATIC_RECIPE);
        assertEquals(List.of("com.huawei.clouds.openrewrite.kafka.FindKafkaAuthoredSourceFiles"),
                automatic.getDescriptor().getPreconditions().stream().map(descriptor -> descriptor.getName()).toList());
        List<Recipe> tree = flatten(automatic);

        Map<String, String> methodRenames = tree.stream()
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .peek(recipe -> {
                    assertNull(recipe.getMatchOverrides());
                    assertNull(recipe.getIgnoreDefinition());
                })
                .collect(Collectors.toMap(ChangeMethodName::getMethodPattern, ChangeMethodName::getNewMethodName));
        assertEquals(Map.of(
                "org.apache.kafka.clients.admin.DescribeTopicsResult values()", "topicNameValues",
                "org.apache.kafka.clients.admin.DescribeTopicsResult all()", "allTopicNames",
                "org.apache.kafka.clients.admin.DeleteTopicsResult values()", "topicNameValues",
                "org.apache.kafka.clients.consumer.MockConsumer setException(org.apache.kafka.common.KafkaException)",
                "setPollException",
                "org.apache.kafka.clients.admin.UpdateFeaturesOptions dryRun(boolean)", "validateOnly"
        ), methodRenames);

        Map<String, String> typeRenames = tree.stream()
                .filter(ChangeType.class::isInstance)
                .map(ChangeType.class::cast)
                .peek(recipe -> assertNull(recipe.getIgnoreDefinition()))
                .collect(Collectors.toMap(
                        ChangeType::getOldFullyQualifiedTypeName, ChangeType::getNewFullyQualifiedTypeName));
        assertEquals(Map.of(
                "org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerLoginCallbackHandler",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler",
                "org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerValidatorCallbackHandler",
                "org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallbackHandler",
                "org.apache.kafka.common.errors.NotLeaderForPartitionException",
                "org.apache.kafka.common.errors.NotLeaderOrFollowerException"
        ), typeRenames);

        Map<String, String> propertyRenames = tree.stream()
                .filter(ChangePropertyKey.class::isInstance)
                .map(ChangePropertyKey.class::cast)
                .peek(recipe -> {
                    assertEquals(Boolean.FALSE, recipe.getRelaxedBinding());
                    assertEquals(Boolean.FALSE, recipe.getRegex());
                })
                .collect(Collectors.toMap(
                        ChangePropertyKey::getOldPropertyKey, ChangePropertyKey::getNewPropertyKey));
        assertEquals(Map.of(
                "metrics.jmx.blacklist", "metrics.jmx.exclude",
                "metrics.jmx.whitelist", "metrics.jmx.include"
        ), propertyRenames);

        List<DeleteProperty> deletedProperties = tree.stream()
                .filter(DeleteProperty.class::isInstance)
                .map(DeleteProperty.class::cast)
                .toList();
        assertEquals(1, deletedProperties.size());
        assertEquals("auto.include.jmx.reporter", deletedProperties.get(0).getPropertyKey());
        assertEquals(Boolean.FALSE, deletedProperties.get(0).getRelaxedBinding());
    }

    @Test
    void recommendedRuntimeTreeKeepsStrictUpgradeAndExcludesBroadKafkaAggregates() {
        Environment environment = environment();
        List<String> tree = flatten(environment.activateRecipes(RECOMMENDED_RECIPE)).stream()
                .map(Recipe::getName).toList();

        assertEquals(1, occurrences(tree,
                "com.huawei.clouds.openrewrite.kafka.UpgradeSelectedKafkaClientsDependency"));
        assertEquals(5, occurrences(tree, "org.openrewrite.java.ChangeMethodName"));
        assertEquals(3, occurrences(tree, "org.openrewrite.java.ChangeType"));
        assertEquals(2, occurrences(tree, "org.openrewrite.properties.ChangePropertyKey"));
        assertEquals(1, occurrences(tree, "org.openrewrite.properties.DeleteProperty"));
        assertFalse(tree.contains("org.openrewrite.java.dependencies.UpgradeDependencyVersion"), tree.toString());
        assertFalse(tree.stream().anyMatch(name -> name.startsWith("io.moderne.kafka.")), tree.toString());
        assertFalse(environment.listRecipes().stream()
                .map(Recipe::getName)
                .anyMatch(name -> name.startsWith("io.moderne.kafka.")));
    }

    @Test
    void officialCoreRecipeMigratesMethodReferences() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTOMATIC_RECIPE))
                        .parser(parser()),
                java(
                        """
                        import java.util.Map;
                        import java.util.function.Function;
                        import org.apache.kafka.clients.admin.DescribeTopicsResult;

                        class TopicFunctions {
                            Function<DescribeTopicsResult, Map<String, Object>> values =
                                    DescribeTopicsResult::values;
                        }
                        """,
                        """
                        import java.util.Map;
                        import java.util.function.Function;
                        import org.apache.kafka.clients.admin.DescribeTopicsResult;

                        class TopicFunctions {
                            Function<DescribeTopicsResult, Map<String, Object>> values =
                                    DescribeTopicsResult::topicNameValues;
                        }
                        """));
    }

    @Test
    void migratesConfluentDemoSceneFixtureAndIsIdempotent() {
        // Reduced from confluentinc/demo-scene @ 3c848e88623a956b07f2dd6fc20021ca45d0256c:
        // scalable-payment-processing/.../KafkaTopicClientImpl.java.
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTOMATIC_RECIPE))
                        .parser(parser()).cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        package io.confluent.kpay.util;

                        import java.util.List;
                        import java.util.Map;
                        import org.apache.kafka.KafkaFuture;
                        import org.apache.kafka.clients.admin.Admin;
                        import org.apache.kafka.clients.admin.DeleteTopicsResult;

                        class KafkaTopicClientImpl {
                            private Admin adminClient;

                            void deleteTopics(final List<String> topicsToDelete) {
                                final DeleteTopicsResult deleteTopicsResult =
                                        adminClient.deleteTopics(topicsToDelete);
                                final Map<String, KafkaFuture<Void>> results =
                                        deleteTopicsResult.values();
                                results.forEach((topic, result) -> result.toString());
                            }
                        }
                        """,
                        """
                        package io.confluent.kpay.util;

                        import java.util.List;
                        import java.util.Map;
                        import org.apache.kafka.KafkaFuture;
                        import org.apache.kafka.clients.admin.Admin;
                        import org.apache.kafka.clients.admin.DeleteTopicsResult;

                        class KafkaTopicClientImpl {
                            private Admin adminClient;

                            void deleteTopics(final List<String> topicsToDelete) {
                                final DeleteTopicsResult deleteTopicsResult =
                                        adminClient.deleteTopics(topicsToDelete);
                                final Map<String, KafkaFuture<Void>> results =
                                        deleteTopicsResult.topicNameValues();
                                results.forEach((topic, result) -> result.toString());
                            }
                        }
                        """,
                        source -> source.path(
                                "scalable-payment-processing/src/main/java/io/confluent/kpay/util/" +
                                "KafkaTopicClientImpl.java")));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath(
                        "com.huawei.clouds.openrewrite.kafka",
                        "org.openrewrite.java",
                        "org.openrewrite.properties")
                .scanYamlResources()
                .build();
    }

    private static JavaParser.Builder parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package org.apache.kafka; public class KafkaFuture<T> {}",
                """
                package org.apache.kafka.clients.admin;
                public class DescribeTopicsResult {
                    public java.util.Map<String,Object> values() { return null; }
                    public java.util.Map<String,Object> topicNameValues() { return null; }
                }
                """,
                """
                package org.apache.kafka.clients.admin;
                public class DeleteTopicsResult {
                    public java.util.Map<String,org.apache.kafka.KafkaFuture<Void>> values() { return null; }
                    public java.util.Map<String,org.apache.kafka.KafkaFuture<Void>> topicNameValues() { return null; }
                }
                """,
                """
                package org.apache.kafka.clients.admin;
                public interface Admin {
                    DeleteTopicsResult deleteTopics(java.util.Collection<String> topics);
                }
                """);
    }

    private static List<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = recipe;
        while (unwrapped instanceof Recipe.DelegatingRecipe delegating) {
            unwrapped = delegating.getDelegate();
        }
        List<Recipe> recipes = new ArrayList<>();
        recipes.add(unwrapped);
        for (Recipe child : unwrapped.getRecipeList()) recipes.addAll(flatten(child));
        return recipes;
    }

    private static long occurrences(List<String> values, String expected) {
        return values.stream().filter(expected::equals).count();
    }
}
