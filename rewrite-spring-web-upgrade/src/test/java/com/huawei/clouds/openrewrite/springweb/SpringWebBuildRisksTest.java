package com.huawei.clouds.openrewrite.springweb;

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

class SpringWebBuildRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springweb.FindSpringWeb6BuildMigrationRisks";

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
                target("5.3.27", "<classifier>sources</classifier>"))),
                source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebBuildRisks.OWNER));
                    assertTrue(actual.contains(FindSpringWebBuildRisks.OUTSIDE));
                    assertTrue(actual.contains(FindSpringWebBuildRisks.TARGET_CONFLICT));
                    assertTrue(actual.contains(FindSpringWebBuildRisks.VARIANT));
                    return actual;
                })));
    }

    @ParameterizedTest(name = "marks no-downgrade target conflict {0}")
    @ValueSource(strings = {
            "6.2.19.1", "6.2.19.0.1", "6.2.20", "6.3.0", "6.9.9", "7.0.0", "7.1.4",
            "8.0.0", "999999999999999999.0.0"
    })
    void marksEveryHigherFixedVersionAsTargetConflict(String version) {
        rewriteRun(xml(
                project("<dependencies>" + target(version, "") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> {
                    assertTrue(actual.contains(FindSpringWebBuildRisks.TARGET_CONFLICT));
                    assertFalse(actual.contains(FindSpringWebBuildRisks.OUTSIDE));
                    return actual;
                })));
    }

    @Test
    void exclusivePropertyIsNotOwnerButSharedPropertyIs() {
        rewriteRun(
                pomXml(project("""
                        <properties><web.version>5.3.27</web.version></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(target("${web.version}", ""))),
                        source -> source.path("exclusive/pom.xml")),
                pomXml(project("""
                        <properties><spring.version>5.3.27</spring.version></properties>
                        <dependencies>
                          %s
                          <dependency><groupId>org.springframework</groupId><artifactId>spring-core</artifactId>
                            <version>${spring.version}</version>
                          </dependency>
                        </dependencies>
                        """.formatted(target("${spring.version}", ""))),
                        source -> source.path("shared/pom.xml").after(actual -> {
                            assertTrue(actual.contains(FindSpringWebBuildRisks.OWNER));
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
                    <artifactId>spring-boot-dependencies</artifactId><version>3.5.8</version>
                    <type>pom</type><scope>import</scope>
                  </dependency>
                </dependencies></dependencyManagement>
                <dependencies>
                  %s
                  <dependency><groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId><version>6.1.14</version></dependency>
                </dependencies>
                <build><plugins><plugin>
                  <groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId>
                  <configuration><source>11</source><parameters>false</parameters></configuration>
                </plugin></plugins></build>
                """.formatted(target("6.2.19", ""))), source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebBuildRisks.JAVA));
            assertTrue(actual.contains(FindSpringWebBuildRisks.PARAMETERS));
            assertTrue(actual.contains(FindSpringWebBuildRisks.ALIGNMENT));
            assertTrue(actual.contains(FindSpringWebBuildRisks.BOOT_OWNER));
            return actual;
        })));
    }

    @Test
    void marksIncompatibleBootParentAndUnresolvedFrameworkOwner() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId><version>2.7.18</version></parent>
                  <groupId>x</groupId><artifactId>a</artifactId><version>1</version>
                  <dependencies>
                    %s
                    <dependency><groupId>org.springframework</groupId>
                      <artifactId>spring-context</artifactId></dependency>
                  </dependencies>
                </project>
                """.formatted(target("5.3.27", "")), source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebBuildRisks.BOOT));
            assertTrue(actual.contains(FindSpringWebBuildRisks.ALIGNMENT_OWNER));
            return actual;
        })));
    }

    @Test
    void neverSuggestsDowngradingNewerSpringFamilyOrBootOwners() {
        rewriteRun(xml(
                project("""
                        <dependencies>
                          %s
                          <dependency><groupId>org.springframework</groupId>
                            <artifactId>spring-core</artifactId><version>7.0.0</version></dependency>
                          <dependency><groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId><version>4.0.0</version></dependency>
                        </dependencies>
                        """.formatted(target("6.2.19", ""))),
                source -> source.path("pom.xml").after(actual -> {
                    assertTrue(actual.contains(FindSpringWebBuildRisks.TARGET_CONFLICT));
                    assertFalse(actual.contains(FindSpringWebBuildRisks.ALIGNMENT));
                    assertFalse(actual.contains(FindSpringWebBuildRisks.BOOT));
                    return actual;
                })));
    }

    @Test
    void marksJakartaHttpClientJacksonReactiveJettyAndObservationAlignment() {
        rewriteRun(pomXml(project("""
                <dependencies>
                  %s
                  <dependency><groupId>javax.servlet</groupId>
                    <artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                  <dependency><groupId>jakarta.validation</groupId>
                    <artifactId>jakarta.validation-api</artifactId><version>2.0.2</version></dependency>
                  <dependency><groupId>org.apache.httpcomponents</groupId>
                    <artifactId>httpclient</artifactId><version>4.5.14</version></dependency>
                  <dependency><groupId>org.apache.httpcomponents.client5</groupId>
                    <artifactId>httpclient5</artifactId><version>5.2.1</version></dependency>
                  <dependency><groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-databind</artifactId><version>2.14.3</version></dependency>
                  <dependency><groupId>io.projectreactor.netty</groupId>
                    <artifactId>reactor-netty-http</artifactId><version>1.1.20</version></dependency>
                  <dependency><groupId>io.netty</groupId>
                    <artifactId>netty-handler</artifactId><version>4.1.100.Final</version></dependency>
                  <dependency><groupId>org.eclipse.jetty</groupId>
                    <artifactId>jetty-reactive-httpclient</artifactId><version>3.0.12</version></dependency>
                  <dependency><groupId>io.micrometer</groupId>
                    <artifactId>micrometer-observation</artifactId><version>1.12.0</version></dependency>
                </dependencies>
                """.formatted(target("6.2.19", ""))), source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebBuildRisks.JAKARTA));
            assertTrue(actual.contains(FindSpringWebBuildRisks.HTTP_CLIENT));
            assertTrue(actual.contains(FindSpringWebBuildRisks.JACKSON));
            assertTrue(actual.contains(FindSpringWebBuildRisks.REACTOR_NETTY));
            assertTrue(actual.contains(FindSpringWebBuildRisks.JETTY));
            assertTrue(actual.contains(FindSpringWebBuildRisks.MICROMETER));
            return actual;
        })));
    }

    @Test
    void acceptsDocumentedMinimumsAndTargetSpringLine() {
        rewriteRun(pomXml(project("""
                <properties><maven.compiler.release>17</maven.compiler.release></properties>
                <dependencies>
                  %s
                  <dependency><groupId>org.springframework</groupId>
                    <artifactId>spring-core</artifactId><version>6.2.19</version></dependency>
                  <dependency><groupId>jakarta.servlet</groupId>
                    <artifactId>jakarta.servlet-api</artifactId><version>6.0.0</version></dependency>
                  <dependency><groupId>jakarta.validation</groupId>
                    <artifactId>jakarta.validation-api</artifactId><version>3.0.2</version></dependency>
                  <dependency><groupId>jakarta.xml.bind</groupId>
                    <artifactId>jakarta.xml.bind-api</artifactId><version>3.0.1</version></dependency>
                  <dependency><groupId>jakarta.json</groupId>
                    <artifactId>jakarta.json-api</artifactId><version>2.0.1</version></dependency>
                  <dependency><groupId>jakarta.json.bind</groupId>
                    <artifactId>jakarta.json.bind-api</artifactId><version>2.0.0</version></dependency>
                  <dependency><groupId>jakarta.activation</groupId>
                    <artifactId>jakarta.activation-api</artifactId><version>2.0.1</version></dependency>
                  <dependency><groupId>javax.inject</groupId>
                    <artifactId>javax.inject</artifactId><version>1</version></dependency>
                  <dependency><groupId>javax.cache</groupId>
                    <artifactId>cache-api</artifactId><version>1.1.1</version></dependency>
                  <dependency><groupId>org.apache.httpcomponents.client5</groupId>
                    <artifactId>httpclient5</artifactId><version>5.5</version></dependency>
                  <dependency><groupId>com.fasterxml.jackson.core</groupId>
                    <artifactId>jackson-databind</artifactId><version>2.18.5</version></dependency>
                  <dependency><groupId>io.micrometer</groupId>
                    <artifactId>micrometer-observation</artifactId><version>1.15.12</version></dependency>
                </dependencies>
                """.formatted(target("6.2.19", "")))));
    }

    @Test
    void profileVisibilityDoesNotLeakIntoUnrelatedProfile() {
        rewriteRun(pomXml(project("""
                <profiles>
                  <profile><id>web</id><dependencies>%s</dependencies></profile>
                  <profile><id>business</id><dependencies>
                    <dependency><groupId>org.springframework</groupId>
                      <artifactId>spring-core</artifactId><version>5.3.27</version></dependency>
                  </dependencies></profile>
                </profiles>
                """.formatted(target("6.2.19", "")))));
    }

    @Test
    void mavenPluginScopeRejectsArbitraryNestedLookalikes() {
        rewriteRun(xml("""
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>x</groupId>
                  <artifactId>a</artifactId><version>1</version>
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
                def v = '5.3.27'
                dependencies {
                  implementation "org.springframework:spring-web:${v}"
                  implementation "prefix-org.springframework:spring-web:${v}"
                  implementation 'org.springframework:spring-web:6.2.20'
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebBuildRisks.OWNER));
            assertTrue(actual.contains(FindSpringWebBuildRisks.TARGET_CONFLICT));
            String lookalike = actual.substring(actual.indexOf("prefix-org.springframework"));
            assertFalse(lookalike.substring(0, lookalike.indexOf('\n'))
                    .contains(FindSpringWebBuildRisks.OWNER));
            return actual;
        })));
    }

    @Test
    void gradleMarksJavaAndCompanionsOnlyWithRootPrimary() {
        rewriteRun(buildGradle("""
                sourceCompatibility = JavaVersion.VERSION_11
                java { toolchain { languageVersion = JavaLanguageVersion.of(11) } }
                dependencies {
                  implementation 'org.springframework:spring-web:6.2.19'
                  implementation 'org.apache.httpcomponents:httpclient:4.5.14'
                  implementation 'com.fasterxml.jackson.core:jackson-databind:2.14.3'
                  implementation 'io.netty:netty-handler:4.1.100.Final'
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebBuildRisks.JAVA));
            assertTrue(actual.contains(FindSpringWebBuildRisks.HTTP_CLIENT));
            assertTrue(actual.contains(FindSpringWebBuildRisks.JACKSON));
            assertTrue(actual.contains(FindSpringWebBuildRisks.REACTOR_NETTY));
            return actual;
        })));
    }

    @Test
    void nestedGradleProjectsAndBuildscriptRemainOutOfScope() {
        rewriteRun(buildGradle("""
                buildscript { dependencies { implementation 'org.springframework:spring-web:7.0.0' } }
                project(':child') {
                  sourceCompatibility = JavaVersion.VERSION_11
                  dependencies {
                    implementation 'org.springframework:spring-web:7.0.0'
                    implementation 'org.apache.httpcomponents:httpclient:4.5.14'
                  }
                }
                """));
    }

    @Test
    void rootPrimaryDoesNotLeakJavaMarkersIntoNestedGradleProject() {
        rewriteRun(buildGradle("""
                dependencies { implementation 'org.springframework:spring-web:6.2.19' }
                project(':child') {
                  sourceCompatibility = JavaVersion.VERSION_11
                  java { toolchain { languageVersion = JavaLanguageVersion.of(11) } }
                }
                """));
    }

    @Test
    void kotlinGradleMarksCatalogOwnerAndNoDowngradeConflict() {
        rewriteRun(buildGradleKts("""
                dependencies {
                    implementation(libs.spring.web)
                    implementation("org.springframework:spring-web:7.0.0")
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebBuildRisks.OWNER));
            assertTrue(actual.contains(FindSpringWebBuildRisks.TARGET_CONFLICT));
            return actual;
        })));
    }

    @Test
    void skipsGeneratedBuildRiskFilesAndMarkersAreIdempotent() {
        rewriteRun(buildGradle(
                "dependencies { implementation 'org.springframework:spring-web:7.0.0' }",
                source -> source.path("build/generated/build.gradle")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(project("<dependencies>" + target("7.0.0", "") + "</dependencies>"),
                        source -> source.after(actual -> {
                            assertTrue(actual.contains(FindSpringWebBuildRisks.TARGET_CONFLICT));
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
        return "<dependency><groupId>org.springframework</groupId><artifactId>spring-web</artifactId>" +
               (version == null ? "" : "<version>" + version + "</version>") +
               metadata + "</dependency>";
    }
}
