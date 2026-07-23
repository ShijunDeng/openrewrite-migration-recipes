package com.huawei.clouds.openrewrite.jettyhttp;

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

class UpgradeJettyHttpDependencyTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.jettyhttp.UpgradeJettyHttpTo12_0_34";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(JettyHttpTestSupport.recipe(RECIPE));
    }

    @ParameterizedTest(name = "Maven exact source {0}")
    @ValueSource(strings = {
            "9.4.39.v20210325", "9.4.53.v20231009", "9.4.54.v20240208",
            "9.4.57.v20241219", "9.4.58.v20250814", "11.0.20",
            "12.0.12", "12.0.15", "12.0.16", "12.0.25"
    })
    void upgradesEveryExactMavenSource(String version) {
        rewriteRun(xml(JettyHttpTestSupport.pom(version), JettyHttpTestSupport.pom("12.0.34"),
                source -> source.path(version + "/pom.xml")));
    }

    @Test
    void upgradesDependencyManagementRootAndProfileLiterals() {
        String before = JettyHttpTestSupport.project(
                "<dependencyManagement><dependencies>" + JettyHttpTestSupport.dependency("9.4.58.v20250814", "") +
                "</dependencies></dependencyManagement><profiles><profile><id>it</id><dependencies>" +
                JettyHttpTestSupport.dependency("11.0.20", "") + "</dependencies></profile></profiles>");
        String after = before.replace("<version>9.4.58.v20250814</version>", "<version>12.0.34</version>")
                .replace("<version>11.0.20</version>", "<version>12.0.34</version>");
        rewriteRun(xml(before, after, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesExclusiveRootAndProfileProperties() {
        rewriteRun(
                xml(JettyHttpTestSupport.project(
                                "<properties><jetty.version>9.4.58.v20250814</jetty.version></properties>" +
                                "<dependencies>" + JettyHttpTestSupport.dependency("${jetty.version}", "") +
                                "</dependencies>"),
                        JettyHttpTestSupport.project(
                                "<properties><jetty.version>12.0.34</jetty.version></properties>" +
                                "<dependencies>" + JettyHttpTestSupport.dependency("${jetty.version}", "") +
                                "</dependencies>"), source -> source.path("root/pom.xml")),
                xml(JettyHttpTestSupport.project(
                                "<profiles><profile><id>it</id><properties><jetty.version>11.0.20</jetty.version>" +
                                "</properties><dependencies>" +
                                JettyHttpTestSupport.dependency("${jetty.version}", "") +
                                "</dependencies></profile></profiles>"),
                        JettyHttpTestSupport.project(
                                "<profiles><profile><id>it</id><properties><jetty.version>12.0.34</jetty.version>" +
                                "</properties><dependencies>" +
                                JettyHttpTestSupport.dependency("${jetty.version}", "") +
                                "</dependencies></profile></profiles>"), source -> source.path("profile/pom.xml")));
    }

    @ParameterizedTest(name = "ambiguous Maven property {0}")
    @MethodSource("ambiguousProperties")
    void leavesAmbiguousPropertiesUntouched(String label, String source) {
        rewriteRun(xml(source, spec -> spec.path(label + "/pom.xml")));
    }

    static Stream<Arguments> ambiguousProperties() {
        return Stream.of(
                Arguments.of("shared", JettyHttpTestSupport.project(
                        "<properties><v>11.0.20</v></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("${v}", "") +
                        "<dependency><groupId>x</groupId><artifactId>other</artifactId><version>${v}</version>" +
                        "</dependency></dependencies>")),
                Arguments.of("duplicate", JettyHttpTestSupport.project(
                        "<properties><v>11.0.20</v><v>11.0.20</v></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("${v}", "") + "</dependencies>")),
                Arguments.of("attribute", "<project marker=\"${v}\"><modelVersion>4.0.0</modelVersion>" +
                        "<groupId>x</groupId><artifactId>a</artifactId><version>1</version>" +
                        "<properties><v>11.0.20</v></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("${v}", "") + "</dependencies></project>"),
                Arguments.of("profile-shadow", JettyHttpTestSupport.project(
                        "<properties><v>11.0.20</v></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("${v}", "") +
                        "</dependencies><profiles><profile><id>it</id><properties><v>11.0.20</v>" +
                        "</properties></profile></profiles>")),
                Arguments.of("build-reference", JettyHttpTestSupport.project(
                        "<properties><v>11.0.20</v></properties><dependencies>" +
                        JettyHttpTestSupport.dependency("${v}", "") +
                        "</dependencies><build><finalName>${v}</finalName></build>")));
    }

    @ParameterizedTest(name = "non-whitelist Maven version {0}")
    @ValueSource(strings = {
            "9.4.38.v20210224", "9.4.59.v20251014", "10.0.0", "10.0.27", "11.0.19",
            "11.0.21", "12.0.11", "12.0.26", "12.0.34", "12.0.34.1", "12.0.34-sp1",
            "12.0.35", "12.1.0", "13.0.0",
            "999999999999999999999.0.0"
    })
    void neverExpandsTheWhitelistOrDowngrades(String version) {
        rewriteRun(xml(JettyHttpTestSupport.pom(version),
                source -> source.path(version.replace('.', '_') + "/pom.xml")));
    }

    @Test
    void upgradesAllSupportedRootGradleLiteralForms() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.eclipse.jetty:jetty-http:9.4.58.v20250814' }",
                        "dependencies { implementation 'org.eclipse.jetty:jetty-http:12.0.34' }",
                        source -> source.path("string.gradle")),
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.eclipse.jetty', name: 'jetty-http', version: '11.0.20' }",
                        "dependencies { runtimeOnly group: 'org.eclipse.jetty', name: 'jetty-http', version: '12.0.34' }",
                        source -> source.path("map.gradle")),
                buildGradle(
                        "dependencies { testImplementation([group: 'org.eclipse.jetty', name: 'jetty-http', version: '12.0.25']) }",
                        "dependencies { testImplementation([group: 'org.eclipse.jetty', name: 'jetty-http', version: '12.0.34']) }",
                        source -> source.path("map-literal.gradle")),
                buildGradleKts(
                        "dependencies { implementation(\"org.eclipse.jetty:jetty-http:12.0.16\") }",
                        "dependencies { implementation(\"org.eclipse.jetty:jetty-http:12.0.34\") }"));
    }

    @Test
    void preservesDynamicCatalogPlatformVariantsAndFourPartCoordinates() {
        rewriteRun(
                buildGradle("""
                        def v = '11.0.20'
                        dependencies {
                          implementation "org.eclipse.jetty:jetty-http:${v}"
                          implementation libs.jetty.http
                          implementation platform('org.eclipse.jetty:jetty-bom:11.0.20')
                          implementation 'org.eclipse.jetty:jetty-http:11.0.20:sources'
                          implementation 'org.eclipse.jetty:jetty-http:11.0.20@zip'
                          implementation group: 'org.eclipse.jetty', name: 'jetty-http',
                                         version: '11.0.20', classifier: 'sources'
                          implementation([group: 'org.eclipse.jetty', name: 'jetty-http',
                                          version: '11.0.20', ext: 'zip'])
                        }
                        """),
                buildGradleKts("""
                        val v = "11.0.20"
                        dependencies {
                          implementation("org.eclipse.jetty:jetty-http:$v")
                          implementation(libs.jetty.http)
                        }
                        """));
    }

    @ParameterizedTest(name = "nested Gradle owner {0}")
    @MethodSource("nestedGradleOwners")
    void ignoresNestedOrForeignGradleOwners(String label, String source) {
        rewriteRun(buildGradle(source, spec -> spec.path(label + "/build.gradle")));
    }

    static Stream<Arguments> nestedGradleOwners() {
        String dependency = "'org.eclipse.jetty:jetty-http:11.0.20'";
        return Stream.of(
                Arguments.of("buildscript", "buildscript { dependencies { classpath " + dependency + " } }"),
                Arguments.of("subprojects", "subprojects { dependencies { implementation " + dependency + " } }"),
                Arguments.of("allprojects", "allprojects { dependencies { implementation " + dependency + " } }"),
                Arguments.of("project", "project(':app') { dependencies { implementation " + dependency + " } }"),
                Arguments.of("constraints", "dependencies { constraints { implementation " + dependency + " } }"),
                Arguments.of("custom", "company { dependencies { implementation " + dependency + " } }"));
    }

    @Test
    void preservesVersionlessVariantsPluginDependenciesAndLookalikes() {
        rewriteRun(
                xml(JettyHttpTestSupport.project("<dependencies>" +
                        JettyHttpTestSupport.dependency(null, "") +
                        JettyHttpTestSupport.dependency("11.0.20", "<classifier>tests</classifier>") +
                        JettyHttpTestSupport.dependency("11.0.20", "<type>test-jar</type>") +
                        "<dependency><groupId>example</groupId><artifactId>jetty-http</artifactId>" +
                        "<version>11.0.20</version></dependency><dependency>" +
                        "<groupId>org.eclipse.jetty</groupId><artifactId>jetty-http-extra</artifactId>" +
                        "<version>11.0.20</version></dependency></dependencies>"),
                        source -> source.path("variants/pom.xml")),
                xml(JettyHttpTestSupport.project("<build><plugins><plugin><artifactId>x</artifactId>" +
                        "<dependencies>" + JettyHttpTestSupport.dependency("11.0.20", "") +
                        "</dependencies></plugin></plugins></build>"), source -> source.path("plugin/pom.xml")));
    }

    @ParameterizedTest(name = "generated/cache parent {0}")
    @ValueSource(strings = {
            "target", "build", "generated", "generatedSources", "install", "installation", ".gradle", ".m2",
            ".idea", "node_modules", "vendor", "reports", "test-results", "tmp", "TEMP"
    })
    void skipsGeneratedAndCacheParents(String parent) {
        rewriteRun(xml(JettyHttpTestSupport.pom("11.0.20"), source -> source.path(parent + "/pom.xml")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(JettyHttpTestSupport.pom("11.0.20"), JettyHttpTestSupport.pom("12.0.34"),
                        source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'org.eclipse.jetty:jetty-http:12.0.25' }",
                        "dependencies { implementation 'org.eclipse.jetty:jetty-http:12.0.34' }"));
    }

    @Test
    void publicStrictRecipeContainsOnlyTheCustomWhitelistUpgrade() {
        var recipe = Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.jettyhttp").build().activateRecipes(RECIPE);
        assertEquals(1, recipe.getRecipeList().size());
        assertEquals(UpgradeSelectedJettyHttpDependency.class.getName(),
                recipe.getRecipeList().get(0).getName());
    }
}
