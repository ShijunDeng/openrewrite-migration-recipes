package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class BcProvRecommendedRecipeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                        "com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateBcProvJdk18onTo1_84"))
                .parser(JavaParser.fromJavaVersion().classpath("bcprov-jdk18on"));
    }

    @Test
    void upgradesDependencyMigratesPackAndMarksProviderBoundary() {
        rewriteRun(
                pomXml(pom("1.79"), pom("1.84")),
                java("""
                        import java.security.Security;
                        import org.bouncycastle.jce.provider.BouncyCastleProvider;
                        import org.bouncycastle.util.Pack;

                        class CryptoBootstrap {
                            static { Security.addProvider(new BouncyCastleProvider()); }
                            void write(byte[] output) { Pack.longToBigEndian(7L, output, 0, 4); }
                        }
                        """, source -> source.after(actual -> {
                            assertTrue(actual.contains("Pack.longToBigEndian_Low"), actual);
                            return actual;
                        }).afterRecipe(after -> assertTrue(after.printAll().contains(
                                FindBcProv184SourceRisks.PROVIDER), after.printAll()))));
    }

    @Test
    void packageAutoAndLegacyReviewMarkerCoexist() {
        rewriteRun(java("""
                import org.bouncycastle.pqc.crypto.bike.BIKEParameters;
                class Bike { BIKEParameters parameters = BIKEParameters.bike128; }
                """, source -> source.after(actual -> {
                    assertTrue(actual.contains("org.bouncycastle.pqc.legacy.bike."), actual);
                    return actual;
                }).afterRecipe(after -> assertTrue(after.printAll().contains(
                        FindBcProv184SourceRisks.LEGACY_PQC_MOVED), after.printAll()))));
    }

    @Test
    void wrapUtilMoveAndBehaviorMarkerCompose() {
        rewriteRun(java("""
                import org.bouncycastle.pqc.jcajce.provider.util.WrapUtil;
                class Wrapping { Object wrapper() { return WrapUtil.getWrapper("AES"); } }
                """, source -> source.after(actual -> {
                    assertTrue(actual.contains(
                            "org.bouncycastle.jcajce.provider.asymmetric.util.WrapUtil"), actual);
                    return actual;
                }).afterRecipe(after -> assertTrue(after.printAll().contains(
                        FindBcProv184SourceRisks.WRAP_UTIL), after.printAll()))));
    }

    @Test
    void aggregateIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("1.81"), pom("1.84")));
    }

    private static String pom(String version) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId>" +
               "<version>1</version><dependencies><dependency><groupId>org.bouncycastle</groupId>" +
               "<artifactId>bcprov-jdk18on</artifactId><version>" + version +
               "</version></dependency></dependencies></project>";
    }
}
