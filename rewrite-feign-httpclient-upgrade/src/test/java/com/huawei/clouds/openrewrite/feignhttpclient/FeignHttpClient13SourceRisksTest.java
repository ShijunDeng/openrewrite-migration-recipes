package com.huawei.clouds.openrewrite.feignhttpclient;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FeignHttpClient13SourceRisksTest implements RewriteTest {
    private static final String DEFAULT_CLIENT =
            "ApacheHttpClient() creates an internal HC4 client but exposes no close handle; verify singleton lifetime, shutdown, idle/expired eviction, DNS changes, connection reuse, and application stop behavior";
    private static final String OWNED_CLIENT =
            "This ApacheHttpClient receives a caller-owned HC4 HttpClient; ensure a CloseableHttpClient is closed exactly once by its real owner after Feign stops, and verify pool sharing, eviction, retries, cookies, and threads";
    private static final String BUILD_CLIENT =
            "HC4 HttpClientBuilder.build() creates closeable transport resources; identify the owner and close lifecycle, especially when this client is injected into Feign ApacheHttpClient";
    private static final String TIMEOUT =
            "Feign ApacheHttpClient 13.6 overrides HC4 connect/socket timeout and redirect enablement per request from Request.Options; verify connection-request timeout, zero/infinite values, units, pool starvation, and precedence";
    private static final String TLS =
            "Custom HC4 TLS configuration detected; verify trust/key material, hostname verification, protocols, ciphers, SNI, mTLS, certificate rotation, system properties, and reject trust-all/noop production settings";
    private static final String POOL =
            "HC4 connection-pool configuration detected; verify max total/per-route, lease timeout, stale validation, TTL, idle/expired eviction, DNS/TLS rotation, shared-manager ownership, and shutdown";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindFeignHttpClient13SourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath("feign-core", "feign-httpclient", "httpclient", "httpcore", "commons-codec"));
    }

    @Test
    void marksDefaultAdapterAtExactConstructor() {
        rewriteRun(java(
                "import feign.httpclient.ApacheHttpClient; class Transport { Object client() { return new ApacheHttpClient(); } }",
                "import feign.httpclient.ApacheHttpClient; class Transport { Object client() { return /*~~(%s)~~>*/new ApacheHttpClient(); } }".formatted(DEFAULT_CLIENT)));
    }

    @Test
    void marksCallerOwnedAdapterAtExactConstructor() {
        rewriteRun(java(
                """
                import feign.httpclient.ApacheHttpClient;
                import org.apache.http.client.HttpClient;
                class Transport { Object client(HttpClient hc4) { return new ApacheHttpClient(hc4); } }
                """,
                """
                import feign.httpclient.ApacheHttpClient;
                import org.apache.http.client.HttpClient;
                class Transport { Object client(HttpClient hc4) { return /*~~(%s)~~>*/new ApacheHttpClient(hc4); } }
                """.formatted(OWNED_CLIENT)));
    }

    @Test
    void marksRealCoinextBuilderOwnershipPattern() {
        // Reduced from coinext/silverstring-exchange at c8ce9255a054f85c7233f567e5e689da69ade03c.
        rewriteRun(java(
                """
                import feign.httpclient.ApacheHttpClient;
                import org.apache.http.impl.client.HttpClientBuilder;
                class FeignConfig {
                    Object client() {
                        HttpClientBuilder builder = HttpClientBuilder.create().setMaxConnPerRoute(300).setMaxConnTotal(300);
                        return new ApacheHttpClient(builder.build());
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, BUILD_CLIENT);
                    assertContains(out, OWNED_CLIENT);
                    assertCount(out, POOL, 2);
                })));
    }

    @Test
    void marksRealHmctsTimeoutAndRedirectConfiguration() {
        // Reduced from hmcts/idam-java-client at c529339353a33912e80b04a051ae8eac33e1ac3d.
        rewriteRun(java(
                """
                import org.apache.http.client.config.RequestConfig;
                import org.apache.http.impl.client.HttpClientBuilder;
                class TimeoutConfig {
                    Object client(int timeout) {
                        RequestConfig config = RequestConfig.custom()
                            .setConnectTimeout(timeout).setConnectionRequestTimeout(timeout).setSocketTimeout(timeout).build();
                        return HttpClientBuilder.create().useSystemProperties().disableRedirectHandling()
                            .setDefaultRequestConfig(config).build();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertCount(out, TIMEOUT, 4);
                    assertContains(out, "Request.Options.isFollowRedirects()");
                    assertContains(out, "proxy/credentials/system route");
                    assertContains(out, BUILD_CLIENT);
                })));
    }

    @Test
    void marksRequestConfigTimeoutAtActualInvocation() {
        rewriteRun(java(
                """
                import org.apache.http.client.config.RequestConfig;
                class Config { Object timeout() { return RequestConfig.custom().setConnectTimeout(1000).build(); } }
                """,
                """
                import org.apache.http.client.config.RequestConfig;
                class Config { Object timeout() { return /*~~(%s)~~>*/RequestConfig.custom().setConnectTimeout(1000).build(); } }
                """.formatted(TIMEOUT)));
    }

    @Test
    void marksRealEuGatewayTlsConfiguration() {
        // Reduced from eu-digital-green-certificates/dgc-gateway at
        // 913cafbcdc6a53be4be6b2113092535173352012.
        rewriteRun(java(
                """
                import javax.net.ssl.SSLContext;
                import org.apache.http.conn.ssl.DefaultHostnameVerifier;
                import org.apache.http.impl.client.HttpClientBuilder;
                class Mtls { Object client(SSLContext context) { return HttpClientBuilder.create()
                    .setSSLContext(context).setSSLHostnameVerifier(new DefaultHostnameVerifier()).build(); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertCount(after.printAll(), TLS, 2);
                    assertContains(after.printAll(), BUILD_CLIENT);
                })));
    }

    @Test
    void marksProxyCredentialsAndSystemProperties() {
        rewriteRun(java(
                """
                import org.apache.http.HttpHost;
                import org.apache.http.impl.client.HttpClientBuilder;
                import org.apache.http.impl.client.BasicCredentialsProvider;
                class ProxyConfig { Object client() { return HttpClientBuilder.create().useSystemProperties()
                    .setProxy(new HttpHost("proxy", 8080)).setDefaultCredentialsProvider(new BasicCredentialsProvider()).build(); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), "proxy/credentials/system route", 3))));
    }

    @Test
    void marksCompressionHeadersRetriesAndInterceptors() {
        rewriteRun(java(
                """
                import org.apache.http.HttpRequestInterceptor;
                import org.apache.http.impl.client.HttpClientBuilder;
                class BodyConfig { Object client(HttpRequestInterceptor audit) { return HttpClientBuilder.create()
                    .disableContentCompression().disableAutomaticRetries().addInterceptorFirst(audit).build(); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), "Content-Length/Transfer-Encoding", 3))));
    }

    @Test
    void marksPoolingManagerConstructionAndSettings() {
        rewriteRun(java(
                """
                import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
                class PoolConfig { Object pool() { PoolingHttpClientConnectionManager p = new PoolingHttpClientConnectionManager();
                    p.setMaxTotal(200); p.setDefaultMaxPerRoute(50); p.setValidateAfterInactivity(1000); return p; } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), POOL, 4))));
    }

    @Test
    void marksCustomHttpRequestInterceptorAtImplementedType() {
        rewriteRun(java(
                """
                import java.io.IOException;
                import org.apache.http.HttpException;
                import org.apache.http.HttpRequest;
                import org.apache.http.HttpRequestInterceptor;
                import org.apache.http.protocol.HttpContext;
                class Audit implements HttpRequestInterceptor {
                    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException { }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "Custom HC4 client/interceptor extension detected");
                    assertContains(out, "implements /*~~(Custom HC4 client/interceptor extension detected");
                })));
    }

    @Test
    void marksCustomCloseableClientAtExtendedType() {
        rewriteRun(java(
                "import org.apache.http.impl.client.CloseableHttpClient; abstract class CustomClient extends CloseableHttpClient { }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "extends /*~~(Custom HC4 client/interceptor extension detected");
                    assertCount(after.printAll(), "Custom HC4 client/interceptor extension detected", 1);
                })));
    }

    @Test
    void classifiesDefaultSocketConfigAsRequestTimeoutTransportNotTls() {
        rewriteRun(java(
                "import org.apache.http.config.SocketConfig; import org.apache.http.impl.client.HttpClientBuilder; class SocketDefaults { Object client() { return HttpClientBuilder.create().setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(1000).build()).build(); } }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertContains(printed, TIMEOUT);
                    assertFalse(printed.contains(TLS), printed);
                })));
    }

    @Test
    void sameNamedApplicationApisAreNoop() {
        rewriteRun(java("""
                class HttpClientBuilder { HttpClientBuilder setProxy(Object p) { return this; } Object build() { return null; } }
                class ApacheHttpClient { ApacheHttpClient() { } }
                class Use { Object x() { return new HttpClientBuilder().setProxy("x").build(); } }
                """));
    }

    @Test
    void generatedInstallAndCachesAreNoop() {
        rewriteRun(
                java("import feign.httpclient.ApacheHttpClient; class GeneratedClient { Object x(){ return new ApacheHttpClient(); } }", source -> source.path("generated-code/GeneratedClient.java")),
                java("import feign.httpclient.ApacheHttpClient; class InstalledClient { Object x(){ return new ApacheHttpClient(); } }", source -> source.path("installation/lib/InstalledClient.java")),
                java("import feign.httpclient.ApacheHttpClient; class CachedClient { Object x(){ return new ApacheHttpClient(); } }", source -> source.path(".m2/cache/CachedClient.java")));
    }

    @Test
    void installLeafFilenameIsStillMarked() {
        rewriteRun(java(
                "import feign.httpclient.ApacheHttpClient; class install { Object x(){ return new ApacheHttpClient(); } }",
                "import feign.httpclient.ApacheHttpClient; class install { Object x(){ return /*~~(%s)~~>*/new ApacheHttpClient(); } }".formatted(DEFAULT_CLIENT),
                source -> source.path("install.java")));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected + "> but found " + result + " in:\n" + actual);
    }
}
