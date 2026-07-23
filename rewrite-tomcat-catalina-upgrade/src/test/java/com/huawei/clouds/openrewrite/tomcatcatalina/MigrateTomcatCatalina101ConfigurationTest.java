package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MigrateTomcatCatalina101ConfigurationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateTomcatCatalina101Configuration());
    }

    @ParameterizedTest(name = "removed listener attribute {0}")
    @ValueSource(strings = {"awtThreadProtection", "gcDaemonProtection", "ldapPoolProtection", "tokenPollerProtection", "xmlParsingProtection", "forkJoinCommonPoolProtection"})
    void removesEveryObsoleteAttribute(String attribute) {
        rewriteRun(xml(
                "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" " + attribute + "=\"true\"/></Server>",
                "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\"/></Server>",
                source -> source.path("conf/server.xml")));
    }

    @Test
    void removesAllSixButPreservesSupportedAttributesAndFormatting() {
        rewriteRun(xml(
                "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" appContextProtection=\"true\" awtThreadProtection=\"true\" gcDaemonProtection=\"false\" ldapPoolProtection=\"true\" tokenPollerProtection=\"true\" xmlParsingProtection=\"true\" forkJoinCommonPoolProtection=\"true\" urlCacheProtection=\"false\"/></Server>",
                "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" appContextProtection=\"true\" urlCacheProtection=\"false\"/></Server>",
                source -> source.path("server.xml")));
    }

    @Test
    void lookalikeListenerAndAttributesAreNoop() {
        rewriteRun(
                xml("<Server><Listener className=\"com.example.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\"/></Server>", source -> source.path("server.xml")),
                xml("<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" gcDaemonProtectionPolicy=\"true\"/></Server>", source -> source.path("conf/server.xml")),
                xml("<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\"/></Server>", source -> source.path("application.xml")),
                xml("<Catalog><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\"/></Catalog>", source -> source.path("server.xml"))
        );
    }

    @Test
    void generatedConfigurationIsNoop() {
        rewriteRun(xml("<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\"/></Server>",
                source -> source.path("target/server.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(specification -> specification.cycles(2).expectedCyclesThatMakeChanges(1), xml(
                "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" xmlParsingProtection=\"true\"/></Server>",
                "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\"/></Server>",
                source -> source.path("server.xml")));
    }
}
