package com.huawei.clouds.openrewrite.springboot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringBootBuildRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springboot.FindSpringBoot3_5Risks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe());
    }

    @ParameterizedTest(name = "No-downgrade conflict {0}")
    @ValueSource(strings = {
            "3.5.15.1", "3.5.15-sp1", "3.5.16", "3.5.20", "3.5.16.1",
            "3.6.0", "3.9.1", "4.0.0", "4.1.2", "5.0.0",
            "3.5.15.999999999999999999999999", "999999999999999999999999.0.0"
    })
    void marksEveryHigherVersionWithTheExactNoDowngradeMessage(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml").after(actual -> {
            assertTrue(actual.contains("目标版本冲突（禁止降级）"), actual);
            assertFalse(actual.contains(FindSpringBoot35BuildRisks.OUTSIDE), actual);
            return actual;
        })));
    }

    @Test
    void distinguishesOwnerOutsideVariantAndFutureConflict() {
        rewriteRun(pomXml(project("""
                <dependencies>
                  %s
                  %s
                  %s
                  %s
                </dependencies>
                """.formatted(
                dependency(null, ""),
                dependency("3.4.1", ""),
                dependency("4.0.0", ""),
                dependency("3.4.12", "<classifier>sources</classifier>"))),
                source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringBoot35BuildRisks.OWNER), actual);
                    assertTrue(actual.contains(FindSpringBoot35BuildRisks.OUTSIDE), actual);
                    assertTrue(actual.contains("目标版本冲突（禁止降级）"), actual);
                    assertTrue(actual.contains(FindSpringBoot35BuildRisks.VARIANT), actual);
                    return actual;
                })));
    }

    @Test
    void acceptsAllSelectedSourcesAndTheTarget() {
        rewriteRun(xml(project("<dependencies>" +
                       dependency("2.1.3.RELEASE", "") +
                       dependency("2.7.18", "") +
                       dependency("3.2.12", "") +
                       dependency("3.4.12", "") +
                       dependency("3.5.12", "") +
                       dependency("3.5.15", "") +
                       "</dependencies>"),
                source -> source.path("pom.xml")));
    }

    @Test
    void unrelatedMavenJavaBaselineIsNotClaimed() {
        rewriteRun(pomXml(project("""
                <properties>
                  <java.version>11</java.version>
                </properties>
                """)));
    }

    @Test
    void marksSharedPropertyButAcceptsAnExclusiveOwnerProperty() {
        rewriteRun(
                pomXml(project("""
                        <properties><boot.version>3.4.12</boot.version></properties>
                        <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                        """.formatted(bom("${boot.version}"))),
                        source -> source.path("exclusive/pom.xml")),
                pomXml(project("""
                        <properties><boot.version>3.4.12</boot.version></properties>
                        <name>${boot.version}</name>
                        <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                        """.formatted(bom("${boot.version}"))),
                        source -> source.path("shared/pom.xml").after(actual -> {
                            assertTrue(actual.contains(FindSpringBoot35BuildRisks.OWNER), actual);
                            return actual;
                        }))
        );
    }

    @Test
    void marksAnExclusiveFuturePropertyWithTheExactNoDowngradeMessage() {
        rewriteRun(pomXml(project("""
                <properties><boot.version>4.0.0</boot.version></properties>
                <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                """.formatted(bom("${boot.version}"))), source -> source.after(actual -> {
            assertTrue(actual.contains("目标版本冲突（禁止降级）"), actual);
            assertFalse(actual.contains(FindSpringBoot35BuildRisks.OWNER), actual);
            return actual;
        })));
    }

    @Test
    void marksJavaRemovedParentMalformedBomAndCloudAlignment() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-parent</artifactId>
                    <version>3.4.12</version>
                  </parent>
                  <groupId>x</groupId><artifactId>a</artifactId><version>1</version>
                  <properties><java.version>11</java.version></properties>
                  <dependencies>
                    %s
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-dependencies</artifactId>
                      <version>3.4.12</version>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.cloud</groupId>
                      <artifactId>spring-cloud-dependencies</artifactId>
                      <version>2023.0.5</version><type>pom</type><scope>import</scope>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(dependency("3.5.15", "")), source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringBoot35BuildRisks.JAVA), actual);
            assertTrue(actual.contains(FindSpringBoot35BuildRisks.REMOVED_PARENT), actual);
            assertTrue(actual.contains(FindSpringBoot35BuildRisks.BOM_SHAPE), actual);
            assertTrue(actual.contains(FindSpringBoot35BuildRisks.CLOUD), actual);
            return actual;
        })));
    }

    @Test
    void marksGradlePluginOwnerFutureVersionsAndVariants() {
        rewriteRun(buildGradle("""
                plugins {
                  id 'org.springframework.boot'
                  id 'org.springframework.boot' version '4.0.0'
                }
                dependencies {
                  implementation 'org.springframework.boot:spring-boot'
                  implementation 'org.springframework.boot:spring-boot:3.5.16'
                  implementation group: 'org.springframework.boot', name: 'spring-boot',
                      version: '3.4.12', classifier: 'sources'
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringBoot35BuildRisks.OWNER), actual);
            assertTrue(actual.contains("目标版本冲突（禁止降级）"), actual);
            assertTrue(actual.contains(FindSpringBoot35BuildRisks.VARIANT), actual);
            return actual;
        })));
    }

    @Test
    void kotlinAcceptsTargetOwnersAndMarksFutureLines() {
        rewriteRun(
                buildGradleKts("""
                        plugins { id("org.springframework.boot") version "3.5.15" }
                        dependencies {
                          implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.15"))
                          implementation("org.springframework.boot:spring-boot:3.5.15")
                        }
                        """, source -> source.path("aligned.gradle.kts")),
                buildGradleKts("""
                        plugins { id("org.springframework.boot") version "4.0.0" }
                        dependencies {
                          implementation("org.springframework.boot:spring-boot:4.1.0")
                        }
                        """, source -> source.path("future.gradle.kts").after(actual -> {
                    assertTrue(actual.contains("目标版本冲突（禁止降级）"), actual);
                    return actual;
                }))
        );
    }

    @Test
    void generatedBuildFilesAreNoopAndMarkersAreTwoCycleIdempotent() {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.springframework.boot:spring-boot:4.0.0' }",
                source -> source.path("target/generated/build.gradle")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("4.0.0"), source -> source.path("pom.xml").after(actual -> {
                    assertTrue(actual.contains("目标版本冲突（禁止降级）"), actual);
                    return actual;
                })));
    }

    private static Recipe recipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE);
    }

    private static String pom(String version) {
        return project("<dependencies>" + dependency(version, "") + "</dependencies>");
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId>" +
               "<artifactId>a</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dependency(String version, String metadata) {
        return "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") +
               metadata + "</dependency>";
    }

    private static String bom(String version) {
        return "<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId>" +
               "<version>" + version + "</version><type>pom</type><scope>import</scope></dependency>";
    }
}
