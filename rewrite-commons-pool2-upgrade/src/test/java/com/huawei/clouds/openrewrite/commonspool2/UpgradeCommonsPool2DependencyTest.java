package com.huawei.clouds.openrewrite.commonspool2;

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

class UpgradeCommonsPool2DependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedCommonsPool2Dependency());
    }

    @ParameterizedTest(name = "workbook source {0}")
    @ValueSource(strings = {"2.6.0", "2.8.0", "2.8.1", "2.9.0", "2.10.0", "2.11.1"})
    void upgradesEveryAndOnlyWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("2.13.1"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesWorkbookRows2607Through2612() {
        assertEquals(Set.of("2.6.0", "2.8.0", "2.8.1", "2.9.0", "2.10.0", "2.11.1"), CommonsPool2Support.SOURCES);
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(project("<dependencyManagement><dependencies>" + dep("2.6.0") +
                        "</dependencies></dependencyManagement><profiles><profile><id>it</id><dependencyManagement><dependencies>" +
                        dep("2.11.1") + "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("2.13.1") +
                        "</dependencies></dependencyManagement><profiles><profile><id>it</id><dependencyManagement><dependencies>" +
                        dep("2.13.1") + "</dependencies></dependencyManagement></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveRootPropertyVisibleInProfile() {
        rewriteRun(xml(project("<properties><pool.version>2.8.0</pool.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${pool.version}") + "</dependencies></profile></profiles>"),
                project("<properties><pool.version>2.13.1</pool.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${pool.version}") + "</dependencies></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWinsWithoutLeakingToRoot() {
        rewriteRun(xml(project("<properties><v>2.7.0</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>2.9.0</v></properties><dependencies>" + dep("${v}") + "</dependencies></profile></profiles>"),
                project("<properties><v>2.7.0</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>2.13.1</v></properties><dependencies>" + dep("${v}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property {0}")
    @MethodSource("ambiguousProperties")
    void preservesSharedOrAmbiguousProperties(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("shared dependency", project("<properties><v>2.8.1</v></properties><dependencies>" + dep("${v}") +
                        "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${v}</version></dependency></dependencies>")),
                Arguments.of("shared build", project("<properties><v>2.8.1</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("duplicate", project("<properties><v>2.8.1</v><v>2.8.1</v></properties><dependencies>" + dep("${v}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><v>2.8.1</v></properties><dependencies>" + dep("${v}") + "</dependencies><x value=\"${v}\"/>")));
    }

    @ParameterizedTest(name = "outside whitelist {0}")
    @ValueSource(strings = {"2.5.0", "2.7.0", "2.10.1", "2.11.0", "2.12.0", "2.12.1", "2.13.0", "2.13.1", "3.0.0"})
    void doesNotWidenWorkbookSources(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "external/dynamic {0}")
    @ValueSource(strings = {"${pool.version}", "[2.8,3)", "2.+", "+", "latest.release"})
    void doesNotGuessUnownedVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void protectsMavenVariantsVersionlessAndNestedOwners() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + dep("2.6.0", "<classifier>tests</classifier>") +
                        dep("2.8.0", "<type>zip</type>") + "</dependencies>"), source -> source.path("pom.xml")),
                xml(project("<build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><dependencies>" +
                        dep("2.8.1") + "</dependencies></plugin></plugins></build>"), source -> source.path("plugin/pom.xml")),
                xml(project("<company><dependencies>" + dep("2.9.0") + "</dependencies></company>"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootGradleGroovyAndKotlinFormsIncludingEmptyRootPaths() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.apache.commons:commons-pool2:2.6.0' }",
                        "dependencies { implementation 'org.apache.commons:commons-pool2:2.13.1' }", source -> source.path("build.gradle")),
                buildGradleKts("dependencies { implementation(\"org.apache.commons:commons-pool2:2.11.1\") }",
                        "dependencies { implementation(\"org.apache.commons:commons-pool2:2.13.1\") }", source -> source.path("build.gradle.kts")));
    }

    @Test
    void upgradesGroovyMapNotations() {
        rewriteRun(
                buildGradle("dependencies { runtimeOnly group: 'org.apache.commons', name: 'commons-pool2', version: '2.8.0', transitive: false }",
                        "dependencies { runtimeOnly group: 'org.apache.commons', name: 'commons-pool2', version: '2.13.1', transitive: false }"),
                buildGradle("dependencies { implementation([group: 'org.apache.commons', name: 'commons-pool2', version: '2.9.0']) }",
                        "dependencies { implementation([group: 'org.apache.commons', name: 'commons-pool2', version: '2.13.1']) }"));
    }

    @ParameterizedTest(name = "nested Gradle NOOP {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleDsl(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'org.apache.commons:commons-pool2:2.6.0'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':app') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom", "custom { dependencies { implementation " + d + " } }"),
                Arguments.of("selected", "dependencies { helper.implementation " + d + " }"));
    }

    @Test
    void protectsGradleVariantsVariablesCatalogsAndWrongCoordinates() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.apache.commons:commons-pool2:2.6.0:tests' }"),
                buildGradle("dependencies { implementation 'org.apache.commons:commons-pool2:2.6.0@zip' }"),
                buildGradle("dependencies { implementation \"org.apache.commons:commons-pool2:$poolVersion\" }"),
                buildGradle("dependencies { implementation libs.commons.pool2 }"),
                buildGradle("dependencies { implementation 'org.apache.commons:commons-pool:2.6.0' }"),
                buildGradle("dependencies { implementation 'commons-pool:commons-pool2:2.6.0' }"));
    }

    @ParameterizedTest(name = "generated parent {0}")
    @ValueSource(strings = {"target", "build", "generated", "generatedSources", "installation", "INSTALL-cache", ".gradle", ".m2", "node_modules"})
    void ignoresGeneratedAndCacheParents(String parent) {
        rewriteRun(xml(pom("2.6.0"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void eligibleLeafNamesAndTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradle("dependencies { implementation 'org.apache.commons:commons-pool2:2.8.1' }",
                        "dependencies { implementation 'org.apache.commons:commons-pool2:2.13.1' }", source -> source.path("install.gradle")),
                xml(pom("2.10.0"), pom("2.13.1"), source -> source.path("pom.xml")));
    }

    @Test
    void publicRecipeIsStrictAndRecommendedReusesItFirst() {
        Environment environment = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.commonspool2").build();
        var upgrade = environment.activateRecipes("com.huawei.clouds.openrewrite.commonspool2.UpgradeCommonsPool2To2_13_1");
        var migrate = environment.activateRecipes("com.huawei.clouds.openrewrite.commonspool2.MigrateCommonsPool2To2_13_1");
        assertEquals(1, upgrade.getRecipeList().size());
        assertEquals(UpgradeSelectedCommonsPool2Dependency.class, upgrade.getRecipeList().get(0).getClass());
        assertEquals("com.huawei.clouds.openrewrite.commonspool2.UpgradeCommonsPool2To2_13_1", migrate.getRecipeList().get(0).getName());
    }

    static String pom(String version) { return project("<dependencies>" + dep(version) + "</dependencies>"); }
    static String project(String body) { return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>pool-app</artifactId><version>1</version>" + body + "</project>"; }
    static String dep(String version) { return dep(version, ""); }
    static String dep(String version, String extra) { return "<dependency><groupId>org.apache.commons</groupId><artifactId>commons-pool2</artifactId><version>" + version + "</version>" + extra + "</dependency>"; }
    static String depWithoutVersion() { return "<dependency><groupId>org.apache.commons</groupId><artifactId>commons-pool2</artifactId></dependency>"; }
}
