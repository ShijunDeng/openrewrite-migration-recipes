package com.huawei.clouds.openrewrite.springboot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringBootVersionUpgradeTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springboot.UpgradeSpringBootTo3_5_15";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.springboot.MigrateSpringBootTo3_5_15";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(UPGRADE));
    }

    @ParameterizedTest(name = "Maven upgrades exact source {0}")
    @ValueSource(strings = {
            "2.1.3.RELEASE", "2.3.4.RELEASE", "2.6.6", "2.7.10", "2.7.12", "2.7.17", "2.7.18",
            "3.1.3", "3.1.6", "3.2.0", "3.2.9", "3.2.12", "3.4.0", "3.4.3", "3.4.5", "3.4.6",
            "3.4.9", "3.4.12", "3.5.12"
    })
    void upgradesEveryWorkbookSourceVersion(String version) {
        rewriteRun(pomXml(pom(version), pom("3.5.15")));
    }

    @ParameterizedTest(name = "Preserves non-selected fixed version {0}")
    @ValueSource(strings = {
            "2.1.2.RELEASE", "2.7.9", "2.7.11", "3.0.13", "3.2.1", "3.3.13", "3.4.13",
            "3.5.14", "3.5.15", "3.5.16", "3.6.0", "4.0.0", "5.0.1"
    })
    void preservesFixedVersionsOutsideSelection(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesEveryLocalMavenOwnerAndPreservesMetadata() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>2.7.18</version>
                    <relativePath/>
                  </parent>
                  <artifactId>app</artifactId>
                  <dependencyManagement><dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-dependencies</artifactId>
                      <version>3.4.12</version>
                      <type>pom</type><scope>import</scope>
                    </dependency>
                  </dependencies></dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot</artifactId>
                      <version>3.5.12</version>
                      <scope>runtime</scope><optional>true</optional>
                    </dependency>
                  </dependencies>
                  <build><plugins><plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>3.2.9</version>
                  </plugin></plugins></build>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.5.15</version>
                    <relativePath/>
                  </parent>
                  <artifactId>app</artifactId>
                  <dependencyManagement><dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-dependencies</artifactId>
                      <version>3.5.15</version>
                      <type>pom</type><scope>import</scope>
                    </dependency>
                  </dependencies></dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot</artifactId>
                      <version>3.5.15</version>
                      <scope>runtime</scope><optional>true</optional>
                    </dependency>
                  </dependencies>
                  <build><plugins><plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>3.5.15</version>
                  </plugin></plugins></build>
                </project>
                """));
    }

    @Test
    void upgradesExclusivePropertyAcrossBootOwners() {
        rewriteRun(pomXml(
                project("""
                        <properties><boot.version>2.7.18</boot.version></properties>
                        <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                        <build><plugins>%s</plugins></build>
                        """.formatted(bom("${boot.version}"), plugin("${boot.version}"))),
                project("""
                        <properties><boot.version>3.5.15</boot.version></properties>
                        <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                        <build><plugins>%s</plugins></build>
                        """.formatted(bom("${boot.version}"), plugin("${boot.version}")))));
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
                        """.formatted(dependency("${boot.version}", ""))),
                    source -> source.path("shared/pom.xml")),
                xml(project("""
                        <properties><boot.version>2.7.18</boot.version><boot.version>3.4.12</boot.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(dependency("${boot.version}", ""))),
                    source -> source.path("duplicate/pom.xml")),
                xml(project("<dependencies>" + dependency("${missing}", "") +
                            dependency("[2.7,3.6)", "") + "</dependencies>"),
                    source -> source.path("unresolved/pom.xml")),
                xml(project("""
                        <properties><boot.version>2.7.18</boot.version></properties>
                        <dependencies>%s</dependencies>
                        <profiles><profile><id>p</id><properties><boot.version>3.4.12</boot.version></properties></profile></profiles>
                        """.formatted(dependency("${boot.version}", ""))),
                    source -> source.path("shadowed/pom.xml"))
        );
    }

    @Test
    void preservesVariantsLookalikesNestedAndGeneratedBuildFiles() {
        rewriteRun(
                xml(project("<dependencies>" +
                            dependency("2.7.18", "<classifier>sources</classifier>") +
                            dependency("3.4.12", "<type>test-jar</type>") +
                            "<dependency><groupId>x</groupId><artifactId>spring-boot</artifactId><version>2.7.18</version></dependency>" +
                            "</dependencies><build><plugins><plugin><groupId>x</groupId>" +
                            "<artifactId>spring-boot-maven-plugin</artifactId><version>2.7.18</version>" +
                            "</plugin></plugins></build>"), source -> source.path("pom.xml")),
                xml("<root>" + pom("2.7.18") + "</root>", source -> source.path("nested/pom.xml")),
                xml(pom("2.7.18"), source -> source.path("target/generated/pom.xml")),
                buildGradle(
                        "dependencies { implementation 'org.springframework.boot:spring-boot:2.7.18' }",
                        source -> source.path("build/generated/build.gradle"))
        );
    }

    @Test
    void upgradesGroovyDependencyMapPlatformAndPlugin() {
        rewriteRun(
                buildGradle(
                        """
                        plugins {
                          id 'java'
                          id 'org.springframework.boot' version '2.7.18'
                        }
                        dependencies {
                          runtimeOnly group: 'org.springframework.boot', name: 'spring-boot', version: '3.4.9'
                          implementation platform('org.springframework.boot:spring-boot-dependencies:3.2.12')
                        }
                        """,
                        """
                        plugins {
                          id 'java'
                          id 'org.springframework.boot' version '3.5.15'
                        }
                        dependencies {
                          runtimeOnly group: 'org.springframework.boot', name: 'spring-boot', version: '3.5.15'
                          implementation platform('org.springframework.boot:spring-boot-dependencies:3.5.15')
                        }
                        """));
    }

    @Test
    void upgradesKotlinDependencyPlatformAndPlugin() {
        rewriteRun(buildGradleKts(
                """
                plugins {
                  java
                  id("org.springframework.boot") version "3.2.0"
                }
                dependencies {
                  runtimeOnly("org.springframework.boot:spring-boot:3.1.6")
                  implementation(platform("org.springframework.boot:spring-boot-dependencies:3.4.3"))
                }
                """,
                """
                plugins {
                  java
                  id("org.springframework.boot") version "3.5.15"
                }
                dependencies {
                  runtimeOnly("org.springframework.boot:spring-boot:3.5.15")
                  implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.15"))
                }
                """));
    }

    @Test
    void preservesCatalogInterpolationVariantsAndIndirectPlugins() {
        rewriteRun(
                buildGradle("""
                        def v = '2.7.18'
                        plugins {
                          id 'org.springframework.boot'
                          id 'org.springframework.boot' version bootVersion
                        }
                        dependencies {
                          implementation "org.springframework.boot:spring-boot:${v}"
                          implementation libs.spring.boot
                          implementation 'org.springframework.boot:spring-boot:2.7.18:sources'
                          implementation group: 'org.springframework.boot', name: 'spring-boot',
                              version: '2.7.18', classifier: 'sources'
                        }
                        """),
                buildGradleKts("""
                        plugins {
                          alias(libs.plugins.spring.boot)
                          id("org.springframework.boot") version libs.versions.spring.boot
                        }
                        dependencies {
                          implementation(libs.spring.boot)
                        }
                        """));
    }

    @Test
    void isTwoCycleIdempotentAndPublishesAggregateOrder() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("3.4.12"), pom("3.5.15")));
        Recipe aggregate = recipe(MIGRATE);
        assertEquals(
                "com.huawei.clouds.openrewrite.springboot.MarkSelectedSpringBootProjects",
                aggregate.getRecipeList().get(0).getName());
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static String pom(String version) {
        return project("<dependencies>" + dependency(version, "") + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>" +
               "<artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dependency(String version, String metadata) {
        return "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot</artifactId>" +
               "<version>" + version + "</version>" + metadata + "</dependency>";
    }

    private static String bom(String version) {
        return "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId>" +
               "<version>" + version + "</version><type>pom</type><scope>import</scope></dependency>";
    }

    private static String plugin(String version) {
        return "<plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId>" +
               "<version>" + version + "</version></plugin>";
    }
}
