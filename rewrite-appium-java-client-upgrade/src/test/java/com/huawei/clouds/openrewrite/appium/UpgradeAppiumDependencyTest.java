package com.huawei.clouds.openrewrite.appium;

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

class UpgradeAppiumDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedAppiumDependency());
    }

    @ParameterizedTest(name = "workbook source {0}")
    @ValueSource(strings = {"7.5.1"})
    void upgradesEveryWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("9.2.3"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesWorkbookSequenceNumber793() {
        assertEquals(Set.of("7.5.1"),
                UpgradeSelectedAppiumDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesExclusiveRootProperty() {
        rewriteRun(xml(
                project("<properties><appium.version>7.5.1</appium.version></properties>" +
                        "<dependencies>" + dep("${appium.version}") + "</dependencies>"),
                project("<properties><appium.version>9.2.3</appium.version></properties>" +
                        "<dependencies>" + dep("${appium.version}") + "</dependencies>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void rootPropertyIsVisibleInDirectProfile() {
        rewriteRun(xml(
                project("<properties><fmt.version>7.5.1</fmt.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                project("<properties><fmt.version>9.2.3</fmt.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWinsWithoutLeakingToRoot() {
        rewriteRun(xml(
                project("<properties><fmt.version>2.4</fmt.version></properties><profiles><profile><id>owned</id>" +
                        "<properties><fmt.version>7.5.1</fmt.version></properties><dependencies>" +
                        dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                project("<properties><fmt.version>2.4</fmt.version></properties><profiles><profile><id>owned</id>" +
                        "<properties><fmt.version>9.2.3</fmt.version></properties><dependencies>" +
                        dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + dep("7.5.1") +
                        "</dependencies></dependencyManagement><profiles><profile><id>legacy</id>" +
                        "<dependencyManagement><dependencies>" + dep("7.5.1") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("9.2.3") +
                        "</dependencies></dependencyManagement><profiles><profile><id>legacy</id>" +
                        "<dependencyManagement><dependencies>" + dep("9.2.3") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property ownership: {0}")
    @MethodSource("ambiguousProperties")
    void leavesAmbiguousPropertiesUntouched(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("unused", project("<properties><f>7.5.1</f></properties>")),
                Arguments.of("shared plugin", project("<properties><f>7.5.1</f></properties><dependencies>" +
                        dep("${f}") + "</dependencies><build><finalName>${f}</finalName></build>")),
                Arguments.of("shared dependency", project("<properties><f>7.5.1</f></properties><dependencies>" +
                        dep("${f}") + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${f}</version>" +
                        "</dependency></dependencies>")),
                Arguments.of("duplicate", project("<properties><f>7.5.1</f><f>7.5.1</f></properties><dependencies>" +
                        dep("${f}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><f>7.5.1</f></properties><dependencies>" +
                        dep("${f}") + "</dependencies><x value=\"${f}\"/>")));
    }

    @Test
    void upgradesRealWorldMavenPattern() {
        // Direct Maven form used by the fixed real repositories documented in README.md.
        rewriteRun(xml(pom("7.5.1"), pom("9.2.3"), source -> source.path("service/pom.xml")));
    }

    @Test
    void upgradesRootKotlinGradlePattern() {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"io.appium:java-client:7.5.1\") }",
                "dependencies { implementation(\"io.appium:java-client:9.2.3\") }"));
    }

    @Test
    void upgradesGroovyStringAndMapNotation() {
        rewriteRun(
                buildGradle("dependencies { implementation 'io.appium:java-client:7.5.1' }",
                        "dependencies { implementation 'io.appium:java-client:9.2.3' }"),
                buildGradle("dependencies { runtimeOnly group: 'io.appium', name: 'java-client', version: '7.5.1', transitive: false }",
                        "dependencies { runtimeOnly group: 'io.appium', name: 'java-client', version: '9.2.3', transitive: false }"));
    }

    @ParameterizedTest(name = "nested Gradle scope NOOP: {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleScopes(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'io.appium:java-client:7.5.1'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':client') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom", "company { dependencies { implementation " + d + " } }"),
                Arguments.of("selected invocation", "dependencies { helper.implementation " + d + " }"));
    }

    @ParameterizedTest(name = "out-of-workbook {0}")
    @ValueSource(strings = {"7.4.1", "7.5.0", "7.6.0", "8.0.0", "9.2.2", "9.2.4", "10.0.0"})
    void doesNotGuessOtherFixedVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "unowned or nonfixed {0}")
    @ValueSource(strings = {"${appium.version}", "[7,10)", "[7.5,)", "9.+", "+", "latest.release"})
    void doesNotGuessDynamicOrExternalOwners(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void versionlessBomOwnedAndVariantsAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + "</dependencies>"), source -> source.path("bom/pom.xml")),
                xml(project("<dependencies>" + dep("7.5.1", "<classifier>tests</classifier>") + "</dependencies>"), source -> source.path("classified/pom.xml")),
                xml(project("<dependencies>" + dep("7.5.1", "<type>zip</type>") + "</dependencies>"), source -> source.path("zip/pom.xml")),
                buildGradle("dependencies { implementation 'io.appium:java-client:7.5.1:tests' }"),
                buildGradle("dependencies { implementation 'io.appium:java-client:7.5.1@zip' }"));
    }

    @ParameterizedTest(name = "generated/cache parent {0}")
    @ValueSource(strings = {"target", "generatedSources", "GENERATED-code", "installation", "INSTALL-cache",
            ".gradle", ".m2", "reports", ".output", "test-results", "storybook-static", "tmp", "TEMP", ".vite",
            ".nuxt", "node_modules"})
    void generatedAndCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("7.5.1"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void leafFilenamesBeginningInstallAreStillProcessed() {
        rewriteRun(
                buildGradle("dependencies { implementation 'io.appium:java-client:7.5.1' }",
                        "dependencies { implementation 'io.appium:java-client:9.2.3' }",
                        source -> source.path("install.gradle")),
                xml(pom("7.5.1"), pom("9.2.3"), source -> source.path("pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("7.5.1"), pom("9.2.3"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'io.appium:java-client:7.5.1' }",
                        "dependencies { implementation 'io.appium:java-client:9.2.3' }"));
    }

    @Test
    void publicLowLevelRecipeContainsOnlyStrictUpgradeAndRecommendedReusesIt() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.appium").build();
        var migrate = environment.activateRecipes(
                "com.huawei.clouds.openrewrite.appium.MigrateAppiumJavaClientTo9_2_3");
        assertEquals("com.huawei.clouds.openrewrite.appium.UpgradeAppiumJavaClientTo9_2_3",
                migrate.getRecipeList().get(0).getName());
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.appium.UpgradeAppiumJavaClientTo9_2_3")),
                xml(pom("7.5.1"), pom("9.2.3"), source -> source.path("pom.xml")));
    }

    static String pom(String version) {
        return project("<dependencies>" + dep(version) + "</dependencies>");
    }

    static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>client</artifactId><version>1</version>" + body + "</project>";
    }

    static String dep(String version) {
        return dep(version, "");
    }

    static String dep(String version, String extra) {
        return "<dependency><groupId>io.appium</groupId><artifactId>java-client</artifactId>" +
               "<version>" + version + "</version>" + extra + "</dependency>";
    }

    static String depWithoutVersion() {
        return "<dependency><groupId>io.appium</groupId><artifactId>java-client</artifactId></dependency>";
    }
}
