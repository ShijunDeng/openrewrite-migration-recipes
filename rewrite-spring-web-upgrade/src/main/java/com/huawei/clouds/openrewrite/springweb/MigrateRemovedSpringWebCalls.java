package com.huawei.clouds.openrewrite.springweb;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Set;

/**
 * Replace removed Spring Web call aliases only where the target expression
 * preserves the old contract and evaluation count.
 */
public final class MigrateRemovedSpringWebCalls extends Recipe {
    private static final MethodMatcher METHOD_VALUE = new MethodMatcher(
            "org.springframework.http.HttpRequest getMethodValue()");
    private static final MethodMatcher RAW_RESPONSE_STATUS = new MethodMatcher(
            "org.springframework.web.server.ResponseStatusException getRawStatusCode()");
    private static final MethodMatcher LEGACY_RESPONSE_STATUS = new MethodMatcher(
            "org.springframework.web.server.ResponseStatusException getStatus()");
    private static final MethodMatcher RAW_CLIENT_RESPONSE = new MethodMatcher(
            "org.springframework.http.client.ClientHttpResponse getRawStatusCode()");
    private static final MethodMatcher RAW_REST_CLIENT_RESPONSE = new MethodMatcher(
            "org.springframework.web.client.RestClientResponseException getRawStatusCode()");
    private static final MethodMatcher PARSE_FORWARDED = new MethodMatcher(
            "org.springframework.web.util.UriComponentsBuilder parseForwardedFor(" +
            "org.springframework.http.HttpRequest,java.net.InetSocketAddress)");
    private static final MethodMatcher FROM_HTTP_REQUEST = new MethodMatcher(
            "org.springframework.web.util.UriComponentsBuilder fromHttpRequest(" +
            "org.springframework.http.HttpRequest)");
    private static final MethodMatcher RESOLVE_METHOD = new MethodMatcher(
            "org.springframework.http.HttpMethod resolve(java.lang.String)");
    private static final Set<String> STANDARD_METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");

    @Override
    public String getDisplayName() {
        return "Replace deterministic removed Spring Web calls";
    }

    @Override
    public String getDescription() {
        return "Replace HttpRequest.getMethodValue, standard literal HttpMethod.resolve calls, and " +
               "side-effect-free UriComponentsBuilder forwarded-header calls with their Spring 6 APIs, plus " +
               "the implicit-receiver status calls that rewrite-spring 6.35.0 cannot safely template. Explicit " +
               "status-code calls are delegated to the official rewrite-spring recipes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit,
                                                           ExecutionContext ctx) {
                return SpringWebSupport.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (METHOD_VALUE.matches(m)) return methodValue(m, ctx);
                // rewrite-spring 6.35.0 substitutes m.getSelect() into a template without
                // guarding implicit receivers; consume only that narrow gap before it runs.
                if (m.getSelect() == null) {
                    if (RAW_RESPONSE_STATUS.matches(m) || RAW_CLIENT_RESPONSE.matches(m) ||
                        RAW_REST_CLIENT_RESPONSE.matches(m)) {
                        return JavaTemplate.builder("getStatusCode().value()").build()
                                .apply(getCursor(), m.getCoordinates().replace());
                    }
                    if (LEGACY_RESPONSE_STATUS.matches(m)) {
                        return m.withName(m.getName().withSimpleName("getStatusCode"));
                    }
                }
                if (RESOLVE_METHOD.matches(m) && standardLiteral(m)) {
                    return m.withName(m.getName().withSimpleName("valueOf"));
                }
                if (FROM_HTTP_REQUEST.matches(m) && m.getArguments().size() == 1 &&
                    m.getArguments().get(0) instanceof J.Identifier request) {
                    maybeRemoveImport("org.springframework.web.util.UriComponentsBuilder");
                    maybeRemoveImport(
                            "org.springframework.web.util.UriComponentsBuilder.fromHttpRequest");
                    return JavaTemplate.builder(
                                    "org.springframework.web.util.ForwardedHeaderUtils.adaptFromForwardedHeaders(" +
                                    "#{any(org.springframework.http.HttpRequest)}.getURI(), " +
                                    "#{any(org.springframework.http.HttpRequest)}.getHeaders())")
                            .build()
                            .apply(updateCursor(m), m.getCoordinates().replace(), request, request);
                }
                if (PARSE_FORWARDED.matches(m) && m.getArguments().size() == 2 &&
                    m.getArguments().get(0) instanceof J.Identifier request) {
                    Expression remote = m.getArguments().get(1);
                    maybeRemoveImport("org.springframework.web.util.UriComponentsBuilder");
                    maybeRemoveImport(
                            "org.springframework.web.util.UriComponentsBuilder.parseForwardedFor");
                    return JavaTemplate.builder(
                                    "org.springframework.web.util.ForwardedHeaderUtils.parseForwardedFor(" +
                                    "#{any(org.springframework.http.HttpRequest)}.getURI(), " +
                                    "#{any(org.springframework.http.HttpRequest)}.getHeaders(), " +
                                    "#{any(java.net.InetSocketAddress)})")
                            .build()
                            .apply(updateCursor(m), m.getCoordinates().replace(), request, request, remote);
                }
                return m;
            }

            private J.MethodInvocation methodValue(J.MethodInvocation method, ExecutionContext ctx) {
                if (method.getSelect() != null) {
                    Expression select = method.getSelect();
                    return JavaTemplate.builder(
                                    "#{any(org.springframework.http.HttpRequest)}.getMethod().name()")
                            .build()
                            .apply(getCursor(), method.getCoordinates().replace(), select);
                }
                return JavaTemplate.builder("getMethod().name()").build()
                        .apply(getCursor(), method.getCoordinates().replace());
            }

        };
    }

    private static boolean standardLiteral(J.MethodInvocation method) {
        if (method.getArguments().size() != 1 ||
            !(method.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String value)) return false;
        return STANDARD_METHODS.contains(value);
    }
}
