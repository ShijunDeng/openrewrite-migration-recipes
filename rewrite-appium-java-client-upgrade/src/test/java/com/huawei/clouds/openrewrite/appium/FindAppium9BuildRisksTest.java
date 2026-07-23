package com.huawei.clouds.openrewrite.appium;

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

class FindAppium9BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindAppium9BuildRisks());
    }

    @Test
    void publishedTargetIsNoop() {
        rewriteRun(xml(UpgradeAppiumDependencyTest.pom("9.2.3"), source ->
                source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "recommended source {0}")
    @ValueSource(strings = {"7.5.1"})
    void recommendedRecipeUpgradesEverySourceWithoutBuildRisk(String sourceVersion) {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.appium").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.appium.MigrateAppiumJavaClientTo9_2_3")),
                xml(UpgradeAppiumDependencyTest.pom(sourceVersion), source ->
                        source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>9.2.3</version>");
                            assertNoMarker(after.printAll());
                        })));
    }

    @Test
    void exclusiveTargetPropertyResolvesWithoutMarker() {
        rewriteRun(xml(UpgradeAppiumDependencyTest.project(
                "<properties><appium.version>9.2.3</appium.version></properties><dependencies>" +
                UpgradeAppiumDependencyTest.dep("${appium.version}") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void sharedSelectedPropertyIsMarkedAtDependencyOwner() {
        String pom = UpgradeAppiumDependencyTest.project(
                "<properties><appium.version>7.5.1</appium.version></properties><dependencies>" +
                UpgradeAppiumDependencyTest.dep("${appium.version}") +
                "</dependencies><build><finalName>${appium.version}</finalName></build>");
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.appium").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.appium.MigrateAppiumJavaClientTo9_2_3")),
                xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindAppium9BuildRisks.OWNER);
                    assertContains(after.printAll(), "<appium.version>7.5.1</appium.version>");
                })));
    }

    @ParameterizedTest(name = "external owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[7,10)", "9.+", "+", "latest.release"})
    void marksVersionlessDynamicAndRangedOwners(String version) {
        String dependency = version.isEmpty() ? UpgradeAppiumDependencyTest.depWithoutVersion() :
                UpgradeAppiumDependencyTest.dep(version);
        rewriteRun(xml(UpgradeAppiumDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindAppium9BuildRisks.OWNER))));
    }

    @Test
    void marksFixedVersionOutsideWorkbook() {
        rewriteRun(xml(UpgradeAppiumDependencyTest.pom("7.4.1"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindAppium9BuildRisks.OUTSIDE))));
    }

    @Test
    void marksMavenVariants() {
        rewriteRun(
                xml(UpgradeAppiumDependencyTest.project("<dependencies>" +
                        UpgradeAppiumDependencyTest.dep("9.2.3", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("classifier/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindAppium9BuildRisks.VARIANT))),
                xml(UpgradeAppiumDependencyTest.project("<dependencies>" +
                        UpgradeAppiumDependencyTest.dep("9.2.3", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("zip/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindAppium9BuildRisks.VARIANT))));
    }

    @Test
    void profileDependencyDoesNotLeakIntoSiblingCompilerBaseline() {
        String pom = UpgradeAppiumDependencyTest.project(
                "<profiles>" +
                "<profile><id>selected</id><dependencies>" + UpgradeAppiumDependencyTest.dep("9.2.3") +
                "</dependencies></profile>" +
                "<profile><id>sibling</id><properties><maven.compiler.release>8</maven.compiler.release>" +
                "</properties></profile>" +
                "</profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void profileDependencyMarksSameProfileJavaBaseline() {
        String pom = UpgradeAppiumDependencyTest.project(
                "<profiles><profile><id>selected</id>" +
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<dependencies>" + UpgradeAppiumDependencyTest.dep("9.2.3") +
                "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindAppium9BuildRisks.JAVA))));
    }

    @Test
    void rootDependencyMarksProfileJavaBaseline() {
        String pom = UpgradeAppiumDependencyTest.project(
                "<dependencies>" + UpgradeAppiumDependencyTest.dep("9.2.3") + "</dependencies>" +
                "<profiles><profile><id>downstream</id>" +
                "<properties><maven.compiler.release>10</maven.compiler.release></properties>" +
                "</profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindAppium9BuildRisks.JAVA))));
    }

    @Test
    void profileDependencyMarksRootJavaBaseline() {
        String pom = UpgradeAppiumDependencyTest.project(
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<profiles><profile><id>selected</id><dependencies>" +
                UpgradeAppiumDependencyTest.dep("9.2.3") + "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindAppium9BuildRisks.JAVA))));
    }

    @ParameterizedTest(name = "pre-Java-11 baseline {0}")
    @ValueSource(strings = {"1.8", "8", "9", "10"})
    void marksPreJava11Baseline(String version) {
        rewriteRun(xml(UpgradeAppiumDependencyTest.project(
                        "<properties><java.version>" + version + "</java.version></properties><dependencies>" +
                        UpgradeAppiumDependencyTest.dep("9.2.3") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindAppium9BuildRisks.JAVA))));
    }

    @ParameterizedTest(name = "supported Java baseline {0}")
    @ValueSource(strings = {"11", "17", "21", "${java.release}"})
    void acceptsSupportedOrExternallyOwnedJavaBaseline(String version) {
        rewriteRun(xml(UpgradeAppiumDependencyTest.project(
                        "<properties><maven.compiler.release>" + version + "</maven.compiler.release></properties>" +
                        "<dependencies>" + UpgradeAppiumDependencyTest.dep("9.2.3") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "compatible Selenium {0}")
    @ValueSource(strings = {"4.19.0", "4.20.0", "4.31.1"})
    void acceptsCompatibleSeleniumFamily(String version) {
        String selenium = "<dependency><groupId>org.seleniumhq.selenium</groupId>" +
                          "<artifactId>selenium-java</artifactId><version>" + version + "</version></dependency>";
        rewriteRun(xml(UpgradeAppiumDependencyTest.project("<dependencies>" +
                        UpgradeAppiumDependencyTest.dep("9.2.3") + selenium + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "incompatible Selenium {0}")
    @ValueSource(strings = {"3.141.59", "4.13.0", "4.18.1", "5.0.0", "4.19.0-alpha1"})
    void marksIncompatibleSeleniumFamily(String version) {
        String selenium = "<dependency><groupId>org.seleniumhq.selenium</groupId>" +
                          "<artifactId>selenium-remote-driver</artifactId><version>" + version + "</version></dependency>";
        rewriteRun(xml(UpgradeAppiumDependencyTest.project("<dependencies>" +
                        UpgradeAppiumDependencyTest.dep("9.2.3") + selenium + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindAppium9BuildRisks.SELENIUM))));
    }

    @Test
    void marksExternallyOwnedSeleniumVersion() {
        String selenium = "<dependency><groupId>org.seleniumhq.selenium</groupId>" +
                          "<artifactId>selenium-support</artifactId><version>${selenium.version}</version></dependency>";
        rewriteRun(xml(UpgradeAppiumDependencyTest.project("<dependencies>" +
                        UpgradeAppiumDependencyTest.dep("9.2.3") + selenium + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindAppium9BuildRisks.SELENIUM_OWNER))));
    }

    @Test
    void profileAppiumDoesNotGateSiblingSelenium() {
        String oldSelenium = "<dependency><groupId>org.seleniumhq.selenium</groupId>" +
                             "<artifactId>selenium-java</artifactId><version>4.13.0</version></dependency>";
        String pom = UpgradeAppiumDependencyTest.project("<profiles>" +
                "<profile><id>appium</id><dependencies>" + UpgradeAppiumDependencyTest.dep("9.2.3") +
                "</dependencies></profile><profile><id>sibling</id><dependencies>" + oldSelenium +
                "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void rootAppiumGatesProfileSelenium() {
        String oldSelenium = "<dependency><groupId>org.seleniumhq.selenium</groupId>" +
                             "<artifactId>selenium-java</artifactId><version>4.13.0</version></dependency>";
        String pom = UpgradeAppiumDependencyTest.project("<dependencies>" +
                UpgradeAppiumDependencyTest.dep("9.2.3") + "</dependencies><profiles><profile><id>it</id>" +
                "<dependencies>" + oldSelenium + "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindAppium9BuildRisks.SELENIUM))));
    }

    @Test
    void gradleSeleniumCompatibilityIsScopedToRootAppiumBuild() {
        rewriteRun(
                buildGradle("dependencies { implementation 'io.appium:java-client:9.2.3'; implementation 'org.seleniumhq.selenium:selenium-java:4.13.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindAppium9BuildRisks.SELENIUM))),
                buildGradle("dependencies { implementation 'io.appium:java-client:9.2.3'; implementation 'org.seleniumhq.selenium:selenium-java:4.19.0' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'org.seleniumhq.selenium:selenium-java:4.13.0' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksOwnedGradleJavaBaselines() {
        rewriteRun(
                buildGradle("sourceCompatibility = '1.8'\ndependencies { implementation 'io.appium:java-client:9.2.3' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindAppium9BuildRisks.JAVA))),
                buildGradleKts("java { targetCompatibility = JavaVersion.VERSION_10 }\n" +
                                "dependencies { implementation(\"io.appium:java-client:9.2.3\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindAppium9BuildRisks.JAVA))));
    }

    @Test
    void rootGradleDependencyDoesNotOwnNestedProjectJavaBaseline() {
        rewriteRun(buildGradle("""
                dependencies { implementation 'io.appium:java-client:9.2.3' }
                project(':legacy') { sourceCompatibility = '1.8' }
                custom { java { targetCompatibility = JavaVersion.VERSION_10 } }
                """, source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksGradleOutsideDynamicAndVariants() {
        rewriteRun(
                buildGradle("dependencies { implementation 'io.appium:java-client:7.4.1' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindAppium9BuildRisks.OUTSIDE))),
                buildGradle("def v='9.2.3'\ndependencies { implementation \"io.appium:java-client:$v\" }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindAppium9BuildRisks.OWNER))),
                buildGradle("dependencies { implementation 'io.appium:java-client:9.2.3@zip' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindAppium9BuildRisks.VARIANT))),
                buildGradleKts("dependencies { implementation(\"io.appium:java-client:7.5.1\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindAppium9BuildRisks.OWNER))));
    }

    @Test
    void nestedGradleUnrelatedAndGeneratedFilesAreNoop() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'io.appium:java-client:7.4.1' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'example:appium:7.4.1' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(UpgradeAppiumDependencyTest.pom("7.4.1"), source -> source.path("generated-code/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeAppiumDependencyTest.pom("7.4.1"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindAppium9BuildRisks.OUTSIDE, 1))));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~(") || actual.contains("<!--~~("), actual);
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
