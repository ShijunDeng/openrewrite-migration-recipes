package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Locate source decisions that cannot be rewritten without application evidence. */
public final class FindTomcatCatalinaJavaRisks extends Recipe {
    static final String REMOVED_SERVLET =
            "Servlet 6 removed this deprecated Servlet 5 API and there is no syntax-only behavior-preserving replacement; " +
            "choose the replacement from request/session/error-handling semantics and rebuild against Servlet 6";
    static final String OVERRIDE =
            "Servlet 6 removed this deprecated interface method; remove the obsolete @Override/method after routing callers " +
            "to the non-deprecated API, taking care not to create a duplicate replacement method";
    static final String INTERNAL =
            "Tomcat 10.1 internal APIs are not binary compatible with 10.0; verify this import and every custom Valve, Realm, " +
            "Connector, Lifecycle, class-loader or container extension against the 10.1.56 JavaDoc";
    static final String APR =
            "Tomcat 10.1 removed the APR connector and most legacy tomcat-native JNI APIs; migrate to NIO/NIO2 plus supported " +
            "OpenSSL integration and validate TLS, sendfile, polling and shutdown behavior";
    static final String COOKIE =
            "Tomcat 10.1 implements Servlet 6 / RFC 6265 cookie behavior; version/comment APIs no longer select legacy cookie " +
            "specifications, so validate emitted attributes, SameSite policy, quoting and client compatibility";
    static final String HTTP_METHOD =
            "Tomcat 10.1.47 made HTTP method comparisons consistently case-sensitive; verify this case-insensitive application " +
            "branch, proxy normalization, authorization rules and tests with lower/mixed-case methods";
    static final String LEAK_LISTENER =
            "This JreMemoryLeakPreventionListener option was removed because its Java-8 leak no longer exists on Java 11; " +
            "remove the setter call or redesign code that reads the old flag";
    static final String NAMESPACE_STRING =
            "String-based javax Servlet/EL reference is not type-safe; identify its reflection, ServiceLoader, scanner, " +
            "serialization or framework owner before changing it to Jakarta";

    private static final Map<String, Set<String>> REMOVED_METHODS = Map.of(
            "jakarta.servlet.http.HttpServletResponse", Set.of("setStatus"),
            "jakarta.servlet.http.HttpSession", Set.of("getSessionContext", "getValueNames"),
            "jakarta.servlet.ServletContext", Set.of("getServlet", "getServlets", "getServletNames"),
            "jakarta.servlet.ServletRequest", Set.of("getRealPath"),
            "jakarta.servlet.UnavailableException", Set.of("getServlet"),
            "jakarta.servlet.http.HttpUtils", Set.of("getRequestURL", "parsePostData", "parseQueryString"));
    private static final Set<String> REMOVED_TYPES = Set.of(
            "jakarta.servlet.SingleThreadModel", "jakarta.servlet.http.HttpSessionContext",
            "jakarta.servlet.http.HttpUtils", "org.apache.catalina.util.Extension",
            "org.apache.catalina.util.ExtensionValidator", "org.apache.catalina.util.ManifestResource",
            "org.apache.coyote.ContainerThreadMarker", "org.apache.coyote.http11.Http11AprProtocol",
            "org.apache.coyote.ajp.AjpAprProtocol");
    private static final Set<String> COOKIE_METHODS = Set.of("getComment", "setComment", "getVersion", "setVersion");
    private static final Set<String> REMOVED_LEAK_OPTIONS = Set.of(
            "isAWTThreadProtection", "setAWTThreadProtection", "isGcDaemonProtection", "setGcDaemonProtection",
            "isLdapPoolProtection", "setLdapPoolProtection", "isTokenPollerProtection", "setTokenPollerProtection",
            "isXmlParsingProtection", "setXmlParsingProtection", "getForkJoinCommonPoolProtection",
            "setForkJoinCommonPoolProtection");
    @Override
    public String getDisplayName() {
        return "Find Tomcat Catalina 10.1 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed Servlet and Tomcat internals, APR/native usage, obsolete listener options, RFC 6265 cookie " +
               "assumptions, case-insensitive HTTP method logic, and removed interface implementations.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedTomcatCatalinaDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String imported = visited.getQualid().printTrimmed();
                if (apr(imported)) return mark(visited, APR);
                String normalized = normalizeServletType(imported);
                if (REMOVED_TYPES.stream().anyMatch(type -> normalized.equals(type) || normalized.startsWith(type + "."))) {
                    return mark(visited, REMOVED_SERVLET);
                }
                if (internal(imported)) return mark(visited, INTERNAL);
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null) return visited;
                String owner = normalizeServletType(owner(method.getDeclaringType()));
                String name = method.getName();
                if ("jakarta.servlet.http.HttpServletResponse".equals(owner) && "setStatus".equals(name) &&
                    visited.getArguments().size() != 2) return visited;
                if (REMOVED_METHODS.getOrDefault(owner, Set.of()).contains(name)) return mark(visited, REMOVED_SERVLET);
                if ("jakarta.servlet.http.Cookie".equals(owner) && COOKIE_METHODS.contains(name)) return mark(visited, COOKIE);
                if ("org.apache.catalina.core.JreMemoryLeakPreventionListener".equals(owner) &&
                    REMOVED_LEAK_OPTIONS.contains(name)) return mark(visited, LEAK_LISTENER);
                if ("equalsIgnoreCase".equals(name) && httpMethodComparison(visited)) return mark(visited, HTTP_METHOD);
                if (apr(owner)) return mark(visited, APR);
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = normalizeServletType(owner(visited.getType()));
                if (apr(type)) return mark(visited, APR);
                if (REMOVED_TYPES.contains(type)) return mark(visited, REMOVED_SERVLET);
                if ("jakarta.servlet.UnavailableException".equals(type) &&
                    visited.getArguments().stream().map(Expression::getType)
                            .anyMatch(argument -> TypeUtils.isAssignableTo("jakarta.servlet.Servlet", argument) ||
                                                  TypeUtils.isAssignableTo("javax.servlet.Servlet", argument))) {
                    return mark(visited, REMOVED_SERVLET);
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                return methodType != null && obsoleteServletOverride(methodType) ? mark(visited, OVERRIDE) : visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                return visited.getValue() instanceof String value &&
                       (value.contains("javax.servlet.") || value.contains("javax.el."))
                        ? mark(visited, NAMESPACE_STRING) : visited;
            }
        };
    }

    private static boolean obsoleteServletOverride(JavaType.Method method) {
        String name = method.getName();
        List<JavaType> parameters = method.getParameterTypes();
        JavaType.FullyQualified declaringType = method.getDeclaringType();
        if (assignableToServlet("jakarta.servlet.http.HttpServletRequest", declaringType) &&
            "isRequestedSessionIdFromUrl".equals(name) && parameters.isEmpty()) return true;
        if (assignableToServlet("jakarta.servlet.http.HttpServletResponse", declaringType)) {
            if (("encodeUrl".equals(name) || "encodeRedirectUrl".equals(name)) &&
                parameters(parameters, "java.lang.String")) return true;
            if ("setStatus".equals(name) && parameters.size() == 2 &&
                parameters.get(0) == JavaType.Primitive.Int && isType(parameters.get(1), "java.lang.String")) return true;
        }
        if (assignableToServlet("jakarta.servlet.http.HttpSession", declaringType)) {
            if (("getSessionContext".equals(name) || "getValueNames".equals(name)) && parameters.isEmpty()) return true;
            if (("getValue".equals(name) || "removeValue".equals(name)) &&
                parameters(parameters, "java.lang.String")) return true;
            if ("putValue".equals(name) && parameters(parameters, "java.lang.String", "java.lang.Object")) return true;
        }
        if (assignableToServlet("jakarta.servlet.ServletContext", declaringType)) {
            if (("getServlets".equals(name) || "getServletNames".equals(name)) && parameters.isEmpty()) return true;
            if ("getServlet".equals(name) && parameters(parameters, "java.lang.String")) return true;
            if ("log".equals(name) && parameters(parameters, "java.lang.Exception", "java.lang.String")) return true;
        }
        return assignableToServlet("jakarta.servlet.ServletRequest", declaringType) &&
               "getRealPath".equals(name) && parameters(parameters, "java.lang.String");
    }

    private static boolean assignableToServlet(String jakartaType, JavaType type) {
        return TypeUtils.isAssignableTo(jakartaType, type) ||
               TypeUtils.isAssignableTo(jakartaType.replace("jakarta.servlet", "javax.servlet"), type);
    }

    private static boolean parameters(List<JavaType> actual, String... expected) {
        if (actual.size() != expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (!isType(actual.get(index), expected[index])) return false;
        }
        return true;
    }

    private static boolean isType(JavaType type, String expected) {
        return TypeUtils.isOfClassType(type, expected);
    }

    private static boolean httpMethodComparison(J.MethodInvocation equalsIgnoreCase) {
        if (isRequestGetMethod(equalsIgnoreCase.getSelect())) return true;
        return equalsIgnoreCase.getArguments().stream().anyMatch(FindTomcatCatalinaJavaRisks::isRequestGetMethod);
    }

    private static boolean isRequestGetMethod(Expression expression) {
        if (!(expression instanceof J.MethodInvocation invocation) || invocation.getMethodType() == null ||
            !"getMethod".equals(invocation.getMethodType().getName())) return false;
        String owner = normalizeServletType(owner(invocation.getMethodType().getDeclaringType()));
        return "jakarta.servlet.ServletRequest".equals(owner) ||
               "jakarta.servlet.http.HttpServletRequest".equals(owner);
    }

    private static boolean apr(String value) {
        return value.startsWith("org.apache.tomcat.jni.") ||
               exactOrNested(value, "org.apache.coyote.http11.Http11AprProtocol") ||
               exactOrNested(value, "org.apache.coyote.ajp.AjpAprProtocol");
    }

    private static boolean exactOrNested(String value, String type) {
        return value.equals(type) || value.startsWith(type + ".") || value.startsWith(type + "$");
    }

    private static boolean internal(String imported) {
        return imported.startsWith("org.apache.catalina.") || imported.startsWith("org.apache.coyote.") ||
               imported.startsWith("org.apache.tomcat.");
    }

    private static String normalizeServletType(String type) {
        return type.startsWith("javax.servlet.")
                ? "jakarta.servlet." + type.substring("javax.servlet.".length()) : type;
    }

    private static String owner(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
