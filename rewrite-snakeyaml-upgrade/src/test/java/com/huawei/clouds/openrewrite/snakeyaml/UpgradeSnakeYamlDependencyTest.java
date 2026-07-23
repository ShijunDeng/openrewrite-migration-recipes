package com.huawei.clouds.openrewrite.snakeyaml;

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

class UpgradeSnakeYamlDependencyTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedSnakeYamlDependency());
    }

    @ParameterizedTest(name = "workbook source {0}")
    @ValueSource(strings = {"1.24", "1.26", "1.27", "1.28", "1.32", "1.33", "2"})
    void upgradesEveryWorkbookSource(String version) {
        rewriteRun(xml(pom(version), pom("2.5"), source -> source.path("pom.xml")));
    }

    @Test
    void whitelistExactlyMatchesRows961Through966And2330() {
        assertEquals(Set.of("1.24", "1.26", "1.27", "1.28", "1.32", "1.33", "2"),
                UpgradeSelectedSnakeYamlDependency.SOURCE_VERSIONS);
    }

    @Test
    void upgradesExclusiveRootProperty() {
        rewriteRun(xml(
                project("<properties><snakeyaml.version>1.27</snakeyaml.version></properties>" +
                        "<dependencies>" + dep("${snakeyaml.version}") + "</dependencies>"),
                project("<properties><snakeyaml.version>2.5</snakeyaml.version></properties>" +
                        "<dependencies>" + dep("${snakeyaml.version}") + "</dependencies>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void rootPropertyIsVisibleInDirectProfile() {
        rewriteRun(xml(
                project("<properties><fmt.version>1.26</fmt.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                project("<properties><fmt.version>2.5</fmt.version></properties><profiles><profile><id>it</id>" +
                        "<dependencies>" + dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void profileOverrideWinsWithoutLeakingToRoot() {
        rewriteRun(xml(
                project("<properties><fmt.version>2.4</fmt.version></properties><profiles><profile><id>owned</id>" +
                        "<properties><fmt.version>1.28</fmt.version></properties><dependencies>" +
                        dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                project("<properties><fmt.version>2.4</fmt.version></properties><profiles><profile><id>owned</id>" +
                        "<properties><fmt.version>2.5</fmt.version></properties><dependencies>" +
                        dep("${fmt.version}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootAndProfileDependencyManagement() {
        rewriteRun(xml(
                project("<dependencyManagement><dependencies>" + dep("1.24") +
                        "</dependencies></dependencyManagement><profiles><profile><id>legacy</id>" +
                        "<dependencyManagement><dependencies>" + dep("1.28") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                project("<dependencyManagement><dependencies>" + dep("2.5") +
                        "</dependencies></dependencyManagement><profiles><profile><id>legacy</id>" +
                        "<dependencyManagement><dependencies>" + dep("2.5") +
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
                Arguments.of("unused", project("<properties><f>1.27</f></properties>")),
                Arguments.of("shared plugin", project("<properties><f>1.27</f></properties><dependencies>" +
                        dep("${f}") + "</dependencies><build><finalName>${f}</finalName></build>")),
                Arguments.of("shared dependency", project("<properties><f>1.27</f></properties><dependencies>" +
                        dep("${f}") + "<dependency><groupId>x</groupId><artifactId>y</artifactId><version>${f}</version>" +
                        "</dependency></dependencies>")),
                Arguments.of("duplicate", project("<properties><f>1.27</f><f>1.27</f></properties><dependencies>" +
                        dep("${f}") + "</dependencies>")),
                Arguments.of("attribute", project("<properties><f>1.27</f></properties><dependencies>" +
                        dep("${f}") + "</dependencies><x value=\"${f}\"/>")));
    }

    @Test
    void upgradesRealWorldMavenPattern() {
        // Direct Maven form used throughout Swagger Parser; API fixtures pin its real repository separately.
        rewriteRun(xml(pom("1.27"), pom("2.5"), source -> source.path("service/pom.xml")));
    }

    @Test
    void upgradesRootKotlinGradlePattern() {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.yaml:snakeyaml:1.27\") }",
                "dependencies { implementation(\"org.yaml:snakeyaml:2.5\") }"));
    }

    @Test
    void upgradesGroovyStringAndMapNotation() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.yaml:snakeyaml:1.26' }",
                        "dependencies { implementation 'org.yaml:snakeyaml:2.5' }"),
                buildGradle("dependencies { runtimeOnly group: 'org.yaml', name: 'snakeyaml', version: '1.28', transitive: false }",
                        "dependencies { runtimeOnly group: 'org.yaml', name: 'snakeyaml', version: '2.5', transitive: false }"));
    }

    @ParameterizedTest(name = "nested Gradle scope NOOP: {0}")
    @MethodSource("nestedGradle")
    void ignoresNestedGradleScopes(String label, String source) {
        rewriteRun(buildGradle(source));
    }

    static Stream<Arguments> nestedGradle() {
        String d = "'org.yaml:snakeyaml:1.27'";
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
    @ValueSource(strings = {"1.23", "1.25", "1.29", "1.30", "1.31", "2.1", "2.4", "2.6"})
    void doesNotGuessOtherFixedVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "unowned or nonfixed {0}")
    @ValueSource(strings = {"${snakeyaml.version}", "[1,3)", "[1.26,)", "2.+", "+", "latest.release"})
    void doesNotGuessDynamicOrExternalOwners(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void versionlessBomOwnedAndVariantsAreNoop() {
        rewriteRun(
                xml(project("<dependencies>" + depWithoutVersion() + "</dependencies>"), source -> source.path("bom/pom.xml")),
                xml(project("<dependencies>" + dep("1.27", "<classifier>tests</classifier>") + "</dependencies>"), source -> source.path("classified/pom.xml")),
                xml(project("<dependencies>" + dep("1.27", "<type>zip</type>") + "</dependencies>"), source -> source.path("zip/pom.xml")),
                buildGradle("dependencies { implementation 'org.yaml:snakeyaml:1.27:tests' }"),
                buildGradle("dependencies { implementation 'org.yaml:snakeyaml:1.27@zip' }"));
    }

    @ParameterizedTest(name = "generated/cache parent {0}")
    @ValueSource(strings = {"target", "generatedSources", "GENERATED-code", "installation", "INSTALL-cache",
            ".gradle", ".m2", "reports", ".output", "test-results", "storybook-static", "tmp", "TEMP", ".vite",
            ".nuxt", "node_modules"})
    void generatedAndCacheParentsAreNoop(String parent) {
        rewriteRun(xml(pom("1.27"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void leafFilenamesBeginningInstallAreStillProcessed() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.yaml:snakeyaml:1.27' }",
                        "dependencies { implementation 'org.yaml:snakeyaml:2.5' }",
                        source -> source.path("install.gradle")),
                xml(pom("1.28"), pom("2.5"), source -> source.path("pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("1.24"), pom("2.5"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'org.yaml:snakeyaml:1.27' }",
                        "dependencies { implementation 'org.yaml:snakeyaml:2.5' }"));
    }

    @Test
    void publicLowLevelRecipeContainsOnlyStrictUpgradeAndRecommendedReusesIt() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.snakeyaml").build();
        var migrate = environment.activateRecipes(
                "com.huawei.clouds.openrewrite.snakeyaml.MigrateSnakeYamlTo2_5");
        assertEquals("com.huawei.clouds.openrewrite.snakeyaml.UpgradeSnakeYamlTo2_5",
                migrate.getRecipeList().get(0).getName());
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.snakeyaml.UpgradeSnakeYamlTo2_5")),
                xml(pom("1.27"), pom("2.5"), source -> source.path("pom.xml")));
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
        return "<dependency><groupId>org.yaml</groupId><artifactId>snakeyaml</artifactId>" +
               "<version>" + version + "</version>" + extra + "</dependency>";
    }

    static String depWithoutVersion() {
        return "<dependency><groupId>org.yaml</groupId><artifactId>snakeyaml</artifactId></dependency>";
    }
}
