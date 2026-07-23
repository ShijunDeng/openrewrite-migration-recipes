package com.huawei.clouds.openrewrite.commonscodec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.ReplaceConstantWithAnotherConstant;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class CommonsCodecOfficialCoreRecipesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(CommonsCodecTestApi.sources()));
    }

    @ParameterizedTest(name = "equivalent method {0}")
    @MethodSource("equivalentMethods")
    void renamesDocumentedDelegates(String label, String before, String after) {
        rewriteRun(spec -> spec.recipe(methodRecipe(label)), java(before, after));
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
                        "import static org.apache.commons.codec.digest.DigestUtils.sha1; class T { byte[] x(byte[] b){return sha1(b);} }"),
                Arguments.of("static Base64 import", "import static org.apache.commons.codec.binary.Base64.isArrayByteBase64; class T { boolean x(byte[] b){return isArrayByteBase64(b);} }",
                        "import static org.apache.commons.codec.binary.Base64.isBase64; class T { boolean x(byte[] b){return isBase64(b);} }")
        );
    }

    @ParameterizedTest(name = "standard charset {0}")
    @ValueSource(strings = {"ISO_8859_1", "US_ASCII", "UTF_16", "UTF_16BE", "UTF_16LE", "UTF_8"})
    void migratesQualifiedCharsetConstants(String field) {
        rewriteRun(spec -> spec.recipe(charsetRecipe(field)), java(
                "import org.apache.commons.codec.Charsets; class T { Object c = Charsets." + field + "; }",
                "import java.nio.charset.StandardCharsets;\n\nclass T { Object c = StandardCharsets." + field + "; }"));
    }

    @Test
    void migratesStaticCharsetImportWithTheOfficialCoreRecipe() {
        rewriteRun(spec -> spec.recipe(charsetRecipe("UTF_8")), java(
                "import static org.apache.commons.codec.Charsets.UTF_8; class T { Object c = UTF_8; }",
                "import static java.nio.charset.StandardCharsets.UTF_8;\n\nclass T { Object c = UTF_8; }"));
    }

    @Test
    void migratesFullyQualifiedCharsetAccessWithTheOfficialCoreRecipe() {
        rewriteRun(spec -> spec.recipe(charsetRecipe("UTF_8")), java(
                "class T { Object c = org.apache.commons.codec.Charsets.UTF_8; }",
                "import java.nio.charset.StandardCharsets;\n\nclass T { Object c = StandardCharsets.UTF_8; }"));
    }

    @Test
    void officialCoreMigratesQualifiedAndStaticConstantsTogether() {
        rewriteRun(spec -> spec.recipe(new CompositeRecipe(List.of(
                        charsetRecipe("US_ASCII"), charsetRecipe("UTF_8")))),
                java(
                """
                import org.apache.commons.codec.Charsets;
                import static org.apache.commons.codec.Charsets.UTF_8;

                class T { Object qualified=Charsets.US_ASCII; Object unqualified=UTF_8; }
                """,
                """
                import java.nio.charset.StandardCharsets;

                import static java.nio.charset.StandardCharsets.UTF_8;

                class T { Object qualified=StandardCharsets.US_ASCII; Object unqualified=UTF_8; }
                """));
    }

    @Test
    void realGobblinGuidFixtureUsesSha1Spelling() {
        // Reduced from apache/gobblin@fcfb06b41d041cb797622264cf5322296753fdea,
        // gobblin-utility/src/main/java/org/apache/gobblin/util/guid/Guid.java.
        rewriteRun(spec -> spec.recipe(methodRecipe("sha bytes")), java(
                "import org.apache.commons.codec.digest.DigestUtils; class Guid { static byte[] computeGuid(byte[] bytes){ return DigestUtils.sha(bytes); } }",
                "import org.apache.commons.codec.digest.DigestUtils; class Guid { static byte[] computeGuid(byte[] bytes){ return DigestUtils.sha1(bytes); } }"));
    }

    @Test
    void realCarbonDataFixtureUsesReplacementPredicate() {
        // Reduced from apache/carbondata@84268138b45abb3ea063d3b2f52bf93e598055e2,
        // processing/.../binary/Base64BinaryDecoder.java.
        rewriteRun(spec -> spec.recipe(methodRecipe("Base64 predicate")), java(
                "import org.apache.commons.codec.binary.Base64; class Decoder { boolean valid(byte[] parsed){ return Base64.isArrayByteBase64(parsed); } }",
                "import org.apache.commons.codec.binary.Base64; class Decoder { boolean valid(byte[] parsed){ return Base64.isBase64(parsed); } }"));
    }

    @Test
    void sameNamedBusinessMethodsAreNoop() {
        rewriteRun(spec -> spec.recipe(methodRecipe("sha bytes")),
                java("class DigestUtils { static byte[] sha(byte[] b){return b;} } class T { byte[] x(byte[] b){return DigestUtils.sha(b);} }"));
    }

    @Test
    void sameNamedBusinessConstantIsNoop() {
        rewriteRun(spec -> spec.recipe(charsetRecipe("UTF_8")),
                java("class Charsets { static final Object UTF_8 = null; } class T { Object c=Charsets.UTF_8; }"));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.recipe(methodRecipe("shaHex string"))
                .cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import org.apache.commons.codec.digest.DigestUtils; class T { String x(String s){return DigestUtils.shaHex(s);} }",
                "import org.apache.commons.codec.digest.DigestUtils; class T { String x(String s){return DigestUtils.sha1Hex(s);} }"));
    }

    private static Recipe methodRecipe(String label) {
        if ("digest factory".equals(label)) {
            return new ChangeMethodName(
                    "org.apache.commons.codec.digest.DigestUtils getShaDigest()",
                    "getSha1Digest", false, true);
        }
        if (label.contains("Base64")) {
            return new ChangeMethodName(
                    "org.apache.commons.codec.binary.Base64 isArrayByteBase64(byte[])",
                    "isBase64", false, true);
        }
        if (label.startsWith("shaHex")) {
            return new ChangeMethodName(
                    "org.apache.commons.codec.digest.DigestUtils shaHex(..)",
                    "sha1Hex", false, true);
        }
        return new ChangeMethodName(
                "org.apache.commons.codec.digest.DigestUtils sha(..)",
                "sha1", false, true);
    }

    private static Recipe charsetRecipe(String field) {
        return new ReplaceConstantWithAnotherConstant(
                "org.apache.commons.codec.Charsets." + field,
                "java.nio.charset.StandardCharsets." + field);
    }
}
