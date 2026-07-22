package com.huawei.clouds.openrewrite.shedlockjdbc;

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

class UpgradeShedLockJdbcTemplateTest implements RewriteTest {
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.shedlockjdbc.MigrateShedLockJdbcTemplateTo7_2_1";
    private static final String ALIAS_RECIPE =
            "com.huawei.clouds.openrewrite.shedlockjdbc.UpgradeShedLockJdbcTemplateTo7_2_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedShedLockJdbcTemplateDependency());
    }

    @ParameterizedTest(name = "upgrades exact spreadsheet version {0}")
    @ValueSource(strings = {"2.2.0", "4.29.0", "4.33.0", "4.44.0"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(pomXml(pom(oldVersion), pom("7.2.1")));
    }

    @Test
    void upgradesCardsRealGradleDeclarationOnly() {
        // shamilvasanov/Cards, fixed commit 8bff0c8b21d9a6bca2c03514fef0cb68b5547bb0
        rewriteRun(buildGradle(
                "dependencies { implementation 'net.javacrumbs.shedlock:shedlock-spring:2.2.0'; implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:2.2.0' }",
                "dependencies { implementation 'net.javacrumbs.shedlock:shedlock-spring:2.2.0'; implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.2.1' }"
        ));
    }

    @Test
    void preservesRieckpilRealSharedProperty() {
        // rieckpil/blog-tutorials, fixed commit cc20cab53eeb73c404b9bcc4a22b169571f4b403
        rewriteRun(xml(sharedPropertyPom("4.29.0"), source -> source.path("pom.xml")));
    }

    @Test
    void preservesAlibabaSreWorksRealManagedSharedProperty() {
        // alibaba/SREWorks, fixed commit 5eb36fa9170fb737a06d9e690bc6df90a9924067
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>com.alibaba.sreworks</groupId><artifactId>appmanager</artifactId><version>1</version>
                  <properties><shedlock-spring.version>4.33.0</shedlock-spring.version></properties><dependencyManagement><dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>${shedlock-spring.version}</version></dependency>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock-spring.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """, source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesKonturioRealSharedPropertyAndJavaImage() {
        // konturio/insights-api, fixed commit c8252503d0adb699ffc300bc149fde51dffb5757
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>io.kontur</groupId><artifactId>insights-api</artifactId><version>1</version>
                  <properties><shedlock.version>4.44.0</shedlock.version><jib.source.image>openjdk:16-alpine</jib.source.image></properties><dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>${shedlock.version}</version></dependency>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.version}</version></dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml")
        ));
    }

    @Test
    void upgradesIsolatedMavenProperty() {
        rewriteRun(pomXml(
                isolatedPropertyPom("4.33.0"),
                isolatedPropertyPom("7.2.1")
        ));
    }

    @Test
    void preservesPropertyReferencedByPluginAndText() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><shedlock.version>4.44.0</shedlock.version></properties>
                  <dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.version}</version></dependency></dependencies>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>audit</artifactId><configuration><expected>${shedlock.version}</expected></configuration></plugin></plugins></build>
                </project>
                """, source -> source.path("pom.xml")
        ));
    }

    @Test
    void upgradesDependencyManagementLiteralAndPreservesVersionlessUse() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>4.44.0</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>7.2.1</version></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesExternalBomManagedVersionlessDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom-user</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-bom</artifactId><version>4.44.0</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.1.0", "2.2.1", "4.28.0", "4.41.0", "5.0.0", "6.10.0", "7.2.1", "7.2.2", "8.0.0", "[4,7)"})
    void preservesUnlistedTargetFutureAndRangeVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesGroovyStringMapAndKotlinLiteralNotations() {
        rewriteRun(
                buildGradle(
                        "dependencies { runtimeOnly 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.29.0' }",
                        "dependencies { runtimeOnly 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.2.1' }",
                        source -> source.path("string.gradle")
                ),
                buildGradle(
                        "dependencies { testImplementation group: 'net.javacrumbs.shedlock', name: 'shedlock-provider-jdbc-template', version: '4.33.0' }",
                        "dependencies { testImplementation group: 'net.javacrumbs.shedlock', name: 'shedlock-provider-jdbc-template', version: '7.2.1' }",
                        source -> source.path("map.gradle")
                ),
                buildGradleKts(
                        "dependencies { implementation(\"net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.44.0\") }",
                        "dependencies { implementation(\"net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.2.1\") }"
                )
        );
    }

    @Test
    void preservesGradleVariablesInterpolationAndCatalogs() {
        rewriteRun(
                buildGradle("def v = '4.44.0'\ndependencies { implementation \"net.javacrumbs.shedlock:shedlock-provider-jdbc-template:${v}\"; implementation libs.shedlock.jdbc }"),
                buildGradleKts("val v = \"4.44.0\"\ndependencies { implementation(\"net.javacrumbs.shedlock:shedlock-provider-jdbc-template:$v\"); implementation(libs.shedlock.jdbc) }")
        );
    }

    @Test
    void preservesCompanionArtifactsOtherProvidersAndSimilarCoordinates() {
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>others</artifactId><version>1</version><dependencies>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-core</artifactId><version>4.44.0</version></dependency>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>4.44.0</version></dependency>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc</artifactId><version>4.44.0</version></dependency>
                  <dependency><groupId>example</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>4.44.0</version></dependency>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template-test</artifactId><version>4.44.0</version></dependency>
                </dependencies></project>
                """, source -> source.path("pom.xml")
        ));
    }

    @Test
    void preservesDependencyShape() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shape</artifactId><version>1</version><dependencies><dependency>
                  <groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>4.44.0</version><scope>runtime</scope><optional>true</optional><classifier>tests</classifier>
                  <exclusions><exclusion><groupId>org.springframework</groupId><artifactId>spring-jdbc</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shape</artifactId><version>1</version><dependencies><dependency>
                  <groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>7.2.1</version><scope>runtime</scope><optional>true</optional><classifier>tests</classifier>
                  <exclusions><exclusion><groupId>org.springframework</groupId><artifactId>spring-jdbc</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """
        ));
    }

    @Test
    void dependencyUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("2.2.0"), pom("7.2.1")));
    }

    @Test
    void recipesAreDiscoverableAndValid() {
        Environment environment = environment();
        for (String name : new String[]{MIGRATION_RECIPE, ALIAS_RECIPE}) {
            Recipe recipe = environment.activateRecipes(name);
            assertEquals(name, recipe.getName());
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
        }
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.shedlockjdbc")
                .scanYamlResources().build();
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scheduler</artifactId><version>1</version><dependencies><dependency>
                 <groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }

    private static String isolatedPropertyPom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>isolated</artifactId><version>1</version>
                 <properties><shedlock.jdbc.version>%s</shedlock.jdbc.version></properties><dependencies><dependency>
                   <groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.jdbc.version}</version>
                 </dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static String sharedPropertyPom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>de.rieckpil.blog</groupId><artifactId>spring-boot-shedlock</artifactId><version>1</version>
                 <properties><shedlock.version>%s</shedlock.version></properties><dependencies>
                   <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>${shedlock.version}</version></dependency>
                   <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.version}</version></dependency>
                 </dependencies>
               </project>
               """.formatted(version);
    }
}
