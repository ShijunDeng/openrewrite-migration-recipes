package com.huawei.clouds.openrewrite.commonspool2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class MigrateCommonsPool2DurationSettersTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateCommonsPool2DurationSetters())
                .parser(JavaParser.fromJavaVersion().classpath("commons-pool2"));
    }

    @ParameterizedTest(name = "config setter {0}")
    @MethodSource("configSetters")
    void migratesConfigurationMillisWithoutChangingUnits(String oldName, String replacement) {
        rewriteRun(java(
                """
                import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
                class PoolConfig {
                    void configure(GenericObjectPoolConfig<String> config, long millis) {
                        config.%s(millis);
                    }
                }
                """.formatted(oldName),
                """
                import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

                import java.time.Duration;

                class PoolConfig {
                    void configure(GenericObjectPoolConfig<String> config, long millis) {
                        config.%s(Duration.ofMillis(millis));
                    }
                }
                """.formatted(replacement)));
    }

    static Stream<Arguments> configSetters() {
        return Stream.of(
                Arguments.of("setMaxWaitMillis", "setMaxWait"),
                Arguments.of("setEvictorShutdownTimeoutMillis", "setEvictorShutdownTimeout"),
                Arguments.of("setMinEvictableIdleTimeMillis", "setMinEvictableIdleDuration"),
                Arguments.of("setSoftMinEvictableIdleTimeMillis", "setSoftMinEvictableIdleDuration"),
                Arguments.of("setTimeBetweenEvictionRunsMillis", "setTimeBetweenEvictionRuns")
        );
    }

    @ParameterizedTest(name = "live pool setter {0}")
    @MethodSource("poolSetters")
    void migratesLivePoolMillisUsingCanonicalTargetApi(String oldName, String replacement) {
        rewriteRun(java(
                """
                import org.apache.commons.pool2.impl.GenericObjectPool;
                class LivePool {
                    void configure(GenericObjectPool<String> pool) {
                        pool.%s(250L);
                    }
                }
                """.formatted(oldName),
                """
                import org.apache.commons.pool2.impl.GenericObjectPool;

                import java.time.Duration;

                class LivePool {
                    void configure(GenericObjectPool<String> pool) {
                        pool.%s(Duration.ofMillis(250L));
                    }
                }
                """.formatted(replacement)));
    }

    static Stream<Arguments> poolSetters() {
        return Stream.of(
                Arguments.of("setMaxWaitMillis", "setMaxWait"),
                Arguments.of("setEvictorShutdownTimeoutMillis", "setEvictorShutdownTimeout"),
                Arguments.of("setMinEvictableIdleTimeMillis", "setMinEvictableIdleDuration"),
                Arguments.of("setSoftMinEvictableIdleTimeMillis", "setSoftMinEvictableIdleDuration"),
                Arguments.of("setTimeBetweenEvictionRunsMillis", "setDurationBetweenEvictionRuns")
        );
    }

    @Test
    void preservesExpressionsAndAddsOneImport() {
        rewriteRun(java(
                """
                import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
                class KeyedConfig {
                    long timeout() { return 42L; }
                    void configure(GenericKeyedObjectPoolConfig<String> c) {
                        c.setMaxWaitMillis(timeout());
                        c.setTimeBetweenEvictionRunsMillis(-1);
                    }
                }
                """,
                """
                import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

                import java.time.Duration;

                class KeyedConfig {
                    long timeout() { return 42L; }
                    void configure(GenericKeyedObjectPoolConfig<String> c) {
                        c.setMaxWait(Duration.ofMillis(timeout()));
                        c.setTimeBetweenEvictionRuns(Duration.ofMillis(-1));
                    }
                }
                """));
    }

    @Test
    void migratesSpringDataRedisStarvationFixture() {
        // Extracted from spring-projects/spring-data-redis@6a016a8f63a09065e4dba8835de11881061b4181.
        rewriteRun(java(
                """
                import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
                class JedisTransactionalConnectionStarvationFixture {
                    void configure(GenericObjectPoolConfig<Object> poolConfig) {
                        poolConfig.setMaxTotal(8);
                        poolConfig.setMaxIdle(8);
                        poolConfig.setMaxWaitMillis(2000L);
                    }
                }
                """,
                """
                import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

                import java.time.Duration;

                class JedisTransactionalConnectionStarvationFixture {
                    void configure(GenericObjectPoolConfig<Object> poolConfig) {
                        poolConfig.setMaxTotal(8);
                        poolConfig.setMaxIdle(8);
                        poolConfig.setMaxWait(Duration.ofMillis(2000L));
                    }
                }
                """));
    }

    @Test
    void durationOverloadsAndSameNamedApplicationMethodsAreNoop() {
        rewriteRun(
                java("""
                        import java.time.Duration;
                        import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
                        class Modern { void configure(GenericObjectPoolConfig<String> c) { c.setMaxWait(Duration.ofSeconds(2)); } }
                        """),
                java("""
                        class Config { void setMaxWaitMillis(long value) {} void setTimeBetweenEvictionRunsMillis(long value) {} }
                        class App { void configure(Config c) { c.setMaxWaitMillis(1); c.setTimeBetweenEvictionRunsMillis(2); } }
                        """));
    }

    @Test
    void generatedParentIsNoopAndLeafInstallIsMigratedIdempotently() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
                        class Generated { void x(GenericObjectPoolConfig<Object> c) { c.setMaxWaitMillis(1); } }
                        """, source -> source.path("generated-code/Generated.java")),
                java("""
                        import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
                        class install { void x(GenericObjectPoolConfig<Object> c) { c.setMaxWaitMillis(1); } }
                        """,
                        """
                        import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

                        import java.time.Duration;

                        class install { void x(GenericObjectPoolConfig<Object> c) {
                            c.setMaxWait(Duration.ofMillis(1)); } }
                        """, source -> source.path("install.java")));
    }
}
