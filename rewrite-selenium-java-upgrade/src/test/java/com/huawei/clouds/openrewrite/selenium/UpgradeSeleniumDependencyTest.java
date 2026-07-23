package com.huawei.clouds.openrewrite.selenium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeSeleniumDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedSeleniumDependency());
    }

    @Test
    void exactWorkbookRow4559() {
        assertEquals(Set.of("4.8.1"), UpgradeSelectedSeleniumDependency.SOURCE_VERSIONS);
        rewriteRun(xml(pom("4.8.1"), pom("4.41.0"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveRootProperty() {
        rewriteRun(xml(
                project("<properties><selenium.version>4.8.1</selenium.version></properties><dependencies>" +
                        dep("${selenium.version}") + "</dependencies>"),
                project("<properties><selenium.version>4.41.0</selenium.version></properties><dependencies>" +
                        dep("${selenium.version}") + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void rootPropertyIsVisibleInProfile() {
        rewriteRun(xml(
                project("<properties><s>4.8.1</s></properties><profiles><profile><id>it</id><dependencies>" +
                        dep("${s}") + "</dependencies></profile></profiles>"),
                project("<properties><s>4.41.0</s></properties><profiles><profile><id>it</id><dependencies>" +
                        dep("${s}") + "</dependencies></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWins() {
        rewriteRun(xml(
                project("<properties><s>4.7.0</s></properties><profiles><profile><id>it</id><properties><s>4.8.1</s></properties>" +
                        "<dependencies>" + dep("${s}") + "</dependencies></profile></profiles>"),
                project("<properties><s>4.7.0</s></properties><profiles><profile><id>it</id><properties><s>4.41.0</s></properties>" +
                        "<dependencies>" + dep("${s}") + "</dependencies></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void dependencyManagementAtRootAndProfile() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + dep("4.8.1") +
                        "</dependencies></dependencyManagement><profiles><profile><id>p</id><dependencyManagement><dependencies>" +
                        dep("4.8.1") + "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("4.41.0") +
                        "</dependencies></dependencyManagement><profiles><profile><id>p</id><dependencyManagement><dependencies>" +
                        dep("4.41.0") + "</dependencies></dependencyManagement></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous owner {0}")
    @MethodSource("ambiguousProperties")
    void ambiguousPropertyIsNoop(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("unused", project("<properties><s>4.8.1</s></properties>")),
                Arguments.of("plugin", project("<properties><s>4.8.1</s></properties><dependencies>" + dep("${s}") + "</dependencies><build><finalName>${s}</finalName></build>")),
                Arguments.of("other dependency", project("<properties><s>4.8.1</s></properties><dependencies>" + dep("${s}") + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${s}</version></dependency></dependencies>")),
                Arguments.of("duplicate", project("<properties><s>4.8.1</s><s>4.8.1</s></properties><dependencies>" + dep("${s}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><s>4.8.1</s></properties><dependencies>" + dep("${s}") + "</dependencies><x value=\"${s}\"/>")),
                Arguments.of("profile cannot alter root use", project("<properties><s>4.7.0</s></properties><dependencies>" + dep("${s}") + "</dependencies><profiles><profile><id>x</id><properties><s>4.8.1</s></properties></profile></profiles>"))
        );
    }

    @Test
    void upgradesRootGradleForms() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.seleniumhq.selenium:selenium-java:4.8.1' }",
                        "dependencies { implementation 'org.seleniumhq.selenium:selenium-java:4.41.0' }"),
                buildGradle("dependencies { testImplementation group: 'org.seleniumhq.selenium', name: 'selenium-java', version: '4.8.1', transitive: false }",
                        "dependencies { testImplementation group: 'org.seleniumhq.selenium', name: 'selenium-java', version: '4.41.0', transitive: false }"),
                buildGradleKts("dependencies { implementation(\"org.seleniumhq.selenium:selenium-java:4.8.1\") }",
                        "dependencies { implementation(\"org.seleniumhq.selenium:selenium-java:4.41.0\") }"));
    }

    @ParameterizedTest(name = "nested Gradle scope {0}")
    @MethodSource("nestedGradle")
    void nestedGradleScopesAreNoop(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'org.seleniumhq.selenium:selenium-java:4.8.1'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':client') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom", "company { dependencies { implementation " + d + " } }"),
                Arguments.of("selected call", "dependencies { helper.implementation " + d + " }"),
                Arguments.of("plugin", "plugins { id 'x' version '1' }; custom(\"org.seleniumhq.selenium:selenium-java:4.8.1\")")
        );
    }

    @ParameterizedTest(name = "out-of-workbook {0}")
    @ValueSource(strings = {"3.141.59", "4.0.0", "4.8.0", "4.8.2", "4.9.0", "4.13.0", "4.14.0", "4.33.0", "4.40.0", "4.41.0", "4.41.1", "5.0.0"})
    void otherVersionsAreNoop(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "external/dynamic owner {0}")
    @ValueSource(strings = {"${selenium.version}", "${revision}", "[4,5)", "[4.8,)", "4.+", "+", "latest.release", "RELEASE"})
    void externalAndDynamicVersionsAreNoop(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "wrong coordinate {0}")
    @MethodSource("wrongCoordinates")
    void wrongCoordinatesAreNoop(String label, String dependency) {
        rewriteRun(xml(project("<dependencies>" + dependency + "</dependencies>"), source -> source.path("pom.xml")));
    }

    static Stream<Arguments> wrongCoordinates() {
        return Stream.of(
                Arguments.of("selenium-api", rawDep("org.seleniumhq.selenium", "selenium-api", "4.8.1", "")),
                Arguments.of("selenium-server", rawDep("org.seleniumhq.selenium", "selenium-server", "4.8.1", "")),
                Arguments.of("other group", rawDep("example", "selenium-java", "4.8.1", "")),
                Arguments.of("classified", rawDep("org.seleniumhq.selenium", "selenium-java", "4.8.1", "<classifier>sources</classifier>")),
                Arguments.of("lookalike", rawDep("org.seleniumhq.seleniumx", "selenium-java", "4.8.1", ""))
        );
    }

    @ParameterizedTest(name = "generated/cache path {0}")
    @ValueSource(strings = {"target", "build", "out", "generated", "generated-sources", "GENERATED-code", "install", "installation", ".gradle", ".mvn", ".m2", ".idea", "node_modules", "vendor", ".cache", ".git", ".vite", "reports", "test-results", "tmp", "TEMP"})
    void generatedAndCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("4.8.1"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void variantsAndVersionlessAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + rawDep("org.seleniumhq.selenium", "selenium-java", "4.8.1", "<classifier>tests</classifier>") + "</dependencies>"), source -> source.path("pom.xml")),
                xml(project("<dependencies>" + rawDep("org.seleniumhq.selenium", "selenium-java", "4.8.1", "<type>zip</type>") + "</dependencies>"), source -> source.path("pom.xml")),
                xml(project("<dependencies><dependency><groupId>org.seleniumhq.selenium</groupId><artifactId>selenium-java</artifactId></dependency></dependencies>"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'org.seleniumhq.selenium:selenium-java:4.8.1:tests' }"),
                buildGradle("dependencies { implementation 'org.seleniumhq.selenium:selenium-java:4.8.1@zip' }"));
    }

    @Test
    void installLeafIsProcessedAndTwoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradle("dependencies { implementation 'org.seleniumhq.selenium:selenium-java:4.8.1' }",
                        "dependencies { implementation 'org.seleniumhq.selenium:selenium-java:4.41.0' }",
                        source -> source.path("install.gradle")),
                xml(pom("4.8.1"), pom("4.41.0"), source -> source.path("pom.xml")));
    }

    @Test
    void recommendedRecipeStartsWithPublicUpgrade() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.selenium").build();
        var migrate = environment.activateRecipes(
                "com.huawei.clouds.openrewrite.selenium.MigrateSeleniumJavaTo4_41_0");
        assertEquals("com.huawei.clouds.openrewrite.selenium.UpgradeSeleniumJavaTo4_41_0",
                migrate.getRecipeList().get(0).getName());
    }

    static String pom(String version) { return project("<dependencies>" + dep(version) + "</dependencies>"); }
    static String project(String body) { return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>client</artifactId><version>1</version>" + body + "</project>"; }
    static String dep(String version) { return rawDep("org.seleniumhq.selenium", "selenium-java", version, ""); }
    static String rawDep(String group, String artifact, String version, String extra) { return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId><version>" + version + "</version>" + extra + "</dependency>"; }
}
