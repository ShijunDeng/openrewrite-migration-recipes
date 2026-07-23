package com.huawei.clouds.openrewrite.springweb;

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

class UpgradeSpringWebTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springweb.UpgradeSpringWebTo6_2_19";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.springweb.MigrateSpringWebTo6_2_19";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(UPGRADE));
    }

    @ParameterizedTest(name = "Maven upgrades exact source {0}")
    @ValueSource(strings = {
            "5.2.15.RELEASE", "5.2.22.RELEASE", "5.2.24.RELEASE", "5.2.5.RELEASE",
            "5.2.9.RELEASE", "5.3.18", "5.3.19", "5.3.20", "5.3.21", "5.3.26",
            "5.3.27", "5.3.8", "5.3.9", "6.0.11"
    })
    void upgradesEverySelectedSourceVersion(String version) {
        rewriteRun(pomXml(pom(version), pom("6.2.19")));
    }

    @ParameterizedTest(name = "Groovy upgrades exact source {0}")
    @ValueSource(strings = {
            "5.2.15.RELEASE", "5.2.22.RELEASE", "5.2.24.RELEASE", "5.2.5.RELEASE",
            "5.2.9.RELEASE", "5.3.18", "5.3.19", "5.3.20", "5.3.21", "5.3.26",
            "5.3.27", "5.3.8", "5.3.9", "6.0.11"
    })
    void upgradesEverySelectedSourceVersionInGroovyGradle(String version) {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.springframework:spring-web:" + version + "' }",
                "dependencies { implementation 'org.springframework:spring-web:6.2.19' }"));
    }

    @ParameterizedTest(name = "Kotlin Gradle upgrades exact source {0}")
    @ValueSource(strings = {
            "5.2.15.RELEASE", "5.2.22.RELEASE", "5.2.24.RELEASE", "5.2.5.RELEASE",
            "5.2.9.RELEASE", "5.3.18", "5.3.19", "5.3.20", "5.3.21", "5.3.26",
            "5.3.27", "5.3.8", "5.3.9", "6.0.11"
    })
    void upgradesEverySelectedSourceVersionInKotlinGradle(String version) {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.springframework:spring-web:" + version + "\") }",
                "dependencies { implementation(\"org.springframework:spring-web:6.2.19\") }"));
    }

    @ParameterizedTest(name = "Never rewrites fixed non-source {0}")
    @ValueSource(strings = {
            "5.2.4.RELEASE", "5.2.6.RELEASE", "5.2.14.RELEASE", "5.2.16.RELEASE",
            "5.2.23.RELEASE", "5.2.25.RELEASE", "5.3.7", "5.3.10", "5.3.17",
            "5.3.22", "5.3.25", "5.3.28", "6.0.10", "6.0.12", "6.1.0",
            "6.2.18", "6.2.19", "6.2.20", "6.3.0", "7.0.0"
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
                        <profiles><profile><id>web</id><dependencies>
                          %s
                        </dependencies></profile></profiles>
                        """.formatted(
                        target("5.2.22.RELEASE",
                               "<type>jar</type><scope>runtime</scope><optional>true</optional>"),
                        target("5.3.26",
                               "<exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>"))),
                project("""
                        <dependencyManagement><dependencies>
                          %s
                        </dependencies></dependencyManagement>
                        <profiles><profile><id>web</id><dependencies>
                          %s
                        </dependencies></profile></profiles>
                        """.formatted(
                        target("6.2.19",
                               "<type>jar</type><scope>runtime</scope><optional>true</optional>"),
                        target("6.2.19",
                               "<exclusions><exclusion><groupId>x</groupId><artifactId>y</artifactId></exclusion></exclusions>")))));
    }

    @Test
    void upgradesExclusiveRootPropertyAcrossRootAndProfileReferences() {
        rewriteRun(pomXml(
                project("""
                        <properties><spring.web.version>5.3.27</spring.web.version></properties>
                        <dependencies>%s</dependencies>
                        <profiles><profile><id>p</id><dependencies>%s</dependencies></profile></profiles>
                        """.formatted(target("${spring.web.version}", ""),
                                     target("${spring.web.version}", ""))),
                project("""
                        <properties><spring.web.version>6.2.19</spring.web.version></properties>
                        <dependencies>%s</dependencies>
                        <profiles><profile><id>p</id><dependencies>%s</dependencies></profile></profiles>
                        """.formatted(target("${spring.web.version}", ""),
                                     target("${spring.web.version}", "")))));
    }

    @Test
    void upgradesExclusiveProfilePropertyWithoutTouchingRootProperty() {
        rewriteRun(pomXml(
                project("""
                        <properties><spring.version>5.3.27</spring.version></properties>
                        <profiles><profile><id>p</id>
                          <properties><web.version>6.0.11</web.version></properties>
                          <dependencies>%s</dependencies>
                        </profile></profiles>
                        """.formatted(target("${web.version}", ""))),
                project("""
                        <properties><spring.version>5.3.27</spring.version></properties>
                        <profiles><profile><id>p</id>
                          <properties><web.version>6.2.19</web.version></properties>
                          <dependencies>%s</dependencies>
                        </profile></profiles>
                        """.formatted(target("${web.version}", "")))));
    }

    @Test
    void preservesSharedDuplicateAttributeAndShadowedProperties() {
        rewriteRun(
                xml("<project marker=\"${spring.version}\"><modelVersion>4.0.0</modelVersion>" +
                    "<groupId>x</groupId><artifactId>a</artifactId><version>1</version>" +
                    "<properties><spring.version>5.3.27</spring.version></properties><dependencies>" +
                    target("${spring.version}", "") + "</dependencies></project>",
                    source -> source.path("attribute/pom.xml")),
                xml(project("<properties><spring.version>5.3.27</spring.version>" +
                            "<spring.version>5.3.27</spring.version></properties><dependencies>" +
                            target("${spring.version}", "") + "</dependencies>"),
                    source -> source.path("duplicate/pom.xml")),
                xml(project("<properties><spring.version>5.3.27</spring.version></properties><dependencies>" +
                            target("${spring.version}", "") +
                            "<dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId>" +
                            "<version>${spring.version}</version></dependency></dependencies>"),
                    source -> source.path("shared/pom.xml")),
                xml(project("<properties><spring.version>5.3.27</spring.version></properties><dependencies>" +
                            target("${spring.version}", "") +
                            "</dependencies><profiles><profile><id>p</id><properties>" +
                            "<spring.version>6.0.11</spring.version></properties></profile></profiles>"),
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
                            target("5.3.27", "<classifier>sources</classifier>") +
                            target("5.3.27", "<type>test-jar</type>") +
                            "<dependency><groupId>example</groupId><artifactId>spring-web</artifactId>" +
                            "<version>5.3.27</version></dependency>" +
                            "<dependency><groupId>org.springframework</groupId><artifactId>spring-web-extra</artifactId>" +
                            "<version>5.3.27</version></dependency></dependencies>" +
                            "<build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId><version>1</version>" +
                            "<dependencies>" + target("5.3.27", "") + "</dependencies></plugin></plugins></build>"),
                    source -> source.path("pom.xml")),
                xml("<root>" + project("<dependencies>" + target("5.3.27", "") + "</dependencies>") + "</root>",
                    source -> source.path("nested/pom.xml"))
        );
    }

    @Test
    void upgradesRootGroovyStringsMapsAndKotlinStrings() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework:spring-web:5.3.18' }",
                        "dependencies { implementation 'org.springframework:spring-web:6.2.19' }",
                        source -> source.path("literal.gradle")),
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.springframework', name: 'spring-web', version: '5.3.21' }",
                        "dependencies { runtimeOnly group: 'org.springframework', name: 'spring-web', version: '6.2.19' }",
                        source -> source.path("map.gradle")),
                buildGradle(
                        "dependencies { testImplementation([group: 'org.springframework', name: 'spring-web', version: '6.0.11']) }",
                        "dependencies { testImplementation([group: 'org.springframework', name: 'spring-web', version: '6.2.19']) }",
                        source -> source.path("map-literal.gradle")),
                buildGradleKts(
                        "dependencies { implementation(\"org.springframework:spring-web:5.2.15.RELEASE\") }",
                        "dependencies { implementation(\"org.springframework:spring-web:6.2.19\") }")
        );
    }

    @Test
    void preservesInterpolationCatalogPlatformFourPartVariantsAndLookalikes() {
        rewriteRun(
                buildGradle("""
                        def v = '5.3.27'
                        dependencies {
                          implementation "org.springframework:spring-web:${v}"
                          implementation "prefix-org.springframework:spring-web:${v}"
                          implementation libs.spring.web
                          implementation 'org.springframework:spring-web:5.3.27:sources'
                          implementation group: 'org.springframework', name: 'spring-web', version: '5.3.27', classifier: 'sources'
                          implementation([group: 'org.springframework', name: 'spring-web', version: '5.3.27', ext: 'zip'])
                          implementation platform('org.springframework:spring-framework-bom:5.3.27')
                        }
                        """),
                buildGradleKts("""
                        val v = "5.3.27"
                        dependencies {
                            implementation("org.springframework:spring-web:$v")
                            implementation(libs.spring.web)
                        }
                        """)
        );
    }

    @Test
    void ignoresBuildscriptCustomAndNestedProjectDependencies() {
        rewriteRun(buildGradle("""
                buildscript { dependencies { implementation 'org.springframework:spring-web:5.3.27' } }
                dependencies { generated { implementation 'org.springframework:spring-web:5.3.27' } }
                project(':child') { dependencies { implementation 'org.springframework:spring-web:5.3.27' } }
                fake { dependencies { implementation 'org.springframework:spring-web:5.3.27' } }
                """));
    }

    @Test
    void skipsGeneratedBuildFilesAndIsIdempotent() {
        rewriteRun(
                xml(pom("5.3.27"), source -> source.path("target/generated/pom.xml")),
                buildGradle("dependencies { implementation 'org.springframework:spring-web:5.3.27' }",
                            source -> source.path("install/build.gradle")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("5.3.27"), pom("6.2.19")));
    }

    @Test
    void discoversAllPublicRecipesAndRecommendedOrdering() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        String[] names = {
                UPGRADE,
                "com.huawei.clouds.openrewrite.springweb.MigrateDeterministicSpringWeb6Java",
                "com.huawei.clouds.openrewrite.springweb.FindSpringWeb6BuildMigrationRisks",
                "com.huawei.clouds.openrewrite.springweb.FindSpringWeb6SourceAndConfigurationRisks",
                MIGRATE
        };
        for (String name : names) assertEquals(name, environment.activateRecipes(name).getName());
        Recipe aggregate = environment.activateRecipes(MIGRATE);
        assertTrue(aggregate.getRecipeList().size() >= 4);
        assertEquals(UPGRADE, aggregate.getRecipeList().get(0).getName());
    }

    @Test
    void aggregateDoesNotMarkSuccessfullyUpgradedExclusivePropertyAsExternalOwner() {
        rewriteRun(spec -> spec.recipe(recipe(MIGRATE)),
                pomXml(
                        project("<properties><web.version>5.3.27</web.version></properties>" +
                                "<dependencies>" + target("${web.version}", "") + "</dependencies>"),
                        project("<properties><web.version>6.2.19</web.version></properties>" +
                                "<dependencies>" + target("${web.version}", "") + "</dependencies>")));
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
        return "<dependency><groupId>org.springframework</groupId><artifactId>spring-web</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") +
               metadata + "</dependency>";
    }
}
