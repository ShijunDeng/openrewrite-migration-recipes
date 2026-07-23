package com.huawei.clouds.openrewrite.bson;

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

class UpgradeBsonDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedBsonDependency());
    }

    @ParameterizedTest(name = "workbook source {0}")
    @ValueSource(strings = {"3.12.14", "4.7.2"})
    void upgradesEveryWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("5.4.0"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesWorkbookSequences704And1926() {
        assertEquals(Set.of("3.12.14", "4.7.2"), BsonSupport.SOURCES);
    }

    @Test
    void upgradesExclusiveRootProperty() {
        rewriteRun(xml(
                project("<properties><bson.version>3.12.14</bson.version></properties><dependencies>" +
                        dep("${bson.version}") + "</dependencies>"),
                project("<properties><bson.version>5.4.0</bson.version></properties><dependencies>" +
                        dep("${bson.version}") + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWinsWithoutLeakingToRoot() {
        rewriteRun(xml(
                project("<properties><v>1.0</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>4.7.2</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies></profile></profiles>"),
                project("<properties><v>1.0</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>5.4.0</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void rootPropertyIsVisibleInProfile() {
        rewriteRun(xml(
                project("<properties><v>3.12.14</v></properties><profiles><profile><id>it</id><dependencies>" +
                        dep("${v}") + "</dependencies></profile></profiles>"),
                project("<properties><v>5.4.0</v></properties><profiles><profile><id>it</id><dependencies>" +
                        dep("${v}") + "</dependencies></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + dep("3.12.14") +
                        "</dependencies></dependencyManagement><profiles><profile><id>legacy</id>" +
                        "<dependencyManagement><dependencies>" + dep("4.7.2") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("5.4.0") +
                        "</dependencies></dependencyManagement><profiles><profile><id>legacy</id>" +
                        "<dependencyManagement><dependencies>" + dep("5.4.0") +
                        "</dependencies></dependencyManagement></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property {0}")
    @MethodSource("ambiguousProperties")
    void leavesAmbiguousPropertiesUntouched(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("unused", project("<properties><v>3.12.14</v></properties>")),
                Arguments.of("shared plugin", project("<properties><v>3.12.14</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("shared dependency", project("<properties><v>3.12.14</v></properties><dependencies>" +
                        dep("${v}") + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${v}</version>" +
                        "</dependency></dependencies>")),
                Arguments.of("duplicate", project("<properties><v>3.12.14</v><v>3.12.14</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><v>3.12.14</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><x value=\"${v}\"/>")));
    }

    @Test
    void upgradesGroovyStringAndMapNotation() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.mongodb:bson:3.12.14' }",
                        "dependencies { implementation 'org.mongodb:bson:5.4.0' }"),
                buildGradle("dependencies { runtimeOnly group: 'org.mongodb', name: 'bson', version: '4.7.2', transitive: false }",
                        "dependencies { runtimeOnly group: 'org.mongodb', name: 'bson', version: '5.4.0', transitive: false }"),
                buildGradle("dependencies { implementation([group: 'org.mongodb', name: 'bson', version: '3.12.14']) }",
                        "dependencies { implementation([group: 'org.mongodb', name: 'bson', version: '5.4.0']) }"));
    }

    @Test
    void upgradesKotlinGradleNotation() {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.mongodb:bson:4.7.2\") }",
                "dependencies { implementation(\"org.mongodb:bson:5.4.0\") }"));
    }

    @ParameterizedTest(name = "nested Gradle NOOP {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleScopes(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'org.mongodb:bson:3.12.14'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':data') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom", "company { dependencies { implementation " + d + " } }"),
                Arguments.of("selected", "dependencies { helper.implementation " + d + " }"));
    }

    @ParameterizedTest(name = "out-of-workbook {0}")
    @ValueSource(strings = {"3.12.13", "3.12.15", "4.0.0", "4.7.1", "4.7.3", "4.11.0", "5.0.0", "5.3.1", "5.4.1"})
    void doesNotGuessOtherFixedVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "unowned {0}")
    @ValueSource(strings = {"${bson.version}", "[3,6)", "[4.0,)", "4.+", "+", "latest.release"})
    void doesNotGuessDynamicOrExternalOwners(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void versionlessBomOwnedAndVariantsAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + "</dependencies>"), source -> source.path("bom/pom.xml")),
                xml(project("<dependencies>" + dep("3.12.14", "<classifier>tests</classifier>") + "</dependencies>"),
                        source -> source.path("classified/pom.xml")),
                xml(project("<dependencies>" + dep("4.7.2", "<type>zip</type>") + "</dependencies>"),
                        source -> source.path("zip/pom.xml")),
                buildGradle("dependencies { implementation 'org.mongodb:bson:3.12.14:tests' }"),
                buildGradle("dependencies { implementation 'org.mongodb:bson:4.7.2@zip' }"));
    }

    @ParameterizedTest(name = "generated parent {0}")
    @ValueSource(strings = {"target", "generatedSources", "GENERATED-code", "installation", "INSTALL-cache",
            ".gradle", ".m2", "reports", "test-results", "tmp", "TEMP", "node_modules"})
    void generatedAndCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("3.12.14"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void wrongCoordinatesAndPluginDependenciesAreNoop() {
        rewriteRun(
                xml(project("<dependencies><dependency><groupId>example</groupId><artifactId>bson</artifactId>" +
                        "<version>3.12.14</version></dependency></dependencies>"), source -> source.path("pom.xml")),
                xml(project("<build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><dependencies>" +
                        dep("3.12.14") + "</dependencies></plugin></plugins></build>"), source -> source.path("plugin/pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("3.12.14"), pom("5.4.0"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'org.mongodb:bson:4.7.2' }",
                        "dependencies { implementation 'org.mongodb:bson:5.4.0' }"));
    }

    @Test
    void publicStrictRecipeContainsOnlySelectedUpgrade() {
        Environment environment = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.bson").build();
        var recipe = environment.activateRecipes("com.huawei.clouds.openrewrite.bson.UpgradeBsonTo5_4_0");
        assertEquals("com.huawei.clouds.openrewrite.bson.UpgradeSelectedBsonDependency",
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
        return "<dependency><groupId>org.mongodb</groupId><artifactId>bson</artifactId><version>" +
               version + "</version>" + extra + "</dependency>";
    }

    static String depWithoutVersion() {
        return "<dependency><groupId>org.mongodb</groupId><artifactId>bson</artifactId></dependency>";
    }
}
