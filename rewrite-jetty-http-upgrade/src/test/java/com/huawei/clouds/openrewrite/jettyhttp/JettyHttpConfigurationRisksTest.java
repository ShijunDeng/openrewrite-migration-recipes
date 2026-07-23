package com.huawei.clouds.openrewrite.jettyhttp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class JettyHttpConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJettyHttpConfigurationRisks());
    }

    @ParameterizedTest(name = "property family {0}")
    @MethodSource("propertyFamilies")
    void marksStructuredJettyProperties(String key, String value, String marker) {
        rewriteRun(markedProperties(key + "=" + value, marker));
    }

    static Stream<Arguments> propertyFamilies() {
        return Stream.of(
                Arguments.of("jetty.base", "/srv/jetty", FindJettyHttpConfigurationRisks.MODULES),
                Arguments.of("jetty.httpConfig.uriCompliance", "RFC3986", FindJettyHttpConfigurationRisks.COMPLIANCE),
                Arguments.of("jetty.httpConfig.requestHeaderSize", "16384", FindJettyHttpConfigurationRisks.LIMITS),
                Arguments.of("jetty.http.port", "8080", FindJettyHttpConfigurationRisks.LIMITS),
                Arguments.of("jetty.deploy.scanInterval", "1", FindJettyHttpConfigurationRisks.DEPLOY),
                Arguments.of("jetty.logging.appender.NAME", "CONSOLE", FindJettyHttpConfigurationRisks.LOGGING),
                Arguments.of("environment", "ee10", FindJettyHttpConfigurationRisks.EE));
    }

    @Test
    void marksYamlComplianceLimitsAndDeployment() {
        rewriteRun(
                yaml("""
                        jetty:
                          httpConfig:
                            uriCompliance: LEGACY
                            requestHeaderSize: 32768
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), FindJettyHttpConfigurationRisks.COMPLIANCE);
                            assertContains(after.printAll(), FindJettyHttpConfigurationRisks.LIMITS);
                        })),
                yaml("""
                        jetty:
                          deploy:
                            scanInterval: 5
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJettyHttpConfigurationRisks.DEPLOY))));
    }

    @ParameterizedTest(name = "moved XML class {0}")
    @ValueSource(strings = {
            "org.eclipse.jetty.server.handler.HandlerCollection",
            "org.eclipse.jetty.server.handler.HandlerList",
            "org.eclipse.jetty.server.handler.RequestLogHandler",
            "org.eclipse.jetty.http.HttpContent",
            "org.eclipse.jetty.http.ResourceHttpContent",
            "org.eclipse.jetty.http.PrecompressedHttpContent"
    })
    void marksMovedOrRemovedJettyXmlClasses(String type) {
        rewriteRun(xml("<Configure class=\"" + type + "\"><Set name=\"handler\"/></Configure>",
                source -> source.path("etc/jetty.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJettyHttpConfigurationRisks.XML_CLASS))));
    }

    @Test
    void marksStartModuleAndEeEnvironmentText() {
        rewriteRun(text("""
                java -jar "$JETTY_HOME/start.jar" \
                  --add-to-start=http,servlet,webapp,deploy
                """, source -> source.path("bin/start-jetty.sh").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindJettyHttpConfigurationRisks.MODULES);
                    assertContains(after.printAll(), FindJettyHttpConfigurationRisks.EE);
                })));
    }

    @Test
    void marksLegacyLoggingAndParserLimitText() {
        rewriteRun(text("""
                jetty-logging.properties
                jetty.httpConfig.requestHeaderSize=65536
                jetty.httpConfig.httpCompliance=LEGACY
                """, source -> source.path("migration-notes.conf").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindJettyHttpConfigurationRisks.LOGGING);
                    assertContains(after.printAll(), FindJettyHttpConfigurationRisks.LIMITS);
                    assertContains(after.printAll(), FindJettyHttpConfigurationRisks.COMPLIANCE);
                })));
    }

    @Test
    void pomAndUnrelatedLookalikesAreNoop() {
        rewriteRun(
                xml("<project><name>org.eclipse.jetty.http.HttpContent</name></project>",
                        source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))),
                properties("company.http.port=8080", source -> source.path("application.properties")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                yaml("documentation: HandlerCollection is a collection", source ->
                        source.path("docs.yml").afterRecipe(after -> assertNoMarker(after.printAll()))),
                text("A start jar is useful", source -> source.path("README.txt")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void generatedConfigurationIsSkipped() {
        rewriteRun(properties("jetty.httpConfig.uriCompliance=LEGACY", source ->
                source.path("target/classes/start.d/http.ini").afterRecipe(after ->
                        assertNoMarker(after.printAll()))));
    }

    @Test
    void configurationMarkersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties("jetty.httpConfig.uriCompliance=LEGACY", source -> source.after(actual -> actual)
                        .afterRecipe(after -> assertEqualsCount(
                                after.printAll(), FindJettyHttpConfigurationRisks.COMPLIANCE, 1))));
    }

    private static SourceSpecs markedProperties(String value, String marker) {
        return properties(value, source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), marker)));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~(") || actual.contains("#~~(") || actual.contains("<!--~~("), actual);
    }

    private static void assertEqualsCount(String actual, String expected, int count) {
        assertTrue(JettyHttpTestSupport.occurrences(actual, expected) == count, actual);
    }
}
