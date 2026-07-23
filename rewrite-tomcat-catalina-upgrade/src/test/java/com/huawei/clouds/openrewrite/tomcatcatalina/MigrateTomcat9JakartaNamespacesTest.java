package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateTomcat9JakartaNamespacesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateTomcat9JakartaNamespaces())
                .parser(JavaParser.fromJavaVersion().dependsOn(TomcatCatalinaTestApi.sources()));
    }

    @Test
    void migratesServletAndElTypeAttributedNamespaces() {
        rewriteRun(java(
                "import javax.el.MethodExpression; import javax.servlet.http.HttpServletRequest; class T { boolean x(HttpServletRequest request,MethodExpression expression){return request.isRequestedSessionIdFromURL() && expression.isParametersProvided();} }",
                "import jakarta.el.MethodExpression; import jakarta.servlet.http.HttpServletRequest; class T { boolean x(HttpServletRequest request,MethodExpression expression){return request.isRequestedSessionIdFromURL() && expression.isParametersProvided();} }"));
    }

    @Test
    void removedServletTypeGuardsWholeCompilationUnit() {
        rewriteRun(java("import javax.servlet.SingleThreadModel; import javax.servlet.http.HttpServletRequest; class T implements SingleThreadModel { HttpServletRequest request; }"));
    }

    @Test
    void stringsCommentsAndBusinessPackagesAreNoop() {
        rewriteRun(java("class T { // javax.servlet.http.HttpServletRequest\n String name=\"javax.servlet.http.HttpSession\"; example.javax.servlet.Session session; } class example { static class javax { static class servlet { static class Session {} } } }"));
    }

    @Test
    void generatedSourceIsNoop() {
        rewriteRun(java("import javax.servlet.http.HttpSession; class T { HttpSession session; }",
                source -> source.path("build/generated/T.java")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(specification -> specification.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import javax.servlet.http.HttpSession; class T { HttpSession session; }",
                "import jakarta.servlet.http.HttpSession; class T { HttpSession session; }"));
    }
}
