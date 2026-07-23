package com.huawei.clouds.openrewrite.springwebmvc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.Set;

/** Type-aware Spring MVC 5.x to 6.2 source review markers. */
public final class FindSpringWebMvc6SourceRisks extends Recipe {
    private static final String HANDLER_INTERCEPTOR = "org.springframework.web.servlet.HandlerInterceptor";
    private static final String RESPONSE_EXCEPTION_HANDLER =
            "org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler";
    private static final Set<String> REMOVED_TYPES = Set.of(
            "org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter",
            "org.springframework.web.servlet.handler.HandlerInterceptorAdapter",
            "org.springframework.web.servlet.resource.GzipResourceResolver",
            "org.springframework.web.servlet.resource.AppCacheManifestTransformer",
            "org.springframework.web.multipart.commons.CommonsMultipartResolver"
    );
    private static final Set<String> REMOVED_SERVLET_TYPES = Set.of(
            "javax.servlet.SingleThreadModel",
            "javax.servlet.http.HttpSessionContext",
            "javax.servlet.http.HttpUtils",
            "jakarta.servlet.SingleThreadModel",
            "jakarta.servlet.http.HttpSessionContext",
            "jakarta.servlet.http.HttpUtils"
    );
    private static final Set<String> PATH_METHODS = Set.of(
            "setPatternParser", "setUseTrailingSlashMatch", "setUseSuffixPatternMatch",
            "setUseRegisteredSuffixPatternMatch", "setUrlPathHelper", "setPathMatcher"
    );
    private static final Set<String> CONTENT_NEGOTIATION_METHODS = Set.of(
            "favorPathExtension", "useRegisteredExtensionsOnly", "ignoreUnknownPathExtensions",
            "mediaType", "defaultContentType", "favorParameter"
    );
    private static final Set<String> EMITTER_TYPES = Set.of(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter",
            "org.springframework.web.servlet.mvc.method.annotation.SseEmitter"
    );
    private static final Set<String> NOT_FOUND_EXCEPTIONS = Set.of(
            "org.springframework.web.servlet.NoHandlerFoundException",
            "org.springframework.web.servlet.resource.NoResourceFoundException"
    );
    private static final MethodMatcher PATH_MATCH = new MethodMatcher(
            "org.springframework.web.servlet.config.annotation.PathMatchConfigurer *(..)");
    private static final MethodMatcher CONTENT_NEGOTIATION = new MethodMatcher(
            "org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer *(..)");
    private static final MethodMatcher DISPATCHER = new MethodMatcher(
            "org.springframework.web.servlet.DispatcherServlet setThrowExceptionIfNoHandlerFound(..)");
    private static final MethodMatcher RESOURCE_LOCATION = new MethodMatcher(
            "org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration addResourceLocations(..)");
    private static final MethodMatcher ROUTER_ORDER = new MethodMatcher(
            "org.springframework.web.servlet.function.support.RouterFunctionMapping setOrder(..)");
    private static final MethodMatcher JAVAX_SESSION_VALUE_NAMES = new MethodMatcher(
            "javax.servlet.http.HttpSession getValueNames()");
    private static final MethodMatcher JAKARTA_SESSION_VALUE_NAMES = new MethodMatcher(
            "jakarta.servlet.http.HttpSession getValueNames()");
    private static final MethodMatcher RESPONSE_ENTITY_STATUS = new MethodMatcher(
            "org.springframework.http.ResponseEntity getStatusCode()");
    private static final List<MethodMatcher> SERVLET_COOKIE_GETTERS = List.of(
            new MethodMatcher("javax.servlet.http.Cookie getComment()"),
            new MethodMatcher("javax.servlet.http.Cookie getVersion()"),
            new MethodMatcher("javax.servlet.SessionCookieConfig getComment()"),
            new MethodMatcher("jakarta.servlet.http.Cookie getComment()"),
            new MethodMatcher("jakarta.servlet.http.Cookie getVersion()"),
            new MethodMatcher("jakarta.servlet.SessionCookieConfig getComment()")
    );

    static final String REMOVED =
            "This Spring MVC API was removed in Framework 6; choose the documented replacement and preserve custom behavior explicitly";
    static final String TILES =
            "Spring Framework 6 removed the complete org.springframework.web.servlet.view.tiles3 integration; select and test a maintained view technology";
    static final String PATH =
            "Spring MVC 6 defaults to PathPatternParser and no optional trailing-slash match; verify encoded paths, matrix variables, suffixes, servlet prefixes, security rules, and redirects";
    static final String CONTROLLER =
            "Spring MVC 6 no longer detects a controller solely from a type-level @RequestMapping; add @Controller/@RestController or register the handler deliberately";
    static final String VALIDATION =
            "Spring MVC 6.1 has built-in method validation; remove class-level @Validated only after reviewing proxy advice, jakarta constraints, BindingResult, exception handling, and validation groups";
    static final String DEFAULT_VALUE =
            "In Spring MVC 6.1 a non-empty but whitespace-only input can select this annotation defaultValue; regression-test blank, missing, and explicit values";
    static final String EXCEPTION =
            "Spring MVC 6.1 raises and handles NoHandlerFoundException/NoResourceFoundException by default; review this exception path, ProblemDetail response, status, headers, and body";
    static final String INTERCEPTOR =
            "Spring MVC 6.1 runs CORS preflight checks before the HandlerInterceptor chain; verify authentication, audit, mutation, async, and completion behavior";
    static final String RESOURCE =
            "Spring MVC 6.2 normalizes String static-resource locations with a trailing slash and changes WebJars resolution; verify containment, cache, encoding, and lookup behavior";
    static final String ROUTER =
            "RouterFunctionMapping now precedes RequestMappingHandlerMapping by default; review overlapping functional and annotated routes and any custom order";
    static final String EMITTER =
            "ResponseBodyEmitter/SseEmitter completion and error behavior changed across Spring 6.1/6.2; verify disconnects, IOException handling, timeouts, and keep-alive listeners";
    static final String STATUS =
            "ResponseEntityExceptionHandler override signatures and status handling changed to HttpStatusCode; recompile every override and preserve headers, ProblemDetail, and response body semantics";
    static final String JAKARTA =
            "Spring MVC 6 uses Jakarta Servlet and Jakarta Validation APIs; migrate this remaining javax type and its dependency owner";
    static final String SERVLET_REMOVED =
            "Servlet 6 removed this type without a syntax-only replacement; redesign the affected request, session, or concurrency contract before migrating the namespace";
    static final String SERVLET_RETURN_TYPE =
            "Servlet 6 removed HttpSession.getValueNames(); getAttributeNames() returns Enumeration<String> rather than String[], so adapt iteration, ordering, and array consumers explicitly";
    static final String SERVLET_COOKIE =
            "Servlet 6 removed this RFC 6265 Cookie getter; replace used return values deliberately (comment is absent and version is no longer part of the contract)";

    @Override
    public String getDisplayName() {
        return "Find Spring Web MVC 6.2 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed MVC APIs and type-attributed route, controller detection, method validation, exception, " +
               "interceptor, resource, functional routing, streaming, and Jakarta behavior changes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit java) || !(tree instanceof SourceFile source) ||
                    UpgradeSelectedSpringWebMvcDependency.generated(source.getSourcePath())) return tree;
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Import visitImport(J.Import anImport, ExecutionContext ec) {
                        J.Import i = super.visitImport(anImport, ec);
                        String type = i.getTypeName();
                        if (type.startsWith("org.springframework.web.servlet.view.tiles3.")) return mark(i, TILES);
                        if (REMOVED_TYPES.contains(type)) return mark(i, REMOVED);
                        if (REMOVED_SERVLET_TYPES.contains(type)) return mark(i, SERVLET_REMOVED);
                        if (type.startsWith("javax.servlet.") || type.startsWith("javax.validation.")) return mark(i, JAKARTA);
                        return i;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration,
                                                                     ExecutionContext ec) {
                        J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ec);
                        if (typeLevelRequestMappingOnly(cd)) return mark(cd, CONTROLLER);
                        if (controllerWithValidated(cd)) return mark(cd, VALIDATION);
                        if (TypeUtils.isAssignableTo(HANDLER_INTERCEPTOR, cd.getType())) return mark(cd, INTERCEPTOR);
                        if (cd.getExtends() != null && TypeUtils.isOfClassType(
                                cd.getExtends().getType(), RESPONSE_EXCEPTION_HANDLER)) return mark(cd, EXCEPTION);
                        if (cd.getExtends() != null && isTypeIn(cd.getExtends().getType(), REMOVED_SERVLET_TYPES)) {
                            return mark(cd, SERVLET_REMOVED);
                        }
                        return cd;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ec) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                        if (JAVAX_SESSION_VALUE_NAMES.matches(m) || JAKARTA_SESSION_VALUE_NAMES.matches(m)) {
                            return mark(m, SERVLET_RETURN_TYPE);
                        }
                        if (SERVLET_COOKIE_GETTERS.stream().anyMatch(matcher -> matcher.matches(m))) {
                            return mark(m, SERVLET_COOKIE);
                        }
                        if (RESPONSE_ENTITY_STATUS.matches(m)) return mark(m, STATUS);
                        if (PATH_MATCH.matches(m) && PATH_METHODS.contains(m.getSimpleName())) return mark(m, PATH);
                        if (CONTENT_NEGOTIATION.matches(m) && CONTENT_NEGOTIATION_METHODS.contains(m.getSimpleName())) return mark(m, PATH);
                        if (DISPATCHER.matches(m)) return mark(m, EXCEPTION);
                        if (RESOURCE_LOCATION.matches(m) || declaringTypeIn(m, Set.of(
                                "org.springframework.web.servlet.resource.WebJarsResourceResolver",
                                "org.springframework.web.servlet.resource.LiteWebJarsResourceResolver"))) return mark(m, RESOURCE);
                        if (ROUTER_ORDER.matches(m)) return mark(m, ROUTER);
                        if (declaringTypeIn(m, EMITTER_TYPES)) return mark(m, EMITTER);
                        return m;
                    }

                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                            ExecutionContext ec) {
                        J.VariableDeclarations variables = super.visitVariableDeclarations(multiVariable, ec);
                        return isTypeIn(variables.getType(), REMOVED_SERVLET_TYPES)
                                ? mark(variables, SERVLET_REMOVED) : variables;
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ec) {
                        J.NewClass n = super.visitNewClass(newClass, ec);
                        if (isTypeIn(n.getType(), REMOVED_SERVLET_TYPES)) return mark(n, SERVLET_REMOVED);
                        return TypeUtils.isOfClassType(n.getType(),
                                "org.springframework.web.servlet.function.support.RouterFunctionMapping")
                                ? mark(n, ROUTER) : n;
                    }

                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ec) {
                        J.Annotation a = super.visitAnnotation(annotation, ec);
                        String type = a.getType() == null ? "" : TypeUtils.asFullyQualified(a.getType()) == null ? "" :
                                TypeUtils.asFullyQualified(a.getType()).getFullyQualifiedName();
                        if (("org.springframework.web.bind.annotation.RequestParam".equals(type) ||
                             "org.springframework.web.bind.annotation.RequestHeader".equals(type) ||
                             "org.springframework.web.bind.annotation.CookieValue".equals(type)) && hasDefaultValue(a)) {
                            return mark(a, DEFAULT_VALUE);
                        }
                        if ("org.springframework.web.bind.annotation.ExceptionHandler".equals(type) &&
                            handlesNotFoundException(a)) return mark(a, EXCEPTION);
                        return a;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ec) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, ec);
                        J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                        if (owner != null && owner.getExtends() != null &&
                            TypeUtils.isOfClassType(owner.getExtends().getType(), RESPONSE_EXCEPTION_HANDLER) &&
                            m.getParameters().stream().anyMatch(parameter -> parameter instanceof J.VariableDeclarations vars &&
                                    TypeUtils.isOfClassType(vars.getType(), "org.springframework.http.HttpStatus"))) {
                            return mark(m, STATUS);
                        }
                        return m;
                    }
                }.visitNonNull(java, ctx);
            }
        };
    }

    private static boolean typeLevelRequestMappingOnly(J.ClassDeclaration cd) {
        boolean mapping = hasAnnotation(cd, "org.springframework.web.bind.annotation.RequestMapping");
        boolean controller = hasAnnotation(cd, "org.springframework.stereotype.Controller") ||
                             hasAnnotation(cd, "org.springframework.web.bind.annotation.RestController");
        return mapping && !controller;
    }

    private static boolean controllerWithValidated(J.ClassDeclaration cd) {
        return (hasAnnotation(cd, "org.springframework.stereotype.Controller") ||
                hasAnnotation(cd, "org.springframework.web.bind.annotation.RestController")) &&
               hasAnnotation(cd, "org.springframework.validation.annotation.Validated");
    }

    private static boolean hasAnnotation(J.ClassDeclaration cd, String type) {
        return cd.getLeadingAnnotations().stream().anyMatch(annotation -> TypeUtils.isOfClassType(annotation.getType(), type));
    }

    private static boolean hasDefaultValue(J.Annotation annotation) {
        return annotation.getArguments() != null && annotation.getArguments().stream().anyMatch(argument ->
                argument instanceof J.Assignment assignment &&
                assignment.getVariable() instanceof J.Identifier identifier &&
                "defaultValue".equals(identifier.getSimpleName()));
    }

    private static boolean declaringTypeIn(J.MethodInvocation method, Set<String> types) {
        JavaType.Method methodType = method.getMethodType();
        if (methodType == null) return false;
        return types.stream().anyMatch(type -> TypeUtils.isAssignableTo(type, methodType.getDeclaringType()));
    }

    private static boolean isTypeIn(JavaType type, Set<String> types) {
        return types.stream().anyMatch(candidate -> TypeUtils.isOfClassType(type, candidate));
    }

    private static boolean handlesNotFoundException(J.Annotation annotation) {
        boolean[] found = {false};
        new JavaIsoVisitor<boolean[]>() {
            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, boolean[] p) {
                J.FieldAccess f = super.visitFieldAccess(fieldAccess, p);
                if ("class".equals(f.getSimpleName()) && NOT_FOUND_EXCEPTIONS.stream()
                        .anyMatch(type -> TypeUtils.isOfClassType(f.getTarget().getType(), type))) p[0] = true;
                return f;
            }
        }.visit(annotation, found);
        return found[0];
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
