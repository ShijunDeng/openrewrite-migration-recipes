package com.huawei.clouds.openrewrite.feignhttpclient;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FeignHttpClient13ApiMigrationTest implements RewriteTest {
    private static final String DEFAULT_CLIENT =
            "ApacheHttpClient() creates an internal HC4 client but exposes no close handle; verify singleton lifetime, shutdown, idle/expired eviction, DNS changes, connection reuse, and application stop behavior";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateFeignHttpClient13Apis())
                .parser(JavaParser.fromJavaVersion().classpath("feign-core", "feign-httpclient", "httpclient", "httpcore"));
    }

    @Test
    void renamesDecode404OnApacheClientBuilderChain() {
        rewriteRun(java(
                """
                import feign.Feign;
                import feign.httpclient.ApacheHttpClient;
                interface Api {}
                class Factory {
                    Api create() {
                        return Feign.builder().client(new ApacheHttpClient()).decode404()
                            .target(Api.class, "https://example.test");
                    }
                }
                """,
                """
                import feign.Feign;
                import feign.httpclient.ApacheHttpClient;
                interface Api {}
                class Factory {
                    Api create() {
                        return Feign.builder().client(new ApacheHttpClient()).dismiss404()
                            .target(Api.class, "https://example.test");
                    }
                }
                """));
    }

    @Test
    void preservesTransportAndOptionsChainOrder() {
        rewriteRun(java(
                """
                import feign.Feign;
                import feign.Request;
                import feign.httpclient.ApacheHttpClient;
                class Wiring {
                    Object builder() {
                        return Feign.builder().options(new Request.Options(1000, 2000))
                            .client(new ApacheHttpClient()).decode404().logLevel(feign.Logger.Level.BASIC);
                    }
                }
                """,
                """
                import feign.Feign;
                import feign.Request;
                import feign.httpclient.ApacheHttpClient;
                class Wiring {
                    Object builder() {
                        return Feign.builder().options(new Request.Options(1000, 2000))
                            .client(new ApacheHttpClient()).dismiss404().logLevel(feign.Logger.Level.BASIC);
                    }
                }
                """));
    }

    @Test
    void sameNamedApplicationBuilderIsNoop() {
        rewriteRun(java("class LocalBuilder { LocalBuilder decode404() { return this; } void use() { decode404(); } }"));
    }

    @Test
    void targetSpellingIsNoop() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                """
                package feign;
                public class Feign {
                    public static Builder builder() { return new Builder(); }
                    public static class Builder { public Builder dismiss404() { return this; } }
                }
                """)), java("import feign.Feign; class Current { Object builder() { return Feign.builder().dismiss404(); } }"));
    }

    @Test
    void generatedAndInstalledTreesAreNoop() {
        rewriteRun(
                java("import feign.Feign; class Generated { Object b() { return Feign.builder().decode404(); } }",
                        source -> source.path("generatedClients/Generated.java")),
                java("import feign.Feign; class Installed { Object b() { return Feign.builder().decode404(); } }",
                        source -> source.path("installation/cache/Installed.java")));
    }

    @Test
    void installLeafFilenameIsMigrated() {
        rewriteRun(java(
                "import feign.Feign; class install { Object b() { return Feign.builder().decode404(); } }",
                "import feign.Feign; class install { Object b() { return Feign.builder().dismiss404(); } }",
                source -> source.path("install.java")));
    }

    @Test
    void deterministicRenameIsTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import feign.Feign; class Client { Object b() { return Feign.builder().decode404(); } }",
                "import feign.Feign; class Client { Object b() { return Feign.builder().dismiss404(); } }"));
    }

    @Test
    void recommendedRecipeReusesPublicUpgradeThenRunsAutoAndMarker() {
        rewriteRun(spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feignhttpclient").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.feignhttpclient.MigrateFeignHttpClientTo13_6")),
                java(
                        """
                        import feign.Feign;
                        import feign.httpclient.ApacheHttpClient;
                        class Client { Object b() { return Feign.builder().client(new ApacheHttpClient()).decode404(); } }
                        """,
                        """
                        import feign.Feign;
                        import feign.httpclient.ApacheHttpClient;
                        class Client { Object b() { return Feign.builder().client(/*~~(%s)~~>*/new ApacheHttpClient()).dismiss404(); } }
                        """.formatted(DEFAULT_CLIENT)));
    }

    @Test
    void lowLevelPublicUpgradeNeverTouchesSource() {
        rewriteRun(spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feignhttpclient").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.feignhttpclient.UpgradeFeignHttpClientTo13_6")),
                java("""
                        import feign.Feign;
                        import feign.httpclient.ApacheHttpClient;
                        class Client { Object b() { return Feign.builder().client(new ApacheHttpClient()).decode404(); } }
                        """));
    }
}
