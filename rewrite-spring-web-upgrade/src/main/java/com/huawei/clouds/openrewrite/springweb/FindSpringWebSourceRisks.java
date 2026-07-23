package com.huawei.clouds.openrewrite.springweb;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;

import java.util.Set;

/** Type-aware markers for behavior-sensitive Spring Web 6.0/6.1/6.2 changes. */
public final class FindSpringWebSourceRisks extends Recipe {
    private static final String HTTP_METHOD_TYPE = "org.springframework.http.HttpMethod";
    private static final String HTTP_REQUEST_TYPE = "org.springframework.http.HttpRequest";
    private static final String RESPONSE_ERROR_HANDLER = "org.springframework.web.client.ResponseErrorHandler";
    private static final String DEFAULT_RESPONSE_ERROR_HANDLER =
            "org.springframework.web.client.DefaultResponseErrorHandler";
    private static final String HTTP_COMPONENTS_FACTORY =
            "org.springframework.http.client.HttpComponentsClientHttpRequestFactory";
    private static final String BASIC_AUTHORIZATION_INTERCEPTOR =
            "org.springframework.http.client.support.BasicAuthorizationInterceptor";
    private static final String CONTROLLER_ADVICE_BEAN =
            "org.springframework.web.method.ControllerAdviceBean";
    private static final Set<String> ASYNC_TYPES = Set.of(
            "org.springframework.http.client.AsyncClientHttpRequest",
            "org.springframework.http.client.AsyncClientHttpRequestExecution",
            "org.springframework.http.client.AsyncClientHttpRequestFactory",
            "org.springframework.http.client.AsyncClientHttpRequestInterceptor",
            "org.springframework.http.client.InterceptingAsyncClientHttpRequestFactory",
            "org.springframework.http.client.HttpComponentsAsyncClientHttpRequestFactory",
            "org.springframework.http.client.Netty4ClientHttpRequestFactory",
            "org.springframework.http.client.OkHttp3ClientHttpRequestFactory",
            "org.springframework.http.client.support.AsyncHttpAccessor",
            "org.springframework.web.client.AsyncRestOperations",
            "org.springframework.web.client.AsyncRestTemplate"
    );
    private static final Set<String> REMOVED_URI_TYPES = Set.of(
            "org.springframework.web.util.AbstractUriTemplateHandler",
            "org.springframework.web.util.DefaultUriTemplateHandler"
    );
    private static final Set<String> BUFFERING_TYPES = Set.of(
            "org.springframework.http.client.BufferingClientHttpRequestFactory",
            "org.springframework.http.client.AbstractBufferingClientHttpRequest"
    );
    private static final Set<String> CONVERTER_BASE_TYPES = Set.of(
            "org.springframework.http.converter.AbstractHttpMessageConverter",
            "org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter",
            "org.springframework.http.converter.json.AbstractJsonHttpMessageConverter",
            "org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter",
            "org.springframework.http.converter.json.JsonbHttpMessageConverter",
            "org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter"
    );
    private static final Set<String> DEFAULT_VALUE_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RequestParam",
            "org.springframework.web.bind.annotation.RequestHeader",
            "org.springframework.web.bind.annotation.CookieValue"
    );
    private static final Set<String> HTTP_ENUM_METHODS =
            Set.of("ordinal", "compareTo", "getDeclaringClass");
    private static final Set<String> BUFFERING_METHODS =
            Set.of("setBufferRequestBody", "setOutputStreaming");
    private static final MethodMatcher RESOLVE_METHOD =
            new MethodMatcher("org.springframework.http.HttpMethod resolve(java.lang.String)");
    private static final MethodMatcher METHOD_VALUE =
            new MethodMatcher("org.springframework.http.HttpRequest getMethodValue()");
    private static final MethodMatcher CLIENT_RAW_STATUS =
            new MethodMatcher("org.springframework.http.client.ClientHttpResponse getRawStatusCode()");
    private static final MethodMatcher EXCEPTION_STATUS =
            new MethodMatcher("org.springframework.web.server.ResponseStatusException getStatus()");
    private static final MethodMatcher EXCEPTION_RAW_STATUS =
            new MethodMatcher("org.springframework.web.server.ResponseStatusException getRawStatusCode()");
    private static final MethodMatcher PARSE_FORWARDED =
            new MethodMatcher("org.springframework.web.util.UriComponentsBuilder parseForwardedFor(" +
                              "org.springframework.http.HttpRequest,java.net.InetSocketAddress)");
    private static final MethodMatcher URI_FROM_STRING =
            new MethodMatcher("org.springframework.web.util.UriComponentsBuilder fromUriString(java.lang.String)");
    private static final MethodMatcher CORS_CREDENTIALS =
            new MethodMatcher("org.springframework.web.cors.CorsConfiguration setAllowCredentials(java.lang.Boolean)");
    private static final MethodMatcher CORS_ORIGINS =
            new MethodMatcher("org.springframework.web.cors.CorsConfiguration setAllowedOrigins(java.util.List)");

    static final String HTTP_METHOD =
            "Spring 6 changed HttpMethod from enum to class and removed resolve/getMethodValue; preserve unknown extension methods when replacing switch, EnumSet, ordinal, custom HttpRequest, or dynamic resolve logic";
    static final String STATUS_CODE =
            "Spring 6 uses HttpStatusCode and removed legacy raw/status accessors; preserve custom and non-standard status codes instead of forcing HttpStatus";
    static final String ASYNC_CLIENT =
            "Spring 6 removed the AsyncRestTemplate/AsyncClientHttpRequest stack; migrate deliberately to WebClient or a synchronous ClientHttpRequestFactory and preserve cancellation, timeout, interceptor, and callback behavior";
    static final String HTTP_CLIENT_5 =
            "Spring 6 HttpComponentsClientHttpRequestFactory uses Apache HttpClient 5 types; port custom factory/subclass/configuration code to client5 and verify pooling, TLS, proxy, timeout, and lifecycle semantics";
    static final String BASIC_AUTH =
            "BasicAuthorizationInterceptor was removed, but BasicAuthenticationInterceptor is not a behavior-equivalent type rename: preserve UTF-8 credential encoding and decide how an existing Authorization header must be handled";
    static final String REMOTING =
            "Spring 6 removed HTTP Invoker and Hessian remoting support; choose and test an explicit replacement protocol, security model, serialization contract, and error mapping";
    static final String MULTIPART =
            "Spring 6 removed Commons FileUpload-based CommonsMultipartResolver; use the Servlet multipart container or a deliberate alternative and retest limits, temp storage, cleanup, encoding, and exceptions";
    static final String URI_PATH =
            "Spring 6 removed or changed this URI/path/forwarded-header API; preserve encoding mode, forwarded trust boundary, query semantics, and single evaluation when migrating";
    static final String CORS =
            "Spring 6 tightened and extended CORS processing; verify wildcard origins versus credentials/private-network access, origin patterns, preflight headers, and cached responses";
    static final String MESSAGE_CONVERTER =
            "Spring 6 changed message-converter Jakarta types and several extension points; recompile custom converters and test media negotiation, generic types, charset, streaming, and malformed input";
    static final String RESPONSE_ERROR =
            "Spring 6 changed ResponseErrorHandler/DefaultResponseErrorHandler status and body handling extension points; port overrides to HttpStatusCode APIs and test unknown statuses, body decoding, headers, and exceptions";
    static final String VALIDATION =
            "Spring 6.1 adds built-in method validation and uses Jakarta Validation; review @Validated proxies, constraints, groups, BindingResult/MethodArgumentNotValidException, and error responses";
    static final String OBSERVABILITY =
            "Spring 6.1/6.2 changed client observation conventions and low-cardinality keys; verify URI templates, client name replacement, tags, errors, retries, and metric cardinality";
    static final String HTTP_INTERFACE =
            "Spring 6.1/6.2 changed HTTP interface client adapter contracts and blocking timeout behavior; update custom adapters and test headers, argument resolvers, errors, and blocking calls";
    static final String REST_CLIENT =
            "Spring 6.2 no longer executes RestClient retrieve() without a terminal ResponseSpec operation; add the business-appropriate terminal operation and verify response closing, errors, observations, and side effects";
    static final String CONTROLLER_ADVICE =
            "Spring 6.2 removed public ControllerAdviceBean construction paths and changed exception-handler media negotiation; use framework discovery and test handler ordering and Accept-specific selection";
    static final String BUFFERING =
            "Spring 6.1/6.2 changed request buffering and factory streaming controls; verify retry/logging interceptors, memory use, content length, and large uploads";
    static final String JETTY_REACTOR =
            "Spring 6.1/6.2 changed reactive client connector resource and buffer APIs; align connector versions and verify ownership, shutdown, event loops, memory, and timeouts";
    static final String DEFAULT_VALUE =
            "In Spring 6.1 a non-empty but whitespace-only input can select this annotation defaultValue; regression-test blank, missing, and explicit values";
    static final String JAKARTA =
            "Spring Web 6 requires Jakarta EE types at this boundary; migrate the API, provider, descriptor, runtime container, serialized names, and integration tests together";

    @Override
    public String getDisplayName() {
        return "Find Spring Web 6.2 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact removed clients/remoting/multipart APIs and behavior-sensitive HttpMethod, status, HTTP " +
               "client, converter, error, URI, CORS, validation, observation, interface-client, and buffering boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || SpringWebSupport.generated(source.getSourcePath())) {
                    return tree;
                }
                if (tree instanceof J.CompilationUnit java) return java(java, ctx);
                if (tree instanceof K.CompilationUnit kotlin) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static J.CompilationUnit java(J.CompilationUnit source, ExecutionContext ctx) {
        return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ec) {
                J.Import i = super.visitImport(anImport, ec);
                String message = importMessage(i.getTypeName());
                return message == null ? i : SpringWebSupport.mark(i, message);
            }

            @Override
            public J.Switch visitSwitch(J.Switch aSwitch, ExecutionContext ec) {
                J.Switch s = super.visitSwitch(aSwitch, ec);
                return TypeUtils.isOfClassType(s.getSelector().getTree().getType(), HTTP_METHOD_TYPE)
                        ? SpringWebSupport.mark(s, HTTP_METHOD) : s;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration,
                                                             ExecutionContext ec) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ec);
                JavaType type = cd.getType();
                if (TypeUtils.isAssignableTo(HTTP_REQUEST_TYPE, type) && declaresMethod(cd, "getMethodValue")) {
                    return SpringWebSupport.mark(cd, HTTP_METHOD);
                }
                if (TypeUtils.isAssignableTo(DEFAULT_RESPONSE_ERROR_HANDLER, type) ||
                    TypeUtils.isAssignableTo(RESPONSE_ERROR_HANDLER, type)) {
                    return SpringWebSupport.mark(cd, RESPONSE_ERROR);
                }
                if (TypeUtils.isAssignableTo(HTTP_COMPONENTS_FACTORY, type)) {
                    return SpringWebSupport.mark(cd, HTTP_CLIENT_5);
                }
                for (String converter : CONVERTER_BASE_TYPES) {
                    if (TypeUtils.isAssignableTo(converter, type) &&
                        !TypeUtils.isOfClassType(type, converter)) {
                        return SpringWebSupport.mark(cd, MESSAGE_CONVERTER);
                    }
                }
                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ec) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ec);
                if ("getMethodValue".equals(m.getSimpleName()) &&
                    m.getMethodType() != null &&
                    TypeUtils.isAssignableTo(HTTP_REQUEST_TYPE, m.getMethodType().getDeclaringType())) {
                    return SpringWebSupport.mark(m, HTTP_METHOD);
                }
                if ("handleError".equals(m.getSimpleName()) && m.getMethodType() != null &&
                    TypeUtils.isAssignableTo(RESPONSE_ERROR_HANDLER, m.getMethodType().getDeclaringType())) {
                    return SpringWebSupport.mark(m, RESPONSE_ERROR);
                }
                return m;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (RESOLVE_METHOD.matches(m) || METHOD_VALUE.matches(m) ||
                    httpEnumAssumption(m) || enumSetOfHttpMethod(m)) {
                    return SpringWebSupport.mark(m, HTTP_METHOD);
                }
                if (CLIENT_RAW_STATUS.matches(m) || EXCEPTION_STATUS.matches(m) ||
                    EXCEPTION_RAW_STATUS.matches(m)) {
                    return SpringWebSupport.mark(m, STATUS_CODE);
                }
                if (PARSE_FORWARDED.matches(m) || URI_FROM_STRING.matches(m) ||
                    declaringTypeIn(m, REMOVED_URI_TYPES)) {
                    return SpringWebSupport.mark(m, URI_PATH);
                }
                if (CORS_CREDENTIALS.matches(m) || CORS_ORIGINS.matches(m)) {
                    return SpringWebSupport.mark(m, CORS);
                }
                String declaring = declaringType(m);
                if (BUFFERING_METHODS.contains(m.getSimpleName()) &&
                    (TypeUtils.isAssignableTo(HTTP_COMPONENTS_FACTORY, declaringTypeValue(m)) ||
                     declaring != null && declaring.startsWith("org.springframework.http.client.") ||
                     enclosingTypeAssignableTo(getCursor(), HTTP_COMPONENTS_FACTORY))) {
                    return SpringWebSupport.mark(m, BUFFERING);
                }
                String message = typeMessage(declaring);
                if (message != null) return SpringWebSupport.mark(m, message);
                if (declaring != null && declaring.startsWith("org.springframework.web.service.invoker.")) {
                    return SpringWebSupport.mark(m, HTTP_INTERFACE);
                }
                if ("retrieve".equals(m.getSimpleName()) && declaring != null &&
                    declaring.startsWith("org.springframework.web.client.RestClient$")) {
                    return SpringWebSupport.mark(m, REST_CLIENT);
                }
                if (isObservationType(declaring)) {
                    return SpringWebSupport.mark(m, OBSERVABILITY);
                }
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ec) {
                J.NewClass n = super.visitNewClass(newClass, ec);
                String type = fullyQualified(n.getType());
                if (CONTROLLER_ADVICE_BEAN.equals(type)) {
                    return SpringWebSupport.mark(n, CONTROLLER_ADVICE);
                }
                String message = typeMessage(type);
                return message == null ? n : SpringWebSupport.mark(n, message);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberReference,
                                                           ExecutionContext ec) {
                J.MemberReference r = super.visitMemberReference(memberReference, ec);
                if ("resolve".equals(r.getReference().getSimpleName()) &&
                    r.getMethodType() != null &&
                    TypeUtils.isOfClassType(r.getMethodType().getDeclaringType(), HTTP_METHOD_TYPE)) {
                    return SpringWebSupport.mark(r, HTTP_METHOD);
                }
                return r;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ec) {
                J.FieldAccess f = super.visitFieldAccess(fieldAccess, ec);
                if ("CLIENT_NAME".equals(f.getSimpleName()) &&
                    "org.springframework.http.client.observation.ClientHttpObservationDocumentation"
                            .equals(fullyQualified(f.getTarget().getType()))) {
                    return SpringWebSupport.mark(f, OBSERVABILITY);
                }
                return f;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ec) {
                J.Annotation a = super.visitAnnotation(annotation, ec);
                String type = fullyQualified(a.getType());
                if (DEFAULT_VALUE_ANNOTATIONS.contains(type) && hasDefaultValue(a)) {
                    return SpringWebSupport.mark(a, DEFAULT_VALUE);
                }
                if ("org.springframework.validation.annotation.Validated".equals(type)) {
                    return SpringWebSupport.mark(a, VALIDATION);
                }
                if ("org.springframework.web.bind.annotation.ExceptionHandler".equals(type)) {
                    return SpringWebSupport.mark(a, CONTROLLER_ADVICE);
                }
                if ("org.springframework.web.service.annotation.HttpExchange".equals(type)) {
                    return SpringWebSupport.mark(a, HTTP_INTERFACE);
                }
                return a;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ec) {
                J.Import i = super.visitImport(anImport, ec);
                String message = importMessage(i.getTypeName());
                return message == null ? i : SpringWebSupport.mark(i, message);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                String message = typeMessage(declaringType(m));
                return message == null ? m : SpringWebSupport.mark(m, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static String importMessage(String type) {
        if (type == null) return null;
        if (type.startsWith("javax.servlet.") || type.startsWith("javax.validation.") ||
            type.startsWith("javax.xml.bind.") || type.startsWith("javax.json.") ||
            type.startsWith("javax.activation.")) return JAKARTA;
        if (type.startsWith("org.apache.http.")) return HTTP_CLIENT_5;
        return typeMessage(type);
    }

    private static String typeMessage(String type) {
        if (type == null) return null;
        if (ASYNC_TYPES.contains(type)) return ASYNC_CLIENT;
        if (type.startsWith("org.springframework.remoting.caucho.") ||
            type.startsWith("org.springframework.remoting.httpinvoker.")) return REMOTING;
        if (type.startsWith("org.springframework.web.multipart.commons.")) return MULTIPART;
        if (REMOVED_URI_TYPES.contains(type)) return URI_PATH;
        if (BUFFERING_TYPES.contains(type)) return BUFFERING;
        if (BASIC_AUTHORIZATION_INTERCEPTOR.equals(type)) return BASIC_AUTH;
        if (HTTP_COMPONENTS_FACTORY.equals(type)) return HTTP_CLIENT_5;
        if ("org.springframework.http.client.reactive.ReactorResourceFactory".equals(type) ||
            type.startsWith("org.springframework.http.client.reactive.JettyClientHttpConnector") ||
            type.startsWith("org.springframework.http.client.reactive.ReactorClientHttpConnector")) {
            return JETTY_REACTOR;
        }
        if (type.startsWith("org.springframework.http.codec.multipart.Synchronoss")) return MULTIPART;
        if (type.startsWith("org.springframework.web.service.invoker.")) return HTTP_INTERFACE;
        if (isObservationType(type)) return OBSERVABILITY;
        return null;
    }

    private static boolean isObservationType(String type) {
        return type != null &&
               (type.startsWith("io.micrometer.observation.") ||
                type.startsWith("org.springframework.http.client.observation.") ||
                type.startsWith("org.springframework.http.server.observation.") ||
                "org.springframework.web.filter.ServerHttpObservationFilter".equals(type));
    }

    private static boolean httpEnumAssumption(J.MethodInvocation method) {
        if (!HTTP_ENUM_METHODS.contains(method.getSimpleName()) || method.getSelect() == null) return false;
        return TypeUtils.isOfClassType(method.getSelect().getType(), HTTP_METHOD_TYPE);
    }

    private static boolean enumSetOfHttpMethod(J.MethodInvocation method) {
        JavaType.Method type = method.getMethodType();
        if (type == null || !TypeUtils.isOfClassType(type.getDeclaringType(), "java.util.EnumSet")) return false;
        return method.getArguments().stream().filter(Expression.class::isInstance)
                .map(Expression.class::cast).map(Expression::getType)
                .anyMatch(argumentType -> TypeUtils.isOfClassType(argumentType, HTTP_METHOD_TYPE));
    }

    private static boolean declaringTypeIn(J.MethodInvocation method, Set<String> candidates) {
        String declaring = declaringType(method);
        return declaring != null && candidates.contains(declaring);
    }

    private static String declaringType(J.MethodInvocation method) {
        return method.getMethodType() == null ? null :
                fullyQualified(method.getMethodType().getDeclaringType());
    }

    private static JavaType declaringTypeValue(J.MethodInvocation method) {
        return method.getMethodType() == null ? null : method.getMethodType().getDeclaringType();
    }

    private static boolean enclosingTypeAssignableTo(org.openrewrite.Cursor cursor, String type) {
        J.ClassDeclaration declaration = cursor.firstEnclosing(J.ClassDeclaration.class);
        return declaration != null && TypeUtils.isAssignableTo(type, declaration.getType());
    }

    private static boolean declaresMethod(J.ClassDeclaration declaration, String name) {
        return declaration.getBody().getStatements().stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .map(J.MethodDeclaration.class::cast)
                .anyMatch(method -> name.equals(method.getSimpleName()));
    }

    private static boolean hasDefaultValue(J.Annotation annotation) {
        return annotation.getArguments() != null && annotation.getArguments().stream().anyMatch(argument ->
                argument instanceof J.Assignment assignment &&
                assignment.getVariable() instanceof J.Identifier identifier &&
                "defaultValue".equals(identifier.getSimpleName()));
    }

    private static String fullyQualified(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }
}
