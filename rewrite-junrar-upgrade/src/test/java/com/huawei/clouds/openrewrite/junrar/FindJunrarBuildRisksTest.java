package com.huawei.clouds.openrewrite.junrar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

class FindJunrarBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                "com.huawei.clouds.openrewrite.junrar.FindJunrar7_5_10BuildRisks"));
    }

    @ParameterizedTest(name = "higher Maven version {0} gets exact marker")
    @ValueSource(strings = {
            "7.5.11", "7.6.0", "7.6.1", "7.10.0", "8.0.0", "8.0.0-beta1",
            "9.0", "10.0.0", "100.200.300", "999999999999999999999.0"
    })
    void marksEveryHigherVersionWithoutChangingIt(String version) {
        rewriteRun(markedPom(version, FindJunrar7510BuildRisks.DOWNGRADE_FORBIDDEN,
                "higher-" + version + "/pom.xml", after -> {
                    assertTrue(after.contains("<version>" + version + "</version>"), after);
                    assertTrue(after.contains(JunrarSupport.TARGET_CONFLICT), after);
                    assertFalse(after.contains("<version>7.5.10</version>"), after);
                }));
    }

    @ParameterizedTest(name = "outside whitelist {0}")
    @ValueSource(strings = {
            "1.0", "6.0.0", "7.0.0", "7.4.1", "7.5.0", "7.5.4", "7.5.6",
            "7.5.7", "7.5.9", "7.5.10-rc1", "7.5.10.Final", "7.5"
    })
    void leavesFixedVersionsOutsideApprovedPathCompletelyUnmarked(String version) {
        rewriteRun(xml(pom(version), source -> source.path("outside-" + version + "/pom.xml")));
    }

    @ParameterizedTest(name = "source {0} has only a non-printing project marker")
    @ValueSource(strings = {"7.5.5", "7.5.8"})
    void selectedVersionsHaveNoPrintedBuildMarker(String version) {
        rewriteRun(spec -> spec.expectedCyclesThatMakeChanges(1),
                xml(pom(version), source -> source.path(version + "/pom.xml")));
    }

    @Test
    void targetVersionIsACompleteNoop() {
        rewriteRun(xml(pom("7.5.10"), source -> source.path("target/pom.xml")));
    }

    @ParameterizedTest(name = "qualifier higher version {0} is never downgraded")
    @ValueSource(strings = {"7.5.10-sp1", "7.5.10-zzz1", "7.5.11-rc1", "7.5.10.1"})
    void officialComparatorPreservesHigherQualifierVersions(String version) {
        rewriteRun(markedPom(version, FindJunrar7510BuildRisks.DOWNGRADE_FORBIDDEN,
                "qualified-higher-" + version + "/pom.xml", ignored -> {
                }));
    }

    @ParameterizedTest(name = "pre-release/equivalent qualifier {0} is outside, not higher")
    @ValueSource(strings = {"7.5.10-alpha1", "7.5.10-beta1", "7.5.10-rc1",
            "7.5.10-SNAPSHOT", "7.5.10.Final", "7.5.10.GA"})
    void officialComparatorLeavesPreReleaseOrEquivalentQualifierUnmarked(String version) {
        rewriteRun(xml(pom(version),
                source -> source.path("qualified-outside-" + version + "/pom.xml")));
    }

    @ParameterizedTest(name = "unresolved Maven owner {0}")
    @MethodSource("unresolvedOwners")
    void marksUnresolvedMavenOwners(String label, String version) {
        rewriteRun(markedPom(version, FindJunrar7510BuildRisks.OWNER,
                label + "/pom.xml", ignored -> {
                }));
    }

    static Stream<Arguments> unresolvedOwners() {
        return Stream.of(
                Arguments.of("missing", "${missing}"),
                Arguments.of("range", "[7.5.5,7.5.10]"),
                Arguments.of("open-range", "[7.5.5,)"),
                Arguments.of("latest", "LATEST"),
                Arguments.of("release", "RELEASE"),
                Arguments.of("wildcard", "7.+"),
                Arguments.of("revision", "${revision}${changelist}"),
                Arguments.of("timestamp", "7.5.10-${buildNumber}")
        );
    }

    @Test
    void marksVersionlessOwner() {
        rewriteRun(xml(project("<dependencies>" + target(null, "") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindJunrar7510BuildRisks.OWNER),
                                after.printAll()))));
    }

    @ParameterizedTest(name = "Maven variant {0}")
    @MethodSource("mavenVariants")
    void marksMavenVariants(String label, String metadata) {
        rewriteRun(xml(project("<dependencies>" + target("7.5.5", metadata) + "</dependencies>"),
                source -> source.path(label + "/pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindJunrar7510BuildRisks.VARIANT),
                                after.printAll()))));
    }

    static Stream<Arguments> mavenVariants() {
        return Stream.of(
                Arguments.of("sources", "<classifier>sources</classifier>"),
                Arguments.of("tests", "<classifier>tests</classifier>"),
                Arguments.of("zip", "<type>zip</type>"),
                Arguments.of("test-jar", "<type>test-jar</type>"),
                Arguments.of("pom", "<type>pom</type>")
        );
    }

    @Test
    void marksSharedDuplicateAndShadowedPropertyOwners() {
        rewriteRun(
                markedXml(project("<properties><v>7.5.5</v></properties><dependencies>" +
                                target("${v}", "") + dependency("example", "other", "${v}") +
                                "</dependencies>"), "shared/pom.xml", FindJunrar7510BuildRisks.OWNER),
                markedXml(project("<properties><v>7.5.8</v><v>7.5.8</v></properties><dependencies>" +
                                target("${v}", "") + "</dependencies>"),
                        "duplicate/pom.xml", FindJunrar7510BuildRisks.OWNER),
                markedXml(project("<properties><v>7.5.5</v></properties><dependencies>" +
                                target("${v}", "") + "</dependencies><profiles><profile><id>rar</id>" +
                                "<properties><v>7.5.8</v></properties></profile></profiles>"),
                        "shadow/pom.xml", FindJunrar7510BuildRisks.OWNER));
    }

    @Test
    void exclusiveSelectedAndTargetPropertiesAreClean() {
        rewriteRun(spec -> spec.expectedCyclesThatMakeChanges(1),
                xml(project("<properties><v>7.5.5</v></properties><dependencies>" +
                        target("${v}", "") + target("${v}", "") + "</dependencies>"),
                        source -> source.path("selected/pom.xml")),
                xml(project("<profiles><profile><id>rar</id><properties><v>7.5.10</v></properties>" +
                        "<dependencies>" + target("${v}", "") +
                        "</dependencies></profile></profiles>"), source -> source.path("target/pom.xml")));
    }

    @ParameterizedTest(name = "shared non-source property {0} is a complete noop")
    @ValueSource(strings = {"7.5.9", "7.5.10", "7.5.10-rc1"})
    void sharedTargetAndOffListPropertiesAreNotMisreportedAsOwnerRisks(String version) {
        rewriteRun(xml(project("<properties><v>" + version + "</v></properties><dependencies>" +
                target("${v}", "") + dependency("example", "other", "${v}") +
                "</dependencies>"), source -> source.path(version + "/pom.xml")));
    }

    @Test
    void sharedFuturePropertyStillGetsOnlyExactNoDowngradeMarker() {
        rewriteRun(xml(project("<properties><v>8.0.0</v></properties><dependencies>" +
                target("${v}", "") + dependency("example", "other", "${v}") +
                "</dependencies>"), source -> source.path("future-property/pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(JunrarSupport.TARGET_CONFLICT), printed);
                    assertFalse(printed.contains(FindJunrar7510BuildRisks.OWNER), printed);
                    assertFalse(printed.contains(JunrarSupport.TARGET_CONFLICT + "："), printed);
                })));
    }

    @ParameterizedTest(name = "SLF4J boundary {0}")
    @MethodSource("slf4jCompanions")
    void marksSlf4j17AndBindingBoundaries(String label, String dependency) {
        rewriteRun(markedXml(project("<dependencies>" + target("7.5.5", "") + dependency +
                        "</dependencies>"), label + "/pom.xml", FindJunrar7510BuildRisks.SLF4J));
    }

    static Stream<Arguments> slf4jCompanions() {
        return Stream.of(
                Arguments.of("api-1", dependency("org.slf4j", "slf4j-api", "1.7.36")),
                Arguments.of("api-unknown", dependency("org.slf4j", "slf4j-api", "${slf4j.version}")),
                Arguments.of("simple-12", dependency("org.slf4j", "slf4j-simple", "1.7.36")),
                Arguments.of("log4j12", dependency("org.slf4j", "slf4j-log4j12", "1.7.36")),
                Arguments.of("jdk14", dependency("org.slf4j", "slf4j-jdk14", "1.7.36")),
                Arguments.of("logback-12", dependency("ch.qos.logback", "logback-classic", "1.2.13"))
        );
    }

    @Test
    void slf4j20AndModernLogbackAreNotMarked() {
        rewriteRun(xml(project("<dependencies>" + target("7.5.10", "") +
                dependency("org.slf4j", "slf4j-api", "2.0.17") +
                dependency("ch.qos.logback", "logback-classic", "1.5.34") +
                "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void marksExclusionAndShadePackaging() {
        String source = project("<dependencies>" + target("7.5.5",
                "<exclusions><exclusion><groupId>org.slf4j</groupId>" +
                "<artifactId>slf4j-api</artifactId></exclusion></exclusions>") +
                "</dependencies><build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                "<artifactId>maven-shade-plugin</artifactId><configuration><relocations><relocation>" +
                "<pattern>com.github.junrar</pattern><shadedPattern>internal.rar</shadedPattern>" +
                "</relocation></relocations></configuration></plugin></plugins></build>");
        rewriteRun(markedXml(source, "pom.xml", FindJunrar7510BuildRisks.PACKAGING));
    }

    @ParameterizedTest(name = "Gradle higher {0}")
    @ValueSource(strings = {"7.5.11", "7.6.0", "8.0.0", "10.0.0"})
    void marksGradleHigherWithoutDowngrade(String version) {
        rewriteRun(
                buildGradle("dependencies { implementation 'com.github.junrar:junrar:" + version + "' }",
                        source -> source.path("groovy/" + version + "/build.gradle")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains(version), printed);
                                    assertTrue(printed.contains(JunrarSupport.TARGET_CONFLICT), printed);
                                })),
                buildGradleKts("dependencies { implementation(\"com.github.junrar:junrar:" + version + "\") }",
                        source -> source.path("kotlin/" + version + "/build.gradle.kts")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains(version), printed);
                                    assertTrue(printed.contains(JunrarSupport.TARGET_CONFLICT), printed);
                                })));
    }

    @ParameterizedTest(name = "future Gradle variant gets only downgrade marker {0}")
    @ValueSource(strings = {
            "com.github.junrar:junrar:8.0.0:sources",
            "com.github.junrar:junrar:8.0.0@zip"
    })
    void futureGradleVariantsStillUseOnlyTheExactNoDowngradeMarker(String coordinate) {
        rewriteRun(buildGradle("dependencies { implementation '" + coordinate + "' }",
                source -> source.path("future-variant/build.gradle")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(JunrarSupport.TARGET_CONFLICT), printed);
                            assertFalse(printed.contains(FindJunrar7510BuildRisks.VARIANT), printed);
                            assertFalse(printed.contains(JunrarSupport.TARGET_CONFLICT + "："), printed);
                        })));
    }

    @Test
    void marksGradleDynamicCatalogAndVariantWithoutLeakingSelectedProjectRisks() {
        rewriteRun(buildGradle("""
                def v = '7.5.5'
                dependencies {
                  implementation "com.github.junrar:junrar:${v}"
                  implementation libs.junrar
                  implementation group: 'com.github.junrar', name: 'junrar',
                                 version: '7.5.5', classifier: 'sources'
                  runtimeOnly 'org.slf4j:slf4j-api:1.7.36'
                }
                shadowJar { relocate 'com.github.junrar', 'internal.rar' }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindJunrar7510BuildRisks.OWNER), printed);
            assertTrue(printed.contains(FindJunrar7510BuildRisks.VARIANT), printed);
            assertFalse(printed.contains(FindJunrar7510BuildRisks.SLF4J), printed);
            assertFalse(printed.contains(FindJunrar7510BuildRisks.PACKAGING), printed);
        })));
    }

    @Test
    void futureProjectReceivesOnlyTheExactNoDowngradeMarker() {
        rewriteRun(xml(project("<dependencies>" + target("8.0.0",
                "<exclusions><exclusion><groupId>org.slf4j</groupId>" +
                "<artifactId>slf4j-api</artifactId></exclusion></exclusions>") +
                dependency("org.slf4j", "slf4j-api", "1.7.36") +
                "</dependencies><build><plugins><plugin>" +
                "<artifactId>maven-shade-plugin</artifactId><configuration>" +
                "<artifactSet><includes><include>com.github.junrar:junrar</include></includes>" +
                "</artifactSet></configuration></plugin></plugins></build>"),
                source -> source.path("future/pom.xml").after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(JunrarSupport.TARGET_CONFLICT), printed);
                            assertFalse(printed.contains(FindJunrar7510BuildRisks.SLF4J), printed);
                            assertFalse(printed.contains(FindJunrar7510BuildRisks.PACKAGING), printed);
                            assertFalse(printed.contains(JunrarSupport.TARGET_CONFLICT + "："), printed);
                        })));
    }

    @ParameterizedTest(name = "target/off-list project has no ancillary markers {0}")
    @ValueSource(strings = {"7.5.10", "7.5.9", "7.5.10-rc1"})
    void targetAndOffListProjectsAreCompletelyUnmarked(String version) {
        rewriteRun(xml(project("<dependencies>" + target(version, "") +
                dependency("org.slf4j", "slf4j-api", "1.7.36") +
                "</dependencies><build><plugins><plugin>" +
                "<artifactId>maven-shade-plugin</artifactId><configuration>" +
                "<artifactSet><includes><include>com.github.junrar:junrar</include></includes>" +
                "</artifactSet></configuration></plugin></plugins></build>"),
                source -> source.path(version + "/pom.xml")));
    }

    @ParameterizedTest(name = "generated build {0}")
    @ValueSource(strings = {
            "target", "build", "generated", "generatedSources", "install", ".gradle", ".m2",
            ".idea", "node_modules", "vendor", "reports", "test-results", "tmp", "TEMP"
    })
    void generatedBuildFilesAreNotMarked(String parent) {
        rewriteRun(xml(pom("8.0.0"), source -> source.path(parent + "/pom.xml")));
    }

    private static org.openrewrite.test.SourceSpecs markedPom(
            String version, String marker, String path,
            java.util.function.Consumer<String> extraAssertions) {
        return xml(pom(version), source -> source.path(path).after(actual -> actual)
                .afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(marker), printed);
                    extraAssertions.accept(printed);
                }));
    }

    private static org.openrewrite.test.SourceSpecs markedXml(
            String value, String path, String marker) {
        return xml(value, source -> source.path(path).after(actual -> actual)
                .afterRecipe(after -> assertTrue(after.printAll().contains(marker), after.printAll())));
    }

    private static String pom(String version) {
        return project("<dependencies>" + target(version, "") + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String target(String version, String metadata) {
        return "<dependency><groupId>com.github.junrar</groupId><artifactId>junrar</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") +
               metadata + "</dependency>";
    }

    private static String dependency(String group, String artifact, String version) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact +
               "</artifactId><version>" + version + "</version></dependency>";
    }
}
