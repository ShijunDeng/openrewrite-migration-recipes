package com.huawei.clouds.openrewrite.feignhttpclient;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate HC4 transport decisions that cannot be inferred from dependency versions alone. */
public final class FindFeignHttpClient13SourceRisks extends Recipe {
    private static final String DEFAULT_CLIENT =
            "ApacheHttpClient() creates an internal HC4 client but exposes no close handle; verify singleton lifetime, " +
            "shutdown, idle/expired eviction, DNS changes, connection reuse, and application stop behavior";
    private static final String OWNED_CLIENT =
            "This ApacheHttpClient receives a caller-owned HC4 HttpClient; ensure a CloseableHttpClient is closed exactly " +
            "once by its real owner after Feign stops, and verify pool sharing, eviction, retries, cookies, and threads";
    private static final String BUILD_CLIENT =
            "HC4 HttpClientBuilder.build() creates closeable transport resources; identify the owner and close lifecycle, " +
            "especially when this client is injected into Feign ApacheHttpClient";
    private static final String TIMEOUT =
            "Feign ApacheHttpClient 13.6 overrides HC4 connect/socket timeout and redirect enablement per request from " +
            "Request.Options; verify connection-request timeout, zero/infinite values, units, pool starvation, and precedence";
    private static final String REDIRECT =
            "Feign 12.2+ applies Request.Options.isFollowRedirects() per request; verify this HC4 redirect policy/strategy, " +
            "maximum/circular redirects, authorization stripping, method/body replay, and cross-host behavior";
    private static final String TLS =
            "Custom HC4 TLS configuration detected; verify trust/key material, hostname verification, protocols, ciphers, " +
            "SNI, mTLS, certificate rotation, system properties, and reject trust-all/noop production settings";
    private static final String PROXY =
            "Custom HC4 proxy/credentials/system route configuration detected; verify proxy authentication, nonProxyHosts, " +
            "CONNECT tunneling, redirects, TLS hostname checks, environment precedence, and secret handling";
    private static final String COMPRESSION_BODY =
            "HC4 compression/header/interceptor customization can change Feign request bodies or response decoding; verify " +
            "Content-Length/Transfer-Encoding, gzip/br, multipart/stream replay, interceptor order, and body closure";
    private static final String POOL =
            "HC4 connection-pool configuration detected; verify max total/per-route, lease timeout, stale validation, TTL, " +
            "idle/expired eviction, DNS/TLS rotation, shared-manager ownership, and shutdown";
    private static final String EXTENSION =
            "Custom HC4 client/interceptor extension detected; recompile against the 13.6 HC4 line and verify execution, " +
            "context, retries, cancellation, request-body repeatability, response consumption, and resource ownership";

    private static final Set<String> TIMEOUT_METHODS = Set.of(
            "setConnectTimeout", "setSocketTimeout", "setConnectionRequestTimeout", "setDefaultRequestConfig",
            "setDefaultSocketConfig");
    private static final Set<String> REDIRECT_METHODS = Set.of(
            "disableRedirectHandling", "setRedirectStrategy", "setRedirectsEnabled", "setCircularRedirectsAllowed",
            "setRelativeRedirectsAllowed", "setMaxRedirects");
    private static final Set<String> TLS_METHODS = Set.of(
            "setSSLContext", "setSSLSocketFactory", "setSSLHostnameVerifier");
    private static final Set<String> PROXY_METHODS = Set.of(
            "setProxy", "setRoutePlanner", "setDefaultCredentialsProvider", "useSystemProperties");
    private static final Set<String> BODY_METHODS = Set.of(
            "disableContentCompression", "setContentDecoderRegistry", "setDefaultHeaders", "addInterceptorFirst",
            "addInterceptorLast", "disableAutomaticRetries", "setRetryHandler");
    private static final Set<String> POOL_METHODS = Set.of(
            "setConnectionManager", "setConnectionManagerShared", "setMaxConnTotal", "setMaxConnPerRoute",
            "setMaxTotal", "setDefaultMaxPerRoute", "setMaxPerRoute", "evictExpiredConnections",
            "evictIdleConnections", "setValidateAfterInactivity");

    @Override
    public String getDisplayName() {
        return "Find Feign Apache HttpClient 13 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark type-attributed ApacheHttpClient construction, HC4 lifecycle, timeout, redirect, TLS, proxy, " +
               "compression/body, pool, and custom extension nodes that require application-specific review.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedFeignHttpClientDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (TypeUtils.isOfClassType(visited.getType(), "feign.httpclient.ApacheHttpClient")) {
                    boolean empty = visited.getArguments().isEmpty() ||
                                    visited.getArguments().stream().allMatch(J.Empty.class::isInstance);
                    return mark(visited, empty ? DEFAULT_CLIENT : OWNED_CLIENT);
                }
                if (TypeUtils.isOfClassType(visited.getType(),
                        "org.apache.http.impl.conn.PoolingHttpClientConnectionManager")) {
                    return mark(visited, POOL);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String name = method.getName();
                if (on(method, "org.apache.http.impl.client.HttpClientBuilder") && "build".equals(name)) {
                    return mark(visited, BUILD_CLIENT);
                }
                boolean requestConfig = on(method, "org.apache.http.client.config.RequestConfig$Builder");
                boolean clientBuilder = on(method, "org.apache.http.impl.client.HttpClientBuilder");
                boolean pool = on(method, "org.apache.http.impl.conn.PoolingHttpClientConnectionManager") ||
                               on(method, "org.apache.http.pool.ConnPoolControl") ||
                               visited.getSelect() != null && TypeUtils.isAssignableTo(
                                       "org.apache.http.impl.conn.PoolingHttpClientConnectionManager",
                                       visited.getSelect().getType());
                if ((requestConfig || clientBuilder) && TIMEOUT_METHODS.contains(name)) return mark(visited, TIMEOUT);
                if ((requestConfig || clientBuilder) && REDIRECT_METHODS.contains(name)) return mark(visited, REDIRECT);
                if (clientBuilder && TLS_METHODS.contains(name)) return mark(visited, TLS);
                if (clientBuilder && PROXY_METHODS.contains(name)) return mark(visited, PROXY);
                if (clientBuilder && BODY_METHODS.contains(name)) return mark(visited, COMPRESSION_BODY);
                if ((clientBuilder || pool) && POOL_METHODS.contains(name)) return mark(visited, POOL);
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
                visited = visited.withImplements(ListUtils.map(visited.getImplements(), type ->
                        extensionType(type.getType()) ? mark(type, EXTENSION) : type));
                return visited.getExtends() != null && extensionType(visited.getExtends().getType())
                        ? visited.withExtends(mark(visited.getExtends(), EXTENSION)) : visited;
            }
        };
    }

    private static boolean on(JavaType.Method method, String owner) {
        return method.getDeclaringType() != null && TypeUtils.isAssignableTo(owner, method.getDeclaringType());
    }

    private static boolean extensionType(JavaType type) {
        return TypeUtils.isAssignableTo("org.apache.http.client.HttpClient", type) ||
               TypeUtils.isAssignableTo("org.apache.http.HttpRequestInterceptor", type) ||
               TypeUtils.isAssignableTo("org.apache.http.HttpResponseInterceptor", type);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
