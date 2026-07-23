package com.huawei.clouds.openrewrite.jakartawrs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate source decisions that cannot be safely inferred from syntax alone. */
public final class FindJakartaRest4SourceRisks extends Recipe {
    static final String JAXB_LINK =
            "Jakarta REST 4 removed Link.JaxbLink and Link.JaxbAdapter with the JAXB dependency; replace them with an " +
            "application-owned JAXB DTO/XmlAdapter and explicitly map URI plus link parameters";
    static final String JAXB =
            "Jakarta REST 4 no longer depends on or opens Link to Jakarta XML Binding; declare the JAXB API/runtime " +
            "explicitly and verify module opens, native-image reflection, QName parameters and XML round trips";
    static final String MANAGED_BEAN =
            "Jakarta REST 4 removed ManagedBean integration; migrate this resource/provider to CDI scope/injection and " +
            "verify discovery, proxyability, lifecycle, interceptors and concurrency";
    static final String SSE_CLOSE =
            "SseEventSink.close now declares IOException in Jakarta REST 4; define propagation/catch policy and verify " +
            "disconnect races, double close, broadcaster ownership, completion failures and resource cleanup";
    static final String RUNTIME_DELEGATE =
            "Custom RuntimeDelegate implementations from 3.0 and earlier must implement the 3.1+ bootstrap and EntityPart " +
            "factory methods; align the provider implementation and service descriptor with Jakarta REST 4";
    static final String CONTEXT =
            "The Jakarta REST 4 specification announces future removal of @Context injection; choose CDI injection or a " +
            "portable request abstraction and verify proxies, request scope, async/SSE handoff and nullability";
    static final String PROVIDER =
            "Provider/default-mapper/service-loading behavior changed since 3.0; verify priority, automatic registration, " +
            "jakarta.ws.rs.loadServices, zero-length entities, exception mapping and client/server runtime constraints";
    static final String SSE =
            "SSE is asynchronous and lifecycle-sensitive; verify send CompletionStage failures, ordering, backpressure, " +
            "disconnect, broadcaster registration/removal, executor ownership and close behavior on the target runtime";
    static final String CLIENT =
            "Client/WebTarget use crosses the Jakarta REST 3.1/4.0 provider boundary; verify AutoCloseable ownership, " +
            "timeouts, TLS/proxy/auth, executor shutdown, service loading and target implementation compatibility";
    static final String RESPONSE_CREATED =
            "Response.created resolves relative URIs against the base URI since Jakarta REST 3.1, not the request URI; " +
            "verify every relative Location value, forwarded/base URI configuration and proxy-visible redirect target";
    static final String NAMESPACE =
            "A Javax EE companion API remains beside migrated jakarta.ws.rs code; upgrade the owning API/implementation " +
            "coherently instead of creating a mixed javax/jakarta runtime";
    static final String MODULE =
            "JPMS module java.ws.rs was renamed jakarta.ws.rs and Jakarta REST 4 no longer requires jakarta.xml.bind; update " +
            "requires/opens directives and declare JAXB explicitly only where the application owns it";

    private static final Set<String> REMOVED_LINK_TYPES = Set.of(
            "jakarta.ws.rs.core.Link$JaxbLink", "jakarta.ws.rs.core.Link$JaxbAdapter",
            "jakarta.ws.rs.core.Link.JaxbLink", "jakarta.ws.rs.core.Link.JaxbAdapter",
            "javax.ws.rs.core.Link$JaxbLink", "javax.ws.rs.core.Link$JaxbAdapter");
    private static final Set<String> PROVIDER_TYPES = Set.of(
            "jakarta.ws.rs.ext.ExceptionMapper", "jakarta.ws.rs.ext.MessageBodyReader",
            "jakarta.ws.rs.ext.MessageBodyWriter", "jakarta.ws.rs.ext.ContextResolver",
            "jakarta.ws.rs.container.DynamicFeature");
    private static final Set<String> SSE_TYPES = Set.of(
            "jakarta.ws.rs.sse.SseEventSink", "jakarta.ws.rs.sse.SseBroadcaster",
            "jakarta.ws.rs.sse.SseEventSource", "jakarta.ws.rs.sse.Sse");
    private static final Set<String> COMPANION_JAVAX = Set.of(
            "javax.annotation.", "javax.inject.", "javax.servlet.", "javax.validation.", "javax.xml.bind.");

    @Override
    public String getDisplayName() {
        return "Find Jakarta REST 4 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed JAXB Link adapters, checked SSE close, ManagedBean, RuntimeDelegate implementors, @Context, " +
               "providers, clients, mixed namespaces and JPMS boundaries.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (UpgradeSelectedJakartaWsRsApiDependency.generated(cu.getSourcePath())) return cu;
                J.CompilationUnit c = super.visitCompilationUnit(cu, ctx);
                if ("module-info.java".equals(c.getSourcePath().getFileName().toString())) {
                    String printed = c.printAll();
                    if (printed.contains("requires java.ws.rs") || printed.contains("requires jakarta.xml.bind")) {
                        return mark(c, MODULE);
                    }
                }
                return c;
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import i = super.visitImport(anImport, ctx);
                String name = i.getTypeName();
                if (removedLink(name)) return mark(i, JAXB_LINK);
                if (name.startsWith("jakarta.xml.bind.") || name.startsWith("javax.xml.bind.")) return mark(i, JAXB);
                if (name.equals("jakarta.annotation.ManagedBean") || name.equals("javax.annotation.ManagedBean")) return mark(i, MANAGED_BEAN);
                if (COMPANION_JAVAX.stream().anyMatch(name::startsWith)) return mark(i, NAMESPACE);
                return i;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                String fqn = fqn(i.getType());
                if (REMOVED_LINK_TYPES.contains(fqn)) return mark(i, JAXB_LINK);
                if ("jakarta.ws.rs.sse.SseEventSink".equals(fqn) &&
                    getCursor().firstEnclosing(J.Try.Resource.class) != null) {
                    return mark(i, SSE_CLOSE);
                }
                return i;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                String type = fqn(a.getType());
                if (type.endsWith(".ManagedBean")) return mark(a, MANAGED_BEAN);
                if ("jakarta.ws.rs.core.Context".equals(type) || "javax.ws.rs.core.Context".equals(type)) return mark(a, CONTEXT);
                return a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method mt = m.getMethodType();
                String owner = mt == null ? "" : fqn(mt.getDeclaringType());
                if ("close".equals(m.getSimpleName()) && owner.endsWith(".ws.rs.sse.SseEventSink")) return mark(m, SSE_CLOSE);
                if (owner.startsWith("jakarta.ws.rs.sse.") &&
                    Set.of("send", "broadcast", "register", "close", "open", "isOpen").contains(m.getSimpleName())) {
                    return mark(m, SSE);
                }
                if (owner.startsWith("jakarta.ws.rs.client.") &&
                    Set.of("newClient", "newBuilder", "build", "target", "request", "register", "close").contains(m.getSimpleName())) {
                    return mark(m, CLIENT);
                }
                if ("jakarta.ws.rs.core.Response".equals(owner) && "created".equals(m.getSimpleName())) {
                    return mark(m, RESPONSE_CREATED);
                }
                return m;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if (!"close".equals(m.getSimpleName())) return m;
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                return owner != null && TypeUtils.isAssignableTo("jakarta.ws.rs.sse.SseEventSink", owner.getType())
                        ? mark(m, SSE_CLOSE) : m;
            }

            @Override
            public J.Try.Resource visitTryResource(J.Try.Resource resource, ExecutionContext ctx) {
                J.Try.Resource r = super.visitTryResource(resource, ctx);
                return TypeUtils.isOfClassType(r.getVariableDeclarations().getType(), "jakarta.ws.rs.sse.SseEventSink")
                        ? mark(r, SSE_CLOSE) : r;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                JavaType.FullyQualified type = c.getType();
                if (type == null) return c;
                if (TypeUtils.isAssignableTo("jakarta.ws.rs.ext.RuntimeDelegate", type)) return mark(c, RUNTIME_DELEGATE);
                if (PROVIDER_TYPES.stream().anyMatch(provider -> TypeUtils.isAssignableTo(provider, type))) return mark(c, PROVIDER);
                return c;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                String type = fqn(n.getType());
                if (REMOVED_LINK_TYPES.contains(type)) return mark(n, JAXB_LINK);
                if ("jakarta.ws.rs.ext.RuntimeDelegate".equals(type) && n.getBody() != null) return mark(n, RUNTIME_DELEGATE);
                return n;
            }
        };
    }

    private static boolean removedLink(String name) {
        return name.equals("jakarta.ws.rs.core.Link.JaxbLink") || name.equals("jakarta.ws.rs.core.Link.JaxbAdapter") ||
               name.equals("javax.ws.rs.core.Link.JaxbLink") || name.equals("javax.ws.rs.core.Link.JaxbAdapter");
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? "" : fq.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
