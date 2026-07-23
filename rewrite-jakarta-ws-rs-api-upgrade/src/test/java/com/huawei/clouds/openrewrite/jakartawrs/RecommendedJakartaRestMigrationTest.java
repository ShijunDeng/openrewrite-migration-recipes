package com.huawei.clouds.openrewrite.jakartawrs;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedJakartaRestMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.jakartawrs").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.jakartawrs.MigrateJakartaWsRsApiTo4_0_0"))
                .parser(JavaParser.fromJavaVersion().dependsOn(JakartaRestTestApi.sources()));
    }

    @Test
    void migratesTypeAttributedJavaxRestNamespace() {
        rewriteRun(java(
                """
                import javax.ws.rs.GET;
                import javax.ws.rs.Path;

                @Path("orders") class Orders { @GET void get() { } }
                """,
                """
                import jakarta.ws.rs.GET;
                import jakarta.ws.rs.Path;

                @Path("orders") class Orders { @GET void get() { } }
                """));
    }

    @Test
    void pureNamespaceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import javax.ws.rs.GET; class Orders { @GET void get(){} }",
                "import jakarta.ws.rs.GET; class Orders { @GET void get(){} }"));
    }

    @Test
    void namespaceAutoSkipsGeneratedParentButProcessesLeafName() {
        String before = "import javax.ws.rs.GET; class Resource { @GET void get(){} }";
        String after = "import jakarta.ws.rs.GET; class Resource { @GET void get(){} }";
        rewriteRun(
                java(before.replace("class Resource", "class GeneratedResource"),
                        source -> source.path("build/generated/GeneratedResource.java")),
                java(before, after, source -> source.path("install.java")));
        var recipe = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.jakartawrs").build()
                .activateRecipes("com.huawei.clouds.openrewrite.jakartawrs.MigrateJakartaWsRsApiTo4_0_0");
        assertEquals(MigrateJavaxWsRsPackage.class, recipe.getRecipeList().get(1).getClass());
    }

    @Test
    void reducedTangoFixtureMigratesNamespaceAndMarksCheckedClose() {
        // Reduced from tango-controls/rest-server@c5f509ca7f93ec15e5cd331ac59bd8e2ce57abc0,
        // src/main/java/org/tango/web/server/event/SseEventSink.java.
        rewriteRun(java("""
                import java.util.concurrent.CompletionStage;
                class SinkWrapper implements javax.ws.rs.sse.SseEventSink {
                    private final javax.ws.rs.sse.SseEventSink impl;
                    SinkWrapper(javax.ws.rs.sse.SseEventSink impl) { this.impl = impl; }
                    public boolean isClosed() { return impl.isClosed(); }
                    public CompletionStage<?> send(Object event) { return impl.send(event); }
                    public void close() { impl.close(); }
                }
                """, spec -> spec.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("jakarta.ws.rs.sse.SseEventSink"), out);
                    assertTrue(out.contains("declares IOException"), out);
                    assertFalse(out.contains("javax.ws.rs.sse"), out);
                })));
    }

    @Test
    void reducedBaeldungFixtureMarksEveryClosePolicyBoundary() {
        // Reduced from eugenp/tutorials@1023d82c27af842a9e86b4663227819db8b19d7a,
        // apache-cxf-modules/sse-jaxrs/sse-jaxrs-server/.../SseResource.java.
        rewriteRun(java("""
                import jakarta.ws.rs.sse.SseEventSink;
                class SseResource {
                    void prices(SseEventSink sink, boolean done) {
                        if (done) sink.close();
                        sink.close();
                    }
                }
                """, spec -> spec.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("disconnect races"), out);
                    assertTrue(out.indexOf("disconnect races") != out.lastIndexOf("disconnect races"), out);
                })));
    }

    @Test
    void reducedNifiFixtureMigratesRestTypeButMarksSelfOwnedJaxbBoundary() {
        // Reduced from apache/nifi@c4b3f20972424b93cb0bfd10045fb1dc9cc95065,
        // nifi-registry/.../link/LinkAdapter.java.
        rewriteRun(java("""
                import javax.ws.rs.core.Link;
                import jakarta.xml.bind.annotation.adapters.XmlAdapter;
                abstract class LinkAdapter extends XmlAdapter<Object, Link> { }
                """, spec -> spec.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("import jakarta.ws.rs.core.Link;"), out);
                    assertTrue(out.contains("declare the JAXB API/runtime explicitly"), out);
                })));
    }

    @Test
    void upgradesDependencyBeforeJavaAndServiceResources() {
        rewriteRun(
                xml(UpgradeJakartaWsRsApiDependencyTest.pom("2.1.6"),
                        UpgradeJakartaWsRsApiDependencyTest.pom("4.0.0"), source -> source.path("pom.xml")),
                java("import javax.ws.rs.GET; class R { @GET void get(){} }",
                        "import jakarta.ws.rs.GET; class R { @GET void get(){} }"),
                text("com.example.Delegate\n", source -> source
                        .path("src/main/resources/META-INF/services/javax.ws.rs.ext.RuntimeDelegate")
                        .afterRecipe(after -> assertEquals(
                                "src/main/resources/META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate",
                                after.getSourcePath().toString()))));
    }
}
