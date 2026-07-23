package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateBcProv184JavaTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateDeterministicBcProv1_84Java";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe())
                .parser(JavaParser.fromJavaVersion().classpath("bcprov-jdk18on"));
    }

    @Test
    void movesBikePackage() {
        rewriteRun(java(
                """
                import org.bouncycastle.pqc.crypto.bike.BIKEParameters;

                class Crypto {
                    BIKEParameters parameters = BIKEParameters.bike128;
                }
                """,
                """
                import org.bouncycastle.pqc.legacy.bike.BIKEParameters;

                class Crypto {
                    BIKEParameters parameters = BIKEParameters.bike128;
                }
                """));
    }

    @Test
    void migratesRealCipherRadarBikeKemShape() {
        // Fixed fixture:
        // https://github.com/nk-sentinel/CipherRadarTestProj/blob/bf3a5a08471f6571a0dd16595185a8ad21673ca5/java/BouncyCastlePQC.java#L20-L48
        rewriteRun(java(
                """
                package benchmark;

                import org.bouncycastle.pqc.crypto.bike.BIKEKEMGenerator;

                public class BouncyCastlePQC {
                    // EXPECTED: BIKE | - | - | info | quantum-safe
                    public void bikeKEM() { new BIKEKEMGenerator(null); }
                }
                """,
                """
                package benchmark;

                import org.bouncycastle.pqc.legacy.bike.BIKEKEMGenerator;

                public class BouncyCastlePQC {
                    // EXPECTED: BIKE | - | - | info | quantum-safe
                    public void bikeKEM() { new BIKEKEMGenerator(null); }
                }
                """));
    }

    @Test
    void movesPicnicPackage() {
        rewriteRun(java(
                """
                import org.bouncycastle.pqc.crypto.picnic.PicnicParameters;

                class Crypto {
                    PicnicParameters parameters = PicnicParameters.picnicl1fs;
                }
                """,
                """
                import org.bouncycastle.pqc.legacy.picnic.PicnicParameters;

                class Crypto {
                    PicnicParameters parameters = PicnicParameters.picnicl1fs;
                }
                """));
    }

    @Test
    void movesRainbowPackage() {
        rewriteRun(java(
                """
                import org.bouncycastle.pqc.crypto.rainbow.RainbowParameters;

                class Crypto {
                    RainbowParameters parameters = RainbowParameters.rainbowIIIclassic;
                }
                """,
                """
                import org.bouncycastle.pqc.legacy.rainbow.RainbowParameters;

                class Crypto {
                    RainbowParameters parameters = RainbowParameters.rainbowIIIclassic;
                }
                """));
    }

    @Test
    void leavesSphincsPlusForVersionAwareManualDecision() {
        // Real shape: https://github.com/Spuddy10345/PQCBenchmark/blob/f91482d015054e38a216d423b9fe019b7cb4b674/app/src/main/java/com/example/pqcbenchmark/SphincsPlusAlgorithm.java
        rewriteRun(java("""
                import org.bouncycastle.pqc.crypto.sphincsplus.SPHINCSPlusParameters;

                class Crypto {
                    SPHINCSPlusParameters parameters = SPHINCSPlusParameters.sha2_128f;
                }
                """));
    }

    @Test
    void movesAllThreeSemanticallyStablePackagesTogether() {
        rewriteRun(java(
                """
                import org.bouncycastle.pqc.crypto.bike.BIKEParameters;
                import org.bouncycastle.pqc.crypto.picnic.PicnicParameters;
                import org.bouncycastle.pqc.crypto.rainbow.RainbowParameters;

                class Algorithms {
                    Object[] all = { BIKEParameters.bike128, PicnicParameters.picnicl1fs,
                            RainbowParameters.rainbowIIIclassic };
                }
                """,
                """
                import org.bouncycastle.pqc.legacy.bike.BIKEParameters;
                import org.bouncycastle.pqc.legacy.picnic.PicnicParameters;
                import org.bouncycastle.pqc.legacy.rainbow.RainbowParameters;

                class Algorithms {
                    Object[] all = { BIKEParameters.bike128, PicnicParameters.picnicl1fs,
                            RainbowParameters.rainbowIIIclassic };
                }
                """));
    }

    @Test
    void movesWrapUtilToPromotedProviderPackage() {
        rewriteRun(java(
                """
                import org.bouncycastle.pqc.jcajce.provider.util.WrapUtil;

                class Wrapping {
                    Object wrapper = WrapUtil.getWrapper("AES");
                }
                """,
                """
                import org.bouncycastle.jcajce.provider.asymmetric.util.WrapUtil;

                class Wrapping {
                    Object wrapper = WrapUtil.getWrapper("AES");
                }
                """));
    }

    @Test
    void replacesRemovedPackOverloadWithEquivalentLowOrderMethod() {
        rewriteRun(java(
                """
                import org.bouncycastle.util.Pack;

                class Encoding {
                    void write(long value, byte[] output) {
                        Pack.longToBigEndian(value, output, 2, 4);
                    }
                }
                """,
                """
                import org.bouncycastle.util.Pack;

                class Encoding {
                    void write(long value, byte[] output) {
                        Pack.longToBigEndian_Low(value, output, 2, 4);
                    }
                }
                """));
    }

    @Test
    void replacesStaticImportedPackOverload() {
        rewriteRun(java(
                """
                import static org.bouncycastle.util.Pack.longToBigEndian;

                class Encoding {
                    void write(byte[] output) {
                        longToBigEndian(7L, output, 0, 3);
                    }
                }
                """,
                """
                import static org.bouncycastle.util.Pack.longToBigEndian_Low;

                class Encoding {
                    void write(byte[] output) {
                        longToBigEndian_Low(7L, output, 0, 3);
                    }
                }
                """));
    }

    @Test
    void fixesHpkeP384ConstantTypo() {
        rewriteRun(java(
                """
                import org.bouncycastle.crypto.hpke.HPKE;

                class HpkeConfig {
                    short kem = HPKE.kem_P384_SHA348;
                }
                """,
                """
                import org.bouncycastle.crypto.hpke.HPKE;

                class HpkeConfig {
                    short kem = HPKE.kem_P384_SHA384;
                }
                """));
    }

    @Test
    void fixesStaticImportedHpkeP384ConstantTypo() {
        rewriteRun(java(
                """
                import static org.bouncycastle.crypto.hpke.HPKE.kem_P384_SHA348;

                class HpkeConfig {
                    short kem = kem_P384_SHA348;
                }
                """,
                """
                import static org.bouncycastle.crypto.hpke.HPKE.kem_P384_SHA384;

                class HpkeConfig {
                    short kem = kem_P384_SHA384;
                }
                """));
    }

    @Test
    void replacesRemovedEcPrivateKeyGetterWithExactFormerImplementation() {
        rewriteRun(java(
                """
                import org.bouncycastle.asn1.sec.ECPrivateKey;

                class EcParameters {
                    Object parameters(ECPrivateKey key) {
                        return key.getParameters();
                    }
                }
                """,
                """
                import org.bouncycastle.asn1.sec.ECPrivateKey;

                class EcParameters {
                    Object parameters(ECPrivateKey key) {
                        return key.getParametersObject().toASN1Primitive();
                    }
                }
                """));
    }

    @Test
    void leavesKyberAndRemovedLegacyPackagesForManualMigration() {
        rewriteRun(java(
                """
                import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters;
                import org.bouncycastle.pqc.legacy.crypto.gmss.GMSSParameters;

                class UnsafeToGuess {
                    Object kyber = KyberParameters.kyber768;
                    Object gmss = new GMSSParameters(4);
                }
                """));
    }

    @Test
    void requiresTypeAttributionAndLeavesLookalikesUntouched() {
        rewriteRun(java(
                """
                class Pack {
                    static void longToBigEndian(long value, byte[] output, int offset, int length) { }
                }

                class Lookalike {
                    void write(byte[] output) {
                        Pack.longToBigEndian(1L, output, 0, 4);
                    }
                }
                """));
        rewriteRun(java("""
                class ECPrivateKey {
                    Object getParameters() { return null; }
                }
                class EcLookalike { Object read(ECPrivateKey key) { return key.getParameters(); } }
                """));
    }

    @Test
    void refusesUnattributedPackageImports() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion())
                        .typeValidationOptions(TypeValidation.none()),
                java("""
                        import org.bouncycastle.pqc.crypto.bike.NotARealBikeType;
                        class Lookalike { NotARealBikeType value; }
                        """));
    }

    @Test
    void refusesToRenameThirdPartyPackageDeclarations() {
        rewriteRun(spec -> spec.typeValidationOptions(TypeValidation.none()), java("""
                package org.bouncycastle.pqc.crypto.bike.application;
                class ApplicationOwnedSource { }
                """));
    }

    @Test
    void migratesAttributedFullyQualifiedTypes() {
        rewriteRun(java(
                """
                class Crypto {
                    org.bouncycastle.pqc.crypto.bike.BIKEParameters parameters =
                            org.bouncycastle.pqc.crypto.bike.BIKEParameters.bike128;
                }
                """,
                """
                class Crypto {
                    org.bouncycastle.pqc.legacy.bike.BIKEParameters parameters =
                            org.bouncycastle.pqc.legacy.bike.BIKEParameters.bike128;
                }
                """));
    }

    @Test
    void skipsGeneratedSourcesAndIsIdempotent() {
        rewriteRun(java(
                """
                import org.bouncycastle.util.Pack;
                class Generated { void write(byte[] out) { Pack.longToBigEndian(1L, out, 0, 4); } }
                """, source -> source.path("target/generated/Generated.java")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                import org.bouncycastle.util.Pack;
                class Encoding { void write(byte[] out) { Pack.longToBigEndian(1L, out, 0, 4); } }
                """,
                """
                import org.bouncycastle.util.Pack;
                class Encoding { void write(byte[] out) { Pack.longToBigEndian_Low(1L, out, 0, 4); } }
                """));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                import org.bouncycastle.asn1.sec.ECPrivateKey;
                class Encoding { Object parameters(ECPrivateKey key) { return key.getParameters(); } }
                """,
                """
                import org.bouncycastle.asn1.sec.ECPrivateKey;
                class Encoding { Object parameters(ECPrivateKey key) { return key.getParametersObject().toASN1Primitive(); } }
                """));
    }

    private static Recipe recipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE);
    }
}
