package com.huawei.clouds.openrewrite.jakartael;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class MigrateJavaxElReferencesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJavaxElReferences())
                .parser(JavaParser.fromJavaVersion().dependsOn(JakartaElTestApi.legacySources()));
    }

    @Test
    void migratesJavaReflectionAndProviderLiterals() {
        rewriteRun(java(
                "class C { String factory = \"javax.el.ExpressionFactory\"; String resolver = \"javax.el.ELResolver\"; }",
                "class C { String factory = \"jakarta.el.ExpressionFactory\"; String resolver = \"jakarta.el.ELResolver\"; }"));
    }

    @Test
    void leavesOtherJavaxNamespacesAndLookalikes() {
        rewriteRun(java("class C { String inject = \"javax.inject.Inject\"; String lookalike = \"javax.elx.Type\"; String prefixed = \"notjavax.el.ExpressionFactory\"; String nested = \"app.javax.el.ExpressionFactory\"; }"));
    }

    @Test
    void resourceNamespaceLookalikesAreNoop() {
        rewriteRun(
                xml("<x>notjavax.el.ExpressionFactory app.javax.el.ELResolver</x>", source -> source.path("web.xml")),
                text("notjavax.el.ExpressionFactory\napp.javax.el.ELResolver\n", source -> source.path("providers.txt")));
    }

    @Test
    void migratesXmlAttributesAndTextButNotPom() {
        rewriteRun(
                xml("<web-app factory=\"javax.el.ExpressionFactory\"><type>javax.el.ELResolver</type></web-app>",
                        "<web-app factory=\"jakarta.el.ExpressionFactory\"><type>jakarta.el.ELResolver</type></web-app>",
                        source -> source.path("src/main/webapp/WEB-INF/web.xml")),
                xml("<project><property>javax.el.ExpressionFactory</property></project>",
                        source -> source.path("pom.xml")));
    }

    @Test
    void migratesPropertiesAndTemplateResources() {
        rewriteRun(
                text("factory=javax.el.ExpressionFactory\nother=javax.inject.Inject\n",
                        "factory=jakarta.el.ExpressionFactory\nother=javax.inject.Inject\n",
                        source -> source.path("src/main/resources/el.properties")),
                text("${javax.el.ExpressionFactory}", "${jakarta.el.ExpressionFactory}",
                        source -> source.path("src/main/resources/provider.txt")));
    }

    @Test
    void renamesOnlyStandardExpressionFactoryServiceDescriptor() {
        rewriteRun(spec -> spec.expectedCyclesThatMakeChanges(1), text("com.example.Factory\n", source -> source
                .path("src/main/resources/META-INF/services/javax.el.ExpressionFactory")
                .afterRecipe(after -> assertEquals(
                        "src/main/resources/META-INF/services/jakarta.el.ExpressionFactory",
                        after.getSourcePath().toString()))));
    }

    @Test
    void serviceDescriptorContentsMigrateInSameCycle() {
        rewriteRun(text("javax.el.ExpressionFactory\n", "jakarta.el.ExpressionFactory\n", source -> source
                .path("META-INF/services/javax.el.ExpressionFactory")
                .afterRecipe(after -> assertEquals("META-INF/services/jakarta.el.ExpressionFactory",
                        after.getSourcePath().toString()))));
    }

    @Test
    void lookalikeServicePathsAreNoop() {
        rewriteRun(
                text("com.example.Factory\n", source -> source.path(
                        "META-INF/services/javax.el.ELResolver")),
                text("com.example.Factory\n", source -> source.path(
                        "notMETA-INF/services/javax.el.ExpressionFactory")));
    }

    @Test
    void generatedAndInstallationTreesAreNoop() {
        rewriteRun(
                text("javax.el.ExpressionFactory\n", source -> source.path(
                        "target/generated/META-INF/services/javax.el.ExpressionFactory")),
                xml("<x>javax.el.ExpressionFactory</x>", source -> source.path("install-cache/web.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), text(
                "javax.el.ExpressionFactory\n", "jakarta.el.ExpressionFactory\n",
                source -> source.path("META-INF/services/javax.el.ExpressionFactory")
                        .afterRecipe(after -> assertEquals("META-INF/services/jakarta.el.ExpressionFactory",
                                after.getSourcePath().toString()))));
    }
}
