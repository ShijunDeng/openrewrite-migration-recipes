package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindBcProv184SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindBcProv184SourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath("bcprov-jdk18on"))
                .typeValidationOptions(TypeValidation.none());
    }

    @ParameterizedTest(name = "removed 1.82 legacy package {0}")
    @ValueSource(strings = {
            "org.bouncycastle.pqc.legacy.crypto.gmss.GMSSParameters",
            "org.bouncycastle.pqc.legacy.crypto.mceliece.McElieceParameters",
            "org.bouncycastle.pqc.legacy.crypto.qtesla.QTESLASigner",
            "org.bouncycastle.pqc.legacy.math.linearalgebra.GF2Matrix",
            "org.bouncycastle.pqc.legacy.math.ntru.polynomial.IntegerPolynomial"
    })
    void marksLegacyPqcPackageRemovedIn182(String type) {
        marked("import " + type + ";\nclass Use { " + simple(type) + " value; }",
                FindBcProv184SourceRisks.LEGACY_PQC_REMOVED);
    }

    @ParameterizedTest(name = "removed/promoted PQC type {0}")
    @ValueSource(strings = {
            "org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters",
            "org.bouncycastle.pqc.crypto.gemss.GeMSSParameters",
            "org.bouncycastle.pqc.jcajce.spec.McElieceKeyGenParameterSpec",
            "org.bouncycastle.pqc.jcajce.spec.QTESLAParameterSpec",
            "org.bouncycastle.pqc.asn1.KyberPrivateKey"
    })
    void marksRemovedOrPromotedPqcApis(String type) {
        marked("import " + type + ";\nclass Use { " + simple(type) + " value; }",
                FindBcProv184SourceRisks.PQC_REMOVED);
    }

    @ParameterizedTest(name = "ASN.1 moved to bcutil {0}")
    @ValueSource(strings = {
            "org.bouncycastle.asn1.iana.IANAObjectIdentifiers",
            "org.bouncycastle.asn1.misc.ScryptParams",
            "org.bouncycastle.asn1.mozilla.PublicKeyAndChallenge",
            "org.bouncycastle.asn1.oiw.OIWObjectIdentifiers",
            "org.bouncycastle.asn1.edec.EdECObjectIdentifiers"
    })
    void marksTypesMovedFromBcprovToBcutil(String type) {
        marked("import " + type + ";\nclass Use { " + simple(type) + " value; }",
                FindBcProv184SourceRisks.BCUTIL_SPLIT);
    }

    @ParameterizedTest(name = "1.84 legacy location {0}")
    @ValueSource(strings = {"bike.BIKEParameters", "picnic.PicnicParameters",
            "rainbow.RainbowParameters"})
    void marksAlgorithmsMovedTo184LegacyLocation(String suffix) {
        String type = "org.bouncycastle.pqc.legacy." + suffix;
        marked("import " + type + ";\nclass Use { " + simple(type) + " value; }",
                FindBcProv184SourceRisks.LEGACY_PQC_MOVED);
    }

    @ParameterizedTest(name = "SPHINCS+ version-sensitive location {0}")
    @ValueSource(strings = {"org.bouncycastle.pqc.crypto.sphincsplus.SPHINCSPlusParameters",
            "org.bouncycastle.pqc.legacy.sphincsplus.SPHINCSPlusParameters"})
    void marksSphincsPlusForExplicitParameterMapping(String type) {
        marked("import " + type + ";\nclass Use { " + simple(type) + " value; }",
                FindBcProv184SourceRisks.SPHINCS_SEMANTICS);
    }

    @Test
    void marksRemovedEcPrivateKeyConstructors() {
        marked("""
                import java.math.BigInteger;
                import org.bouncycastle.asn1.sec.ECPrivateKey;

                class EcEncoding {
                    Object encode(BigInteger d) {
                        ECPrivateKey key = new ECPrivateKey(d);
                        return key;
                    }
                }
                """, FindBcProv184SourceRisks.REMOVED_API);
    }

    @ParameterizedTest(name = "removed AEAD block size owner {0}")
    @ValueSource(strings = {"ElephantEngine", "ISAPEngine", "PhotonBeetleEngine", "XoodyakEngine"})
    void marksRemovedAeadGetBlockSize(String type) {
        marked("import org.bouncycastle.crypto.engines." + type + ";\n" +
               "class Aead { int size(" + type + " engine) { return engine.getBlockSize(); } }",
                FindBcProv184SourceRisks.REMOVED_API);
    }

    @Test
    void marksRemovedHqcSeedGeneratorAndLmsVisibility() {
        marked("""
                import org.bouncycastle.pqc.crypto.hqc.HQCKeyPairGenerator;
                class Hqc { Object generate(HQCKeyPairGenerator generator, byte[] seed) {
                    return generator.generateKeyPairWithSeed(seed);
                } }
                """, FindBcProv184SourceRisks.REMOVED_API);
        marked("""
                import org.bouncycastle.pqc.crypto.lms.HSSSignature;
                class Lms { HSSSignature signature; }
                """, FindBcProv184SourceRisks.REMOVED_API);
    }

    @Test
    void marksRemovedDilithiumAesParameterVariants() {
        marked("""
                import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumParameters;
                class Dilithium { Object parameters = DilithiumParameters.dilithium3_aes; }
                """, FindBcProv184SourceRisks.REMOVED_API);
    }

    @Test
    void marksGcmSpecReturnTypeChangeAndRemovedBrokenKdf() {
        marked("""
                import java.security.spec.AlgorithmParameterSpec;
                import org.bouncycastle.jcajce.provider.symmetric.util.GcmSpecUtil;
                import org.bouncycastle.jce.provider.BrokenKDF2BytesGenerator;

                class Removed {
                    Object gcm(AlgorithmParameterSpec spec) throws Exception {
                        return GcmSpecUtil.extractGcmParameters(spec);
                    }
                    BrokenKDF2BytesGenerator kdf;
                }
                """, FindBcProv184SourceRisks.REMOVED_API);
    }

    @Test
    void marks184StringsSplitBehavior() {
        marked("""
                import org.bouncycastle.util.Strings;
                class Parser { String[] split(String input) { return Strings.split(input, ':'); } }
                """, FindBcProv184SourceRisks.STRINGS);
    }

    @Test
    void marksWrapUtilKeyWrappingBehaviorAfterDeterministicMove() {
        marked("""
                import org.bouncycastle.pqc.jcajce.provider.util.WrapUtil;
                class Wrapping { Object wrapper() { return WrapUtil.getWrapper("AES"); } }
                """, FindBcProv184SourceRisks.WRAP_UTIL);
    }

    @Test
    void marksRealMembraneStyleProviderRegistration() {
        // Fixture shape: https://github.com/membrane/api-gateway/blob/324c5dde40f82226b514b9a7824a9b51c7a5c35f/core/src/main/java/com/predic8/membrane/core/transport/ssl/PEMSupport.java
        marked("""
                import java.security.Security;
                import org.bouncycastle.jce.provider.BouncyCastleProvider;

                class PEMSupport {
                    static { Security.addProvider(new BouncyCastleProvider()); }
                }
                """, FindBcProv184SourceRisks.PROVIDER);
    }

    @Test
    void marksProviderInsertionRemovalAndLookup() {
        marked("""
                import java.security.Security;
                import org.bouncycastle.jce.provider.BouncyCastleProvider;

                class Providers {
                    void configure() {
                        Security.insertProviderAt(new BouncyCastleProvider(), 1);
                        Security.getProvider("BC");
                        Security.removeProvider("BC");
                    }
                }
                """, FindBcProv184SourceRisks.PROVIDER);
    }

    @ParameterizedTest(name = "changed PQC JCA algorithm {0}")
    @ValueSource(strings = {"Kyber", "Dilithium", "SPHINCS+", "Rainbow", "McEliece", "BIKE", "HQC", "ML-KEM"})
    void marksChangedPqcProviderAlgorithms(String algorithm) {
        marked("""
                import java.security.KeyPairGenerator;
                class Algorithms {
                    Object create() throws Exception {
                        return KeyPairGenerator.getInstance("%s", "BCPQC");
                    }
                }
                """.formatted(algorithm), FindBcProv184SourceRisks.ALGORITHM);
    }

    @Test
    void marksBcPkcs12BoundaryButNotDefaultJdkPkcs12() {
        marked("""
                import java.security.KeyStore;
                class Stores { Object load() throws Exception { return KeyStore.getInstance("PKCS12", "BC"); } }
                """, FindBcProv184SourceRisks.PKCS12);
        clean("""
                import java.security.KeyStore;
                class Stores { Object load() throws Exception { return KeyStore.getInstance("PKCS12"); } }
                """);
    }

    @Test
    void marksAsn1InputOidAndDerBoundaries() {
        marked("""
                import java.io.InputStream;
                import org.bouncycastle.asn1.ASN1InputStream;
                import org.bouncycastle.asn1.ASN1Object;
                import org.bouncycastle.asn1.ASN1ObjectIdentifier;

                class Der {
                    ASN1InputStream input(InputStream in) { return new ASN1InputStream(in); }
                    ASN1ObjectIdentifier oid() { return new ASN1ObjectIdentifier("1.2.3"); }
                    byte[] encode(ASN1Object object) throws Exception { return object.getEncoded("DER"); }
                }
                """, FindBcProv184SourceRisks.ASN1);
    }

    @Test
    void marksPqcParameterEncodingButNotUnchangedLowLevelHkdf() {
        marked("""
                import org.bouncycastle.pqc.crypto.crystals.kyber.KyberPrivateKeyParameters;

                class Parameters {
                    byte[] persist(KyberPrivateKeyParameters key) { return key.getEncoded(); }
                }
                """, FindBcProv184SourceRisks.CRYPTO_PARAMETERS);
        clean("""
                import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
                import org.bouncycastle.crypto.digests.SHA256Digest;
                import org.bouncycastle.crypto.params.HKDFParameters;
                class Hkdf { void derive(byte[] ikm) {
                    HKDFBytesGenerator generator = new HKDFBytesGenerator(new SHA256Digest());
                    generator.init(new HKDFParameters(ikm, null, null));
                } }
                """);
    }

    @Test
    void marksDirectBouncyCastleJavaSerialization() {
        marked("""
                import java.io.ObjectOutputStream;
                import org.bouncycastle.jce.provider.BouncyCastleProvider;

                class PersistProvider {
                    void save(ObjectOutputStream out, BouncyCastleProvider provider) throws Exception {
                        out.writeObject(provider);
                    }
                }
                """, FindBcProv184SourceRisks.SERIALIZATION);
    }

    @Test
    void marksPinnedSonarCryptographyStringFixture() {
        // Fixture shape: https://github.com/cbomkit/sonar-cryptography/blob/e83dd0b39d33932325fcc71a7efb4e9f744d0bcd/java/src/main/java/com/ibm/plugin/rules/detection/bc/encapsulatedsecret/BcEncapsulatedSecretGenerator.java
        marked("""
                class Detector {
                    String bike = "org.bouncycastle.pqc.crypto.bike.";
                    String kyber = "org.bouncycastle.pqc.crypto.crystals.kyber.";
                }
                """, FindBcProv184SourceRisks.PQC_REMOVED);
        marked("class Detector { String bike = \"org.bouncycastle.pqc.crypto.bike.\"; }",
                FindBcProv184SourceRisks.LEGACY_PQC_MOVED);
    }

    @Test
    void unrelatedLookalikesAndOrdinaryAlgorithmsAreNoop() {
        clean("""
                import java.security.KeyPairGenerator;
                class BouncyCastleProvider { }
                class Safe {
                    void addProvider(BouncyCastleProvider provider) { }
                    Object rsa() throws Exception { return KeyPairGenerator.getInstance("RSA"); }
                }
                """);
    }

    @Test
    void skipsGeneratedSourcesAndMarkersAreIdempotent() {
        rewriteRun(java(
                "import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters; class Generated { KyberParameters p; }",
                source -> source.path("build/generated/Generated.java")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import org.bouncycastle.pqc.crypto.crystals.kyber.KyberParameters; class Use { KyberParameters p; }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindBcProv184SourceRisks.PQC_REMOVED, 1))));
    }

    private void marked(String source, String message) {
        rewriteRun(java(source, spec -> spec.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), message))));
    }

    private void clean(String source) {
        rewriteRun(java(source, spec -> spec.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    private static String simple(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
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
