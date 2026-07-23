package com.huawei.clouds.openrewrite.springsecurityweb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class SpringSecurityWebBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringSecurityWeb6511BuildRisks());
    }

    @ParameterizedTest(name = "Maven no-downgrade {0}")
    @ValueSource(strings = {
            "7.0.0", "7.0.4", "6.5.12", "6.5.11.1", "6.5.11-sp1",
            "6.6.0-M1", "99.0.0", "6.5.11.999999999999999999999"
    })
    void marksEveryHigherMavenVersionWithTheExactConflict(String version) {
        rewriteRun(xml(UpgradeSpringSecurityWebDependencyTest.pom(version),
                source -> source.path(version + "/pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>" + version + "</version>"), printed);
                    assertEquals(1, occurrences(printed, SpringSecurityWebUpgradeSupport.TARGET_CONFLICT), printed);
                    assertFalse(printed.contains("<version>6.5.11</version>"), printed);
                })));
    }

    @ParameterizedTest(name = "approved/target has no primary marker {0}")
    @ValueSource(strings = {"5.1.5.RELEASE", "5.8.16", "6.4.13", "6.5.10", "6.5.11"})
    void approvedSourcesAndTargetHaveNoPrimaryRisk(String version) {
        rewriteRun(xml(UpgradeSpringSecurityWebDependencyTest.pom(version),
                source -> source.path(version + "/pom.xml")));
    }

    @Test
    void marksHigherVersionsThroughUniquePropertiesAndGradleWithoutChangingText() {
        rewriteRun(
                xml(UpgradeSpringSecurityWebDependencyTest.project(
                                "<properties><v>7.0.4</v></properties><dependencies>" +
                                UpgradeSpringSecurityWebDependencyTest.dep("${v}") + "</dependencies>"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("<v>7.0.4</v>"), after.printAll());
                            assertTrue(after.printAll().contains(SpringSecurityWebUpgradeSupport.TARGET_CONFLICT),
                                    after.printAll());
                        })),
                buildGradle(
                        "dependencies { implementation 'org.springframework.security:spring-security-web:7.0.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("spring-security-web:7.0.0"), after.printAll());
                            assertTrue(after.printAll().contains(SpringSecurityWebUpgradeSupport.TARGET_CONFLICT),
                                    after.printAll());
                        })),
                buildGradleKts(
                        "dependencies { implementation(\"org.springframework.security:spring-security-web:7.0.4\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(
                                        SpringSecurityWebUpgradeSupport.TARGET_CONFLICT), after.printAll()))));
    }

    @Test
    void marksOwnerOutsideAndVariantBoundaries() {
        rewriteRun(
                xml(UpgradeSpringSecurityWebDependencyTest.pom("6.4.12"),
                        source -> source.path("outside/pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(
                                        FindSpringSecurityWeb6511BuildRisks.OUTSIDE), after.printAll()))),
                xml(UpgradeSpringSecurityWebDependencyTest.project(
                                "<dependencies><dependency><groupId>org.springframework.security</groupId>" +
                                "<artifactId>spring-security-web</artifactId></dependency></dependencies>"),
                        source -> source.path("owner/pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(
                                        FindSpringSecurityWeb6511BuildRisks.OWNER), after.printAll()))),
                xml(UpgradeSpringSecurityWebDependencyTest.project("<dependencies>" +
                                UpgradeSpringSecurityWebDependencyTest.dep(
                                        "5.8.16", "<classifier>tests</classifier>") + "</dependencies>"),
                        source -> source.path("variant/pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(
                                        FindSpringSecurityWeb6511BuildRisks.VARIANT), after.printAll()))));
    }

    @Test
    void marksJavaParametersAndPublishedDependencyFamilyMisalignment() {
        String pom = UpgradeSpringSecurityWebDependencyTest.project("""
                <properties>
                  <maven.compiler.release>11</maven.compiler.release>
                  <maven.compiler.parameters>false</maven.compiler.parameters>
                </properties>
                <dependencies>
                  %s
                  <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-core</artifactId><version>6.4.10</version></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>5.3.39</version></dependency>
                  <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                  <dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>5.0.0</version></dependency>
                  <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-observation</artifactId><version>1.14.0</version></dependency>
                  <dependency><groupId>io.micrometer</groupId><artifactId>context-propagation</artifactId><version>1.1.3</version></dependency>
                  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>3.4.0</version></dependency>
                  <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-oauth2-authorization-server</artifactId><version>1.4.0</version></dependency>
                </dependencies>
                """.formatted(UpgradeSpringSecurityWebDependencyTest.dep("6.5.10")));
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringSecurityWeb6511BuildRisks.JAVA_BASELINE), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511BuildRisks.PARAMETERS), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511BuildRisks.SECURITY_ALIGNMENT), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511BuildRisks.SPRING_ALIGNMENT), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511BuildRisks.JAKARTA_ALIGNMENT), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511BuildRisks.MICROMETER_ALIGNMENT), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511BuildRisks.BOOT_OWNER), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511BuildRisks.AUTHORIZATION_SERVER), printed);
        })));
    }

    @Test
    void exactPublishedCompanionVersionsAreNotMarked() {
        String pom = UpgradeSpringSecurityWebDependencyTest.project("""
                <properties>
                  <maven.compiler.release>17</maven.compiler.release>
                  <maven.compiler.parameters>true</maven.compiler.parameters>
                </properties>
                <dependencies>
                  %s
                  <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-core</artifactId><version>6.5.11</version></dependency>
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>6.2.19</version></dependency>
                  <dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.0.0</version></dependency>
                  <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-observation</artifactId><version>1.15.12</version></dependency>
                  <dependency><groupId>io.micrometer</groupId><artifactId>context-propagation</artifactId><version>1.1.4</version></dependency>
                </dependencies>
                """.formatted(UpgradeSpringSecurityWebDependencyTest.dep("6.5.11")));
        rewriteRun(xml(pom, source -> source.path("pom.xml")));
    }

    @Test
    void marksGradleJava11DisabledParametersAndCatalogOwnership() {
        rewriteRun(
                buildGradle("""
                        java { sourceCompatibility = JavaVersion.VERSION_11 }
                        tasks.withType(JavaCompile).configureEach { options.compilerArgs -= '-parameters' }
                        dependencies { implementation 'org.springframework.security:spring-security-web:5.8.16' }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertTrue(after.printAll().contains(FindSpringSecurityWeb6511BuildRisks.JAVA_BASELINE),
                            after.printAll());
                    assertTrue(after.printAll().contains(FindSpringSecurityWeb6511BuildRisks.PARAMETERS),
                            after.printAll());
                })),
                buildGradleKts("dependencies { implementation(libs.spring.security.web) }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(
                                        FindSpringSecurityWeb6511BuildRisks.OWNER), after.printAll()))));
    }

    @Test
    void conflictMarkerIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeSpringSecurityWebDependencyTest.pom("7.0.4"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(),
                                        SpringSecurityWebUpgradeSupport.TARGET_CONFLICT)))));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
