package com.huawei.clouds.openrewrite.springsecuritycore;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringSecurityCoreBuildRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springsecuritycore.FindSpringSecurityCore6_5_11BuildRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(SpringSecurityCoreTestSupport.environment().activateRecipes(RECIPE));
    }

    @Test
    void marksMissingDynamicSharedAndUnsupportedFixedOwners() {
        rewriteRun(
                xml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>org.springframework.security</groupId>
                              <artifactId>spring-security-core</artifactId>
                            </dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("missing/pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(
                                        FindSpringSecurityCore6511BuildRisks.OWNER), after.printAll()))),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                          <properties><security.version>5.8.5</security.version></properties>
                          <dependencies>
                            <dependency>
                              <groupId>org.springframework.security</groupId>
                              <artifactId>spring-security-core</artifactId>
                              <version>${security.version}</version>
                            </dependency>
                            <dependency>
                              <groupId>org.springframework.security</groupId>
                              <artifactId>spring-security-test</artifactId>
                              <version>${security.version}</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("shared/pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(
                                        FindSpringSecurityCore6511BuildRisks.OWNER), after.printAll()))),
                pomXml(UpgradeSpringSecurityCoreDependencyTest.pom("6.5.10"),
                        source -> source.path("outside/pom.xml")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertTrue(after.printAll().contains(
                                                FindSpringSecurityCore6511BuildRisks.OUTSIDE), after.printAll()))));
    }

    @Test
    void marksHighVersionAndVariantAtTheConcreteDeclaration() {
        rewriteRun(
                pomXml(UpgradeSpringSecurityCoreDependencyTest.pom("7.0.4"),
                        source -> source.path("high/pom.xml")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertTrue(after.printAll().contains(
                                                SpringSecurityCoreSupport.TARGET_CONFLICT), after.printAll()))),
                buildGradle("""
                        dependencies {
                            implementation 'org.springframework.security:spring-security-core:5.8.5@zip'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(
                                FindSpringSecurityCore6511BuildRisks.VARIANT), after.printAll()))));
    }

    @Test
    void marksHighVersionOnItsExclusivePropertyOwnerOnly() {
        rewriteRun(xml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <properties><security.core.version>7.1.0</security.core.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>${security.core.version}</version>
                    </dependency>
                  </dependencies>
                </project>
        """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            int marker = printed.indexOf(SpringSecurityCoreSupport.TARGET_CONFLICT);
            int property = printed.indexOf("<security.core.version>");
            int reference = printed.indexOf("<version>${security.core.version}</version>");
            assertTrue(marker >= 0 && marker < property, printed);
            assertTrue(property < reference, printed);
            assertFalse(printed.substring(reference).contains(
                    SpringSecurityCoreSupport.TARGET_CONFLICT), printed);
        })));
    }

    @Test
    void sharedFuturePropertyStillGetsTheExactNoDowngradeMarker() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <properties><security.version>7.0.4</security.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>${security.version}</version>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-test</artifactId>
                      <version>${security.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml").after(actual -> actual)
                .afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(
                            SpringSecurityCoreSupport.TARGET_CONFLICT), printed);
                    assertFalse(printed.contains(
                            FindSpringSecurityCore6511BuildRisks.OWNER), printed);
                })));
    }

    @Test
    void marksDynamicGradleOwnersInGroovyAndKotlin() {
        rewriteRun(
                buildGradle("""
                        def securityVersion = '5.8.5'
                        dependencies {
                            implementation "org.springframework.security:spring-security-core:$securityVersion"
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(
                                FindSpringSecurityCore6511BuildRisks.OWNER), after.printAll()))),
                buildGradleKts("""
                        val securityVersion = "5.8.5"
                        dependencies {
                            implementation("org.springframework.security:spring-security-core:$securityVersion")
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(
                                FindSpringSecurityCore6511BuildRisks.OWNER), after.printAll()))));
    }

    @Test
    void marksJavaParametersAndPublishedDependencyAlignment() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <properties>
                    <maven.compiler.release>11</maven.compiler.release>
                    <maven.compiler.parameters>false</maven.compiler.parameters>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>6.5.11</version>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-crypto</artifactId>
                      <version>6.4.10</version>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework</groupId>
                      <artifactId>spring-context</artifactId>
                      <version>6.1.20</version>
                    </dependency>
                    <dependency>
                      <groupId>io.micrometer</groupId>
                      <artifactId>micrometer-observation</artifactId>
                      <version>1.14.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringSecurityCore6511BuildRisks.JAVA_BASELINE), printed);
            assertTrue(printed.contains(FindSpringSecurityCore6511BuildRisks.PARAMETERS), printed);
            assertTrue(printed.contains(FindSpringSecurityCore6511BuildRisks.SECURITY_ALIGNMENT), printed);
            assertTrue(printed.contains(FindSpringSecurityCore6511BuildRisks.SPRING_ALIGNMENT), printed);
            assertTrue(printed.contains(FindSpringSecurityCore6511BuildRisks.MICROMETER_ALIGNMENT), printed);
        })));
    }

    @Test
    void newerDependencyFamiliesAreConflictsNeverAlignmentDowngradeSuggestions() {
        rewriteRun(xml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>6.5.11</version>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-crypto</artifactId>
                      <version>7.0.4</version>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework</groupId>
                      <artifactId>spring-context</artifactId>
                      <version>7.0.5</version>
                    </dependency>
                    <dependency>
                      <groupId>io.micrometer</groupId>
                      <artifactId>micrometer-observation</artifactId>
                      <version>2.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml").after(actual -> actual)
                .afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(3, occurrences(printed,
                            SpringSecurityCoreSupport.TARGET_CONFLICT), printed);
                    assertFalse(printed.contains(
                            FindSpringSecurityCore6511BuildRisks.SECURITY_ALIGNMENT), printed);
                    assertFalse(printed.contains(
                            FindSpringSecurityCore6511BuildRisks.SPRING_ALIGNMENT), printed);
                    assertFalse(printed.contains(
                            FindSpringSecurityCore6511BuildRisks.MICROMETER_ALIGNMENT), printed);
                })));
    }

    @Test
    void arbitrarilyLargeJavaBaselineCannotOverflow() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <properties>
                    <maven.compiler.release>999999999999999999999999</maven.compiler.release>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>6.5.11</version>
                    </dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void staysScopedToFilesThatActuallyDeclareCoreAndSkipsGeneratedOutputs() {
        rewriteRun(
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                          <properties><maven.compiler.release>8</maven.compiler.release></properties>
                          <dependencies>
                            <dependency>
                              <groupId>org.springframework</groupId>
                              <artifactId>spring-core</artifactId>
                              <version>5.3.39</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("unrelated/pom.xml").afterRecipe(after -> {
                    String printed = after.printAll();
                    assertFalse(printed.contains(FindSpringSecurityCore6511BuildRisks.JAVA_BASELINE), printed);
                    assertFalse(printed.contains(FindSpringSecurityCore6511BuildRisks.SPRING_ALIGNMENT), printed);
                })),
                pomXml(UpgradeSpringSecurityCoreDependencyTest.pom("7.0.4"),
                        source -> source.path("target/generated/pom.xml").afterRecipe(after ->
                                assertFalse(after.printAll().contains(
                                        SpringSecurityCoreSupport.TARGET_CONFLICT), after.printAll()))));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0;
             at += token.length()) {
            count++;
        }
        return count;
    }
}
