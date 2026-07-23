package com.huawei.clouds.openrewrite.springsecurityweb;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Structured Spring Security servlet configuration markers. */
public final class FindSpringSecurityWeb6511ConfigurationRisks extends Recipe {
    static final String FILTER_CHAIN =
            "该配置改变 Spring Security FilterChain 的 dispatcher、匹配范围或默认保护边界；" +
            "请验证 REQUEST/ASYNC/ERROR/FORWARD/INCLUDE、首个匹配链以及未匹配请求";
    static final String AUTHENTICATION =
            "该认证配置会影响默认用户、密码、登录入口或认证协议；请移除临时凭据，" +
            "并回归成功、失败、锁定、过期及 401/403 响应";
    static final String SESSION_CONTEXT =
            "该 session/cookie 配置与 Spring Security 6 的显式 SecurityContext 保存、RequestCache 和并发会话相关；" +
            "请验证 stateless、session fixation、continue 参数、SameSite/Secure 与多节点行为";
    static final String CSRF =
            "该 CSRF 配置与 Spring Security 6 默认延迟 token 和 XOR/BREACH 保护相关；" +
            "请验证 SPA、multipart、WebSocket、登录/退出、失效 session 和忽略路径";
    static final String HEADERS_CORS =
            "该 headers/CORS/proxy 配置会影响 HSTS、CSP、frame、cache、preflight 和安全协议识别；" +
            "请验证可信代理、allowed origins/methods/headers、credentials 与浏览器策略";
    static final String OAUTH_SAML =
            "该 OAuth2/OIDC/SAML/WebAuthn/OTT 配置跨越协议过滤器链；请核对 issuer/JWK、redirect URI、" +
            "client secret、PKCE、state/nonce、claim/authority 映射与 logout";
    static final String OBSERVATION =
            "该 observation/logging 配置可能依赖旧指标名或暴露敏感安全上下文；" +
            "Spring Security 6.5 已修正 reached-filter-section key，请同步 dashboard、告警和脱敏";
    static final String XML_SECURITY =
            "该 Spring Security XML web 配置跨越 5.x 到 6.5；请验证 namespace schema、matcher 顺序、" +
            "AuthorizationManager、CSRF、session/context、remember-me、logout、协议登录和自定义过滤器";
    static final String WEB_XML =
            "web.xml 中的 springSecurityFilterChain/DelegatingFilterProxy 或 javax Servlet 声明需要 Jakarta " +
            "Servlet 6 容器复核；请验证 dispatcher、async、filter mapping 和初始化顺序";

    private static final Set<String> FILTER_KEYS = Set.of(
            "spring.security.filter.dispatcher-types",
            "spring.security.filter.order");
    private static final Set<String> AUTHENTICATION_PREFIXES = Set.of(
            "spring.security.user.",
            "spring.security.basic.");
    private static final Set<String> SESSION_PREFIXES = Set.of(
            "server.servlet.session.",
            "server.servlet.session.cookie.");
    private static final Set<String> CSRF_KEYS = Set.of(
            "spring.security.csrf.enabled",
            "spring.security.csrf.cookie.name",
            "spring.security.csrf.header.name",
            "spring.security.csrf.parameter.name");
    private static final Set<String> HEADERS_CORS_PREFIXES = Set.of(
            "spring.web.cors.",
            "spring.mvc.cors.",
            "server.forward-headers-strategy",
            "server.tomcat.remoteip.",
            "server.servlet.context-path");
    private static final Set<String> PROTOCOL_PREFIXES = Set.of(
            "spring.security.oauth2.",
            "spring.security.saml2.",
            "spring.security.webauthn.",
            "spring.security.ott.");
    private static final Set<String> OBSERVATION_PREFIXES = Set.of(
            "management.observations.",
            "management.tracing.",
            "management.metrics.",
            "logging.level.org.springframework.security");
    private static final Set<String> SECURITY_XML_TAGS = Set.of(
            "http", "intercept-url", "csrf", "headers", "cors", "session-management", "remember-me",
            "logout", "form-login", "http-basic", "x509", "jee", "anonymous", "access-denied-handler",
            "authentication-manager", "authentication-provider", "user-service", "password-encoder",
            "custom-filter", "filter-chain-map", "filter-chain", "request-cache", "expression-handler",
            "oauth2-client", "oauth2-login", "oauth2-resource-server", "saml2-login", "saml2-logout",
            "global-method-security", "method-security", "websocket-message-broker");
    private static final Set<String> SECURITY_XML_ATTRIBUTES = Set.of(
            "use-authorization-manager", "once-per-request", "pattern", "request-matcher",
            "security", "access", "filters", "position", "before", "after", "ref",
            "create-session", "session-fixation-protection", "max-sessions", "expired-url",
            "invalid-session-url", "entry-point-ref", "access-denied-page", "access-denied-handler-ref",
            "authentication-manager-ref", "security-context-repository-ref", "request-cache-ref",
            "disable-url-rewriting", "require-explicit-save", "token-validity-seconds",
            "remember-me-parameter", "remember-me-cookie", "key", "logout-url", "logout-success-url",
            "delete-cookies", "invalidate-session", "login-page", "login-processing-url",
            "authentication-success-handler-ref", "authentication-failure-handler-ref",
            "always-use-default-target", "default-target-url");

    @Override
    public String getDisplayName() {
        return "Find Spring Security Web 6.5.11 configuration migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact Spring Security filter, authentication, session/context, CSRF, headers/CORS, " +
               "protocol and observation properties/YAML paths plus Spring Security XML and web.xml boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringSecurityWebUpgradeSupport.generated(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                if (tree instanceof Xml.Document xml &&
                    !"pom.xml".equals(source.getSourcePath().getFileName().toString())) return xml(xml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                String message = propertyMessage(normalize(visited.getKey()));
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ec);
                if (!(visited.getValue() instanceof Yaml.Scalar)) return visited;
                String message = propertyMessage(normalize(path()));
                return message == null ? visited : mark(visited, message);
            }

            private String path() {
                List<String> keys = new ArrayList<>();
                getCursor().getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                        .map(Yaml.Mapping.Entry.class::cast)
                        .forEach(entry -> keys.add(entry.getKey().getValue()));
                Collections.reverse(keys);
                return String.join(".", keys);
            }
        }.visitNonNull(source, ctx);
    }

    private static Xml.Document xml(Xml.Document source, ExecutionContext ctx) {
        return (Xml.Document) new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ec) {
                Xml.Tag visited = super.visitTag(tag, ec);
                if (securityTag(getCursor(), visited)) return mark(visited, XML_SECURITY);
                if (webXmlBoundary(getCursor(), visited)) return mark(visited, WEB_XML);
                String className = attribute(visited, "class");
                if (springSecurityWebClass(className)) return mark(visited, XML_SECURITY);
                if ("property".equals(localName(visited))) {
                    String name = attribute(visited, "name");
                    if (SECURITY_XML_ATTRIBUTES.contains(name) && springSecurityBean(getCursor())) {
                        return mark(visited, XML_SECURITY);
                    }
                }
                String value = visited.getValue().map(String::trim).orElse("");
                if (springSecurityWebClass(value)) return mark(visited, XML_SECURITY);
                if (value.startsWith("javax.servlet.") ||
                    "springSecurityFilterChain".equals(value)) return mark(visited, WEB_XML);
                return visited;
            }
        }.visitNonNull(source, ctx);
    }

    static String propertyMessage(String rawKey) {
        String key = normalize(rawKey);
        if (FILTER_KEYS.contains(key)) return FILTER_CHAIN;
        if (startsWithAny(key, AUTHENTICATION_PREFIXES)) return AUTHENTICATION;
        if (startsWithAny(key, SESSION_PREFIXES)) return SESSION_CONTEXT;
        if (CSRF_KEYS.contains(key) || key.startsWith("spring.security.csrf.")) return CSRF;
        if (startsWithAny(key, HEADERS_CORS_PREFIXES)) return HEADERS_CORS;
        if (startsWithAny(key, PROTOCOL_PREFIXES)) return OAUTH_SAML;
        if (startsWithAny(key, OBSERVATION_PREFIXES) &&
            (key.contains("security") || key.startsWith("management."))) return OBSERVATION;
        return null;
    }

    private static boolean startsWithAny(String value, Set<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static boolean securityTag(Cursor cursor, Xml.Tag tag) {
        String local = localName(tag);
        if (!SECURITY_XML_TAGS.contains(local)) return false;
        String prefix = prefix(tag);
        return "security".equals(prefix) || "sec".equals(prefix) || springSecurityNamespace(cursor);
    }

    private static boolean springSecurityNamespace(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (current.getValue() instanceof Xml.Tag candidate &&
                candidate.getAttributes().stream()
                        .map(Xml.Attribute::getValueAsString)
                        .anyMatch(value -> value.contains("springframework.org/schema/security"))) return true;
        }
        return false;
    }

    private static boolean webXmlBoundary(Cursor cursor, Xml.Tag tag) {
        String local = localName(tag);
        String value = tag.getValue().map(String::trim).orElse("");
        if (Set.of("filter-name", "filter-class", "servlet-class", "listener-class")
                .contains(local) &&
            ("springSecurityFilterChain".equals(value) ||
             "org.springframework.web.filter.DelegatingFilterProxy".equals(value) ||
             value.startsWith("javax.servlet."))) return true;
        if ("dispatcher".equals(local) && webXmlFilterMapping(cursor)) return true;
        return false;
    }

    private static boolean webXmlFilterMapping(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "filter-mapping".equals(localName(tag))) {
                return tag.getChildValue("filter-name").filter("springSecurityFilterChain"::equals).isPresent();
            }
        }
        return false;
    }

    private static boolean springSecurityWebClass(String value) {
        return value != null && value.startsWith("org.springframework.security.") &&
               (value.contains(".web.") || value.contains("SecurityFilterChain") ||
                value.contains("AuthenticationManager") || value.contains("MethodSecurity"));
    }

    private static boolean springSecurityBean(Cursor cursor) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag tag && "bean".equals(localName(tag))) {
                return springSecurityWebClass(attribute(tag, "class"));
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }

    private static String localName(Xml.Tag tag) {
        String name = tag.getName();
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    private static String prefix(Xml.Tag tag) {
        String name = tag.getName();
        int colon = name.indexOf(':');
        return colon < 0 ? "" : name.substring(0, colon);
    }

    private static String attribute(Xml.Tag tag, String name) {
        return tag.getAttributes().stream().filter(attribute -> name.equals(attribute.getKeyAsString()))
                .map(Xml.Attribute::getValueAsString).findFirst().orElse(null);
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
