package com.huawei.clouds.openrewrite.jsoup;

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

class UpgradeJsoupDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedJsoupDependency());
    }

    @ParameterizedTest(name = "workbook source {0}")
    @ValueSource(strings = {"1.14.2", "1.14.3", "1.15.3", "1.15.4", "1.16.1"})
    void upgradesEveryAndOnlyWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("1.21.1"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesRows2464Through2468() {
        assertEquals(Set.of("1.14.2", "1.14.3", "1.15.3", "1.15.4", "1.16.1"), JsoupSupport.SOURCES);
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(project("<dependencyManagement><dependencies>" + dep("1.14.2") +
                        "</dependencies></dependencyManagement><profiles><profile><id>it</id><dependencyManagement><dependencies>" +
                        dep("1.16.1") + "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("1.21.1") +
                        "</dependencies></dependencyManagement><profiles><profile><id>it</id><dependencyManagement><dependencies>" +
                        dep("1.21.1") + "</dependencies></dependencyManagement></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveRootPropertyVisibleInProfile() {
        rewriteRun(xml(project("<properties><jsoup.version>1.14.3</jsoup.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${jsoup.version}") + "</dependencies></profile></profiles>"),
                project("<properties><jsoup.version>1.21.1</jsoup.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${jsoup.version}") + "</dependencies></profile></profiles>"), source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWinsWithoutLeakingToRoot() {
        rewriteRun(xml(project("<properties><v>1.13.1</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>1.15.4</v></properties><dependencies>" + dep("${v}") + "</dependencies></profile></profiles>"),
                project("<properties><v>1.13.1</v></properties><profiles><profile><id>it</id>" +
                        "<properties><v>1.21.1</v></properties><dependencies>" + dep("${v}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property {0}")
    @MethodSource("ambiguousProperties")
    void preservesSharedOrAmbiguousProperties(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("shared dependency", project("<properties><v>1.14.2</v></properties><dependencies>" + dep("${v}") +
                        "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${v}</version></dependency></dependencies>")),
                Arguments.of("shared build", project("<properties><v>1.14.2</v></properties><dependencies>" + dep("${v}") +
                        "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("duplicate", project("<properties><v>1.14.2</v><v>1.14.2</v></properties><dependencies>" + dep("${v}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><v>1.14.2</v></properties><dependencies>" + dep("${v}") + "</dependencies><x value=\"${v}\"/>")));
    }

    @ParameterizedTest(name = "outside whitelist {0}")
    @ValueSource(strings = {"1.13.1", "1.14.1", "1.15.1", "1.16.2", "1.17.1", "1.18.3", "1.19.1", "1.20.1", "1.21.1", "1.22.1"})
    void doesNotWidenWorkbookSources(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "external/dynamic {0}")
    @ValueSource(strings = {"${jsoup.version}", "[1.14,2)", "1.+", "+", "latest.release"})
    void doesNotGuessUnownedVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void protectsMavenVariantsVersionlessAndPluginDependencies() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + dep("1.14.2", "<classifier>tests</classifier>") +
                        dep("1.15.3", "<type>zip</type>") + "</dependencies>"), source -> source.path("pom.xml")),
                xml(project("<build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><dependencies>" +
                        dep("1.14.2") + "</dependencies></plugin></plugins></build>"), source -> source.path("plugin/pom.xml")));
    }

    @Test
    void upgradesRealisticGradleGroovyAndKotlinForms() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.jsoup:jsoup:1.14.2' }",
                        "dependencies { implementation 'org.jsoup:jsoup:1.21.1' }", source -> source.path("app/build.gradle")),
                buildGradleKts("dependencies { implementation(\"org.jsoup:jsoup:1.16.1\") }",
                        "dependencies { implementation(\"org.jsoup:jsoup:1.21.1\") }"));
    }

    @Test
    void upgradesGroovyMapNotations() {
        rewriteRun(
                buildGradle("dependencies { runtimeOnly group: 'org.jsoup', name: 'jsoup', version: '1.15.3', transitive: false }",
                        "dependencies { runtimeOnly group: 'org.jsoup', name: 'jsoup', version: '1.21.1', transitive: false }"),
                buildGradle("dependencies { implementation([group: 'org.jsoup', name: 'jsoup', version: '1.15.4']) }",
                        "dependencies { implementation([group: 'org.jsoup', name: 'jsoup', version: '1.21.1']) }"));
    }

    @ParameterizedTest(name = "nested Gradle NOOP {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleDsl(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'org.jsoup:jsoup:1.14.2'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':app') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("selected", "dependencies { helper.implementation " + d + " }"));
    }

    @Test
    void protectsGradleVariantsVariablesAndCatalogs() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.jsoup:jsoup:1.14.2:tests' }"),
                buildGradle("dependencies { implementation 'org.jsoup:jsoup:1.14.2@zip' }"),
                buildGradle("dependencies { implementation \"org.jsoup:jsoup:$jsoupVersion\" }"),
                buildGradle("dependencies { implementation libs.jsoup }"));
    }

    @ParameterizedTest(name = "generated parent {0}")
    @ValueSource(strings = {"target", "build", "generated", "generatedSources", "installation", "INSTALL-cache", ".gradle", ".m2", "node_modules"})
    void ignoresGeneratedAndCacheParents(String parent) {
        rewriteRun(xml(pom("1.14.2"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void eligibleLeafNamesAndTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradle("dependencies { implementation 'org.jsoup:jsoup:1.14.3' }",
                        "dependencies { implementation 'org.jsoup:jsoup:1.21.1' }", source -> source.path("install.gradle")),
                xml(pom("1.16.1"), pom("1.21.1"), source -> source.path("pom.xml")));
    }

    @Test
    void publicRecipeIsStrictAndRecommendedReusesItFirst() {
        Environment environment = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.jsoup").build();
        var upgrade = environment.activateRecipes("com.huawei.clouds.openrewrite.jsoup.UpgradeJsoupTo1_21_1");
        var migrate = environment.activateRecipes("com.huawei.clouds.openrewrite.jsoup.MigrateJsoupTo1_21_1");
        assertEquals(1, upgrade.getRecipeList().size());
        assertEquals(UpgradeSelectedJsoupDependency.class, upgrade.getRecipeList().get(0).getClass());
        assertEquals("com.huawei.clouds.openrewrite.jsoup.UpgradeJsoupTo1_21_1", migrate.getRecipeList().get(0).getName());
    }

    static String pom(String version) { return project("<dependencies>" + dep(version) + "</dependencies>"); }
    static String project(String body) { return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>web</artifactId><version>1</version>" + body + "</project>"; }
    static String dep(String version) { return dep(version, ""); }
    static String dep(String version, String extra) { return "<dependency><groupId>org.jsoup</groupId><artifactId>jsoup</artifactId><version>" + version + "</version>" + extra + "</dependency>"; }
    static String depWithoutVersion() { return "<dependency><groupId>org.jsoup</groupId><artifactId>jsoup</artifactId></dependency>"; }
}
