package com.huawei.clouds.openrewrite.jettyhttp;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class JettyHttpRealRepositoryFixtureTest implements RewriteTest {
    private static final String RISKS =
            "com.huawei.clouds.openrewrite.jettyhttp.FindJettyHttp12SourceRisks";

    @Test
    void marksJetty94HttpFieldsFixture() throws Exception {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RISKS))
                        .parser(JavaParser.fromJavaVersion().dependsOn(JettyHttpLegacyApi.sources())),
                java(fixture("jetty9-http-fields.java"),
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindJettyHttpSourceRisks.HEADERS),
                                        after.printAll()))));
    }

    @Test
    void marksJetty94HttpUriFixture() throws Exception {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RISKS))
                        .parser(JavaParser.fromJavaVersion().dependsOn(JettyHttpLegacyApi.sources())),
                java(fixture("jetty9-http-uri.java"),
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindJettyHttpSourceRisks.URI),
                                        after.printAll()))));
    }

    @Test
    void marksDropwizardJetty12HeaderAndUriBoundaries() throws Exception {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RISKS))
                        .parser(JettyHttpTestSupport.targetParser()),
                java(fixture("dropwizard-jetty12.java"),
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindJettyHttpSourceRisks.HEADERS), printed);
                            assertTrue(printed.contains(FindJettyHttpSourceRisks.URI), printed);
                        })));
    }

    @Test
    void recommendedEntryScopesRealJettyFixturesByNearestBuildRoot() throws Exception {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(
                                "com.huawei.clouds.openrewrite.jettyhttp.MigrateJettyHttpTo12_0_34"))
                        .parser(JavaParser.fromJavaVersion().dependsOn(JettyHttpLegacyApi.sources())),
                xml(JettyHttpTestSupport.pom("9.4.58.v20250814"),
                        source -> source.path("selected/pom.xml").after(actual -> actual)),
                java(fixture("jetty9-http-fields.java"),
                        source -> source.path("selected/src/test/java/Jetty9HttpFieldsFixture.java")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertTrue(after.printAll().contains(
                                                FindJettyHttpSourceRisks.HEADERS),
                                                after.printAll()))),
                xml(JettyHttpTestSupport.pom("12.0.34"),
                        source -> source.path("unrelated/pom.xml")),
                java(fixture("jetty9-http-uri.java"),
                        source -> source.path("unrelated/src/test/java/Jetty9HttpUriFixture.java")
                                .afterRecipe(after ->
                                        assertFalse(after.printAll().contains(
                                                FindJettyHttpSourceRisks.URI),
                                                after.printAll()))));
    }

    private static String fixture(String name) throws IOException, URISyntaxException {
        return Files.readString(Path.of(JettyHttpRealRepositoryFixtureTest.class
                .getResource("/fixtures/real/" + name).toURI()));
    }
}
