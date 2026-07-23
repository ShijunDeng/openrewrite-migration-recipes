package com.huawei.clouds.openrewrite.springsecurityweb;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Type-attributed markers for behavior-sensitive Spring Security Web boundaries. */
public final class FindSpringSecurityWeb6511SourceRisks extends Recipe {
    static final String LEGACY =
            "仍存在 Spring Security 5.x 配置 API；确认 WebSecurityConfigurerAdapter、authorizeRequests、" +
            "ant/mvc/security matchers 与链式 and() 已迁移，并复核等价的过滤器链";
    static final String FILTER_CHAIN =
            "SecurityFilterChain/FilterChainProxy 的 matcher、@Order、首个匹配链和默认拒绝规则决定真实保护范围；" +
            "请对多链、静态资源、错误/异步 dispatcher 与未匹配请求做授权矩阵测试";
    static final String AUTHORIZATION =
            "请求授权已转向 AuthorizationManager/requestMatchers；核对 matcher 顺序、servletPath、MVC/Ant/regex " +
            "语义、DispatcherType、角色前缀、AccessDecisionManager/voter 与 401/403 结果";
    static final String AUTHENTICATION =
            "认证 manager/provider/filter/entry point 边界需要验证异常传播、ProviderNotFound、账户状态、" +
            "凭据擦除、Basic/Form/X509 和自定义认证成功/失败处理";
    static final String CONTEXT_SESSION =
            "Spring Security 6 默认显式保存 SecurityContext，并改变默认 repository 与 RequestCache 查询；" +
            "核对 save/clear、session fixation、并发会话、stateless、continue 参数和异步线程传播";
    static final String CSRF =
            "Spring Security 6 默认延迟加载并使用 XOR CSRF token 防 BREACH；核对 SPA、WebSocket、token " +
            "repository/handler、忽略 matcher、登录/退出、multipart 与失效 session";
    static final String HEADERS_CORS =
            "Headers/CORS 配置影响 HSTS、CSP、frame、cache、preflight 与 credentials；" +
            "确认反向代理 HTTPS 识别、允许源/方法/header 以及浏览器安全回归";
    static final String FILTER_ORDER =
            "自定义 Filter 或 addFilterBefore/After/At 会改变认证、上下文、CSRF、异常转换和授权次序；" +
            "请核对唯一锚点、重复注册、dispatcher、OncePerRequest 与异步/错误请求";
    static final String REMEMBER_ME =
            "Remember-me 令牌默认算法、key、cookie、UserDetailsService 与旧 token 兼容需要显式决策；" +
            "验证 SHA-256 切换、轮换、过期、登出清理和多节点一致性";
    static final String LOGOUT =
            "Logout matcher/handler/success handler 会决定请求方法、SecurityContext/session/cookie 清理和重定向；" +
            "请验证 CSRF 开启/关闭下的 GET/POST、OIDC/SAML logout 与幂等";
    static final String OAUTH_SAML =
            "OAuth2/OIDC/SAML/Bearer/WebAuthn/OTT 过滤器链跨版本变化；核对 redirect URI、PKCE、state/nonce、" +
            "JWK、claim/authority 映射、DPoP、single logout 与失败响应";
    static final String OBSERVATION =
            "Spring Security 6.5 修正了 reached-filter-section 指标 key，并支持 Micrometer context propagation；" +
            "请核对 ObservationRegistry、低/高基数标签、trace 传播、dashboard 与告警";
    static final String METHOD_SECURITY =
            "方法安全迁移到 @EnableMethodSecurity/AuthorizationManager；含 #参数名 的 SpEL 在 Spring 6.1+ " +
            "需要 -parameters，并需验证代理边界、默认 pre/post、角色前缀和重复注解";
    static final String JAKARTA =
            "该 servlet 集成已进入 jakarta.servlet 边界；请用 Jakarta 容器重新编译自定义 Filter、wrapper、" +
            "listener 和测试 mock，排除任何 javax/jakarta 混装";
    static final String EXTENSION =
            "该类扩展 Spring Security Web SPI；请按 6.5.11 重新编译并验证生命周期、泛型、线程安全、" +
            "异常处理、过滤器顺序与二进制签名";

    private static final Set<String> LEGACY_TYPES = Set.of(
            "org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter",
            "org.springframework.security.web.access.intercept.FilterSecurityInterceptor",
            "org.springframework.security.access.AccessDecisionManager",
            "org.springframework.security.access.AccessDecisionVoter");
    private static final Set<String> FILTER_TYPES = Set.of(
            "org.springframework.security.web.SecurityFilterChain",
            "org.springframework.security.web.FilterChainProxy",
            "org.springframework.security.config.annotation.web.builders.HttpSecurity",
            "org.springframework.security.config.annotation.web.builders.WebSecurity");
    private static final Set<String> EXTENSION_TYPES = Set.of(
            "javax.servlet.Filter",
            "jakarta.servlet.Filter",
            "org.springframework.web.filter.OncePerRequestFilter",
            "org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter",
            "org.springframework.security.web.AuthenticationEntryPoint",
            "org.springframework.security.web.access.AccessDeniedHandler",
            "org.springframework.security.web.util.matcher.RequestMatcher",
            "org.springframework.security.web.context.SecurityContextRepository");
    private static final Set<String> METHOD_SECURITY_ANNOTATIONS = Set.of(
            "org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity",
            "org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity",
            "org.springframework.security.access.prepost.PreAuthorize",
            "org.springframework.security.access.prepost.PostAuthorize",
            "org.springframework.security.access.prepost.PreFilter",
            "org.springframework.security.access.prepost.PostFilter",
            "org.springframework.security.access.annotation.Secured",
            "jakarta.annotation.security.RolesAllowed",
            "javax.annotation.security.RolesAllowed");
    private static final Set<String> FILTER_METHODS = Set.of(
            "addFilter", "addFilterBefore", "addFilterAfter", "addFilterAt");
    private static final Set<String> AUTHORIZATION_METHODS = Set.of(
            "authorizeRequests", "authorizeHttpRequests", "antMatchers", "mvcMatchers", "regexMatchers",
            "requestMatchers", "securityMatcher", "securityMatchers", "dispatcherTypeMatchers",
            "anyRequest", "access", "accessDecisionManager", "expressionHandler", "hasRole", "hasAnyRole",
            "hasAuthority", "hasAnyAuthority", "permitAll", "denyAll", "authenticated", "fullyAuthenticated",
            "anonymous", "filterAllDispatcherTypes");
    private static final Set<String> AUTHENTICATION_METHODS = Set.of(
            "authenticationManager", "authenticationProvider", "userDetailsService", "httpBasic", "formLogin",
            "x509", "jee", "exceptionHandling", "authenticationEntryPoint", "accessDeniedHandler",
            "successHandler", "failureHandler", "loginProcessingUrl", "loginPage");
    private static final Set<String> CONTEXT_METHODS = Set.of(
            "securityContext", "securityContextRepository", "requireExplicitSave", "saveContext", "setContext",
            "clearContext", "requestCache", "requestCacheMatcher", "matchingRequestParameterName",
            "sessionManagement", "sessionCreationPolicy", "sessionAuthenticationStrategy", "maximumSessions",
            "maxSessionsPreventsLogin", "invalidSessionUrl", "sessionFixation");
    private static final Set<String> CSRF_METHODS = Set.of(
            "csrf", "csrfTokenRepository", "csrfTokenRequestHandler", "ignoringAntMatchers",
            "ignoringRequestMatchers", "setCsrfRequestAttributeName");
    private static final Set<String> HEADERS_METHODS = Set.of(
            "cors", "headers", "contentSecurityPolicy", "frameOptions", "httpStrictTransportSecurity",
            "referrerPolicy", "permissionsPolicy", "cacheControl", "xssProtection");
    private static final Set<String> REMEMBER_METHODS = Set.of(
            "rememberMe", "rememberMeServices", "tokenValiditySeconds", "useSecureCookie",
            "rememberMeParameter", "rememberMeCookieName", "setMatchingAlgorithm");
    private static final Set<String> LOGOUT_METHODS = Set.of(
            "logout", "logoutUrl", "logoutRequestMatcher", "addLogoutHandler", "logoutSuccessHandler",
            "logoutSuccessUrl", "deleteCookies", "clearAuthentication", "invalidateHttpSession");
    private static final Set<String> PROTOCOL_METHODS = Set.of(
            "oauth2Login", "oauth2Client", "oauth2ResourceServer", "saml2Login", "saml2Logout",
            "oidcLogout", "bearerTokenResolver", "jwt", "opaqueToken", "webAuthn", "oneTimeTokenLogin");
    private static final Set<String> OBSERVATION_METHODS = Set.of(
            "observationRegistry", "setObservationRegistry", "observationConvention", "setObservationConvention");

    @Override
    public String getDisplayName() {
        return "Find Spring Security Web 6.5.11 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark filter-chain, authorization, authentication, context/session, CSRF, headers/CORS, " +
               "filter order, remember-me, logout, protocol, observation, method-security and Jakarta decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return SpringSecurityWebUpgradeSupport.generated(cu.getSourcePath()) ? cu :
                        super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String message = typeMessage(visited.getTypeName());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                String type = fqn(visited.getType());
                return METHOD_SECURITY_ANNOTATIONS.contains(type) ? mark(visited, METHOD_SECURITY) : visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                JavaType type = visited.getType();
                for (String extension : EXTENSION_TYPES) {
                    if (assignable(extension, type)) return mark(visited, EXTENSION);
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                if ("org.springframework.security.web.SecurityFilterChain".equals(
                        fqn(visited.getReturnTypeExpression() == null ?
                                null : visited.getReturnTypeExpression().getType()))) {
                    return mark(visited, FILTER_CHAIN);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String message = typeMessage(fqn(visited.getType()));
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                String owner = methodType == null ? "" : fqn(methodType.getDeclaringType());
                String name = visited.getSimpleName();
                boolean securityOwner = owner.startsWith("org.springframework.security.");
                if (!securityOwner) return visited;
                if (FILTER_METHODS.contains(name)) return mark(visited, FILTER_ORDER);
                if (CONTEXT_METHODS.contains(name)) return mark(visited, CONTEXT_SESSION);
                if (CSRF_METHODS.contains(name)) return mark(visited, CSRF);
                if (HEADERS_METHODS.contains(name)) return mark(visited, HEADERS_CORS);
                if (REMEMBER_METHODS.contains(name)) return mark(visited, REMEMBER_ME);
                if (LOGOUT_METHODS.contains(name)) return mark(visited, LOGOUT);
                if (PROTOCOL_METHODS.contains(name)) return mark(visited, OAUTH_SAML);
                if (OBSERVATION_METHODS.contains(name)) return mark(visited, OBSERVATION);
                if (AUTHORIZATION_METHODS.contains(name)) return mark(visited, AUTHORIZATION);
                if (AUTHENTICATION_METHODS.contains(name)) return mark(visited, AUTHENTICATION);
                return visited;
            }
        };
    }

    private static String typeMessage(String type) {
        if (type == null || type.isEmpty()) return null;
        if (LEGACY_TYPES.contains(type)) return LEGACY;
        if (FILTER_TYPES.contains(type)) return FILTER_CHAIN;
        if (METHOD_SECURITY_ANNOTATIONS.contains(type)) return METHOD_SECURITY;
        if (type.startsWith("javax.servlet.") || type.startsWith("jakarta.servlet.")) return JAKARTA;
        if (type.startsWith("org.springframework.security.web.csrf.")) return CSRF;
        if (type.startsWith("org.springframework.security.web.header.") ||
            type.startsWith("org.springframework.security.web.access.channel.") ||
            type.startsWith("org.springframework.security.web.cors.")) return HEADERS_CORS;
        if (type.startsWith("org.springframework.security.web.context.") ||
            type.startsWith("org.springframework.security.web.session.") ||
            type.startsWith("org.springframework.security.web.savedrequest.")) return CONTEXT_SESSION;
        if (type.startsWith("org.springframework.security.web.authentication.rememberme.")) return REMEMBER_ME;
        if (type.startsWith("org.springframework.security.web.authentication.logout.")) return LOGOUT;
        if (type.contains(".oauth2.") || type.contains(".saml2.") || type.contains(".webauthn.") ||
            type.contains(".ott.") || type.contains("BearerToken")) return OAUTH_SAML;
        if (type.startsWith("org.springframework.security.web.util.matcher.") ||
            type.startsWith("org.springframework.security.authorization.") ||
            type.startsWith("org.springframework.security.web.access.")) return AUTHORIZATION;
        if (type.startsWith("org.springframework.security.authentication.") ||
            type.startsWith("org.springframework.security.web.authentication.")) return AUTHENTICATION;
        if (type.contains("Observation") && type.startsWith("org.springframework.security.")) return OBSERVATION;
        return null;
    }

    private static boolean assignable(String target, JavaType type) {
        return type != null && TypeUtils.isAssignableTo(target, type);
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
