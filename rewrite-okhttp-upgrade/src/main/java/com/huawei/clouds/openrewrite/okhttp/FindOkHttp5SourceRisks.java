package com.huawei.clouds.openrewrite.okhttp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.marker.SearchResult;

import java.util.Map;
import java.util.Set;

/** Marks attributed OkHttp source constructs whose replacement depends on application semantics. */
public final class FindOkHttp5SourceRisks extends Recipe {
    private static final String INTERNAL =
            "okhttp3.internal is not a public API and is strongly encapsulated by OkHttp 5 JPMS modules; replace this exact dependency with a public API and verify shading, OSGi, native-image, and module-path execution";
    private static final String INHERITANCE =
            "OkHttpClient accessors became final; replace inheritance or client mocking with a Call.Factory, wrapper, interceptor, or a real client and verify timeout, dispatcher, pool, and lifecycle behavior";
    private static final String CREDENTIALS =
            "Credentials.basic username and password are non-null; decide whether missing credentials should fail, omit authentication, or use an explicit value instead of preserving the old string-null behavior";
    private static final String MOCK_WEB_SERVER =
            "The okhttp3.mockwebserver API is the obsolete JUnit 4-compatible module in OkHttp 5; deliberately choose mockwebserver3 core, its JUnit 4/5 integration, or temporary compatibility and review immutable request/response semantics";
    private static final String KOTLIN_STATIC =
            "This OkHttp Java-style static factory is not Kotlin source-compatible; choose the null-preserving toXxxOrNull extension or the throwing toXxx extension that matches existing behavior and overloads";
    private static final String QUERY_VALUES =
            "HttpUrl.queryParameterValues returns List<String?>; update Kotlin nullability deliberately before filtering, mapping, sorting, or assigning the result";
    private static final String EXPERIMENTAL =
            "This OkHttp experimental API crossed multiple incompatible alpha revisions before 5.3.0; consult the fixed 5.3.0 API and redesign the call instead of guessing a same-name replacement";
    private static final String CONNECTION =
            "OkHttp 5 changes dual-stack fast fallback and connection behavior; verify this exact builder choice with IPv4/IPv6 races, DNS failures, proxy callbacks, TLS/pinning, retries, cancellation, and EventListener ordering";

    private static final Set<String> KOTLIN_STATIC_OWNERS = Set.of(
            "okhttp3.HttpUrl", "okhttp3.MediaType", "okhttp3.RequestBody", "okhttp3.ResponseBody"
    );
    private static final Set<String> KOTLIN_STATIC_NAMES = Set.of("parse", "get", "create");
    private static final Set<String> CONNECTION_METHODS = Set.of(
            "dns", "proxy", "proxySelector", "certificatePinner", "connectionSpecs", "fastFallback",
            "retryOnConnectionFailure"
    );
    private static final Map<String, String> TYPE_MESSAGES = Map.ofEntries(
            Map.entry("okhttp3.AddressPolicy", EXPERIMENTAL),
            Map.entry("okhttp3.AsyncDns", EXPERIMENTAL),
            Map.entry("okhttp3.ConnectionListener", EXPERIMENTAL),
            Map.entry("mockwebserver3.SocketPolicy", EXPERIMENTAL),
            Map.entry("mockwebserver3.RecordedRequest", EXPERIMENTAL)
    );

    @Override
    public String getDisplayName() {
        return "Find OkHttp 5 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark attributed internal, inheritance, nullable credentials, Kotlin factory/nullability, legacy " +
               "MockWebServer, experimental alpha, and connection-behavior boundaries with decision-specific reasons.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    UpgradeSelectedOkHttpDependency.generated(source.getSourcePath())) return tree;
                if (tree instanceof J.CompilationUnit java) return javaVisitor(false).visitNonNull(java, ctx);
                if (tree instanceof K.CompilationUnit kotlin) return kotlinVisitor().visitNonNull(kotlin, ctx);
                return tree;
            }
        };
    }

    private static JavaIsoVisitor<ExecutionContext> javaVisitor(boolean kotlin) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                return inspectImport(super.visitImport(anImport, ctx));
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                return inspectClass(super.visitClassDeclaration(classDecl, ctx));
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                return getCursor().firstEnclosing(J.Import.class) == null ? inspectIdentifier(visited) : visited;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                return inspectAnnotation(super.visitAnnotation(annotation, ctx));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                return inspectMethod(super.visitMethodInvocation(method, ctx), kotlin);
            }
        };
    }

    private static KotlinIsoVisitor<ExecutionContext> kotlinVisitor() {
        return new KotlinIsoVisitor<ExecutionContext>() {
            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                return inspectImport(super.visitImport(anImport, ctx));
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                return inspectClass(super.visitClassDeclaration(classDecl, ctx));
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                return getCursor().firstEnclosing(J.Import.class) == null ? inspectIdentifier(visited) : visited;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                return inspectAnnotation(super.visitAnnotation(annotation, ctx));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                return inspectMethod(super.visitMethodInvocation(method, ctx), true);
            }
        };
    }

    private static J.Import inspectImport(J.Import anImport) {
        String imported = anImport.getQualid().printTrimmed();
        if (imported.startsWith("okhttp3.internal.")) return mark(anImport, INTERNAL);
        if (imported.startsWith("okhttp3.mockwebserver.")) return mark(anImport, MOCK_WEB_SERVER);
        if (imported.startsWith("okhttp3.coroutines.")) return mark(anImport, EXPERIMENTAL);
        return anImport;
    }

    private static J.ClassDeclaration inspectClass(J.ClassDeclaration classDecl) {
        TypeTree extended = classDecl.getExtends();
        return extended != null && TypeUtils.isOfClassType(extended.getType(), "okhttp3.OkHttpClient")
                ? mark(classDecl, INHERITANCE) : classDecl;
    }

    private static J.Identifier inspectIdentifier(J.Identifier identifier) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(identifier.getType());
        if (type == null || !identifier.getSimpleName().equals(type.getClassName())) return identifier;
        String fqn = type.getFullyQualifiedName();
        if (fqn.startsWith("okhttp3.internal.")) return mark(identifier, INTERNAL);
        if (fqn.startsWith("okhttp3.mockwebserver.")) return mark(identifier, MOCK_WEB_SERVER);
        String message = TYPE_MESSAGES.get(fqn);
        return message == null ? identifier : mark(identifier, message);
    }

    private static J.Annotation inspectAnnotation(J.Annotation annotation) {
        return TypeUtils.isOfClassType(annotation.getType(), "okhttp3.ExperimentalOkHttpApi")
                ? mark(annotation, EXPERIMENTAL) : annotation;
    }

    private static J.MethodInvocation inspectMethod(J.MethodInvocation method, boolean kotlin) {
        JavaType.Method type = method.getMethodType();
        if (type == null || type.getDeclaringType() == null) return method;
        String owner = type.getDeclaringType().getFullyQualifiedName();
        if (owner.endsWith("$Companion")) owner = owner.substring(0, owner.length() - 10);
        String name = type.getName();
        if ("okhttp3.Credentials".equals(owner) && "basic".equals(name) &&
            method.getArguments().stream().anyMatch(FindOkHttp5SourceRisks::isNull)) {
            return mark(method, CREDENTIALS);
        }
        if (kotlin && KOTLIN_STATIC_OWNERS.contains(owner) && KOTLIN_STATIC_NAMES.contains(name)) {
            return mark(method, KOTLIN_STATIC);
        }
        if (kotlin && "okhttp3.HttpUrl".equals(owner) && "queryParameterValues".equals(name)) {
            return mark(method, QUERY_VALUES);
        }
        if ("okhttp3.OkHttpClient$Builder".equals(owner) && CONNECTION_METHODS.contains(name)) {
            return mark(method, CONNECTION);
        }
        if (("org.mockito.Mockito".equals(owner) || "org.mockito.Mockito$MockitoCore".equals(owner)) &&
            ("mock".equals(name) || "spy".equals(name)) && !method.getArguments().isEmpty() &&
            isOkHttpClassLiteral(method.getArguments().get(0))) {
            return mark(method, INHERITANCE);
        }
        return method;
    }

    private static boolean isNull(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() == null;
    }

    private static boolean isOkHttpClassLiteral(Expression expression) {
        return expression instanceof J.FieldAccess field && "class".equals(field.getSimpleName()) &&
               TypeUtils.isOfClassType(field.getTarget().getType(), "okhttp3.OkHttpClient");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
