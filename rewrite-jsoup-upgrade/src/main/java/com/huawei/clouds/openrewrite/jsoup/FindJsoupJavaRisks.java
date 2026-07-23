package com.huawei.clouds.openrewrite.jsoup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks behavior changes that require application policy or regression fixtures. */
public final class FindJsoupJavaRisks extends Recipe {
    private static final String JSOUP = "org.jsoup.";
    private static final Set<String> ELEMENT_MUTATORS = Set.of("set", "remove", "clear", "removeAll", "retainAll", "removeIf", "replaceAll");

    @Override
    public String getDisplayName() {
        return "Find jsoup 1.21.1 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks network backend, response streaming, cleaning policy, selectors, DOM mutation, serialization, XML/W3C namespace, and removed internal API boundaries.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return JsoupSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String type = visited.getTypeName();
                if (type.equals("org.jsoup.UncheckedIOException")) {
                    return SearchResult.found(visited, "org.jsoup.UncheckedIOException was removed; migrate IOException constructors/catches to java.io.UncheckedIOException, but replace the old String constructor and ioException() accessor deliberately");
                }
                if (type.startsWith("org.jsoup.internal.") || type.equals("org.jsoup.helper.Consumer") ||
                    type.equals("org.jsoup.helper.ChangeNotifyingArrayList")) {
                    return SearchResult.found(visited, "This jsoup internal/compatibility API was removed by 1.21.1; replace it with a JDK or application-owned abstraction");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                String owner = methodType == null ? "" : fqn(methodType.getDeclaringType());
                String receiver = visited.getSelect() == null ? "" : fqn(visited.getSelect().getType());
                String name = visited.getSimpleName();
                if (owner.equals(JSOUP + "Connection$Response") && "bufferUp".equals(name)) {
                    return SearchResult.found(visited, "Response.bufferUp() is deprecated for readFully(), whose checked IOException is explicit; verify buffering, max-body, timeout, close, and error propagation contracts");
                }
                if (owner.equals(JSOUP + "Connection$Response") && "bodyStream".equals(name)) {
                    return SearchResult.found(visited, "Since 1.17.1 bodyStream() is an unconstrained BufferedInputStream; maxBodySize and timeout still apply to parse/readFully but not to direct stream consumption, so enforce limits and closure explicitly");
                }
                if ((owner.equals(JSOUP + "Jsoup") && "connect".equals(name)) ||
                    (owner.startsWith(JSOUP) && Set.of("execute", "get", "post").contains(name))) {
                    return SearchResult.found(visited, "jsoup 1.21.1 prefers JDK HttpClient on Java 11+, enabling HTTP/2 and changing proxy/auth/TLS/cookie/redirect behavior and the default user-agent; replay recorded HTTP fixtures or opt out deliberately");
                }
                if ((owner.equals(JSOUP + "Jsoup") && Set.of("clean", "isValid").contains(name)) ||
                    (owner.equals(JSOUP + "safety.Cleaner") && Set.of("clean", "isValid", "isValidBodyHtml").contains(name))) {
                    return SearchResult.found(visited, "Cleaner/Safelist behavior changed for preserveRelativeLinks, rel=nofollow, noscript, SVG/MathML and namespace-aware tags; replay malicious and allowed HTML corpora");
                }
                if (owner.equals(JSOUP + "safety.Safelist") &&
                    Set.of("preserveRelativeLinks", "addTags", "addAttributes", "addProtocols", "addEnforcedAttribute").contains(name)) {
                    return SearchResult.found(visited, "This Safelist call defines an XSS security boundary; verify relative-link resolution, noscript rejection, protocol canonicalization, SVG/MathML case, and enforced rel attributes on 1.21.1");
                }
                if ((owner.equals(JSOUP + "select.Elements") || receiver.equals(JSOUP + "select.Elements")) && ELEMENT_MUTATORS.contains(name)) {
                    return SearchResult.found(visited, "Elements collection mutators now update the backing DOM; use deselect/asList for list-only changes and regression-test node parentage, indices, and iteration");
                }
                if (owner.startsWith(JSOUP + "nodes.") && Set.of("before", "after", "remove", "empty", "replaceWith").contains(name)) {
                    return SearchResult.found(visited, "Node mutation and detachment behavior changed, including sibling moves, parent clearing, clone caches, and orphan remove no-op semantics; verify DOM identity and ordering");
                }
                if ((owner.equals(JSOUP + "nodes.Document$OutputSettings") &&
                     Set.of("prettyPrint", "outline", "indentAmount", "maxPaddingWidth", "syntax", "escapeMode").contains(name)) ||
                    (owner.startsWith(JSOUP + "nodes.") && Set.of("html", "outerHtml").contains(name))) {
                    return SearchResult.found(visited, "The 1.20 pretty-printer and 1.21 attribute escaping can change serialized bytes; snapshot whitespace, self-closing tags, attribute escaping, XML/XHTML syntax and downstream signatures");
                }
                if (owner.equals(JSOUP + "helper.W3CDom") && Set.of("fromJsoup", "convert", "asString", "namespaceAware").contains(name)) {
                    return SearchResult.found(visited, "W3CDom/XML namespace and invalid-name normalization changed; verify XHTML namespace defaults, xmlns scoping, doctypes, processing instructions and serialized XML");
                }
                markSelectorLiteral(visited);
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                if (visited.getExtends() != null && TypeUtils.isOfClassType(visited.getExtends().getType(), JSOUP + "safety.Safelist")) {
                    return SearchResult.found(visited, "Custom Safelist subclass is an XSS policy extension; diff overridden safety decisions and replay an adversarial corpus on 1.21.1");
                }
                return visited;
            }

            private void markSelectorLiteral(J.MethodInvocation method) {
                String owner = method.getMethodType() == null ? "" : fqn(method.getMethodType().getDeclaringType());
                if (!owner.startsWith(JSOUP) || !Set.of("select", "selectFirst", "expectFirst", "matches", "is").contains(method.getSimpleName())) return;
                for (var argument : method.getArguments()) {
                    if (!(argument instanceof J.Literal literal) || !(literal.getValue() instanceof String selector)) continue;
                    String message = null;
                    if (selector.contains(":matchText")) message = ":matchText mutates the DOM and is deprecated; migrate deliberately to ::textnode with selectNodes and verify result node types/order";
                    else if (selector.contains(":empty") || selector.contains(":has(") || selector.matches(".*[>+~]{2,}.*"))
                        message = "Selector parsing/matching changed for blank :empty nodes, nested/sibling :has, escaped identifiers and invalid consecutive combinators; replay exact selector fixtures";
                    if (message != null) getCursor().putMessage("jsoup.selector.risk." + literal.getId(), message);
                }
                method.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast).forEach(literal -> {
                    String message = getCursor().pollMessage("jsoup.selector.risk." + literal.getId());
                    if (message != null) doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {
                        @Override public J.Literal visitLiteral(J.Literal candidate, ExecutionContext p) {
                            return candidate.getId().equals(literal.getId()) ? SearchResult.found(candidate, message) : candidate;
                        }
                    });
                });
            }
        };
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }
}
