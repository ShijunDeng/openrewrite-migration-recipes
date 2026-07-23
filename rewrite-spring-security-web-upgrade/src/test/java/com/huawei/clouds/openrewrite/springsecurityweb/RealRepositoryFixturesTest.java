package com.huawei.clouds.openrewrite.springsecurityweb;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class RealRepositoryFixturesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springsecurityweb",
                                              "org.openrewrite.java.spring")
                        .build()
                        .activateRecipes(
                                "com.huawei.clouds.openrewrite.springsecurityweb.MigrateSpringSecurityWebTo6_5_11"))
                .parser(SpringSecurityWebTestSupport.parser());
    }

    @Test
    void eugenpJjwtFixtureMigratesJakartaCsrfAuthorizationAndMarksCustomFilter() {
        rewriteRun(selectedPom(), java(fixture("eugenp-jjwt-security.java"),
                source -> source.path("RealJjwtSecurityFixture.java")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("import jakarta.servlet.FilterChain;"), printed);
                            assertFalse(printed.contains("authorizeRequests"), printed);
                            assertFalse(printed.contains("antMatchers"), printed);
                            assertFalse(printed.contains("ignoringAntMatchers"), printed);
                            assertTrue(printed.contains("authorizeHttpRequests"), printed);
                            assertTrue(printed.contains("requestMatchers"), printed);
                            assertTrue(printed.contains("ignoringRequestMatchers"), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.CSRF), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.FILTER_ORDER), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.EXTENSION), printed);
                        })));
    }

    @Test
    void eugenpHttpClientFixtureMigratesLegacyDslAndMarksFilterAuthentication() {
        rewriteRun(selectedPom(), java(fixture("eugenp-httpclient-security.java"),
                source -> source.path("RealHttpClientSecurityFixture.java")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertFalse(printed.contains("authorizeRequests"), printed);
                            assertFalse(printed.contains("antMatchers"), printed);
                            assertTrue(printed.contains("authorizeHttpRequests"), printed);
                            assertTrue(printed.contains("requestMatchers"), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.AUTHENTICATION), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.FILTER_ORDER), printed);
                        })));
    }

    @Test
    void springGuideFixtureKeepsModernDslAndMarksBehaviorBoundaries() {
        rewriteRun(selectedPom(), java(fixture("spring-guide-security.java"),
                source -> source.path("RealSpringGuideSecurityFixture.java")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("authorizeHttpRequests"), printed);
                            assertTrue(printed.contains("requestMatchers"), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.FILTER_CHAIN), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.AUTHORIZATION), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.AUTHENTICATION), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.LOGOUT), printed);
                        })));
    }

    @Test
    void officialRememberMeSampleMarksRememberMeAndLoginReview() {
        rewriteRun(selectedPom(), java(fixture("spring-security-sample-remember-me.java"),
                source -> source.path("RealRememberMeSecurityFixture.java")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.REMEMBER_ME), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.AUTHENTICATION), printed);
                            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.FILTER_CHAIN), printed);
                        })));
    }

    private static org.openrewrite.test.SourceSpecs selectedPom() {
        return pomXml(UpgradeSpringSecurityWebDependencyTest.pom("5.8.16"),
                source -> source.path("pom.xml").after(actual -> actual));
    }

    private static String fixture(String name) {
        String path = "/fixtures/real/" + name;
        try (InputStream stream = RealRepositoryFixturesTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load " + path, exception);
        }
    }
}
