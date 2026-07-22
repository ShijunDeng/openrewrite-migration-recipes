package com.huawei.clouds.openrewrite.okhttp;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class OkHttpMigrationTest implements RewriteTest {
    private static final String MIGRATION = "com.huawei.clouds.openrewrite.okhttp.MigrateOkHttpTo5_3_0";
    private static final String SOURCE = "com.huawei.clouds.openrewrite.okhttp.MigrateDeterministicOkHttpSourceTo5";
    private static final String AUDIT = "com.huawei.clouds.openrewrite.okhttp.FindManualOkHttp5SourceRisks";
    private static final String COMPANIONS = "com.huawei.clouds.openrewrite.okhttp.FindOkHttp5CompanionDependencyRisks";
    private static final String UPGRADE = "com.huawei.clouds.openrewrite.okhttp.UpgradeOkHttpTo5_3_0";
    private static final String MAVEN_JVM = "com.huawei.clouds.openrewrite.okhttp.MigrateMavenJvmOkHttpTo5_3_0";

    private static final String OKHTTP_STUB = """
            package okhttp3;
            public class OkHttpClient implements Cloneable {
                @Override public OkHttpClient clone() { return this; }
                public Builder newBuilder() { return new Builder(); }
                public static class Builder { public OkHttpClient build() { return new OkHttpClient(); } }
            }
            """;
    private static final String RETROFIT_CALL_STUB = """
            package retrofit2;
            public interface Call<T> extends Cloneable { Call<T> clone(); }
            """;
    private static final String KOTLIN_FACTORY_STUB = """
            package okhttp3
            class HttpUrl {
                companion object {
                    @JvmStatic fun get(value: String): HttpUrl = HttpUrl()
                    @JvmStatic fun parse(value: String): HttpUrl? = null
                }
            }
            """;
    private static final String MEDIA_TYPE_STUB = """
            package okhttp3
            class MediaType {
                companion object {
                    @JvmStatic fun get(value: String): MediaType = MediaType()
                    @JvmStatic fun parse(value: String): MediaType? = null
                }
            }
            """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(SOURCE))
                .parser(JavaParser.fromJavaVersion().dependsOn(OKHTTP_STUB, RETROFIT_CALL_STUB));
    }

    @Test
    void replacesTypeAttributedOkHttpClientCloneWithBuilderDerivation() {
        rewriteRun(java(
                """
                import okhttp3.OkHttpClient;
                class ClientFactory {
                    OkHttpClient copy(OkHttpClient client) {
                        return client.clone();
                    }
                }
                """,
                """
                import okhttp3.OkHttpClient;
                class ClientFactory {
                    OkHttpClient copy(OkHttpClient client) {
                        return client.newBuilder().build();
                    }
                }
                """
        ));
    }

    @Test
    void migratesDeterministicKotlinFactoriesAndPreservesNullSemantics() {
        rewriteRun(
                spec -> spec.recipe(recipe(SOURCE)).cycles(2).expectedCyclesThatMakeChanges(1)
                        .parser(KotlinParser.builder().dependsOn(KOTLIN_FACTORY_STUB, MEDIA_TYPE_STUB)),
                kotlin(
                        """
                        import okhttp3.HttpUrl
                        import okhttp3.MediaType

                        class Parser {
                            fun url(raw: String) = HttpUrl.get(raw)
                            fun nullableUrl(raw: String) = HttpUrl.parse(raw)
                            fun media(raw: String) = MediaType.get(raw)
                            fun nullableMedia(raw: String) = MediaType.parse(raw)
                        }
                        """,
                        """
                        import okhttp3.HttpUrl
                        import okhttp3.HttpUrl.Companion.toHttpUrl
                        import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
                        import okhttp3.MediaType
                        import okhttp3.MediaType.Companion.toMediaType
                        import okhttp3.MediaType.Companion.toMediaTypeOrNull

                        class Parser {
                            fun url(raw: String) = raw.toHttpUrl()
                            fun nullableUrl(raw: String) = raw.toHttpUrlOrNull()
                            fun media(raw: String) = raw.toMediaType()
                            fun nullableMedia(raw: String) = raw.toMediaTypeOrNull()
                        }
                        """
                )
        );
    }

    @Test
    void preservesRetrofitCallCloneFromRealRepository() {
        // Reduced from square/retrofit at d0b112dad073b7fe49c953ebc46ff1b424cb1e51:
        // https://github.com/square/retrofit/blob/d0b112dad073b7fe49c953ebc46ff1b424cb1e51/retrofit-adapters/rxjava/src/main/java/retrofit2/adapter/rxjava/CallExecuteOnSubscribe.java
        rewriteRun(java(
                """
                import retrofit2.Call;
                class CallExecuteOnSubscribe<T> {
                    private final Call<T> originalCall;
                    CallExecuteOnSubscribe(Call<T> originalCall) { this.originalCall = originalCall; }
                    void run() { Call<T> call = originalCall.clone(); }
                }
                """
        ));
    }

    @Test
    void preservesUnrelatedCloneMethods() {
        rewriteRun(java(
                """
                class LocalClient implements Cloneable {
                    public LocalClient clone() { return this; }
                    LocalClient copy() { return clone(); }
                }
                """
        ));
    }

    @Test
    void marksAttributedJavaRisksWithDecisionSpecificReasons() {
        // OkHttp 3.14.9's fixed source implements Cloneable, exposes newBuilder(), and contains internal APIs:
        // https://github.com/square/okhttp/blob/ad97bd3df34376eec85aa187dc8f45cfde8a2c01/okhttp/src/main/java/okhttp3/OkHttpClient.java#L123
        rewriteRun(
                spec -> spec.recipe(recipe(AUDIT)).parser(JavaParser.fromJavaVersion().dependsOn(
                        "package okhttp3.internal; public class Util {}",
                        "package okhttp3; public class OkHttpClient { public static class Builder { public Builder dns(Object value) { return this; } } }",
                        "package okhttp3; public class Credentials { public static String basic(String user, String password) { return user; } }",
                        "package okhttp3; public @interface ExperimentalOkHttpApi {}",
                        "package okhttp3; public class ConnectionListener {}",
                        "package okhttp3.mockwebserver; public class MockWebServer {}"
                )),
                java(
                        """
                        import okhttp3.ConnectionListener;
                        import okhttp3.Credentials;
                        import okhttp3.ExperimentalOkHttpApi;
                        import okhttp3.OkHttpClient;
                        import okhttp3.internal.Util;
                        import okhttp3.mockwebserver.MockWebServer;

                        @ExperimentalOkHttpApi
                        class UnsafeClient extends OkHttpClient {
                            ConnectionListener listener;
                            MockWebServer server;
                            String auth(String user) { return Credentials.basic(user, null); }
                            void configure(Object dns) { new OkHttpClient.Builder().dns(dns); }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "okhttp3.internal is not a public API");
                            assertContains(printed, "OkHttpClient accessors became final");
                            assertContains(printed, "Credentials.basic username and password are non-null");
                            assertContains(printed, "obsolete JUnit 4-compatible module");
                            assertContains(printed, "experimental API crossed multiple incompatible alpha revisions");
                            assertContains(printed, "dual-stack fast fallback");
                        })
                )
        );
    }

    @Test
    void marksRealRetrofitMockWebServerPackageFromFixedCommit() {
        // Reduced from square/retrofit at d0b112dad073b7fe49c953ebc46ff1b424cb1e51:
        // https://github.com/square/retrofit/blob/d0b112dad073b7fe49c953ebc46ff1b424cb1e51/retrofit-adapters/guava/src/test/java/retrofit2/adapter/guava/GuavaCallAdapterFactoryTest.java
        rewriteRun(
                spec -> spec.recipe(recipe(AUDIT)).parser(JavaParser.fromJavaVersion().dependsOn(
                        "package okhttp3.mockwebserver; public class MockWebServer {}"
                )),
                java(
                        """
                        import okhttp3.mockwebserver.MockWebServer;
                        class AdapterTest { MockWebServer server; }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "obsolete JUnit 4-compatible module"))
                )
        );
    }

    @Test
    void marksKotlinAmbiguitiesAfterDeterministicFactoriesAreMigrated() {
        rewriteRun(
                spec -> spec.recipe(recipe(MIGRATION)).parser(KotlinParser.builder().dependsOn(
                        KOTLIN_FACTORY_STUB,
                        """
                        package okhttp3
                        class RequestBody {
                            companion object {
                                @JvmStatic fun create(type: Any, value: String): RequestBody = RequestBody()
                            }
                        }
                        """
                )),
                kotlin(
                        """
                        import okhttp3.HttpUrl
                        import okhttp3.RequestBody

                        class KotlinClient {
                            fun url(raw: String) = HttpUrl.parse(raw)
                            fun body(type: Any, value: String) = RequestBody.create(type, value)
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "raw.toHttpUrlOrNull()");
                            assertContains(printed, "Java-style static factory is not Kotlin source-compatible");
                        })
                )
        );
    }

    @Test
    void ignoresSameNamedApplicationApis() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUDIT)),
                java(
                        """
                        class Credentials { static String basic(String user, String password) { return user; } }
                        class Builder { Builder dns(Object dns) { return this; } }
                        class LocalClient extends Builder {
                            String auth(String user) { return Credentials.basic(user, null); }
                            void configure(Object dns) { new Builder().dns(dns); }
                        }
                        """
                )
        );
    }

    @Test
    void marksOwnedBuildRisksAndProtectsPluginsNestedDslAndVariants() {
        rewriteRun(
                spec -> spec.recipe(recipe(COMPANIONS)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>build-risks</artifactId><version>1</version><dependencies>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>[4.9,5)</version></dependency>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version><classifier>tests</classifier></dependency>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>5.2.0</version></dependency>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>logging-interceptor</artifactId><version>4.11.0</version></dependency>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>mockwebserver</artifactId><version>5.3.0</version></dependency>
                          <dependency><groupId>com.squareup.okio</groupId><artifactId>okio</artifactId><version>3.9.0</version></dependency>
                          <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId><version>1.9.23</version></dependency>
                        </dependencies></project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "outside the workbook selection");
                            assertContains(printed, "inherited or managed externally");
                            assertContains(printed, "indirect, dynamic, ranged");
                            assertContains(printed, "nonstandard variant");
                            assertContains(printed, "companion is not aligned");
                            assertContains(printed, "obsolete JUnit 4-compatible API");
                            assertContains(printed, "explicit Okio version");
                            assertContains(printed, "explicit Kotlin standard library version");
                        })
                ),
                buildGradle(
                        """
                        dependencies {
                            implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'
                            runtimeOnly 'com.squareup.okhttp3:okhttp:4.+'
                            compileOnly 'com.squareup.okhttp3:okhttp'
                            constraints { implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0' }
                            implementation group: 'com.squareup.okhttp3', name: 'logging-interceptor', version: '4.11.0', classifier: 'tests'
                        }
                        fake { dependencies { implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0' } }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "companion is not aligned");
                            assertContains(printed, "indirect, dynamic, ranged");
                            assertContains(printed, "inherited or managed externally");
                            assertContains(printed, "nonstandard variant");
                        })
                ),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin</artifactId><version>1</version><build><plugins><plugin>
                          <groupId>example</groupId><artifactId>tool</artifactId><dependencies><dependency>
                            <groupId>com.squareup.okhttp3</groupId><artifactId>logging-interceptor</artifactId><version>4.11.0</version>
                          </dependency></dependencies>
                        </plugin></plugins></build></project>
                        """,
                        source -> source.path("plugin/pom.xml")
                )
        );
    }

    @Test
    void marksParsedConfigurationPrecisely() {
        rewriteRun(
                spec -> spec.recipe(new FindOkHttp5ConfigurationRisks()),
                properties(
                        "okhttp.reflective-class=okhttp3.internal.Util\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "strongly encapsulated"))
                ),
                yaml(
                        "testClass: okhttp3.mockwebserver.MockWebServer\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "obsolete okhttp3.mockwebserver package"))
                ),
                xml(
                        "<configuration><class>okhttp3/internal/Util</class></configuration>",
                        source -> source.path("config.xml").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "strongly encapsulated"))
                ),
                properties(
                        "unrelated=example.okhttp3.internalized.Util\n# value=okhttp3.internal.Util\n"
                ),
                properties(
                        "value=okhttp3.internal.Util\n",
                        source -> source.path("install/generated/application.properties")
                )
        );
    }

    @Test
    void fullMigrationCombinesDependencyAndJavaChanges() {
        rewriteRun(
                spec -> spec.recipe(recipe(MIGRATION)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>5.3.0</version></dependency>
                        </dependencies></project>
                        """
                ),
                java(
                        """
                        import okhttp3.OkHttpClient;
                        class Clients { OkHttpClient copy(OkHttpClient client) { return client.clone(); } }
                        """,
                        """
                        import okhttp3.OkHttpClient;
                        class Clients { OkHttpClient copy(OkHttpClient client) { return client.newBuilder().build(); } }
                        """
                )
        );
    }

    @Test
    void recommendedRecipeSkipsGeneratedAndInstalledInputs() {
        rewriteRun(
                spec -> spec.recipe(recipe(MIGRATION)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>generated</artifactId><version>1</version><dependencies>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version></dependency>
                        </dependencies></project>
                        """,
                        source -> source.path("target/generated-poms/pom.xml")
                ),
                java(
                        """
                        import okhttp3.OkHttpClient;
                        class Generated { OkHttpClient copy(OkHttpClient client) { return client.clone(); } }
                        """,
                        source -> source.path("build/generated/sources/Generated.java")
                ),
                properties(
                        "value=okhttp3.internal.Util\n",
                        source -> source.path("install/config/application.properties")
                )
        );
    }

    @Test
    void recommendedAutoAndMarkersAreStableAcrossTwoCycles() {
        rewriteRun(
                spec -> spec.recipe(recipe(MIGRATION)).cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>stable</artifactId><version>1</version><dependencies>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>4.11.0</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>stable</artifactId><version>1</version><dependencies>
                          <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>5.3.0</version></dependency>
                        </dependencies></project>
                        """
                ),
                java(
                        """
                        import okhttp3.OkHttpClient;
                        class Stable { OkHttpClient copy(OkHttpClient client) { return client.clone(); } }
                        """,
                        """
                        import okhttp3.OkHttpClient;
                        class Stable { OkHttpClient copy(OkHttpClient client) { return client.newBuilder().build(); } }
                        """
                ),
                properties(
                        "class=okhttp3.internal.Util\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "strongly encapsulated"))
                )
        );
    }

    @Test
    void discoversAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{MIGRATION, SOURCE, AUDIT, COMPANIONS, UPGRADE, MAVEN_JVM}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> name + ": " + recipe.validate().failures());
        }
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected to find <" + expected + "> in:\n" + actual);
    }

    private static Recipe recipe(String name) {
        return environment().activateRecipes(name);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.okhttp")
                .scanYamlResources()
                .build();
    }
}
