package com.huawei.clouds.openrewrite.log4jcore;

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

class FindLog4jCore25SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindLog4jCore25SourceRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(Log4jCoreTestApi.legacySources()));
    }

    @Test
    void marksCustomPluginAnnotation() {
        marked("""
                import org.apache.logging.log4j.core.config.plugins.Plugin;
                @Plugin(name="Audit", category="Core") class AuditPlugin {}
                """, FindLog4jCore25SourceRisks.PLUGIN_DISCOVERY);
    }

    @ParameterizedTest(name = "programmatic package discovery {0}")
    @MethodSource("packageDiscovery")
    void marksProgrammaticPackageDiscovery(String label, String invocation) {
        marked("import org.apache.logging.log4j.core.config.plugins.util.PluginManager; " +
               "class C { void register(){ " + invocation + "; } }",
                FindLog4jCore25SourceRisks.PLUGIN_DISCOVERY);
    }

    static Stream<Arguments> packageDiscovery() {
        return Stream.of(
                Arguments.of("crate fixture", "PluginManager.addPackage(C.class.getPackage().getName())"),
                Arguments.of("collection", "PluginManager.addPackages(java.util.List.of(\"com.acme.logging\"))")
        );
    }

    @Test
    void marksJmxImportAndInvocation() {
        marked("""
                import org.apache.logging.log4j.core.jmx.Server;
                class Admin { void reload(){ Server.reregisterMBeansAfterReconfigure(); } }
                """, FindLog4jCore25SourceRisks.JMX);
    }

    @ParameterizedTest(name = "date implementation {0}")
    @ValueSource(strings = {"FixedDateFormat", "FastDateFormat"})
    void marksLegacyDateImplementations(String type) {
        marked("import org.apache.logging.log4j.core.util.datetime." + type + "; " +
               "class C { String render(long now){ return new " + type + "().format(now); } }",
                FindLog4jCore25SourceRisks.DATETIME);
    }

    @ParameterizedTest(name = "JNDI integration {0}")
    @MethodSource("jndiIntegrations")
    void marksJndiIntegrations(String type, String expression) {
        marked("import " + type + "; class C { Object value = " + expression + "; }",
                FindLog4jCore25SourceRisks.JNDI);
    }

    static Stream<Arguments> jndiIntegrations() {
        return Stream.of(
                Arguments.of("org.apache.logging.log4j.core.lookup.JndiLookup", "new JndiLookup()"),
                Arguments.of("org.apache.logging.log4j.core.net.JndiManager", "new JndiManager()"),
                Arguments.of("org.apache.logging.log4j.core.appender.db.jdbc.JndiConnectionSource", "new JndiConnectionSource()"),
                Arguments.of("org.apache.logging.log4j.core.appender.mom.JmsAppender", "new JmsAppender()")
        );
    }

    @ParameterizedTest(name = "script integration {0}")
    @ValueSource(strings = {"Script", "ScriptFile", "ScriptRef"})
    void marksScriptIntegrations(String type) {
        marked("import org.apache.logging.log4j.core.script." + type + "; class C { Object script = new " +
               type + "(); }", FindLog4jCore25SourceRisks.SCRIPT);
    }

    @Test
    void marksThrowableProxyPersistenceBoundary() {
        marked("import org.apache.logging.log4j.core.impl.ThrowableProxy; " +
               "class Store { Object snapshot(Throwable t){ return new ThrowableProxy(t); } }",
                FindLog4jCore25SourceRisks.SERIALIZATION);
    }

    @ParameterizedTest(name = "LogEvent serialization {0}")
    @MethodSource("serializationCalls")
    void marksLogEventSerializationCalls(String label, String call) {
        marked("import org.apache.logging.log4j.core.impl.Log4jLogEvent; " +
               "class Store { Object snapshot(Log4jLogEvent event, java.io.Serializable data){ return " +
               call + "; } }", FindLog4jCore25SourceRisks.SERIALIZATION);
    }

    static Stream<Arguments> serializationCalls() {
        return Stream.of(
                Arguments.of("serialize", "Log4jLogEvent.serialize(event, true)"),
                Arguments.of("deserialize", "Log4jLogEvent.deserialize(data)")
        );
    }

    @ParameterizedTest(name = "changed property {0}")
    @MethodSource("changedProperties")
    void marksChangedSystemProperties(String operation, String property) {
        String invocation = "clearProperty".equals(operation)
                ? "System.clearProperty(\"" + property + "\")"
                : "System." + operation + "(\"" + property + "\", \"true\")";
        if ("getProperty".equals(operation)) invocation = "System.getProperty(\"" + property + "\", \"false\")";
        marked("class Bootstrap { Object configure(){ return " + invocation + "; } }",
                FindLog4jCore25SourceRisks.PROPERTY);
    }

    static Stream<Arguments> changedProperties() {
        return Stream.of(
                Arguments.of("setProperty", "log4j2.formatMsgNoLookups"),
                Arguments.of("getProperty", "log4j.formatMsgNoLookups"),
                Arguments.of("clearProperty", "log4j2.loggerContextFactory")
        );
    }

    @Test
    void explicitlySupportedLegacyContextSelectorAliasIsNoop() {
        clean("class Bootstrap { void configure(){ System.setProperty(\"Log4jContextSelector\", \"com.acme.Selector\"); } }");
    }

    @ParameterizedTest(name = "explicit lookup pattern {0}")
    @ValueSource(strings = {"%m{lookups}%n", "%msg{lookups}", "%message{lookups}", "$${ctx:user}"})
    void marksExplicitLookupPatterns(String pattern) {
        marked("class C { String pattern = \"" + pattern + "\"; }", FindLog4jCore25SourceRisks.LOOKUP);
    }

    @Test
    void typeMatchingAvoidsUnrelatedSameNamedApplicationApis() {
        clean("""
                @interface Plugin { String name(); }
                class PluginManager { static void addPackage(String p){} }
                class Log4jLogEvent { static Object serialize(Object e, boolean b){return e;} }
                @Plugin(name="own") class C { Object f(){ PluginManager.addPackage("own");
                    return Log4jLogEvent.serialize(this, true); } }
                """);
    }

    @Test
    void ordinaryPatternsAndSupportedPropertiesAreNoop() {
        clean("class C { String pattern=\"%m%n $${env:HOME}\"; Object f(){ " +
              "return System.getProperty(\"log4j2.disableJmx\"); } }");
    }

    @Test
    void generatedSourcesAreNoop() {
        rewriteRun(java("import org.apache.logging.log4j.core.lookup.JndiLookup; class C { Object x=new JndiLookup(); }",
                source -> source.path("target/generated-sources/C.java")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("import org.apache.logging.log4j.core.script.Script; class C { Object x=new Script(); }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindLog4jCore25SourceRisks.SCRIPT, 2))));
    }

    private void marked(String source, String message) {
        rewriteRun(java(source, spec -> spec.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), message))));
    }

    private void clean(String source) {
        rewriteRun(java(source, spec -> spec.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~("), actual);
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
