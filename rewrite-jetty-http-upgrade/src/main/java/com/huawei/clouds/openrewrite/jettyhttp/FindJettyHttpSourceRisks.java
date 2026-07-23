package com.huawei.clouds.openrewrite.jettyhttp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Set;

/** Type-attributed markers for Jetty 9/11 to 12 HTTP and server API boundaries. */
public final class FindJettyHttpSourceRisks extends Recipe {
    private static final String HTTP = "org.eclipse.jetty.http.";
    private static final Set<String> REMOVED_TYPES = Set.of(
            HTTP + "MultiPartFormInputStream", HTTP + "MultiPartParser", HTTP + "PathMap",
            HTTP + "HttpComplianceSection");
    private static final Set<String> CONTENT_TYPES = Set.of(
            HTTP + "HttpContent", HTTP + "ResourceHttpContent", HTTP + "PrecompressedHttpContent",
            HTTP + "content.HttpContent", HTTP + "content.ResourceHttpContent",
            HTTP + "content.PreCompressedHttpContent");
    private static final Set<String> REMOVED_CONTENT_METHODS = Set.of(
            "getIndirectBuffer", "getDirectBuffer", "getInputStream", "getReadableByteChannel",
            "getPrecompressedContents");
    private static final Set<String> MIGRATED_CONTENT_METHODS = Set.of("getByteBuffer");
    private static final Set<String> HEADER_MUTATORS = Set.of(
            "add", "put", "remove", "clear", "addCSV", "putDateField", "putLongField");

    static final String REMOVED =
            "Jetty 12 删除或重构了该 jetty-http 类型（MultiPartFormInputStream/MultiPartParser/PathMap/" +
            "HttpComplianceSection）；请选择 Jetty 12 MultiPart、pathmap 或 ComplianceViolation API，并重写生命周期测试";
    static final String CONTENT =
            "Jetty 12 将 HttpContent 系列移到 org.eclipse.jetty.http.content，并删除 direct/indirect buffer、" +
            "InputStream/channel 与 precompressed map 契约；请迁移到 getByteBuffer()/Content.Source、" +
            "PreCompressedContentFormats，并验证 release、缓存、ETag/range 与异步写入";
    static final String HEADERS =
            "Jetty 9 的可变 HttpFields 构造/修改模型在 Jetty 11/12 改为 HttpFields.Mutable/HttpFields.build()；" +
            "请验证重复 header、CSV、大小写、顺序、只读视图、trailer 和线程所有权";
    static final String URI =
            "Jetty 12 HttpURI 使用 immutable/build 模型并收紧 ambiguous path、encoding、authority 与 URI compliance；" +
            "请替换旧构造/set* 逻辑并覆盖 encoded slash、dot segment、userinfo、host/port 和代理原始 URI";
    static final String COOKIE =
            "Jetty 12 HttpCookie 是接口并使用 HttpCookie.build(...)；请迁移构造/可变 setter，验证 SameSite、" +
            "Partitioned、domain/path/max-age、重复属性及 RFC6265/RFC6265bis compliance";
    static final String PARSER =
            "Jetty HttpParser/HttpGenerator 的 handler、compliance、EOF、chunk、header limit 与 bad-message 行为跨主版本变化；" +
            "请用分片/畸形输入、请求走私、TE+CL、100-continue、trailer 和连接复用测试自定义 parser/generator";
    static final String COMPLIANCE =
            "Jetty 12 的 HttpCompliance/UriCompliance/CookieCompliance 与 ComplianceViolation listener/default 集合发生变化；" +
            "请显式选择模式，不要依赖旧 LEGACY/RFC7230 默认，并记录安全例外";
    static final String MIME_DATE =
            "Jetty 12 的 MimeTypes、DateParser/DateGenerator/HttpDateTime、QuotedCSV 与 charset 映射有 API/解析变化；" +
            "请验证扩展名、默认 MIME、locale、过期日期、重复 token 和无效输入";
    static final String HANDLER =
            "Jetty 12 Handler.handle 改为异步 handle(Request, Response, Callback)，返回 handled 状态且必须恰好完成 callback；" +
            "请迁移 blocking request/response、setHandled、header/content 访问以及异常/取消/backpressure 路径";
    static final String EE =
            "Jetty 9 的 javax Servlet 与 Jetty 11 的 Jakarta EE 9 部署栈在 Jetty 12 必须显式选择 ee8/ee9/ee10；" +
            "请迁移对应 artifact/namespace/Servlet 版本并验证 web.xml、SCI、WebSocket、JSP、JNDI 和类加载隔离";
    static final String SPI_MODULE =
            "Jetty HTTP SPI/service 或 JPMS 边界发生变化；请验证 HttpFieldPreEncoder ServiceLoader、" +
            "module-info requires/uses/provides、OSGi capability、shade 后服务文件和自定义 encoder 的目标字节码";

    @Override
    public String getDisplayName() {
        return "Find Jetty 12 HTTP source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed HTTP types, content/header/URI/cookie/parser/compliance behavior, asynchronous Handler, " +
               "Jakarta EE lineage and service/module boundaries using attributed Java types.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit source, ExecutionContext ctx) {
                if (JettyHttpSupport.generated(source.getSourcePath())) return source;
                J.CompilationUnit visited = super.visitCompilationUnit(source, ctx);
                if ("module-info.java".equals(visited.getSourcePath().getFileName().toString()) &&
                    visited.printAll().contains("org.eclipse.jetty")) {
                    return JettyHttpSupport.mark(visited, SPI_MODULE);
                }
                return visited;
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String name = visited.getQualid().printTrimmed(getCursor());
                String message = typeMessage(name.replace(".*", ""));
                return message == null ? visited : JettyHttpSupport.mark(visited, message);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(visited.getType());
                if (type == null) return visited;
                if (assignable(type, HTTP + "HttpParser$RequestHandler") ||
                    assignable(type, HTTP + "HttpParser$ResponseHandler")) {
                    visited = JettyHttpSupport.mark(visited, PARSER);
                }
                if (assignable(type, HTTP + "HttpFieldPreEncoder")) {
                    visited = JettyHttpSupport.mark(visited, SPI_MODULE);
                }
                if (assignable(type, "org.eclipse.jetty.server.Handler")) {
                    visited = JettyHttpSupport.mark(visited, HANDLER);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = fqn(visited.getType());
                String message = typeMessage(type);
                if (message != null) visited = JettyHttpSupport.mark(visited, message);
                if ((HTTP + "HttpFields").equals(type) || (HTTP + "MutableHttpFields").equals(type)) {
                    visited = JettyHttpSupport.mark(visited, HEADERS);
                }
                if ((HTTP + "HttpURI").equals(type)) visited = JettyHttpSupport.mark(visited, URI);
                if ((HTTP + "HttpCookie").equals(type)) visited = JettyHttpSupport.mark(visited, COOKIE);
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                String owner = methodType == null ? "" : fqn(methodType.getDeclaringType());
                String name = visited.getSimpleName();
                if (CONTENT_TYPES.contains(owner) &&
                    (REMOVED_CONTENT_METHODS.contains(name) ||
                     MIGRATED_CONTENT_METHODS.contains(name))) {
                    return JettyHttpSupport.mark(visited, CONTENT);
                }
                if ((HTTP + "HttpFields").equals(owner) || (HTTP + "HttpFields$Mutable").equals(owner) ||
                    (HTTP + "MutableHttpFields").equals(owner)) {
                    if (HEADER_MUTATORS.contains(name) || Set.of("build", "asImmutable").contains(name)) {
                        return JettyHttpSupport.mark(visited, HEADERS);
                    }
                }
                if ((HTTP + "HttpURI").equals(owner) || (HTTP + "HttpURI$Mutable").equals(owner)) {
                    return JettyHttpSupport.mark(visited, URI);
                }
                if ((HTTP + "HttpCookie").equals(owner) || owner.startsWith(HTTP + "HttpCookie$")) {
                    return JettyHttpSupport.mark(visited, COOKIE);
                }
                if (owner.startsWith(HTTP + "HttpParser") || owner.startsWith(HTTP + "HttpGenerator")) {
                    return JettyHttpSupport.mark(visited, PARSER);
                }
                if (owner.equals(HTTP + "HttpCompliance") || owner.equals(HTTP + "UriCompliance") ||
                    owner.equals(HTTP + "CookieCompliance") || owner.startsWith(HTTP + "ComplianceViolation")) {
                    return JettyHttpSupport.mark(visited, COMPLIANCE);
                }
                if (owner.equals(HTTP + "MimeTypes") || owner.equals(HTTP + "DateParser") ||
                    owner.equals(HTTP + "DateGenerator") || owner.equals(HTTP + "HttpDateTime") ||
                    owner.startsWith(HTTP + "Quoted")) {
                    return JettyHttpSupport.mark(visited, MIME_DATE);
                }
                if (owner.startsWith("org.eclipse.jetty.server.Request") ||
                    owner.startsWith("org.eclipse.jetty.server.Response") ||
                    owner.startsWith("org.eclipse.jetty.server.Handler")) {
                    return JettyHttpSupport.mark(visited, HANDLER);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String text)) return visited;
                if (text.contains("RFC7230") || text.contains("LEGACY") || text.contains("HttpCompliance") ||
                    text.contains("UriCompliance") || text.contains("CookieCompliance")) {
                    return JettyHttpSupport.mark(visited, COMPLIANCE);
                }
                if (text.contains("org.eclipse.jetty.http.HttpContent") ||
                    text.contains("org.eclipse.jetty.http.ResourceHttpContent") ||
                    text.contains("org.eclipse.jetty.http.PrecompressedHttpContent")) {
                    return JettyHttpSupport.mark(visited, CONTENT);
                }
                return visited;
            }
        };
    }

    private static String typeMessage(String type) {
        if (type == null || type.isBlank()) return null;
        if (REMOVED_TYPES.contains(type)) return REMOVED;
        if (CONTENT_TYPES.contains(type)) return CONTENT;
        if (type.equals(HTTP + "HttpFields") || type.equals(HTTP + "HttpFields$Mutable") ||
            type.equals(HTTP + "MutableHttpFields")) return HEADERS;
        if (type.equals(HTTP + "HttpURI") || type.startsWith(HTTP + "HttpURI$")) return URI;
        if (type.equals(HTTP + "HttpCookie") || type.startsWith(HTTP + "HttpCookie$")) return COOKIE;
        if (type.startsWith(HTTP + "HttpParser") || type.startsWith(HTTP + "HttpGenerator")) return PARSER;
        if (type.equals(HTTP + "HttpCompliance") || type.equals(HTTP + "UriCompliance") ||
            type.equals(HTTP + "CookieCompliance") || type.startsWith(HTTP + "ComplianceViolation")) {
            return COMPLIANCE;
        }
        if (type.equals(HTTP + "MimeTypes") || type.equals(HTTP + "DateParser") ||
            type.equals(HTTP + "DateGenerator") || type.equals(HTTP + "HttpDateTime") ||
            type.startsWith(HTTP + "Quoted")) return MIME_DATE;
        if (type.equals(HTTP + "HttpFieldPreEncoder") || type.startsWith(HTTP + "PreEncodedHttpField")) {
            return SPI_MODULE;
        }
        if (type.startsWith("org.eclipse.jetty.server.Handler") ||
            type.startsWith("org.eclipse.jetty.server.Request") ||
            type.startsWith("org.eclipse.jetty.server.Response")) return HANDLER;
        if (type.startsWith("javax.servlet.") || type.startsWith("jakarta.servlet.") ||
            type.startsWith("org.eclipse.jetty.servlet.") || type.startsWith("org.eclipse.jetty.webapp.") ||
            type.startsWith("org.eclipse.jetty.ee")) return EE;
        return null;
    }

    private static boolean assignable(JavaType.FullyQualified type, String target) {
        return TypeUtils.isAssignableTo(target, type);
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }
}
