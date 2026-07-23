package com.huawei.clouds.openrewrite.jettyhttp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class JettyHttpBuildRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.jettyhttp.FindJettyHttp12_0_34BuildRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(JettyHttpTestSupport.recipe(RECIPE));
    }

    @ParameterizedTest(name = "higher Maven version {0}")
    @ValueSource(strings = {
            "12.0.35", "12.0.100", "12.1.0", "12.1.9", "13.0.0", "20.0.0",
            "12.0.34.1", "12.0.34-sp1", "12.0.34.999999999999999999999",
            "999999999999999999999.0.0", "12.0.35-SNAPSHOT", "13.0.0-RC1"
    })
    void marksEveryHigherFixedVersionWithExactNoDowngradeText(String version) {
        rewriteRun(xml(JettyHttpTestSupport.pom(version),
                source -> source.path(version.replace('.', '_') + "/pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>" + version + "</version>"), printed);
                            assertTrue(printed.contains(JettyHttpSupport.TARGET_CONFLICT), printed);
                            assertFalse(printed.contains("<version>12.0.34</version>"), printed);
                        })));
    }

    @ParameterizedTest(name = "outside whitelist {0}")
    @ValueSource(strings = {
            "9.4.38.v20210224", "9.4.59.v20251014", "10.0.0", "10.0.27",
            "11.0.19", "11.0.21", "12.0.11", "12.0.26", "12.0.33"
    })
    void marksFixedVersionsOutsideTheApprovedPath(String version) {
        rewriteRun(markedPom(version, FindJettyHttpBuildRisks.OUTSIDE));
    }

    @ParameterizedTest(name = "approved/target {0}")
    @ValueSource(strings = {
            "9.4.39.v20210325", "9.4.53.v20231009", "9.4.54.v20240208",
            "9.4.57.v20241219", "9.4.58.v20250814", "11.0.20",
            "12.0.12", "12.0.15", "12.0.16", "12.0.25", "12.0.34"
    })
    void selectedSourcesAndTargetDoNotGetVersionMarkers(String version) {
        rewriteRun(xml(JettyHttpTestSupport.pom(version),
                source -> source.path(version + "/pom.xml")));
    }

    @ParameterizedTest(name = "unresolved owner {0}")
    @MethodSource("ownerVersions")
    void marksUnresolvedOrDynamicMavenOwners(String label, String version) {
        rewriteRun(markedPom(version, FindJettyHttpBuildRisks.OWNER, label + "/pom.xml"));
    }

    static Stream<Arguments> ownerVersions() {
        return Stream.of(
                Arguments.of("missing-property", "${missing}"),
                Arguments.of("range", "[11.0.20,12.0.34]"),
                Arguments.of("latest", "LATEST"),
                Arguments.of("release", "RELEASE"),
                Arguments.of("wildcard", "12.+"),
                Arguments.of("revision", "${revision}${changelist}"));
    }

    @ParameterizedTest(name = "ambiguous Maven property owner {0}")
    @MethodSource("ambiguousPropertyOwners")
    void marksPropertiesThatTheStrictUpgradeCannotSafelyOwn(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path(label + "/pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("${v}"), printed);
                    assertTrue(printed.contains(FindJettyHttpBuildRisks.OWNER), printed);
                    assertFalse(printed.contains("<v>12.0.34</v>"), printed);
                })));
    }

    static Stream<Arguments> ambiguousPropertyOwners() {
        return Stream.of(
                Arguments.of("shared", JettyHttpTestSupport.project(
                        "<properties><v>11.0.20</v></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("${v}", "") +
                        "<dependency><groupId>example</groupId><artifactId>other</artifactId>" +
                        "<version>${v}</version></dependency></dependencies>")),
                Arguments.of("duplicate", JettyHttpTestSupport.project(
                        "<properties><v>11.0.20</v><v>11.0.20</v></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("${v}", "") + "</dependencies>")),
                Arguments.of("profile-shadow", JettyHttpTestSupport.project(
                        "<properties><v>11.0.20</v></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("${v}", "") +
                        "</dependencies><profiles><profile><id>it</id><properties>" +
                        "<v>11.0.20</v></properties></profile></profiles>")));
    }

    @Test
    void marksVersionlessParentOrBomOwner() {
        rewriteRun(xml(JettyHttpTestSupport.project("<dependencies>" +
                        JettyHttpTestSupport.dependency(null, "") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindJettyHttpBuildRisks.OWNER),
                                after.printAll()))));
    }

    @ParameterizedTest(name = "Maven variant {0}")
    @MethodSource("mavenVariants")
    void marksNonstandardMavenVariants(String label, String extra) {
        rewriteRun(xml(JettyHttpTestSupport.project("<dependencies>" +
                        JettyHttpTestSupport.dependency("11.0.20", extra) + "</dependencies>"),
                source -> source.path(label + "/pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindJettyHttpBuildRisks.VARIANT),
                                after.printAll()))));
    }

    static Stream<Arguments> mavenVariants() {
        return Stream.of(
                Arguments.of("classifier", "<classifier>tests</classifier>"),
                Arguments.of("sources", "<classifier>sources</classifier>"),
                Arguments.of("zip", "<type>zip</type>"),
                Arguments.of("test-jar", "<type>test-jar</type>"));
    }

    @ParameterizedTest(name = "Java baseline {0}")
    @ValueSource(strings = {"8", "1.8", "9", "11", "16"})
    void marksMavenJavaBaselinesBelow17(String javaVersion) {
        String source = JettyHttpTestSupport.project("<properties><java.version>" + javaVersion +
                "</java.version></properties><dependencies>" +
                JettyHttpTestSupport.dependency("11.0.20", "") + "</dependencies>");
        rewriteRun(xml(source, spec -> spec.path(javaVersion.replace('.', '_') + "/pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindJettyHttpBuildRisks.JAVA_BASELINE),
                                after.printAll()))));
    }

    @Test
    void Java17AndNewerDoNotGetBaselineMarker() {
        String source = JettyHttpTestSupport.project(
                "<properties><java.version>17</java.version><maven.compiler.release>21</maven.compiler.release>" +
                "</properties><dependencies>" + JettyHttpTestSupport.dependency("12.0.34", "") +
                "</dependencies>");
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    @ParameterizedTest(name = "companion boundary {0}")
    @MethodSource("companions")
    void marksCompanionAlignmentAndEeLineage(String label, String dependency, String marker) {
        String source = JettyHttpTestSupport.project("<dependencies>" +
                JettyHttpTestSupport.dependency("12.0.34", "") + dependency + "</dependencies>");
        rewriteRun(xml(source, spec -> spec.path(label + "/pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(marker), after.printAll()))));
    }

    static Stream<Arguments> companions() {
        return Stream.of(
                Arguments.of("jetty-io-skew",
                        JettyHttpTestSupport.dependency("org.eclipse.jetty", "jetty-io", "12.0.25"),
                        FindJettyHttpBuildRisks.FAMILY_ALIGNMENT),
                Arguments.of("jetty-client-11",
                        JettyHttpTestSupport.dependency("org.eclipse.jetty", "jetty-client", "11.0.20"),
                        FindJettyHttpBuildRisks.FAMILY_ALIGNMENT),
                Arguments.of("legacy-servlet",
                        JettyHttpTestSupport.dependency("org.eclipse.jetty", "jetty-servlet", "11.0.20"),
                        FindJettyHttpBuildRisks.EE_LINEAGE),
                Arguments.of("legacy-websocket",
                        JettyHttpTestSupport.dependency("org.eclipse.jetty.websocket", "websocket-server", "9.4.58.v20250814"),
                        FindJettyHttpBuildRisks.EE_LINEAGE));
    }

    @Test
    void alignedCoreCompanionsDoNotGetFamilyMarker() {
        String source = JettyHttpTestSupport.project("<dependencies>" +
                JettyHttpTestSupport.dependency("12.0.34", "") +
                JettyHttpTestSupport.dependency("org.eclipse.jetty", "jetty-io", "12.0.34") +
                JettyHttpTestSupport.dependency("org.eclipse.jetty", "jetty-util", "12.0.34") +
                "</dependencies>");
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    @Test
    void marksExclusionsAndShadeRelocation() {
        String source = JettyHttpTestSupport.project("<dependencies>" +
                JettyHttpTestSupport.dependency("12.0.34",
                        "<exclusions><exclusion><groupId>org.eclipse.jetty</groupId>" +
                        "<artifactId>jetty-util</artifactId></exclusion></exclusions>") +
                "</dependencies><build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                "<artifactId>maven-shade-plugin</artifactId><configuration><relocations><relocation>" +
                "<pattern>org.eclipse.jetty</pattern><shadedPattern>internal.jetty</shadedPattern>" +
                "</relocation></relocations></configuration></plugin></plugins></build>");
        rewriteRun(xml(source, spec -> spec.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(FindJettyHttpBuildRisks.PACKAGING), after.printAll()))));
    }

    @Test
    void marksGroovyAndKotlinConflictWithoutChangingTheVersion() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.eclipse.jetty:jetty-http:12.1.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("12.1.0"), after.printAll());
                            assertTrue(after.printAll().contains(JettyHttpSupport.TARGET_CONFLICT), after.printAll());
                        })),
                buildGradleKts("dependencies { implementation(\"org.eclipse.jetty:jetty-http:13.0.0\") }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("13.0.0"), after.printAll());
                            assertTrue(after.printAll().contains(JettyHttpSupport.TARGET_CONFLICT), after.printAll());
                        })));
    }

    @Test
    void marksGradleDynamicCatalogVariantJavaAndPackagingOwners() {
        rewriteRun(buildGradle("""
                sourceCompatibility = '11'
                def v = '11.0.20'
                dependencies {
                  implementation "org.eclipse.jetty:jetty-http:${v}"
                  implementation libs.jetty.http
                  implementation group: 'org.eclipse.jetty', name: 'jetty-http',
                                 version: '11.0.20', classifier: 'sources'
                }
                shadowJar { relocate 'org.eclipse.jetty', 'internal.jetty' }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindJettyHttpBuildRisks.OWNER), printed);
                    assertTrue(printed.contains(FindJettyHttpBuildRisks.JAVA_BASELINE), printed);
                    assertTrue(printed.contains(FindJettyHttpBuildRisks.PACKAGING), printed);
                    assertTrue(printed.contains(FindJettyHttpBuildRisks.VARIANT), printed);
                })));
    }

    @ParameterizedTest(name = "generated/cache build {0}")
    @ValueSource(strings = {
            "target", "build", "generated", "generatedSources", "install", ".gradle", ".m2", ".idea",
            "node_modules", "vendor", "reports", "test-results", "tmp", "TEMP"
    })
    void generatedBuildFilesAreNotMarked(String parent) {
        rewriteRun(xml(JettyHttpTestSupport.pom("13.0.0"), source -> source.path(parent + "/pom.xml")));
    }

    private static org.openrewrite.test.SourceSpecs markedPom(String version, String marker) {
        return markedPom(version, marker, version.replace('.', '_') + "/pom.xml");
    }

    private static org.openrewrite.test.SourceSpecs markedPom(String version, String marker, String path) {
        return xml(JettyHttpTestSupport.pom(version),
                source -> source.path(path).after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(marker), after.printAll())));
    }
}
