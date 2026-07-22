package com.huawei.clouds.openrewrite.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

class MigrateKafkaClientsTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.kafka.UpgradeKafkaClientsDependencyTo4_1_2";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2";
    private static final String AUTOMATIC_RECIPE =
            "com.huawei.clouds.openrewrite.kafka.MigrateDeterministicKafkaClientSourceAndConfig";
    private static final String AUDIT_RECIPE =
            "com.huawei.clouds.openrewrite.kafka.AuditKafkaClient4Compatibility";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE))
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package org.apache.kafka.common;
                        public class KafkaException extends RuntimeException {}
                        """,
                        """
                        package org.apache.kafka.clients.admin;
                        public class DescribeTopicsResult {
                            public java.util.Map<String, Object> values() { return null; }
                            public Object all() { return null; }
                            public java.util.Map<String, Object> topicNameValues() { return null; }
                            public Object allTopicNames() { return null; }
                        }
                        """,
                        """
                        package org.apache.kafka.clients.admin;
                        public class DeleteTopicsResult {
                            public java.util.Map<String, Object> values() { return null; }
                            public java.util.Map<String, Object> topicNameValues() { return null; }
                        }
                        """,
                        """
                        package org.apache.kafka.clients.consumer;
                        public class MockConsumer<K,V> {
                            public void setException(org.apache.kafka.common.KafkaException e) {}
                            public void setPollException(org.apache.kafka.common.KafkaException e) {}
                        }
                        """,
                        """
                        package org.apache.kafka.clients.consumer;
                        public interface Consumer<K,V> {
                            Object poll(long timeout);
                        }
                        """,
                        """
                        package org.apache.kafka.clients.admin;
                        public class UpdateFeaturesOptions {
                            public UpdateFeaturesOptions dryRun(boolean value) { return this; }
                            public UpdateFeaturesOptions validateOnly(boolean value) { return this; }
                        }
                        """,
                        """
                        package org.apache.kafka.common.security.oauthbearer.secured;
                        public class OAuthBearerLoginCallbackHandler {}
                        """,
                        """
                        package org.apache.kafka.common.security.oauthbearer.secured;
                        public class OAuthBearerValidatorCallbackHandler {}
                        """,
                        """
                        package org.apache.kafka.common.security.oauthbearer;
                        public class OAuthBearerLoginCallbackHandler {}
                        """,
                        """
                        package org.apache.kafka.common.security.oauthbearer;
                        public class OAuthBearerValidatorCallbackHandler {}
                        """,
                        """
                        package org.apache.kafka.clients.admin;
                        public interface Admin {
                            Object alterConfigs(java.util.Map<?,?> configs);
                            Object describeConsumerGroups(java.util.Collection<String> groups);
                        }
                        """,
                        """
                        package org.apache.kafka.clients.producer;
                        public interface Producer<K,V> {
                            void sendOffsetsToTransaction(java.util.Map<?,?> offsets, String groupId);
                        }
                        """,
                        """
                        package org.apache.kafka.clients.admin;
                        public class ListConsumerGroupOffsetsOptions {
                            public ListConsumerGroupOffsetsOptions topicPartitions(java.util.List<?> partitions) { return this; }
                        }
                        """,
                        """
                        package org.apache.kafka.clients.admin;
                        public class TopicListing { public TopicListing(String name, boolean internal) {} }
                        """,
                        """
                        package org.apache.kafka.clients.admin;
                        public class FeatureUpdate {
                            public FeatureUpdate(short level, boolean allowDowngrade) {}
                            public boolean allowDowngrade() { return false; }
                        }
                        """,
                        """
                        package org.apache.kafka.common.metrics;
                        public class JmxReporter { public JmxReporter(String prefix) {} }
                        """
                ));
    }

    @ParameterizedTest(name = "upgrades exact spreadsheet Maven version {0}")
    @ValueSource(strings = {
            "2.4.1", "2.5.1", "3.1.2", "3.4.0", "3.4.1",
            "3.5.1", "3.6.0", "3.6.1", "3.6.2", "3.7.0"
    })
    void upgradesEveryExactSpreadsheetVersion(String version) {
        rewriteRun(pomXml(pomWithVersion(version), pomWithVersion("4.1.2")));
    }

    @ParameterizedTest(name = "does not upgrade unlisted Maven version {0}")
    @ValueSource(strings = {"2.4.0", "2.7.0", "3.3.2", "3.7.1", "4.1.1", "4.1.2", "4.2.0", "[3.7,4.0)", "LATEST"})
    void rejectsUnlistedTargetNewerRangesAndDynamicVersions(String version) {
        rewriteRun(pomXml(pomWithVersion(version)));
    }

    @Test
    void upgradesExclusiveMavenProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>consumer</artifactId><version>1</version>
                  <properties><kafka.version>2.4.1</kafka.version></properties>
                  <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>consumer</artifactId><version>1</version>
                  <properties><kafka.version>4.1.2</kafka.version></properties>
                  <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void isolatesSharedJHipsterStyleMavenProperty() {
        // Coordinate/property shape from jhipster/generator-jhipster at 41d71af1eb85ae7c94e0e9b05acab968c4d047e3:
        // generators/spring-boot/resources/spring-boot-dependencies.pom
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>platform</artifactId><version>1</version>
                  <properties><kafka.version>3.6.2</kafka.version></properties><dependencies>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-streams</artifactId><version>${kafka.version}</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>platform</artifactId><version>1</version>
                  <properties><kafka.version>3.6.2</kafka.version></properties><dependencies>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency>
                    <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-streams</artifactId><version>${kafka.version}</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesDependencyManagementButLeavesExternalBomManagedDependencyUntouched() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>platform</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>3.6.2</version></dependency></dependencies></dependencyManagement>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>platform</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency></dependencies></dependencyManagement>
                        </project>
                        """
                ),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>3.2.0</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId></dependency></dependencies>
                        </project>
                        """, spec -> spec.path("bom/pom.xml"))
        );
    }

    @Test
    void upgradesLiteralGradleAndKotlinDslCoordinates() {
        rewriteRun(
                buildGradle(
                        "plugins { id 'java' }\ndependencies { implementation 'org.apache.kafka:kafka-clients:3.4.1' }",
                        "plugins { id 'java' }\ndependencies { implementation 'org.apache.kafka:kafka-clients:4.1.2' }"
                ),
                buildGradleKts(
                        "plugins { java }\ndependencies { implementation(\"org.apache.kafka:kafka-clients:3.7.0\") }",
                        "plugins { java }\ndependencies { implementation(\"org.apache.kafka:kafka-clients:4.1.2\") }",
                        spec -> spec.path("kotlin/build.gradle.kts")
                )
        );
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                "plugins { id 'java' }\ndependencies { api group: 'org.apache.kafka', name: 'kafka-clients', version: '3.5.1' }",
                "plugins { id 'java' }\ndependencies { api group: 'org.apache.kafka', name: 'kafka-clients', version: '4.1.2' }"
        ));
    }

    @Test
    void rejectsConductorStyleDynamicGradleVersionAndOtherArtifacts() {
        // Exact dynamic declaration from conductor-oss/conductor at
        // 54f8369fa8875a2bad4ed5baa8a66f89720b1594/kafka/build.gradle.
        rewriteRun(buildGradle("""
                plugins { id 'java' }
                ext.revKafka = '3.4.1'
                dependencies {
                    implementation "org.apache.kafka:kafka-clients:${revKafka}"
                    implementation 'org.apache.kafka:kafka-streams:3.4.1'
                    implementation 'org.apache.kafka:kafka_2.13:3.4.1'
                }
                """));
    }

    @Test
    void migratesDeterministicRemovedApis() {
        // DescribeTopicsResult.values() shape from codingmiao/hppt at
        // 509da821a3cc33e8049d6037d90637e2274a0016/addons-kafka/src/main/java/org/wowtools/hppt/addons/kafka/KafkaUtil.java.
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import org.apache.kafka.clients.admin.*;
                        import org.apache.kafka.clients.consumer.MockConsumer;
                        import org.apache.kafka.common.KafkaException;
                        class KafkaUsage {
                            Object describeValues(DescribeTopicsResult r) { return r.values(); }
                            Object describeAll(DescribeTopicsResult r) { return r.all(); }
                            Object deleteValues(DeleteTopicsResult r) { return r.values(); }
                            void mock(MockConsumer<String,String> c) { c.setException(new KafkaException()); }
                            Object feature(UpdateFeaturesOptions o) { return o.dryRun(true); }
                        }
                        """,
                        """
                        import org.apache.kafka.clients.admin.*;
                        import org.apache.kafka.clients.consumer.MockConsumer;
                        import org.apache.kafka.common.KafkaException;
                        class KafkaUsage {
                            Object describeValues(DescribeTopicsResult r) { return r.topicNameValues(); }
                            Object describeAll(DescribeTopicsResult r) { return r.allTopicNames(); }
                            Object deleteValues(DeleteTopicsResult r) { return r.topicNameValues(); }
                            void mock(MockConsumer<String,String> c) { c.setPollException(new KafkaException()); }
                            Object feature(UpdateFeaturesOptions o) { return o.validateOnly(true); }
                        }
                        """
                )
        );
    }

    @Test
    void migratesRemovedSecuredOauthHandlerPackages() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerLoginCallbackHandler;
                        import org.apache.kafka.common.security.oauthbearer.secured.OAuthBearerValidatorCallbackHandler;
                        class OAuthConfig {
                            OAuthBearerLoginCallbackHandler login;
                            OAuthBearerValidatorCallbackHandler validator;
                        }
                        """,
                        """
                        import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler;
                        import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallbackHandler;

                        class OAuthConfig {
                            OAuthBearerLoginCallbackHandler login;
                            OAuthBearerValidatorCallbackHandler validator;
                        }
                        """
                )
        );
    }

    @Test
    void keepsAlreadyMigratedAndUnrelatedMethodsIdempotent() {
        // topicNameValues() is present in openGauss datachecker at
        // 3099b9db802cf0b09d4f1ad1a556b1cd5f5c6988/datachecker-extract/.../KafkaAdminService.java.
        // DescribeAclsResult.values() is a real unrelated call shape in vert-x3/vertx-kafka-client at
        // 57cdfd5e63cb45dccc18cacb0d19d69972675a90.
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java("""
                        import java.util.Map;
                        import org.apache.kafka.clients.admin.DeleteTopicsResult;
                        class StableUsage {
                            Object migrated(DeleteTopicsResult r) { return r.topicNameValues(); }
                            Object unrelated(Map<String,String> map) { return map.values(); }
                        }
                        """)
        );
    }

    @Test
    void migratesExactJmxPropertiesAndDeletesRemovedAutoIncludeProperty() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                properties(
                        """
                        metrics.jmx.blacklist=kafka.consumer:type=*,client-id=*
                        metrics.jmx.whitelist=kafka.producer:type=producer-metrics,*
                        auto.include.jmx.reporter=true
                        app.metrics.jmx.blacklist=application-only
                        """,
                        """
                        metrics.jmx.exclude=kafka.consumer:type=*,client-id=*
                        metrics.jmx.include=kafka.producer:type=producer-metrics,*
                        app.metrics.jmx.blacklist=application-only
                        """,
                        spec -> spec.path("config/client.properties")
                )
        );
    }

    @Test
    void leavesGeneratedJavaAndPropertiesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUTOMATIC_RECIPE)),
                java(
                        "import org.apache.kafka.clients.admin.DescribeTopicsResult; class Generated { Object f(DescribeTopicsResult r) { return r.values(); } }",
                        source -> source.path("target/generated-sources/Generated.java")
                ),
                properties(
                        "metrics.jmx.blacklist=kafka.consumer:type=*\nauto.include.jmx.reporter=true\n",
                        source -> source.path("build/generated-resources/kafka.properties")
                )
        );
    }

    @Test
    void marksPropertiesRisksPrecisely() {
        rewriteRun(
                spec -> spec.recipe(new FindKafkaClientPropertiesRisks()),
                properties(
                        """
                        sasl.oauthbearer.token.endpoint.url=https://id.example/token
                        max.in.flight.requests.per.connection=6
                        dashboard.metric=io-waittime-total
                        """,
                        """
                        ~~(Kafka 4.1 requires this endpoint to be allowed by the org.apache.kafka.sasl.oauthbearer.allowed.urls system property)~~>sasl.oauthbearer.token.endpoint.url=https://id.example/token
                        ~~(With idempotence enabled (including its default), Kafka 4.1 rejects max.in.flight.requests.per.connection greater than 5)~~>max.in.flight.requests.per.connection=6
                        ~~(Kafka 4.1 removed a legacy metric name; use its -ns- replacement and recalculate unit-sensitive thresholds)~~>dashboard.metric=io-waittime-total
                        """,
                        spec -> spec.path("producer.properties")
                )
        );
    }

    @Test
    void doesNotMarkSafeOrUnresolvedProducerSettings() {
        rewriteRun(
                spec -> spec.recipe(new FindKafkaClientPropertiesRisks()),
                properties("""
                        enable.idempotence=false
                        max.in.flight.requests.per.connection=12
                        """, spec -> spec.path("disabled.properties")),
                properties("""
                        enable.idempotence=true
                        max.in.flight.requests.per.connection=5
                        """, spec -> spec.path("safe.properties")),
                properties("max.in.flight.requests.per.connection=${MAX_IN_FLIGHT}\n",
                        spec -> spec.path("unresolved.properties"))
        );
    }

    @Test
    void marksBehaviorSensitiveJavaApis() {
        rewriteRun(
                spec -> spec.recipe(new FindKafkaClientJavaMigrationRisks()).cycles(1).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import java.util.*;
                        import org.apache.kafka.clients.admin.*;
                        import org.apache.kafka.clients.producer.Producer;
                        import org.apache.kafka.common.metrics.JmxReporter;
                        class LegacyApis {
                            void admin(Admin admin) {
                                admin.alterConfigs(Collections.emptyMap());
                                admin.describeConsumerGroups(Collections.singleton("group"));
                            }
                            void producer(Producer<String,String> producer) {
                                producer.sendOffsetsToTransaction(Collections.emptyMap(), "group");
                            }
                            Object options(ListConsumerGroupOffsetsOptions o) { return o.topicPartitions(Collections.emptyList()); }
                            Object listing() { return new TopicListing("orders", false); }
                            Object feature() { return new FeatureUpdate((short) 1, true); }
                            Object reporter() { return new JmxReporter("prefix"); }
                        }
                        """,
                        """
                        import java.util.*;
                        import org.apache.kafka.clients.admin.*;
                        import org.apache.kafka.clients.producer.Producer;
                        import org.apache.kafka.common.metrics.JmxReporter;
                        class LegacyApis {
                            void admin(Admin admin) {
                                /*~~(alterConfigs was removed; convert complete Config maps into explicit incrementalAlterConfigs AlterConfigOp operations)~~>*/admin.alterConfigs(Collections.emptyMap());
                                /*~~(Missing groups now fail with GroupIdNotFoundException instead of returning a DEAD group; review error handling)~~>*/admin.describeConsumerGroups(Collections.singleton("group"));
                            }
                            void producer(Producer<String,String> producer) {
                                /*~~(The consumerGroupId overload was removed; obtain the matching ConsumerGroupMetadata before choosing the replacement)~~>*/producer.sendOffsetsToTransaction(Collections.emptyMap(), "group");
                            }
                            Object options(ListConsumerGroupOffsetsOptions o) { return /*~~(topicPartitions was removed; move the partition selection to the Map argument of listConsumerGroupOffsets)~~>*/o.topicPartitions(Collections.emptyList()); }
                            Object listing() { return /*~~(The two-argument TopicListing constructor was removed; supply the real topic Uuid)~~>*/new TopicListing("orders", false); }
                            Object feature() { return /*~~(The boolean allowDowngrade constructor was removed; choose an explicit FeatureUpdate.UpgradeType)~~>*/new FeatureUpdate((short) 1, true); }
                            Object reporter() { return /*~~(JmxReporter(String) was removed; use the no-arg reporter and configure include/exclude rules)~~>*/new JmxReporter("prefix"); }
                        }
                        """
                )
        );
    }

    @Test
    void recommendedAutomaticPathIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pomWithVersion("3.6.2"), pomWithVersion("4.1.2")),
                java(
                        "import org.apache.kafka.clients.admin.DescribeTopicsResult; class Safe { Object f(DescribeTopicsResult r) { return r.values(); } }",
                        "import org.apache.kafka.clients.admin.DescribeTopicsResult; class Safe { Object f(DescribeTopicsResult r) { return r.topicNameValues(); } }"
                )
        );
    }

    @Test
    void recommendedRecipeCombinesStrictBuildSourceConfigAutoAndPreciseMarks() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><java.version>8</java.version></properties>
                          <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>3.6.2</version></dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><!--~~(Kafka clients 4.1 requires Java 11 or newer; align compiler, test JVM and production runtime)~~>--><java.version>8</java.version></properties>
                          <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency></dependencies>
                        </project>
                        """
                ),
                java(
                        """
                        import org.apache.kafka.clients.admin.DescribeTopicsResult;
                        import org.apache.kafka.clients.consumer.Consumer;
                        class Combined {
                            Object topics(DescribeTopicsResult result) { return result.values(); }
                            void records(Consumer<String, String> consumer) { consumer.poll(1000L); }
                        }
                        """,
                        """
                        import org.apache.kafka.clients.admin.DescribeTopicsResult;
                        import org.apache.kafka.clients.consumer.Consumer;
                        class Combined {
                            Object topics(DescribeTopicsResult result) { return result.topicNameValues(); }
                            void records(Consumer<String, String> consumer) { /*~~(Consumer.poll(long) was removed; use poll(Duration), noting that Duration polling does not wait beyond the timeout for initial assignment)~~>*/consumer.poll(1000L); }
                        }
                        """
                ),
                properties(
                        """
                        metrics.jmx.blacklist=kafka.producer:type=*
                        transactional.id=orders-writer
                        """,
                        """
                        metrics.jmx.exclude=kafka.producer:type=*
                        ~~(Kafka 4 strengthens transaction fencing and adds abortable transaction errors; verify stable transactional.id ownership, timeouts, abort paths and rolling deployment)~~>transactional.id=orders-writer
                        """,
                        source -> source.path("config/producer.properties")
                )
        );
    }

    @Test
    void discoversAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{DEPENDENCY_RECIPE, AUTOMATIC_RECIPE, AUDIT_RECIPE, MIGRATION_RECIPE}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(r -> name.equals(r.getName())), name);
            assertTrue(recipe.validate().isValid(), () -> name + ": " + recipe.validate().failures());
        }
    }

    private static String pomWithVersion(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                 <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>%s</version></dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.kafka")
                .scanYamlResources()
                .build();
    }
}
