package com.huawei.clouds.openrewrite.jakartawrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class MigrateJavaxWsRsReferencesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJavaxWsRsReferences())
                .parser(JavaParser.fromJavaVersion().dependsOn(JakartaRestTestApi.sources()));
    }

    @Test
    void migratesJavaStringLiteralsOnly() {
        rewriteRun(java(
                "class C { String provider = \"javax.ws.rs.ext.RuntimeDelegate\"; String other = \"javax.persistence.Entity\"; }",
                "class C { String provider = \"jakarta.ws.rs.ext.RuntimeDelegate\"; String other = \"javax.persistence.Entity\"; }"));
    }

    @Test
    void migratesXmlTextAndAttributeButNotPom() {
        rewriteRun(
                xml("<web-app provider=\"javax.ws.rs.ext.RuntimeDelegate\"><param>javax.ws.rs.Application</param></web-app>",
                        "<web-app provider=\"jakarta.ws.rs.ext.RuntimeDelegate\"><param>jakarta.ws.rs.Application</param></web-app>",
                        source -> source.path("src/main/webapp/WEB-INF/web.xml")),
                xml("<project><x>javax.ws.rs.Application</x></project>", source -> source.path("pom.xml")));
    }

    @Test
    void migratesPlainResources() {
        rewriteRun(text(
                "provider=javax.ws.rs.client.ClientBuilder\nother=javax.inject.Inject\n",
                "provider=jakarta.ws.rs.client.ClientBuilder\nother=javax.inject.Inject\n",
                source -> source.path("src/main/resources/rest.properties")));
    }

    @ParameterizedTest(name = "service descriptor {0}")
    @ValueSource(strings = {"javax.ws.rs.client.ClientBuilder", "javax.ws.rs.ext.RuntimeDelegate", "javax.ws.rs.sse.SseEventSource$Builder"})
    void renamesStandardServiceDescriptor(String oldName) {
        rewriteRun(spec -> spec.expectedCyclesThatMakeChanges(1), text("com.example.Provider\n", source -> source
                .path("src/main/resources/META-INF/services/" + oldName)
                .afterRecipe(after -> assertEquals(
                        "src/main/resources/META-INF/services/" + oldName.replace("javax.ws.rs.", "jakarta.ws.rs."),
                        after.getSourcePath().toString()))));
    }

    @Test
    void leavesLookalikeServiceAndGeneratedTreesUntouched() {
        rewriteRun(
                text("com.example.Provider\n", source -> source.path("src/main/resources/META-INF/services/javax.ws.rs.Custom")),
                text("com.example.Provider\n", source -> source.path("src/main/resources/notMETA-INF/services/javax.ws.rs.ext.RuntimeDelegate")),
                text("javax.ws.rs.Application\n", source -> source.path("target/generated/META-INF/services/javax.ws.rs.ext.RuntimeDelegate")
                        .afterRecipe(after -> assertEquals(
                                "target/generated/META-INF/services/javax.ws.rs.ext.RuntimeDelegate",
                                after.getSourcePath().toString()))));
    }

    @Test
    void serviceRenameAndContentAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                text("javax.ws.rs.Application\n", "jakarta.ws.rs.Application\n", source -> source
                        .path("META-INF/services/javax.ws.rs.ext.RuntimeDelegate")
                        .afterRecipe(after -> assertEquals(
                                "META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate", after.getSourcePath().toString()))));
    }
}
