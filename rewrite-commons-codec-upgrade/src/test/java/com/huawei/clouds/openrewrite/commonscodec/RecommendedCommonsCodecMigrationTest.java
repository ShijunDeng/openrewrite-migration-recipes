package com.huawei.clouds.openrewrite.commonscodec;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedCommonsCodecMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath(
                                "com.huawei.clouds.openrewrite.commonscodec",
                                "org.openrewrite.apache.commons.codec",
                                "org.openrewrite.java.dependencies").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.commonscodec.MigrateCommonsCodecTo1_22_0"))
                .parser(JavaParser.fromJavaVersion().dependsOn(CommonsCodecTestApi.sources()));
    }

    @Test
    void officialModelAwareUpgradeRunsBeforeMigratingDigestApi() {
        rewriteRun(
                pomXml(pom("1.11"), pom("1.22.0")),
                java("import org.apache.commons.codec.digest.DigestUtils; class Guid { byte[] id(byte[] b){return DigestUtils.sha(b);} }",
                        "import org.apache.commons.codec.digest.DigestUtils; class Guid { byte[] id(byte[] b){return DigestUtils.sha1(b);} }"));
    }

    @Test
    void migratesPredicateAndCharsetInOneCycle() {
        rewriteRun(
                xml(pom("1.13"), pom("1.22.0"),
                        source -> source.path("pom.xml")),
                java(
                """
                import org.apache.commons.codec.Charsets;
                import org.apache.commons.codec.binary.Base64;

                class T { Object c=Charsets.UTF_8; boolean x(byte[] b){return Base64.isArrayByteBase64(b);} }
                """,
                """
                import org.apache.commons.codec.binary.Base64;

                import java.nio.charset.StandardCharsets;

                class T { Object c=StandardCharsets.UTF_8; boolean x(byte[] b){return Base64.isBase64(b);} }
                """));
    }

    @Test
    void keepsMurmurCorrectionAsExplicitDecision() {
        rewriteRun(
                xml(pom("1.14"), pom("1.22.0"),
                        source -> source.path("pom.xml")),
                java("import org.apache.commons.codec.digest.MurmurHash3; class T { int x(byte[] b){return MurmurHash3.hash32(b);} }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("persisted-key migration"), after::printAll))));
    }

    @Test
    void realOpenRefineShapeIsMarkedNotRewritten() {
        rewriteRun(
                xml(pom("1.15"), pom("1.22.0"),
                        source -> source.path("pom.xml")),
                java("import org.apache.commons.codec.language.ColognePhonetic; class Keyer { String key(String s){return new ColognePhonetic().colognePhonetic(s);} }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("rebuild golden data"), after::printAll))));
    }

    @Test
    void bundleMetadataRemainsVisibleAfterDependencyUpgrade() {
        rewriteRun(
                xml(pom("1.15"), pom("1.22.0"), source -> source.path("pom.xml")),
                text("Import-Package: org.apache.commons.codec.binary\n", source -> source.path("bnd.bnd")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("complete bundle graph"), after::printAll))));
    }

    @Test
    void pureAutoMigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("1.16.0"), pom("1.22.0"),
                        source -> source.path("pom.xml")),
                java(
                        "import org.apache.commons.codec.digest.DigestUtils; class T { String x(String s){return DigestUtils.shaHex(s);} }",
                        "import org.apache.commons.codec.digest.DigestUtils; class T { String x(String s){return DigestUtils.sha1Hex(s);} }"));
    }

    private static String pom(String version) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" +
               "<dependencies><dependency><groupId>commons-codec</groupId><artifactId>commons-codec</artifactId><version>" +
               version + "</version></dependency></dependencies></project>";
    }
}
