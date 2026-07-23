package com.huawei.clouds.openrewrite.commonscli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeCommonsCliDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedCommonsCliDependency());
    }

    @Test
    void upgradesTheOnlyWorkbookRow() {
        rewriteRun(xml(pom("1.5.0"), pom("1.9.0"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + dep("1.5.0") +
                        "</dependencies></dependencyManagement><profiles><profile><id>it</id>" +
                        "<dependencyManagement><dependencies>" + dep("1.5.0") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("1.9.0") +
                        "</dependencies></dependencyManagement><profiles><profile><id>it</id>" +
                        "<dependencyManagement><dependencies>" + dep("1.9.0") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveRootPropertyAndItsProfileUse() {
        rewriteRun(xml(
                project("<properties><cli.version>1.5.0</cli.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${cli.version}") + "</dependencies></profile></profiles>"),
                project("<properties><cli.version>1.9.0</cli.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${cli.version}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWinsWithoutChangingRoot() {
        rewriteRun(xml(
                project("<properties><cli.version>1.4.0</cli.version></properties><profiles><profile><id>it</id>" +
                        "<properties><cli.version>1.5.0</cli.version></properties><dependencies>" +
                        dep("${cli.version}") + "</dependencies></profile></profiles>"),
                project("<properties><cli.version>1.4.0</cli.version></properties><profiles><profile><id>it</id>" +
                        "<properties><cli.version>1.9.0</cli.version></properties><dependencies>" +
                        dep("${cli.version}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous Maven ownership: {0}")
    @MethodSource("ambiguousProperties")
    void preservesSharedOrAmbiguousProperties(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path("pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("shared dependency", project("<properties><v>1.5.0</v></properties><dependencies>" +
                        dep("${v}") + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${v}</version></dependency></dependencies>")),
                Arguments.of("shared plugin", project("<properties><v>1.5.0</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><build><finalName>${v}</finalName></build>")),
                Arguments.of("duplicate", project("<properties><v>1.5.0</v><v>1.5.0</v></properties><dependencies>" + dep("${v}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><v>1.5.0</v></properties><dependencies>" +
                        dep("${v}") + "</dependencies><x value=\"${v}\"/>")));
    }

    @ParameterizedTest(name = "outside exact whitelist: {0}")
    @ValueSource(strings = {"1.4", "1.4.0", "1.5", "1.5.1", "1.6.0", "1.8.0", "1.9.0", "2.0.0"})
    void doesNotWidenTheWorkbookWhitelist(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "external/dynamic: {0}")
    @ValueSource(strings = {"${cli.version}", "[1.5,2)", "1.+", "+", "latest.release"})
    void doesNotGuessExternalOrDynamicOwners(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void protectsMavenVariantsVersionlessAndPluginDependencies() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + dep("1.5.0", "<classifier>tests</classifier>") +
                        dep("1.5.0", "<type>zip</type>") + "</dependencies>"), source -> source.path("pom.xml")),
                xml(project("<build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><dependencies>" +
                        dep("1.5.0") + "</dependencies></plugin></plugins></build>"), source -> source.path("plugin/pom.xml")));
    }

    @Test
    void upgradesRealGradleGroovyFixtures() {
        // elastic/elasticsearch@88c0b366dab1ceb617adaea4789a6f250d4ae6a8, plugins/repository-hdfs/build.gradle
        rewriteRun(buildGradle(
                "dependencies { api 'commons-cli:commons-cli:1.5.0' }",
                "dependencies { api 'commons-cli:commons-cli:1.9.0' }",
                source -> source.path("plugins/repository-hdfs/build.gradle")));
    }

    @Test
    void upgradesRealGradleKotlinFixture() {
        // RipMeApp/ripme@fb840651b26d1f590559177d29a32faed7385342, build.gradle.kts
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"commons-cli:commons-cli:1.5.0\") }",
                "dependencies { implementation(\"commons-cli:commons-cli:1.9.0\") }"));
    }

    @Test
    void upgradesGroovyMapNotations() {
        rewriteRun(
                buildGradle("dependencies { runtimeOnly group: 'commons-cli', name: 'commons-cli', version: '1.5.0', transitive: false }",
                        "dependencies { runtimeOnly group: 'commons-cli', name: 'commons-cli', version: '1.9.0', transitive: false }"),
                buildGradle("dependencies { implementation([group: 'commons-cli', name: 'commons-cli', version: '1.5.0']) }",
                        "dependencies { implementation([group: 'commons-cli', name: 'commons-cli', version: '1.9.0']) }"));
    }

    @ParameterizedTest(name = "nested Gradle scope NOOP: {0}")
    @MethodSource("nestedGradle")
    void doesNotRewriteNestedGradleDsl(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String coordinate = "'commons-cli:commons-cli:1.5.0'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + coordinate + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + coordinate + " } }"),
                Arguments.of("project", "project(':app') { dependencies { implementation " + coordinate + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + coordinate + " } }"),
                Arguments.of("selected", "dependencies { helper.implementation " + coordinate + " }"));
    }

    @Test
    void protectsGradleVariantsCatalogsAndVariables() {
        rewriteRun(
                buildGradle("dependencies { implementation 'commons-cli:commons-cli:1.5.0:tests' }"),
                buildGradle("dependencies { implementation 'commons-cli:commons-cli:1.5.0@zip' }"),
                buildGradle("dependencies { implementation libs.commons.cli }"),
                buildGradle("dependencies { implementation \"commons-cli:commons-cli:$cliVersion\" }"));
    }

    @ParameterizedTest(name = "generated/cache parent: {0}")
    @ValueSource(strings = {"target", "build", "generated", "generatedSources", "installation", "INSTALL-cache", ".gradle", ".m2", "node_modules"})
    void ignoresGeneratedAndCacheParents(String parent) {
        rewriteRun(xml(pom("1.5.0"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void leafNamesBeginningWithInstallOrGeneratedRemainEligible() {
        rewriteRun(
                buildGradle("dependencies { implementation 'commons-cli:commons-cli:1.5.0' }",
                        "dependencies { implementation 'commons-cli:commons-cli:1.9.0' }", source -> source.path("install.gradle")),
                buildGradle("dependencies { implementation 'commons-cli:commons-cli:1.5.0' }",
                        "dependencies { implementation 'commons-cli:commons-cli:1.9.0' }", source -> source.path("generated.gradle")));
    }

    @Test
    void isIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("1.5.0"), pom("1.9.0"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'commons-cli:commons-cli:1.5.0' }",
                        "dependencies { implementation 'commons-cli:commons-cli:1.9.0' }"));
    }

    @Test
    void publicRecipeIsStrictAndRecommendedReusesItFirst() {
        Environment environment = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.commonscli").build();
        var upgrade = environment.activateRecipes("com.huawei.clouds.openrewrite.commonscli.UpgradeCommonsCliTo1_9_0");
        var migrate = environment.activateRecipes("com.huawei.clouds.openrewrite.commonscli.MigrateCommonsCliTo1_9_0");
        assertEquals(1, upgrade.getRecipeList().size());
        assertEquals(UpgradeSelectedCommonsCliDependency.class, upgrade.getRecipeList().get(0).getClass());
        assertEquals("com.huawei.clouds.openrewrite.commonscli.UpgradeCommonsCliTo1_9_0", migrate.getRecipeList().get(0).getName());
    }

    static String pom(String version) {
        return project("<dependencies>" + dep(version) + "</dependencies>");
    }

    static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>cli-client</artifactId><version>1</version>" + body + "</project>";
    }

    static String dep(String version) {
        return dep(version, "");
    }

    static String dep(String version, String extra) {
        return "<dependency><groupId>commons-cli</groupId><artifactId>commons-cli</artifactId><version>" +
               version + "</version>" + extra + "</dependency>";
    }

    static String depWithoutVersion() {
        return "<dependency><groupId>commons-cli</groupId><artifactId>commons-cli</artifactId></dependency>";
    }
}
