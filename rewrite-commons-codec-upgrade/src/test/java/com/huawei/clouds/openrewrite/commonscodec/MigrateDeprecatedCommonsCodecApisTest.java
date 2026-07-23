package com.huawei.clouds.openrewrite.commonscodec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class MigrateDeprecatedCommonsCodecApisTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDeprecatedCommonsCodecApis())
                .parser(JavaParser.fromJavaVersion().dependsOn(CommonsCodecTestApi.sources()));
    }

    @ParameterizedTest(name = "equivalent method {0}")
    @MethodSource("equivalentMethods")
    void renamesDocumentedDelegates(String label, String before, String after) {
        rewriteRun(java(before, after));
    }

    static Stream<Arguments> equivalentMethods() {
        return Stream.of(
                Arguments.of("digest factory", "import org.apache.commons.codec.digest.DigestUtils; class T { Object x(){return DigestUtils.getShaDigest();} }",
                        "import org.apache.commons.codec.digest.DigestUtils; class T { Object x(){return DigestUtils.getSha1Digest();} }"),
                Arguments.of("sha bytes", "import org.apache.commons.codec.digest.DigestUtils; class T { byte[] x(byte[] b){return DigestUtils.sha(b);} }",
                        "import org.apache.commons.codec.digest.DigestUtils; class T { byte[] x(byte[] b){return DigestUtils.sha1(b);} }"),
                Arguments.of("sha string", "import org.apache.commons.codec.digest.DigestUtils; class T { byte[] x(String s){return DigestUtils.sha(s);} }",
                        "import org.apache.commons.codec.digest.DigestUtils; class T { byte[] x(String s){return DigestUtils.sha1(s);} }"),
                Arguments.of("sha stream", "import java.io.*; import org.apache.commons.codec.digest.DigestUtils; class T { byte[] x(InputStream in)throws IOException{return DigestUtils.sha(in);} }",
                        "import java.io.*; import org.apache.commons.codec.digest.DigestUtils; class T { byte[] x(InputStream in)throws IOException{return DigestUtils.sha1(in);} }"),
                Arguments.of("shaHex bytes", "import org.apache.commons.codec.digest.DigestUtils; class T { String x(byte[] b){return DigestUtils.shaHex(b);} }",
                        "import org.apache.commons.codec.digest.DigestUtils; class T { String x(byte[] b){return DigestUtils.sha1Hex(b);} }"),
                Arguments.of("shaHex string", "import org.apache.commons.codec.digest.DigestUtils; class T { String x(String s){return DigestUtils.shaHex(s);} }",
                        "import org.apache.commons.codec.digest.DigestUtils; class T { String x(String s){return DigestUtils.sha1Hex(s);} }"),
                Arguments.of("shaHex stream", "import java.io.*; import org.apache.commons.codec.digest.DigestUtils; class T { String x(InputStream in)throws IOException{return DigestUtils.shaHex(in);} }",
                        "import java.io.*; import org.apache.commons.codec.digest.DigestUtils; class T { String x(InputStream in)throws IOException{return DigestUtils.sha1Hex(in);} }"),
                Arguments.of("Base64 predicate", "import org.apache.commons.codec.binary.Base64; class T { boolean x(byte[] b){return Base64.isArrayByteBase64(b);} }",
                        "import org.apache.commons.codec.binary.Base64; class T { boolean x(byte[] b){return Base64.isBase64(b);} }"),
                Arguments.of("static sha import", "import static org.apache.commons.codec.digest.DigestUtils.sha; class T { byte[] x(byte[] b){return sha(b);} }",
                        "import static org.apache.commons.codec.digest.DigestUtils.sha1;\n\nclass T { byte[] x(byte[] b){return sha1(b);} }"),
                Arguments.of("static Base64 import", "import static org.apache.commons.codec.binary.Base64.isArrayByteBase64; class T { boolean x(byte[] b){return isArrayByteBase64(b);} }",
                        "import static org.apache.commons.codec.binary.Base64.isBase64;\n\nclass T { boolean x(byte[] b){return isBase64(b);} }")
        );
    }

    @ParameterizedTest(name = "standard charset {0}")
    @ValueSource(strings = {"ISO_8859_1", "US_ASCII", "UTF_16", "UTF_16BE", "UTF_16LE", "UTF_8"})
    void migratesQualifiedCharsetConstants(String field) {
        rewriteRun(java(
                "import org.apache.commons.codec.Charsets; class T { Object c = Charsets." + field + "; }",
                "import java.nio.charset.StandardCharsets;\n\nclass T { Object c = StandardCharsets." + field + "; }"));
    }

    @Test
    void realGobblinGuidFixtureUsesSha1Spelling() {
        // Reduced from apache/gobblin@fcfb06b41d041cb797622264cf5322296753fdea,
        // gobblin-utility/src/main/java/org/apache/gobblin/util/guid/Guid.java.
        rewriteRun(java(
                "import org.apache.commons.codec.digest.DigestUtils; class Guid { static byte[] computeGuid(byte[] bytes){ return DigestUtils.sha(bytes); } }",
                "import org.apache.commons.codec.digest.DigestUtils; class Guid { static byte[] computeGuid(byte[] bytes){ return DigestUtils.sha1(bytes); } }"));
    }

    @Test
    void realCarbonDataFixtureUsesReplacementPredicate() {
        // Reduced from apache/carbondata@84268138b45abb3ea063d3b2f52bf93e598055e2,
        // processing/.../binary/Base64BinaryDecoder.java.
        rewriteRun(java(
                "import org.apache.commons.codec.binary.Base64; class Decoder { boolean valid(byte[] parsed){ return Base64.isArrayByteBase64(parsed); } }",
                "import org.apache.commons.codec.binary.Base64; class Decoder { boolean valid(byte[] parsed){ return Base64.isBase64(parsed); } }"));
    }

    @Test
    void sameNamedBusinessMethodsAreNoop() {
        rewriteRun(java("class DigestUtils { static byte[] sha(byte[] b){return b;} } class T { byte[] x(byte[] b){return DigestUtils.sha(b);} }"));
    }

    @Test
    void generatedParentIsNoop() {
        rewriteRun(java("import org.apache.commons.codec.digest.DigestUtils; class T { byte[] x(byte[] b){return DigestUtils.sha(b);} }",
                source -> source.path("generated-code/T.java")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import org.apache.commons.codec.digest.DigestUtils; class T { String x(String s){return DigestUtils.shaHex(s);} }",
                "import org.apache.commons.codec.digest.DigestUtils; class T { String x(String s){return DigestUtils.sha1Hex(s);} }"));
    }
}
