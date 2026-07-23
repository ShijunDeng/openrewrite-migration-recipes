package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FindBcProv184BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindBcProv184BuildRisks());
    }

    @ParameterizedTest(name = "selected or target version {0}")
    @ValueSource(strings = {"1.75", "1.78", "1.78.1", "1.79", "1.80", "1.81", "1.84"})
    void selectedAndTargetVersionsAreNotBuildRisks(String version) {
        cleanXml(pom(version));
    }

    @ParameterizedTest(name = "unresolved owner {0}")
    @ValueSource(strings = {"", "${external.version}", "[1.75,1.84)", "[1.80,)", "1.+", "+", "latest.release"})
    void marksVersionlessExternalDynamicAndRangeOwners(String version) {
        markedXml(project("<dependencies>" + (version.isEmpty() ? target(null, "") : target(version, "")) +
                          "</dependencies>"), FindBcProv184BuildRisks.OWNER);
    }

    @ParameterizedTest(name = "outside version {0}")
    @ValueSource(strings = {"1.74", "1.76", "1.77", "1.82", "1.83", "1.85", "2.0.0"})
    void marksFixedVersionsOutsideSelectedSet(String version) {
        markedXml(pom(version), FindBcProv184BuildRisks.OUTSIDE);
    }

    @Test
    void marksSharedDuplicateAndProfileShadowedPropertyOwners() {
        rewriteRun(
                markedXmlSpec(project("<properties><bc.version>1.79</bc.version></properties><dependencies>" +
                        target("${bc.version}", "") + dep("org.bouncycastle", "bcutil-jdk18on", "${bc.version}") +
                        "</dependencies>"), "shared/pom.xml", FindBcProv184BuildRisks.OWNER),
                markedXmlSpec(project("<properties><bc.version>1.79</bc.version><bc.version>1.79</bc.version></properties>" +
                        "<dependencies>" + target("${bc.version}", "") + "</dependencies>"),
                        "duplicate/pom.xml", FindBcProv184BuildRisks.OWNER),
                markedXmlSpec(project("<properties><bc.version>1.79</bc.version></properties><dependencies>" +
                        target("${bc.version}", "") + "</dependencies><profiles><profile><id>p</id><properties>" +
                        "<bc.version>1.80</bc.version></properties></profile></profiles>"),
                        "shadow/pom.xml", FindBcProv184BuildRisks.OWNER));
    }

    @Test
    void exclusiveLiteralPropertyResolvesWithoutMarker() {
        cleanXml(project("<properties><bc.version>1.84</bc.version></properties><dependencies>" +
                         target("${bc.version}", "") + "</dependencies>"));
    }

    @Test
    void marksMavenClassifierAndNonJarVariants() {
        rewriteRun(
                markedXmlSpec(project("<dependencies>" + target("1.84", "<classifier>sources</classifier>") +
                        "</dependencies>"), "classifier/pom.xml", FindBcProv184BuildRisks.VARIANT),
                markedXmlSpec(project("<dependencies>" + target("1.84", "<type>zip</type>") +
                        "</dependencies>"), "zip/pom.xml", FindBcProv184BuildRisks.VARIANT));
    }

    @ParameterizedTest(name = "family skew {0}")
    @ValueSource(strings = {"bc-bom", "bcutil-jdk18on", "bcpkix-jdk18on", "bcpg-jdk18on", "bctls-jdk18on", "bcmail-jdk18on"})
    void marksJdk18onFamilySkew(String artifact) {
        markedXml(withCompanion(artifact, "1.81"), FindBcProv184BuildRisks.FAMILY);
    }

    @Test
    void alignedJdk18onFamilyIsNoop() {
        cleanXml(withCompanion("bcutil-jdk18on", "1.84"));
    }

    @Test
    void bcpkix1811UserTargetIsFamilyMarkOnly() {
        markedXml(withCompanion("bcpkix-jdk18on", "1.81.1"), FindBcProv184BuildRisks.FAMILY);
    }

    @ParameterizedTest(name = "provider collision {0}")
    @ValueSource(strings = {"bcprov-jdk14", "bcprov-jdk15", "bcprov-jdk16", "bcprov-jdk15on",
            "bcprov-jdk15to18", "bcprov-ext-jdk14", "bcprov-ext-jdk15on", "bcprov-ext-jdk15to18",
            "bcprov-ext-jdk18on", "bcprov-lts8on", "bc-fips"})
    void marksProviderLineageCollisions(String artifact) {
        markedXml(withCompanion(artifact, "1.81"), FindBcProv184BuildRisks.PROVIDER_COLLISION);
    }

    @ParameterizedTest(name = "signed-provider packaging {0}")
    @ValueSource(strings = {"maven-shade-plugin", "bnd-maven-plugin", "native-maven-plugin"})
    void marksPackagingThatMentionsBouncyCastle(String plugin) {
        String build = "<build><plugins><plugin><groupId>x</groupId><artifactId>" + plugin +
                "</artifactId><configuration><relocation>org.bouncycastle</relocation>" +
                "</configuration></plugin></plugins></build>";
        markedXml(project("<dependencies>" + target("1.84", "") + "</dependencies>" + build),
                FindBcProv184BuildRisks.PACKAGING);
    }

    @Test
    void marksDirectProfileBuildPackagingButNotArbitraryNestedBuild() {
        String profileBuild = "<profiles><profile><id>crypto</id><dependencies>" + target("1.84", "") +
                "</dependencies><build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                "<configuration><relocation>org.bouncycastle</relocation></configuration>" +
                "</plugin></plugins></build></profile></profiles>";
        markedXml(project(profileBuild), FindBcProv184BuildRisks.PACKAGING);

        String nested = "<dependencies>" + target("1.84", "") + "</dependencies><configuration>" +
                "<build><plugins><plugin><artifactId>maven-shade-plugin</artifactId>" +
                "<configuration><relocation>org.bouncycastle</relocation></configuration>" +
                "</plugin></plugins></build></configuration>";
        cleanXml(project(nested));
    }

    @Test
    void unrelatedPackagingIsNoop() {
        cleanXml(project("<dependencies>" + target("1.84", "") + "</dependencies><build><plugins><plugin>" +
                "<artifactId>maven-shade-plugin</artifactId><configuration><relocation>com.example</relocation>" +
                "</configuration></plugin></plugins></build>"));
    }

    @Test
    void selectedProfileDoesNotInspectSiblingButRootInspectsProfiles() {
        cleanXml(project("<profiles><profile><id>selected</id><dependencies>" + target("1.84", "") +
                "</dependencies></profile><profile><id>sibling</id><dependencies>" +
                dep("org.bouncycastle", "bcutil-jdk18on", "1.81") +
                "</dependencies></profile></profiles>"));
        markedXml(project("<dependencies>" + target("1.84", "") + "</dependencies><profiles><profile><id>it</id>" +
                "<dependencies>" + dep("org.bouncycastle", "bcutil-jdk18on", "1.81") +
                "</dependencies></profile></profiles>"), FindBcProv184BuildRisks.FAMILY);
    }

    @Test
    void marksGradleOwnerVariantFamilyCollisionCatalogBomAndPackaging() {
        rewriteRun(
                markedGradle("dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.83' }",
                        FindBcProv184BuildRisks.OUTSIDE),
                markedGradle("def v='1.84'\ndependencies { implementation \"org.bouncycastle:bcprov-jdk18on:$v\" }",
                        FindBcProv184BuildRisks.OWNER),
                markedGradle("dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.84:sources' }",
                        FindBcProv184BuildRisks.VARIANT),
                markedGradle("dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.84'; runtimeOnly 'org.bouncycastle:bcutil-jdk18on:1.81' }",
                        FindBcProv184BuildRisks.FAMILY),
                markedGradle("dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.84'; runtimeOnly 'org.bouncycastle:bcprov-jdk15on:1.70' }",
                        FindBcProv184BuildRisks.PROVIDER_COLLISION),
                markedGradle("dependencies { implementation libs.bouncycastle.bcprov.jdk18on }",
                        FindBcProv184BuildRisks.OWNER),
                markedGradle("dependencies { implementation 'org.bouncycastle:bcprov-jdk18on'; implementation platform('org.bouncycastle:bc-bom:1.81') }",
                        FindBcProv184BuildRisks.FAMILY),
                markedGradle("dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.84' }\n" +
                        "shadowJar { relocate 'org.bouncycastle', 'internal.bc' }", FindBcProv184BuildRisks.PACKAGING));
    }

    @Test
    void marksGroovyMapsAndKotlinDynamicCatalogAndFamily() {
        rewriteRun(
                markedGradle("dependencies { implementation group: 'org.bouncycastle', name: 'bcprov-jdk18on', version: '1.83' }",
                        FindBcProv184BuildRisks.OUTSIDE),
                markedGradle("dependencies { implementation([group: 'org.bouncycastle', name: 'bcprov-jdk18on']) }",
                        FindBcProv184BuildRisks.OWNER),
                markedGradle("dependencies { implementation group: 'org.bouncycastle', name: 'bcprov-jdk18on', version: '1.84', classifier: 'tests' }",
                        FindBcProv184BuildRisks.VARIANT),
                markedKotlin("val v=\"1.84\"\ndependencies { implementation(\"org.bouncycastle:bcprov-jdk18on:$v\") }",
                        FindBcProv184BuildRisks.OWNER),
                markedKotlin("dependencies { implementation(libs.bouncycastle.bcprov.jdk18on) }",
                        FindBcProv184BuildRisks.OWNER),
                markedKotlin("dependencies { implementation(\"org.bouncycastle:bcprov-jdk18on:1.84\"); runtimeOnly(\"org.bouncycastle:bcpkix-jdk18on:1.81\") }",
                        FindBcProv184BuildRisks.FAMILY));
    }

    @Test
    void dynamicCoordinatesRequireExactLeadingCoordinatePrefix() {
        rewriteRun(
                markedGradle("def v='1.84'\ndependencies { implementation \"  org.bouncycastle:bcprov-jdk18on:$v\" }",
                        FindBcProv184BuildRisks.OWNER),
                buildGradle("def v='1.84'\ndependencies { implementation \"xorg.bouncycastle:bcprov-jdk18on:$v\" }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradleKts("val v=\"1.84\"\ndependencies { implementation(\"xorg.bouncycastle:bcprov-jdk18on:$v\") }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void noPrimaryNestedLookalikesAndGeneratedBuildsAreNoop() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.bouncycastle:bcutil-jdk18on:1.81' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("subprojects { dependencies { implementation 'org.bouncycastle:bcprov-jdk18on:1.83' } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                buildGradle("dependencies { implementation 'example:bcprov-jdk18on:1.83' }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                xml(pom("1.83"), source -> source.path("target/generated/pom.xml")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("1.83"), source -> source.path("pom.xml").after(actual -> actual)
                        .afterRecipe(after -> assertCount(after.printAll(), FindBcProv184BuildRisks.OUTSIDE, 1))));
    }

    private static String withCompanion(String artifact, String version) {
        return project("<dependencies>" + target("1.84", "") +
                       dep("org.bouncycastle", artifact, version) + "</dependencies>");
    }

    private static String pom(String version) {
        return project("<dependencies>" + target(version, "") + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId>" +
               "<version>1</version>" + body + "</project>";
    }

    private static String target(String version, String metadata) {
        return dep("org.bouncycastle", "bcprov-jdk18on", version, metadata);
    }

    private static String dep(String group, String artifact, String version) {
        return dep(group, artifact, version, "");
    }

    private static String dep(String group, String artifact, String version, String metadata) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + metadata + "</dependency>";
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

    private static SourceSpecs markedKotlin(String gradle, String message) {
        return buildGradleKts(gradle, source -> source.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message)));
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
