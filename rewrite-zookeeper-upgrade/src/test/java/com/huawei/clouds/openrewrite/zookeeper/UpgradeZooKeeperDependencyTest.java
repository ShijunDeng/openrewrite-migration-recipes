package com.huawei.clouds.openrewrite.zookeeper;

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

class UpgradeZooKeeperDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedZooKeeperDependency());
    }

    @ParameterizedTest(name = "approved Maven source {0}")
    @ValueSource(strings = {"3.4.14", "3.6.0", "3.7.1", "3.8.3", "3.8.4"})
    void upgradesEveryApprovedMavenSource(String version) {
        rewriteRun(xml(pom(version), pom("3.8.6"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "approved Groovy source {0}")
    @ValueSource(strings = {"3.4.14", "3.6.0", "3.7.1", "3.8.3", "3.8.4"})
    void upgradesEveryApprovedGroovySource(String version) {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.apache.zookeeper:zookeeper:" + version + "' }",
                "dependencies { implementation 'org.apache.zookeeper:zookeeper:3.8.6' }"));
    }

    @ParameterizedTest(name = "approved Kotlin source {0}")
    @ValueSource(strings = {"3.4.14", "3.6.0", "3.7.1", "3.8.3", "3.8.4"})
    void upgradesEveryApprovedKotlinSource(String version) {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.apache.zookeeper:zookeeper:" + version + "\") }",
                "dependencies { implementation(\"org.apache.zookeeper:zookeeper:3.8.6\") }"));
    }

    @Test
    void whitelistSeparatesApprovedUpgradesFromConflicts() {
        assertEquals(Set.of("3.4.14", "3.6.0", "3.7.1", "3.8.3", "3.8.4"),
                ZooKeeperSupport.AUTO_SOURCES);
        assertEquals(Set.of("3.9.3", "3.9.4"), ZooKeeperSupport.PRIORITY_SOURCES.stream()
                .filter(ZooKeeperSupport::higherThanTarget)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(7, ZooKeeperSupport.PRIORITY_SOURCES.size());
    }

    @ParameterizedTest(name = "never downgrades {0}")
    @ValueSource(strings = {"3.8.7", "3.9.0", "3.9.3", "3.9.4", "3.10.0", "4.0.0"})
    void neverDowngradesHigherSources(String version) {
        rewriteRun(
                xml(pom(version), source -> source.path("maven/pom.xml")),
                buildGradle("dependencies { implementation 'org.apache.zookeeper:zookeeper:" + version + "' }"),
                buildGradleKts("dependencies { implementation(\"org.apache.zookeeper:zookeeper:" + version + "\") }"));
    }

    @ParameterizedTest(name = "outside fixed source {0}")
    @ValueSource(strings = {
            "3.4.13", "3.4.15", "3.5.6-hw-ei-302002", "3.5.10", "3.6.1",
            "3.6.3-hw-ei-312002", "3.6.4", "3.7.0", "3.7.2",
            "3.8.0", "3.8.1", "3.8.2", "3.8.5", "3.8.7", "3.9.2", "3.10.0"
    })
    void doesNotGuessOtherFixedVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "unowned version {0}")
    @ValueSource(strings = {"${zookeeper.version}", "[3.4,3.9)", "[3.8,)", "3.8.+", "+", "latest.release"})
    void doesNotGuessDynamicOrExternalOwners(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveRootProperty() {
        rewriteRun(xml(
                project("<properties><zookeeper.version>3.7.1</zookeeper.version></properties><dependencies>" +
                        dep("${zookeeper.version}") + "</dependencies>"),
                project("<properties><zookeeper.version>3.8.6</zookeeper.version></properties><dependencies>" +
                        dep("${zookeeper.version}") + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveProfilePropertyWithoutTouchingRoot() {
        rewriteRun(xml(
                project("<properties><v>3.9.4</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>3.6.0</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies></profile></profiles>"),
                project("<properties><v>3.9.4</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>3.8.6</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property {0}")
    @MethodSource("ambiguousProperties")
    void leavesAmbiguousPropertiesUntouched(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("shared dependency", project(
                        "<properties><v>3.7.1</v></properties><dependencies>" + dep("${v}") +
                        "<dependency><groupId>org.apache.curator</groupId><artifactId>curator-framework</artifactId>" +
                        "<version>${v}</version></dependency></dependencies>")),
                Arguments.of("shared build", project(
                        "<properties><v>3.8.3</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("duplicate", project(
                        "<properties><v>3.8.4</v><v>3.8.4</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies>")),
                Arguments.of("attribute", project(
                        "<properties><v>3.6.0</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies><x value=\"${v}\"/>")),
                Arguments.of("profile shadow", project(
                        "<properties><v>3.7.1</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies><profiles><profile><id>x</id><properties>" +
                        "<v>3.8.4</v></properties></profile></profiles>")));
    }

    @Test
    void upgradesDependencyManagementAndGroovyMapForms() {
        rewriteRun(
                xml(project("<dependencyManagement><dependencies>" + dep("3.8.3") +
                        "</dependencies></dependencyManagement>"),
                        project("<dependencyManagement><dependencies>" + dep("3.8.6") +
                                "</dependencies></dependencyManagement>"), source -> source.path("pom.xml")),
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.apache.zookeeper', name: 'zookeeper', version: '3.8.4', transitive: false }",
                        "dependencies { runtimeOnly group: 'org.apache.zookeeper', name: 'zookeeper', version: '3.8.6', transitive: false }"),
                buildGradle(
                        "dependencies { implementation([group: 'org.apache.zookeeper', name: 'zookeeper', version: '3.4.14']) }",
                        "dependencies { implementation([group: 'org.apache.zookeeper', name: 'zookeeper', version: '3.8.6']) }"));
    }

    @ParameterizedTest(name = "nested Gradle scope {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleScopes(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'org.apache.zookeeper:zookeeper:3.7.1'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':app') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom DSL", "company { dependencies { implementation " + d + " } }"));
    }

    @Test
    void variantsVersionlessPluginAndLookalikesAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + "</dependencies>"),
                        source -> source.path("managed/pom.xml")),
                xml(project("<dependencies>" + dep("3.7.1", "<classifier>tests</classifier>") +
                        dep("3.8.3", "<type>zip</type>") + "</dependencies>"),
                        source -> source.path("variant/pom.xml")),
                xml(project("<build><plugins><plugin><artifactId>x</artifactId><dependencies>" +
                        dep("3.8.4") + "</dependencies></plugin></plugins></build>"),
                        source -> source.path("plugin/pom.xml")),
                xml(project("<dependencies><dependency><groupId>org.apache.zookeeperx</groupId>" +
                        "<artifactId>zookeeper</artifactId><version>3.7.1</version></dependency>" +
                        "<dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeperx</artifactId>" +
                        "<version>3.8.4</version></dependency></dependencies>"), source -> source.path("look/pom.xml")),
                buildGradle("dependencies { implementation 'org.apache.zookeeper:zookeeper:3.7.1:tests' }"),
                buildGradle("dependencies { implementation 'org.apache.zookeeper:zookeeper:3.8.3@zip' }"));
    }

    @ParameterizedTest(name = "generated parent {0}")
    @ValueSource(strings = {
            "target", "build", "generatedSources", "GENERATED-code", "installation", "INSTALL-cache",
            ".gradle", ".m2", "reports", "test-results", "tmp", "TEMP", "node_modules"
    })
    void generatedAndCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("3.7.1"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("3.8.4"), pom("3.8.6"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'org.apache.zookeeper:zookeeper:3.6.0' }",
                        "dependencies { implementation 'org.apache.zookeeper:zookeeper:3.8.6' }"));
    }

    @Test
    void publicStrictRecipeContainsOnlyTheSelectedUpgrade() {
        var recipe = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.zookeeper").build()
                .activateRecipes("com.huawei.clouds.openrewrite.zookeeper.UpgradeZooKeeperTo3_8_6");
        assertEquals("com.huawei.clouds.openrewrite.zookeeper.UpgradeSelectedZooKeeperDependency",
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
        return "<dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeper</artifactId>" +
               "<version>" + version + "</version>" + extra + "</dependency>";
    }

    static String depWithoutVersion() {
        return "<dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeper</artifactId></dependency>";
    }
}
