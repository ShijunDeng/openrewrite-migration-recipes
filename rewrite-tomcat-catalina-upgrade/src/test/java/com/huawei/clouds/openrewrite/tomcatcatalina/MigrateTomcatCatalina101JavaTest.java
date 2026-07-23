package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class MigrateTomcatCatalina101JavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateTomcatCatalina101Java())
                .parser(JavaParser.fromJavaVersion().dependsOn(TomcatCatalinaTestApi.sources()));
    }

    @ParameterizedTest(name = "equivalent API {0}")
    @MethodSource("equivalentApis")
    void migratesEveryDocumentedEquivalent(String label, String before, String after) {
        rewriteRun(java(before, after));
    }

    static Stream<Arguments> equivalentApis() {
        return Stream.of(
                Arguments.of("request URL spelling",
                        "import jakarta.servlet.http.HttpServletRequest; class T { boolean x(HttpServletRequest r){return r.isRequestedSessionIdFromUrl();} }",
                        "import jakarta.servlet.http.HttpServletRequest; class T { boolean x(HttpServletRequest r){return r.isRequestedSessionIdFromURL();} }"),
                Arguments.of("response URL spelling",
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeUrl(\"/x\");} }",
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeURL(\"/x\");} }"),
                Arguments.of("redirect URL spelling",
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeRedirectUrl(\"/x\");} }",
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeRedirectURL(\"/x\");} }"),
                Arguments.of("session get",
                        "import jakarta.servlet.http.HttpSession; class T { Object x(HttpSession s){return s.getValue(\"user\");} }",
                        "import jakarta.servlet.http.HttpSession; class T { Object x(HttpSession s){return s.getAttribute(\"user\");} }"),
                Arguments.of("session put",
                        "import jakarta.servlet.http.HttpSession; class T { void x(HttpSession s,Object v){s.putValue(\"user\",v);} }",
                        "import jakarta.servlet.http.HttpSession; class T { void x(HttpSession s,Object v){s.setAttribute(\"user\",v);} }"),
                Arguments.of("session remove",
                        "import jakarta.servlet.http.HttpSession; class T { void x(HttpSession s){s.removeValue(\"user\");} }",
                        "import jakarta.servlet.http.HttpSession; class T { void x(HttpSession s){s.removeAttribute(\"user\");} }"),
                Arguments.of("EL spelling",
                        "import jakarta.el.MethodExpression; class T { boolean x(MethodExpression e){return e.isParmetersProvided();} }",
                        "import jakarta.el.MethodExpression; class T { boolean x(MethodExpression e){return e.isParametersProvided();} }"),
                Arguments.of("ServletContext log arguments",
                        "import jakarta.servlet.ServletContext; class T { void x(ServletContext c,Exception e){c.log(e,\"failed\");} }",
                        "import jakarta.servlet.ServletContext; class T { void x(ServletContext c,Exception e){c.log(\"failed\",e);} }")
        );
    }

    @Test
    void realJfinalRequestWrapperFixture() {
        // Reduced from jfinal/jfinal@a0e9e8b99dc793bcf0cd40ca7feba005ba0c5349,
        // src/main/java/com/jfinal/core/paragetter/JsonRequest.java; javax->jakarta only.
        rewriteRun(java(
                "import jakarta.servlet.http.HttpServletRequest; class JsonRequest { HttpServletRequest req; boolean legacy(){ return req.isRequestedSessionIdFromUrl(); } }",
                "import jakarta.servlet.http.HttpServletRequest; class JsonRequest { HttpServletRequest req; boolean legacy(){ return req.isRequestedSessionIdFromURL(); } }"));
    }

    @Test
    void realJahiaServletContextWrapperFixture() {
        // Reduced from Jahia/jahia@5e201521576ec5814b58321845915c3a984892d8,
        // bundles/jahiamodule-extender/.../ServletContextWrapper.java; javax->jakarta only.
        rewriteRun(java(
                "import jakarta.servlet.ServletContext; abstract class ServletContextWrapper implements ServletContext { ServletContext servletContext; @Override public void log(Exception e,String s){servletContext.log(e,s);} }",
                "import jakarta.servlet.ServletContext; abstract class ServletContextWrapper implements ServletContext { ServletContext servletContext; @Override public void log(Exception e,String s){servletContext.log(s,e);} }"));
    }

    @Test
    void overloadsAndSameNamedBusinessMethodsAreNoop() {
        rewriteRun(
                java("class BusinessSession { Object getValue(String key){return null;} } class BusinessCalls { Object x(BusinessSession s){return s.getValue(\"x\");} }"),
                java("import jakarta.servlet.ServletContext; class LogCalls { void x(ServletContext c){c.log(\"ok\");} }"),
                java("import jakarta.servlet.http.HttpServletResponse; class StatusCalls { void x(HttpServletResponse r){r.setStatus(404,\"missing\");} }")
        );
    }

    @Test
    void inheritedUnqualifiedServletCallDoesNotCreateStaticImports() {
        rewriteRun(java(
                "import jakarta.servlet.http.HttpSession; abstract class SessionFacade implements HttpSession { Object value(){return getValue(\"user\");} }",
                "import jakarta.servlet.http.HttpSession; abstract class SessionFacade implements HttpSession { Object value(){return getAttribute(\"user\");} }"));
    }

    @Test
    void missingTypeAttributionIsNoop() {
        rewriteRun(java("class T { boolean x(Object request){ return request.toString().contains(\"isRequestedSessionIdFromUrl\"); } }"));
    }

    @Test
    void generatedParentIsNoop() {
        rewriteRun(java(
                "import jakarta.servlet.http.HttpSession; class T { Object x(HttpSession s){return s.getValue(\"x\");} }",
                source -> source.path("generated-code/T.java")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(specification -> specification.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeRedirectUrl(\"/x\");} }",
                "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeRedirectURL(\"/x\");} }"));
    }
}
