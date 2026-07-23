package com.huawei.clouds.openrewrite.commonspool2;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class CommonsPool2JavaRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindCommonsPool2JavaRisks())
                .parser(JavaParser.fromJavaVersion().classpath("commons-pool2"));
    }

    @Test
    void marksBorrowCapacityAndConcurrentIdentityOperations() {
        rewriteRun(java("""
                import org.apache.commons.pool2.impl.GenericObjectPool;
                class Service {
                    void use(GenericObjectPool<String> pool, String value) throws Exception {
                        pool.borrowObject(250);
                        pool.addObject();
                        pool.invalidateObject(value);
                        pool.returnObject(value);
                        pool.clear();
                        pool.preparePool();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String out = after.printAll();
            assertEquals(6, count(out, "/*~~("), out);
            assertTrue(out.contains("spurious-wakeup"), out);
            assertTrue(out.contains("concurrent returnObject/invalidateObject"), out);
        })));
    }

    @Test
    void marksSaturationOrderingAndValidationPolicy() {
        rewriteRun(java("""
                import java.time.Duration;
                import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
                class Config {
                    void configure(GenericObjectPoolConfig<String> c) {
                        c.setMaxTotal(20); c.setMaxIdle(10); c.setMinIdle(2);
                        c.setBlockWhenExhausted(true); c.setLifo(false); c.setFairness(true); c.setMaxWait(Duration.ofSeconds(1));
                        c.setTestOnCreate(true); c.setTestOnBorrow(true); c.setTestOnReturn(true); c.setTestWhileIdle(true);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String out = after.printAll();
            assertEquals(11, count(out, "/*~~("), out);
            assertTrue(out.contains("saturation, ordering or wait policy"), out);
            assertTrue(out.contains("Validation policy"), out);
        })));
    }

    @Test
    void marksEvictionAndAbandonedObjectPolicy() {
        rewriteRun(java("""
                import java.time.Duration;
                import org.apache.commons.pool2.impl.AbandonedConfig;
                import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
                class Policy {
                    void configure(GenericObjectPoolConfig<String> c, AbandonedConfig a) {
                        c.setMinEvictableIdleDuration(Duration.ofSeconds(5));
                        c.setSoftMinEvictableIdleDuration(Duration.ofSeconds(2));
                        c.setTimeBetweenEvictionRuns(Duration.ofSeconds(1));
                        c.setNumTestsPerEvictionRun(3);
                        a.setRemoveAbandonedOnBorrow(true);
                        a.setRemoveAbandonedOnMaintenance(true);
                        a.setRemoveAbandonedTimeout(Duration.ofMinutes(2));
                        a.setLogAbandoned(true);
                        a.setUseUsageTracking(true);
                        a.setRequireFullStackTrace(false);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String out = after.printAll();
            assertEquals(10, count(out, "/*~~("), out);
            assertTrue(out.contains("Eviction scheduling/eligibility"), out);
            assertTrue(out.contains("replacement capacity"), out);
        })));
    }

    @Test
    void marksLegacyTimingAndDetailedStatistics() {
        rewriteRun(java("""
                import org.apache.commons.pool2.PooledObject;
                import org.apache.commons.pool2.impl.GenericObjectPool;
                import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
                class Metrics {
                    long sample(PooledObject<String> p, GenericObjectPool<String> pool, GenericObjectPoolConfig<String> c) {
                        c.setCollectDetailedStatistics(true);
                        pool.setMessagesStatistics(true);
                        return p.getActiveTimeMillis() + p.getIdleTimeMillis() + p.getLastUsedTime() +
                               pool.getMeanBorrowWaitTimeMillis() + pool.getMaxBorrowWaitTimeMillis();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String out = after.printAll();
            assertEquals(7, count(out, "/*~~("), out);
            assertTrue(out.contains("nanosecond precision"), out);
            assertTrue(out.contains("observability"), out);
        })));
    }

    @Test
    void marksCustomFactoryAndEvictionPolicyBoundaries() {
        rewriteRun(java("""
                import org.apache.commons.pool2.PooledObjectFactory;
                import org.apache.commons.pool2.impl.EvictionPolicy;
                abstract class Factory implements PooledObjectFactory<String> {}
                abstract class Policy implements EvictionPolicy<String> {}
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String out = after.printAll();
            assertEquals(2, count(out, "/*~~("), out);
            assertTrue(out.contains("Custom pool factory"), out);
            assertTrue(out.contains("Custom eviction policy"), out);
        })));
    }

    @Test
    void marksJedisBorrowReturnInvalidateLifecycleFixture() {
        // Extracted from redis/jedis@9ee063688ff88c3f156d595b18aed84f2aa2a3ec.
        rewriteRun(java("""
                import org.apache.commons.pool2.impl.GenericObjectPool;
                class JedisPoolFixture<T> extends GenericObjectPool<T> {
                    JedisPoolFixture(org.apache.commons.pool2.PooledObjectFactory<T> factory) { super(factory); }
                    T getResource() throws Exception { return super.borrowObject(); }
                    void returnResource(T value) { super.returnObject(value); }
                    void returnBrokenResource(T value) throws Exception { super.invalidateObject(value); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String out = after.printAll();
            assertTrue(out.contains("Custom pool subclass"), out);
            assertTrue(out.contains("spurious-wakeup"), out);
            assertEquals(2, count(out, "concurrent returnObject/invalidateObject"), out);
        })));
    }

    @Test
    void marksCommonsDbcpAbandonedPolicyMethodReferenceFixture() {
        // Extracted from apache/commons-dbcp@91c061be12b2a8a5b2e92567e567511ddd968386.
        rewriteRun(java("""
                import java.util.function.BiConsumer;
                import org.apache.commons.pool2.impl.AbandonedConfig;
                class DbcpFixture {
                    BiConsumer<AbandonedConfig, Boolean> policy = AbandonedConfig::setRemoveAbandonedOnBorrow;
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains("Abandoned-object policy callback")))));
    }

    @Test
    void sameNamedApplicationApisAndModernDurationReadsStayClean() {
        rewriteRun(java("""
                import org.apache.commons.pool2.PooledObject;
                class LocalPool { void borrowObject() {} void addObject() {} void setMaxWaitMillis(long n) {} }
                class App {
                    void local(LocalPool pool, PooledObject<String> pooled) {
                        pool.borrowObject(); pool.addObject(); pool.setMaxWaitMillis(1);
                        pooled.getActiveDuration(); pooled.getIdleDuration(); pooled.getCreateInstant();
                    }
                }
                """));
    }

    @Test
    void generatedParentIsNoopAndLeafSourceIsMarkedOnceAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.apache.commons.pool2.impl.GenericObjectPool;
                        class Generated { void x(GenericObjectPool<String> p) throws Exception { p.borrowObject(); } }
                        """, source -> source.path("target/generated-sources/Generated.java")),
                java("""
                        import org.apache.commons.pool2.impl.GenericObjectPool;
                        class install { void x(GenericObjectPool<String> p) throws Exception { p.borrowObject(); } }
                        """, source -> source.path("install.java").after(actual -> actual).afterRecipe(after ->
                        assertEquals(1, count(after.printAll(), "/*~~("), after.printAll()))));
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = 0; (i = text.indexOf(needle, i)) >= 0; i += needle.length()) count++;
        return count;
    }
}
