package com.huawei.clouds.openrewrite.feignhttpclient;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FeignHttpClient13BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindFeignHttpClient13BuildRisks());
    }

    @Test
    void marksVersionlessPropertyRangeAndDynamicOwners() {
        rewriteRun(
                xml(project("<properties><f>12.4</f></properties><dependencies>" + dep("${f}") + depNoVersion() + "</dependencies>"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), "migrate the actual owner deliberately", 2))),
                buildGradle("""
                        def f = '12.4'
                        dependencies {
                            implementation "io.github.openfeign:feign-httpclient:${f}"
                            runtimeOnly 'io.github.openfeign:feign-httpclient:+'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), "migrate the actual owner deliberately", 2))));
    }

    @Test
    void marksFixedOutOfScopeAndVariants() {
        rewriteRun(xml(project("<dependencies>" + dep("13.5") +
                        dep("12.4", "<classifier>tests</classifier>") +
                        dep("12.2", "<type>zip</type>") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "outside the workbook source set");
                    assertCount(after.printAll(), "classified or non-JAR Feign Apache HttpClient artifact", 2);
                })));
    }

    @Test
    void marksPreJava8OnlyAtStandardScopedProperties() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>java</artifactId><version>1</version>
                  <properties><maven.compiler.release>7</maven.compiler.release><random.java>6</random.java></properties><dependencies>%s</dependencies>
                  <profiles><profile><id>legacy</id><properties><java.version>1.7</java.version></properties></profile></profiles>
                </project>
                """.formatted(dep("13.6")), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertCount(after.printAll(), "requires Java 8 or newer", 2);
                    assertFalse(after.printAll().contains("random.java>/*~~"), after.printAll());
                })));
    }

    @Test
    void marksFeignFamilyAndMisalignedHc4CodecModules() {
        rewriteRun(xml(project("<dependencies>" + dep("13.6") +
                        dependency("io.github.openfeign", "feign-core", "12.4") +
                        dependency("io.github.openfeign", "feign-jackson", "12.4") +
                        dependency("org.apache.httpcomponents", "httpclient", "4.5.3") +
                        dependency("org.apache.httpcomponents", "httpcore", "4.4.10") +
                        dependency("commons-codec", "commons-codec", "1.15") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertCount(after.printAll(), "not aligned to 13.6", 2);
                    assertCount(after.printAll(), "remains an HC4 adapter", 2);
                    assertContains(after.printAll(), "manages commons-codec 1.18.0");
                })));
    }

    @Test
    void marksHc5AndFeignHc5MixEvenWhenVersionAligned() {
        rewriteRun(
                xml(project("<dependencies>" + dep("13.6") +
                                dependency("io.github.openfeign", "feign-hc5", "13.6") +
                                dependency("org.apache.httpcomponents.client5", "httpclient5", "5.4.2") +
                                dependency("org.apache.httpcomponents.core5", "httpcore5", "5.3.3") + "</dependencies>"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), "cannot consume Apache HC5 types", 3))),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-httpclient:13.6'; implementation 'io.github.openfeign:feign-hc5:13.6'; implementation 'org.apache.httpcomponents.client5:httpclient5:5.4.2' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), "cannot consume Apache HC5 types", 2))));
    }

    @Test
    void marksGradleGroovyMapAndKotlinTransportOverrides() {
        rewriteRun(
                buildGradle("""
                        dependencies {
                            implementation group: 'io.github.openfeign', name: 'feign-httpclient', version: '13.5'
                            implementation group: 'org.apache.httpcomponents', name: 'httpclient', version: '4.5.13'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "outside the workbook source set");
                    assertContains(after.printAll(), "remains an HC4 adapter");
                })),
                buildGradleKts("""
                        dependencies {
                            implementation("io.github.openfeign:feign-httpclient:13.6")
                            implementation("io.github.openfeign:feign-core:12.4")
                            implementation("commons-codec:commons-codec:1.16.0")
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "not aligned to 13.6");
                    assertContains(after.printAll(), "manages commons-codec 1.18.0");
                })));
    }

    @Test
    void alignedTargetHc4AndCodecLineAreClean() {
        rewriteRun(
                xml(project("<properties><maven.compiler.release>17</maven.compiler.release></properties><dependencies>" +
                                dep("13.6") + dependency("io.github.openfeign", "feign-core", "13.6") +
                                dependency("org.apache.httpcomponents", "httpclient", "4.5.14") +
                                dependency("org.apache.httpcomponents", "httpcore", "4.4.16") +
                                dependency("commons-codec", "commons-codec", "1.18.0") + "</dependencies>"),
                        source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-httpclient:13.6'; implementation 'org.apache.httpcomponents:httpclient:4.5.14' }"));
    }

    @Test
    void localTargetPropertiesResolveWithoutFalsePositive() {
        rewriteRun(xml(project("<properties><f>13.6</f><hc>4.5.14</hc></properties><dependencies>" +
                        dep("${f}") + dependency("org.apache.httpcomponents", "httpclient", "${hc}") + "</dependencies>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void nestedGradlePluginDependenciesAndFakeXmlAreNotAudited() {
        rewriteRun(
                buildGradle("subprojects { dependencies { implementation 'io.github.openfeign:feign-httpclient:13.5' } }"),
                buildGradle("buildscript { dependencies { classpath 'org.apache.httpcomponents:httpclient:4.5.3' } }"),
                xml("<catalog><dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>13.5</version></dependency></catalog>", source -> source.path("catalog.xml")),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin</artifactId><version>1</version><build><plugins><plugin>
                          <groupId>x</groupId><artifactId>tool</artifactId><version>1</version><dependencies>%s</dependencies>
                        </plugin></plugins></build></project>
                        """.formatted(dep("13.5")), source -> source.path("pom.xml")));
    }

    @Test
    void unrelatedBuildOwnersWithoutFeignHttpClientAreNotAudited() {
        rewriteRun(
                xml(project("<properties><maven.compiler.release>7</maven.compiler.release></properties><dependencies>" +
                                dependency("io.github.openfeign", "feign-core", "12.4") +
                                dependency("org.apache.httpcomponents", "httpclient", "4.5.3") +
                                dependency("commons-codec", "commons-codec", "1.15") + "</dependencies>"),
                        source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-hc5:13.6'; implementation 'org.apache.httpcomponents.client5:httpclient5:5.4.2' }"),
                buildGradleKts("dependencies { implementation(\"io.github.openfeign:feign-core:12.4\"); implementation(\"commons-codec:commons-codec:1.15\") }"));
    }

    @Test
    void generatedPrefixesCachesAndLeafFilenameAreFilteredPrecisely() {
        rewriteRun(
                xml(pom("13.5"), source -> source.path("generatedClients/pom.xml")),
                xml(pom("13.5"), source -> source.path("installation/cache/pom.xml")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-httpclient:13.5' }", source -> source.path(".gradle/cache/build.gradle")),
                buildGradle("dependencies { implementation 'io.github.openfeign:feign-httpclient:13.5' }",
                        source -> source.path("install.gradle").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "outside the workbook source set"))));
    }

    @Test
    void markersAreTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("13.5"), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), "outside the workbook source set", 1))));
    }

    @Test
    void recommendedRecipeUpgradesDirectAndOwnedPropertyBeforeAudit() {
        rewriteRun(spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feignhttpclient").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.feignhttpclient.MigrateFeignHttpClientTo13_6")),
                xml(pom("12.4"), pom("13.6"), source -> source.path("direct/pom.xml")),
                xml(
                        project("<properties><f>12.2</f></properties><dependencies>" + dep("${f}") + "</dependencies>"),
                        project("<properties><f>13.6</f></properties><dependencies>" + dep("${f}") + "</dependencies>"),
                        source -> source.path("pom.xml")));
    }

    private static String pom(String version) { return project("<dependencies>" + dep(version) + "</dependencies>"); }
    private static String project(String body) { return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>client</artifactId><version>1</version>" + body + "</project>"; }
    private static String dep(String version) { return dep(version, ""); }
    private static String dep(String version, String extra) { return "<dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId><version>" + version + "</version>" + extra + "</dependency>"; }
    private static String depNoVersion() { return "<dependency><groupId>io.github.openfeign</groupId><artifactId>feign-httpclient</artifactId></dependency>"; }
    private static String dependency(String group, String artifact, String version) { return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId><version>" + version + "</version></dependency>"; }
    private static void assertContains(String actual, String expected) { assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual); }
    private static void assertCount(String actual, String expected, int count) { int found = 0; for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++; int result = found; assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected + "> but found " + result + " in:\n" + actual); }
}
