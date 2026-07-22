package com.huawei.clouds.openrewrite.okhttp;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

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

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(SOURCE))
                .parser(JavaParser.fromJavaVersion().dependsOn(OKHTTP_STUB, RETROFIT_CALL_STUB));
    }

    @Test
    void replacesRemovedOkHttpClientCloneWithBuilderDerivation() {
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
    void replacesCloneOnTypedMethodResult() {
        rewriteRun(java(
                """
                import okhttp3.OkHttpClient;
                class Registry {
                    OkHttpClient configured() { return new OkHttpClient(); }
                    OkHttpClient isolated() { return configured().clone(); }
                }
                """,
                """
                import okhttp3.OkHttpClient;
                class Registry {
                    OkHttpClient configured() { return new OkHttpClient(); }
                    OkHttpClient isolated() { return configured().newBuilder().build(); }
                }
                """
        ));
    }

    @Test
    void preservesRetrofitCallCloneFromRealRepository() {
        // Reduced from square/retrofit at d0b112dad073b7fe49c953ebc46ff1b424cb1e51.
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
    void marksRetrofitMockWebServerPackageFromFixedCommit() {
        // Reduced from square/retrofit at d0b112dad073b7fe49c953ebc46ff1b424cb1e51.
        // https://github.com/square/retrofit/blob/d0b112dad073b7fe49c953ebc46ff1b424cb1e51/retrofit-adapters/guava/src/test/java/retrofit2/adapter/guava/GuavaCallAdapterFactoryTest.java
        rewriteRun(
                spec -> spec.recipe(recipe(AUDIT)),
                text(
                        "import okhttp3.mockwebserver.MockWebServer;\nclass AdapterTest {}\n",
                        "import ~~>okhttp3.mockwebserver.MockWebServer;\nclass AdapterTest {}\n",
                        source -> source.path("retrofit-adapters/guava/src/test/java/AdapterTest.java")
                )
        );
    }

    @Test
    void marksInternalApiInheritanceCredentialsAndRemainingClone() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUDIT)),
                text(
                        """
                        import okhttp3.internal.Util;
                        class UnsafeClient extends OkHttpClient {
                          String auth(String user) { return Credentials.basic(user, null); }
                          OkHttpClient copy() { return client.clone(); }
                        }
                        """,
                        """
                        ~~>import okhttp3.internal.Util;
                        class UnsafeClient ~~>extends OkHttpClient {
                          String auth(String user) { return ~~>Credentials.basic(user, null); }
                          OkHttpClient copy() { return ~~>client.clone(); }
                        }
                        """,
                        source -> source.path("src/main/java/example/UnsafeClient.java")
                )
        );
    }

    @Test
    void marksKotlinLegacyApisAlphaApisAndConnectionBehavior() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUDIT)),
                text(
                        """
                        @ExperimentalOkHttpApi
                        class Client : OkHttpClient() {
                          val url = HttpUrl.parse(raw)
                          val body = RequestBody.create(mediaType, payload)
                          val listener = ConnectionListener()
                          val configured = OkHttpClient.Builder().dns(dns).certificatePinner(pinner)
                        }
                        """,
                        """
                        ~~>@ExperimentalOkHttpApi
                        class Client ~~>: OkHttpClient() {
                          val url = ~~>HttpUrl.parse(raw)
                          val body = ~~>RequestBody.create(mediaType, payload)
                          val listener = ~~>ConnectionListener()
                          val configured = OkHttpClient.Builder().~~>dns(dns).~~>certificatePinner(pinner)
                        }
                        """,
                        source -> source.path("src/main/kotlin/example/Client.kt")
                )
        );
    }

    @Test
    void strictUpgradePreservesSharedMavenProperty() {
        rewriteRun(
                spec -> spec.recipe(recipe(UPGRADE)),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>${network.version}</version>
                          <properties><network.version>4.9.3</network.version></properties>
                          <dependencies>
                            <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>${network.version}</version></dependency>
                          </dependencies>
                        </project>
                        """)
        );
    }

    @Test
    void strictUpgradePreservesExternalBomDynamicAndUnlistedVersions() {
        rewriteRun(
                spec -> spec.recipe(recipe(UPGRADE)),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>boundaries</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency>
                            <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp-jvm</artifactId><version>5.2.0</version></dependency>
                            <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId><version>[4.9,5)</version></dependency>
                          </dependencies>
                        </project>
                        """)
        );
    }

    @Test
    void fullMigrationCombinesDependencyAndSourceChanges() {
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
    void discoversAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{MIGRATION, SOURCE, AUDIT, COMPANIONS, UPGRADE, MAVEN_JVM}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> name + ": " + recipe.validate().failures());
        }
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
