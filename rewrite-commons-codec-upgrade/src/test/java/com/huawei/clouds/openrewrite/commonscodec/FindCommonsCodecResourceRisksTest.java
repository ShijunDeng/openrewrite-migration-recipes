package com.huawei.clouds.openrewrite.commonscodec;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class FindCommonsCodecResourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindCommonsCodecResourceRisks());
    }

    @Test
    void marksBndAndManifestMetadata() {
        rewriteRun(
                text("Import-Package: org.apache.commons.codec.binary\n", source -> source.path("bnd.bnd")
                        .after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("restored OSGi")))),
                text("Import-Package: org.apache.commons.codec.digest\n", source -> source.path("src/main/resources/META-INF/MANIFEST.MF")
                        .after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("complete bundle graph")))));
    }

    @Test
    void marksMavenBundleInstructionsOnly() {
        rewriteRun(xml("""
                <project><modelVersion>4.0.0</modelVersion><build><plugins><plugin>
                  <artifactId>maven-bundle-plugin</artifactId><configuration><instructions>
                    <Import-Package>org.apache.commons.codec.*</Import-Package>
                  </instructions></configuration>
                </plugin></plugins></build></project>
                """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains("regenerate and resolve"), after::printAll))));
    }

    @Test
    void ordinaryTextXmlAndGeneratedMetadataAreNoop() {
        rewriteRun(
                text("class=org.apache.commons.codec.binary.Base64\n", source -> source.path("application.properties")),
                xml("<config><class>org.apache.commons.codec.binary.Base64</class></config>", source -> source.path("config.xml")),
                text("Import-Package: org.apache.commons.codec.binary\n", source -> source.path("build/META-INF/MANIFEST.MF")
                        .afterRecipe(after -> assertFalse(after.printAll().contains("bundle graph")))));
    }

    @Test
    void similarlyNamedXmlOutsideBundlePluginIsNoop() {
        rewriteRun(
                xml("<project><modelVersion>4.0.0</modelVersion><company><instructions>org.apache.commons.codec.*</instructions></company></project>", source -> source.path("pom.xml")),
                xml("<project><modelVersion>4.0.0</modelVersion><build><plugins><plugin><artifactId>unrelated-plugin</artifactId><configuration><instructions>org.apache.commons.codec.*</instructions></configuration></plugin></plugins></build></project>", source -> source.path("plugin/pom.xml")));
    }
}
