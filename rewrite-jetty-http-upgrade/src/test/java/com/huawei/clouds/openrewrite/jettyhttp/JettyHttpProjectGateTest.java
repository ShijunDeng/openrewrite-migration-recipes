package com.huawei.clouds.openrewrite.jettyhttp;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;

class JettyHttpProjectGateTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.jettyhttp.MigrateJettyHttpTo12_0_34";

    @Test
    void selectedMavenRootEnablesOfficialTypeMigration() {
        rewriteRun(spec -> selectedRecipe(spec),
                xml(JettyHttpTestSupport.pom("11.0.20"),
                        source -> source.path("pom.xml").after(actual -> actual)),
                oldContent("SelectedContent", "src/main/java/SelectedContent.java", true));
    }

    @Test
    void JavaWithoutAnyBuildOwnerIsUntouched() {
        rewriteRun(spec -> selectedRecipe(spec),
                oldContent("OwnerlessContent", "src/main/java/OwnerlessContent.java", false));
    }

    @Test
    void targetOutsideAndForbiddenBuildRootsCannotEnableSourceAuto() {
        rewriteRun(spec -> selectedRecipe(spec),
                xml(JettyHttpTestSupport.pom("12.0.34"),
                        source -> source.path("target-line/pom.xml")),
                oldContent("TargetContent",
                        "target-line/src/main/java/TargetContent.java", false),
                xml(JettyHttpTestSupport.pom("11.0.21"),
                        source -> source.path("outside/pom.xml")
                                .after(actual -> actual)),
                oldContent("OutsideContent",
                        "outside/src/main/java/OutsideContent.java", false),
                xml(JettyHttpTestSupport.pom("12.1.0"),
                        source -> source.path("forbidden/pom.xml")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("12.1.0"), printed);
                                    assertTrue(printed.contains(
                                            JettyHttpSupport.TARGET_CONFLICT), printed);
                                    assertFalse(printed.contains(
                                            "<version>12.0.34</version>"), printed);
                                })),
                oldContent("ForbiddenContent",
                        "forbidden/src/main/java/ForbiddenContent.java", false));
    }

    @Test
    void nearestNestedMavenBuildBoundaryBlocksTheOuterSelection() {
        rewriteRun(spec -> selectedRecipe(spec),
                xml(JettyHttpTestSupport.pom("11.0.20"),
                        source -> source.path("pom.xml").after(actual -> actual)),
                oldContent("RootContent", "src/main/java/RootContent.java", true),
                xml(JettyHttpTestSupport.pom("12.0.34"),
                        source -> source.path("nested/pom.xml")),
                oldContent("NestedTargetContent",
                        "nested/src/main/java/NestedTargetContent.java", false));
    }

    @Test
    void nestedSelectedMavenBuildDoesNotLeakIntoItsOuterProject() {
        rewriteRun(spec -> selectedRecipe(spec),
                xml(JettyHttpTestSupport.pom("12.0.34"),
                        source -> source.path("pom.xml")),
                oldContent("OuterTargetContent",
                        "src/main/java/OuterTargetContent.java", false),
                xml(JettyHttpTestSupport.pom("9.4.58.v20250814"),
                        source -> source.path("nested/pom.xml").after(actual -> actual)),
                oldContent("NestedSelectedContent",
                        "nested/src/main/java/NestedSelectedContent.java", true));
    }

    @Test
    void mixedSelectedAndHigherDeclarationsBlockProjectSourceAuto() {
        String pom = JettyHttpTestSupport.project("<dependencies>" +
                JettyHttpTestSupport.dependency("11.0.20", "") +
                JettyHttpTestSupport.dependency("12.1.0", "") +
                "</dependencies>");
        rewriteRun(spec -> selectedRecipe(spec),
                xml(pom, source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>12.0.34</version>"), printed);
                            assertTrue(printed.contains("<version>12.1.0</version>"), printed);
                            assertTrue(printed.contains(JettyHttpSupport.TARGET_CONFLICT), printed);
                        })),
                oldContent("MixedContent", "src/main/java/MixedContent.java", false));
    }

    @Test
    void exclusiveMavenPropertyOwnerEnablesButSharedOwnerBlocksProjectAuto() {
        String exclusive = JettyHttpTestSupport.project(
                "<properties><jetty.version>11.0.20</jetty.version></properties>" +
                "<dependencies>" + JettyHttpTestSupport.dependency("${jetty.version}", "") +
                "</dependencies>");
        String shared = JettyHttpTestSupport.project(
                "<properties><jetty.version>11.0.20</jetty.version></properties>" +
                "<dependencies>" + JettyHttpTestSupport.dependency("${jetty.version}", "") +
                "<dependency><groupId>example</groupId><artifactId>other</artifactId>" +
                "<version>${jetty.version}</version></dependency></dependencies>");
        rewriteRun(spec -> selectedRecipe(spec),
                xml(exclusive, source -> source.path("exclusive/pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(
                                        "<jetty.version>12.0.34</jetty.version>"),
                                        after.printAll()))),
                oldContent("ExclusiveContent",
                        "exclusive/src/main/java/ExclusiveContent.java", true),
                xml(shared, source -> source.path("shared/pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(
                                    FindJettyHttpBuildRisks.OWNER), printed);
                            assertTrue(printed.contains(
                                    "<jetty.version>11.0.20</jetty.version>"), printed);
                            assertFalse(printed.contains(
                                    "<jetty.version>12.0.34</jetty.version>"), printed);
                        })),
                oldContent("SharedContent",
                        "shared/src/main/java/SharedContent.java", false));
    }

    @Test
    void selectedGradleRootEnablesButNestedTargetBuildBlocksSourceAuto() {
        rewriteRun(spec -> selectedRecipe(spec),
                buildGradle(
                        "dependencies { implementation 'org.eclipse.jetty:jetty-http:12.0.25' }",
                        source -> source.path("build.gradle").after(actual -> actual)),
                oldContent("GradleRootContent",
                        "src/main/java/GradleRootContent.java", true),
                buildGradle(
                        "dependencies { implementation 'org.eclipse.jetty:jetty-http:12.0.34' }",
                        source -> source.path("nested/build.gradle")),
                oldContent("GradleNestedContent",
                        "nested/src/main/java/GradleNestedContent.java", false));
    }

    @Test
    void sourceAndConfigurationRiskMarkersStayInsideSelectedBuildRoot() {
        rewriteRun(spec -> selectedRecipe(spec),
                xml(JettyHttpTestSupport.pom("11.0.20"),
                        source -> source.path("selected/pom.xml").after(actual -> actual)),
                properties("jetty.httpConfig.requestHeaderSize=8192",
                        source -> source.path("selected/src/main/resources/jetty.properties")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertTrue(after.printAll().contains(
                                                FindJettyHttpConfigurationRisks.LIMITS),
                                                after.printAll()))),
                xml(JettyHttpTestSupport.pom("12.0.34"),
                        source -> source.path("unrelated/pom.xml")),
                properties("jetty.httpConfig.requestHeaderSize=8192",
                        source -> source.path("unrelated/src/main/resources/jetty.properties")
                                .afterRecipe(after ->
                                        assertFalse(after.printAll().contains(
                                                FindJettyHttpConfigurationRisks.LIMITS),
                                        after.printAll()))));
    }

    @Test
    void officialBufferRenameAlsoStaysInsideSelectedBuildRoot() {
        rewriteRun(spec -> selectedRecipe(spec),
                xml(JettyHttpTestSupport.pom("11.0.20"),
                        source -> source.path("selected/pom.xml").after(actual -> actual)),
                legacyBuffer("SelectedBuffer",
                        "selected/src/main/java/SelectedBuffer.java", true),
                xml(JettyHttpTestSupport.pom("12.0.34"),
                        source -> source.path("unrelated/pom.xml")),
                legacyBuffer("UnrelatedBuffer",
                        "unrelated/src/main/java/UnrelatedBuffer.java", false));
    }

    private static void selectedRecipe(org.openrewrite.test.RecipeSpec spec) {
        spec.recipe(JettyHttpTestSupport.recipe(RECIPE))
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        JettyHttpLegacyApi.sources()));
    }

    private static org.openrewrite.test.SourceSpecs oldContent(
            String className, String path, boolean migrated) {
        return java("""
                import org.eclipse.jetty.http.HttpContent;
                class %s { HttpContent content; }
                """.formatted(className), source -> {
            source.path(path).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(migrated
                            ? "org.eclipse.jetty.http.content.HttpContent"
                            : "org.eclipse.jetty.http.HttpContent"), printed);
                    if (migrated) {
                        assertTrue(printed.contains(FindJettyHttpSourceRisks.CONTENT), printed);
                    } else {
                        assertFalse(printed.contains(FindJettyHttpSourceRisks.CONTENT), printed);
                        assertFalse(printed.contains(
                                "org.eclipse.jetty.http.content.HttpContent"), printed);
                    }
                });
            if (migrated) source.after(actual -> actual);
        });
    }

    private static org.openrewrite.test.SourceSpecs legacyBuffer(
            String className, String path, boolean migrated) {
        return java("""
                import org.eclipse.jetty.http.HttpContent;
                class %s {
                    java.nio.ByteBuffer read(HttpContent content) {
                        return content.getDirectBuffer();
                    }
                }
                """.formatted(className), source -> {
            source.path(path).afterRecipe(after -> {
                String printed = after.printAll();
                assertTrue(printed.contains(migrated
                        ? "getByteBuffer()"
                        : "getDirectBuffer()"), printed);
                assertFalse(printed.contains(migrated
                        ? "getDirectBuffer()"
                        : "getByteBuffer()"), printed);
            });
            if (migrated) source.after(actual -> actual);
        });
    }
}
