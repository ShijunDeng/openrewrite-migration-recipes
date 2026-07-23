package com.huawei.clouds.openrewrite.springbootactuator;

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

class ActuatorBuildRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springbootactuator.FindSpringBootActuator3_5Risks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe());
    }

    @ParameterizedTest(name = "No-downgrade conflict {0}")
    @ValueSource(strings = {
            "3.5.16", "3.5.20", "3.5.16.1", "3.6.0", "3.9.1", "4.0.0",
            "4.1.2", "5.0.0", "999999999999999999999999.0.0"
    })
    void marksEveryHigherVersionAsNoDowngradeConflict(String version) {
        rewriteRun(xml(project("<dependencies>" + target(version, "") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> {
                    assertTrue(actual.contains(FindActuatorBuildRisks.TARGET_CONFLICT));
                    assertTrue(actual.contains("目标版本冲突（禁止降级）"));
                    assertFalse(actual.contains(FindActuatorBuildRisks.OUTSIDE));
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
                target(null, ""),
                target("3.4.1", ""),
                target("4.0.0", ""),
                target("3.4.12", "<classifier>sources</classifier>"))),
                source -> source.after(actual -> {
                    assertTrue(actual.contains(FindActuatorBuildRisks.OWNER));
                    assertTrue(actual.contains(FindActuatorBuildRisks.OUTSIDE));
                    assertTrue(actual.contains(FindActuatorBuildRisks.TARGET_CONFLICT));
                    assertTrue(actual.contains(FindActuatorBuildRisks.VARIANT));
                    return actual;
                })));
    }

    @Test
    void acceptsSelectedFixedSourcesButMarksStandaloneTargetForPlatformAlignment() {
        rewriteRun(
                pomXml(project("<dependencies>" +
                        target("2.7.18", "") + target("3.4.12", "") +
                        "</dependencies>"), source -> source.path("selected/pom.xml")),
                pomXml(project("<dependencies>" + target("3.5.15", "") + "</dependencies>"),
                        source -> source.path("already-aligned/pom.xml").after(actual -> {
                            assertTrue(actual.contains(FindActuatorBuildRisks.BOM_ALIGNMENT));
                            return actual;
                        }))
        );
    }

    @Test
    void acceptsVersionlessStarterWithLocalTargetParentOrBom() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion>
                          <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.15</version></parent>
                          <artifactId>a</artifactId><dependencies>%s</dependencies>
                        </project>
                        """.formatted(target(null, "")), source -> source.path("parent/pom.xml")),
                pomXml(project("<dependencyManagement><dependencies>" + bom("3.5.15") +
                               "</dependencies></dependencyManagement><dependencies>" + target(null, "") +
                               "</dependencies>"), source -> source.path("bom/pom.xml"))
        );
    }

    @Test
    void marksSharedPropertyButAcceptsExclusiveLocalProperty() {
        rewriteRun(
                pomXml(project("""
                        <properties><actuator.version>3.4.12</actuator.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(target("${actuator.version}", ""))),
                        source -> source.path("exclusive/pom.xml")),
                pomXml(project("""
                        <properties><boot.version>3.4.12</boot.version></properties>
                        <name>${boot.version}</name>
                        <dependencies>
                          %s
                        </dependencies>
                        """.formatted(target("${boot.version}", ""))),
                        source -> source.path("shared/pom.xml").after(actual -> {
                            assertTrue(actual.contains(FindActuatorBuildRisks.OWNER));
                            return actual;
                        }))
        );
    }

    @Test
    void marksJavaJakartaSecurityMicrometerAndMigratorBoundaries() {
        rewriteRun(pomXml(project("""
                <properties><java.version>11</java.version></properties>
                <dependencies>
                  %s
                  <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                  <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-web</artifactId><version>5.8.16</version></dependency>
                  <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-core</artifactId><version>1.10.13</version></dependency>
                  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-properties-migrator</artifactId><version>3.4.12</version></dependency>
                </dependencies>
                """.formatted(target("3.5.15", ""))), source -> source.after(actual -> {
            assertTrue(actual.contains(FindActuatorBuildRisks.JAVA));
            assertTrue(actual.contains(FindActuatorBuildRisks.JAKARTA));
            assertTrue(actual.contains(FindActuatorBuildRisks.SECURITY));
            assertTrue(actual.contains(FindActuatorBuildRisks.MICROMETER));
            assertTrue(actual.contains(FindActuatorBuildRisks.MIGRATOR));
            return actual;
        })));
    }

    @Test
    void marksMavenCompilerPluginBaselineButNotUnrelatedReleaseTag() {
        rewriteRun(pomXml(project("""
                <build><plugins><plugin>
                  <groupId>org.apache.maven.plugins</groupId>
                  <artifactId>maven-compiler-plugin</artifactId>
                  <configuration><release>11</release></configuration>
                </plugin></plugins></build>
                <reporting><release>8</release></reporting>
                """), source -> source.after(actual -> {
            assertTrue(actual.contains(FindActuatorBuildRisks.JAVA));
            String reporting = actual.substring(actual.indexOf("<reporting>"));
            assertFalse(reporting.contains(FindActuatorBuildRisks.JAVA));
            return actual;
        })));
    }

    @Test
    void marksRemovedInternalBootParent() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-parent</artifactId><version>3.4.12</version></parent>
                  <artifactId>a</artifactId>
                </project>
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindActuatorBuildRisks.INTERNAL_PARENT));
            return actual;
        })));
    }

    @Test
    void gradleMarksOwnerFutureConflictCompanionsAndJava() {
        rewriteRun(buildGradle("""
                sourceCompatibility = 11
                dependencies {
                  implementation 'org.springframework.boot:spring-boot-starter-actuator'
                  implementation 'org.springframework.boot:spring-boot-starter-actuator:4.0.0'
                  implementation 'javax.validation:validation-api:2.0.1.Final'
                  implementation 'io.micrometer:micrometer-core:1.10.13'
                  implementation 'org.springframework.security:spring-security-web:5.8.16'
                  runtimeOnly 'org.springframework.boot:spring-boot-properties-migrator:3.4.12'
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindActuatorBuildRisks.OWNER));
            assertTrue(actual.contains(FindActuatorBuildRisks.TARGET_CONFLICT));
            assertTrue(actual.contains(FindActuatorBuildRisks.JAKARTA));
            assertTrue(actual.contains(FindActuatorBuildRisks.MICROMETER));
            assertTrue(actual.contains(FindActuatorBuildRisks.SECURITY));
            assertTrue(actual.contains(FindActuatorBuildRisks.MIGRATOR));
            assertTrue(actual.contains(FindActuatorBuildRisks.JAVA));
            return actual;
        })));
    }

    @Test
    void kotlinGradleMarksFutureButAcceptsTargetPlatformOwner() {
        rewriteRun(
                buildGradleKts("""
                        dependencies {
                            implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.15"))
                            implementation("org.springframework.boot:spring-boot-starter-actuator")
                        }
                        """, source -> source.path("managed.gradle.kts")),
                buildGradleKts("""
                        dependencies {
                            implementation("org.springframework.boot:spring-boot-starter-actuator:4.0.0")
                        }
                        """, source -> source.path("future.gradle.kts").after(actual -> {
                    assertTrue(actual.contains(FindActuatorBuildRisks.TARGET_CONFLICT));
                    return actual;
                }))
        );
    }

    @Test
    void skipsGeneratedBuildFilesAndMarkersAreIdempotent() {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.springframework.boot:spring-boot-starter-actuator:4.0.0' }",
                source -> source.path("target/generated/build.gradle")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(project("<dependencies>" + target("4.0.0", "") + "</dependencies>"),
                        source -> source.after(actual -> {
                            assertTrue(actual.contains(FindActuatorBuildRisks.TARGET_CONFLICT));
                            return actual;
                        })));
    }

    private static Recipe recipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE);
    }

    private static String project(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId>" +
               "<artifactId>a</artifactId><version>1</version>" + body + "</project>";
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
