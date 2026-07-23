package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class FindTomcatCatalinaResourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindTomcatCatalinaResourceRisks());
    }

    @Test
    void marksJavaxServiceContractPath() {
        rewriteRun(text("example.LegacyInitializer\n", source -> source
                .path("src/main/resources/META-INF/services/javax.servlet.ServletContainerInitializer")
                .after(actual -> actual).afterRecipe(after -> assertTrue(
                        after.printAll().contains("rename the META-INF/services contract"), after::printAll))));
    }

    @Test
    void marksConfigurationValuesAndManifestMetadata() {
        rewriteRun(
                text("initializer=javax.servlet.ServletContainerInitializer\n", source -> source.path("application.properties")
                        .after(actual -> actual).afterRecipe(after -> assertTrue(
                                after.printAll().contains("Configuration still names"), after::printAll))),
                text("Import-Package: javax.servlet.http\n", source -> source.path("META-INF/MANIFEST.MF")
                        .after(actual -> actual).afterRecipe(after -> assertTrue(
                                after.printAll().contains("Configuration still names"), after::printAll)))
        );
    }

    @Test
    void sourceCodeDocsLookalikesAndGeneratedResourcesAreUnmarked() {
        rewriteRun(
                text("javax.servlet.http.HttpSession", source -> source.path("README.md")),
                text("type=example.javax.servletish.Session", source -> source.path("application.properties")),
                text("type=javax.servlet.http.HttpSession", source -> source.path("target/application.properties"))
        );
    }
}
