package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindTomcatCatalinaJavaRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindTomcatCatalinaJavaRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(TomcatCatalinaTestApi.sources()));
    }

    @ParameterizedTest(name = "removed Servlet API {0}")
    @MethodSource("removedServletCalls")
    void marksRemovedServletCallsWithoutSafeReplacement(String label, String source) {
        rewriteRun(java(source, specification -> specification.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), "no syntax-only behavior-preserving replacement"))));
    }

    static Stream<Arguments> removedServletCalls() {
        return Stream.of(
                Arguments.of("response status message", "import jakarta.servlet.http.HttpServletResponse; class T { void x(HttpServletResponse r){r.setStatus(404,\"missing\");} }"),
                Arguments.of("session context", "import jakarta.servlet.http.HttpSession; class T { Object x(HttpSession s){return s.getSessionContext();} }"),
                Arguments.of("session value names", "import jakarta.servlet.http.HttpSession; class T { String[] x(HttpSession s){return s.getValueNames();} }"),
                Arguments.of("servlet by name", "import jakarta.servlet.ServletContext; class T { Object x(ServletContext c){return c.getServlet(\"legacy\");} }"),
                Arguments.of("servlets", "import jakarta.servlet.ServletContext; class T { Object x(ServletContext c){return c.getServlets();} }"),
                Arguments.of("servlet names", "import jakarta.servlet.ServletContext; class T { Object x(ServletContext c){return c.getServletNames();} }"),
                Arguments.of("real path", "import jakarta.servlet.ServletRequest; class T { String x(ServletRequest r){return r.getRealPath(\"/x\");} }"),
                Arguments.of("HttpUtils query", "import jakarta.servlet.http.HttpUtils; class T { Object x(){return HttpUtils.parseQueryString(\"a=b\");} }"),
                Arguments.of("HttpUtils URL", "import jakarta.servlet.http.*; class T { Object x(HttpServletRequest r){return HttpUtils.getRequestURL(r);} }"),
                Arguments.of("UnavailableException state", "import jakarta.servlet.UnavailableException; class T { Object x(UnavailableException e){return e.getServlet();} }")
        );
    }

    @ParameterizedTest(name = "removed type {0}")
    @MethodSource("removedTypes")
    void marksRemovedTypes(String label, String source) {
        rewriteRun(java(source, specification -> specification.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), "no syntax-only behavior-preserving replacement"))));
    }

    static Stream<Arguments> removedTypes() {
        return Stream.of(
                Arguments.of("SingleThreadModel", "import jakarta.servlet.SingleThreadModel; class T implements SingleThreadModel {}"),
                Arguments.of("HttpSessionContext", "import jakarta.servlet.http.HttpSessionContext; class T { HttpSessionContext x; }"),
                Arguments.of("HttpUtils", "import jakarta.servlet.http.HttpUtils; class T { HttpUtils x; }")
        );
    }

    @ParameterizedTest(name = "cookie compatibility {0}")
    @ValueSource(strings = {"getComment", "setComment", "getVersion", "setVersion"})
    void marksLegacyCookieSpecificationMethods(String method) {
        String expression = method.startsWith("set") ? "c." + method + "(" + (method.endsWith("Version") ? "1" : "\"legacy\"") + ");"
                : "Object x=c." + method + "();";
        rewriteRun(java("import jakarta.servlet.http.Cookie; class T { void x(Cookie c){" + expression + "} }",
                specification -> specification.after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), "RFC 6265 cookie behavior"))));
    }

    @ParameterizedTest(name = "removed listener option {0}")
    @ValueSource(strings = {"setAWTThreadProtection", "setGcDaemonProtection", "setLdapPoolProtection", "setTokenPollerProtection", "setXmlParsingProtection", "setForkJoinCommonPoolProtection"})
    void marksRemovedLeakListenerOptions(String method) {
        rewriteRun(java("import org.apache.catalina.core.JreMemoryLeakPreventionListener; class T { void x(JreMemoryLeakPreventionListener l){l." + method + "(true);} }",
                specification -> specification.after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), "Java-8 leak no longer exists"))));
    }

    @ParameterizedTest(name = "internal API {0}")
    @MethodSource("internalApis")
    void marksInternalAndAprImports(String label, String source, String marker) {
        rewriteRun(java(source, specification -> specification.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), marker))));
    }

    static Stream<Arguments> internalApis() {
        return Stream.of(
                Arguments.of("embedded bootstrap", "import org.apache.catalina.startup.Tomcat; class T { Tomcat x; }", "not binary compatible"),
                Arguments.of("coyote APR", "import org.apache.coyote.http11.Http11AprProtocol; class T { Http11AprProtocol x; }", "APR connector"),
                Arguments.of("native buffer", "import org.apache.tomcat.jni.Buffer; class T { Object x(){return Buffer.malloc(4);} }", "legacy tomcat-native JNI")
        );
    }

    @Test
    void marksLegacyUnavailableExceptionConstructors() {
        rewriteRun(java("import jakarta.servlet.*; class T { Exception x(Servlet s){return new UnavailableException(s,\"down\");} Exception y(Servlet s){return new UnavailableException(30,s,\"down\");} }",
                specification -> specification.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "no syntax-only behavior-preserving replacement");
                    assertAtLeast(after.printAll(), "no syntax-only behavior-preserving replacement", 2);
                })));
    }

    @Test
    void marksCaseInsensitiveRequestMethodOnEitherSide() {
        rewriteRun(java("import jakarta.servlet.http.HttpServletRequest; class T { boolean a(HttpServletRequest r){return r.getMethod().equalsIgnoreCase(\"POST\");} boolean b(HttpServletRequest r){return \"GET\".equalsIgnoreCase(r.getMethod());} }",
                specification -> specification.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "case-sensitive");
                    assertAtLeast(after.printAll(), "case-sensitive", 2);
                })));
    }

    @ParameterizedTest(name = "obsolete override {0}")
    @MethodSource("obsoleteOverrides")
    void marksRemovedInterfaceImplementations(String label, String source) {
        rewriteRun(java(source, specification -> specification.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), "remove the obsolete @Override"))));
    }

    static Stream<Arguments> obsoleteOverrides() {
        return Stream.of(
                Arguments.of("response spelling", "import jakarta.servlet.http.HttpServletResponse; abstract class R implements HttpServletResponse { @Override public String encodeUrl(String s){return s;} }"),
                Arguments.of("request spelling", "import jakarta.servlet.http.HttpServletRequest; abstract class R implements HttpServletRequest { @Override public boolean isRequestedSessionIdFromUrl(){return false;} }"),
                Arguments.of("session value", "import jakarta.servlet.http.HttpSession; abstract class S implements HttpSession { @Override public Object getValue(String n){return null;} }"),
                Arguments.of("context log", "import jakarta.servlet.ServletContext; abstract class C implements ServletContext { @Override public void log(Exception e,String s){} }")
        );
    }

    @Test
    void realYonaResponseWrapperMarksBothRemovedOverrides() {
        // Reduced from yona-projects/yona@60a5ac40689fc36ee5b55eddedd345fc34878190,
        // app/utils/PlayServletResponse.java; javax->jakarta only.
        rewriteRun(java("import jakarta.servlet.http.HttpServletResponse; abstract class PlayServletResponse implements HttpServletResponse { @Override public String encodeRedirectUrl(String url){return encodeRedirectURL(url);} @Override public String encodeUrl(String url){return encodeURL(url);} }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertAtLeast(after.printAll(), "remove the obsolete @Override", 2))));
    }

    @Test
    void javaxServlet9RemovedApisAreMarkedBeforeNamespaceMigration() {
        rewriteRun(
                java("import javax.servlet.http.HttpSession; class SessionCalls { String[] names(HttpSession session){return session.getValueNames();} }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertContains(
                                after.printAll(), "no syntax-only behavior-preserving replacement"))),
                java("import javax.servlet.SingleThreadModel; class RemovedModel implements SingleThreadModel {}",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertContains(
                                after.printAll(), "no syntax-only behavior-preserving replacement")))
        );
    }

    @Test
    void targetAndBusinessMethodsAreUnmarked() {
        rewriteRun(
                java("import jakarta.servlet.http.*; class TargetCalls { void x(HttpServletResponse r,HttpSession s){r.setStatus(204); s.getAttribute(\"x\"); s.setAttribute(\"x\",1); s.removeAttribute(\"x\");} }"),
                java("class BusinessCookie { int getVersion(){return 1;} } class BusinessCalls { int x(BusinessCookie c){return c.getVersion();} }"),
                java("import jakarta.servlet.http.HttpSession; abstract class SessionWithOverloads implements HttpSession { Object getValue(int index){return null;} void putValue(int index,Object value){} }"),
                java("class BusinessHttp11AprProtocol { BusinessHttp11AprProtocol(){} } class Factory { Object create(){return new BusinessHttp11AprProtocol();} }")
        );
    }

    @Test
    void generatedJavaIsUnmarked() {
        rewriteRun(java("import jakarta.servlet.http.HttpSession; class T { String[] x(HttpSession s){return s.getValueNames();} }",
                specification -> specification.path("build/generated/T.java")));
    }

    @Test
    void marksStringBasedJavaxReferencesButNotCommentsOrLookalikes() {
        rewriteRun(
                java("class ReflectionConfig { String initializer=\"javax.servlet.ServletContainerInitializer\"; String el=\"javax.el.ExpressionFactory\"; }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertAtLeast(after.printAll(), "String-based javax Servlet/EL reference", 2))),
                java("class Documentation { // javax.servlet.http.HttpServletRequest\n String lookalike=\"example.javax.servletish.Type\"; }")
        );
    }

    private static void assertContains(String actual, String token) {
        assertTrue(actual.contains(token), () -> "Expected marker containing '" + token + "' in:\n" + actual);
    }

    private static void assertAtLeast(String value, String token, int expected) {
        int count = 0;
        for (int index = 0; (index = value.indexOf(token, index)) >= 0; index += token.length()) count++;
        int actual = count;
        assertTrue(actual >= expected, () -> "Expected at least " + expected + " occurrences but found " + actual + " in:\n" + value);
    }
}
