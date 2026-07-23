package com.huawei.clouds.openrewrite.bson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FindBson5BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindBson5BuildRisks());
    }

    @Test
    void publishedTargetIsNoop() {
        rewriteRun(xml(UpgradeBsonDependencyTest.pom("5.4.0"), source ->
                source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "recommended source {0}")
    @ValueSource(strings = {"3.12.14", "4.7.2"})
    void recommendedRecipeUpgradesSourcesWithoutBuildMarker(String version) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(
                        "com.huawei.clouds.openrewrite.bson.MigrateBsonTo5_4_0")),
                xml(UpgradeBsonDependencyTest.pom(version), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "<version>5.4.0</version>");
                            assertNoMarker(after.printAll());
                        })));
    }

    @Test
    void targetPropertyResolvesWithoutMarker() {
        rewriteRun(xml(UpgradeBsonDependencyTest.project(
                "<properties><bson.version>5.4.0</bson.version></properties><dependencies>" +
                UpgradeBsonDependencyTest.dep("${bson.version}") + "</dependencies>"),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "unresolved owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[3,6)", "[4.0,)", "4.+", "+", "latest.release"})
    void marksVersionlessExternalDynamicAndRangeOwners(String version) {
        String dependency = version.isEmpty() ? UpgradeBsonDependencyTest.depWithoutVersion() :
                UpgradeBsonDependencyTest.dep(version);
        markedXml(UpgradeBsonDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                FindBson5BuildRisks.OWNER);
    }

    @ParameterizedTest(name = "fixed outside {0}")
    @ValueSource(strings = {"3.12.13", "4.0.0", "4.11.0", "5.0.0", "5.4.1"})
    void marksFixedVersionsOutsideWorkbook(String version) {
        markedXml(UpgradeBsonDependencyTest.pom(version), FindBson5BuildRisks.OUTSIDE);
    }

    @Test
    void recommendedRecipeMarksSharedPropertyOwner() {
        String pom = UpgradeBsonDependencyTest.project(
                "<properties><v>3.12.14</v></properties><dependencies>" +
                UpgradeBsonDependencyTest.dep("${v}") +
                "</dependencies><build><finalName>${v}</finalName></build>");
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(
                        "com.huawei.clouds.openrewrite.bson.MigrateBsonTo5_4_0")),
                xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), FindBson5BuildRisks.OWNER);
                    assertContains(after.printAll(), "<v>3.12.14</v>");
                })));
    }

    @Test
    void marksMavenClassifierAndNonJarVariants() {
        rewriteRun(
                xml(UpgradeBsonDependencyTest.project("<dependencies>" +
                        UpgradeBsonDependencyTest.dep("5.4.0", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("classified/pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBson5BuildRisks.VARIANT))),
                xml(UpgradeBsonDependencyTest.project("<dependencies>" +
                        UpgradeBsonDependencyTest.dep("5.4.0", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("zip/pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBson5BuildRisks.VARIANT))));
    }

    @Test
    void mavenVariantDoesNotOwnFamilyOrPackagingCompanions() {
        String family = "<dependency><groupId>org.mongodb</groupId><artifactId>mongodb-driver-core</artifactId>" +
                        "<version>4.7.2</version></dependency>";
        String packaging = "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                           "<configuration><relocation>org.bson</relocation></configuration></plugin></plugins></build>";
        String pom = UpgradeBsonDependencyTest.project("<dependencies>" +
                UpgradeBsonDependencyTest.dep("5.4.0", "<classifier>tests</classifier>") + family +
                "</dependencies>" + packaging);
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertContains(printed, FindBson5BuildRisks.VARIANT);
            assertFalse(printed.contains(FindBson5BuildRisks.FAMILY_SKEW), printed);
            assertFalse(printed.contains(FindBson5BuildRisks.PACKAGING), printed);
        })));
    }

    @ParameterizedTest(name = "Mongo family {0}")
    @org.junit.jupiter.params.provider.MethodSource("mongoFamily")
    void marksMongoArtifactFamilySkew(String artifact) {
        String companion = "<dependency><groupId>org.mongodb</groupId><artifactId>" + artifact +
                           "</artifactId><version>4.7.2</version></dependency>";
        markedXml(UpgradeBsonDependencyTest.project("<dependencies>" +
                UpgradeBsonDependencyTest.dep("5.4.0") + companion + "</dependencies>"),
                FindBson5BuildRisks.FAMILY_SKEW);
    }

    static Stream<String> mongoFamily() {
        return Stream.of("mongodb-driver-core", "mongodb-driver-sync", "mongodb-driver-reactivestreams",
                "mongodb-driver-legacy", "bson-record-codec", "bson-kotlinx", "bson-kotlinx-serialization");
    }

    @ParameterizedTest(name = "legacy uber jar {0}")
    @ValueSource(strings = {"mongo-java-driver", "mongodb-driver"})
    void marksLegacyUberJars(String artifact) {
        String uber = "<dependency><groupId>org.mongodb</groupId><artifactId>" + artifact +
                      "</artifactId><version>3.12.14</version></dependency>";
        markedXml(UpgradeBsonDependencyTest.project("<dependencies>" +
                UpgradeBsonDependencyTest.dep("5.4.0") + uber + "</dependencies>"),
                FindBson5BuildRisks.LEGACY_UBER);
    }

    @Test
    void profilePrimaryDoesNotInspectSiblingFamily() {
        String pom = UpgradeBsonDependencyTest.project("<profiles>" +
                "<profile><id>selected</id><dependencies>" + UpgradeBsonDependencyTest.dep("5.4.0") +
                "</dependencies></profile>" +
                "<profile><id>sibling</id><dependencies><dependency><groupId>org.mongodb</groupId>" +
                "<artifactId>mongodb-driver-core</artifactId><version>4.7.2</version></dependency>" +
                "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void rootPrimaryInspectsProfileFamily() {
        String pom = UpgradeBsonDependencyTest.project("<dependencies>" +
                UpgradeBsonDependencyTest.dep("5.4.0") + "</dependencies><profiles><profile><id>it</id>" +
                "<dependencies><dependency><groupId>org.mongodb</groupId><artifactId>bson-record-codec</artifactId>" +
                "<version>4.7.2</version></dependency></dependencies></profile></profiles>");
        markedXml(pom, FindBson5BuildRisks.FAMILY_SKEW);
    }

    @ParameterizedTest(name = "Maven packaging plugin {0}")
    @ValueSource(strings = {"maven-shade-plugin", "bnd-maven-plugin", "native-maven-plugin"})
    void marksMavenPackagingThatMentionsBson(String artifact) {
        String plugin = "<build><plugins><plugin><groupId>x</groupId><artifactId>" + artifact +
                "</artifactId><configuration><packages>org.bson.**</packages></configuration></plugin></plugins></build>";
        markedXml(UpgradeBsonDependencyTest.project("<dependencies>" +
                UpgradeBsonDependencyTest.dep("5.4.0") + "</dependencies>" + plugin),
                FindBson5BuildRisks.PACKAGING);
    }

    @Test
    void unrelatedPackagingPluginIsNoop() {
        String plugin = "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                "<configuration><packages>com.example.**</packages></configuration></plugin></plugins></build>";
        rewriteRun(xml(UpgradeBsonDependencyTest.project("<dependencies>" +
                UpgradeBsonDependencyTest.dep("5.4.0") + "</dependencies>" + plugin),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void packagingPluginLookalikeIsNoop() {
        String plugin = "<build><plugins><plugin><artifactId>company-shade-helper</artifactId>" +
                "<configuration><packages>org.bson.**</packages></configuration></plugin></plugins></build>";
        rewriteRun(xml(UpgradeBsonDependencyTest.project("<dependencies>" +
                UpgradeBsonDependencyTest.dep("5.4.0") + "</dependencies>" + plugin),
                source -> source.path("pom.xml").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksGradleOutsideDynamicVariantsFamilyAndUber() {
        rewriteRun(
                markedGradle("dependencies { implementation 'org.mongodb:bson:4.0.0' }",
                        FindBson5BuildRisks.OUTSIDE),
                markedGradle("def v='5.4.0'\ndependencies { implementation \"org.mongodb:bson:$v\" }",
                        FindBson5BuildRisks.OWNER),
                markedGradle("dependencies { implementation 'org.mongodb:bson:5.4.0:tests' }",
                        FindBson5BuildRisks.VARIANT),
                markedGradle("dependencies { implementation 'org.mongodb:bson:5.4.0'; runtimeOnly 'org.mongodb:mongodb-driver-core:4.7.2' }",
                        FindBson5BuildRisks.FAMILY_SKEW),
                markedGradle("dependencies { implementation 'org.mongodb:bson:5.4.0'; runtimeOnly 'org.mongodb:mongo-java-driver:3.12.14' }",
                        FindBson5BuildRisks.LEGACY_UBER));
    }

    @Test
    void marksGroovyMapAndMapLiteralOwnership() {
        rewriteRun(
                markedGradle("dependencies { implementation group: 'org.mongodb', name: 'bson', version: '4.0.0' }",
                        FindBson5BuildRisks.OUTSIDE),
                markedGradle("dependencies { implementation([group: 'org.mongodb', name: 'bson']) }",
                        FindBson5BuildRisks.OWNER));
    }

    @Test
    void marksKotlinFixedAndDynamicOwnership() {
        rewriteRun(
                buildGradleKts("dependencies { implementation(\"org.mongodb:bson:4.0.0\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBson5BuildRisks.OUTSIDE))),
                buildGradleKts("val v=\"5.4.0\"\ndependencies { implementation(\"org.mongodb:bson:$v\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindBson5BuildRisks.OWNER))));
    }

    @Test
    void dynamicGradleVariantDoesNotOwnCompanions() {
        String gradle = "def v='5.4.0'\ndependencies {\n" +
                "  implementation \"org.mongodb:bson:$v:tests\"\n" +
                "  runtimeOnly 'org.mongodb:mongodb-driver-core:4.7.2'\n" +
                "}\nshadowJar { relocate 'org.bson', 'internal.bson' }";
        rewriteRun(buildGradle(gradle, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertContains(printed, FindBson5BuildRisks.VARIANT);
            assertFalse(printed.contains(FindBson5BuildRisks.FAMILY_SKEW), printed);
            assertFalse(printed.contains(FindBson5BuildRisks.PACKAGING), printed);
        })));
    }

    @Test
    void dynamicStandardGradlePrimaryOwnsCompanions() {
        String gradle = "def v='5.4.0'\ndependencies {\n" +
                "  implementation \"org.mongodb:bson:$v\"\n" +
                "  runtimeOnly 'org.mongodb:mongodb-driver-core:4.7.2'\n" +
                "}\nshadowJar { relocate 'org.bson', 'internal.bson' }";
        rewriteRun(buildGradle(gradle, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertContains(printed, FindBson5BuildRisks.OWNER);
            assertContains(printed, FindBson5BuildRisks.FAMILY_SKEW);
            assertContains(printed, FindBson5BuildRisks.PACKAGING);
        })));
    }

    @Test
    void dynamicGradleLookalikeIsNoop() {
        rewriteRun(
                buildGradle("def v='5.4.0'\ndependencies { implementation \"xorg.mongodb:bson:$v\" }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradleKts("val v=\"5.4.0\"\ndependencies { implementation(\"xorg.mongodb:bson:$v\") }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void dynamicKotlinVariantDoesNotOwnCompanions() {
        String gradle = "val v=\"5.4.0\"\ndependencies {\n" +
                "  implementation(\"org.mongodb:bson:$v:tests\")\n" +
                "  runtimeOnly(\"org.mongodb:mongodb-driver-core:4.7.2\")\n" +
                "}";
        rewriteRun(buildGradleKts(gradle, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertContains(printed, FindBson5BuildRisks.VARIANT);
            assertFalse(printed.contains(FindBson5BuildRisks.FAMILY_SKEW), printed);
        })));
    }

    @Test
    void marksTopLevelShadowRelocation() {
        rewriteRun(markedGradle("dependencies { implementation 'org.mongodb:bson:5.4.0' }\n" +
                "shadowJar { relocate 'org.bson', 'internal.shaded.bson' }", FindBson5BuildRisks.PACKAGING));
    }

    @Test
    void nestedGradleWrongCoordinatesNoPrimaryAndGeneratedAreNoop() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'org.mongodb:bson:4.0.0' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'example:bson:4.0.0' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'org.mongodb:mongodb-driver-core:4.7.2' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(UpgradeBsonDependencyTest.pom("4.0.0"), source -> source.path("target/generated/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void rootGradlePrimaryDoesNotOwnNestedProjectCompanions() {
        String gradle = "dependencies { implementation 'org.mongodb:bson:5.4.0' }\n" +
                "project(':child') {\n" +
                "  dependencies { runtimeOnly 'org.mongodb:mongodb-driver-core:4.7.2' }\n" +
                "  shadowJar { relocate 'org.bson', 'nested.bson' }\n" +
                "}";
        rewriteRun(buildGradle(gradle, source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeBsonDependencyTest.pom("4.0.0"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindBson5BuildRisks.OUTSIDE, 1))));
    }

    private void markedXml(String pom, String message) {
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    private static org.openrewrite.test.SourceSpecs markedGradle(String gradle, String message) {
        return buildGradle(gradle, source -> source.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message)));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.bson").build();
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
