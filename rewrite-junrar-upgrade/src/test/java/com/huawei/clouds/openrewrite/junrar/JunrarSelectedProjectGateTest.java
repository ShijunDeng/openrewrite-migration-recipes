package com.huawei.clouds.openrewrite.junrar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class JunrarSelectedProjectGateTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                        "com.huawei.clouds.openrewrite.junrar.MigrateJunrarTo7_5_10"))
                .parser(JavaParser.fromJavaVersion().classpath("junrar"));
    }

    @ParameterizedTest(name = "Maven source {0} selects its project")
    @ValueSource(strings = {"7.5.5", "7.5.8"})
    void exactMavenSourcesEnableOnlyTheirOwnProject(String version) {
        rewriteRun(
                xml(pom(version), pom("7.5.10"),
                        source -> source.path("selected/pom.xml")),
                markedExtraction("selected/src/main/java/Extraction.java"));
    }

    @ParameterizedTest(name = "Groovy source {0} selects its project")
    @ValueSource(strings = {"7.5.5", "7.5.8"})
    void exactGroovySourcesEnableOnlyTheirOwnProject(String version) {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'com.github.junrar:junrar:" + version + "' }",
                        "dependencies { implementation 'com.github.junrar:junrar:7.5.10' }",
                        source -> source.path("groovy/build.gradle")),
                markedExtraction("groovy/src/main/java/Extraction.java"));
    }

    @ParameterizedTest(name = "Kotlin source {0} selects its project")
    @ValueSource(strings = {"7.5.5", "7.5.8"})
    void exactKotlinSourcesEnableOnlyTheirOwnProject(String version) {
        rewriteRun(
                buildGradleKts(
                        "dependencies { implementation(\"com.github.junrar:junrar:" +
                        version + "\") }",
                        "dependencies { implementation(\"com.github.junrar:junrar:7.5.10\") }",
                        source -> source.path("kotlin/build.gradle.kts")),
                markedExtraction("kotlin/src/main/java/Extraction.java"));
    }

    @Test
    void targetOffListAndUnrelatedProjectsAreCompleteNoops() {
        rewriteRun(
                xml(pom("7.5.10"), source -> source.path("target/pom.xml")),
                unmarkedExtraction("target/src/main/java/Extraction.java"),
                xml(pom("7.5.9"), source -> source.path("off-list/pom.xml")),
                unmarkedExtraction("off-list/src/main/java/Extraction.java"),
                xml(project(""), source -> source.path("unrelated/pom.xml")),
                unmarkedExtraction("unrelated/src/main/java/Extraction.java"));
    }

    @Test
    void futureProjectGetsOnlyBuildConflictAndNoSourceMarkers() {
        rewriteRun(
                xml(pom("8.0.0"), source -> source.path("future/pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(JunrarSupport.TARGET_CONFLICT), printed);
                            assertFalse(printed.contains(JunrarSupport.TARGET_CONFLICT + "："), printed);
                        })),
                unmarkedExtraction("future/src/main/java/Extraction.java"));
    }

    @Test
    void nestedBuildBoundaryStopsOuterEligibility() {
        rewriteRun(
                xml(pom("7.5.5"), pom("7.5.10"), source -> source.path("pom.xml")),
                markedExtraction("src/main/java/RootExtraction.java"),
                xml(project(""), source -> source.path("nested/pom.xml")),
                unmarkedExtraction("nested/src/main/java/NestedExtraction.java"));
    }

    @Test
    void conflictingVersionsAtOneRootBlockUpgradeAndSourceSearches() {
        rewriteRun(
                xml(project("<dependencies>" + dependency("7.5.5") +
                        dependency("7.5.10") + "</dependencies>"),
                        source -> source.path("conflict/pom.xml")),
                unmarkedExtraction("conflict/src/main/java/Extraction.java"));
    }

    @Test
    void differentApprovedVersionsAtOneRootAreStillAConflict() {
        rewriteRun(
                xml(project("<dependencies>" + dependency("7.5.5") +
                        dependency("7.5.8") + "</dependencies>"),
                        source -> source.path("conflict/pom.xml")),
                unmarkedExtraction("conflict/src/main/java/Extraction.java"));
    }

    @Test
    void nestedGradleOwnerInsideRootScriptBlocksOuterAutomaticChange() {
        rewriteRun(
                buildGradle("""
                        dependencies {
                          implementation 'com.github.junrar:junrar:7.5.5'
                        }
                        project(':nested') {
                          dependencies {
                            implementation 'com.github.junrar:junrar:7.5.8'
                          }
                        }
                        """, source -> source.path("build.gradle")),
                unmarkedExtraction("src/main/java/Extraction.java"),
                unmarkedExtraction("nested/src/main/java/NestedExtraction.java"));
    }

    private static org.openrewrite.test.SourceSpecs markedExtraction(String path) {
        return java(extractionSource(path), source -> source.path(path).after(actual -> actual)
                .afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindJunrar7510SourceRisks.DESTINATION), printed);
                    assertTrue(printed.contains("/*~~>"), printed);
                }));
    }

    private static org.openrewrite.test.SourceSpecs unmarkedExtraction(String path) {
        return java(extractionSource(path), source -> source.path(path).afterRecipe(after -> {
            String printed = after.printAll();
            assertFalse(printed.contains(FindJunrar7510SourceRisks.DESTINATION), printed);
            assertFalse(printed.contains("/*~~>"), printed);
        }));
    }

    private static String extractionSource(String path) {
        String type = "Extraction" + Integer.toUnsignedString(path.hashCode());
        return """
                import com.github.junrar.Junrar;
                class %s {
                    void run() throws Exception {
                        Junrar.extract("input.rar", "out");
                    }
                }
                """.formatted(type);
    }

    private static String pom(String version) {
        return project("<dependencies>" + dependency(version) + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dependency(String version) {
        return "<dependency><groupId>com.github.junrar</groupId>" +
               "<artifactId>junrar</artifactId><version>" + version +
               "</version></dependency>";
    }
}
