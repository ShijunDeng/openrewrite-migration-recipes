package com.huawei.clouds.openrewrite.junrar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class JunrarRecommendedRecipeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                        "com.huawei.clouds.openrewrite.junrar.MigrateJunrarTo7_5_10"))
                .parser(JavaParser.fromJavaVersion().classpath("junrar"));
    }

    @ParameterizedTest(name = "aggregate upgrades selected version {0}")
    @ValueSource(strings = {"7.5.5", "7.5.8"})
    void aggregateUpgradesSelectedVersions(String version) {
        rewriteRun(xml(pom(version), pom("7.5.10"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "aggregate never downgrades {0}")
    @ValueSource(strings = {"7.5.11", "7.6.0", "8.0.0", "10.0.0"})
    void aggregateNeverDowngradesHigherVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path(version + "/pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(JunrarSupport.TARGET_CONFLICT), printed);
                    assertTrue(printed.contains("<version>" + version + "</version>"), printed);
                    assertFalse(printed.contains("<version>7.5.10</version>"), printed);
                })));
    }

    @Test
    void aggregateCombinesOfficialInventoryAndPreciseRiskMarker() {
        rewriteRun(
                xml(pom("7.5.5"), pom("7.5.10"),
                        source -> source.path("selected/pom.xml")),
                java("""
                        import java.io.File;
                        import com.github.junrar.Junrar;
                        class Extraction {
                            void extractArchive(File input, File output) throws Exception {
                                Junrar.extract(input, output);
                            }
                        }
                        """, source -> source.path("selected/src/main/java/Extraction.java")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindJunrar7510SourceRisks.DESTINATION), printed);
                            assertTrue(printed.contains("/*~~>"), printed);
                        })));
    }

    @Test
    void realStirlingBuildAndSourceFixtureRunTogether() throws Exception {
        String fixture = resource("fixtures/real/stirling-cbr-utils.java");
        rewriteRun(
                buildGradle(
                        "dependencies { api 'com.github.junrar:junrar:7.5.8' }",
                        "dependencies { api 'com.github.junrar:junrar:7.5.10' }",
                        source -> source.path("app/common/build.gradle")),
                java(fixture, source -> source.path(
                                "app/common/src/main/java/stirling/software/common/util/CbrUtils.java")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindJunrar7510SourceRisks.ARCHIVE_FORMAT),
                                    printed);
                            assertTrue(printed.contains(FindJunrar7510SourceRisks.CUSTOM_EXTRACTION),
                                    printed);
                            assertTrue(printed.contains(FindJunrar7510SourceRisks.EXCEPTION), printed);
                        })));
    }

    @Test
    void aggregateIsTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("7.5.5"), pom("7.5.10"), source -> source.path("pom.xml")));
    }

    @Test
    void targetBuildAndUnrelatedSourceAreStable() {
        rewriteRun(
                xml(pom("7.5.10"), source -> source.path("target/pom.xml")),
                java("""
                        import com.github.junrar.Junrar;
                        class TargetExtraction {
                            void run() throws Exception {
                                Junrar.extract("input.rar", "out");
                            }
                        }
                        """, source -> source.path("target/src/main/java/TargetExtraction.java")),
                xml("<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
                        "<artifactId>other</artifactId><version>1</version></project>",
                        source -> source.path("unrelated/pom.xml")),
                java("class Business { String value() { return \"junrar\"; } }",
                        source -> source.path("unrelated/src/main/java/Business.java")));
    }

    private static String resource(String path) throws IOException {
        try (var stream = JunrarRecommendedRecipeTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing test fixture " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String pom(String version) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>app</artifactId><version>1</version><dependencies><dependency>" +
               "<groupId>com.github.junrar</groupId><artifactId>junrar</artifactId><version>" +
               version + "</version></dependency></dependencies></project>";
    }
}
