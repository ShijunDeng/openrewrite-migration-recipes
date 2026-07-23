package com.huawei.clouds.openrewrite.commonscodec;

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

class UpgradeCommonsCodecDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) { spec.recipe(new UpgradeSelectedCommonsCodecDependency()); }

    @ParameterizedTest(name = "workbook source {0}")
    @ValueSource(strings = {"1.11", "1.13", "1.14", "1.15", "1.16.0"})
    void upgradesEveryWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("1.22.0"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesRows2284To2288() {
        assertEquals(Set.of("1.11", "1.13", "1.14", "1.15", "1.16.0"),
                UpgradeSelectedCommonsCodecDependency.SOURCE_VERSIONS);
    }

    @ParameterizedTest(name = "exclusive property {0}")
    @ValueSource(strings = {"1.11", "1.13", "1.14", "1.15", "1.16.0"})
    void upgradesExclusiveRootProperty(String version) {
        rewriteRun(xml(
                project("<properties><rest.version>" + version + "</rest.version></properties><dependencies>" +
                        dep("${rest.version}") + "</dependencies>"),
                project("<properties><rest.version>1.22.0</rest.version></properties><dependencies>" +
                        dep("${rest.version}") + "</dependencies>"), source -> source.path("pom.xml")));
    }

    @Test
    void rootPropertyVisibleInProfileAndProfileOverrideWins() {
        rewriteRun(
                xml(project("<properties><r>1.11</r></properties><profiles><profile><id>it</id><dependencies>" + dep("${r}") + "</dependencies></profile></profiles>"),
                        project("<properties><r>1.22.0</r></properties><profiles><profile><id>it</id><dependencies>" + dep("${r}") + "</dependencies></profile></profiles>"), source -> source.path("pom.xml")),
                xml(project("<properties><r>2.0</r></properties><profiles><profile><id>it</id><properties><r>1.13</r></properties><dependencies>" + dep("${r}") + "</dependencies></profile></profiles>"),
                        project("<properties><r>2.0</r></properties><profiles><profile><id>it</id><properties><r>1.22.0</r></properties><dependencies>" + dep("${r}") + "</dependencies></profile></profiles>"), source -> source.path("profile/pom.xml")));
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + dep("1.11") + "</dependencies></dependencyManagement>" +
                        "<profiles><profile><id>p</id><dependencyManagement><dependencies>" + dep("1.16.0") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("1.22.0") + "</dependencies></dependencyManagement>" +
                        "<profiles><profile><id>p</id><dependencyManagement><dependencies>" + dep("1.22.0") +
                        "</dependencies></dependencyManagement></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property {0}")
    @MethodSource("ambiguousProperties")
    void ambiguousPropertiesAreNoop(String label, String source) { rewriteRun(xml(source, s -> s.path("pom.xml"))); }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("unused", project("<properties><r>1.11</r></properties>")),
                Arguments.of("plugin", project("<properties><r>1.11</r></properties><dependencies>" + dep("${r}") + "</dependencies><build><finalName>${r}</finalName></build>")),
                Arguments.of("other dependency", project("<properties><r>1.13</r></properties><dependencies>" + dep("${r}") + rawDep("x", "y", "${r}", "") + "</dependencies>")),
                Arguments.of("duplicate", project("<properties><r>1.16.0</r><r>1.16.0</r></properties><dependencies>" + dep("${r}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><r>1.16.0</r></properties><dependencies>" + dep("${r}") + "</dependencies><x value=\"${r}\"/>")),
                Arguments.of("root use not changed by unused profile override", project("<properties><r>2.0</r></properties><dependencies>" + dep("${r}") + "</dependencies><profiles><profile><id>x</id><properties><r>1.11</r></properties></profile></profiles>")),
                Arguments.of("profile shared", project("<profiles><profile><id>x</id><properties><r>1.11</r></properties><dependencies>" + dep("${r}") + rawDep("x", "y", "${r}", "") + "</dependencies></profile></profiles>")),
                Arguments.of("embedded expression", project("<properties><r>1.11</r></properties><dependencies>" + dep("${r}-custom") + "</dependencies>"))
        );
    }

    @ParameterizedTest(name = "invalid Maven owner {0}")
    @MethodSource("invalidMavenOwners")
    void emptyRootAndFirstLevelNestedOwnersAreNoop(String label, String source) {
        rewriteRun(xml(source, s -> s.path("pom.xml")));
    }

    static Stream<Arguments> invalidMavenOwners() {
        String d = dep("1.11");
        return Stream.of(
                Arguments.of("empty root", "<project/>"),
                Arguments.of("dependency directly under project", project(d)),
                Arguments.of("first-level build", project("<build><dependencies>" + d + "</dependencies></build>")),
                Arguments.of("first-level reporting", project("<reporting><dependencies>" + d + "</dependencies></reporting>")),
                Arguments.of("plugin dependency", project("<build><plugins><plugin><dependencies>" + d + "</dependencies></plugin></plugins></build>")),
                Arguments.of("nested module", project("<module><dependencies>" + d + "</dependencies></module>")),
                Arguments.of("profiles wrapper without profile", project("<profiles><dependencies>" + d + "</dependencies></profiles>")),
                Arguments.of("profile nested build", project("<profiles><profile><id>x</id><build><dependencies>" + d + "</dependencies></build></profile></profiles>")),
                Arguments.of("dependencyManagement wrong child", project("<dependencyManagement>" + d + "</dependencyManagement>")),
                Arguments.of("arbitrary first level", project("<company><dependencies>" + d + "</dependencies></company>"))
        );
    }

    @ParameterizedTest(name = "Gradle source {0}")
    @ValueSource(strings = {"1.11", "1.13", "1.14", "1.15", "1.16.0"})
    void upgradesRootGradleGroovyAndKotlin(String version) {
        rewriteRun(
                buildGradle("dependencies { implementation 'commons-codec:commons-codec:" + version + "' }",
                        "dependencies { implementation 'commons-codec:commons-codec:1.22.0' }"),
                buildGradleKts("dependencies { compileOnly(\"commons-codec:commons-codec:" + version + "\") }",
                        "dependencies { compileOnly(\"commons-codec:commons-codec:1.22.0\") }"));
    }

    @Test
    void upgradesGroovyMapNotations() {
        rewriteRun(
                buildGradle("dependencies { implementation group: 'commons-codec', name: 'commons-codec', version: '1.11', transitive: false }",
                        "dependencies { implementation group: 'commons-codec', name: 'commons-codec', version: '1.22.0', transitive: false }"),
                buildGradle("dependencies { implementation([group: 'commons-codec', name: 'commons-codec', version: '1.16.0']) }",
                        "dependencies { implementation([group: 'commons-codec', name: 'commons-codec', version: '1.22.0']) }"));
    }

    @ParameterizedTest(name = "nested Gradle owner {0}")
    @MethodSource("nestedGradle")
    void nestedGradleScopesAreNoop(String label, String source) { rewriteRun(buildGradle(source)); }

    static Stream<Arguments> nestedGradle() {
        String d = "'commons-codec:commons-codec:1.11'";
        return Stream.of(
                Arguments.of("empty root", ""),
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("one-level subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("one-level allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("one-level project", "project(':api') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom", "company { dependencies { implementation " + d + " } }"),
                Arguments.of("selected call", "dependencies { helper.implementation " + d + " }"),
                Arguments.of("plugin", "plugins { id 'java' }; helper(" + d + ")"),
                Arguments.of("double nested", "subprojects { project(':api') { dependencies { implementation " + d + " } } }"),
                Arguments.of("configuration declaration", "configurations { implementation }; other " + d)
        );
    }

    @ParameterizedTest(name = "out-of-workbook {0}")
    @ValueSource(strings = {"2.0", "2.1", "2.1.1", "2.1.5", "2.1.7", "2.2.0", "3.0.1", "3.1.1", "3.1.2", "1.22.0", "4.0.1", "4.1.0", "1.0", "1.1", "1.2", "2.0.1", "3.2.0", "5.0.0"})
    void outOfWorkbookVersionsAreNoop(String version) { rewriteRun(xml(pom(version), s -> s.path("pom.xml"))); }

    @ParameterizedTest(name = "external/dynamic {0}")
    @ValueSource(strings = {"${rest.version}", "${revision}", "[2,5)", "[3.0,)", "(,4]", "3.+", "4.+", "+", "latest.release", "RELEASE"})
    void externalDynamicOwnersAreNoop(String version) { rewriteRun(xml(pom(version), s -> s.path("pom.xml"))); }

    @ParameterizedTest(name = "wrong coordinate {0}")
    @MethodSource("wrongCoordinates")
    void wrongCoordinatesAreNoop(String label, String dependency) {
        rewriteRun(xml(project("<dependencies>" + dependency + "</dependencies>"), s -> s.path("pom.xml")));
    }

    static Stream<Arguments> wrongCoordinates() {
        return Stream.of(
                Arguments.of("wrong group", rawDep("org.apache.commons", "commons-codec", "1.11", "")),
                Arguments.of("Commons IO", rawDep("commons-io", "commons-io", "1.11", "")),
                Arguments.of("Commons Lang", rawDep("org.apache.commons", "commons-lang3", "1.13", "")),
                Arguments.of("other artifact", rawDep("commons-codec", "other", "1.16.0", "")),
                Arguments.of("lookalike group", rawDep("commons-codecx", "commons-codec", "1.11", "")),
                Arguments.of("classified", rawDep("commons-codec", "commons-codec", "1.11", "<classifier>sources</classifier>")),
                Arguments.of("zip", rawDep("commons-codec", "commons-codec", "1.13", "<type>zip</type>")),
                Arguments.of("tests", rawDep("commons-codec", "commons-codec", "1.16.0", "<classifier>tests</classifier>"))
        );
    }

    @ParameterizedTest(name = "generated/cache {0}")
    @ValueSource(strings = {"target", "build", "out", "dist", "generated", "generated-code", "generatedSources", "GENERATED", "install", "installation", ".gradle", ".mvn", ".m2", ".idea", "node_modules", "vendor", ".cache", ".git", ".vite", "reports", "test-results", "tmp", "TEMP"})
    void generatedCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("1.11"), s -> s.path(parent + "/pom.xml")));
    }

    @Test
    void variantsAndVersionlessAreNoop() {
        rewriteRun(
                xml(project("<dependencies><dependency><groupId>commons-codec</groupId><artifactId>commons-codec</artifactId></dependency></dependencies>"), s -> s.path("pom.xml")),
                buildGradle("dependencies { implementation 'commons-codec:commons-codec:1.11:tests' }"),
                buildGradle("dependencies { implementation 'commons-codec:commons-codec:1.13@zip' }"));
    }

    @Test
    void installLeafProcessedAndTwoCyclesIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("1.11"), pom("1.22.0"), s -> s.path("pom.xml")),
                buildGradle("dependencies { implementation 'commons-codec:commons-codec:1.16.0' }",
                        "dependencies { implementation 'commons-codec:commons-codec:1.22.0' }", s -> s.path("install.gradle")));
    }

    @Test
    void recommendedStartsWithPublicUpgrade() {
        Environment environment = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.commonscodec").build();
        var migrate = environment.activateRecipes("com.huawei.clouds.openrewrite.commonscodec.MigrateCommonsCodecTo1_22_0");
        assertEquals("com.huawei.clouds.openrewrite.commonscodec.UpgradeCommonsCodecTo1_22_0",
                migrate.getRecipeList().get(0).getName());
    }

    static String pom(String version) { return project("<dependencies>" + dep(version) + "</dependencies>"); }
    static String project(String body) { return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>"; }
    static String dep(String version) { return rawDep("commons-codec", "commons-codec", version, ""); }
    static String rawDep(String group, String artifact, String version, String extra) { return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId><version>" + version + "</version>" + extra + "</dependency>"; }
}
