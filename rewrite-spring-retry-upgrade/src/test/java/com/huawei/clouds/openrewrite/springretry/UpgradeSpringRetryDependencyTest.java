package com.huawei.clouds.openrewrite.springretry;

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

class UpgradeSpringRetryDependencyTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springretry.UpgradeSpringRetryTo2_0_13";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(SpringRetryTestSupport.recipe(RECIPE));
    }

    @Test
    void upgradesTheOnlyApprovedMavenLiteral() {
        rewriteRun(xml(
                SpringRetryTestSupport.pom("1.3.4"),
                SpringRetryTestSupport.pom("2.0.13"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesDependencyManagementRootAndProfileLiterals() {
        String before = SpringRetryTestSupport.project(
                "<dependencyManagement><dependencies>" + SpringRetryTestSupport.dependency("1.3.4", "") +
                "</dependencies></dependencyManagement><profiles><profile><id>it</id><dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4", "") + "</dependencies></profile></profiles>");
        String after = before.replace("<version>1.3.4</version>", "<version>2.0.13</version>");
        rewriteRun(xml(before, after, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveRootAndProfileProperties() {
        rewriteRun(
                xml(SpringRetryTestSupport.project(
                                "<properties><retry.version>1.3.4</retry.version></properties><dependencies>" +
                                SpringRetryTestSupport.dependency("${retry.version}", "") + "</dependencies>"),
                        SpringRetryTestSupport.project(
                                "<properties><retry.version>2.0.13</retry.version></properties><dependencies>" +
                                SpringRetryTestSupport.dependency("${retry.version}", "") + "</dependencies>"),
                        source -> source.path("root/pom.xml")),
                xml(SpringRetryTestSupport.project(
                                "<profiles><profile><id>it</id><properties><retry.version>1.3.4</retry.version>" +
                                "</properties><dependencies>" +
                                SpringRetryTestSupport.dependency("${retry.version}", "") +
                                "</dependencies></profile></profiles>"),
                        SpringRetryTestSupport.project(
                                "<profiles><profile><id>it</id><properties><retry.version>2.0.13</retry.version>" +
                                "</properties><dependencies>" +
                                SpringRetryTestSupport.dependency("${retry.version}", "") +
                                "</dependencies></profile></profiles>"),
                        source -> source.path("profile/pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous property: {0}")
    @MethodSource("ambiguousProperties")
    void leavesAmbiguousPropertiesUntouched(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path(label + "/pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("shared", SpringRetryTestSupport.project(
                        "<properties><v>1.3.4</v></properties><dependencies>" +
                        SpringRetryTestSupport.dependency("${v}", "") +
                        "<dependency><groupId>x</groupId><artifactId>other</artifactId><version>${v}</version>" +
                        "</dependency></dependencies>")),
                Arguments.of("duplicate", SpringRetryTestSupport.project(
                        "<properties><v>1.3.4</v><v>1.3.4</v></properties><dependencies>" +
                        SpringRetryTestSupport.dependency("${v}", "") + "</dependencies>")),
                Arguments.of("attribute", "<project marker=\"${v}\"><modelVersion>4.0.0</modelVersion>" +
                        "<groupId>x</groupId><artifactId>a</artifactId><version>1</version>" +
                        "<properties><v>1.3.4</v></properties><dependencies>" +
                        SpringRetryTestSupport.dependency("${v}", "") + "</dependencies></project>"),
                Arguments.of("shadow", SpringRetryTestSupport.project(
                        "<properties><v>1.3.4</v></properties><dependencies>" +
                        SpringRetryTestSupport.dependency("${v}", "") +
                        "</dependencies><profiles><profile><id>it</id><properties><v>1.3.4</v>" +
                        "</properties></profile></profiles>")),
                Arguments.of("build-reference", SpringRetryTestSupport.project(
                        "<properties><v>1.3.4</v></properties><dependencies>" +
                        SpringRetryTestSupport.dependency("${v}", "") +
                        "</dependencies><build><finalName>${v}</finalName></build>")));
    }

    @ParameterizedTest(name = "Maven non-whitelist version {0} remains byte-stable")
    @ValueSource(strings = {
            "1.0.0", "1.1.4", "1.2.5.RELEASE", "1.3.2", "1.3.3", "1.3.5",
            "2.0.0", "2.0.1", "2.0.12", "2.0.13", "2.0.14", "2.1.0", "3.0.0",
            "999999999999999999999.0.0"
    })
    void neverExpandsTheMavenWhitelist(String version) {
        rewriteRun(xml(SpringRetryTestSupport.pom(version),
                source -> source.path(version.replace('.', '_') + "/pom.xml")));
    }

    @ParameterizedTest(name = "higher version {0} is never downgraded in any build DSL")
    @ValueSource(strings = {"2.0.14", "2.1.0", "3.0.0", "10.0.0", "999999999999999999999.0.0"})
    void preservesHigherVersionsAcrossMavenGroovyAndKotlin(String version) {
        rewriteRun(
                xml(SpringRetryTestSupport.pom(version), source -> source.path("maven/" + version + "/pom.xml")),
                buildGradle("dependencies { implementation 'org.springframework.retry:spring-retry:" +
                        version + "' }", source -> source.path("groovy/" + version + "/build.gradle")),
                buildGradleKts("dependencies { implementation(\"org.springframework.retry:spring-retry:" +
                        version + "\") }", source -> source.path("kotlin/" + version + "/build.gradle.kts")));
    }

    @Test
    void upgradesGroovyStringsMapArgumentsMapLiteralsAndKotlinStrings() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework.retry:spring-retry:1.3.4' }",
                        "dependencies { implementation 'org.springframework.retry:spring-retry:2.0.13' }",
                        source -> source.path("string/build.gradle")),
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.springframework.retry', name: 'spring-retry', version: '1.3.4' }",
                        "dependencies { runtimeOnly group: 'org.springframework.retry', name: 'spring-retry', version: '2.0.13' }",
                        source -> source.path("map/build.gradle")),
                buildGradle(
                        "dependencies { testImplementation([group: 'org.springframework.retry', name: 'spring-retry', version: '1.3.4']) }",
                        "dependencies { testImplementation([group: 'org.springframework.retry', name: 'spring-retry', version: '2.0.13']) }",
                        source -> source.path("map-literal/build.gradle")),
                buildGradleKts(
                        "dependencies { implementation(\"org.springframework.retry:spring-retry:1.3.4\") }",
                        "dependencies { implementation(\"org.springframework.retry:spring-retry:2.0.13\") }"));
    }

    @Test
    void preservesDynamicCatalogPlatformVariantsAndFourPartCoordinates() {
        rewriteRun(
                buildGradle("""
                        def v = '1.3.4'
                        dependencies {
                          implementation "org.springframework.retry:spring-retry:${v}"
                          implementation libs.spring.retry
                          implementation platform('org.springframework.boot:spring-boot-dependencies:2.7.18')
                          implementation 'org.springframework.retry:spring-retry:1.3.4:sources'
                          implementation 'org.springframework.retry:spring-retry:1.3.4@zip'
                          implementation group: 'org.springframework.retry', name: 'spring-retry',
                                         version: '1.3.4', classifier: 'sources'
                          implementation([group: 'org.springframework.retry', name: 'spring-retry',
                                          version: '1.3.4', ext: 'zip'])
                        }
                        """),
                buildGradleKts("""
                        val v = "1.3.4"
                        dependencies {
                          implementation("org.springframework.retry:spring-retry:$v")
                          implementation(libs.spring.retry)
                        }
                        """));
    }

    @ParameterizedTest(name = "nested Gradle owner {0}")
    @MethodSource("nestedGradleOwners")
    void ignoresNestedOrForeignGradleOwners(String label, String source) {
        rewriteRun(buildGradle(source, spec -> spec.path(label + "/build.gradle")));
    }

    static Stream<Arguments> nestedGradleOwners() {
        String d = "'org.springframework.retry:spring-retry:1.3.4'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + d + " } }"),
                Arguments.of("project", "project(':app') { dependencies { implementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + d + " } }"),
                Arguments.of("custom", "company { dependencies { implementation " + d + " } }"));
    }

    @Test
    void preservesVersionlessVariantsPluginDependenciesAndLookalikes() {
        rewriteRun(
                xml(SpringRetryTestSupport.project("<dependencies>" +
                        SpringRetryTestSupport.dependency(null, "") +
                        SpringRetryTestSupport.dependency("1.3.4", "<classifier>tests</classifier>") +
                        SpringRetryTestSupport.dependency("1.3.4", "<type>test-jar</type>") +
                        "<dependency><groupId>example</groupId><artifactId>spring-retry</artifactId>" +
                        "<version>1.3.4</version></dependency><dependency>" +
                        "<groupId>org.springframework.retry</groupId><artifactId>spring-retry-extra</artifactId>" +
                        "<version>1.3.4</version></dependency></dependencies>"),
                        source -> source.path("variants/pom.xml")),
                xml(SpringRetryTestSupport.project("<build><plugins><plugin><artifactId>x</artifactId>" +
                        "<dependencies>" + SpringRetryTestSupport.dependency("1.3.4", "") +
                        "</dependencies></plugin></plugins></build>"), source -> source.path("plugin/pom.xml")));
    }

    @Test
    void standaloneRecipeBlocksMixedSourceAndNonSourceVersionsAtOneRoot() {
        String mixed = SpringRetryTestSupport.project("<dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4", "") +
                SpringRetryTestSupport.dependency("2.0.13", "") +
                SpringRetryTestSupport.dependency("2.0.14", "") +
                SpringRetryTestSupport.dependency("1.3.3", "") +
                "</dependencies>");
        rewriteRun(xml(mixed, source -> source.path("mixed/pom.xml")));
    }

    @Test
    void standaloneRecipeBlocksALiteralWhenAnotherOwnerIsAmbiguous() {
        String conflicted = SpringRetryTestSupport.project(
                "<properties><v>1.3.4</v></properties><dependencies>" +
                SpringRetryTestSupport.dependency("1.3.4", "") +
                SpringRetryTestSupport.dependency("${v}", "") +
                "<dependency><groupId>example</groupId><artifactId>shared</artifactId>" +
                "<version>${v}</version></dependency></dependencies>");
        rewriteRun(xml(conflicted, source -> source.path("ambiguous-owner/pom.xml")));
    }

    @Test
    void standaloneRecipeBlocksPropagatingParentWhenNestedRootIsTarget() {
        rewriteRun(
                xml(SpringRetryTestSupport.pom("1.3.4"),
                        source -> source.path("nested/pom.xml")),
                xml(SpringRetryTestSupport.pom("2.0.13"),
                        source -> source.path("nested/child/pom.xml")));
    }

    @ParameterizedTest(name = "generated/cache parent {0}")
    @ValueSource(strings = {
            "target", "build", "generated", "generatedSources", "install", "installation", ".gradle", ".m2",
            ".idea", "node_modules", "vendor", "reports", "test-results", "tmp", "TEMP"
    })
    void skipsGeneratedAndCacheParents(String parent) {
        rewriteRun(xml(SpringRetryTestSupport.pom("1.3.4"),
                source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(SpringRetryTestSupport.pom("1.3.4"), SpringRetryTestSupport.pom("2.0.13"),
                        source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'org.springframework.retry:spring-retry:1.3.4' }",
                        "dependencies { implementation 'org.springframework.retry:spring-retry:2.0.13' }"));
    }

    @Test
    void publicStrictRecipeMarksTheProjectThenRunsOfficialFirstWithFallback() {
        var recipe = Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.springretry").build().activateRecipes(RECIPE);
        assertEquals(2, recipe.getRecipeList().size());
        assertEquals("com.huawei.clouds.openrewrite.springretry.MarkSelectedSpringRetryProjects",
                recipe.getRecipeList().get(0).getName());
        assertEquals("com.huawei.clouds.openrewrite.springretry.UpgradeMarkedSpringRetryDependencyTo2_0_13",
                recipe.getRecipeList().get(1).getName());
        var executable = recipe.getRecipeList().get(1).getRecipeList().stream()
                .map(child -> child.getName())
                .filter(name -> !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether".equals(name))
                .toList();
        assertEquals(java.util.List.of(
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion",
                "com.huawei.clouds.openrewrite.springretry.UpgradeSelectedSpringRetryDependency"), executable);
    }
}
