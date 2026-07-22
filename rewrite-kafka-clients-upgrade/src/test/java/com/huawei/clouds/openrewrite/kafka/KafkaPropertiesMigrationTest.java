package com.huawei.clouds.openrewrite.kafka;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;

class KafkaPropertiesMigrationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.kafka.MigrateDeterministicKafkaClientSourceAndConfig";
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.kafka.AuditKafkaClient4Compatibility";

    @ParameterizedTest(name = "migrates property {0}")
    @MethodSource("automaticProperties")
    void migratesOnlyExactRemovedJmxProperties(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUTO)),
                properties(before, after, source -> source.path("config/client.properties")));
    }

    static Stream<Arguments> automaticProperties() {
        return Stream.of(
                Arguments.of("metrics.jmx.blacklist=a.*\n", "metrics.jmx.exclude=a.*\n"),
                Arguments.of("metrics.jmx.whitelist=b.*\n", "metrics.jmx.include=b.*\n"),
                Arguments.of("metrics.jmx.blacklist : a.*\n", "metrics.jmx.exclude : a.*\n"),
                Arguments.of("metrics.jmx.whitelist b.*\n", "metrics.jmx.include b.*\n"),
                Arguments.of("auto.include.jmx.reporter=true\nclient.id=orders\n", "client.id=orders\n"),
                Arguments.of("auto.include.jmx.reporter=false\nclient.id=orders\n", "client.id=orders\n"),
                Arguments.of("# keep\nmetrics.jmx.blacklist=a.*\nmetrics.jmx.whitelist=b.*\n",
                        "# keep\nmetrics.jmx.exclude=a.*\nmetrics.jmx.include=b.*\n")
        );
    }

    @ParameterizedTest(name = "marks property risk {0}")
    @MethodSource("riskProperties")
    void marksExactOperationalProperty(String label, String source, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                properties(source, input -> input.path("config/" + label + ".properties")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    static Stream<Arguments> riskProperties() {
        return Stream.of(
                Arguments.of("token", "sasl.oauthbearer.token.endpoint.url=https://id/token\n", "allowed.urls"),
                Arguments.of("jwks", "sasl.oauthbearer.jwks.endpoint.url=https://id/jwks\n", "allowed.urls"),
                Arguments.of("flight-default", "max.in.flight.requests.per.connection=6\n", "rejects max.in.flight"),
                Arguments.of("flight-enabled", "enable.idempotence=true\nmax.in.flight.requests.per.connection=20\n", "rejects max.in.flight"),
                Arguments.of("buffer-metric", "dashboard=bufferpool-wait-time-total\n", "-ns- replacement"),
                Arguments.of("io-wait-metric", "dashboard=io-waittime-total\n", "-ns- replacement"),
                Arguments.of("io-metric", "dashboard=iotime-total\n", "-ns- replacement"),
                Arguments.of("producer-defaults", "bootstrap.servers=kafka:9092\nkey.serializer=example.Key\n", "Producer defaults changed"),
                Arguments.of("producer-linger", "bootstrap.servers=kafka:9092\nvalue.serializer=example.Value\nlinger.ms=0\n", "Producer defaults changed"),
                Arguments.of("consumer-protocol", "group.protocol=consumer\ngroup.id=orders\n", "heartbeat/session ownership"),
                Arguments.of("assignor", "group.protocol=consumer\npartition.assignment.strategy=example.CustomAssignor\n", "does not configure the new consumer"),
                Arguments.of("transaction", "transactional.id=orders-writer\nbootstrap.servers=kafka:9092\n", "transaction fencing")
        );
    }

    @ParameterizedTest(name = "preserves safe property fixture {index}")
    @ValueSource(strings = {
            "enable.idempotence=false\nmax.in.flight.requests.per.connection=6\n",
            "enable.idempotence=FALSE\nmax.in.flight.requests.per.connection=100\n",
            "enable.idempotence=true\nmax.in.flight.requests.per.connection=5\n",
            "enable.idempotence=true\nmax.in.flight.requests.per.connection=1\n",
            "max.in.flight.requests.per.connection=${MAX_IN_FLIGHT}\n",
            "max.in.flight.requests.per.connection=not-a-number\n",
            "bootstrap.servers=kafka:9092\ngroup.id=orders\n",
            "bootstrap.servers=kafka:9092\nkey.serializer=x\nlinger.ms=0\nenable.idempotence=false\n",
            "group.protocol=classic\npartition.assignment.strategy=example.Assignor\n",
            "partition.assignment.strategy=example.Assignor\n",
            "metrics.jmx.include=a.*\n",
            "metrics.jmx.exclude=b.*\n",
            "app.metrics.jmx.blacklist=a.*\n",
            "auto.include.jmx.reporter.enabled=true\n",
            "dashboard=bufferpool-wait-time-ns-total\n",
            "dashboard=io-wait-time-ns-total\n",
            "dashboard=io-time-ns-total\n",
            "dashboard=prefix-iotime-totalized\n",
            "sasl.oauthbearer.token.endpoint.uri=https://id/token\n",
            "sasl.oauthbearer.allowed.urls=https://id/token\n",
            "linger.ms=5\nenable.idempotence=true\n",
            "client.id=orders\n",
            "application.name=orders\n"
    })
    void leavesSafeExplicitAndLookalikePropertiesUnmarked(String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)), properties(source));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.kafka")
                .scanYamlResources().build();
    }
}
