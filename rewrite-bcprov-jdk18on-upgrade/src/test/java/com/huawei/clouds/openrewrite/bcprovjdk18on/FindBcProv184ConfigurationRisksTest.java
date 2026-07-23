package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class FindBcProv184ConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindBcProv184ConfigurationRisks());
    }

    @ParameterizedTest(name = "ASN.1 limit {0}")
    @ValueSource(strings = {"org.bouncycastle.asn1.max_cons_depth", "org.bouncycastle.asn1.max_limit"})
    void marksAsn1LimitProperties(String key) {
        rewriteRun(markedProperties(key + "=64", FindBcProv184ConfigurationRisks.ASN1_LIMITS));
    }

    @ParameterizedTest(name = "PKCS12 setting {0}")
    @ValueSource(strings = {"org.bouncycastle.pkcs12.max_it_count", "org.bouncycastle.pkcs12.default"})
    void marksPkcs12Settings(String key) {
        rewriteRun(markedProperties(key + "=10000000", FindBcProv184ConfigurationRisks.PKCS12_LIMIT));
    }

    @ParameterizedTest(name = "compatibility switch {0}")
    @ValueSource(strings = {"org.bouncycastle.asn1.allow_wrong_oid_enc", "org.bouncycastle.pemreader.lax",
            "org.bouncycastle.ec.disable_f2m", "org.bouncycastle.drbg.effective_256bits_entropy",
            "org.bouncycastle.rsa.no_lenstra_check"})
    void marksCompatibilityAndSecuritySwitches(String key) {
        rewriteRun(markedProperties(key + "=true", FindBcProv184ConfigurationRisks.COMPATIBILITY_SWITCH));
    }

    @Test
    void marksJavaSecurityProviderOrder() {
        rewriteRun(markedProperties(
                "security.provider.3=org.bouncycastle.jce.provider.BouncyCastleProvider",
                FindBcProv184ConfigurationRisks.PROVIDER_ORDER));
    }

    @Test
    void marksYamlProviderAndLimits() {
        rewriteRun(
                yaml("""
                        security:
                          provider.4: org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBcProv184ConfigurationRisks.PROVIDER_ORDER))),
                yaml("""
                        crypto:
                          org.bouncycastle.asn1.max_cons_depth: 80
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBcProv184ConfigurationRisks.ASN1_LIMITS))));
    }

    @Test
    void marksXmlSystemPropertyConfiguration() {
        rewriteRun(xml("""
                <configuration>
                  <property name="org.bouncycastle.pkcs12.max_it_count" value="7000000"/>
                </configuration>
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindBcProv184ConfigurationRisks.PKCS12_LIMIT))));
    }

    @Test
    void marksManifestAndBndPackagingMetadata() {
        rewriteRun(
                text("Import-Package: org.bouncycastle.*", source -> source.path("META-INF/MANIFEST.MF")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBcProv184ConfigurationRisks.PACKAGING))),
                text("-includeresource: @bcprov-jdk18on.jar!/META-INF/**", source -> source.path("bnd.bnd")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBcProv184ConfigurationRisks.PACKAGING))));
    }

    @Test
    void arbitraryLookalikesAndPomTextAreNoop() {
        rewriteRun(
                properties("example.asn1.max_limit=64", source -> source.afterRecipe(after ->
                        assertNoMarker(after.printAll()))),
                properties("documentation=org.bouncycastle.asn1.max_limit", source ->
                        source.path("log4j2.component.properties").afterRecipe(after ->
                                assertNoMarker(after.printAll()))),
                yaml("provider: com.example.BouncyCastleProviderFactory", source -> source.afterRecipe(after ->
                        assertNoMarker(after.printAll()))),
                yaml("docs: org.bouncycastle.pkcs12.max_it_count", source -> source.path("application.yml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                yaml("docs:\n  provider.4: org.bouncycastle.jce.provider.BouncyCastleProvider", source ->
                        source.path("documentation.yml").afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml("<docs><code>org.bouncycastle.asn1.max_limit</code></docs>", source ->
                        source.path("docs.xml").afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml("<project><name>org.bouncycastle.asn1.max_limit</name></project>", source -> source.path("pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                text("org.bouncycastle.asn1.max_limit=64", source -> source.path("README.md")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksExactPlainJavaSecurityEntryButIgnoresComments() {
        rewriteRun(text("security.provider.3=org.bouncycastle.jce.provider.BouncyCastleProvider", source ->
                source.path("java.security").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindBcProv184ConfigurationRisks.PROVIDER_ORDER))));
        rewriteRun(text("# org.bouncycastle.asn1.max_limit=unbounded", source -> source.path("java.security")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
        rewriteRun(text("// documentation mentions org.bouncycastle only", source -> source.path("sandbox.policy")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void generatedConfigurationsAreSkippedAndMarkersAreIdempotent() {
        rewriteRun(properties("org.bouncycastle.asn1.max_limit=10m", source ->
                source.path("target/classes/application.properties").afterRecipe(after ->
                        assertNoMarker(after.printAll()))));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties("org.bouncycastle.asn1.max_limit=10m", source -> source.after(actual -> actual)
                        .afterRecipe(after -> assertCount(after.printAll(),
                                FindBcProv184ConfigurationRisks.ASN1_LIMITS, 1))));
    }

    private static SourceSpecs markedProperties(String value, String message) {
        return properties(value, source -> source.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), message)));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~(") || actual.contains("#~~(") || actual.contains("<!--~~("), actual);
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
