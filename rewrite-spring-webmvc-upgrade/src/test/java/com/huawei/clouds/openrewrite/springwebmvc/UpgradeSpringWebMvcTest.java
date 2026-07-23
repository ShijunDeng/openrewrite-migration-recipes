package com.huawei.clouds.openrewrite.springwebmvc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeSpringWebMvcTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springwebmvc.UpgradeSpringWebMvcTo6_2_19";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcTo6_2_19";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(UPGRADE));
    }

    @ParameterizedTest(name = "Maven upgrades exact high-priority source {0}")
    @ValueSource(strings = {
            "5.2.5.RELEASE", "5.2.9.RELEASE", "5.3.21", "5.3.23", "5.3.26",
            "5.3.27", "5.3.30", "5.3.31", "5.3.32", "5.3.33", "5.3.34",
            "5.3.39", "6.0.11", "6.0.17", "6.0.19", "6.1.14", "6.2.0",
            "6.2.7", "6.2.8", "6.2.10", "6.2.11", "6.2.12", "6.2.17",
            "6.2.18"
    })
    void upgradesEveryHighPrioritySourceVersion(String version) {
        rewriteRun(
                xml(pom(version), pom("6.2.19"), source -> source.path("pom.xml")),
                buildGradle("dependencies { implementation 'org.springframework:spring-webmvc:" + version + "' }",
                        "dependencies { implementation 'org.springframework:spring-webmvc:6.2.19' }",
                        source -> source.path("build.gradle")),
                buildGradleKts("dependencies { implementation(\"org.springframework:spring-webmvc:" + version + "\") }",
                        "dependencies { implementation(\"org.springframework:spring-webmvc:6.2.19\") }",
                        source -> source.path("build.gradle.kts")));
    }

    @Test
    void whitelistAndTargetExactlyMatchTheUserSuppliedContract() {
        assertEquals(java.util.Set.of(
                        "5.2.5.RELEASE", "5.2.9.RELEASE", "5.3.21", "5.3.23", "5.3.26",
                        "5.3.27", "5.3.30", "5.3.31", "5.3.32", "5.3.33", "5.3.34",
                        "5.3.39", "6.0.11", "6.0.17", "6.0.19", "6.1.14", "6.2.0",
                        "6.2.7", "6.2.8", "6.2.10", "6.2.11", "6.2.12", "6.2.17",
                        "6.2.18"),
                UpgradeSelectedSpringWebMvcDependency.SOURCE_VERSIONS);
        assertEquals("6.2.19", UpgradeSelectedSpringWebMvcDependency.TARGET);
    }

    @Test
    void upgradesDirectDependencyAndPreservesMetadata() {
        rewriteRun(pomXml(
                dependency("5.3.23", "<scope>runtime</scope><optional>true</optional><exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>"),
                dependency("6.2.19", "<scope>runtime</scope><optional>true</optional><exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>")));
    }

    @Test
    void upgradesDependencyManagementAndExplicitJar() {
        rewriteRun(pomXml(
                project("<dependencyManagement><dependencies>" + target("5.3.30", "<type>jar</type>") +
                        "</dependencies></dependencyManagement><dependencies>" + target(null, "") + "</dependencies>"),
                project("<dependencyManagement><dependencies>" + target("6.2.19", "<type>jar</type>") +
                        "</dependencies></dependencyManagement><dependencies>" + target(null, "") + "</dependencies>")));
    }

    @Test
    void upgradesRootAndProfileLiteralOwners() {
        rewriteRun(pomXml(
                project("<dependencies>" + target("5.3.31", "") + "</dependencies><profiles><profile><id>p</id><dependencies>" + target("5.2.9.RELEASE", "") + "</dependencies></profile></profiles>"),
                project("<dependencies>" + target("6.2.19", "") + "</dependencies><profiles><profile><id>p</id><dependencies>" + target("6.2.19", "") + "</dependencies></profile></profiles>")));
    }

    @Test
    void upgradesExclusiveRootPropertyAcrossRootAndProfile() {
        rewriteRun(pomXml(
                propertyProject("5.3.27", "spring.webmvc.version"),
                propertyProject("6.2.19", "spring.webmvc.version")));
    }

    @Test
    void upgradesExclusiveProfilePropertyWithoutChangingRoot() {
        rewriteRun(pomXml(
                project("<properties><spring.version>5.3.26</spring.version></properties><profiles><profile><id>p</id><properties><mvc.version>5.3.21</mvc.version></properties><dependencies>" + target("${mvc.version}", "") + "</dependencies></profile></profiles>"),
                project("<properties><spring.version>5.3.26</spring.version></properties><profiles><profile><id>p</id><properties><mvc.version>6.2.19</mvc.version></properties><dependencies>" + target("${mvc.version}", "") + "</dependencies></profile></profiles>")));
    }

    @Test
    void preservesSharedAttributeDuplicateAndShadowedProperties() {
        rewriteRun(
                xml("<project marker=\"${spring.version}\"><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId><version>1</version><properties><spring.version>5.3.23</spring.version></properties><dependencies>" + target("${spring.version}", "") + "</dependencies></project>", source -> source.path("pom.xml")),
                xml(project("<properties><spring.version>5.3.23</spring.version><spring.version>5.3.23</spring.version></properties><dependencies>" + target("${spring.version}", "") + "</dependencies>"), source -> source.path("duplicate/pom.xml")),
                xml(project("<properties><spring.version>5.3.23</spring.version></properties><dependencies>" + target("${spring.version}", "") + "<dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>${spring.version}</version></dependency></dependencies>"), source -> source.path("shared/pom.xml")),
                xml(project("<properties><spring.version>5.3.23</spring.version></properties><dependencies>" + target("${spring.version}", "") + "</dependencies><profiles><profile><id>p</id><properties><spring.version>5.3.21</spring.version></properties></profile></profiles>"), source -> source.path("shadow/pom.xml"))
        );
    }

    @Test
    void preservesExternalOwnersRangesOutsideTargetAndFuture() {
        rewriteRun(xml(project("<dependencies>" +
                target(null, "") + target("${missing}", "") + target("[5.3,6)", "") +
                target("5.3.22", "") + target("6.2.19", "") + target("7.0.0", "") +
                "</dependencies>"), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "higher source {0} is never downgraded")
    @ValueSource(strings = {"6.2.20", "6.3.0", "7.0.0"})
    void higherVersionsAreNeverDowngraded(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void arbitrarilyLargeHigherVersionCannotOverflowOrDowngrade() {
        String version = "999999999999999999999999999999999999.0.0";
        rewriteRun(specification -> specification.recipe(recipe(MIGRATE)),
                xml(pom(version), source -> source.path("pom.xml").after(actual -> {
                    assertTrue(actual.contains("<version>" + version + "</version>"), actual);
                    assertTrue(actual.contains(FindSpringWebMvc6BuildRisks.TARGET_CONFLICT), actual);
                    assertFalse(actual.contains("<version>6.2.19</version>"), actual);
                    return actual;
                })));
    }

    @Test
    void recommendedCompositionMarksButNeverDowngradesHigherVersion() {
        rewriteRun(specification -> specification.recipe(recipe(MIGRATE)),
                pomXml(pom("7.0.0"), source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertTrue(after.printAll().contains("<version>7.0.0</version>"), after::printAll);
                    assertTrue(after.printAll().contains(FindSpringWebMvc6BuildRisks.TARGET_CONFLICT), after::printAll);
                    assertFalse(after.printAll().contains("<version>6.2.19</version>"), after::printAll);
                })));
    }

    @Test
    void preservesVariantsPluginDependenciesAndNestedLookalikes() {
        rewriteRun(xml(project("<dependencies>" + target("5.3.23", "<classifier>sources</classifier>") +
                        target("5.3.23", "<type>test-jar</type>") +
                        "<dependency><groupId>example</groupId><artifactId>spring-webmvc</artifactId><version>5.3.23</version></dependency>" +
                        "<dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc-extra</artifactId><version>5.3.23</version></dependency>" +
                        "</dependencies><build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><version>1</version><dependencies>" +
                        target("5.3.23", "") + "</dependencies></plugin></plugins></build>"), source -> source.path("pom.xml")),
                xml("<root>" + project("<dependencies>" + target("5.3.23", "") + "</dependencies>") + "</root>",
                        source -> source.path("nested/pom.xml")));
    }

    @Test
    void upgradesGroovyStringsMapsAndKotlinStrings() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.springframework:spring-webmvc:5.3.23' }",
                        "dependencies { implementation 'org.springframework:spring-webmvc:6.2.19' }", s -> s.path("string.gradle")),
                buildGradle("dependencies { runtimeOnly group: 'org.springframework', name: 'spring-webmvc', version: '5.3.26' }",
                        "dependencies { runtimeOnly group: 'org.springframework', name: 'spring-webmvc', version: '6.2.19' }", s -> s.path("map.gradle")),
                buildGradle("dependencies { testImplementation([group: 'org.springframework', name: 'spring-webmvc', version: '5.3.32']) }",
                        "dependencies { testImplementation([group: 'org.springframework', name: 'spring-webmvc', version: '6.2.19']) }", s -> s.path("map-literal.gradle")),
                buildGradleKts("dependencies { implementation(\"org.springframework:spring-webmvc:5.2.5.RELEASE\") }",
                        "dependencies { implementation(\"org.springframework:spring-webmvc:6.2.19\") }")
        );
    }

    @Test
    void preservesInterpolationCatalogPlatformsFourPartAndVariants() {
        rewriteRun(
                buildGradle("""
                        def v = '5.3.23'
                        dependencies {
                          implementation "org.springframework:spring-webmvc:${v}"
                          implementation libs.spring.webmvc
                          implementation 'org.springframework:spring-webmvc:5.3.23:sources'
                          implementation group: 'org.springframework', name: 'spring-webmvc', version: '5.3.23', classifier: 'sources'
                          implementation([group: 'org.springframework', name: 'spring-webmvc', version: '5.3.23', ext: 'zip'])
                          implementation platform('org.springframework:spring-framework-bom:5.3.23')
                        }
                        """),
                buildGradleKts("""
                        val v = "5.3.23"
                        dependencies {
                            implementation("org.springframework:spring-webmvc:$v")
                            implementation(libs.spring.webmvc)
                        }
                        """));
    }

    @Test
    void ignoresBuildscriptCustomAndNestedProjectDependencies() {
        rewriteRun(buildGradle("""
                buildscript { dependencies { implementation 'org.springframework:spring-webmvc:5.3.23' } }
                dependencies { generated { implementation 'org.springframework:spring-webmvc:5.3.23' } }
                project(':child') { dependencies { implementation 'org.springframework:spring-webmvc:5.3.23' } }
                fake { dependencies { implementation 'org.springframework:spring-webmvc:5.3.23' } }
                """));
    }

    @Test
    void skipsGeneratedBuildFilesAndIsIdempotent() {
        rewriteRun(
                xml(pom("5.3.23"), source -> source.path("target/generated/pom.xml")),
                buildGradle("dependencies { implementation 'org.springframework:spring-webmvc:5.3.23' }", s -> s.path("install/build.gradle")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("5.3.23"), pom("6.2.19")));
    }

    @Test
    void discoversAllPublicRecipesAndAggregateParity() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        String[] names = {
                "com.huawei.clouds.openrewrite.springwebmvc.MarkSelectedSpringWebMvcProjects",
                UPGRADE,
                "com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcServletNamespaces",
                "com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcServlet6Apis",
                "com.huawei.clouds.openrewrite.springwebmvc.MigrateDeterministicSpringWebMvc6Java",
                "com.huawei.clouds.openrewrite.springwebmvc.MigrateSelectedSpringWebMvc6Java",
                "com.huawei.clouds.openrewrite.springwebmvc.FindSpringWebMvc6BuildMigrationRisks",
                "com.huawei.clouds.openrewrite.springwebmvc.FindSpringWebMvc6SourceAndConfigurationRisks",
                "com.huawei.clouds.openrewrite.springwebmvc.FindSelectedSpringWebMvc6SourceAndConfigurationRisks",
                MIGRATE
        };
        for (String name : names) assertEquals(name, environment.activateRecipes(name).getName());
        Recipe aggregate = environment.activateRecipes(MIGRATE);
        assertTrue(aggregate.getRecipeList().size() >= 5);
        assertEquals("com.huawei.clouds.openrewrite.springwebmvc.MarkSelectedSpringWebMvcProjects",
                aggregate.getRecipeList().get(0).getName());
        assertEquals(UPGRADE, aggregate.getRecipeList().get(1).getName());
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static String pom(String version) {
        return project("<dependencies>" + target(version, "") + "</dependencies>");
    }

    private static String dependency(String version, String metadata) {
        return project("<dependencies>" + target(version, metadata) + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String target(String version, String metadata) {
        return "<dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") + metadata + "</dependency>";
    }

    private static String propertyProject(String version, String property) {
        return project("<properties><" + property + ">" + version + "</" + property + "></properties>" +
                "<dependencies>" + target("${" + property + "}", "") + "</dependencies>" +
                "<profiles><profile><id>p</id><dependencies>" + target("${" + property + "}", "") +
                "</dependencies></profile></profiles>");
    }
}
