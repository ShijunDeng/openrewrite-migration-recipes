package com.huawei.clouds.openrewrite.commonscodec;

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

class FindCommonsCodecBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindCommonsCodecBuildRisks());
    }

    @ParameterizedTest(name = "unresolved Maven version {0}")
    @ValueSource(strings = {"1.10", "1.12", "1.16.1", "1.17.0", "1.21.0", "1.22.1", "[1.11,2)", "${codec.version}"})
    void marksEveryNonTargetMavenVersion(String version) {
        rewriteRun(xml(pom(dep(version)), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains("not a workbook-selected literal"), after::printAll))));
    }

    @ParameterizedTest(name = "Maven variant {0}")
    @MethodSource("mavenVariants")
    void marksMavenVariants(String label, String extra) {
        rewriteRun(xml(pom("<dependencies>" + rawDep("1.11", extra) + "</dependencies>"), source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains("shaded/relocated"), after::printAll))));
    }

    static Stream<Arguments> mavenVariants() {
        return Stream.of(
                Arguments.of("sources", "<classifier>sources</classifier>"),
                Arguments.of("tests", "<classifier>tests</classifier>"),
                Arguments.of("zip", "<type>zip</type>"),
                Arguments.of("test jar", "<type>test-jar</type>"));
    }

    @Test
    void targetLiteralAndExclusiveTargetPropertyAreNoop() {
        rewriteRun(
                xml(pom(dep("1.22.0")), source -> source.path("pom.xml")),
                xml(pom("<properties><codec.version>1.22.0</codec.version></properties><dependencies>" +
                        dep("${codec.version}") + "</dependencies>"), source -> source.path("property/pom.xml")),
                xml(pom("<dependencyManagement>" + dep("1.22.0") +
                        "</dependencyManagement><dependencies><dependency>" +
                        "<groupId>commons-codec</groupId><artifactId>commons-codec</artifactId>" +
                        "</dependency></dependencies>"), source -> source.path("managed/pom.xml")));
    }

    @ParameterizedTest(name = "Maven Java {0}")
    @ValueSource(strings = {"1.4", "1.5", "1.6", "1.7", "5", "6", "7"})
    void marksMavenJavaBelowEight(String version) {
        rewriteRun(xml(pom("<properties><maven.compiler.release>" + version + "</maven.compiler.release></properties>" + dep("1.22.0")),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("requires Java 8"), after::printAll))));
    }

    @ParameterizedTest(name = "supported Java {0}")
    @ValueSource(strings = {"1.8", "8", "11", "17", "21"})
    void supportedMavenJavaIsNoop(String version) {
        rewriteRun(xml(pom("<properties><java.version>" + version + "</java.version></properties>" + dep("1.22.0")),
                source -> source.path("pom.xml")));
    }

    @Test
    void JavaPropertyWithoutPrimaryDependencyIsNoop() {
        rewriteRun(xml(pom("<properties><java.version>1.7</java.version></properties>"), source -> source.path("pom.xml")));
    }

    @Test
    void resolvesIndirectOwnedMavenJavaProperty() {
        rewriteRun(xml(pom("<properties><jdk.release>7</jdk.release><maven.compiler.release>${jdk.release}</maven.compiler.release></properties>" + dep("1.22.0")),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("requires Java 8"), after::printAll))));
    }

    @ParameterizedTest(name = "unresolved Gradle {0}")
    @ValueSource(strings = {"1.10", "1.12", "1.16.1", "1.17.0", "1.21.0", "1.22.1"})
    void marksNonTargetGradleVersions(String version) {
        rewriteRun(buildGradle("dependencies { implementation 'commons-codec:commons-codec:" + version + "' }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("actual property/BOM/catalog"), after::printAll))));
    }

    @Test
    void marksGradleVariableAndVariants() {
        rewriteRun(
                buildGradle("dependencies { implementation \"commons-codec:commons-codec:$codecVersion\" }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("source whitelist"), after::printAll))),
                buildGradle("dependencies { implementation 'commons-codec:commons-codec:1.11:tests' }",
                        source -> source.path("variant.gradle").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("Gradle variant"), after::printAll))),
                buildGradle("dependencies { implementation 'commons-codec:commons-codec:1.13@zip' }",
                        source -> source.path("zip.gradle").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("ordinary 1.22.0 jar"), after::printAll))));
    }

    @Test
    void marksGradleJavaBelowEightInOwnedRootScopes() {
        rewriteRun(
                buildGradle("plugins { id 'java' }; sourceCompatibility = JavaVersion.VERSION_1_7; dependencies { implementation 'commons-codec:commons-codec:1.22.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("Java 8 or later"), after::printAll))),
                buildGradle("java { toolchain { languageVersion = JavaLanguageVersion.of(7) } }; dependencies { implementation 'commons-codec:commons-codec:1.22.0' }",
                        source -> source.path("toolchain.gradle").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("Android level"), after::printAll))),
                buildGradleKts("java { toolchain { languageVersion.set(JavaLanguageVersion.of(7)) } }; dependencies { implementation(\"commons-codec:commons-codec:1.22.0\") }",
                        source -> source.path("build.gradle.kts").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("CI images"), after::printAll))));
    }

    @Test
    void targetGradleAndNestedJavaAssignmentsAreNoop() {
        rewriteRun(
                buildGradle("dependencies { implementation 'commons-codec:commons-codec:1.22.0' }; sourceCompatibility = JavaVersion.VERSION_1_8"),
                buildGradle("subprojects { sourceCompatibility = JavaVersion.VERSION_1_7 }; dependencies { implementation 'commons-codec:commons-codec:1.22.0' }",
                        source -> source.path("nested.gradle").afterRecipe(after ->
                                assertFalse(after.printAll().contains("Java 8 or later")))));
    }

    @Test
    void lookalikeGradleCoordinateDoesNotEnterScope() {
        rewriteRun(
                buildGradle("sourceCompatibility = JavaVersion.VERSION_1_7; dependencies { implementation 'notcommons-codec:commons-codec:1.11' }"),
                buildGradle("sourceCompatibility = JavaVersion.VERSION_1_7; dependencies { implementation 'commons-codec:commons-codec-extra:1.11' }",
                        source -> source.path("artifact.gradle")));
    }

    @Test
    void GradleVariantDoesNotGateJavaBaseline() {
        rewriteRun(buildGradle("sourceCompatibility = JavaVersion.VERSION_1_7; dependencies { implementation 'commons-codec:commons-codec:1.22.0:tests' }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("Gradle variant"), out);
                    assertFalse(out.contains("Java 8 or later"), out);
                })));
    }

    @Test
    void generatedAndWrongCoordinateAreNoop() {
        rewriteRun(
                xml(pom(dep("1.11")), source -> source.path("target/pom.xml")),
                xml(pom("<dependencies><dependency><groupId>org.apache.commons</groupId><artifactId>commons-codec</artifactId><version>1.11</version></dependency></dependencies>"),
                        source -> source.path("wrong/pom.xml")));
    }

    private static String pom(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dep(String version) {
        return "<dependencies>" + rawDep(version, "") + "</dependencies>";
    }

    private static String rawDep(String version, String extra) {
        return "<dependency><groupId>commons-codec</groupId><artifactId>commons-codec</artifactId><version>" + version + "</version>" + extra + "</dependency>";
    }
}
