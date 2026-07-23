package com.huawei.clouds.openrewrite.springsecuritycore;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringSecurityCoreSelectedProjectGateTest implements RewriteTest {
    private static final String PREFIX =
            "com.huawei.clouds.openrewrite.springsecuritycore.";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(selectedMigrations())
                .parser(SpringSecurityCoreTestSupport.parser());
    }

    @Test
    void selectedMavenOwnerEnablesOfficialJavaAndXmlMigrations() {
        rewriteRun(
                xml(pom("5.8.5"), source -> source.path("pom.xml")),
                java("""
                        import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
                        class Passwords {
                            Pbkdf2PasswordEncoder encoder() {
                                return new Pbkdf2PasswordEncoder();
                            }
                        }
                        """, """
                        import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
                        class Passwords {
                            Pbkdf2PasswordEncoder encoder() {
                                return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
                            }
                        }
                        """, source -> source.path("src/main/java/Passwords.java")),
                xml("""
                        <beans xmlns="http://www.springframework.org/schema/beans"
                               xmlns:ss="http://www.springframework.org/schema/security">
                          <ss:global-method-security pre-post-enabled="true"/>
                        </beans>
                        """, """
                        <beans xmlns="http://www.springframework.org/schema/beans"
                               xmlns:ss="http://www.springframework.org/schema/security">
                          <ss:method-security/>
                        </beans>
                        """, source -> source.path("src/main/resources/security.xml")));
    }

    @Test
    void exclusiveMavenPropertyOwnerRetainsPreUpgradeEligibility() {
        rewriteRun(
                xml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><security.core.version>6.4.10</security.core.version></properties>
                          <dependencies><dependency>
                            <groupId>org.springframework.security</groupId>
                            <artifactId>spring-security-core</artifactId>
                            <version>${security.core.version}</version>
                          </dependency></dependencies>
                        </project>
                        """, source -> source.path("pom.xml")),
                java("""
                        import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
                        class Passwords {
                            SCryptPasswordEncoder encoder() {
                                return new SCryptPasswordEncoder();
                            }
                        }
                        """, """
                        import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
                        class Passwords {
                            SCryptPasswordEncoder encoder() {
                                return SCryptPasswordEncoder.defaultsForSpringSecurity_v4_1();
                            }
                        }
                        """, source -> source.path("src/main/java/Passwords.java")));
    }

    @Test
    void targetHigherOffWhitelistAndUnrelatedProjectsCannotRunSourceRecipes() {
        rewriteRun(
                xml(pom("6.5.11"), source -> source.path("target/pom.xml")),
                passwordSource("TargetPasswords", "target/src/main/java/TargetPasswords.java"),
                xml(pom("7.0.4"), source -> source.path("higher/pom.xml")),
                passwordSource("HigherPasswords", "higher/src/main/java/HigherPasswords.java"),
                xml(pom("6.5.10"), source -> source.path("off-list/pom.xml")),
                passwordSource("OffListPasswords", "off-list/src/main/java/OffListPasswords.java"),
                passwordSource("UnrelatedPasswords", "unrelated/src/main/java/UnrelatedPasswords.java"));
    }

    @Test
    void conflictingOwnersBlockEverySourceLeaf() {
        rewriteRun(
                xml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>org.springframework.security</groupId>
                              <artifactId>spring-security-core</artifactId>
                              <version>5.8.5</version>
                            </dependency>
                            <dependency>
                              <groupId>org.springframework.security</groupId>
                              <artifactId>spring-security-core</artifactId>
                              <version>6.5.11</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("pom.xml")),
                passwordSource("Passwords", "src/main/java/Passwords.java"));
    }

    @Test
    void nestedBuildBoundaryStopsOuterEligibility() {
        rewriteRun(
                xml(pom("5.8.5"), source -> source.path("pom.xml")),
                java(password("RootPasswords"), migratedPassword("RootPasswords"),
                        source -> source.path("src/main/java/RootPasswords.java")),
                xml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>nested</artifactId><version>1</version>
                        </project>
                        """, source -> source.path("nested/pom.xml")),
                passwordSource("NestedPasswords",
                        "nested/src/main/java/NestedPasswords.java"));
    }

    @Test
    void selectedGradleOwnerUsesTheSameNearestBuildRootGate() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework.security:spring-security-core:5.7.1' }",
                        source -> source.path("build.gradle")),
                java(password("Passwords"), migratedPassword("Passwords"),
                        source -> source.path("src/main/java/Passwords.java")));
    }

    @Test
    void recommendedRecipeLeavesTargetOffListAndUnrelatedProjectsCompletelyAlone() {
        rewriteRun(spec -> spec.recipe(recommended()),
                pomXml(pom("6.5.11"), source -> source.path("target/pom.xml")),
                passwordSource("TargetPasswords", "target/src/main/java/TargetPasswords.java"),
                pomXml(pom("6.5.10"), source -> source.path("off-list/pom.xml")),
                passwordSource("OffListPasswords", "off-list/src/main/java/OffListPasswords.java"),
                passwordSource("UnrelatedPasswords", "unrelated/src/main/java/UnrelatedPasswords.java"));
    }

    @Test
    void recommendedRecipePreservesFutureAndAddsOnlyTheExactConflictMarker() {
        rewriteRun(spec -> spec.recipe(recommended())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("7.0.4"), source -> source.path("future/pom.xml")
                        .after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>7.0.4</version>"), printed);
                            assertFalse(printed.contains("<version>6.5.11</version>"), printed);
                            assertEquals(1, occurrences(printed,
                                    SpringSecurityCoreSupport.TARGET_CONFLICT), printed);
                            assertFalse(printed.contains(
                                    FindSpringSecurityCore6511BuildRisks.OUTSIDE), printed);
                            assertFalse(printed.contains(
                                    FindSpringSecurityCore6511BuildRisks.OWNER), printed);
                        })),
                passwordSource("FuturePasswords",
                        "future/src/main/java/FuturePasswords.java"));
    }

    @Test
    void recommendedRecipeResolvesSharedFuturePropertyWithoutOwnerNoise() {
        rewriteRun(spec -> spec.recipe(recommended())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
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
                        """, source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertEquals(1, occurrences(printed,
                                    SpringSecurityCoreSupport.TARGET_CONFLICT), printed);
                            assertFalse(printed.contains(
                                    FindSpringSecurityCore6511BuildRisks.OWNER), printed);
                            assertTrue(printed.contains(
                                    "<security.version>7.0.4</security.version>"), printed);
                        })));
    }

    @Test
    void recommendedRecipeHandlesArbitrarilyLargeFutureVersionWithoutOverflow() {
        rewriteRun(spec -> spec.recipe(recommended())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                xml(pom("999999999999999999999.0.0"),
                        source -> source.path("pom.xml")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertEquals(1, occurrences(printed,
                                            SpringSecurityCoreSupport.TARGET_CONFLICT), printed);
                                    assertTrue(printed.contains(
                                            "<version>999999999999999999999.0.0</version>"),
                                            printed);
                                })));
    }

    @Test
    void sourceRiskMarkersAreAlsoConfinedToSelectedProjects() {
        rewriteRun(spec -> spec.recipe(selectedSourceRisks()),
                xml(pom("6.4.4"), source -> source.path("selected/pom.xml")),
                java("""
                        import org.springframework.security.access.prepost.PreAuthorize;
                        class SelectedService {
                            @PreAuthorize("#owner == authentication.name")
                            void read(String owner) {
                            }
                        }
                        """, source -> source.path("selected/src/main/java/SelectedService.java")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(
                                        FindSpringSecurityCore6511SourceRisks.AUTHORIZATION),
                                        after.printAll()))),
                xml(pom("6.5.11"), source -> source.path("target/pom.xml")),
                java("""
                        import org.springframework.security.access.prepost.PreAuthorize;
                        class TargetService {
                            @PreAuthorize("#owner == authentication.name")
                            void read(String owner) {
                            }
                        }
                        """, source -> source.path("target/src/main/java/TargetService.java")
                        .afterRecipe(after -> assertFalse(after.printAll().contains(
                                FindSpringSecurityCore6511SourceRisks.AUTHORIZATION),
                                after.printAll()))));
    }

    private static org.openrewrite.test.SourceSpecs passwordSource(
            String type, String path) {
        return java(password(type), source -> source.path(path));
    }

    private static String password(String type) {
        return """
                import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
                class %s {
                    Pbkdf2PasswordEncoder encoder() {
                        return new Pbkdf2PasswordEncoder();
                    }
                }
                """.formatted(type);
    }

    private static String migratedPassword(String type) {
        return """
                import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
                class %s {
                    Pbkdf2PasswordEncoder encoder() {
                        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
                    }
                }
                """.formatted(type);
    }

    private static Recipe selectedMigrations() {
        return environment().activateRecipes(
                PREFIX + "MarkSelectedSpringSecurityCoreProjects",
                PREFIX + "MigrateSelectedDeterministicSpringSecurityCore6");
    }

    private static Recipe selectedSourceRisks() {
        return environment().activateRecipes(
                PREFIX + "MarkSelectedSpringSecurityCoreProjects",
                PREFIX + "FindSelectedSpringSecurityCore6_5_11SourceRisks");
    }

    private static Recipe recommended() {
        return environment().activateRecipes(
                PREFIX + "MigrateSpringSecurityCoreTo6_5_11");
    }

    private static Environment environment() {
        return SpringSecurityCoreTestSupport.environment();
    }

    private static String pom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.springframework.security</groupId>
                    <artifactId>spring-security-core</artifactId>
                    <version>%s</version>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
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
