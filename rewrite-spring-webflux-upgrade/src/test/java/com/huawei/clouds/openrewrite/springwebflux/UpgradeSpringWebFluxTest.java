package com.huawei.clouds.openrewrite.springwebflux;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeSpringWebFluxTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springwebflux.UpgradeSpringWebFluxTo6_2_19";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.springwebflux.MigrateSpringWebFluxTo6_2_19";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(UPGRADE));
    }

    @ParameterizedTest(name = "Maven upgrades exact source {0}")
    @ValueSource(strings = {
            "5.2.7.RELEASE", "5.3.26", "6.0.19", "6.1.13", "6.1.14",
            "6.2.7", "6.2.8", "6.2.10", "6.2.11", "6.2.12", "6.2.17", "6.2.18"
    })
    void upgradesEverySelectedSourceVersion(String version) {
        rewriteRun(pomXml(pom(version), pom("6.2.19")));
    }

    @ParameterizedTest(name = "Groovy upgrades exact source {0}")
    @ValueSource(strings = {
            "5.2.7.RELEASE", "5.3.26", "6.0.19", "6.1.13", "6.1.14",
            "6.2.7", "6.2.8", "6.2.10", "6.2.11", "6.2.12", "6.2.17", "6.2.18"
    })
    void upgradesEverySelectedSourceVersionInGroovyGradle(String version) {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.springframework:spring-webflux:" + version + "' }",
                "dependencies { implementation 'org.springframework:spring-webflux:6.2.19' }"));
    }

    @ParameterizedTest(name = "Kotlin Gradle upgrades exact source {0}")
    @ValueSource(strings = {
            "5.2.7.RELEASE", "5.3.26", "6.0.19", "6.1.13", "6.1.14",
            "6.2.7", "6.2.8", "6.2.10", "6.2.11", "6.2.12", "6.2.17", "6.2.18"
    })
    void upgradesEverySelectedSourceVersionInKotlinGradle(String version) {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.springframework:spring-webflux:" + version + "\") }",
                "dependencies { implementation(\"org.springframework:spring-webflux:6.2.19\") }"));
    }

    @ParameterizedTest(name = "Never rewrites non-selected fixed source {0}")
    @ValueSource(strings = {
            "5.2.6.RELEASE", "5.2.8.RELEASE", "5.3.25", "5.3.27", "6.0.18",
            "6.1.12", "6.2.6", "6.2.9", "6.2.13", "6.2.19", "6.2.20", "7.0.0"
    })
    void preservesEveryFixedVersionOutsideSelection(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesDependencyManagementProfileAndPreservesMetadata() {
        rewriteRun(pomXml(
                project("""
                        <dependencyManagement><dependencies>
                          %s
                        </dependencies></dependencyManagement>
                        <profiles><profile><id>reactive</id><dependencies>
                          %s
                        </dependencies></profile></profiles>
                        """.formatted(
                        target("5.3.26", "<type>jar</type><scope>runtime</scope><optional>true</optional>"),
                        target("6.1.14", "<exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>"))),
                project("""
                        <dependencyManagement><dependencies>
                          %s
                        </dependencies></dependencyManagement>
                        <profiles><profile><id>reactive</id><dependencies>
                          %s
                        </dependencies></profile></profiles>
                        """.formatted(
                        target("6.2.19", "<type>jar</type><scope>runtime</scope><optional>true</optional>"),
                        target("6.2.19", "<exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>")))));
    }

    @Test
    void upgradesExclusiveRootPropertyAcrossRootAndProfileReferences() {
        rewriteRun(pomXml(
                project("""
                        <properties><spring.webflux.version>5.2.7.RELEASE</spring.webflux.version></properties>
                        <dependencies>%s</dependencies>
                        <profiles><profile><id>p</id><dependencies>%s</dependencies></profile></profiles>
                        """.formatted(target("${spring.webflux.version}", ""),
                                     target("${spring.webflux.version}", ""))),
                project("""
                        <properties><spring.webflux.version>6.2.19</spring.webflux.version></properties>
                        <dependencies>%s</dependencies>
                        <profiles><profile><id>p</id><dependencies>%s</dependencies></profile></profiles>
                        """.formatted(target("${spring.webflux.version}", ""),
                                     target("${spring.webflux.version}", "")))));
    }

    @Test
    void upgradesExclusiveProfilePropertyWithoutTouchingRootProperty() {
        rewriteRun(pomXml(
                project("""
                        <properties><spring.version>5.3.26</spring.version></properties>
                        <profiles><profile><id>p</id>
                          <properties><webflux.version>6.1.13</webflux.version></properties>
                          <dependencies>%s</dependencies>
                        </profile></profiles>
                        """.formatted(target("${webflux.version}", ""))),
                project("""
                        <properties><spring.version>5.3.26</spring.version></properties>
                        <profiles><profile><id>p</id>
                          <properties><webflux.version>6.2.19</webflux.version></properties>
                          <dependencies>%s</dependencies>
                        </profile></profiles>
                        """.formatted(target("${webflux.version}", "")))));
    }

    @Test
    void preservesSharedDuplicateAttributeAndShadowedProperties() {
        rewriteRun(
                xml("<project marker=\"${spring.version}\"><modelVersion>4.0.0</modelVersion>" +
                    "<groupId>x</groupId><artifactId>a</artifactId><version>1</version>" +
                    "<properties><spring.version>5.3.26</spring.version></properties><dependencies>" +
                    target("${spring.version}", "") + "</dependencies></project>",
                    source -> source.path("attribute/pom.xml")),
                xml(project("<properties><spring.version>5.3.26</spring.version>" +
                            "<spring.version>5.3.26</spring.version></properties><dependencies>" +
                            target("${spring.version}", "") + "</dependencies>"),
                    source -> source.path("duplicate/pom.xml")),
                xml(project("<properties><spring.version>5.3.26</spring.version></properties><dependencies>" +
                            target("${spring.version}", "") +
                            "<dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId>" +
                            "<version>${spring.version}</version></dependency></dependencies>"),
                    source -> source.path("shared/pom.xml")),
                xml(project("<properties><spring.version>5.3.26</spring.version></properties><dependencies>" +
                            target("${spring.version}", "") +
                            "</dependencies><profiles><profile><id>p</id><properties>" +
                            "<spring.version>6.1.13</spring.version></properties></profile></profiles>"),
                    source -> source.path("shadow/pom.xml"))
        );
    }

    @Test
    void neverDowngradesTargetFutureOutsideRangeOrExternalOwner() {
        rewriteRun(pomXml(project("<dependencies>" +
                target(null, "") +
                target("${missing.version}", "") +
                target("[5.3,6.3)", "") +
                target("5.3.25", "") +
                target("6.2.19", "") +
                target("6.2.20", "") +
                target("7.0.0", "") +
                "</dependencies>")));
    }

    @Test
    void preservesVariantsPluginDependenciesAndNestedLookalikes() {
        rewriteRun(
                xml(project("<dependencies>" +
                            target("6.1.14", "<classifier>sources</classifier>") +
                            target("6.1.14", "<type>test-jar</type>") +
                            "<dependency><groupId>example</groupId><artifactId>spring-webflux</artifactId>" +
                            "<version>6.1.14</version></dependency>" +
                            "<dependency><groupId>org.springframework</groupId><artifactId>spring-webflux-extra</artifactId>" +
                            "<version>6.1.14</version></dependency></dependencies>" +
                            "<build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><version>1</version>" +
                            "<dependencies>" + target("6.1.14", "") + "</dependencies></plugin></plugins></build>"),
                    source -> source.path("pom.xml")),
                xml("<root>" + project("<dependencies>" + target("6.1.14", "") + "</dependencies>") + "</root>",
                    source -> source.path("nested/pom.xml"))
        );
    }

    @Test
    void upgradesRootGroovyStringsMapsAndKotlinStrings() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework:spring-webflux:5.3.26' }",
                        "dependencies { implementation 'org.springframework:spring-webflux:6.2.19' }",
                        source -> source.path("literal.gradle")),
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.springframework', name: 'spring-webflux', version: '6.1.13' }",
                        "dependencies { runtimeOnly group: 'org.springframework', name: 'spring-webflux', version: '6.2.19' }",
                        source -> source.path("map.gradle")),
                buildGradle(
                        "dependencies { testImplementation([group: 'org.springframework', name: 'spring-webflux', version: '6.2.18']) }",
                        "dependencies { testImplementation([group: 'org.springframework', name: 'spring-webflux', version: '6.2.19']) }",
                        source -> source.path("map-literal.gradle")),
                buildGradleKts(
                        "dependencies { implementation(\"org.springframework:spring-webflux:6.0.19\") }",
                        "dependencies { implementation(\"org.springframework:spring-webflux:6.2.19\") }")
        );
    }

    @Test
    void preservesInterpolationCatalogPlatformFourPartVariantsAndLookalikes() {
        rewriteRun(
                buildGradle("""
                        def v = '6.1.14'
                        dependencies {
                          implementation "org.springframework:spring-webflux:${v}"
                          implementation "prefix-org.springframework:spring-webflux:${v}"
                          implementation libs.spring.webflux
                          implementation 'org.springframework:spring-webflux:6.1.14:sources'
                          implementation group: 'org.springframework', name: 'spring-webflux', version: '6.1.14', classifier: 'sources'
                          implementation([group: 'org.springframework', name: 'spring-webflux', version: '6.1.14', ext: 'zip'])
                          implementation platform('org.springframework:spring-framework-bom:6.1.14')
                        }
                        """),
                buildGradleKts("""
                        val v = "6.1.14"
                        dependencies {
                            implementation("org.springframework:spring-webflux:$v")
                            implementation(libs.spring.webflux)
                        }
                        """)
        );
    }

    @Test
    void ignoresBuildscriptCustomAndNestedProjectDependencies() {
        rewriteRun(buildGradle("""
                buildscript { dependencies { implementation 'org.springframework:spring-webflux:6.1.14' } }
                dependencies { generated { implementation 'org.springframework:spring-webflux:6.1.14' } }
                project(':child') { dependencies { implementation 'org.springframework:spring-webflux:6.1.14' } }
                fake { dependencies { implementation 'org.springframework:spring-webflux:6.1.14' } }
                """));
    }

    @Test
    void skipsGeneratedBuildFilesAndIsIdempotent() {
        rewriteRun(
                xml(pom("6.1.14"), source -> source.path("target/generated/pom.xml")),
                buildGradle("dependencies { implementation 'org.springframework:spring-webflux:6.1.14' }",
                            source -> source.path("install/build.gradle")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("6.1.14"), pom("6.2.19")));
    }

    @Test
    void discoversAllPublicRecipesAndRecommendedOrdering() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        String[] names = {
                UPGRADE,
                "com.huawei.clouds.openrewrite.springwebflux.MigrateDeterministicSpringWebFlux6Java",
                "com.huawei.clouds.openrewrite.springwebflux.FindSpringWebFlux6BuildMigrationRisks",
                "com.huawei.clouds.openrewrite.springwebflux.FindSpringWebFlux6SourceAndConfigurationRisks",
                MIGRATE
        };
        for (String name : names) assertEquals(name, environment.activateRecipes(name).getName());
        Recipe aggregate = environment.activateRecipes(MIGRATE);
        assertTrue(aggregate.getRecipeList().size() >= 4);
        assertEquals(UPGRADE, aggregate.getRecipeList().get(0).getName());
    }

    @Test
    void recommendedAggregateDoesNotMarkSuccessfullyUpgradedExclusivePropertyAsExternalOwner() {
        rewriteRun(spec -> spec.recipe(recipe(MIGRATE)),
                pomXml(
                        project("<properties><webflux.version>6.1.14</webflux.version></properties>" +
                                "<dependencies>" + target("${webflux.version}", "") + "</dependencies>"),
                        project("<properties><webflux.version>6.2.19</webflux.version></properties>" +
                                "<dependencies>" + target("${webflux.version}", "") + "</dependencies>")));
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static String pom(String version) {
        return project("<dependencies>" + target(version, "") + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String target(String version, String metadata) {
        return "<dependency><groupId>org.springframework</groupId><artifactId>spring-webflux</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") +
               metadata + "</dependency>";
    }
}
