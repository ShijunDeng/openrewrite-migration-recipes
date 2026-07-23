package com.huawei.clouds.openrewrite.log4jcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class FindLog4jCore25ConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindLog4jCore25ConfigurationRisks());
    }

    @Test
    void marksImplicitExceptionRenderingInXmlPatternAttribute() {
        markedXml("<Configuration><Appenders><Console><PatternLayout pattern=\"%d %p %m%n\"/>" +
                  "</Console></Appenders></Configuration>", FindLog4jCore25ConfigurationRisks.EXCEPTION);
    }

    @Test
    void marksImplicitExceptionRenderingInNestedXmlPattern() {
        markedXml("<Configuration><PatternLayout><Pattern>%d %p %m%n</Pattern></PatternLayout></Configuration>",
                FindLog4jCore25ConfigurationRisks.EXCEPTION);
    }

    @Test
    void marksImplicitExceptionRenderingInProperties() {
        markedProperties("appender.console.layout.type = PatternLayout\n" +
                         "appender.console.layout.pattern = %d %p %m%n\n",
                FindLog4jCore25ConfigurationRisks.EXCEPTION);
    }

    @Test
    void propertiesAlwaysWriteExceptionsFalseSuppressesImplicitMarker() {
        rewriteRun(properties("appender.console.layout.pattern = %d %m%n\n" +
                        "appender.console.layout.alwaysWriteExceptions = false\n",
                source -> source.path("src/main/resources/log4j2.properties")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void explicitThrowableConverterIsStable() {
        cleanXml("<Configuration><PatternLayout pattern=\"%d %m%n%xEx\"/></Configuration>");
    }

    @Test
    void disabledAlwaysWriteExceptionsIsStable() {
        cleanXml("<Configuration><PatternLayout pattern=\"%d %m%n\" alwaysWriteExceptions=\"false\"/>" +
                 "</Configuration>");
    }

    @ParameterizedTest(name = "removed exception ansi option {0}")
    @ValueSource(strings = {"%ex{ansi}", "%exception{short,ansi}", "%throwable{ANSI}", "%xEx{full,ansi}"})
    void marksRemovedExceptionAnsiOption(String pattern) {
        markedXml("<Configuration><PatternLayout pattern=\"" + pattern + "\"/></Configuration>",
                FindLog4jCore25ConfigurationRisks.ANSI);
    }

    @ParameterizedTest(name = "date directive {0}")
    @ValueSource(strings = {"%d{yyyy-MM-dd HH:mm:ss.nnn}", "%date{UNIX_MILLIS} %m", "%d{yyyy-x-MM}",
            "%d{yyyy-MM-dd HH:mm:ss}"})
    void marksDateFormatterSensitivePatterns(String pattern) {
        markedXml("<Configuration><PatternLayout pattern=\"" + pattern + "%xEx\"/></Configuration>",
                FindLog4jCore25ConfigurationRisks.DATE);
    }

    @Test
    void marksDeprecatedXmlPackageScanning() {
        markedXml("<Configuration packages=\"com.acme.logging\"><Appenders/></Configuration>",
                FindLog4jCore25ConfigurationRisks.PACKAGES);
    }

    @Test
    void marksDeprecatedRootStatusAttribute() {
        markedXml("<Configuration status=\"WARN\"><Appenders/></Configuration>",
                FindLog4jCore25ConfigurationRisks.STATUS);
    }

    @ParameterizedTest(name = "JNDI XML value {0}")
    @ValueSource(strings = {"${jndi:java:comp/env/jdbc/App}", "$${jndi:java:comp/env/mail/Session}"})
    void marksJndiXmlValues(String lookup) {
        markedXml("<Configuration><Property name=\"resource\">" + lookup + "</Property></Configuration>",
                FindLog4jCore25ConfigurationRisks.JNDI);
    }

    @ParameterizedTest(name = "script tag {0}")
    @ValueSource(strings = {"Script", "ScriptFile", "ScriptRef"})
    void marksScriptElements(String tag) {
        markedXml("<Configuration><Scripts><" + tag + " name=\"routing\" language=\"groovy\"/>" +
                  "</Scripts></Configuration>", FindLog4jCore25ConfigurationRisks.SCRIPT);
    }

    @ParameterizedTest(name = "explicit lookup {0}")
    @ValueSource(strings = {"%m{lookups}%n", "%msg{lookups}", "%message{lookups}", "$${ctx:user}"})
    void marksExplicitLookupConfiguration(String pattern) {
        markedProperties("appender.console.layout.pattern = " + pattern + "\n",
                FindLog4jCore25ConfigurationRisks.LOOKUPS);
    }

    @ParameterizedTest(name = "obsolete lookup switch {0}")
    @ValueSource(strings = {"log4j2.formatMsgNoLookups", "log4j.formatMsgNoLookups"})
    void marksObsoleteLookupSwitch(String key) {
        markedProperties(key + " = true\n", FindLog4jCore25ConfigurationRisks.LOOKUPS);
    }

    @ParameterizedTest(name = "properties key {0}")
    @MethodSource("deprecatedPropertyKeys")
    void marksDeprecatedPropertiesConfiguration(String key, String message) {
        markedProperties(key + " = com.acme.logging\n", message);
    }

    static Stream<Arguments> deprecatedPropertyKeys() {
        return Stream.of(
                Arguments.of("packages", FindLog4jCore25ConfigurationRisks.PACKAGES),
                Arguments.of("log4j.plugin.packages", FindLog4jCore25ConfigurationRisks.PACKAGES),
                Arguments.of("status", FindLog4jCore25ConfigurationRisks.STATUS)
        );
    }

    @ParameterizedTest(name = "bundle metadata {0}")
    @ValueSource(strings = {"bnd.bnd", "MANIFEST.MF"})
    void marksLog4jBundleAndModuleMetadata(String file) {
        markedText("Import-Package: org.apache.logging.log4j.core;version=\"[2.13,3)\"\n", file,
                FindLog4jCore25ConfigurationRisks.OSGI_JPMS);
    }

    @Test
    void marksYamlAndJsonRisks() {
        rewriteRun(
                text("pattern: \"%ex{ansi}\"\n", source -> source.path("src/main/resources/log4j2.yaml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindLog4jCore25ConfigurationRisks.ANSI))),
                text("{\"pattern\":\"%d{yyyy-MM-dd.nnn} %xEx\"}\n",
                        source -> source.path("src/main/resources/log4j2.json")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertContains(after.printAll(), FindLog4jCore25ConfigurationRisks.DATE))));
    }

    @Test
    void marksStructuredConfigurationKeysAndImplicitException() {
        String yaml = "status: WARN\npackages: com.acme.logging\nScriptFile:\n  name: routing\n" +
                      "pattern: \"%d %m%n\"\n";
        rewriteRun(text(yaml, source -> source.path("src/main/resources/log4j2.yaml")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertContains(printed, FindLog4jCore25ConfigurationRisks.STATUS);
                    assertContains(printed, FindLog4jCore25ConfigurationRisks.PACKAGES);
                    assertContains(printed, FindLog4jCore25ConfigurationRisks.SCRIPT);
                    assertContains(printed, FindLog4jCore25ConfigurationRisks.EXCEPTION);
                })));
    }

    @Test
    void pomXmlAndOrdinaryResourcesAreNoop() {
        rewriteRun(
                xml("<project><properties><pattern>%ex{ansi}</pattern></properties></project>",
                        source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))),
                text("ordinary application text mentioning org.apache.logging.log4j\n",
                        source -> source.path("README.txt").afterRecipe(after -> assertNoMarker(after.printAll()))),
                text("Document %ex{ansi}, $${jndi:java:comp/env/x}, and %d{yyyy.nnn}.\n",
                        source -> source.path("README.md").afterRecipe(after -> assertNoMarker(after.printAll()))),
                properties("status=active\npackages=com.example\nappender.console.layout.pattern=%m%n\n",
                        source -> source.path("src/main/resources/application.properties")
                                .afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml("<article status=\"draft\"><code>%ex{ansi}</code></article>",
                        source -> source.path("src/main/resources/help.xml")
                                .afterRecipe(after -> assertNoMarker(after.printAll()))),
                properties("appender.console.layout.pattern = %d %m%n%xEx\n",
                        source -> source.path("src/main/resources/log4j2.properties")
                                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void generatedConfigurationIsNoop() {
        rewriteRun(xml("<Configuration packages=\"com.acme\" status=\"WARN\"/>",
                source -> source.path("build/generated/log4j2.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties("log4j2.formatMsgNoLookups=true\n",
                        source -> source.path("src/main/resources/log4j2.properties")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertCount(after.printAll(), FindLog4jCore25ConfigurationRisks.LOOKUPS, 1))));
    }

    private void markedXml(String source, String message) {
        rewriteRun(xml(source, spec -> spec.path("src/main/resources/log4j2.xml")
                .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    private void cleanXml(String source) {
        rewriteRun(xml(source, spec -> spec.path("src/main/resources/log4j2.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    private void markedProperties(String source, String message) {
        rewriteRun(properties(source, spec -> spec.path("src/main/resources/log4j2.properties")
                .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    private void markedText(String source, String path, String message) {
        rewriteRun(text(source, spec -> spec.path(path).after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~(") || actual.contains("<!--~~(") || actual.contains("~~>"), actual);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
