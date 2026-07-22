package com.huawei.clouds.openrewrite.shedlockspring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Type-aware review markers for ShedLock Spring 7 proxy and locking semantics. */
public final class FindShedLockSpring7JavaRisks extends Recipe {
    private static final String SCHEDULER_LOCK =
            "net.javacrumbs.shedlock.spring.annotation.SchedulerLock";
    private static final String ENABLE_SCHEDULER_LOCK =
            "net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock";
    private static final String LOCK_PROVIDER_TO_USE =
            "net.javacrumbs.shedlock.spring.annotation.LockProviderToUse";
    private static final String LOCK_PROVIDER = "net.javacrumbs.shedlock.core.LockProvider";
    private static final String LOCK_CONFIGURATION = "net.javacrumbs.shedlock.core.LockConfiguration";
    private static final String KEEP_ALIVE = "net.javacrumbs.shedlock.support.KeepAliveLockProvider";
    private static final Set<String> OTHER_ADVICE = Set.of(
            "org.springframework.scheduling.annotation.Async",
            "org.springframework.transaction.annotation.Transactional",
            "org.springframework.retry.annotation.Retryable");
    private static final Set<String> DURATION_ATTRIBUTES = Set.of(
            "lockAtMostFor", "lockAtLeastFor", "defaultLockAtMostFor", "defaultLockAtLeastFor");
    private static final Pattern SIMPLE_DURATION = Pattern.compile("^([+-]?\\d+)(ns|us|ms|s|m|h|d)?$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getDisplayName() {
        return "Find ShedLock Spring 7 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark PROXY_METHOD boundaries, multiple provider selection, duration/SpEL and clock assumptions, " +
               "KeepAlive, reactive/virtual-thread execution, AOP order, and LockException behavior.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private List<JavaType.Method> lockedMethods = List.of();
            private int providerBeans;
            private boolean primaryProvider;
            private boolean shedLockSource;

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                shedLockSource = compilationUnit.printAll().contains("net.javacrumbs.shedlock");
                List<JavaType.Method> methods = new ArrayList<>();
                int[] providers = {0};
                boolean[] primary = {false};
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                                       ExecutionContext executionContext) {
                        J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);
                        if (hasAnnotation(m, SCHEDULER_LOCK) && m.getMethodType() != null) {
                            methods.add(m.getMethodType());
                        }
                        if (isLockProviderBean(m)) {
                            providers[0]++;
                            primary[0] |= hasAnnotation(m, "org.springframework.context.annotation.Primary");
                        }
                        return m;
                    }
                }.visit(compilationUnit, ctx);
                lockedMethods = methods;
                providerBeans = providers[0];
                primaryProvider = primary[0];
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import i = super.visitImport(anImport, ctx);
                String imported = i.getQualid().printTrimmed(getCursor());
                if (shedLockSource && (imported.startsWith("javax.annotation.") ||
                                      imported.startsWith("javax.persistence.") ||
                                      imported.startsWith("javax.validation."))) {
                    return SearchResult.found(i,
                            "ShedLock 7 runs with Spring 6.2/7; migrate this framework-facing javax API to its Jakarta equivalent");
                }
                return i;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDeclaration, ctx);
                return c.getType() != null && TypeUtils.isAssignableTo(LOCK_PROVIDER, c.getType())
                        ? SearchResult.found(c,
                        "Custom LockProvider: retest LockException propagation, unavailable-lock vs provider-error handling, clock source and extension support")
                        : c;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (providerBeans > 1 && !primaryProvider && isLockProviderBean(m)) {
                    return SearchResult.found(m,
                            "Multiple LockProvider beans detected; select one with @Primary or bind each scheduled path using @LockProviderToUse") ;
                }
                if (!hasAnnotation(m, SCHEDULER_LOCK)) {
                    return m;
                }
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                boolean finalClass = enclosing != null && enclosing.hasModifier(J.Modifier.Type.Final);
                if (m.hasModifier(J.Modifier.Type.Private) || m.hasModifier(J.Modifier.Type.Final) ||
                    m.hasModifier(J.Modifier.Type.Static) || finalClass) {
                    return SearchResult.found(m,
                            "PROXY_METHOD cannot reliably advise private/static/final methods or methods on a final proxy target; make the scheduled boundary proxyable");
                }
                JavaType returnType = m.getReturnTypeExpression() == null ? null : m.getReturnTypeExpression().getType();
                if (returnType instanceof JavaType.Primitive primitive && primitive != JavaType.Primitive.Void) {
                    return SearchResult.found(m,
                            "ShedLock PROXY_METHOD rejects primitive non-void return types; use void, Optional, or an explicit locking boundary");
                }
                JavaType.FullyQualified returnClass = TypeUtils.asFullyQualified(returnType);
                if (returnClass != null && isReactiveOrAsyncType(returnClass.getFullyQualifiedName())) {
                    return SearchResult.found(m,
                            "The proxy locks Publisher/future creation, not necessarily asynchronous execution; move the lock around the actual work and test cancellation/error paths");
                }
                return m;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                String fqn = annotationType(a);
                if (ENABLE_SCHEDULER_LOCK.equals(fqn)) {
                    boolean proxyScheduler = a.getArguments().stream().filter(J.Assignment.class::isInstance)
                            .map(J.Assignment.class::cast).filter(arg -> "interceptMode".equals(assignmentName(arg)))
                            .map(J.Assignment::getAssignment).map(value -> value.printTrimmed(getCursor()))
                            .anyMatch(value -> value.endsWith("PROXY_SCHEDULER"));
                    boolean explicitMode = a.getArguments().stream().filter(J.Assignment.class::isInstance)
                            .map(J.Assignment.class::cast).anyMatch(arg -> "interceptMode".equals(assignmentName(arg)));
                    List<Expression> args = a.getArguments().stream().map(argument -> {
                        if (argument instanceof J.Assignment assignment && "order".equals(assignmentName(assignment))) {
                            return SearchResult.found(assignment,
                                    "Explicit ShedLock advisor order: verify ordering against transactions, retries, async and security advice");
                        }
                        return argument;
                    }).toList();
                    a = a.withArguments(args);
                    if (proxyScheduler) {
                        return SearchResult.found(a,
                                "PROXY_SCHEDULER is deprecated for removal and uses a Spring 6.2 reflection workaround; migrate deliberately to PROXY_METHOD");
                    }
                    if (!explicitMode) {
                        return SearchResult.found(a,
                                "ShedLock 7 defaults to PROXY_METHOD; direct calls are locked too, so verify proxyability, self-invocation and advisor order");
                    }
                    return a;
                }
                if (SCHEDULER_LOCK.equals(fqn)) {
                    J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
                    J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (providerBeans > 1 && !primaryProvider && !hasAnnotation(method, LOCK_PROVIDER_TO_USE) &&
                        !hasAnnotation(enclosing, LOCK_PROVIDER_TO_USE)) {
                        return SearchResult.found(a,
                                "Multiple LockProvider beans exist but this lock has no @LockProviderToUse selection; runtime execution can fail");
                    }
                    boolean hasAtMost = a.getArguments().stream().filter(J.Assignment.class::isInstance)
                            .map(J.Assignment.class::cast).anyMatch(arg -> "lockAtMostFor".equals(assignmentName(arg)));
                    if (!hasAtMost) {
                        return SearchResult.found(a,
                                "This lock inherits defaultLockAtMostFor; verify the default exceeds the worst-case task duration");
                    }
                    return a;
                }
                if (LOCK_PROVIDER_TO_USE.equals(fqn)) {
                    return SearchResult.found(a,
                            "Verify the selected LockProvider bean name exists for every method, type and package path; resolution fails at execution time");
                }
                if (OTHER_ADVICE.contains(fqn)) {
                    J.MethodDeclaration method = getCursor().firstEnclosing(J.MethodDeclaration.class);
                    if (hasAnnotation(method, SCHEDULER_LOCK)) {
                        return SearchResult.found(a,
                                "ShedLock shares this join point with another advisor; integration-test ordering, exception propagation and thread hand-off");
                    }
                }
                return a;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment a = super.visitAssignment(assignment, ctx);
                J.Annotation annotation = getCursor().firstEnclosing(J.Annotation.class);
                if (annotation == null || (!SCHEDULER_LOCK.equals(annotationType(annotation)) &&
                                           !ENABLE_SCHEDULER_LOCK.equals(annotationType(annotation)))) {
                    return a;
                }
                String name = assignmentName(a);
                if ("name".equals(name) && a.getAssignment() instanceof J.Literal literal &&
                    literal.getValue() instanceof String value && isExpression(value)) {
                    return SearchResult.found(a,
                            "Dynamic lock name uses a placeholder/SpEL expression; validate parameter names, bean access, uniqueness and failure behavior");
                }
                if (!DURATION_ATTRIBUTES.contains(name)) {
                    return a;
                }
                if (!(a.getAssignment() instanceof J.Literal literal) || !(literal.getValue() instanceof String value)) {
                    return SearchResult.found(a,
                            "Computed ShedLock duration requires manual validation as a non-negative millisecond/simple/ISO-8601 value");
                }
                if (isExpression(value)) {
                    return SearchResult.found(a,
                            "Duration uses a placeholder/SpEL expression; validate resolution, parse failures, negativity and atLeast <= atMost");
                }
                ParsedDuration parsed = parseDuration(value);
                if (!parsed.valid || parsed.duration.isNegative()) {
                    return SearchResult.found(a,
                            "Invalid ShedLock 7 duration; use a non-negative millisecond, simple-unit or ISO-8601 value");
                }
                if (name.contains("AtMost")) {
                    return SearchResult.found(a,
                            "lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks");
                }
                return SearchResult.found(a,
                        "lockAtLeastFor relies on synchronized clocks and must not exceed lockAtMostFor; verify skip and short-task behavior");
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(n.getType());
                if (type == null) {
                    return n;
                }
                if (KEEP_ALIVE.equals(type.getFullyQualifiedName())) {
                    return SearchResult.found(n,
                            "KeepAlive extends at the interval midpoint, requires lockAtMostFor >= 30s, an extensible provider and a managed scheduler; test shutdown and extension failure");
                }
                if (LOCK_CONFIGURATION.equals(type.getFullyQualifiedName())) {
                    return SearchResult.found(n, n.getArguments().size() == 4
                            ? "Verify createdAt, Duration bounds and the provider clock for this manual lock configuration"
                            : "Legacy LockConfiguration constructor detected; ShedLock 7 requires createdAt, name, lockAtMostFor Duration and lockAtLeastFor Duration");
                }
                return n;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (isSelfInvocationOfLockedMethod(m)) {
                    return SearchResult.found(m,
                            "Self-invocation bypasses the Spring PROXY_METHOD advisor; call through a proxied collaborator or move the locked boundary");
                }
                J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (hasAnnotation(enclosing, SCHEDULER_LOCK) && "subscribe".equals(m.getSimpleName())) {
                    return SearchResult.found(m,
                            "Reactive work is subscribed asynchronously inside the locked method; the lock can be released before the pipeline completes");
                }
                JavaType.Method methodType = m.getMethodType();
                JavaType.FullyQualified owner = methodType == null ? null :
                        TypeUtils.asFullyQualified(methodType.getDeclaringType());
                if (owner == null && m.getSelect() != null) {
                    // JDK 17 can still attribute the static owner even though virtual-thread factory
                    // methods were added in JDK 21 and therefore have no JavaType.Method there.
                    owner = TypeUtils.asFullyQualified(m.getSelect().getType());
                }
                String ownerName = owner == null ? "" : owner.getFullyQualifiedName();
                if ("lock".equals(m.getSimpleName()) && owner != null && TypeUtils.isAssignableTo(LOCK_PROVIDER, owner)) {
                    return SearchResult.found(m,
                            "LockProvider.lock returns empty when unavailable but ShedLock 7 providers throw LockException on unexpected errors; review retries and catch clauses");
                }
                if (("extendActiveLock".equals(m.getSimpleName()) && ownerName.endsWith(".LockExtender")) ||
                    ("extend".equals(m.getSimpleName()) && ownerName.endsWith(".SimpleLock"))) {
                    return SearchResult.found(m,
                            "Lock extension replaces the active SimpleLock and uses thread-local context; verify one-time unlock, provider support and thread hand-off");
                }
                boolean threadFactory = "ofVirtual".equals(m.getSimpleName()) &&
                                        isStaticOwner(m, ownerName, "java.lang.Thread", "Thread");
                boolean executorFactory = "newVirtualThreadPerTaskExecutor".equals(m.getSimpleName()) &&
                                          isStaticOwner(m, ownerName, "java.util.concurrent.Executors", "Executors");
                if (threadFactory || executorFactory) {
                    return SearchResult.found(m,
                            "Virtual-thread hand-off detected; verify the lock covers actual task completion and do not rely on LockAssert/LockExtender ThreadLocal state across threads");
                }
                return m;
            }

            private boolean isSelfInvocationOfLockedMethod(J.MethodInvocation invocation) {
                if (invocation.getMethodType() == null || lockedMethods.stream().noneMatch(invocation.getMethodType()::equals)) {
                    return false;
                }
                Expression select = invocation.getSelect();
                return select == null || "this".equals(select.printTrimmed(getCursor()));
            }

            private boolean isStaticOwner(J.MethodInvocation invocation, String attributedOwner,
                                          String fullyQualifiedOwner, String simpleOwner) {
                if (fullyQualifiedOwner.equals(attributedOwner)) {
                    return true;
                }
                if (!attributedOwner.isEmpty() || invocation.getSelect() == null) {
                    return false;
                }
                String select = invocation.getSelect().printTrimmed(getCursor());
                if (fullyQualifiedOwner.equals(select)) {
                    return true;
                }
                if (!simpleOwner.equals(select)) {
                    return false;
                }
                if (fullyQualifiedOwner.startsWith("java.lang.")) {
                    return true;
                }
                J.CompilationUnit compilationUnit = getCursor().firstEnclosing(J.CompilationUnit.class);
                return compilationUnit != null && compilationUnit.getImports().stream()
                        .anyMatch(anImport -> fullyQualifiedOwner.equals(anImport.getQualid().printTrimmed()));
            }
        };
    }

    private static boolean isLockProviderBean(J.MethodDeclaration method) {
        return method != null && hasAnnotation(method, "org.springframework.context.annotation.Bean") &&
               method.getReturnTypeExpression() != null &&
               TypeUtils.isAssignableTo(LOCK_PROVIDER, method.getReturnTypeExpression().getType());
    }

    private static boolean hasAnnotation(J.MethodDeclaration method, String fqn) {
        return method != null && method.getLeadingAnnotations().stream().anyMatch(a -> fqn.equals(annotationType(a)));
    }

    private static boolean hasAnnotation(J.ClassDeclaration type, String fqn) {
        return type != null && type.getLeadingAnnotations().stream().anyMatch(a -> fqn.equals(annotationType(a)));
    }

    private static String annotationType(J.Annotation annotation) {
        JavaType.FullyQualified type = annotation == null ? null : TypeUtils.asFullyQualified(annotation.getType());
        return type == null ? "" : type.getFullyQualifiedName();
    }

    private static String assignmentName(J.Assignment assignment) {
        return assignment.getVariable() instanceof J.Identifier identifier ? identifier.getSimpleName() : "";
    }

    private static boolean isExpression(String value) {
        return value.contains("${") || value.contains("#{");
    }

    private static boolean isReactiveOrAsyncType(String fqn) {
        return fqn.startsWith("reactor.core.publisher.") || fqn.startsWith("org.reactivestreams.") ||
               fqn.startsWith("java.util.concurrent.CompletionStage") ||
               fqn.startsWith("java.util.concurrent.CompletableFuture");
    }

    private static ParsedDuration parseDuration(String value) {
        try {
            if (value.toUpperCase(Locale.ROOT).startsWith("P") || value.startsWith("+P") || value.startsWith("-P")) {
                return new ParsedDuration(true, Duration.parse(value));
            }
            Matcher matcher = SIMPLE_DURATION.matcher(value);
            if (!matcher.matches()) {
                return ParsedDuration.INVALID;
            }
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2) == null ? "ms" : matcher.group(2).toLowerCase(Locale.ROOT);
            ChronoUnit chronoUnit = switch (unit) {
                case "ns" -> ChronoUnit.NANOS;
                case "us" -> ChronoUnit.MICROS;
                case "ms" -> ChronoUnit.MILLIS;
                case "s" -> ChronoUnit.SECONDS;
                case "m" -> ChronoUnit.MINUTES;
                case "h" -> ChronoUnit.HOURS;
                case "d" -> ChronoUnit.DAYS;
                default -> throw new IllegalArgumentException("Unsupported unit");
            };
            return new ParsedDuration(true, Duration.of(amount, chronoUnit));
        } catch (RuntimeException ignored) {
            return ParsedDuration.INVALID;
        }
    }

    private record ParsedDuration(boolean valid, Duration duration) {
        private static final ParsedDuration INVALID = new ParsedDuration(false, Duration.ZERO);
    }
}
