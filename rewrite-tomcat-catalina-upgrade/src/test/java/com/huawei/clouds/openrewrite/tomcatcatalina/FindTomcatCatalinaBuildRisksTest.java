package com.huawei.clouds.openrewrite.tomcatcatalina;

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

class FindTomcatCatalinaBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindTomcatCatalinaBuildRisks());
    }

    @ParameterizedTest(name = "Maven unresolved version {0}")
    @ValueSource(strings = {"10.0.26", "10.1.14", "10.1.42", "${tomcat.version}", "[10.1,11)", "10.1.+", "latest.release"})
    void marksEveryNonTargetMavenOwner(String version) {
        rewriteRun(xml(UpgradeTomcatCatalinaDependencyTest.pom(version), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "actual property, BOM"))));
    }

    @ParameterizedTest(name = "variant {0}")
    @MethodSource("variants")
    void marksMavenVariants(String label, String extra) {
        rewriteRun(xml(UpgradeTomcatCatalinaDependencyTest.project("<dependencies>" +
                UpgradeTomcatCatalinaDependencyTest.rawDep("org.apache.tomcat", "tomcat-catalina", "10.0.27", extra) +
                "</dependencies>"), source -> source.path("pom.xml").after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), "classifier/non-jar"))));
    }

    static Stream<Arguments> variants() {
        return Stream.of(
                Arguments.of("sources", "<classifier>sources</classifier>"),
                Arguments.of("tests", "<classifier>tests</classifier>"),
                Arguments.of("zip", "<type>zip</type>"),
                Arguments.of("war", "<type>war</type>")
        );
    }

    @ParameterizedTest(name = "Maven Java level {0}")
    @ValueSource(strings = {"1.7", "1.8", "8", "9", "10"})
    void marksMavenJavaBelow11(String value) {
        String pom = UpgradeTomcatCatalinaDependencyTest.project(
                "<properties><maven.compiler.release>" + value + "</maven.compiler.release></properties><dependencies>" +
                UpgradeTomcatCatalinaDependencyTest.dep("10.1.56") + "</dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), "requires Java 11"))));
    }

    @Test
    void rootJavaBaselineIsVisibleToProfileOwnedCore() {
        String pom = UpgradeTomcatCatalinaDependencyTest.project(
                "<properties><maven.compiler.release>8</maven.compiler.release></properties>" +
                "<profiles><profile><id>tomcat</id><dependencies>" +
                UpgradeTomcatCatalinaDependencyTest.dep("10.1.56") +
                "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), "requires Java 11"))));
    }

    @ParameterizedTest(name = "safe Maven Java level {0}")
    @ValueSource(strings = {"11", "17", "21", "25"})
    void targetAndSupportedJavaOnlyReceiveInterimSecurityMarker(String value) {
        String pom = UpgradeTomcatCatalinaDependencyTest.project(
                "<properties><maven.compiler.release>" + value + "</maven.compiler.release></properties><dependencies>" +
                UpgradeTomcatCatalinaDependencyTest.dep("10.1.56") + "</dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            assertContains(after.printAll(), "interim security target");
            assertFalse(after.printAll().contains("requires Java 11"), after::printAll);
        })));
    }

    @Test
    void marksUnalignedCatalinaFamilyInSameProject() {
        String pom = UpgradeTomcatCatalinaDependencyTest.project("<dependencies>" +
                UpgradeTomcatCatalinaDependencyTest.dep("10.1.56") +
                UpgradeTomcatCatalinaDependencyTest.rawDep("org.apache.tomcat", "tomcat-coyote", "10.1.41", "") +
                UpgradeTomcatCatalinaDependencyTest.rawDep("org.apache.tomcat", "tomcat-util", "${tomcat.version}", "") +
                "</dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            assertContains(after.printAll(), "Tomcat Catalina sibling artifacts");
            assertEqualsCount(after.printAll(), "Tomcat Catalina sibling artifacts", 2);
        })));
    }

    @Test
    void alignedFamilyOnlyReceivesInterimSecurityMarker() {
        String pom = UpgradeTomcatCatalinaDependencyTest.project("<dependencies>" +
                UpgradeTomcatCatalinaDependencyTest.dep("10.1.56") +
                UpgradeTomcatCatalinaDependencyTest.rawDep("org.apache.tomcat", "tomcat-api", "10.1.56", "") +
                "</dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            assertContains(after.printAll(), "interim security target");
            assertFalse(after.printAll().contains("Tomcat Catalina sibling artifacts"), after::printAll);
        })));
    }

    @Test
    void marksJavaxAndExplicitJakartaWebApisBesideTomcat() {
        String pom = UpgradeTomcatCatalinaDependencyTest.project("<dependencies>" +
                UpgradeTomcatCatalinaDependencyTest.dep("10.1.56") +
                UpgradeTomcatCatalinaDependencyTest.rawDep("javax.servlet", "javax.servlet-api", "4.0.1", "") +
                UpgradeTomcatCatalinaDependencyTest.rawDep("javax.el", "javax.el-api", "3.0.0", "") +
                UpgradeTomcatCatalinaDependencyTest.rawDep("jakarta.servlet", "jakarta.servlet-api", "6.0.0", "") +
                "</dependencies>");
        rewriteRun(
                xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertEqualsCount(after.printAll(), "implements Jakarta Servlet 6.0", 3))),
                buildGradle("dependencies { implementation 'org.apache.tomcat:tomcat-catalina:10.1.56'; implementation 'javax.servlet:javax.servlet-api:4.0.1'; compileOnly 'jakarta.el:jakarta.el-api:5.0.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertEqualsCount(after.printAll(), "implements Jakarta Servlet 6.0", 2)))
        );
    }

    @ParameterizedTest(name = "Gradle unresolved {0}")
    @ValueSource(strings = {"10.0.26", "10.1.14", "10.1.42", "10.1.+", "latest.release"})
    void marksGradleNonTargetLiterals(String version) {
        rewriteRun(buildGradle("dependencies { implementation 'org.apache.tomcat:tomcat-catalina:" + version + "' }",
                source -> source.after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "actual property, BOM"))));
    }

    @Test
    void marksGradleTemplatesAndFamilyMisalignment() {
        rewriteRun(
                buildGradle("def tomcatVersion = '10.1.41'; dependencies { implementation \"org.apache.tomcat:tomcat-catalina:$tomcatVersion\" }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "actual property, BOM"))),
                buildGradleKts("val tomcatVersion = \"10.1.41\"\ndependencies { implementation(\"org.apache.tomcat:tomcat-catalina:$tomcatVersion\") }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "actual property, BOM"))),
                buildGradle("dependencies { implementation 'org.apache.tomcat:tomcat-catalina:10.1.56'; implementation 'org.apache.tomcat:tomcat-coyote:10.1.41' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "must be aligned")))
        );
    }

    @Test
    void marksInterimTargetInGradleWithoutChangingIt() {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.apache.tomcat:tomcat-catalina:10.1.56' }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "interim security target");
                    assertContains(after.printAll(), "10.1.56");
                })));
    }

    @ParameterizedTest(name = "Gradle Java assignment {0}")
    @ValueSource(strings = {"1.8", "8", "9", "10", "JavaVersion.VERSION_1_8", "JavaVersion.VERSION_10"})
    void marksGradleJavaAssignmentsBelow11(String value) {
        rewriteRun(buildGradle("sourceCompatibility = " + (value.startsWith("Java") ? value : "'" + value + "'") +
                        "\ndependencies { implementation 'org.apache.tomcat:tomcat-catalina:10.1.56' }",
                source -> source.after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "requires Java 11"))));
    }

    @ParameterizedTest(name = "Gradle toolchain {0}")
    @ValueSource(ints = {8, 9, 10})
    void marksGradleToolchainsBelow11(int version) {
        rewriteRun(buildGradle("java { toolchain { languageVersion = JavaLanguageVersion.of(" + version + ") } }\n" +
                        "dependencies { implementation 'org.apache.tomcat:tomcat-catalina:10.1.56' }",
                source -> source.after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "requires Java 11"))));
    }

    @Test
    void nestedOwnersAndUnrelatedBuildsAreUnmarked() {
        rewriteRun(
                xml(UpgradeTomcatCatalinaDependencyTest.project("<build><dependencies>" + UpgradeTomcatCatalinaDependencyTest.dep("10.0.27") + "</dependencies></build>"),
                        source -> source.path("pom.xml")),
                buildGradle("subprojects { dependencies { implementation 'org.apache.tomcat:tomcat-catalina:10.0.27' } }"),
                buildGradle("sourceCompatibility = '1.8'")
        );
    }

    @Test
    void generatedBuildIsUnmarked() {
        rewriteRun(xml(UpgradeTomcatCatalinaDependencyTest.pom("10.0.27"), source -> source.path("target/pom.xml")));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected marker containing '" + expected + "' in:\n" + actual);
    }

    private static void assertEqualsCount(String value, String token, int expected) {
        int count = 0;
        for (int index = 0; (index = value.indexOf(token, index)) >= 0; index += token.length()) count++;
        int actual = count;
        assertTrue(actual == expected, () -> "Expected " + expected + " occurrences but found " + actual + " in:\n" + value);
    }
}
