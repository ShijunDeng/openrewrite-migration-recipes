package com.huawei.clouds.openrewrite.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;

class KafkaJavaMigrationRiskTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.kafka.MigrateDeterministicKafkaClientSourceAndConfig";
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.kafka.AuditKafkaClient4Compatibility";

    @ParameterizedTest(name = "automatic Java migration {0}")
    @MethodSource("automaticChanges")
    void appliesEveryDeterministicJavaChange(String label, String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTO)).parser(parser()),
                java(before, after, source -> source.path("src/" + label + ".java")));
    }

    static Stream<Arguments> automaticChanges() {
        return Stream.of(
                Arguments.of("DescribeValues", "import org.apache.kafka.clients.admin.DescribeTopicsResult; class T { Object f(DescribeTopicsResult r) { return r.values(); } }",
                        "import org.apache.kafka.clients.admin.DescribeTopicsResult; class T { Object f(DescribeTopicsResult r) { return r.topicNameValues(); } }"),
                Arguments.of("DescribeAll", "import org.apache.kafka.clients.admin.DescribeTopicsResult; class T { Object f(DescribeTopicsResult r) { return r.all(); } }",
                        "import org.apache.kafka.clients.admin.DescribeTopicsResult; class T { Object f(DescribeTopicsResult r) { return r.allTopicNames(); } }"),
                Arguments.of("DeleteValues", "import org.apache.kafka.clients.admin.DeleteTopicsResult; class T { Object f(DeleteTopicsResult r) { return r.values(); } }",
                        "import org.apache.kafka.clients.admin.DeleteTopicsResult; class T { Object f(DeleteTopicsResult r) { return r.topicNameValues(); } }"),
                Arguments.of("MockPoll", "import org.apache.kafka.clients.consumer.MockConsumer; import org.apache.kafka.common.KafkaException; class T { void f(MockConsumer<String,String> c, KafkaException e) { c.setException(e); } }",
                        "import org.apache.kafka.clients.consumer.MockConsumer; import org.apache.kafka.common.KafkaException; class T { void f(MockConsumer<String,String> c, KafkaException e) { c.setPollException(e); } }"),
                Arguments.of("ValidateOnly", "import org.apache.kafka.clients.admin.UpdateFeaturesOptions; class T { Object f(UpdateFeaturesOptions o) { return o.dryRun(true); } }",
                        "import org.apache.kafka.clients.admin.UpdateFeaturesOptions; class T { Object f(UpdateFeaturesOptions o) { return o.validateOnly(true); } }"),
                Arguments.of("LoginHandler", "import org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerLoginCallbackHandler; class T { OAuthBearerLoginCallbackHandler h; }",
                        "import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler;\n\nclass T { OAuthBearerLoginCallbackHandler h; }"),
                Arguments.of("ValidatorHandler", "import org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerValidatorCallbackHandler; class T { OAuthBearerValidatorCallbackHandler h; }",
                        "import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallbackHandler;\n\nclass T { OAuthBearerValidatorCallbackHandler h; }"),
                Arguments.of("LeaderException", "import org.apache.kafka.common.errors.NotLeaderForPartitionException; class T { RuntimeException f() { return new NotLeaderForPartitionException(); } }",
                        "import org.apache.kafka.common.errors.NotLeaderOrFollowerException;\n\nclass T { RuntimeException f() { return new NotLeaderOrFollowerException(); } }")
        );
    }

    @ParameterizedTest(name = "marks Java risk {0}")
    @MethodSource("riskCases")
    void marksExactBehaviorSensitiveJavaNode(String label, String source, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)).parser(parser()),
                java(source, input -> input.path("src/" + label + ".java").after(actual -> actual)
                        .afterRecipe(after -> assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    static Stream<Arguments> riskCases() {
        return Stream.of(
                Arguments.of("Alter", "import java.util.*; import org.apache.kafka.clients.admin.Admin; class T { void f(Admin a) { a.alterConfigs(Map.of()); } }", "alterConfigs was removed"),
                Arguments.of("MissingGroup", "import java.util.*; import org.apache.kafka.clients.admin.Admin; class T { void f(Admin a) { a.describeConsumerGroups(List.of(\"g\")); } }", "GroupIdNotFoundException"),
                Arguments.of("ListGroups", "import org.apache.kafka.clients.admin.Admin; class T { void f(Admin a) { a.listConsumerGroups(); } }", "deprecated in 4.1"),
                Arguments.of("Offsets", "import java.util.*; import org.apache.kafka.clients.producer.Producer; class T { void f(Producer<String,String> p) { p.sendOffsetsToTransaction(Map.of(), \"g\"); } }", "ConsumerGroupMetadata"),
                Arguments.of("Partitions", "import java.util.*; import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions; class T { void f(ListConsumerGroupOffsetsOptions o) { o.topicPartitions(List.of()); } }", "Map argument"),
                Arguments.of("PollLong", "import org.apache.kafka.clients.consumer.Consumer; class T { void f(Consumer<String,String> c) { c.poll(1000L); } }", "poll(Duration)"),
                Arguments.of("Committed", "import org.apache.kafka.clients.consumer.Consumer; import org.apache.kafka.common.TopicPartition; class T { void f(Consumer<String,String> c, TopicPartition p) { c.committed(p); } }", "Set<TopicPartition>"),
                Arguments.of("Listing", "import org.apache.kafka.clients.admin.TopicListing; class T { Object f() { return new TopicListing(\"orders\", false); } }", "real topic Uuid"),
                Arguments.of("FeatureCtor", "import org.apache.kafka.clients.admin.FeatureUpdate; class T { Object f() { return new FeatureUpdate((short) 1, true); } }", "UpgradeType"),
                Arguments.of("FeatureMethod", "import org.apache.kafka.clients.admin.FeatureUpdate; class T { boolean f(FeatureUpdate u) { return u.allowDowngrade(); } }", "allowDowngrade was removed"),
                Arguments.of("Reporter", "import org.apache.kafka.common.metrics.JmxReporter; class T { Object f() { return new JmxReporter(\"prefix\"); } }", "no-arg reporter"),
                Arguments.of("DescribeCtor", "import java.util.*; import org.apache.kafka.clients.admin.DescribeTopicsResult; class T { Object f() { return new DescribeTopicsResult(Map.of()); } }", "one-Map DescribeTopicsResult"),
                Arguments.of("DefaultPartitioner", "import org.apache.kafka.clients.producer.internals.DefaultPartitioner; class T { DefaultPartitioner p; }", "DefaultPartitioner was removed"),
                Arguments.of("StickyPartitioner", "import org.apache.kafka.clients.producer.UniformStickyPartitioner; class T { UniformStickyPartitioner p; }", "UniformStickyPartitioner was removed"),
                Arguments.of("GroupState", "import org.apache.kafka.common.ConsumerGroupState; class T { ConsumerGroupState s; }", "deprecated in favor of GroupState"),
                Arguments.of("ProducerDefaults", "import java.util.*; import org.apache.kafka.clients.producer.KafkaProducer; class T { Object f() { return new KafkaProducer<String,String>(new Properties()); } }", "Producer defaults changed"),
                Arguments.of("ConsumerProtocol", "import java.util.*; import org.apache.kafka.clients.consumer.KafkaConsumer; class T { Object f() { return new KafkaConsumer<String,String>(new Properties()); } }", "classic versus consumer group protocol"),
                Arguments.of("CallbackFlush", "import org.apache.kafka.clients.producer.*; class T { void f(Producer<String,String> p, ProducerRecord<String,String> r) { p.send(r, (m, e) -> p.flush()); } }", "rejects producer.flush() from a send callback"),
                Arguments.of("PartitionerOverride", "import java.util.*; import org.apache.kafka.clients.producer.Partitioner; class T implements Partitioner { public void onNewBatch(String t, Object c, int p) {} }", "onNewBatch was removed")
        );
    }

    @ParameterizedTest(name = "does not change Java lookalike {index}")
    @ValueSource(strings = {
            "import java.util.Map; class T { Object f(Map<String,String> m) { return m.values(); } }",
            "class T { Object all() { return null; } Object f() { return all(); } }",
            "class T { void setException(RuntimeException e) {} }",
            "class T { Object dryRun(boolean b) { return b; } }",
            "class T { void poll(long timeout) {} }",
            "class T { void committed(Object partition) {} }",
            "class T { void alterConfigs(Object values) {} }",
            "class T { void flush() {} void f() { Runnable r = () -> flush(); } }",
            "class DefaultPartitioner {}",
            "class ConsumerGroupState {}",
            "class T { void onNewBatch(String t, Object c, int p) {} }",
            "class T { T(String name, boolean internal) {} }"
    })
    void leavesUnattributedAndUnrelatedLookalikesUntouched(String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTO)).parser(parser()), java(source));
    }

    @Test
    void marksRemovedTypeExactlyOnceAtTheUseSiteNotItsImportOrVariableName() {
        String message = "DefaultPartitioner was removed";
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)).parser(parser()),
                java("import org.apache.kafka.clients.producer.internals.DefaultPartitioner; class T { DefaultPartitioner partitioner; }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("/*~~(" + message), printed);
                            assertEquals(1, occurrences(printed, message), printed);
                        })));
    }

    private static int occurrences(String text, String wanted) {
        int count = 0;
        for (int offset = 0; (offset = text.indexOf(wanted, offset)) >= 0; offset += wanted.length()) {
            count++;
        }
        return count;
    }

    private static JavaParser.Builder parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package org.apache.kafka.common; public class KafkaException extends RuntimeException {}",
                "package org.apache.kafka.common; public class TopicPartition {}",
                "package org.apache.kafka.common; public class Uuid {}",
                "package org.apache.kafka.common; public enum ConsumerGroupState { UNKNOWN, DEAD, STABLE }",
                "package org.apache.kafka.common.errors; public class NotLeaderForPartitionException extends RuntimeException { public NotLeaderForPartitionException() {} }",
                "package org.apache.kafka.common.errors; public class NotLeaderOrFollowerException extends RuntimeException { public NotLeaderOrFollowerException() {} }",
                "package org.apache.kafka.clients.admin; public class DescribeTopicsResult { public DescribeTopicsResult(java.util.Map<?,?> names) {} public java.util.Map<String,Object> values(){return null;} public Object all(){return null;} public java.util.Map<String,Object> topicNameValues(){return null;} public Object allTopicNames(){return null;} }",
                "package org.apache.kafka.clients.admin; public class DeleteTopicsResult { public java.util.Map<String,Object> values(){return null;} public java.util.Map<String,Object> topicNameValues(){return null;} }",
                "package org.apache.kafka.clients.admin; public class UpdateFeaturesOptions { public UpdateFeaturesOptions dryRun(boolean b){return this;} public UpdateFeaturesOptions validateOnly(boolean b){return this;} }",
                "package org.apache.kafka.clients.admin; public interface Admin { Object alterConfigs(java.util.Map<?,?> c); Object describeConsumerGroups(java.util.Collection<String> g); Object listConsumerGroups(); }",
                "package org.apache.kafka.clients.admin; public class ListConsumerGroupOffsetsOptions { public Object topicPartitions(java.util.List<?> p){return this;} }",
                "package org.apache.kafka.clients.admin; public class TopicListing { public TopicListing(String n, boolean i) {} }",
                "package org.apache.kafka.clients.admin; public class FeatureUpdate { public FeatureUpdate(short l, boolean d) {} public boolean allowDowngrade(){return false;} }",
                "package org.apache.kafka.clients.consumer; public class ConsumerRecords<K,V> {}",
                "package org.apache.kafka.clients.consumer; public interface Consumer<K,V> { ConsumerRecords<K,V> poll(long t); Object committed(org.apache.kafka.common.TopicPartition p); }",
                "package org.apache.kafka.clients.consumer; public class KafkaConsumer<K,V> implements Consumer<K,V> { public KafkaConsumer(java.util.Properties p) {} public ConsumerRecords<K,V> poll(long t){return null;} public Object committed(org.apache.kafka.common.TopicPartition p){return null;} }",
                "package org.apache.kafka.clients.consumer; public class MockConsumer<K,V> { public void setException(org.apache.kafka.common.KafkaException e){} public void setPollException(org.apache.kafka.common.KafkaException e){} }",
                "package org.apache.kafka.clients.producer; public class ProducerRecord<K,V> {}",
                "package org.apache.kafka.clients.producer; public class RecordMetadata {}",
                "package org.apache.kafka.clients.producer; public interface Callback { void onCompletion(RecordMetadata m, Exception e); }",
                "package org.apache.kafka.clients.producer; public interface Producer<K,V> { void sendOffsetsToTransaction(java.util.Map<?,?> o,String g); Object send(ProducerRecord<K,V> r, Callback c); void flush(); }",
                "package org.apache.kafka.clients.producer; public class KafkaProducer<K,V> implements Producer<K,V> { public KafkaProducer(java.util.Properties p){} public void sendOffsetsToTransaction(java.util.Map<?,?> o,String g){} public Object send(ProducerRecord<K,V> r, Callback c){return null;} public void flush(){} }",
                "package org.apache.kafka.clients.producer; public interface Partitioner { void onNewBatch(String topic, Object cluster, int prevPartition); }",
                "package org.apache.kafka.clients.producer.internals; public class DefaultPartitioner {}",
                "package org.apache.kafka.clients.producer; public class UniformStickyPartitioner {}",
                "package org.apache.kafka.common.metrics; public class JmxReporter { public JmxReporter(String p){} }",
                "package org.apache.kafka.common.security.oauthbearer.secured; public class OAuthBearerLoginCallbackHandler {}",
                "package org.apache.kafka.common.security.oauthbearer.secured; public class OAuthBearerValidatorCallbackHandler {}",
                "package org.apache.kafka.common.security.oauthbearer; public class OAuthBearerLoginCallbackHandler {}",
                "package org.apache.kafka.common.security.oauthbearer; public class OAuthBearerValidatorCallbackHandler {}"
        );
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.kafka")
                .scanYamlResources().build();
    }
}
