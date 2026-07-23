package com.huawei.clouds.openrewrite.springbootactuator;

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

class UpgradeActuatorTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springbootactuator.UpgradeSpringBootActuatorTo3_5_15";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBootActuatorTo3_5_15";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(UPGRADE));
    }

    @ParameterizedTest(name = "Maven upgrades exact source {0}")
    @ValueSource(strings = {
            "2.2.6.RELEASE", "2.6.6", "2.7.9", "2.7.10", "2.7.12", "2.7.16", "2.7.17", "2.7.18",
            "3.4.0", "3.4.3", "3.4.5", "3.4.6", "3.4.9", "3.4.12"
    })
    void upgradesEverySelectedMavenVersion(String version) {
        rewriteRun(pomXml(pom(version), pom("3.5.15")));
    }

    @ParameterizedTest(name = "Groovy upgrades exact source {0}")
    @ValueSource(strings = {
            "2.2.6.RELEASE", "2.6.6", "2.7.9", "2.7.10", "2.7.12", "2.7.16", "2.7.17", "2.7.18",
            "3.4.0", "3.4.3", "3.4.5", "3.4.6", "3.4.9", "3.4.12"
    })
    void upgradesEverySelectedGroovyVersion(String version) {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.springframework.boot:spring-boot-starter-actuator:" +
                version + "' }",
                "dependencies { implementation 'org.springframework.boot:spring-boot-starter-actuator:3.5.15' }"));
    }

    @ParameterizedTest(name = "Kotlin upgrades exact source {0}")
    @ValueSource(strings = {
            "2.2.6.RELEASE", "2.6.6", "2.7.9", "2.7.10", "2.7.12", "2.7.16", "2.7.17", "2.7.18",
            "3.4.0", "3.4.3", "3.4.5", "3.4.6", "3.4.9", "3.4.12"
    })
    void upgradesEverySelectedKotlinVersion(String version) {
        rewriteRun(buildGradleKts(
                "dependencies { implementation(\"org.springframework.boot:spring-boot-starter-actuator:" +
                version + "\") }",
                "dependencies { implementation(\"org.springframework.boot:spring-boot-starter-actuator:3.5.15\") }"));
    }

    @ParameterizedTest(name = "Preserves non-selected fixed version {0}")
    @ValueSource(strings = {
            "2.2.5.RELEASE", "2.7.8", "2.7.11", "3.3.13", "3.4.1", "3.4.13",
            "3.5.14", "3.5.15", "3.5.16", "3.6.0", "4.0.0", "5.0.1"
    })
    void preservesFixedVersionsOutsideSelection(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesStarterParentThatOwnsVersionlessStarter() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>2.7.18</version></parent>
                  <artifactId>app</artifactId>
                  <dependencies>%s</dependencies>
                </project>
                """.formatted(target(null, "")),
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.15</version></parent>
                  <artifactId>app</artifactId>
                  <dependencies>%s</dependencies>
                </project>
                """.formatted(target(null, ""))));
    }

    @Test
    void upgradesImportedBootBomThatOwnsVersionlessStarter() {
        rewriteRun(pomXml(
                project("""
                        <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                        <dependencies>%s</dependencies>
                        """.formatted(bom("3.4.12"), target(null, ""))),
                project("""
                        <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                        <dependencies>%s</dependencies>
                        """.formatted(bom("3.5.15"), target(null, "")))));
    }

    @Test
    void upgradesExclusivePropertyAcrossStarterAndBootOwner() {
        rewriteRun(pomXml(
                project("""
                        <properties><boot.version>2.7.18</boot.version></properties>
                        <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                        <dependencies>%s</dependencies>
                        """.formatted(bom("${boot.version}"), target("${boot.version}", ""))),
                project("""
                        <properties><boot.version>3.5.15</boot.version></properties>
                        <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                        <dependencies>%s</dependencies>
                        """.formatted(bom("${boot.version}"), target("${boot.version}", "")))));
    }

    @Test
    void preservesSharedDuplicateMissingRangedAndShadowedProperties() {
        rewriteRun(
                xml(project("""
                        <properties><boot.version>2.7.18</boot.version></properties>
                        <dependencies>
                          %s
                          <dependency><groupId>x</groupId><artifactId>other</artifactId><version>${boot.version}</version></dependency>
                        </dependencies>
                        """.formatted(target("${boot.version}", ""))),
                    source -> source.path("shared/pom.xml")),
                xml(project("""
                        <properties><boot.version>2.7.18</boot.version><boot.version>3.4.12</boot.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(target("${boot.version}", ""))),
                    source -> source.path("duplicate/pom.xml")),
                xml(project("<dependencies>" + target("${missing}", "") +
                            target("[2.7,3.6)", "") + "</dependencies>"),
                    source -> source.path("unresolved/pom.xml")),
                xml(project("""
                        <properties><boot.version>2.7.18</boot.version></properties>
                        <dependencies>%s</dependencies>
                        <profiles><profile><id>p</id><properties><boot.version>3.4.12</boot.version></properties></profile></profiles>
                        """.formatted(target("${boot.version}", ""))),
                    source -> source.path("shadowed/pom.xml"))
        );
    }

    @Test
    void upgradesProfileOwnedVersionAndPreservesMetadata() {
        rewriteRun(pomXml(
                project("""
                        <profiles><profile><id>ops</id>
                          <properties><actuator.version>2.6.6</actuator.version></properties>
                          <dependencies>%s</dependencies>
                        </profile></profiles>
                        """.formatted(target("${actuator.version}",
                                "<scope>runtime</scope><optional>true</optional>"))),
                project("""
                        <profiles><profile><id>ops</id>
                          <properties><actuator.version>3.5.15</actuator.version></properties>
                          <dependencies>%s</dependencies>
                        </profile></profiles>
                        """.formatted(target("${actuator.version}",
                                "<scope>runtime</scope><optional>true</optional>")))));
    }

    @Test
    void preservesVariantsPluginDependenciesNestedLookalikesAndGeneratedFiles() {
        rewriteRun(
                xml(project("<dependencies>" +
                            target("2.7.18", "<classifier>sources</classifier>") +
                            target("3.4.12", "<type>test-jar</type>") +
                            "<dependency><groupId>x</groupId><artifactId>spring-boot-starter-actuator</artifactId><version>2.7.18</version></dependency>" +
                            "</dependencies><build><plugins><plugin><groupId>x</groupId><artifactId>p</artifactId>" +
                            "<dependencies>" + target("2.7.18", "") + "</dependencies></plugin></plugins></build>"),
                    source -> source.path("pom.xml")),
                xml("<root>" + pom("2.7.18") + "</root>", source -> source.path("nested/pom.xml")),
                xml(pom("2.7.18"), source -> source.path("target/generated/pom.xml")),
                buildGradle("dependencies { implementation 'org.springframework.boot:spring-boot-starter-actuator:2.7.18' }",
                            source -> source.path("build/generated/build.gradle"))
        );
    }

    @Test
    void upgradesGroovyMapAndLocalBootPlatform() {
        rewriteRun(
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.springframework.boot', name: 'spring-boot-starter-actuator', version: '3.4.9' }",
                        "dependencies { runtimeOnly group: 'org.springframework.boot', name: 'spring-boot-starter-actuator', version: '3.5.15' }",
                        source -> source.path("map.gradle")),
                buildGradle(
                        """
                        dependencies {
                          implementation platform('org.springframework.boot:spring-boot-dependencies:2.7.18')
                          implementation 'org.springframework.boot:spring-boot-starter-actuator'
                        }
                        """,
                        """
                        dependencies {
                          implementation platform('org.springframework.boot:spring-boot-dependencies:3.5.15')
                          implementation 'org.springframework.boot:spring-boot-starter-actuator'
                        }
                        """,
                        source -> source.path("platform.gradle"))
        );
    }

    @Test
    void preservesCatalogInterpolationPlatformWithoutStarterAndVariants() {
        rewriteRun(
                buildGradle("""
                        def v = '2.7.18'
                        dependencies {
                          implementation "org.springframework.boot:spring-boot-starter-actuator:${v}"
                          implementation libs.spring.boot.starter.actuator
                          implementation 'org.springframework.boot:spring-boot-starter-actuator:2.7.18:sources'
                          implementation group: 'org.springframework.boot', name: 'spring-boot-starter-actuator', version: '2.7.18', classifier: 'sources'
                        }
                        """),
                buildGradle("""
                        dependencies {
                          implementation platform('org.springframework.boot:spring-boot-dependencies:2.7.18')
                          implementation 'x:y:1'
                        }
                        """)
        );
    }

    @Test
    void isIdempotentAndPublishesAllRecipesInOrder() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("3.4.12"), pom("3.5.15")));
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        String[] names = {
                UPGRADE,
                "com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBootActuatorConfiguration",
                "com.huawei.clouds.openrewrite.springbootactuator.MigrateSpringBoot3JakartaPackages",
                "com.huawei.clouds.openrewrite.springbootactuator.FindSpringBootActuator3_5Risks",
                MIGRATE
        };
        for (String name : names) assertEquals(name, environment.activateRecipes(name).getName());
        Recipe aggregate = environment.activateRecipes(MIGRATE);
        assertTrue(aggregate.getRecipeList().size() >= 4);
        assertEquals(UPGRADE, aggregate.getRecipeList().get(0).getName());
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
        return "<dependency><groupId>org.springframework.boot</groupId>" +
               "<artifactId>spring-boot-starter-actuator</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") +
               metadata + "</dependency>";
    }

    private static String bom(String version) {
        return "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId>" +
               "<version>" + version + "</version><type>pom</type><scope>import</scope></dependency>";
    }
}
