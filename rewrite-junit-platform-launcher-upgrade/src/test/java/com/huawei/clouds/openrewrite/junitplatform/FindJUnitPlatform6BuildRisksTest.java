package com.huawei.clouds.openrewrite.junitplatform;

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

class FindJUnitPlatform6BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJUnitPlatform6BuildRisks());
    }

    @Test
    void publishedTargetIsNoop() {
        rewriteRun(xml(UpgradeJUnitPlatformLauncherDependencyTest.pom("6.0.1"), source ->
                source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "recommended source {0}")
    @ValueSource(strings = {"1.7.2", "1.8.2"})
    void recommendedRecipeUpgradesEverySourceWithoutBuildRisk(String sourceVersion) {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitplatform").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.junitplatform.MigrateJUnitPlatformLauncherTo6_0_1")),
                xml(UpgradeJUnitPlatformLauncherDependencyTest.pom(sourceVersion), source ->
                        source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>6.0.1</version>");
                            assertNoMarker(after.printAll());
                        })));
    }

    @Test
    void exclusiveTargetPropertyResolvesWithoutMarker() {
        rewriteRun(xml(UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<properties><junitPlatformLauncher.version>6.0.1</junitPlatformLauncher.version></properties><dependencies>" +
                UpgradeJUnitPlatformLauncherDependencyTest.dep("${junitPlatformLauncher.version}") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void sharedSelectedPropertyIsMarkedAtDependencyOwner() {
        String pom = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<properties><junitPlatformLauncher.version>1.8.2</junitPlatformLauncher.version></properties><dependencies>" +
                UpgradeJUnitPlatformLauncherDependencyTest.dep("${junitPlatformLauncher.version}") +
                "</dependencies><build><finalName>${junitPlatformLauncher.version}</finalName></build>");
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitplatform").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.junitplatform.MigrateJUnitPlatformLauncherTo6_0_1")),
                xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.OWNER);
                    assertContains(after.printAll(), "<junitPlatformLauncher.version>1.8.2</junitPlatformLauncher.version>");
                })));
    }

    @ParameterizedTest(name = "external owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[3,5)", "4.+", "+", "latest.release"})
    void marksVersionlessDynamicAndRangedOwners(String version) {
        String dependency = version.isEmpty() ? UpgradeJUnitPlatformLauncherDependencyTest.depWithoutVersion() :
                UpgradeJUnitPlatformLauncherDependencyTest.dep(version);
        rewriteRun(xml(UpgradeJUnitPlatformLauncherDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.OWNER))));
    }

    @Test
    void marksFixedVersionOutsideWorkbook() {
        rewriteRun(xml(UpgradeJUnitPlatformLauncherDependencyTest.pom("1.9.0"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.OUTSIDE))));
    }

    @Test
    void marksMavenVariants() {
        rewriteRun(
                xml(UpgradeJUnitPlatformLauncherDependencyTest.project("<dependencies>" +
                        UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("classifier/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.VARIANT))),
                xml(UpgradeJUnitPlatformLauncherDependencyTest.project("<dependencies>" +
                        UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("zip/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.VARIANT))));
    }

    @Test
    void variantsDoNotGateCompanionMavenOrGradleAudits() {
        String maven = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<properties><java.version>11</java.version></properties><dependencies>" +
                UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1", "<classifier>tests</classifier>") +
                "<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-engine</artifactId><version>5.8.2</version></dependency>" +
                "</dependencies><build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId><version>2.22.2</version></plugin></plugins></build>");
        rewriteRun(xml(maven, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll(); assertContains(out, FindJUnitPlatform6BuildRisks.VARIANT);
                    assertFalse(out.contains(FindJUnitPlatform6BuildRisks.JAVA) || out.contains(FindJUnitPlatform6BuildRisks.ALIGNMENT) ||
                                out.contains(FindJUnitPlatform6BuildRisks.PROVIDER), out);
                })),
                buildGradle("sourceCompatibility = JavaVersion.VERSION_11\ndependencies { " +
                        "testRuntimeOnly 'org.junit.platform:junit-platform-launcher:6.0.1:tests'; " +
                        "testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.2' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll(); assertContains(out, FindJUnitPlatform6BuildRisks.VARIANT);
                            assertFalse(out.contains(FindJUnitPlatform6BuildRisks.JAVA) || out.contains(FindJUnitPlatform6BuildRisks.ALIGNMENT), out);
                        })));
    }

    @Test
    void profileDependencyDoesNotLeakIntoSiblingCompilerBaseline() {
        String pom = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<profiles>" +
                "<profile><id>selected</id><dependencies>" + UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") +
                "</dependencies></profile>" +
                "<profile><id>sibling</id><properties><maven.compiler.release>8</maven.compiler.release>" +
                "</properties></profile>" +
                "</profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void profileDependencyMarksSameProfileJavaBaseline() {
        String pom = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<profiles><profile><id>selected</id>" +
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<dependencies>" + UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") +
                "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.JAVA))));
    }

    @Test
    void rootDependencyMarksProfileJavaBaseline() {
        String pom = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<dependencies>" + UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") + "</dependencies>" +
                "<profiles><profile><id>downstream</id>" +
                "<properties><maven.compiler.release>10</maven.compiler.release></properties>" +
                "</profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.JAVA))));
    }

    @Test
    void profileDependencyMarksRootJavaBaseline() {
        String pom = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<profiles><profile><id>selected</id><dependencies>" +
                UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") + "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.JAVA))));
    }

    @Test
    void resolvesIndirectOwnedMavenJavaBaseline() {
        String pom = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<properties><jdk.release>11</jdk.release><maven.compiler.release>${jdk.release}</maven.compiler.release></properties>" +
                "<dependencies>" + UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") + "</dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.JAVA))));
    }

    @Test
    void marksUnalignedJUnitFamilyAndBomVersions() {
        String pom = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<dependencyManagement><dependencies><dependency><groupId>org.junit</groupId>" +
                "<artifactId>junit-bom</artifactId><version>5.8.2</version><type>pom</type><scope>import</scope>" +
                "</dependency></dependencies></dependencyManagement><dependencies>" +
                UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") +
                "<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-engine</artifactId>" +
                "<version>5.8.2</version></dependency></dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertCount(after.printAll(), FindJUnitPlatform6BuildRisks.ALIGNMENT, 2))));
    }

    @Test
    void alignedJUnit6FamilyIsNoop() {
        String pom = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<dependencies>" + UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") +
                "<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-engine</artifactId>" +
                "<version>6.0.1</version></dependency></dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksOldAndExternallyOwnedMavenTestProviders() {
        String pom = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<properties><surefire.version>2.22.2</surefire.version></properties><dependencies>" +
                UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") + "</dependencies><build><plugins>" +
                "<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId>" +
                "<version>${surefire.version}</version></plugin>" +
                "<plugin><artifactId>maven-failsafe-plugin</artifactId></plugin>" +
                "</plugins></build>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.PROVIDER);
            assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.PROVIDER_OWNER);
        })));
    }

    @Test
    void acceptsSupportedMavenProvider() {
        String pom = UpgradeJUnitPlatformLauncherDependencyTest.project(
                "<dependencies>" + UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") +
                "</dependencies><build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId>" +
                "<version>3.5.5</version></plugin></plugins></build>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "pre-Java-17 baseline {0}")
    @ValueSource(strings = {"1.8", "8", "9", "16"})
    void marksPreJava17Baseline(String version) {
        rewriteRun(xml(UpgradeJUnitPlatformLauncherDependencyTest.project(
                        "<properties><java.version>" + version + "</java.version></properties><dependencies>" +
                        UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.JAVA))));
    }

    @ParameterizedTest(name = "supported Java baseline {0}")
    @ValueSource(strings = {"17", "21", "${java.release}"})
    void acceptsSupportedOrExternallyOwnedJavaBaseline(String version) {
        rewriteRun(xml(UpgradeJUnitPlatformLauncherDependencyTest.project(
                        "<properties><maven.compiler.release>" + version + "</maven.compiler.release></properties>" +
                        "<dependencies>" + UpgradeJUnitPlatformLauncherDependencyTest.dep("6.0.1") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksGradleOutsideDynamicAndVariants() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.junit.platform:junit-platform-launcher:1.9.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.OUTSIDE))),
                buildGradle("def v='6.0.1'\ndependencies { implementation \"org.junit.platform:junit-platform-launcher:$v\" }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.OWNER))),
                buildGradle("dependencies { implementation 'org.junit.platform:junit-platform-launcher:6.0.1@zip' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.VARIANT))),
                buildGradleKts("dependencies { implementation(\"org.junit.platform:junit-platform-launcher:1.7.2\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.OWNER))));
    }

    @Test
    void marksGradleJavaAndJUnitFamilyAlignment() {
        rewriteRun(buildGradle(
                "java { sourceCompatibility = JavaVersion.VERSION_11 }\n" +
                "dependencies { testRuntimeOnly 'org.junit.platform:junit-platform-launcher:6.0.1'; " +
                "testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.2' }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.JAVA);
                    assertContains(after.printAll(), FindJUnitPlatform6BuildRisks.ALIGNMENT);
                })));
    }

    @Test
    void nestedGradleUnrelatedAndGeneratedFilesAreNoop() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'org.junit.platform:junit-platform-launcher:1.9.0' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'example:junit-platform-launcher:1.9.0' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(UpgradeJUnitPlatformLauncherDependencyTest.pom("1.9.0"), source -> source.path("generated-code/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void rootGradleDependencyDoesNotOwnNestedProjectPolicy() {
        rewriteRun(buildGradle("dependencies { testRuntimeOnly 'org.junit.platform:junit-platform-launcher:6.0.1' }\n" +
                "project(':child') { sourceCompatibility = JavaVersion.VERSION_11; dependencies { " +
                "testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.8.2' } }",
                source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeJUnitPlatformLauncherDependencyTest.pom("1.9.0"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindJUnitPlatform6BuildRisks.OUTSIDE, 1))));
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
