package com.huawei.clouds.openrewrite.elasticsearch;

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

class UpgradeTestcontainersElasticsearchDependencyTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.elasticsearch.UpgradeTestcontainersElasticsearchTo1_21_4";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(ElasticsearchTestSupport.recipe(RECIPE));
    }

    @Test
    void upgradesOnlyTheApprovedMavenLiteral() {
        rewriteRun(xml(
                ElasticsearchTestSupport.pom("1.17.6"),
                ElasticsearchTestSupport.pom("1.21.4"),
                source -> source.path("pom.xml")));
    }

    @Test
    void upgradesDependencyManagementRootAndProfileLiterals() {
        String before = ElasticsearchTestSupport.project(
                "<dependencyManagement><dependencies>" +
                ElasticsearchTestSupport.testcontainersDependency("1.17.6", "") +
                "</dependencies></dependencyManagement><profiles><profile><id>it</id><dependencies>" +
                ElasticsearchTestSupport.testcontainersDependency("1.17.6", "") +
                "</dependencies></profile></profiles>");
        String after = before.replace("<version>1.17.6</version>", "<version>1.21.4</version>");
        rewriteRun(xml(before, after, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveRootAndProfileProperties() {
        rewriteRun(
                xml(ElasticsearchTestSupport.project(
                                "<properties><tc.elasticsearch.version>1.17.6</tc.elasticsearch.version></properties>" +
                                "<dependencies>" + ElasticsearchTestSupport.testcontainersDependency(
                                        "${tc.elasticsearch.version}", "") + "</dependencies>"),
                        ElasticsearchTestSupport.project(
                                "<properties><tc.elasticsearch.version>1.21.4</tc.elasticsearch.version></properties>" +
                                "<dependencies>" + ElasticsearchTestSupport.testcontainersDependency(
                                        "${tc.elasticsearch.version}", "") + "</dependencies>"),
                        source -> source.path("root/pom.xml")),
                xml(ElasticsearchTestSupport.project(
                                "<profiles><profile><id>it</id><properties><v>1.17.6</v></properties>" +
                                "<dependencies>" + ElasticsearchTestSupport.testcontainersDependency("${v}", "") +
                                "</dependencies></profile></profiles>"),
                        ElasticsearchTestSupport.project(
                                "<profiles><profile><id>it</id><properties><v>1.21.4</v></properties>" +
                                "<dependencies>" + ElasticsearchTestSupport.testcontainersDependency("${v}", "") +
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
                Arguments.of("shared", ElasticsearchTestSupport.project(
                        "<properties><v>1.17.6</v></properties><dependencies>" +
                        ElasticsearchTestSupport.testcontainersDependency("${v}", "") +
                        ElasticsearchTestSupport.dependency("x", "other", "${v}", "") +
                        "</dependencies>")),
                Arguments.of("duplicate", ElasticsearchTestSupport.project(
                        "<properties><v>1.17.6</v><v>1.17.6</v></properties><dependencies>" +
                        ElasticsearchTestSupport.testcontainersDependency("${v}", "") + "</dependencies>")),
                Arguments.of("attribute", "<project marker=\"${v}\"><modelVersion>4.0.0</modelVersion>" +
                        "<groupId>x</groupId><artifactId>a</artifactId><version>1</version>" +
                        "<properties><v>1.17.6</v></properties><dependencies>" +
                        ElasticsearchTestSupport.testcontainersDependency("${v}", "") + "</dependencies></project>"),
                Arguments.of("profile-shadow", ElasticsearchTestSupport.project(
                        "<properties><v>1.17.6</v></properties><dependencies>" +
                        ElasticsearchTestSupport.testcontainersDependency("${v}", "") +
                        "</dependencies><profiles><profile><id>it</id><properties><v>1.17.6</v>" +
                        "</properties></profile></profiles>")),
                Arguments.of("build-reference", ElasticsearchTestSupport.project(
                        "<properties><v>1.17.6</v></properties><dependencies>" +
                        ElasticsearchTestSupport.testcontainersDependency("${v}", "") +
                        "</dependencies><build><finalName>${v}</finalName></build>")));
    }

    @ParameterizedTest(name = "non-whitelist Maven version {0} remains byte-stable")
    @ValueSource(strings = {
            "1.15.1", "1.16.3", "1.17.5", "1.17.7", "1.18.0", "1.19.8",
            "1.20.6", "1.21.3", "1.21.4", "1.21.5", "2.0.0",
            "999999999999999999999.0.0"
    })
    void neverExpandsTheMavenWhitelist(String version) {
        rewriteRun(xml(ElasticsearchTestSupport.pom(version),
                source -> source.path(version.replace('.', '_') + "/pom.xml")));
    }

    @ParameterizedTest(name = "higher version {0} is never downgraded in any build DSL")
    @ValueSource(strings = {"1.21.5", "1.22.0", "2.0.0", "10.0.0", "999999999999999999999.0.0"})
    void preservesHigherVersionsAcrossMavenGroovyAndKotlin(String version) {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom(version),
                        source -> source.path("maven/" + version + "/pom.xml")),
                buildGradle("dependencies { testImplementation 'org.testcontainers:elasticsearch:" +
                        version + "' }", source -> source.path("groovy/" + version + "/build.gradle")),
                buildGradleKts("dependencies { testImplementation(\"org.testcontainers:elasticsearch:" +
                        version + "\") }", source -> source.path("kotlin/" + version + "/build.gradle.kts")));
    }

    @Test
    void upgradesGroovyStringsMapsMapLiteralsAndKotlinStrings() {
        rewriteRun(
                buildGradle(
                        "dependencies { testImplementation 'org.testcontainers:elasticsearch:1.17.6' }",
                        "dependencies { testImplementation 'org.testcontainers:elasticsearch:1.21.4' }",
                        source -> source.path("groovy-string/build.gradle")),
                buildGradle(
                        "dependencies { implementation group: 'org.testcontainers', name: 'elasticsearch', version: '1.17.6' }",
                        "dependencies { implementation group: 'org.testcontainers', name: 'elasticsearch', version: '1.21.4' }",
                        source -> source.path("groovy-map/build.gradle")),
                buildGradle(
                        "dependencies { testImplementation([group: 'org.testcontainers', name: 'elasticsearch', version: '1.17.6']) }",
                        "dependencies { testImplementation([group: 'org.testcontainers', name: 'elasticsearch', version: '1.21.4']) }",
                        source -> source.path("groovy-map-literal/build.gradle")),
                buildGradleKts(
                        "dependencies { testImplementation(\"org.testcontainers:elasticsearch:1.17.6\") }",
                        "dependencies { testImplementation(\"org.testcontainers:elasticsearch:1.21.4\") }",
                        source -> source.path("kotlin/build.gradle.kts")));
    }

    @Test
    void preservesDynamicCatalogPlatformVariantsAndFourPartCoordinates() {
        rewriteRun(
                buildGradle("""
                        def v = '1.17.6'
                        dependencies {
                          testImplementation "org.testcontainers:elasticsearch:${v}"
                          testImplementation libs.testcontainers.elasticsearch
                          testImplementation platform('org.testcontainers:testcontainers-bom:1.17.6')
                          testImplementation 'org.testcontainers:elasticsearch:1.17.6:sources'
                          testImplementation 'org.testcontainers:elasticsearch:1.17.6@zip'
                          testImplementation group: 'org.testcontainers', name: 'elasticsearch',
                                             version: '1.17.6', classifier: 'sources'
                          testImplementation([group: 'org.testcontainers', name: 'elasticsearch',
                                              version: '1.17.6', ext: 'zip'])
                        }
                        """),
                buildGradleKts("""
                        val v = "1.17.6"
                        dependencies {
                          testImplementation("org.testcontainers:elasticsearch:$v")
                          testImplementation(libs.testcontainers.elasticsearch)
                        }
                        """));
    }

    @ParameterizedTest(name = "nested Gradle owner {0}")
    @MethodSource("nestedGradleOwners")
    void ignoresNestedOrForeignGradleOwners(String label, String source) {
        rewriteRun(buildGradle(source, spec -> spec.path(label + "/build.gradle")));
    }

    static Stream<Arguments> nestedGradleOwners() {
        String d = "'org.testcontainers:elasticsearch:1.17.6'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + d + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { testImplementation " + d + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { testImplementation " + d + " } }"),
                Arguments.of("project", "project(':app') { dependencies { testImplementation " + d + " } }"),
                Arguments.of("constraints", "dependencies { constraints { testImplementation " + d + " } }"),
                Arguments.of("custom", "company { dependencies { testImplementation " + d + " } }"));
    }

    @Test
    void doesNotTreatAnAppliedGradleScriptAsAnIndependentBuildRoot() {
        rewriteRun(
                buildGradle(
                        "dependencies { testImplementation 'org.testcontainers:elasticsearch:1.17.6' }",
                        source -> source.path("gradle/dependencies.gradle")));
    }

    @Test
    void lowLevelDependencyVisitorCannotBypassThePreUpgradeProjectMarker() {
        rewriteRun(spec -> spec.recipe(
                        new UpgradeSelectedTestcontainersElasticsearchDependency()),
                xml(ElasticsearchTestSupport.pom("1.17.6"),
                        source -> source.path("pom.xml")));
    }

    @Test
    void preservesVariantsPluginDependenciesLookalikesAndElasticsearchServer() {
        rewriteRun(
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                        ElasticsearchTestSupport.testcontainersDependency(null, "") +
                        ElasticsearchTestSupport.testcontainersDependency("1.17.6",
                                "<classifier>tests</classifier>") +
                        ElasticsearchTestSupport.testcontainersDependency("1.17.6",
                                "<type>test-jar</type>") +
                        ElasticsearchTestSupport.dependency("example", "elasticsearch", "1.17.6", "") +
                        ElasticsearchTestSupport.dependency("org.testcontainers", "elasticsearch-extra",
                                "1.17.6", "") +
                        ElasticsearchTestSupport.serverDependency("7.10.2") +
                        "</dependencies>"), source -> source.path("variants/pom.xml")),
                xml(ElasticsearchTestSupport.project("<build><plugins><plugin><artifactId>x</artifactId>" +
                        "<dependencies>" + ElasticsearchTestSupport.testcontainersDependency("1.17.6", "") +
                        "</dependencies></plugin></plugins></build>"), source -> source.path("plugin/pom.xml")));
    }

    @ParameterizedTest(name = "generated/cache parent {0}")
    @ValueSource(strings = {
            "target", "build", "generated", "generatedSources", "install", "installation", ".gradle", ".m2",
            ".idea", "node_modules", "vendor", "reports", "test-results", "tmp", "TEMP"
    })
    void skipsGeneratedAndCacheParents(String parent) {
        rewriteRun(xml(ElasticsearchTestSupport.pom("1.17.6"),
                source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(ElasticsearchTestSupport.pom("1.17.6"),
                        ElasticsearchTestSupport.pom("1.21.4"), source -> source.path("pom.xml")),
                buildGradle("dependencies { testImplementation 'org.testcontainers:elasticsearch:1.17.6' }",
                        "dependencies { testImplementation 'org.testcontainers:elasticsearch:1.21.4' }"));
    }

    @Test
    void mixedIdentityOrVersionOwnersBlockEveryDependencyAuto() {
        rewriteRun(
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("1.17.6", "") +
                                ElasticsearchTestSupport.testcontainersDependency("1.21.4", "") +
                                "</dependencies>"),
                        source -> source.path("target-conflict/pom.xml")),
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("1.17.6", "") +
                                ElasticsearchTestSupport.testcontainersDependency("1.21.5", "") +
                                "</dependencies>"),
                        source -> source.path("future-conflict/pom.xml")),
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("1.17.6", "") +
                                ElasticsearchTestSupport.serverDependency("7.10.2") +
                                "</dependencies>"),
                        source -> source.path("identity-conflict/pom.xml")),
                buildGradle("""
                        dependencies {
                          testImplementation 'org.testcontainers:elasticsearch:1.17.6'
                          testImplementation 'org.testcontainers:elasticsearch:1.20.6'
                        }
                        """, source -> source.path("gradle-conflict/build.gradle")));
    }

    @Test
    void mixedOutOfScopeGradleIdentityAlsoBlocksDependencyAuto() {
        rewriteRun(buildGradle("""
                buildscript {
                  dependencies {
                    classpath 'org.testcontainers:elasticsearch:1.21.4'
                  }
                }
                dependencies {
                  testImplementation 'org.testcontainers:elasticsearch:1.17.6'
                }
                """, source -> source.path("build.gradle")));
    }

    @Test
    void nearestNestedBuildBoundaryCannotBorrowOuterDependencyEligibility() {
        rewriteRun(
                xml(ElasticsearchTestSupport.pom("1.17.6"),
                        ElasticsearchTestSupport.pom("1.21.4"),
                        source -> source.path("pom.xml")),
                xml(ElasticsearchTestSupport.project("<dependencies>" +
                                ElasticsearchTestSupport.testcontainersDependency("1.17.6", "") +
                                ElasticsearchTestSupport.testcontainersDependency("1.21.4", "") +
                                "</dependencies>"),
                        source -> source.path("nested/pom.xml")));
    }

    @Test
    void publicStrictRecipeScansPreUpgradeOwnershipBeforeTheCustomWhitelistUpgrade() {
        var recipe = Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.elasticsearch").build().activateRecipes(RECIPE);
        assertEquals(2, recipe.getRecipeList().size());
        assertEquals(MarkSelectedElasticsearchProjects.class.getName(),
                recipe.getRecipeList().get(0).getName());
        assertEquals(UpgradeSelectedTestcontainersElasticsearchDependency.class.getName(),
                recipe.getRecipeList().get(1).getName());
    }
}
