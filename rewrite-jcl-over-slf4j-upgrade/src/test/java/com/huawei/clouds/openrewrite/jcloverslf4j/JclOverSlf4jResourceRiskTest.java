package com.huawei.clouds.openrewrite.jcloverslf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class JclOverSlf4jResourceRiskTest implements RewriteTest {
    @Test
    void marksSlf4j2ProviderServiceDescriptor() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jResourceRisks()),
                text("# providers\ncom.example.LoggingProvider\ncom.example.AuditProvider # retained comment\n",
                        source -> source.path("src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider")
                                .after(actual -> actual).afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("# providers"));
                                    assertTrue(printed.contains("retained comment"));
                                    assertTrue(occurrences(printed, "verify exactly one implementation") == 2);
                                })));
    }

    @Test
    void marksCommonsLoggingServiceAndPropertiesDiscovery() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jResourceRisks()),
                text("com.example.CustomLogFactory\n",
                        source -> source.path("src/main/resources/META-INF/services/org.apache.commons.logging.LogFactory")
                                .after(actual -> actual).afterRecipe(file ->
                                        assertTrue(file.printAll().contains("ignores Commons Logging")))),
                text("org.apache.commons.logging.LogFactory=com.example.CustomLogFactory\nuse_tccl=true\n",
                        source -> source.path("src/main/resources/commons-logging.properties")
                                .after(actual -> actual).afterRecipe(file ->
                                        assertTrue(file.printAll().contains("always delegates to SLF4J")))));
    }

    @Test
    void marksGradleVersionCatalogOwner() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jResourceRisks()),
                text("[versions]\nslf4j = \"1.7.36\"\n[libraries]\njcl-bridge = { module = \"org.slf4j:jcl-over-slf4j\", version.ref = \"slf4j\" }\n",
                        source -> source.path("gradle/libs.versions.toml").after(actual -> actual)
                                .afterRecipe(file -> assertTrue(file.printAll().contains("select 2.0.17 here")))));
    }

    @Test
    void marksExplicitProviderAndStaticBinderConfiguration() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jResourceRisks()),
                text("slf4j.provider=com.example.Provider\nlegacy=org.slf4j.impl.StaticLoggerBinder\n",
                        source -> source.path("src/main/resources/application.properties").after(actual -> actual)
                                .afterRecipe(file -> assertTrue(file.printAll().contains("service descriptor")))));
    }

    @Test
    void marksXmlDiscoveryValues() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jResourceRisks()),
                xml("<context><property name=\"org.apache.commons.logging.LogFactory\" value=\"example.Custom\"/></context>",
                        source -> source.path("src/main/resources/context.xml").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("discovery")))));
    }

    @Test
    void leavesModernUnrelatedConfigurationAlone() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jResourceRisks()),
                text("logging.level.root=INFO\n", source -> source.path("src/main/resources/application.properties")),
                text("[versions]\nslf4j = \"2.0.17\"\n", source -> source.path("gradle/libs.versions.toml")));
    }

    @Test
    void excludesGeneratedInstalledAndBuiltResources() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jResourceRisks()),
                text("example.Provider\n", source -> source.path("build/resources/main/META-INF/services/org.slf4j.spi.SLF4JServiceProvider")),
                text("org.apache.commons.logging.LogFactory=example.Factory\n", source -> source.path("generated/commons-logging.properties")),
                text("slf4j.provider=example.Provider\n", source -> source.path("install/application.properties")));
    }

    @Test
    void resourceMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jResourceRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("example.Provider\n",
                        source -> source.path("src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider")
                                .after(actual -> actual)));
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int index = value.indexOf(needle); index >= 0; index = value.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }
}
