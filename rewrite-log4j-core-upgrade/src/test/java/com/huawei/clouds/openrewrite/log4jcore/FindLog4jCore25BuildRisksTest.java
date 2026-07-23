package com.huawei.clouds.openrewrite.log4jcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FindLog4jCore25BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindLog4jCore25BuildRisks());
    }

    @Test
    void publishedTargetIsNoop() {
        cleanXml(UpgradeLog4jCoreDependencyTest.pom("2.25.5"));
    }

    @ParameterizedTest(name = "recommended source {0}")
    @ValueSource(strings = {"2.13.3", "2.17.0", "2.17.1", "2.17.2", "2.18.0",
            "2.19.0", "2.20.0", "2.23.1", "2.24.1", "2.25.3"})
    void recommendedRecipeUpgradesWorkbookSourcesWithoutOwnerMarker(String version) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(
                        "com.huawei.clouds.openrewrite.log4jcore.MigrateLog4jCoreTo2_25_5")),
                xml(UpgradeLog4jCoreDependencyTest.pom(version), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>2.25.5</version>");
                            assertNoMarker(after.printAll());
                        })));
    }

    @Test
    void targetPropertyResolvesWithoutMarker() {
        cleanXml(UpgradeLog4jCoreDependencyTest.project(
                "<properties><log4j.version>2.25.5</log4j.version></properties><dependencies>" +
                UpgradeLog4jCoreDependencyTest.dep("${log4j.version}") + "</dependencies>"));
    }

    @ParameterizedTest(name = "unresolved owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[2.13,3)", "[2.24,)", "2.+", "+", "latest.release"})
    void marksVersionlessExternalDynamicAndRangeOwners(String version) {
        String dependency = version.isEmpty() ? UpgradeLog4jCoreDependencyTest.depWithoutVersion() :
                UpgradeLog4jCoreDependencyTest.dep(version);
        markedXml(UpgradeLog4jCoreDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                FindLog4jCore25BuildRisks.OWNER);
    }

    @ParameterizedTest(name = "fixed outside {0}")
    @ValueSource(strings = {"2.13.2", "2.16.0", "2.21.0", "2.24.2", "2.25.4"})
    void marksFixedVersionsOutsideWorkbook(String version) {
        markedXml(UpgradeLog4jCoreDependencyTest.pom(version), FindLog4jCore25BuildRisks.OUTSIDE);
    }

    @ParameterizedTest(name = "higher fixed version {0}")
    @ValueSource(strings = {"2.25.6", "2.26.0", "3.0.0-beta1", "10.0.0"})
    void marksHigherFixedVersionsAsForbiddenDowngrades(String version) {
        markedXml(UpgradeLog4jCoreDependencyTest.pom(version),
                FindLog4jCore25BuildRisks.targetConflictMessage(version));
    }

    @Test
    void recommendedRecipeMarksSharedPropertyOwner() {
        String pom = UpgradeLog4jCoreDependencyTest.project(
                "<properties><v>2.13.3</v></properties><dependencies>" +
                UpgradeLog4jCoreDependencyTest.dep("${v}") +
                "</dependencies><build><finalName>${v}</finalName></build>");
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(
                        "com.huawei.clouds.openrewrite.log4jcore.MigrateLog4jCoreTo2_25_5")),
                xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindLog4jCore25BuildRisks.OWNER);
                    assertContains(after.printAll(), "<v>2.13.3</v>");
                })));
    }

    @Test
    void marksMavenClassifierAndNonJarVariants() {
        rewriteRun(
                markedXmlSpec(UpgradeLog4jCoreDependencyTest.project("<dependencies>" +
                        UpgradeLog4jCoreDependencyTest.dep("2.25.5", "<classifier>tests</classifier>") +
                        "</dependencies>"), "classified/pom.xml", FindLog4jCore25BuildRisks.VARIANT),
                markedXmlSpec(UpgradeLog4jCoreDependencyTest.project("<dependencies>" +
                        UpgradeLog4jCoreDependencyTest.dep("2.25.5", "<type>zip</type>") +
                        "</dependencies>"), "zip/pom.xml", FindLog4jCore25BuildRisks.VARIANT));
    }

    @ParameterizedTest(name = "Log4j family {0}")
    @ValueSource(strings = {"log4j-api", "log4j-slf4j2-impl", "log4j-jul", "log4j-layout-template-json"})
    void marksLog4jArtifactFamilySkew(String artifact) {
        markedXml(withCompanion("org.apache.logging.log4j", artifact, "2.20.0"),
                FindLog4jCore25BuildRisks.FAMILY);
    }

    @Test
    void alignedLog4jFamilyIsNoop() {
        cleanXml(withCompanion("org.apache.logging.log4j", "log4j-api", "2.25.5"));
    }

    @ParameterizedTest(name = "removed module {0}")
    @ValueSource(strings = {"log4j-flume-ng", "log4j-kubernetes", "log4j-mongodb3"})
    void marksRemovedModules(String artifact) {
        markedXml(withCompanion("org.apache.logging.log4j", artifact, "2.20.0"),
                FindLog4jCore25BuildRisks.REMOVED_MODULE);
    }

    @Test
    void marksLog4jToSlf4jRoutingLoopRisk() {
        markedXml(withCompanion("org.apache.logging.log4j", "log4j-to-slf4j", "2.25.5"),
                FindLog4jCore25BuildRisks.ROUTING_LOOP);
    }

    @ParameterizedTest(name = "unsupported Disruptor {0}")
    @ValueSource(strings = {"", "3.3.10", "5.0.0", "${disruptor.version}"})
    void marksUnsupportedOrUnresolvedDisruptor(String version) {
        markedXml(withCompanion("com.lmax", "disruptor", version), FindLog4jCore25BuildRisks.DISRUPTOR);
    }

    @ParameterizedTest(name = "supported Disruptor {0}")
    @ValueSource(strings = {"3.4.0", "3.4.4", "4.0.0", "4.1.0"})
    void supportedDisruptorRangeIsNoop(String version) {
        cleanXml(withCompanion("com.lmax", "disruptor", version));
    }

    @Test
    void marksExplicitJansiDependency() {
        markedXml(withCompanion("org.fusesource.jansi", "jansi", "1.18"), FindLog4jCore25BuildRisks.JANSI);
    }

    @ParameterizedTest(name = "Maven packaging {0}")
    @ValueSource(strings = {"maven-shade-plugin", "bnd-maven-plugin", "native-maven-plugin"})
    void marksPackagingIntegrationsThatMentionLog4j(String plugin) {
        String build = "<build><plugins><plugin><groupId>x</groupId><artifactId>" + plugin +
                "</artifactId><configuration><resource>META-INF/org/apache/logging/log4j/core/config/plugins/" +
                "Log4j2Plugins.dat</resource></configuration></plugin></plugins></build>";
        markedXml(UpgradeLog4jCoreDependencyTest.project("<dependencies>" +
                UpgradeLog4jCoreDependencyTest.dep("2.25.5") + "</dependencies>" + build),
                FindLog4jCore25BuildRisks.PACKAGING);
    }

    @Test
    void unrelatedPackagingConfigurationIsNoop() {
        String build = "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                "<configuration><relocation>com.example</relocation></configuration></plugin></plugins></build>";
        cleanXml(UpgradeLog4jCoreDependencyTest.project("<dependencies>" +
                UpgradeLog4jCoreDependencyTest.dep("2.25.5") + "</dependencies>" + build));
    }

    @Test
    void marksPackagingIntegrationInsideOwningProfile() {
        String profile = "<profiles><profile><id>native</id><dependencies>" +
                UpgradeLog4jCoreDependencyTest.dep("2.25.5") + "</dependencies><build><plugins><plugin>" +
                "<artifactId>native-maven-plugin</artifactId><configuration><resource>" +
                "Log4j2Plugins.dat</resource></configuration></plugin></plugins></build></profile></profiles>";
        markedXml(UpgradeLog4jCoreDependencyTest.project(profile), FindLog4jCore25BuildRisks.PACKAGING);
    }

    @Test
    void arbitraryNestedBuildElementIsNotTreatedAsMavenProjectBuild() {
        String nested = "<reporting><build><plugins><plugin><artifactId>native-maven-plugin</artifactId>" +
                "<configuration><resource>Log4j2Plugins.dat</resource></configuration></plugin></plugins></build>" +
                "</reporting>";
        cleanXml(UpgradeLog4jCoreDependencyTest.project("<dependencies>" +
                UpgradeLog4jCoreDependencyTest.dep("2.25.5") + "</dependencies>" + nested));
    }

    @Test
    void selectedProfileDoesNotInspectSiblingProfile() {
        String pom = UpgradeLog4jCoreDependencyTest.project("<profiles><profile><id>selected</id><dependencies>" +
                UpgradeLog4jCoreDependencyTest.dep("2.25.5") + "</dependencies></profile>" +
                "<profile><id>sibling</id><dependencies>" + dep("org.apache.logging.log4j", "log4j-api", "2.20.0") +
                "</dependencies></profile></profiles>");
        cleanXml(pom);
    }

    @Test
    void rootPrimaryInspectsProfileCompanions() {
        String pom = UpgradeLog4jCoreDependencyTest.project("<dependencies>" +
                UpgradeLog4jCoreDependencyTest.dep("2.25.5") + "</dependencies><profiles><profile><id>it</id>" +
                "<dependencies>" + dep("org.apache.logging.log4j", "log4j-api", "2.20.0") +
                "</dependencies></profile></profiles>");
        markedXml(pom, FindLog4jCore25BuildRisks.FAMILY);
    }

    @Test
    void marksGradleFixedDynamicVariantFamilyAndOptionalRuntimeRisks() {
        rewriteRun(
                markedGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.16.0' }",
                        FindLog4jCore25BuildRisks.OUTSIDE),
                markedGradle("def v='2.25.5'\ndependencies { implementation \"org.apache.logging.log4j:log4j-core:$v\" }",
                        FindLog4jCore25BuildRisks.OWNER),
                markedGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.25.5:tests' }",
                        FindLog4jCore25BuildRisks.VARIANT),
                markedGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.25.5'; runtimeOnly 'org.apache.logging.log4j:log4j-api:2.20.0' }",
                        FindLog4jCore25BuildRisks.FAMILY),
                markedGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.25.5'; runtimeOnly 'com.lmax:disruptor:5.0.0' }",
                        FindLog4jCore25BuildRisks.DISRUPTOR),
                markedGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.25.5'; runtimeOnly 'org.fusesource.jansi:jansi:1.18' }",
                        FindLog4jCore25BuildRisks.JANSI));
    }

    @Test
    void marksGroovyMapAndMapLiteralOwnership() {
        rewriteRun(
                markedGradle("dependencies { implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version: '2.16.0' }",
                        FindLog4jCore25BuildRisks.OUTSIDE),
                markedGradle("dependencies { implementation([group: 'org.apache.logging.log4j', name: 'log4j-core']) }",
                        FindLog4jCore25BuildRisks.OWNER),
                markedGradle("dependencies { implementation group: 'org.apache.logging.log4j', name: 'log4j-core', version: '2.25.5', classifier: 'tests' }",
                        FindLog4jCore25BuildRisks.VARIANT));
    }

    @Test
    void marksKotlinFixedDynamicAndFamilyRisks() {
        rewriteRun(
                buildGradleKts("dependencies { implementation(\"org.apache.logging.log4j:log4j-core:2.16.0\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindLog4jCore25BuildRisks.OUTSIDE))),
                buildGradleKts("val v=\"2.25.5\"\ndependencies { implementation(\"org.apache.logging.log4j:log4j-core:$v\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindLog4jCore25BuildRisks.OWNER))),
                buildGradleKts("dependencies { implementation(\"org.apache.logging.log4j:log4j-core:2.25.5\"); " +
                        "runtimeOnly(\"org.apache.logging.log4j:log4j-api:2.20.0\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindLog4jCore25BuildRisks.FAMILY))));
    }

    @Test
    void marksHigherGradleVersionsAsForbiddenDowngrades() {
        rewriteRun(
                markedGradle(
                        "dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.26.0' }",
                        FindLog4jCore25BuildRisks.targetConflictMessage("2.26.0")),
                buildGradleKts(
                        "dependencies { implementation(\"org.apache.logging.log4j:log4j-core:3.0.0-beta1\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(),
                                        FindLog4jCore25BuildRisks.targetConflictMessage("3.0.0-beta1")))));
    }

    @Test
    void marksTopLevelShadowRelocation() {
        rewriteRun(markedGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.25.5' }\n" +
                "shadowJar { relocate 'org.apache.logging.log4j', 'internal.logging' }",
                FindLog4jCore25BuildRisks.PACKAGING));
    }

    @Test
    void noPrimaryNestedWrongCoordinatesAndGeneratedBuildsAreNoop() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-api:2.20.0' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("subprojects { dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.16.0' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'example:log4j-core:2.16.0' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("def v='2.25.5'\ndependencies { implementation \"xorg.apache.logging.log4j:log4j-core:$v\" }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(UpgradeLog4jCoreDependencyTest.pom("2.16.0"), source -> source.path("target/generated/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeLog4jCoreDependencyTest.pom("2.16.0"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindLog4jCore25BuildRisks.OUTSIDE, 1))));
    }

    private static String withCompanion(String group, String artifact, String version) {
        return UpgradeLog4jCoreDependencyTest.project("<dependencies>" +
                UpgradeLog4jCoreDependencyTest.dep("2.25.5") + dep(group, artifact, version) + "</dependencies>");
    }

    private static String dep(String group, String artifact, String version) {
        String v = version.isEmpty() ? "" : "<version>" + version + "</version>";
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact +
               "</artifactId>" + v + "</dependency>";
    }

    private void markedXml(String pom, String message) {
        rewriteRun(markedXmlSpec(pom, "pom.xml", message));
    }

    private void cleanXml(String pom) {
        rewriteRun(xml(pom, source -> source.path("pom.xml")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    private static SourceSpecs markedXmlSpec(String pom, String path, String message) {
        return xml(pom, source -> source.path(path).after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message)));
    }

    private static SourceSpecs markedGradle(String gradle, String message) {
        return buildGradle(gradle, source -> source.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message)));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.log4jcore").build();
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
