package com.huawei.clouds.openrewrite.springwebflux;

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

class SpringWebFluxBuildRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springwebflux.FindSpringWebFlux6BuildMigrationRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe());
    }

    @Test
    void separatesOwnerOutsideVariantAndNoDowngradeConflict() {
        rewriteRun(pomXml(project("""
                <dependencies>
                  %s
                  %s
                  %s
                  %s
                  %s
                </dependencies>
                """.formatted(
                target(null, ""),
                target("5.3.25", ""),
                target("6.2.20", ""),
                target("7.0.0", ""),
                target("6.1.14", "<classifier>sources</classifier>"))),
                source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxBuildRisks.OWNER));
                    assertTrue(actual.contains(FindSpringWebFluxBuildRisks.OUTSIDE));
                    assertTrue(actual.contains(FindSpringWebFluxBuildRisks.TARGET_CONFLICT));
                    assertTrue(actual.contains(FindSpringWebFluxBuildRisks.VARIANT));
                    return actual;
                })));
    }

    @ParameterizedTest(name = "marks no-downgrade target conflict {0}")
    @ValueSource(strings = {"6.2.20", "6.3.0", "6.9.9", "7.0.0", "7.1.4", "8.0.0"})
    void marksEveryHigherFixedVersionAsTargetConflict(String version) {
        rewriteRun(xml(
                project("<dependencies>" + target(version, "") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxBuildRisks.TARGET_CONFLICT));
                    assertFalse(actual.contains(FindSpringWebFluxBuildRisks.OUTSIDE));
                    return actual;
                })));
    }

    @Test
    void exclusivePropertyIsNotOwnerButSharedPropertyIs() {
        rewriteRun(
                pomXml(project("""
                        <properties><webflux.version>6.1.14</webflux.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(target("${webflux.version}", ""))),
                        source -> source.path("exclusive/pom.xml")),
                pomXml(project("""
                        <properties><spring.version>6.1.14</spring.version></properties>
                        <dependencies>
                          %s
                          <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId>
                            <version>${spring.version}</version>
                          </dependency>
                        </dependencies>
                        """.formatted(target("${spring.version}", ""))),
                        source -> source.path("shared/pom.xml").after(actual -> {
                            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.OWNER));
                            return actual;
                        }))
        );
    }

    @Test
    void marksJavaParameterSpringAndBootOwnership() {
        rewriteRun(pomXml(project("""
                <properties>
                  <maven.compiler.release>11</maven.compiler.release>
                  <maven.compiler.parameters>false</maven.compiler.parameters>
                </properties>
                <dependencyManagement><dependencies>
                  <dependency><groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-dependencies</artifactId><version>3.5.15</version><type>pom</type><scope>import</scope>
                  </dependency>
                </dependencies></dependencyManagement>
                <dependencies>
                  %s
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>6.1.14</version></dependency>
                </dependencies>
                <build><plugins><plugin>
                  <groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>
                  <configuration><source>11</source><parameters>false</parameters></configuration>
                </plugin></plugins></build>
                """.formatted(target("6.2.19", ""))), source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.JAVA));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.PARAMETERS));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.ALIGNMENT));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.BOOT_OWNER));
            return actual;
        })));
    }

    @Test
    void marksIncompatibleBootParentAndUnresolvedFrameworkOwner() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>2.7.18</version></parent>
                  <groupId>x</groupId><artifactId>a</artifactId><version>1</version>
                  <dependencies>
                    %s
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId></dependency>
                  </dependencies>
                </project>
                """.formatted(target("6.1.14", "")), source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.BOOT));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.ALIGNMENT_OWNER));
            return actual;
        })));
    }

    @Test
    void neverSuggestsDowngradingNewerSpringFamilyOrBootOwners() {
        rewriteRun(xml(
                project("""
                        <dependencies>
                          %s
                          <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>7.0.0</version></dependency>
                          <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webflux</artifactId><version>4.0.0</version></dependency>
                        </dependencies>
                        """.formatted(target("6.2.19", ""))),
                source -> source.path("pom.xml").after(actual -> {
                    assertTrue(actual.contains(FindSpringWebFluxBuildRisks.TARGET_CONFLICT));
                    assertFalse(actual.contains(FindSpringWebFluxBuildRisks.ALIGNMENT));
                    assertFalse(actual.contains(FindSpringWebFluxBuildRisks.BOOT));
                    return actual;
                })));
    }

    @Test
    void marksReactiveStackBaselinesAndWebJarsCore() {
        rewriteRun(pomXml(project("""
                <dependencies>
                  %s
                  <dependency><groupId>io.projectreactor</groupId><artifactId>reactor-core</artifactId><version>3.5.0</version></dependency>
                  <dependency><groupId>io.projectreactor.netty</groupId><artifactId>reactor-netty-http</artifactId><version>1.1.20</version></dependency>
                  <dependency><groupId>io.netty</groupId><artifactId>netty-handler</artifactId><version>4.1.100.Final</version></dependency>
                  <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>2.13.5</version></dependency>
                  <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-reflect</artifactId><version>1.8.22</version></dependency>
                  <dependency><groupId>org.jetbrains.kotlinx</groupId><artifactId>kotlinx-coroutines-reactor</artifactId><version>1.6.4</version></dependency>
                  <dependency><groupId>javax.validation</groupId><artifactId>validation-api</artifactId><version>2.0.1.Final</version></dependency>
                  <dependency><groupId>org.webjars</groupId><artifactId>webjars-locator-core</artifactId><version>0.59</version></dependency>
                </dependencies>
                """.formatted(target("6.2.19", ""))), source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.REACTOR));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.NETTY));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.JACKSON));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.KOTLIN));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.VALIDATION));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.WEBJARS));
            return actual;
        })));
    }

    @Test
    void acceptsDocumentedTargetBaselinesAndNewerCompatibleLines() {
        rewriteRun(pomXml(project("""
                <properties><maven.compiler.release>17</maven.compiler.release></properties>
                <dependencies>
                  %s
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>6.2.19</version></dependency>
                  <dependency><groupId>io.projectreactor</groupId><artifactId>reactor-core</artifactId><version>3.7.18</version></dependency>
                  <dependency><groupId>io.projectreactor.netty</groupId><artifactId>reactor-netty-http</artifactId><version>1.2.18</version></dependency>
                  <dependency><groupId>io.netty</groupId><artifactId>netty-handler</artifactId><version>4.1.136.Final</version></dependency>
                  <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>2.18.5</version></dependency>
                  <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-reflect</artifactId><version>1.9.25</version></dependency>
                  <dependency><groupId>org.jetbrains.kotlinx</groupId><artifactId>kotlinx-coroutines-reactor</artifactId><version>1.8.1</version></dependency>
                  <dependency><groupId>jakarta.validation</groupId><artifactId>jakarta.validation-api</artifactId><version>3.0.2</version></dependency>
                </dependencies>
                """.formatted(target("6.2.19", "")))));
    }

    @Test
    void profileVisibilityDoesNotLeakIntoUnrelatedProfile() {
        rewriteRun(pomXml(project("""
                <profiles>
                  <profile><id>webflux</id><dependencies>%s</dependencies></profile>
                  <profile><id>business</id><dependencies>
                    <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId><version>5.3.26</version></dependency>
                  </dependencies></profile>
                </profiles>
                """.formatted(target("6.2.19", "")))));
    }

    @Test
    void mavenPluginScopeRejectsArbitraryNestedLookalikes() {
        rewriteRun(xml("""
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId><version>1</version>
                  <dependencies>%s</dependencies>
                  <company><build><plugins><plugin>
                    <groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>
                    <configuration><release>8</release></configuration>
                  </plugin></plugins></build></company>
                </project>
                """.formatted(target("6.2.19", "")), source -> source.path("pom.xml")));
    }

    @Test
    void gradleMarksExactDynamicOwnerFutureConflictAndNotLookalikeTemplate() {
        rewriteRun(buildGradle("""
                def v = '6.1.14'
                dependencies {
                  implementation "org.springframework:spring-webflux:${v}"
                  implementation "prefix-org.springframework:spring-webflux:${v}"
                  implementation 'org.springframework:spring-webflux:6.2.20'
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.OWNER));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.TARGET_CONFLICT));
            String lookalike = actual.substring(actual.indexOf("prefix-org.springframework"));
            assertFalse(lookalike.substring(0, lookalike.indexOf('\n'))
                    .contains(FindSpringWebFluxBuildRisks.OWNER));
            return actual;
        })));
    }

    @Test
    void gradleMarksJavaAndCompanionsOnlyWithRootPrimary() {
        rewriteRun(buildGradle("""
                sourceCompatibility = JavaVersion.VERSION_11
                java { toolchain { languageVersion = JavaLanguageVersion.of(11) } }
                dependencies {
                  implementation 'org.springframework:spring-webflux:6.2.19'
                  implementation 'io.projectreactor:reactor-core:3.5.0'
                  implementation 'io.netty:netty-handler:4.1.100.Final'
                  implementation 'com.fasterxml.jackson.core:jackson-databind:2.13.5'
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.JAVA));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.REACTOR));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.NETTY));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.JACKSON));
            return actual;
        })));
    }

    @Test
    void nestedGradleProjectsAndBuildscriptRemainOutOfScope() {
        rewriteRun(buildGradle("""
                buildscript { dependencies { implementation 'org.springframework:spring-webflux:7.0.0' } }
                project(':child') {
                  sourceCompatibility = JavaVersion.VERSION_11
                  dependencies {
                    implementation 'org.springframework:spring-webflux:7.0.0'
                    implementation 'io.projectreactor:reactor-core:3.5.0'
                  }
                }
                """));
    }

    @Test
    void kotlinGradleMarksCatalogOwnerAndNoDowngradeConflict() {
        rewriteRun(buildGradleKts("""
                dependencies {
                    implementation(libs.spring.webflux)
                    implementation("org.springframework:spring-webflux:7.0.0")
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.OWNER));
            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.TARGET_CONFLICT));
            return actual;
        })));
    }

    @Test
    void skipsGeneratedBuildRiskFilesAndMarkersAreIdempotent() {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.springframework:spring-webflux:7.0.0' }",
                source -> source.path("build/generated/build.gradle")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(project("<dependencies>" + target("7.0.0", "") + "</dependencies>"),
                        source -> source.after(actual -> {
                            assertTrue(actual.contains(FindSpringWebFluxBuildRisks.TARGET_CONFLICT));
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
        return "<dependency><groupId>org.springframework</groupId><artifactId>spring-webflux</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") +
               metadata + "</dependency>";
    }
}
