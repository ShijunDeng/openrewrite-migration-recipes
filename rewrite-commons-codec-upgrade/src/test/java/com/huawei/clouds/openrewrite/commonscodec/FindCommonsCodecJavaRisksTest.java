package com.huawei.clouds.openrewrite.commonscodec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindCommonsCodecJavaRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindCommonsCodecJavaRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(CommonsCodecTestApi.sources()));
    }

    @ParameterizedTest(name = "source risk {0}")
    @MethodSource("risks")
    void marksPreciseRisk(String label, String source, String fragment) {
        rewriteRun(java(source, spec -> spec.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(fragment), after::printAll))));
    }

    static Stream<Arguments> risks() {
        return Stream.of(
                Arguments.of("Murmur hash32", "import org.apache.commons.codec.digest.MurmurHash3; class T { int x(byte[] b){return MurmurHash3.hash32(b);} }", "persisted-key migration"),
                Arguments.of("Murmur hash64", "import org.apache.commons.codec.digest.MurmurHash3; class T { long x(byte[] b){return MurmurHash3.hash64(b);} }", "sign-extension"),
                Arguments.of("Murmur hash128", "import org.apache.commons.codec.digest.MurmurHash3; class T { long[] x(byte[] b){return MurmurHash3.hash128(b);} }", "x86/x64 API"),
                Arguments.of("Murmur incremental", "import org.apache.commons.codec.digest.MurmurHash3; class T { Object x(){return new MurmurHash3.IncrementalHash32();} }", "hash-compatibility"),
                Arguments.of("Cologne", "import org.apache.commons.codec.language.ColognePhonetic; class T { String x(String s){return new ColognePhonetic().colognePhonetic(s);} }", "golden data"),
                Arguments.of("Metaphone", "import org.apache.commons.codec.language.Metaphone; class T { String x(String s){return new Metaphone().metaphone(s);} }", "changed codes"),
                Arguments.of("DoubleMetaphone", "import org.apache.commons.codec.language.DoubleMetaphone; class T { String x(String s){return new DoubleMetaphone().doubleMetaphone(s);} }", "match thresholds"),
                Arguments.of("Daitch Mokotoff", "import org.apache.commons.codec.language.DaitchMokotoffSoundex; class T { String x(String s){return new DaitchMokotoffSoundex().soundex(s);} }", "rebuild"),
                Arguments.of("Match rating", "import org.apache.commons.codec.language.MatchRatingApproachEncoder; class T { String x(String s){return new MatchRatingApproachEncoder().encode(s);} }", "phonetic output"),
                Arguments.of("Refined Soundex", "import org.apache.commons.codec.language.RefinedSoundex; class T { String x(String s){return new RefinedSoundex().encode(s);} }", "indexes"),
                Arguments.of("Base64 strict builder", "import org.apache.commons.codec.CodecPolicy; import org.apache.commons.codec.binary.Base64; class T { Object x(){return Base64.builder().setDecodingPolicy(CodecPolicy.STRICT).get();} }", "non-zero trailing bits"),
                Arguments.of("Base32 strict builder", "import org.apache.commons.codec.CodecPolicy; import org.apache.commons.codec.binary.Base32; class T { Object x(){return Base32.builder().setDecodingPolicy(CodecPolicy.STRICT).get();} }", "custom alphabets"),
                Arguments.of("Base64 strict constructor", "import org.apache.commons.codec.CodecPolicy; import org.apache.commons.codec.binary.Base64; class T { Object x(){return new Base64(0,new byte[]{'\\r','\\n'},false,CodecPolicy.STRICT);} }", "padding"),
                Arguments.of("Base16 decode", "import org.apache.commons.codec.binary.Base16; class T { byte[] x(byte[] b){return new Base16().decode(b);} }", "malformed input"),
                Arguments.of("Base64 mutable separator", "import org.apache.commons.codec.binary.Base64; class T { Object x(byte[] sep){return new Base64(76,sep);} }", "defensively copy"),
                Arguments.of("Base32 mutable separator", "import org.apache.commons.codec.binary.Base32; class T { Object x(byte[] sep){return new Base32(76,sep);} }", "array after construction"),
                Arguments.of("Md5Crypt prefix", "import org.apache.commons.codec.digest.Md5Crypt; class T { String x(byte[] b,String salt){return Md5Crypt.md5Crypt(b,salt);} }", "invalid prefixes"),
                Arguments.of("apr1 prefix", "import org.apache.commons.codec.digest.Md5Crypt; class T { String x(byte[] b,String salt){return Md5Crypt.apr1Crypt(b,salt);} }", "authentication/error policy"),
                Arguments.of("BCodec exception", "import org.apache.commons.codec.*; import org.apache.commons.codec.net.BCodec; class T { String x(String s,String c)throws EncoderException{return new BCodec().encode(s,c);} }", "UnsupportedCharsetException"),
                Arguments.of("QCodec exception", "import org.apache.commons.codec.*; import org.apache.commons.codec.net.QCodec; class T { String x(String s,String c)throws EncoderException{return new QCodec().encode(s,c);} }", "HTTP/mail error mapping"),
                Arguments.of("Hex encode buffer", "import java.nio.ByteBuffer; import org.apache.commons.codec.binary.Hex; class T { char[] x(ByteBuffer b){return Hex.encodeHex(b);} }", "position/remaining"),
                Arguments.of("Hex decode buffer", "import java.nio.ByteBuffer; import org.apache.commons.codec.binary.Hex; class T { byte[] x(ByteBuffer b){return Hex.decodeHex(b);} }", "read-only buffers"),
                Arguments.of("static charset import", "import static org.apache.commons.codec.Charsets.UTF_8; class T { Object c=UTF_8; }", "static import")
        );
    }

    @Test
    void correctedMurmurAndSameNamedBusinessApisAreNoop() {
        rewriteRun(
                java("import org.apache.commons.codec.digest.MurmurHash3; class T { int x(byte[] b){return MurmurHash3.hash32x86(b);} }"),
                java("class BusinessUse { static class BusinessPhonetic { String encode(String s){return s;} } String x(){return new BusinessPhonetic().encode(\"x\");} }"));
    }

    @Test
    void strictSubstringVariableIsNoop() {
        rewriteRun(java("import org.apache.commons.codec.CodecPolicy; import org.apache.commons.codec.binary.Base64; class T { Object x(){ CodecPolicy NOT_STRICT=CodecPolicy.LENIENT; return Base64.builder().setDecodingPolicy(NOT_STRICT).get(); } }"));
    }

    @Test
    void realOpenRefineFixtureMarksIndexOutput() {
        // Reduced from OpenRefine/OpenRefine@1d08ef931267d1a1881c2585656faf42bb926a96,
        // main/src/com/google/refine/clustering/binning/ColognePhoneticKeyer.java.
        rewriteRun(java("""
                import org.apache.commons.codec.language.ColognePhonetic;
                class ColognePhoneticKeyer {
                    private final ColognePhonetic codec = new ColognePhonetic();
                    String key(String value) { return codec.colognePhonetic(value); }
                }
                """, spec -> spec.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("rebuild golden data"), out);
                    assertFalse(out.contains("~~>new ColognePhonetic"), out);
                })));
    }

    @Test
    void generatedParentIsNoop() {
        rewriteRun(java("import org.apache.commons.codec.language.Metaphone; class T { String x(String s){return new Metaphone().metaphone(s);} }",
                source -> source.path("target/generated/T.java")));
    }
}
