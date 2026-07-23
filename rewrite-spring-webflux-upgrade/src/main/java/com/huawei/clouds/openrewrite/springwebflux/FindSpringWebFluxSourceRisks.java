package com.huawei.clouds.openrewrite.springwebflux;

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
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;

import java.util.Set;

/** Type-aware markers for behavior-sensitive Spring WebFlux 6.0/6.1/6.2 changes. */
public final class FindSpringWebFluxSourceRisks extends Recipe {
    private static final String HTTP_METHOD = "org.springframework.http.HttpMethod";
    private static final String FLUX = "reactor.core.publisher.Flux";
    private static final String MONO = "reactor.core.publisher.Mono";
    private static final String OBSERVATION_FILTER =
            "org.springframework.web.filter.reactive.ServerHttpObservationFilter";
    private static final String WEBJARS_RESOLVER =
            "org.springframework.web.reactive.resource.WebJarsResourceResolver";
    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.web.bind.annotation.DeleteMapping"
    );
    private static final MethodMatcher DISPATCHER = new MethodMatcher(
            "org.springframework.web.reactive.DispatcherHandler setThrowExceptionIfNoHandlerFound(boolean)");
    private static final MethodMatcher RESOURCE_LOCATION = new MethodMatcher(
            "org.springframework.web.reactive.config.ResourceHandlerRegistration addResourceLocations(..)");
    private static final MethodMatcher MONO_EMPTY = new MethodMatcher("reactor.core.publisher.Mono empty()");

    static final String HTTP_METHOD_CHANGE =
            "Spring 6.0 changed HttpMethod from enum to class; replace switch/EnumSet assumptions with Set and equals/if logic while preserving unknown extension methods";
    static final String CONTROLLER =
            "Spring MVC and WebFlux 6 no longer detect a controller solely from type-level @RequestMapping; add @Controller/@RestController or register it explicitly";
    static final String KOTLIN_BODY =
            "Spring 6.0 removed KotlinBodySpec and expectBody now returns Java BodySpec; recompile Kotlin WebTestClient chains and replace KotlinBodySpec-only consumeWith assumptions";
    static final String FLUX_SEMANTICS =
            "Spring WebFlux 6 writes Flux values for non-streaming media types incrementally instead of collecting to List; verify JSON shape, framing, ordering, memory, cancellation, and negotiated media type";
    static final String VALIDATION =
            "Spring WebFlux 6.1 has built-in method validation; review class-level @Validated proxies, jakarta constraints, BindingResult/WebExchangeBindException, validation groups, and error bodies";
    static final String DEFAULT_VALUE =
            "In Spring 6.1 a non-empty but whitespace-only input can select this annotation defaultValue; regression-test blank, missing, and explicit values";
    static final String NOT_FOUND =
            "DispatcherHandler now raises and handles no-handler/no-resource failures as 404 by default; verify custom WebExceptionHandler, ProblemDetail, metrics, status, headers, and body";
    static final String OBSERVABILITY =
            "Spring 6.1 deprecated reactive ServerHttpObservationFilter in favor of direct WebHttpHandlerBuilder instrumentation; remove manual filter registration only after verifying tags, errors, cancellations, and ordering";
    static final String HTTP_INTERFACE =
            "Spring 6.1 removed the HTTP interface client's five-second blocking default; configure the underlying client or AbstractReactorHttpExchangeAdapter block timeout and test blocking signatures";
    static final String COROUTINES =
            "Spring 6.1 revised WebFlux coroutine context propagation, coRouter filters/context, suspending @ModelAttribute handling, and awaitSingle usage; regression-test cancellation and Reactor/Kotlin context";
    static final String EMPTY_MONO =
            "Spring WebFlux 6.2 no longer writes Content-Type for an empty response such as Mono.empty(); update HTTP contract tests and downstream clients that asserted this header";
    static final String RESOURCE =
            "Spring 6.2 normalizes String static-resource locations with a trailing slash and deprecates WebJarsResourceResolver for LiteWebJarsResourceResolver; verify containment, cache, encoding, and URLs";
    static final String EXCEPTION_NEGOTIATION =
            "Spring 6.2 applies content negotiation to @ExceptionHandler methods; test Accept-specific handler selection, media type, ProblemDetail, status, headers, and fallback behavior";
    static final String REACTOR_FACTORY =
            "ReactorResourceFactory moved from org.springframework.http.client.reactive to org.springframework.http.client in Spring 6.1; the deterministic recipe changes this exact attributed type";

    @Override
    public String getDisplayName() {
        return "Find Spring WebFlux 6.2 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact HttpMethod enum assumptions, controller discovery, Flux/Mono response semantics, " +
               "validation, 404, observability, HTTP interface timeout, coroutine, resource, and exception risks.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    SpringWebFluxSupport.generated(source.getSourcePath())) return tree;
                if (tree instanceof J.CompilationUnit java) return java(java, ctx);
                if (tree instanceof K.CompilationUnit kotlin) return kotlin(kotlin, ctx);
                return tree;
            }
        };
    }

    private static J.CompilationUnit java(J.CompilationUnit source, ExecutionContext ctx) {
        boolean webFlux = source.getImports().stream()
                .anyMatch(i -> i.getTypeName().startsWith("org.springframework.web.reactive.") ||
                               i.getTypeName().startsWith("org.springframework.web.filter.reactive."));
        return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ec) {
                J.Import i = super.visitImport(anImport, ec);
                String type = i.getTypeName();
                if ("org.springframework.test.web.reactive.server.KotlinBodySpec".equals(type)) {
                    return SpringWebFluxSupport.mark(i, KOTLIN_BODY);
                }
                if ("org.springframework.http.client.reactive.ReactorResourceFactory".equals(type)) {
                    return SpringWebFluxSupport.mark(i, REACTOR_FACTORY);
                }
                if (OBSERVATION_FILTER.equals(type)) {
                    return SpringWebFluxSupport.mark(i, OBSERVABILITY);
                }
                if (WEBJARS_RESOLVER.equals(type)) {
                    return SpringWebFluxSupport.mark(i, RESOURCE);
                }
                if (webFlux && coroutineImport(type)) {
                    return SpringWebFluxSupport.mark(i, COROUTINES);
                }
                return i;
            }

            @Override
            public J.Switch visitSwitch(J.Switch aSwitch, ExecutionContext ec) {
                J.Switch s = super.visitSwitch(aSwitch, ec);
                return TypeUtils.isOfClassType(s.getSelector().getTree().getType(), HTTP_METHOD)
                        ? SpringWebFluxSupport.mark(s, HTTP_METHOD_CHANGE) : s;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration,
                                                             ExecutionContext ec) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ec);
                if (typeLevelRequestMappingOnly(cd)) return SpringWebFluxSupport.mark(cd, CONTROLLER);
                if (controllerWithValidated(cd)) return SpringWebFluxSupport.mark(cd, VALIDATION);
                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                               ExecutionContext ec) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ec);
                if (handlerMethod(m) && !explicitStreamingOnly(m) &&
                    TypeUtils.isAssignableTo(FLUX, methodReturnType(m))) {
                    return SpringWebFluxSupport.mark(m, FLUX_SEMANTICS);
                }
                return m;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                             ExecutionContext ec) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (enumSetOfHttpMethod(m)) return SpringWebFluxSupport.mark(m, HTTP_METHOD_CHANGE);
                if (DISPATCHER.matches(m)) return SpringWebFluxSupport.mark(m, NOT_FOUND);
                if (RESOURCE_LOCATION.matches(m) || declaringType(m, WEBJARS_RESOLVER)) {
                    return SpringWebFluxSupport.mark(m, RESOURCE);
                }
                if (declaringType(m, OBSERVATION_FILTER)) {
                    return SpringWebFluxSupport.mark(m, OBSERVABILITY);
                }
                if (httpInterfaceInvocation(m)) {
                    return SpringWebFluxSupport.mark(m, HTTP_INTERFACE);
                }
                if (MONO_EMPTY.matches(m)) {
                    J.MethodDeclaration owner = getCursor().firstEnclosing(J.MethodDeclaration.class);
                    if (owner != null && handlerMethod(owner)) {
                        return SpringWebFluxSupport.mark(m, EMPTY_MONO);
                    }
                }
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ec) {
                J.NewClass n = super.visitNewClass(newClass, ec);
                if (TypeUtils.isOfClassType(n.getType(), OBSERVATION_FILTER)) {
                    return SpringWebFluxSupport.mark(n, OBSERVABILITY);
                }
                if (TypeUtils.isOfClassType(
                        n.getType(), "org.springframework.http.client.reactive.ReactorResourceFactory")) {
                    return SpringWebFluxSupport.mark(n, REACTOR_FACTORY);
                }
                if (TypeUtils.isOfClassType(n.getType(), WEBJARS_RESOLVER)) {
                    return SpringWebFluxSupport.mark(n, RESOURCE);
                }
                return n;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ec) {
                J.Annotation a = super.visitAnnotation(annotation, ec);
                String type = fullyQualified(a.getType());
                if (Set.of(
                        "org.springframework.web.bind.annotation.RequestParam",
                        "org.springframework.web.bind.annotation.RequestHeader",
                        "org.springframework.web.bind.annotation.CookieValue")
                        .contains(type) && hasDefaultValue(a)) {
                    return SpringWebFluxSupport.mark(a, DEFAULT_VALUE);
                }
                if ("org.springframework.web.bind.annotation.ExceptionHandler".equals(type)) {
                    return SpringWebFluxSupport.mark(a, EXCEPTION_NEGOTIATION);
                }
                if ("org.springframework.web.service.annotation.HttpExchange".equals(type)) {
                    return SpringWebFluxSupport.mark(a, HTTP_INTERFACE);
                }
                return a;
            }
        }.visitNonNull(source, ctx);
    }

    private static K.CompilationUnit kotlin(K.CompilationUnit source, ExecutionContext ctx) {
        boolean webFlux = source.getImports().stream()
                .anyMatch(i -> i.getTypeName().startsWith("org.springframework.web.reactive.") ||
                               i.getTypeName().startsWith("org.springframework.web.filter.reactive."));
        return (K.CompilationUnit) new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ec) {
                J.Import i = super.visitImport(anImport, ec);
                String type = i.getTypeName();
                if ("org.springframework.test.web.reactive.server.KotlinBodySpec".equals(type)) {
                    return SpringWebFluxSupport.mark(i, KOTLIN_BODY);
                }
                if ("org.springframework.http.client.reactive.ReactorResourceFactory".equals(type)) {
                    return SpringWebFluxSupport.mark(i, REACTOR_FACTORY);
                }
                if (OBSERVATION_FILTER.equals(type)) {
                    return SpringWebFluxSupport.mark(i, OBSERVABILITY);
                }
                if (WEBJARS_RESOLVER.equals(type)) {
                    return SpringWebFluxSupport.mark(i, RESOURCE);
                }
                if (webFlux && coroutineImport(type)) {
                    return SpringWebFluxSupport.mark(i, COROUTINES);
                }
                return i;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method,
                                                             ExecutionContext ec) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ec);
                if (DISPATCHER.matches(m)) return SpringWebFluxSupport.mark(m, NOT_FOUND);
                if (RESOURCE_LOCATION.matches(m) || declaringType(m, WEBJARS_RESOLVER)) {
                    return SpringWebFluxSupport.mark(m, RESOURCE);
                }
                if (declaringType(m, OBSERVATION_FILTER)) {
                    return SpringWebFluxSupport.mark(m, OBSERVABILITY);
                }
                return m;
            }
        }.visitNonNull(source, ctx);
    }

    private static boolean typeLevelRequestMappingOnly(J.ClassDeclaration cd) {
        return hasAnnotation(cd, "org.springframework.web.bind.annotation.RequestMapping") &&
               !hasAnnotation(cd, "org.springframework.stereotype.Controller") &&
               !hasAnnotation(cd, "org.springframework.web.bind.annotation.RestController");
    }

    private static boolean controllerWithValidated(J.ClassDeclaration cd) {
        return (hasAnnotation(cd, "org.springframework.stereotype.Controller") ||
                hasAnnotation(cd, "org.springframework.web.bind.annotation.RestController")) &&
               hasAnnotation(cd, "org.springframework.validation.annotation.Validated");
    }

    private static boolean handlerMethod(J.MethodDeclaration method) {
        return method.getLeadingAnnotations().stream()
                .map(annotation -> fullyQualified(annotation.getType()))
                .anyMatch(MAPPING_ANNOTATIONS::contains);
    }

    private static boolean explicitStreamingOnly(J.MethodDeclaration method) {
        for (J.Annotation annotation : method.getLeadingAnnotations()) {
            if (!MAPPING_ANNOTATIONS.contains(fullyQualified(annotation.getType())) ||
                annotation.getArguments() == null) continue;
            for (Expression argument : annotation.getArguments()) {
                if (argument instanceof J.Assignment assignment &&
                    assignment.getVariable() instanceof J.Identifier identifier &&
                    "produces".equals(identifier.getSimpleName())) {
                    return streamingExpression(assignment.getAssignment());
                }
            }
        }
        return false;
    }

    private static boolean streamingExpression(Expression expression) {
        if (expression instanceof J.Literal literal && literal.getValue() instanceof String value) {
            return "text/event-stream".equalsIgnoreCase(value) ||
                   "application/x-ndjson".equalsIgnoreCase(value) ||
                   "application/stream+json".equalsIgnoreCase(value);
        }
        if (expression instanceof J.FieldAccess field) {
            return Set.of("TEXT_EVENT_STREAM", "TEXT_EVENT_STREAM_VALUE",
                    "APPLICATION_NDJSON", "APPLICATION_NDJSON_VALUE",
                    "APPLICATION_STREAM_JSON", "APPLICATION_STREAM_JSON_VALUE")
                    .contains(field.getSimpleName());
        }
        if (expression instanceof J.NewArray array && array.getInitializer() != null &&
            !array.getInitializer().isEmpty()) {
            return array.getInitializer().stream().allMatch(FindSpringWebFluxSourceRisks::streamingExpression);
        }
        return false;
    }

    private static boolean hasAnnotation(J.ClassDeclaration cd, String type) {
        return cd.getLeadingAnnotations().stream()
                .anyMatch(annotation -> TypeUtils.isOfClassType(annotation.getType(), type));
    }

    private static JavaType methodReturnType(J.MethodDeclaration method) {
        if (method.getMethodType() != null) return method.getMethodType().getReturnType();
        return method.getReturnTypeExpression() == null ? null : method.getReturnTypeExpression().getType();
    }

    private static boolean enumSetOfHttpMethod(J.MethodInvocation method) {
        JavaType.Method type = method.getMethodType();
        if (type == null || !TypeUtils.isOfClassType(type.getDeclaringType(), "java.util.EnumSet")) return false;
        return method.getArguments().stream().filter(Expression.class::isInstance)
                .map(Expression.class::cast).map(Expression::getType)
                .anyMatch(argumentType -> TypeUtils.isOfClassType(argumentType, HTTP_METHOD));
    }

    private static boolean declaringType(J.MethodInvocation method, String type) {
        JavaType.Method methodType = method.getMethodType();
        return methodType != null && TypeUtils.isAssignableTo(type, methodType.getDeclaringType());
    }

    private static boolean httpInterfaceInvocation(J.MethodInvocation method) {
        JavaType.Method type = method.getMethodType();
        if (type == null) return false;
        return TypeUtils.isAssignableTo(
                       "org.springframework.web.service.invoker.AbstractReactorHttpExchangeAdapter",
                       type.getDeclaringType()) && "setBlockTimeout".equals(method.getSimpleName()) ||
               TypeUtils.isOfClassType(
                       type.getDeclaringType(),
                       "org.springframework.web.service.invoker.HttpServiceProxyFactory") &&
               "createClient".equals(method.getSimpleName());
    }

    private static boolean hasDefaultValue(J.Annotation annotation) {
        return annotation.getArguments() != null && annotation.getArguments().stream().anyMatch(argument ->
                argument instanceof J.Assignment assignment &&
                assignment.getVariable() instanceof J.Identifier identifier &&
                "defaultValue".equals(identifier.getSimpleName()));
    }

    private static boolean coroutineImport(String type) {
        return type.startsWith("org.springframework.web.reactive.function.server.Co") ||
               type.startsWith("org.springframework.web.reactive.function.server.coRouter") ||
               type.startsWith("kotlinx.coroutines.reactor.awaitSingle") ||
               type.startsWith("kotlinx.coroutines.reactor.mono");
    }

    private static String fullyQualified(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }
}
