package com.huawei.clouds.openrewrite.kafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

class MigrateKafkaClientsTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.kafka.UpgradeKafkaClientsDependencyTo4_1_2";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.kafka.MigrateKafkaClientsTo4_1_2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE))
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package org.apache.kafka.clients.admin;
                        public class DescribeTopicsResult {
                            public java.util.Map<String, Object> values() { return null; }
                            public Object all() { return null; }
                            public java.util.Map<String, Object> topicNameValues() { return null; }
                            public Object allTopicNames() { return null; }
                        }
                        """
                ));
    }

    @Test
    void upgradesMaxwellStyleMavenDependency() {
        // Adapted from zendesk/maxwell at b600e519:
        // https://github.com/zendesk/maxwell/blob/b600e5190c3cd6051a498dd443910d30860b78f5/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>com.zendesk</groupId><artifactId>maxwell</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>2.7.0</version>
                  </dependency></dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>com.zendesk</groupId><artifactId>maxwell</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesMavenVersionPropertyFromSpreadsheetVersion() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>consumer</artifactId><version>1</version>
                  <properties><kafka.version>2.4.1</kafka.version></properties>
                  <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency></dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>consumer</artifactId><version>1</version>
                  <properties><kafka.version>4.1.2</kafka.version></properties>
                  <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>${kafka.version}</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesDependencyManagement() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>platform</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>3.6.2</version></dependency></dependencies></dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>platform</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesConductorStyleGradleDependency() {
        // Adapted from conductor-oss/conductor at 54f8369f:
        // https://github.com/conductor-oss/conductor/blob/54f8369fa8875a2bad4ed5baa8a66f89720b1594/kafka/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation "org.apache.kafka:kafka-clients:3.4.1" }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation "org.apache.kafka:kafka-clients:4.1.2" }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { api group: 'org.apache.kafka', name: 'kafka-clients', version: '3.5.1' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { api group: 'org.apache.kafka', name: 'kafka-clients', version: '4.1.2' }
                """
        ));
    }

    @Test
    void leavesTargetAndNewerVersionsUntouched() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>target</artifactId><version>1</version><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency></dependencies></project>
                        """),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>newer</artifactId><version>1</version><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.2.1</version></dependency></dependencies></project>
                        """, spec -> spec.path("newer-pom.xml")),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>newer-patch</artifactId><version>1</version><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.2.0</version></dependency></dependencies></project>
                        """, spec -> spec.path("newer-patch-pom.xml"))
        );
    }

    @Test
    void leavesSimilarKafkaArtifactsUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>other</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-streams</artifactId><version>3.6.2</version></dependency>
                  <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka_2.13</artifactId><version>3.6.2</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void migratesRemovedDescribeTopicsMethodsFromRealUsage() {
        // Shape adapted from codingmiao/hppt:
        // https://github.com/codingmiao/hppt/blob/509da821a3cc33e8049d6037d90637e2274a0016/addons-kafka/src/main/java/org/wowtools/hppt/addons/kafka/KafkaUtil.java
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import org.apache.kafka.clients.admin.DescribeTopicsResult;
                        class KafkaUtil {
                            Object futures(DescribeTopicsResult result) { return result.values(); }
                            Object all(DescribeTopicsResult result) { return result.all(); }
                        }
                        """,
                        """
                        import org.apache.kafka.clients.admin.DescribeTopicsResult;
                        class KafkaUtil {
                            Object futures(DescribeTopicsResult result) { return result.topicNameValues(); }
                            Object all(DescribeTopicsResult result) { return result.allTopicNames(); }
                        }
                        """
                )
        );
    }

    @Test
    void doesNotRenameUnrelatedValuesOrAllMethods() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import java.util.Map;
                        class Unrelated {
                            Object values(Map<String, String> map) { return map.values(); }
                            Object all(Service service) { return service.all(); }
                        }
                        class Service { Object all() { return null; } }
                        """
                )
        );
    }

    @Test
    void migratesRemovedJmxReporterProperties() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                properties(
                        """
                        bootstrap.servers=localhost:9092
                        metrics.jmx.blacklist=kafka.consumer:type=*,client-id=*
                        metrics.jmx.whitelist=kafka.producer:type=producer-metrics,*
                        """,
                        """
                        bootstrap.servers=localhost:9092
                        metrics.jmx.exclude=kafka.consumer:type=*,client-id=*
                        metrics.jmx.include=kafka.producer:type=producer-metrics,*
                        """,
                        spec -> spec.path("config/client.properties")
                )
        );
    }

    @Test
    void preservesSimilarAndAlreadyMigratedProperties() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                properties(
                        """
                        app.metrics.jmx.blacklist=application-only
                        metrics.jmx.exclude=current
                        metrics.jmx.include=current
                        """,
                        spec -> spec.path("client.properties")
                )
        );
    }

    @Test
    void fullRecipeMigratesDependencySourceAndPropertiesTogether() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>admin</artifactId><version>1</version><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>3.1.2</version></dependency></dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>admin</artifactId><version>1</version><dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>4.1.2</version></dependency></dependencies></project>
                        """
                ),
                java(
                        """
                        import org.apache.kafka.clients.admin.DescribeTopicsResult;
                        class AdminService { Object describe(DescribeTopicsResult result) { return result.values(); } }
                        """,
                        """
                        import org.apache.kafka.clients.admin.DescribeTopicsResult;
                        class AdminService { Object describe(DescribeTopicsResult result) { return result.topicNameValues(); } }
                        """
                ),
                properties(
                        "metrics.jmx.whitelist=kafka.producer:*\n",
                        "metrics.jmx.include=kafka.producer:*\n",
                        spec -> spec.path("client.properties")
                )
        );
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe migration = environment.activateRecipes(MIGRATION_RECIPE);

        assertTrue(environment.listRecipes().stream().anyMatch(r -> DEPENDENCY_RECIPE.equals(r.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(r -> MIGRATION_RECIPE.equals(r.getName())));
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(migration.validate().isValid(), () -> migration.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.kafka")
                .scanYamlResources()
                .build();
    }
}
