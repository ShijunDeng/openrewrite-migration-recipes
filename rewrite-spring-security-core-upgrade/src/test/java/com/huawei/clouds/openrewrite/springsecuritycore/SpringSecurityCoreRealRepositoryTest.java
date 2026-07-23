package com.huawei.clouds.openrewrite.springsecuritycore;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;

class SpringSecurityCoreRealRepositoryTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springsecuritycore.MigrateSpringSecurityCoreTo6_5_11";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(SpringSecurityCoreTestSupport.environment().activateRecipes(RECIPE));
    }

    @Test
    void cognizantViewerExactDependencyExcerptUpgrades() {
        rewriteRun(buildGradle("""
                dependencies {
                    implementation 'io.github.openfeign:feign-jackson:12.4'
                    implementation 'org.springframework.security:spring-security-core:5.8.5'
                    testImplementation 'org.springframework.security:spring-security-test:5.8.5'
                }
                """, source -> source.path("CXA-Viewer/Back-End/Java/auth-api/build.gradle")
                .after(actual -> actual)
                .afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(
                            "org.springframework.security:spring-security-core:6.5.11"), printed);
                    assertTrue(printed.contains(
                            "org.springframework.security:spring-security-test:5.8.5"), printed);
                })));
    }

    @Test
    void apacheFineractDependencyManagementClosureIsNotMistakenForRootDependencyOwner() {
        String fixture = """
                dependencies {
                    components {
                        dependency 'com.nimbusds:nimbus-jose-jwt:10.9'
                        dependency 'org.springframework.security:spring-security-core:6.5.10'
                        dependency 'io.netty:netty-buffer:4.1.135.Final'
                    }
                }
                """;
        rewriteRun(buildGradle(fixture, source -> source
                .path("buildSrc/src/main/groovy/org.apache.fineract.dependencies.gradle")
                .afterRecipe(after -> assertEquals(fixture.trim(), after.printAll().trim()))));
    }

    @Test
    void urlShortenerHigherReleaseIsPreservedAndMarkedInsteadOfDowngraded() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                buildGradle("""
                        dependencies {
                            runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
                            implementation("org.springframework.security:spring-security-core:7.1.0")
                            implementation 'org.springframework.boot:spring-boot-starter-security'
                        }
                        """, source -> source.path("build.gradle")
                        .after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(
                                    "org.springframework.security:spring-security-core:7.1.0"), printed);
                            assertFalse(printed.contains(
                                    "org.springframework.security:spring-security-core:6.5.11"), printed);
                            assertEquals(1, occurrences(printed,
                                    SpringSecurityCoreSupport.TARGET_CONFLICT), printed);
                        })));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
