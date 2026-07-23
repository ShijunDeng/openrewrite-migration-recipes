package com.huawei.clouds.openrewrite.springsecuritycore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSpringSecurityCoreDependencyTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springsecuritycore.UpgradeSpringSecurityCoreTo6_5_11";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.springsecuritycore.MigrateSpringSecurityCoreTo6_5_11";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "5.3.10.RELEASE", "5.7.1", "5.8.5", "6.4.4", "6.4.6", "6.4.10", "6.5.1"
    })
    void upgradesEveryExactWorkbookSourceInMaven(String version) {
        rewriteRun(pomXml(pom(version), pom("6.5.11"), source -> source.path("pom.xml")));
    }

    @Test
    void upgradesAnExclusivelyOwnedMavenProperty() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <properties><security.core.version>5.8.5</security.core.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>${security.core.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """, """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <properties><security.core.version>6.5.11</security.core.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>${security.core.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesDependencyManagementAndProfileOwnersWithoutChangingStructure() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.springframework.security</groupId>
                        <artifactId>spring-security-core</artifactId>
                        <version>5.7.1</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <profiles>
                    <profile>
                      <id>legacy</id>
                      <dependencies>
                        <dependency>
                          <groupId>org.springframework.security</groupId>
                          <artifactId>spring-security-core</artifactId>
                          <version>5.7.1</version>
                        </dependency>
                      </dependencies>
                    </profile>
                  </profiles>
                </project>
                """, """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.springframework.security</groupId>
                        <artifactId>spring-security-core</artifactId>
                        <version>6.5.11</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <profiles>
                    <profile>
                      <id>legacy</id>
                      <dependencies>
                        <dependency>
                          <groupId>org.springframework.security</groupId>
                          <artifactId>spring-security-core</artifactId>
                          <version>6.5.11</version>
                        </dependency>
                      </dependencies>
                    </profile>
                  </profiles>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void refusesAmbiguousRootPropertyShadowedByAProfile() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <properties><security.core.version>5.8.5</security.core.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>${security.core.version}</version>
                    </dependency>
                  </dependencies>
                  <profiles>
                    <profile>
                      <id>legacy</id>
                      <properties><security.core.version>6.4.4</security.core.version></properties>
                    </profile>
                  </profiles>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void refusesToRewriteAPropertySharedWithAnotherArtifact() {
        rewriteRun(pomXml("""
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
                """, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesRootGroovyStringAndMapDeclarations() {
        rewriteRun(
                buildGradle("""
                        dependencies {
                            implementation 'org.springframework.security:spring-security-core:5.7.1'
                        }
                        """, """
                        dependencies {
                            implementation 'org.springframework.security:spring-security-core:6.5.11'
                        }
                        """),
                buildGradle("""
                        dependencies {
                            implementation group: 'org.springframework.security',
                                    name: 'spring-security-core', version: '5.7.1'
                        }
                        """, """
                        dependencies {
                            implementation group: 'org.springframework.security',
                                    name: 'spring-security-core', version: '6.5.11'
                        }
                        """));
    }

    @Test
    void upgradesRootKotlinStringAndNamedDeclaration() {
        rewriteRun(
                buildGradleKts("""
                        dependencies {
                            implementation("org.springframework.security:spring-security-core:5.8.5")
                        }
                        """, """
                        dependencies {
                            implementation("org.springframework.security:spring-security-core:6.5.11")
                        }
                        """),
                buildGradleKts("""
                        dependencies {
                            implementation(group = "org.springframework.security",
                                name = "spring-security-core", version = "5.8.5")
                        }
                        """, """
                        dependencies {
                            implementation(group = "org.springframework.security",
                                name = "spring-security-core", version = "6.5.11")
                        }
                        """));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5.8.4", "6.5.10", "6.5.11", "7.0.4", "7.1.0"})
    void upgradeOnlyRecipePreservesEveryVersionOutsideTheExactWhitelist(String version) {
        rewriteRun(pomXml(pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"6.5.11", "6.5.10", "6.4.4"})
    void mixedTargetOffListOrSecondSelectedVersionBlocksAllAuto(String companionVersion) {
        rewriteRun(pomXml(pomWithVersions("5.8.5", companionVersion),
                source -> source.path("pom.xml")));
    }

    @Test
    void conflictingVersionsAcrossBuildFilesAtOneRootBlockAllAuto() {
        rewriteRun(
                pomXml(pom("5.8.5"), source -> source.path("pom.xml")),
                buildGradle("""
                        dependencies {
                            implementation 'org.springframework.security:spring-security-core:6.5.11'
                        }
                        """, source -> source.path("build.gradle")));
    }

    @Test
    void nestedConflictingOwnerDoesNotBlockOuterRootOrInheritItsEligibility() {
        rewriteRun(
                pomXml(pom("5.8.5"), pom("6.5.11"),
                        source -> source.path("pom.xml")),
                pomXml(pomWithVersions("5.7.1", "6.5.11"),
                        source -> source.path("nested/pom.xml")));
    }

    @Test
    void variantAndGeneratedDeclarationsAreNeverChanged() {
        rewriteRun(
                pomXml(pomWithClassifier("5.8.5"), source -> source.path("pom.xml")),
                pomXml(pom("5.8.5"), source -> source.path("target/generated/pom.xml")),
                buildGradle("""
                        dependencies {
                            implementation 'org.springframework.security:spring-security-core:5.8.5@zip'
                        }
                        """, source -> source.path("build/generated/build.gradle")));
    }

    @Test
    void recommendedRecipePreservesHighVersionAndAddsExactConflictMarkerOnce() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("7.1.0"), source -> source.path("pom.xml")
                        .after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>7.1.0</version>"), printed);
                            assertFalse(printed.contains("<version>6.5.11</version>"), printed);
                            assertEquals(1, occurrences(printed,
                                    SpringSecurityCoreSupport.TARGET_CONFLICT), printed);
                        })));
    }

    @Test
    void targetComparisonHandlesNumericOverflowWithoutDowngrade() {
        assertTrue(SpringSecurityCoreSupport.targetConflict("999999999999999999999.0.0"));
        assertTrue(SpringSecurityCoreSupport.targetConflict("7.0.0"));
        assertTrue(SpringSecurityCoreSupport.targetConflict("6.5.12"));
        assertFalse(SpringSecurityCoreSupport.targetConflict("6.5.11"));
        assertFalse(SpringSecurityCoreSupport.targetConflict("6.4.999"));
        assertTrue(SpringSecurityCoreSupport.versionGreaterThan(
                "999999999999999999999.0.0", "6.2.19"));
        assertTrue(SpringSecurityCoreSupport.versionGreaterThan("6.2.20", "6.2.19"));
        assertFalse(SpringSecurityCoreSupport.versionGreaterThan("6.2.19", "6.2.19"));
        assertFalse(SpringSecurityCoreSupport.versionGreaterThan("dynamic", "6.2.19"));
    }

    static String pom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(version);
    }

    private static String pomWithVersions(String first, String second) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>%s</version>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(first, second);
    }

    private static String pomWithClassifier(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.security</groupId>
                      <artifactId>spring-security-core</artifactId>
                      <version>%s</version>
                      <classifier>tests</classifier>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(version);
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }

    private static Environment environment() {
        return SpringSecurityCoreTestSupport.environment();
    }
}
