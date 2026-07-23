package com.huawei.clouds.openrewrite.jakartael;

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

class FindJakartaEl6BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJakartaEl6BuildRisks());
    }

    @Test
    void publishedTargetIsNoop() {
        rewriteRun(xml(UpgradeJakartaElApiDependencyTest.pom("6.0.1"), source ->
                source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "recommended source {0}")
    @ValueSource(strings = {"3.0.3", "5.0.1"})
    void recommendedRecipeUpgradesEverySourceWithoutBuildRisk(String sourceVersion) {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jakartael").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.jakartael.MigrateJakartaElApiTo6_0_1")),
                xml(UpgradeJakartaElApiDependencyTest.pom(sourceVersion), source ->
                        source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>6.0.1</version>");
                            assertNoMarker(after.printAll());
                        })));
    }

    @Test
    void exclusiveTargetPropertyResolvesWithoutMarker() {
        rewriteRun(xml(UpgradeJakartaElApiDependencyTest.project(
                "<properties><jakartaEl.version>6.0.1</jakartaEl.version></properties><dependencies>" +
                UpgradeJakartaElApiDependencyTest.dep("${jakartaEl.version}") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void sharedSelectedPropertyIsMarkedAtDependencyOwner() {
        String pom = UpgradeJakartaElApiDependencyTest.project(
                "<properties><jakartaEl.version>5.0.1</jakartaEl.version></properties><dependencies>" +
                UpgradeJakartaElApiDependencyTest.dep("${jakartaEl.version}") +
                "</dependencies><build><finalName>${jakartaEl.version}</finalName></build>");
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jakartael").build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.jakartael.MigrateJakartaElApiTo6_0_1")),
                xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindJakartaEl6BuildRisks.OWNER);
                    assertContains(after.printAll(), "<jakartaEl.version>5.0.1</jakartaEl.version>");
                })));
    }

    @ParameterizedTest(name = "external owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[3,5)", "4.+", "+", "latest.release"})
    void marksVersionlessDynamicAndRangedOwners(String version) {
        String dependency = version.isEmpty() ? UpgradeJakartaElApiDependencyTest.depWithoutVersion() :
                UpgradeJakartaElApiDependencyTest.dep(version);
        rewriteRun(xml(UpgradeJakartaElApiDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJakartaEl6BuildRisks.OWNER))));
    }

    @Test
    void marksFixedVersionOutsideWorkbook() {
        rewriteRun(xml(UpgradeJakartaElApiDependencyTest.pom("4.0.0"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJakartaEl6BuildRisks.OUTSIDE))));
    }

    @Test
    void marksMavenVariants() {
        rewriteRun(
                xml(UpgradeJakartaElApiDependencyTest.project("<dependencies>" +
                        UpgradeJakartaElApiDependencyTest.dep("6.0.1", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("classifier/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindJakartaEl6BuildRisks.VARIANT))),
                xml(UpgradeJakartaElApiDependencyTest.project("<dependencies>" +
                        UpgradeJakartaElApiDependencyTest.dep("6.0.1", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("zip/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), FindJakartaEl6BuildRisks.VARIANT))));
    }

    @Test
    void variantsDoNotGateCompanionMavenOrGradleAudits() {
        String maven = UpgradeJakartaElApiDependencyTest.project(
                "<properties><java.version>11</java.version></properties><dependencies>" +
                UpgradeJakartaElApiDependencyTest.dep("6.0.1", "<classifier>tests</classifier>") +
                "<dependency><groupId>javax.el</groupId><artifactId>javax.el-api</artifactId><version>3.0.1-b12</version></dependency>" +
                "<dependency><groupId>org.glassfish</groupId><artifactId>jakarta.el</artifactId><version>6.0.1</version></dependency></dependencies>");
        rewriteRun(xml(maven, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll(); assertContains(out, FindJakartaEl6BuildRisks.VARIANT);
                    assertFalse(out.contains(FindJakartaEl6BuildRisks.JAVA) || out.contains(FindJakartaEl6BuildRisks.LEGACY_API) ||
                                out.contains(FindJakartaEl6BuildRisks.PROVIDER), out);
                })),
                buildGradle("sourceCompatibility = JavaVersion.VERSION_11\ndependencies { " +
                        "implementation 'jakarta.el:jakarta.el-api:6.0.1:tests'; " +
                        "implementation 'javax.el:javax.el-api:3.0.1-b12' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll(); assertContains(out, FindJakartaEl6BuildRisks.VARIANT);
                            assertFalse(out.contains(FindJakartaEl6BuildRisks.JAVA) || out.contains(FindJakartaEl6BuildRisks.LEGACY_API), out);
                        })));
    }

    @Test
    void profileDependencyDoesNotLeakIntoSiblingCompilerBaseline() {
        String pom = UpgradeJakartaElApiDependencyTest.project(
                "<profiles>" +
                "<profile><id>selected</id><dependencies>" + UpgradeJakartaElApiDependencyTest.dep("6.0.1") +
                "</dependencies></profile>" +
                "<profile><id>sibling</id><properties><maven.compiler.release>8</maven.compiler.release>" +
                "</properties></profile>" +
                "</profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void profileDependencyMarksSameProfileJavaBaseline() {
        String pom = UpgradeJakartaElApiDependencyTest.project(
                "<profiles><profile><id>selected</id>" +
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<dependencies>" + UpgradeJakartaElApiDependencyTest.dep("6.0.1") +
                "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6BuildRisks.JAVA))));
    }

    @Test
    void rootDependencyMarksProfileJavaBaseline() {
        String pom = UpgradeJakartaElApiDependencyTest.project(
                "<dependencies>" + UpgradeJakartaElApiDependencyTest.dep("6.0.1") + "</dependencies>" +
                "<profiles><profile><id>downstream</id>" +
                "<properties><maven.compiler.release>10</maven.compiler.release></properties>" +
                "</profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6BuildRisks.JAVA))));
    }

    @Test
    void profileDependencyMarksRootJavaBaseline() {
        String pom = UpgradeJakartaElApiDependencyTest.project(
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<profiles><profile><id>selected</id><dependencies>" +
                UpgradeJakartaElApiDependencyTest.dep("6.0.1") + "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6BuildRisks.JAVA))));
    }

    @Test
    void resolvesIndirectOwnedMavenJavaBaseline() {
        String pom = UpgradeJakartaElApiDependencyTest.project(
                "<properties><jdk.release>11</jdk.release><maven.compiler.release>${jdk.release}</maven.compiler.release></properties>" +
                "<dependencies>" + UpgradeJakartaElApiDependencyTest.dep("6.0.1") + "</dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), FindJakartaEl6BuildRisks.JAVA))));
    }

    @ParameterizedTest(name = "pre-Java-17 baseline {0}")
    @ValueSource(strings = {"1.8", "8", "9", "11", "16"})
    void marksPreJava17Baseline(String version) {
        rewriteRun(xml(UpgradeJakartaElApiDependencyTest.project(
                        "<properties><java.version>" + version + "</java.version></properties><dependencies>" +
                        UpgradeJakartaElApiDependencyTest.dep("6.0.1") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJakartaEl6BuildRisks.JAVA))));
    }

    @ParameterizedTest(name = "supported Java baseline {0}")
    @ValueSource(strings = {"17", "21", "${java.release}"})
    void acceptsSupportedOrExternallyOwnedJavaBaseline(String version) {
        rewriteRun(xml(UpgradeJakartaElApiDependencyTest.project(
                        "<properties><maven.compiler.release>" + version + "</maven.compiler.release></properties>" +
                        "<dependencies>" + UpgradeJakartaElApiDependencyTest.dep("6.0.1") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "legacy API {0}:{1}")
    @org.junit.jupiter.params.provider.CsvSource({
            "javax.el, javax.el-api", "org.glassfish, javax.el"
    })
    void marksLegacyJavaxApiBesideSelectedDependency(String group, String artifact) {
        String legacy = "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact +
                        "</artifactId><version>3.0.1-b12</version></dependency>";
        rewriteRun(xml(UpgradeJakartaElApiDependencyTest.project("<dependencies>" +
                UpgradeJakartaElApiDependencyTest.dep("6.0.1") + legacy + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJakartaEl6BuildRisks.LEGACY_API))));
    }

    @ParameterizedTest(name = "provider {0}:{1}")
    @org.junit.jupiter.params.provider.CsvSource({
            "org.glassfish, jakarta.el", "org.glassfish.web, jakarta.el",
            "org.apache.tomcat.embed, tomcat-embed-el"
    })
    void marksExplicitProviderBesideSelectedDependency(String group, String artifact) {
        String provider = "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact +
                          "</artifactId><version>6.0.1</version></dependency>";
        rewriteRun(xml(UpgradeJakartaElApiDependencyTest.project("<dependencies>" +
                UpgradeJakartaElApiDependencyTest.dep("6.0.1") + provider + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJakartaEl6BuildRisks.PROVIDER))));
    }

    @Test
    void marksRootGradleJavaBaselinesButNotNestedProjects() {
        rewriteRun(
                buildGradle("sourceCompatibility = JavaVersion.VERSION_11\n" +
                                "dependencies { implementation 'jakarta.el:jakarta.el-api:6.0.1' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJakartaEl6BuildRisks.JAVA))),
                buildGradle("java { targetCompatibility = JavaVersion.VERSION_16 }\n" +
                                "dependencies { implementation 'jakarta.el:jakarta.el-api:6.0.1' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJakartaEl6BuildRisks.JAVA))),
                buildGradle("subprojects { sourceCompatibility = JavaVersion.VERSION_8 }\n" +
                                "dependencies { implementation 'jakarta.el:jakarta.el-api:6.0.1' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksGradleLegacyApiAndProvider() {
        rewriteRun(
                buildGradle("dependencies {\n" +
                                "  implementation 'jakarta.el:jakarta.el-api:6.0.1'\n" +
                                "  implementation 'javax.el:javax.el-api:3.0.1-b12'\n" +
                                "}", source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJakartaEl6BuildRisks.LEGACY_API))),
                buildGradle("dependencies {\n" +
                                "  implementation 'jakarta.el:jakarta.el-api:6.0.1'\n" +
                                "  runtimeOnly group: 'org.glassfish', name: 'jakarta.el', version: '6.0.1'\n" +
                                "}", source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJakartaEl6BuildRisks.PROVIDER))));
    }

    @Test
    void marksGradleOutsideDynamicAndVariants() {
        rewriteRun(
                buildGradle("dependencies { implementation 'jakarta.el:jakarta.el-api:4.0.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJakartaEl6BuildRisks.OUTSIDE))),
                buildGradle("def v='6.0.1'\ndependencies { implementation \"jakarta.el:jakarta.el-api:$v\" }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJakartaEl6BuildRisks.OWNER))),
                buildGradle("dependencies { implementation 'jakarta.el:jakarta.el-api:6.0.1@zip' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJakartaEl6BuildRisks.VARIANT))),
                buildGradleKts("dependencies { implementation(\"jakarta.el:jakarta.el-api:3.0.3\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJakartaEl6BuildRisks.OWNER))));
    }

    @Test
    void nestedGradleUnrelatedAndGeneratedFilesAreNoop() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'jakarta.el:jakarta.el-api:4.0.0' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'example:jakarta.el-api:4.0.0' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(UpgradeJakartaElApiDependencyTest.pom("4.0.0"), source -> source.path("generated-code/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void rootGradleDependencyDoesNotOwnNestedProjectPolicy() {
        rewriteRun(buildGradle("dependencies { implementation 'jakarta.el:jakarta.el-api:6.0.1' }\n" +
                "project(':child') { sourceCompatibility = JavaVersion.VERSION_11; dependencies { " +
                "implementation 'javax.el:javax.el-api:3.0.1-b12'; runtimeOnly 'org.glassfish:jakarta.el:6.0.1' } }",
                source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void dynamicGradleVariantIsMarkedAsVariantNotStandardOwner() {
        rewriteRun(buildGradle("def v='6.0.1'\ndependencies { implementation \"jakarta.el:jakarta.el-api:$v:tests\" }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll(); assertContains(out, FindJakartaEl6BuildRisks.VARIANT);
                    assertFalse(out.contains(FindJakartaEl6BuildRisks.OWNER), out);
                })));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeJakartaElApiDependencyTest.pom("4.0.0"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindJakartaEl6BuildRisks.OUTSIDE, 1))));
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
