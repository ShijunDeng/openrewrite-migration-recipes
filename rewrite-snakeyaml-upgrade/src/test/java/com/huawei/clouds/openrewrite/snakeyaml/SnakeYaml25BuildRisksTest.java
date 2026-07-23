package com.huawei.clouds.openrewrite.snakeyaml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class SnakeYaml25BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSnakeYaml25BuildRisks());
    }

    @Test
    void publishedTargetIsNoop() {
        rewriteRun(xml(UpgradeSnakeYamlDependencyTest.pom("2.5"), source ->
                source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "recommended source {0}")
    @ValueSource(strings = {"1.24", "1.26", "1.27", "1.28", "1.32", "1.33", "2"})
    void recommendedRecipeUpgradesEverySourceWithoutBuildRisk(String sourceVersion) {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.snakeyaml").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.snakeyaml.MigrateSnakeYamlTo2_5")),
                xml(UpgradeSnakeYamlDependencyTest.pom(sourceVersion), source ->
                        source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("<version>2.5</version>"), after.printAll());
                            assertNoMarker(after.printAll());
                        })));
    }

    @Test
    void exclusiveTargetPropertyResolvesWithoutMarker() {
        rewriteRun(xml(UpgradeSnakeYamlDependencyTest.project(
                "<properties><snakeyaml.version>2.5</snakeyaml.version></properties><dependencies>" +
                UpgradeSnakeYamlDependencyTest.dep("${snakeyaml.version}") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void sharedSelectedPropertyIsMarkedAtDependencyOwner() {
        String pom = UpgradeSnakeYamlDependencyTest.project(
                "<properties><snakeyaml.version>1.33</snakeyaml.version></properties><dependencies>" +
                UpgradeSnakeYamlDependencyTest.dep("${snakeyaml.version}") +
                "</dependencies><build><finalName>${snakeyaml.version}</finalName></build>");
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.snakeyaml").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.snakeyaml.MigrateSnakeYamlTo2_5")),
                xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindSnakeYaml25BuildRisks.OWNER);
                    assertContains(after.printAll(), "<snakeyaml.version>1.33</snakeyaml.version>");
                })));
    }

    @ParameterizedTest(name = "external owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[1,3)", "2.+", "+", "latest.release"})
    void marksVersionlessDynamicAndRangedOwners(String version) {
        String dependency = version.isEmpty() ? UpgradeSnakeYamlDependencyTest.depWithoutVersion() :
                UpgradeSnakeYamlDependencyTest.dep(version);
        rewriteRun(xml(UpgradeSnakeYamlDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSnakeYaml25BuildRisks.OWNER))));
    }

    @Test
    void marksFixedVersionOutsideWorkbook() {
        rewriteRun(xml(UpgradeSnakeYamlDependencyTest.pom("2.4"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSnakeYaml25BuildRisks.OUTSIDE))));
    }

    @Test
    void marksMavenVariants() {
        rewriteRun(
                xml(UpgradeSnakeYamlDependencyTest.project("<dependencies>" +
                        UpgradeSnakeYamlDependencyTest.dep("2.5", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("classifier/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindSnakeYaml25BuildRisks.VARIANT))),
                xml(UpgradeSnakeYamlDependencyTest.project("<dependencies>" +
                        UpgradeSnakeYamlDependencyTest.dep("2.5", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("zip/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindSnakeYaml25BuildRisks.VARIANT))));
    }

    @Test
    void marksClassicAndEngineMixOnlyWhenClassicIsPresent() {
        String engine = "<dependency><groupId>org.snakeyaml</groupId><artifactId>snakeyaml-engine</artifactId>" +
                        "<version>2.9</version></dependency>";
        rewriteRun(
                xml(UpgradeSnakeYamlDependencyTest.project("<dependencies>" +
                        UpgradeSnakeYamlDependencyTest.dep("2.5") + engine + "</dependencies>"),
                        source -> source.path("mixed/pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSnakeYaml25BuildRisks.ENGINE))),
                xml(UpgradeSnakeYamlDependencyTest.project("<dependencies>" + engine + "</dependencies>"),
                        source -> source.path("engine-only/pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void profileClassicDoesNotLeakIntoSiblingProfile() {
        String pom = UpgradeSnakeYamlDependencyTest.project(
                "<profiles>" +
                "<profile><id>classic</id><dependencies>" + UpgradeSnakeYamlDependencyTest.dep("2.5") +
                "</dependencies></profile>" +
                "<profile><id>sibling</id><properties><maven.compiler.release>7</maven.compiler.release>" +
                "</properties><dependencies>" + engineDependency() + "</dependencies></profile>" +
                "</profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void profileClassicMarksSameProfileEngineAndJavaBaseline() {
        String pom = UpgradeSnakeYamlDependencyTest.project(
                "<profiles><profile><id>classic</id>" +
                "<properties><maven.compiler.release>7</maven.compiler.release></properties>" +
                "<dependencies>" + UpgradeSnakeYamlDependencyTest.dep("2.5") + engineDependency() +
                "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            assertContains(after.printAll(), FindSnakeYaml25BuildRisks.ENGINE);
            assertContains(after.printAll(), FindSnakeYaml25BuildRisks.JAVA);
        })));
    }

    @Test
    void rootClassicMarksProfileEngineAndJavaBaseline() {
        String pom = UpgradeSnakeYamlDependencyTest.project(
                "<dependencies>" + UpgradeSnakeYamlDependencyTest.dep("2.5") + "</dependencies>" +
                "<profiles><profile><id>downstream</id>" +
                "<properties><maven.compiler.release>7</maven.compiler.release></properties>" +
                "<dependencies>" + engineDependency() + "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            assertContains(after.printAll(), FindSnakeYaml25BuildRisks.ENGINE);
            assertContains(after.printAll(), FindSnakeYaml25BuildRisks.JAVA);
        })));
    }

    @Test
    void profileClassicMarksRootEngineAndJavaBaseline() {
        String pom = UpgradeSnakeYamlDependencyTest.project(
                "<properties><maven.compiler.release>7</maven.compiler.release></properties>" +
                "<dependencies>" + engineDependency() + "</dependencies>" +
                "<profiles><profile><id>classic</id><dependencies>" +
                UpgradeSnakeYamlDependencyTest.dep("2.5") + "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            assertContains(after.printAll(), FindSnakeYaml25BuildRisks.ENGINE);
            assertContains(after.printAll(), FindSnakeYaml25BuildRisks.JAVA);
        })));
    }

    @Test
    void marksPreJava8BaselineButAcceptsJava8() {
        rewriteRun(
                xml(UpgradeSnakeYamlDependencyTest.project("<properties><maven.compiler.release>7</maven.compiler.release></properties>" +
                        "<dependencies>" + UpgradeSnakeYamlDependencyTest.dep("2.5") + "</dependencies>"),
                        source -> source.path("java7/pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSnakeYaml25BuildRisks.JAVA))),
                xml(UpgradeSnakeYamlDependencyTest.project("<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                        "<dependencies>" + UpgradeSnakeYamlDependencyTest.dep("2.5") + "</dependencies>"),
                        source -> source.path("java8/pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksGradleOutsideDynamicVariantAndEngine() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.yaml:snakeyaml:2.4' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSnakeYaml25BuildRisks.OUTSIDE))),
                buildGradle("def v='2.5'\ndependencies { implementation \"org.yaml:snakeyaml:$v\" }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSnakeYaml25BuildRisks.OWNER))),
                buildGradle("dependencies { implementation 'org.yaml:snakeyaml:2.5@zip' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSnakeYaml25BuildRisks.VARIANT))),
                buildGradleKts("dependencies { implementation(\"org.yaml:snakeyaml:2.5\"); implementation(\"org.snakeyaml:snakeyaml-engine:2.9\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindSnakeYaml25BuildRisks.ENGINE))));
    }

    @Test
    void nestedGradleUnrelatedAndGeneratedFilesAreNoop() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'org.yaml:snakeyaml:2.4' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'org.example:snakeyaml:2.4' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(UpgradeSnakeYamlDependencyTest.pom("2.4"), source -> source.path("generated-code/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeSnakeYamlDependencyTest.pom("2.4"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindSnakeYaml25BuildRisks.OUTSIDE, 1))));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~(") || actual.contains("<!--~~("), actual);
    }

    private static String engineDependency() {
        return "<dependency><groupId>org.snakeyaml</groupId><artifactId>snakeyaml-engine</artifactId>" +
               "<version>2.9</version></dependency>";
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
