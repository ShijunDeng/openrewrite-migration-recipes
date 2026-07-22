package com.huawei.clouds.openrewrite.jedis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeJedisTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.UpgradeJedisTo7_2_1";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.MigrateDeterministicJedisSourceTo7";
    private static final String RISK_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.FindManualJedis7MigrationRisks";
    private static final String BUILD_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.FindManualJedis7BuildBaselineRisks";
    private static final String AUDIT_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.AuditJedis7Compatibility";
    private static final String COMPOSITE_RECIPE =
            "com.huawei.clouds.openrewrite.jedis.MigrateJedisTo7_2_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
        spec.typeValidationOptions(TypeValidation.none());
        spec.parser(JavaParser.fromJavaVersion().dependsOn(
                """
                package redis.clients.jedis;
                public class BinaryJedis {
                    public BinaryJedis(java.net.URI uri) {}
                    public String set(byte[] key, byte[] value) { return null; }
                }
                """,
                """
                package redis.clients.jedis;
                public class BinaryJedisCluster {}
                """,
                """
                package redis.clients.jedis;
                public class Jedis {
                    public Jedis(String host) {}
                    public Jedis(String host, int port) {}
                    public Jedis(java.net.URI uri) {}
                    public void select(int database) {}
                    public byte[] get(byte[] key) { return null; }
                    public String get(String key) { return null; }
                    public String set(String key, String value) { return null; }
                    public java.util.List<String> blpop(int timeout, String key) { return null; }
                    public java.util.List<String> configGet(String pattern) { return null; }
                    public java.util.Set<String> zunion(Object params, String... keys) { return null; }
                    public Long scriptExists(byte[] script) { return null; }
                    public Object xpending(Object key, Object group, Object start, Object end, int count, Object consumer) { return null; }
                    public Object ftSearch(String index, Object query) { return null; }
                    public Object eval(String script) { return null; }
                    public Object evalsha(String sha) { return null; }
                    public Object pubsubNumSub(String... channels) { return null; }
                    public Object shutdown() { return null; }
                }
                """,
                """
                package redis.clients.jedis;
                public class JedisPool {
                    public JedisPool(String host) {}
                    public JedisPool(String host, int port) {}
                    public Jedis getResource() { return null; }
                    public void returnResource(Jedis jedis) {}
                    public void returnBrokenResource(Jedis jedis) {}
                }
                """,
                """
                package redis.clients.jedis;
                public class JedisCluster {
                    public JedisCluster(Object nodes, Object connectionTimeout, Object socketTimeout,
                                        Object maxAttempts, Object password, Object pool) {}
                    public Object getClusterNodes() { return null; }
                    public Object getConnectionFromSlot(int slot) { return null; }
                }
                """,
                """
                package redis.clients.jedis;
                public class Pipeline {
                    public Object multi() { return null; }
                    public Object exec() { return null; }
                    public Object discard() { return null; }
                }
                """,
                """
                package redis.clients.jedis;
                public class Transaction {
                    public Object execGetResponse() { return null; }
                    public Object watch(String key) { return null; }
                    public Object unwatch() { return null; }
                }
                """,
                """
                package redis.clients.jedis;
                public class ShardedJedisPool {}
                """,
                """
                package redis.clients.jedis;
                public class JedisPooled {
                    public static Builder builder() { return new Builder(); }
                    public String set(String key, String value) { return null; }
                    public String get(String key) { return null; }
                    public static class Builder {
                        public Builder hostAndPort(String host, int port) { return this; }
                        public JedisPooled build() { return null; }
                    }
                }
                """,
                """
                package redis.clients.jedis.params;
                public class SetParams {
                    public static SetParams setParams() { return null; }
                    public SetParams get() { return this; }
                }
                """,
                """
                package redis.clients.jedis.exceptions;
                public class JedisNoReachableClusterNodeException extends RuntimeException {}
                """,
                """
                package redis.clients.jedis.exceptions;
                public class JedisDataException extends RuntimeException {}
                """,
                """
                package org.apache.commons.pool2.impl;
                public class GenericObjectPoolConfig<T> {}
                """,
                """
                package redis.clients.jedis.graph;
                public class ResultSet {}
                """,
                """
                package redis.clients.jedis.providers;
                public interface ConnectionProvider {}
                """,
                """
                package redis.clients.jedis;
                public class HostAndPort {
                    public Object extractParts(String value) { return null; }
                    public Object parseString(String value) { return null; }
                }
                """,
                """
                package redis.clients.jedis;
                public class Connection {
                    public void quit() {}
                    public Object getRawObjectMultiBulkReply() { return null; }
                }
                """,
                """
                package redis.clients.jedis;
                public final class Protocol { public static final int DEFAULT_PORT = 6379; }
                """,
                """
                package redis.clients.jedis;
                public class ScanParams { public static final String SCAN_POINTER_START = "0"; }
                """,
                """
                package redis.clients.jedis;
                public class ScanResult<T> {}
                """,
                """
                package redis.clients.jedis;
                public abstract class PipelineBase {}
                """,
                """
                package redis.clients.jedis;
                public abstract class TransactionBase {}
                """,
                """
                package redis.clients.jedis;
                public class Tuple {}
                """,
                """
                package redis.clients.jedis;
                public enum BitOP { AND, OR, XOR, NOT }
                """
        ));
    }

    @ParameterizedTest(name = "upgrades explicit spreadsheet Jedis version {0}")
    @ValueSource(strings = {
            "2.8.0", "2.9.3", "2.10.2", "3.1.0", "3.5.2",
            "3.6.3", "3.7.0", "3.7.1", "3.8.0", "3.10.0"
    })
    void upgradesEveryExplicitSpreadsheetVersionInMaven(String version) {
        rewriteRun(pomXml(directPom(version), directPom("7.2.1")));
    }

    @Test
    void strictDependencyUpgradeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(directPom("3.8.0"), directPom("7.2.1"))
        );
    }

    @Test
    void sourceVersionWhitelistExactlyMatchesVisibleSpreadsheetValues() {
        assertEquals(Set.of(
                "2.8.0", "2.9.3", "2.10.2", "3.1.0", "3.5.2",
                "3.6.3", "3.7.0", "3.7.1", "3.8.0", "3.10.0"
        ), UpgradeSelectedJedisDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesDirectAndManagedMavenDeclarationsWithoutChangingCompanions() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>cache</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>3.10.0</version>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.25</version></dependency>
                    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-pool2</artifactId><version>2.6.2</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>cache</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>7.2.1</version>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.25</version></dependency>
                    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-pool2</artifactId><version>2.6.2</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @ParameterizedTest(name = "upgrades Gradle declaration {0}")
    @ValueSource(strings = {"2.8.0", "3.1.0", "3.8.0", "3.10.0"})
    void upgradesSelectedGradleStringDeclarations(String version) {
        rewriteRun(buildGradle(
                "plugins { id 'java' }\ndependencies { implementation 'redis.clients:jedis:" + version + "' }",
                "plugins { id 'java' }\ndependencies { implementation 'redis.clients:jedis:7.2.1' }"
        ));
    }

    @Test
    void upgradesSelectedGradleKotlinStringDeclaration() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradleKts(
                        "plugins { java }\ndependencies { implementation(\"redis.clients:jedis:3.7.1\") }",
                        "plugins { java }\ndependencies { implementation(\"redis.clients:jedis:7.2.1\") }"
                )
        );
    }

    @ParameterizedTest(name = "preserves unlisted Jedis version {0}")
    @ValueSource(strings = {
            "2.8.1", "2.10.1", "3.0.0", "3.6.2", "3.9.0", "4.4.8",
            "5.2.0", "6.2.0", "7.0.0", "7.2.0", "7.2.1", "7.3.0"
    })
    void preservesUnlistedAndTargetVersions(String version) {
        rewriteRun(pomXml(directPom(version)));
    }

    @Test
    void upgradesAnIsolatedMavenPropertyButPreservesOtherCoordinates() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>strict</artifactId><version>1</version>
                  <properties><jedis.version>3.8.0</jedis.version></properties>
                  <dependencies>
                    <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>${jedis.version}</version></dependency>
                    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId><version>3.8</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>strict</artifactId><version>1</version>
                  <properties><jedis.version>7.2.1</jedis.version></properties>
                  <dependencies>
                    <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>${jedis.version}</version></dependency>
                    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId><version>3.8</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesAnExclusiveProfileProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>legacy</id>
                    <properties><jedis.version>3.7.1</jedis.version></properties>
                    <dependencies><dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>${jedis.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile</artifactId><version>1</version>
                  <profiles><profile><id>legacy</id>
                    <properties><jedis.version>7.2.1</jedis.version></properties>
                    <dependencies><dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>${jedis.version}</version></dependency></dependencies>
                  </profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void preservesSharedMavenPropertyBomAndExternalReferences() {
        rewriteRun(pomXml(
                """
                <project description="uses ${jedis.version}">
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>strict</artifactId><version>1</version>
                  <properties><jedis.version>3.8.0</jedis.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>redis.clients</groupId><artifactId>jedis</artifactId>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>${jedis.version}</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesDuplicateMavenPropertyDefinitions() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicates</artifactId><version>1</version>
                  <properties><jedis.version>3.8.0</jedis.version></properties>
                  <dependencies><dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>${jedis.version}</version></dependency></dependencies>
                  <profiles><profile><id>other</id><properties><jedis.version>3.7.1</jedis.version></properties></profile></profiles>
                </project>
                """));
    }

    @Test
    void upgradesGroovyMapNotationOnlyForSelectedJedis() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                dependencies {
                  implementation group: 'redis.clients', name: 'jedis', version: '3.8.0'
                  implementation group: 'redis.clients', name: 'jedis', version: '4.4.8'
                  implementation group: 'example', name: 'jedis', version: '3.8.0'
                }
                """,
                """
                plugins { id 'java' }
                dependencies {
                  implementation group: 'redis.clients', name: 'jedis', version: '7.2.1'
                  implementation group: 'redis.clients', name: 'jedis', version: '4.4.8'
                  implementation group: 'example', name: 'jedis', version: '3.8.0'
                }
                """
        ));
    }

    @Test
    void preservesGradleMapVariantsAndSameNamedCallsOutsideDependenciesBlock() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                implementation 'redis.clients:jedis:3.8.0'
                dependencies {
                  generatedFixture { implementation 'redis.clients:jedis:3.8.0' }
                  implementation group: 'redis.clients', name: 'jedis', version: '3.8.0', classifier: 'tests'
                  implementation([group: 'redis.clients', name: 'jedis', version: '3.8.0', ext: 'zip'])
                }
                """
        ));
    }

    @Test
    void preservesGradleVariablesAndVersionCatalog() {
        rewriteRun(
                buildGradle(
                        """
                        plugins { id 'java' }
                        def jedisVersion = '3.8.0'
                        dependencies {
                          implementation "redis.clients:jedis:$jedisVersion"
                        }
                        """
                ),
                text(
                        """
                        [versions]
                        jedis = "3.8.0"
                        [libraries]
                        jedis = { module = "redis.clients:jedis", version.ref = "jedis" }
                        """,
                        source -> source.path("gradle/libs.versions.toml")
                )
        );
    }

    @Test
    void upgradesOnlySelectedEntryInMixedMavenFileAndProfile() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>3.8.0</version></dependency>
                    <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>4.4.8</version></dependency>
                  </dependencies>
                  <profiles><profile><id>legacy</id><dependencies><dependency>
                    <groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>2.10.2</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>7.2.1</version></dependency>
                    <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>4.4.8</version></dependency>
                  </dependencies>
                  <profiles><profile><id>legacy</id><dependencies><dependency>
                    <groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>7.2.1</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void leavesClassifierNonJarPluginAndGeneratedDeclarationsUntouched() {
        String pom = """
                <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>3.8.0</version><classifier>tests</classifier></dependency>
                    <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>3.8.0</version><type>test-jar</type></dependency>
                  </dependencies>
                  <build><plugins><plugin><groupId>x</groupId><artifactId>x</artifactId><dependencies><dependency>
                    <groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>3.8.0</version>
                  </dependency></dependencies></plugin></plugins></build>
                </project>
                """;
        rewriteRun(
                pomXml(pom),
                pomXml(directPom("3.8.0"), spec -> spec.path("target/generated/pom.xml")),
                buildGradle("dependencies { implementation 'redis.clients:jedis:3.8.0' }",
                        spec -> spec.path("build/generated.gradle"))
        );
    }

    @Test
    void preservesMavenDependencyShapedConfigurationAndAllGeneratedDirectories() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                          <build><plugins><plugin><groupId>x</groupId><artifactId>generator</artifactId><configuration><fixture>
                            <dependencies><dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>3.8.0</version></dependency></dependencies>
                          </fixture></configuration></plugin></plugins></build>
                        </project>
                        """),
                pomXml(directPom("3.8.0"), spec -> spec.path("src/generated/pom.xml")),
                buildGradle("dependencies { implementation 'redis.clients:jedis:3.8.0' }",
                        spec -> spec.path(".idea/generated/build.gradle"))
        );
    }

    @Test
    void migratesRedisInActionLiteralHostConstructor() {
        // Reduced from josiahcarlson/redis-in-action at 9ea2f986:
        // https://github.com/josiahcarlson/redis-in-action/blob/9ea2f9862faee248f53f663a8e3f6306327d352b/java/src/main/java/Chapter04.java#L16-L18
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.Jedis;

                        class Chapter04 {
                            void run() {
                                Jedis conn = new Jedis("localhost");
                                conn.select(15);
                            }
                        }
                        """,
                        """
                        import redis.clients.jedis.Jedis;

                        class Chapter04 {
                            void run() {
                                Jedis conn = new Jedis("localhost", redis.clients.jedis.Protocol.DEFAULT_PORT);
                                conn.select(15);
                            }
                        }
                        """,
                        source -> source.path("java/src/main/java/Chapter04.java")
                )
        );
    }

    @Test
    void migratesApacheSeataMovedScanTypes() {
        // Reduced from apache/incubator-seata at e6d0860a:
        // https://github.com/apache/incubator-seata/blob/e6d0860a4345b10cb59c65c78215ec51d67f59d1/discovery/seata-discovery-redis/src/main/java/org/apache/seata/discovery/registry/redis/RedisRegistryServiceImpl.java#L38-L39
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.ScanParams;
                        import redis.clients.jedis.ScanResult;

                        class RedisRegistryServiceImpl {
                            ScanParams params = new ScanParams();
                            ScanResult<String> result;
                        }
                        """,
                        """
                        import redis.clients.jedis.params.ScanParams;
                        import redis.clients.jedis.resps.ScanResult;

                        class RedisRegistryServiceImpl {
                            ScanParams params = new ScanParams();
                            ScanResult<String> result;
                        }
                        """,
                        source -> source.path("discovery/seata-discovery-redis/src/main/java/org/apache/seata/discovery/registry/redis/RedisRegistryServiceImpl.java")
                )
        );
    }

    @Test
    void migratesMemcachedSessionManagerBinaryJedis() {
        // Reduced from magro/memcached-session-manager at 716e147c:
        // https://github.com/magro/memcached-session-manager/blob/716e147c9840ab10298c4d2b9edd0662058331e6/core/src/main/java/de/javakaffee/web/msm/storage/RedisStorageClient.java
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import java.net.URI;
                        import redis.clients.jedis.BinaryJedis;

                        class RedisStorageClient {
                            BinaryJedis create(URI uri) { return new BinaryJedis(uri); }
                            byte[] get(BinaryJedis jedis, byte[] key) { return jedis.get(key); }
                        }
                        """,
                        """
                        import redis.clients.jedis.Jedis;

                        import java.net.URI;

                        class RedisStorageClient {
                            Jedis create(URI uri) { return new Jedis(uri); }
                            byte[] get(Jedis jedis, byte[] key) { return jedis.get(key); }
                        }
                        """,
                        source -> source.path("core/src/main/java/de/javakaffee/web/msm/storage/RedisStorageClient.java")
                )
        );
    }

    @Test
    void migratesPhantomThiefPipelineBase() {
        // Reduced from PhantomThief/jedis-helper at b531143e:
        // https://github.com/PhantomThief/jedis-helper/blob/b531143ee1ce6e94be6ce8e56d279f53d9faf3b6/src/main/java/com/github/phantomthief/jedis/JedisHelper.java
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.PipelineBase;
                        class JedisHelper { PipelineBase pipeline; }
                        """,
                        """
                        import redis.clients.jedis.AbstractPipeline;

                        class JedisHelper { AbstractPipeline pipeline; }
                        """,
                        source -> source.path("src/main/java/com/github/phantomthief/jedis/JedisHelper.java")
                )
        );
    }

    @Test
    void migratesAdditionalOfficialTypeMovesAndTransactionBase() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.BitOP;
                        import redis.clients.jedis.Tuple;
                        import redis.clients.jedis.TransactionBase;
                        class LegacyTypes { BitOP op; Tuple tuple; TransactionBase tx; }
                        """,
                        """
                        import redis.clients.jedis.AbstractTransaction;
                        import redis.clients.jedis.args.BitOP;
                        import redis.clients.jedis.resps.Tuple;

                        class LegacyTypes { BitOP op; Tuple tuple; AbstractTransaction tx; }
                        """
                ),
                java(
                        "import redis.clients.jedis.BinaryJedis; class Generated { BinaryJedis jedis; }",
                        source -> source.path("target/generated-sources/Generated.java")
                )
        );
    }

    @Test
    void sourceRecipePreservesUrisDynamicHostsOtherJedisAndBinaryPubSub() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.JedisPool;
                        import redis.clients.jedis.BinaryJedisPubSub;
                        class Boundaries {
                            Jedis uri = new Jedis("redis://localhost:6379");
                            Jedis dynamic = new Jedis(System.getenv("REDIS_URL"));
                            JedisPool secure = new JedisPool("rediss://localhost:6380");
                            BinaryJedisPubSub listener;
                        }
                        """
                ),
                java(
                        """
                        package com.example;
                        class Jedis { Jedis(String value) {} }
                        class NotRedis { Jedis value = new Jedis("localhost"); }
                        """
                )
        );
    }

    @Test
    void sourceRecipeDoesNotTrustWildcardImportsOverAttributedBusinessTypes() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        package example;
                        import redis.clients.jedis.*;
                        class Jedis { Jedis(String host) {} }
                        class Business { Jedis jedis = new Jedis("localhost"); }
                        """
                )
        );
    }

    @Test
    void sourceRecipeIsIdempotentForTargetTypesAndExplicitConstructor() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                java(
                        """
                        import redis.clients.jedis.AbstractPipeline;
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.Protocol;
                        import redis.clients.jedis.params.ScanParams;
                        import redis.clients.jedis.resps.ScanResult;
                        class Modern { Jedis jedis = new Jedis("localhost", Protocol.DEFAULT_PORT); }
                        """
                )
        );
    }

    @Test
    void marksRemovedShardingAndAmbiguousConstructors() {
        // ShardedJedisPool is reduced from PhantomThief/jedis-helper at b531143e:
        // https://github.com/PhantomThief/jedis-helper/blob/b531143ee1ce6e94be6ce8e56d279f53d9faf3b6/src/main/java/com/github/phantomthief/jedis/JedisHelper.java#L64-L66
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.ShardedJedisPool;
                        class Legacy {
                          ShardedJedisPool pool;
                          Jedis create(String redisEndpoint) { return new Jedis(redisEndpoint); }
                        }
                        """,
                        source -> source.path("src/main/java/example/Legacy.java")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "Jedis sharding APIs were removed");
                                    assertContains(after.printAll(), "one-string host constructor");
                                })
                )
        );
    }

    @Test
    void marksSslTimeoutPoolAndClusterConstruction() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.JedisCluster;
                        class ClusterConfig {
                          void open(Object nodes, Object connectionTimeout, Object socketTimeout,
                                    Object maxAttempts, Object password, int slot) {
                            GenericObjectPoolConfig<Jedis> config = new GenericObjectPoolConfig<>();
                            JedisCluster cluster = new JedisCluster(nodes, connectionTimeout, socketTimeout, maxAttempts, password, config);
                            cluster.getClusterNodes();
                            cluster.getConnectionFromSlot(slot);
                          }
                        }
                        """,
                        source -> source.path("src/main/java/example/ClusterConfig.java")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "pool values changed from Jedis to Connection");
                                    assertContains(after.printAll(), "multi-argument client constructor");
                                    assertContains(after.printAll(), "node/slot access now exposes");
                                })
                )
        );
    }

    @Test
    void unusedClusterImportDoesNotOwnAnUnrelatedPoolGeneric() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.JedisCluster;
                        class StandalonePoolConfig {
                          GenericObjectPoolConfig<Jedis> config = new GenericObjectPoolConfig<>();
                        }
                        """
                )
        );
    }

    @Test
    void marksPoolResourcePipelineAndTransactionBehavior() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.JedisPool;
                        import redis.clients.jedis.Pipeline;
                        import redis.clients.jedis.Transaction;
                        class Batch {
                          void run(JedisPool pool, Pipeline pipeline, Transaction transaction) {
                            Jedis jedis = pool.getResource();
                            pipeline.multi();
                            pipeline.exec();
                            transaction.execGetResponse();
                            transaction.watch("key");
                          }
                        }
                        """,
                        source -> source.path("src/main/java/example/Batch.java")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "pooled connection ownership boundary");
                                    assertContains(after.printAll(), "Pipeline transaction control was removed");
                                    assertContains(after.printAll(), "Transaction API changed");
                                })
                )
        );
    }

    @Test
    void marksChangedCommandReturnsAndBinaryScriptExists() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        import java.util.List;
                        import java.util.Set;
                        import redis.clients.jedis.Jedis;
                        class Returns {
                          void read(Jedis jedis, Object params, byte[] scriptBytes) {
                            List<String> popped = jedis.blpop(5, "queue");
                            List<String> config = jedis.configGet("*");
                            Set<String> union = jedis.zunion(params, "a", "b");
                            Long binary = jedis.scriptExists(scriptBytes);
                          }
                        }
                        """,
                        source -> source.path("src/main/java/example/Returns.java")
                                .after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(),
                                        "command crosses Jedis return/timeout signature changes"))
                )
        );
    }

    @Test
    void marksChangedExceptionsSetParamsAndXpending() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.params.SetParams;
                        import redis.clients.jedis.exceptions.JedisDataException;
                        import redis.clients.jedis.exceptions.JedisNoReachableClusterNodeException;
                        class Errors {
                          void run(Jedis jedis, Object key, Object group, Object start, Object end, Object consumer) {
                            try { command(); } catch (JedisDataException e) { recover(); }
                            SetParams params = SetParams.setParams().get();
                            jedis.xpending(key, group, start, end, 10, consumer);
                          }
                          void command() throws JedisNoReachableClusterNodeException {}
                          void recover() {}
                        }
                        """,
                        source -> source.path("src/main/java/example/Errors.java")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "exception contract was removed or consolidated");
                                    assertContains(after.printAll(), "SetParams.get() was removed");
                                    assertContains(after.printAll(), "XPENDING overload changed");
                                })
                )
        );
    }

    @Test
    void marksRemovedModulesSearchSemanticsAndInternalExtensions() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.Pipeline;
                        import redis.clients.jedis.graph.ResultSet;
                        import redis.clients.jedis.providers.ConnectionProvider;
                        class CustomPipeline extends Pipeline {}
                        class Extensions {
                          ResultSet result;
                          ConnectionProvider provider;
                          void search(Jedis jedis, Object query) { jedis.ftSearch("idx", query); }
                        }
                        """,
                        source -> source.path("src/main/java/example/Extensions.java")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "RedisGraph and RedisGears v2 support was removed");
                                    assertContains(after.printAll(), "internal Jedis boundary");
                                    assertContains(after.printAll(), "Search/module behavior changed");
                                })
                )
        );
    }

    @Test
    void marksOfficialHostConnectionAndCommandBehaviorChanges() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        import redis.clients.jedis.Connection;
                        import redis.clients.jedis.HostAndPort;
                        import redis.clients.jedis.Jedis;
                        class OfficialGuideShapes {
                          void run(Jedis jedis, HostAndPort endpoint, Connection connection) {
                            endpoint.parseString("localhost:6379");
                            connection.quit();
                            connection.getRawObjectMultiBulkReply();
                            jedis.eval("return 1");
                            jedis.pubsubNumSub("events");
                            jedis.shutdown();
                          }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "HostAndPort helper was removed");
                            assertContains(after.printAll(), "low-level Connection method was removed");
                            assertContains(after.printAll(), "Jedis command crosses a removed or behavior/return-type change");
                        })
                )
        );
    }

    @Test
    void riskRecipeLeavesModernStraightLineCommandsUnmarked() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        import redis.clients.jedis.JedisPooled;
                        class Modern {
                          void run() {
                            JedisPooled jedis = JedisPooled.builder().hostAndPort("localhost", 6379).build();
                            jedis.set("key", "value");
                            String value = jedis.get("key");
                          }
                        }
                        """,
                        source -> source.path("src/main/java/example/Modern.java")
                )
        );
    }

    @Test
    void leavesUnrelatedSameNamedJavaTypesAndMethodsUnmarked() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        package example;
                        class Pool { Object getResource() { return null; } }
                        class Jedis {
                          Object blpop(int timeout, String key) { return null; }
                          Object xpending(Object a, Object b, Object c, Object d, int e, Object f) { return null; }
                          Object ftSearch(String index, Object query) { return null; }
                        }
                        class Business {
                          void run(Pool pool, Jedis jedis) {
                            pool.getResource();
                            jedis.blpop(5, "queue");
                            jedis.xpending(1, 2, 3, 4, 5, 6);
                            jedis.ftSearch("idx", new Object());
                          }
                        }
                        """
                )
        );
    }

    @Test
    void exactRiskMarkersAreIdempotentAcrossCycles() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()).cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import redis.clients.jedis.Jedis;
                        class QueueReader { Object read(Jedis jedis) { return jedis.blpop(5, "queue"); } }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "command crosses Jedis return/timeout signature changes");
                            assertEquals(printed.indexOf("command crosses"), printed.lastIndexOf("command crosses"));
                        })
                )
        );
    }

    @Test
    void skipsGeneratedJavaRiskCandidates() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                java(
                        """
                        import redis.clients.jedis.Jedis;
                        class GeneratedQueueReader { Object read(Jedis jedis) { return jedis.blpop(5, "queue"); } }
                        """,
                        source -> source.path("target/generated-sources/GeneratedQueueReader.java")
                )
        );
    }

    @Test
    void recommendedRecipeUpgradesIsolatedPropertyWithoutFalseBuildMarker() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(COMPOSITE_RECIPE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>cache</artifactId><version>1</version>
                          <properties><jedis.version>3.8.0</jedis.version><maven.compiler.release>17</maven.compiler.release></properties>
                          <dependencies><dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>${jedis.version}</version></dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>cache</artifactId><version>1</version>
                          <properties><jedis.version>7.2.1</jedis.version><maven.compiler.release>17</maven.compiler.release></properties>
                          <dependencies><dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>${jedis.version}</version></dependency></dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void marksMavenJavaSlf4jAndCommonsPoolBaselines() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>build</artifactId><version>1</version>
                          <properties><maven.compiler.source>1.7</maven.compiler.source></properties>
                          <dependencies>
                            <dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>4.4.8</version></dependency>
                            <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.25</version></dependency>
                            <dependency><groupId>org.apache.commons</groupId><artifactId>commons-pool2</artifactId><version>2.6.2</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "requires Java 8 or newer");
                            assertContains(after.printAll(), "Jedis remains on an unselected");
                            assertContains(after.printAll(), "API/provider generation");
                            assertContains(after.printAll(), "Commons Pool owner");
                        })
                )
        );
    }

    @Test
    void buildRiskRequiresRealMavenDependencyOwnership() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>fixture</artifactId><version>1</version>
                          <properties><maven.compiler.source>1.7</maven.compiler.source></properties>
                          <build><plugins><plugin><artifactId>generator</artifactId><configuration><fixture>
                            <dependencies><dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>3.8.0</version></dependency></dependencies>
                          </fixture></configuration></plugin></plugins></build>
                        </project>
                        """)
        );
    }

    @Test
    void buildRiskMarksPluginOwnedJedisWithItsExactReason() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin-owner</artifactId><version>1</version>
                          <build><plugins><plugin><artifactId>generator</artifactId><dependencies><dependency>
                            <groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>3.8.0</version>
                          </dependency></dependencies></plugin></plugins></build>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "plugin-owned Jedis declaration"))
                )
        );
    }

    @Test
    void buildRiskMarkersAreIdempotentAcrossCycles() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()).cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(
                        directPom("4.4.8"),
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "Jedis remains on an unselected");
                            assertEquals(printed.indexOf("Jedis remains"), printed.lastIndexOf("Jedis remains"));
                        })
                )
        );
    }

    @Test
    void buildRiskResolvesAnExclusiveTargetProfileProperty() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profile-target</artifactId><version>1</version>
                          <profiles><profile><id>target</id>
                            <properties><jedis.version>7.2.1</jedis.version><maven.compiler.release>17</maven.compiler.release></properties>
                            <dependencies><dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>${jedis.version}</version></dependency></dependencies>
                          </profile></profiles>
                        </project>
                        """)
        );
    }

    @Test
    void marksGradleJavaSlf4jAndCommonsPoolBaselines() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                buildGradle(
                        """
                        java { sourceCompatibility = '1.7' }
                        dependencies {
                          implementation 'redis.clients:jedis:4.4.8'
                          implementation 'org.slf4j:slf4j-api:1.7.25'
                          implementation 'org.apache.commons:commons-pool2:2.6.2'
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "requires Java 8 or newer");
                            assertContains(after.printAll(), "Jedis remains on an unselected");
                            assertContains(after.printAll(), "API/provider generation");
                            assertContains(after.printAll(), "Commons Pool owner");
                        })
                )
        );
    }

    @Test
    void buildRiskMarksDynamicAndVariantGradleOwnersPrecisely() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                buildGradle(
                        """
                        plugins { id 'java' }
                        def jedisVersion = '3.8.0'
                        dependencies {
                          implementation "redis.clients:jedis:$jedisVersion"
                          testImplementation([group: 'redis.clients', name: 'jedis', version: '3.8.0', classifier: 'tests'])
                          implementation group: 'org.springframework.data', name: 'spring-data-redis', version: '2.7.18'
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "interpolated Jedis dependency");
                            assertContains(after.printAll(), "classifier, ext, or type-qualified Jedis map");
                            assertContains(after.printAll(), "Spring Data/Boot generation compatible with Jedis 7");
                        })
                )
        );
    }

    @Test
    void buildRiskMarksKotlinInterpolationAtTheDependencyOwner() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()).beforeRecipe(withToolingApi()),
                buildGradleKts(
                        """
                        plugins { java }
                        val jedisVersion = "3.8.0"
                        dependencies { implementation("redis.clients:jedis:$jedisVersion") }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "interpolated Jedis dependency"))
                )
        );
    }

    @Test
    void buildRiskIgnoresSameNamedGradleCallsOutsideDependenciesBlock() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                buildGradle("""
                        java { sourceCompatibility = '1.7' }
                        implementation 'redis.clients:jedis:3.8.0'
                        """)
        );
    }

    @Test
    void buildRiskRecipePreservesSupportedBaselineWithoutExplicitCompanions() {
        rewriteRun(
                spec -> spec.recipe(buildRiskRecipe()),
                pomXml(
                        "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>build</artifactId><version>1</version><properties><maven.compiler.release>17</maven.compiler.release></properties><dependencies><dependency><groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>7.2.1</version></dependency></dependencies></project>"
                ),
                buildGradle(
                        "java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }\ndependencies { implementation 'redis.clients:jedis:7.2.1' }"
                )
        );
    }

    @Test
    void compositeUpgradesDependencyMigratesSourceAndMarksBehaviorRisk() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(COMPOSITE_RECIPE)),
                pomXml(directPom("3.8.0"), directPom("7.2.1")),
                java(
                        """
                        import redis.clients.jedis.Jedis;
                        import redis.clients.jedis.ScanResult;
                        class Composite {
                          Jedis jedis = new Jedis("localhost");
                          void pop() { jedis.blpop(5, "queue"); }
                          ScanResult<String> result;
                        }
                        """,
                        source -> source.path("src/main/java/example/Composite.java")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    assertContains(after.printAll(), "redis.clients.jedis.resps.ScanResult");
                                    assertContains(after.printAll(), "Protocol.DEFAULT_PORT");
                                    assertContains(after.printAll(), "command crosses Jedis return/timeout signature changes");
                                })
                )
        );
    }

    @Test
    void discoversAndValidatesEveryRecipe() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe source = environment.activateRecipes(SOURCE_RECIPE);
        Recipe risks = environment.activateRecipes(RISK_RECIPE);
        Recipe buildRisks = environment.activateRecipes(BUILD_RISK_RECIPE);
        Recipe audit = environment.activateRecipes(AUDIT_RECIPE);
        Recipe composite = environment.activateRecipes(COMPOSITE_RECIPE);

        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> DEPENDENCY_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> SOURCE_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> RISK_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> BUILD_RISK_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> AUDIT_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> COMPOSITE_RECIPE.equals(recipe.getName())));
        assertEquals("Upgrade workbook-selected Jedis declarations to 7.2.1", dependency.getDisplayName());
        assertEquals("Migrate deterministic Jedis source constructs to version 7", source.getDisplayName());
        assertEquals("Find exact Jedis 7 Java migration risks", risks.getDisplayName());
        assertEquals("Find exact Jedis 7 build and platform risks", buildRisks.getDisplayName());
        assertEquals("Audit Jedis 7.2 compatibility without changing source", audit.getDisplayName());
        assertEquals("Migrate Jedis applications to 7.2.1", composite.getDisplayName());
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(source.validate().isValid(), () -> source.validate().failures().toString());
        assertTrue(risks.validate().isValid(), () -> risks.validate().failures().toString());
        assertTrue(buildRisks.validate().isValid(), () -> buildRisks.validate().failures().toString());
        assertTrue(audit.validate().isValid(), () -> audit.validate().failures().toString());
        assertTrue(composite.validate().isValid(), () -> composite.validate().failures().toString());
    }

    private static String directPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>jedis-app</artifactId><version>1</version>
                 <dependencies><dependency>
                   <groupId>redis.clients</groupId><artifactId>jedis</artifactId><version>%s</version>
                 </dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static Recipe sourceRecipe() {
        return environment().activateRecipes(SOURCE_RECIPE);
    }

    private static Recipe riskRecipe() {
        return environment().activateRecipes(RISK_RECIPE);
    }

    private static Recipe buildRiskRecipe() {
        return environment().activateRecipes(BUILD_RISK_RECIPE);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jedis")
                .scanYamlResources()
                .build();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected to find <" + expected + "> in:\n" + actual);
    }
}
