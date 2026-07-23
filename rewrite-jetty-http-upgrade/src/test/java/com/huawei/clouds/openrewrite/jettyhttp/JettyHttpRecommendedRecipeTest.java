package com.huawei.clouds.openrewrite.jettyhttp;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class JettyHttpRecommendedRecipeTest implements RewriteTest {
    private static final String BASELINE =
            "com.huawei.clouds.openrewrite.jettyhttp.UpgradeJettyHttpBuildToJava17";
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.jettyhttp.MigrateJettyHttpTo12_0_34";

    @Test
    void recommendedRecipeHasTheAuditedSafetyOrder() {
        Recipe recipe = JettyHttpTestSupport.recipe(RECIPE);
        assertEquals(List.of(
                MarkSelectedJettyHttpProjects.class.getName(),
                "com.huawei.clouds.openrewrite.jettyhttp.FindJettyHttp12_0_34BuildRisks",
                BASELINE,
                "com.huawei.clouds.openrewrite.jettyhttp.UpgradeJettyHttpTo12_0_34",
                "com.huawei.clouds.openrewrite.jettyhttp.MigrateSelectedJettyHttp12ContentBufferAccess",
                "com.huawei.clouds.openrewrite.jettyhttp.MigrateSelectedJettyHttp12TypeRelocations",
                "com.huawei.clouds.openrewrite.jettyhttp.FindSelectedJettyHttp12SourceRisks",
                "com.huawei.clouds.openrewrite.jettyhttp.FindSelectedJettyHttp12ConfigurationRisks"),
                recipe.getRecipeList().stream().map(Recipe::getName).toList());
    }

    @Test
    void officialJava17RecipeActivatesForExactMavenSource() {
        String source = JettyHttpTestSupport.project(
                "<properties><maven.compiler.source>11</maven.compiler.source>" +
                "<maven.compiler.target>11</maven.compiler.target></properties><dependencies>" +
                JettyHttpTestSupport.dependency("11.0.20", "") + "</dependencies>");
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(BASELINE)),
                pomXml(source, pom -> pom.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(">17<"), printed);
                    assertFalse(printed.contains("<maven.compiler.source>11</maven.compiler.source>"), printed);
                    assertFalse(printed.contains("<maven.compiler.target>11</maven.compiler.target>"), printed);
                    assertTrue(printed.contains("<version>11.0.20</version>"), printed);
                })));
    }

    @Test
    void officialJava17RecipeActivatesForExactGradleSource() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(BASELINE)),
                buildGradle("""
                        sourceCompatibility = '11'
                        targetCompatibility = '11'
                        dependencies {
                          implementation 'org.eclipse.jetty:jetty-http:12.0.25'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("VERSION_17") || printed.contains("'17'") ||
                               printed.contains("\"17\""), printed);
                    assertFalse(printed.contains("'11'"), printed);
                    assertTrue(printed.contains("jetty-http:12.0.25"), printed);
                })));
    }

    @Test
    void baselineDoesNotActivateForTargetOutsideOrForbiddenVersions() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(BASELINE)),
                pomXml(JettyHttpTestSupport.project(
                        "<properties><maven.compiler.release>8</maven.compiler.release></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("12.0.34", "") + "</dependencies>"),
                        source -> source.path("target/pom.xml")),
                xml(JettyHttpTestSupport.project(
                        "<properties><java.version>8</java.version></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("12.1.0", "") + "</dependencies>"),
                        source -> source.path("conflict/pom.xml")),
                xml(JettyHttpTestSupport.project(
                        "<properties><java.version>8</java.version></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("11.0.21", "") + "</dependencies>"),
                        source -> source.path("outside/pom.xml")));
    }

    @Test
    void baselineDoesNotActivateForSharedPropertyOrMixedJettyOwners() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(BASELINE)),
                xml(JettyHttpTestSupport.project(
                        "<properties><java.version>8</java.version><v>11.0.20</v></properties>" +
                        "<dependencies>" + JettyHttpTestSupport.dependency("${v}", "") +
                        "<dependency><groupId>example</groupId><artifactId>other</artifactId>" +
                        "<version>${v}</version></dependency></dependencies>"),
                        source -> source.path("shared/pom.xml")),
                xml(JettyHttpTestSupport.project(
                        "<properties><java.version>8</java.version></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("11.0.20", "") +
                        JettyHttpTestSupport.dependency("12.1.0", "") +
                        "</dependencies>"), source -> source.path("mixed/pom.xml")));
    }

    @Test
    void fullRecipeUpgradesJavaAndDependencyInOneRun() {
        String source = JettyHttpTestSupport.project(
                "<properties><maven.compiler.release>11</maven.compiler.release></properties><dependencies>" +
                JettyHttpTestSupport.dependency("9.4.58.v20250814", "") + "</dependencies>");
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RECIPE)),
                pomXml(source, pom -> pom.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>12.0.34</version>"), printed);
                    assertTrue(printed.contains(">17<"), printed);
                    assertFalse(printed.contains(FindJettyHttpBuildRisks.OUTSIDE), printed);
                    assertFalse(printed.contains(JettyHttpSupport.TARGET_CONFLICT), printed);
                })));
    }

    @Test
    void fullRecipeNeverDowngradesJetty121AndAddsTheExactMarker() {
        String source = JettyHttpTestSupport.project(
                "<properties><maven.compiler.release>21</maven.compiler.release></properties><dependencies>" +
                JettyHttpTestSupport.dependency("12.1.0", "") + "</dependencies>");
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RECIPE)),
                xml(source, pom -> pom.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>12.1.0</version>"), printed);
                    assertTrue(printed.contains(JettyHttpSupport.TARGET_CONFLICT), printed);
                    assertFalse(printed.contains("<version>12.0.34</version>"), printed);
                    assertTrue(printed.contains("<maven.compiler.release>21</maven.compiler.release>"), printed);
                })));
    }

    @Test
    void fullRecipeLeavesUnapprovedLowerVersionAndExplainsTheGap() {
        String source = JettyHttpTestSupport.project("<dependencies>" +
                JettyHttpTestSupport.dependency("11.0.21", "") + "</dependencies>");
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RECIPE)),
                xml(source, pom -> pom.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>11.0.21</version>"), printed);
                    assertTrue(printed.contains(FindJettyHttpBuildRisks.OUTSIDE), printed);
                    assertFalse(printed.contains("<version>12.0.34</version>"), printed);
                })));
    }

    @Test
    void fullRecipeRelocatesContentTypesAndKeepsChangedApiMarkers() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RECIPE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(JettyHttpLegacyApi.sources())),
                xml(JettyHttpTestSupport.pom("11.0.20"),
                        source -> source.path("pom.xml").after(actual -> actual)),
                java("""
                        import org.eclipse.jetty.http.ResourceHttpContent;
                        class ResourceUse {
                            ResourceHttpContent create(Object resource) {
                                return new ResourceHttpContent(resource, "text/plain", 4096);
                            }
                        }
                        """, source -> source.path("src/main/java/ResourceUse.java")
                                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(
                            "org.eclipse.jetty.http.content.ResourceHttpContent"), printed);
                    assertTrue(printed.contains(FindJettyHttpSourceRisks.CONTENT), printed);
                    assertTrue(printed.contains("4096"), printed);
                })));
    }

    @Test
    void fullCompositionIsIdempotent() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RECIPE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(JettyHttpLegacyApi.sources()))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                xml(JettyHttpTestSupport.pom("12.0.25"), JettyHttpTestSupport.pom("12.0.34"),
                        source -> source.path("pom.xml")),
                java("""
                        import org.eclipse.jetty.http.HttpContent;
                        class ContentUse { HttpContent content; }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertEquals(1, JettyHttpTestSupport.occurrences(
                            after.printAll(), "org.eclipse.jetty.http.content.HttpContent"));
                    assertEquals(1, JettyHttpTestSupport.occurrences(
                            after.printAll(), FindJettyHttpSourceRisks.CONTENT));
                })));
    }

    @Test
    void generatedBuildAndJavaRemainUntouchedInFullComposition() {
        rewriteRun(spec -> spec.recipe(JettyHttpTestSupport.recipe(RECIPE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(JettyHttpLegacyApi.sources())),
                xml(JettyHttpTestSupport.project(
                        "<properties><maven.compiler.release>8</maven.compiler.release></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("11.0.20", "") + "</dependencies>"),
                        source -> source.path("target/generated/pom.xml")),
                java("""
                        import org.eclipse.jetty.http.HttpContent;
                        class Generated { HttpContent content; }
                        """, source -> source.path("build/generated/sources/Generated.java")));
    }
}
