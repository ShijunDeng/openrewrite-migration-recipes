package com.huawei.clouds.openrewrite.log4jcore;

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

class UpgradeLog4jCoreDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedLog4jCoreDependency());
    }

    @ParameterizedTest(name = "workbook source {0}")
    @ValueSource(strings = {"2.13.3", "2.17.0", "2.17.1", "2.17.2", "2.18.0",
            "2.19.0", "2.20.0", "2.23.1", "2.24.1", "2.25.3"})
    void upgradesEveryWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("2.25.5"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesCoordinateRows() {
        assertEquals(Set.of("2.13.3", "2.17.0", "2.17.1", "2.17.2", "2.18.0",
                "2.19.0", "2.20.0", "2.23.1", "2.24.1", "2.25.3"),
                Log4jCoreSupport.SOURCES);
    }

    @Test
    void upgradesExclusiveRootProperty() {
        rewriteRun(xml(
                project("<properties><log4j.version>2.13.3</log4j.version></properties><dependencies>" +
                        dep("${log4j.version}") + "</dependencies>"),
                project("<properties><log4j.version>2.25.5</log4j.version></properties><dependencies>" +
                        dep("${log4j.version}") + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWinsWithoutLeakingToRoot() {
        rewriteRun(xml(
                project("<properties><v>1</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>2.17.2</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies></profile></profiles>"),
                project("<properties><v>1</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>2.25.5</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void rootPropertyIsVisibleInProfile() {
        rewriteRun(xml(
                project("<properties><v>2.20.0</v></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${v}") + "</dependencies></profile></profiles>"),
                project("<properties><v>2.25.5</v></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${v}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + dep("2.13.3") +
                        "</dependencies></dependencyManagement><profiles><profile><id>legacy</id>" +
                        "<dependencyManagement><dependencies>" + dep("2.24.1") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("2.25.5") +
                        "</dependencies></dependencyManagement><profiles><profile><id>legacy</id>" +
                        "<dependencyManagement><dependencies>" + dep("2.25.5") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property {0}")
    @MethodSource("ambiguousProperties")
    void leavesAmbiguousPropertiesUntouched(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("unused", project("<properties><v>2.13.3</v></properties>")),
                Arguments.of("shared plugin", project("<properties><v>2.13.3</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("shared api", project("<properties><v>2.17.2</v></properties><dependencies>" +
                        dep("${v}") + "<dependency><groupId>org.apache.logging.log4j</groupId>" +
                        "<artifactId>log4j-api</artifactId><version>${v}</version></dependency></dependencies>")),
                Arguments.of("duplicate", project("<properties><v>2.20.0</v><v>2.20.0</v></properties>" +
                        "<dependencies>" + dep("${v}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><v>2.24.1</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><x value=\"${v}\"/>")));
    }

    @Test
    void upgradesGroovyStringMapAndMapLiteralNotation() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.13.3' }",
                        "dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.25.5' }"),
                buildGradle("dependencies { runtimeOnly group: 'org.apache.logging.log4j', name: 'log4j-core', version: '2.24.1', transitive: false }",
                        "dependencies { runtimeOnly group: 'org.apache.logging.log4j', name: 'log4j-core', version: '2.25.5', transitive: false }"),
                buildGradle("dependencies { implementation([group: 'org.apache.logging.log4j', name: 'log4j-core', version: '2.25.3']) }",
                        "dependencies { implementation([group: 'org.apache.logging.log4j', name: 'log4j-core', version: '2.25.5']) }"));
    }

    @Test
    void upgradesKotlinGradleNotation() {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.apache.logging.log4j:log4j-core:2.19.0\") }",
                "dependencies { implementation(\"org.apache.logging.log4j:log4j-core:2.25.5\") }"));
    }

    @ParameterizedTest(name = "nested Gradle NOOP {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleScopes(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'org.apache.logging.log4j:log4j-core:2.13.3'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':app') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom", "company { dependencies { implementation " + d + " } }"),
                Arguments.of("selected", "dependencies { helper.implementation " + d + " }"));
    }

    @ParameterizedTest(name = "out-of-workbook {0}")
    @ValueSource(strings = {"2.13.2", "2.13.4", "2.16.0", "2.17.3", "2.21.0",
            "2.22.1", "2.23.0", "2.24.0", "2.24.2", "2.25.0", "2.25.4", "2.26.0"})
    void doesNotGuessOtherFixedVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "unowned {0}")
    @ValueSource(strings = {"${log4j.version}", "[2.13,3)", "[2.24,)", "2.+", "+", "latest.release"})
    void doesNotGuessDynamicOrExternalOwners(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void versionlessBomOwnedAndVariantsAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + "</dependencies>"),
                        source -> source.path("bom/pom.xml")),
                xml(project("<dependencies>" + dep("2.13.3", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("classified/pom.xml")),
                xml(project("<dependencies>" + dep("2.24.1", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("zip/pom.xml")),
                buildGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.17.1:tests' }"),
                buildGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.20.0@zip' }"));
    }

    @ParameterizedTest(name = "generated parent {0}")
    @ValueSource(strings = {"target", "generatedSources", "GENERATED-code", "installation",
            "INSTALL-cache", ".gradle", ".m2", "reports", "test-results", "tmp", "TEMP", "node_modules"})
    void generatedAndCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("2.13.3"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void wrongCoordinatesAndPluginDependenciesAreNoop() {
        rewriteRun(
                xml(project("<dependencies><dependency><groupId>example</groupId>" +
                        "<artifactId>log4j-core</artifactId><version>2.13.3</version></dependency>" +
                        "</dependencies>"), source -> source.path("pom.xml")),
                xml(project("<build><plugins><plugin><artifactId>p</artifactId><dependencies>" +
                        dep("2.13.3") + "</dependencies></plugin></plugins></build>"),
                        source -> source.path("plugin/pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("2.17.0"), pom("2.25.5"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.25.3' }",
                        "dependencies { implementation 'org.apache.logging.log4j:log4j-core:2.25.5' }"));
    }

    @Test
    void publicStrictRecipeContainsOnlySelectedUpgrade() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.log4jcore").build();
        var recipe = environment.activateRecipes(
                "com.huawei.clouds.openrewrite.log4jcore.UpgradeLog4jCoreTo2_25_5");
        assertEquals("com.huawei.clouds.openrewrite.log4jcore.UpgradeSelectedLog4jCoreDependency",
                recipe.getRecipeList().get(0).getName());
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
        return "<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId>" +
               "<version>" + version + "</version>" + extra + "</dependency>";
    }

    static String depWithoutVersion() {
        return "<dependency><groupId>org.apache.logging.log4j</groupId>" +
               "<artifactId>log4j-core</artifactId></dependency>";
    }
}
