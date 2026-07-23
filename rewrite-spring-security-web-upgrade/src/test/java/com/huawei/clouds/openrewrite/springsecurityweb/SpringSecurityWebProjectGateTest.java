package com.huawei.clouds.openrewrite.springsecurityweb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringSecurityWebProjectGateTest implements RewriteTest {
    @Test
    void carriesExactSourceVersionFromBuildRoot() {
        rewriteRun(
                spec -> spec.recipe(new MarkSelectedSpringSecurityWebProjects()),
                pomXml(UpgradeSpringSecurityWebDependencyTest.pom("5.8.16"),
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            SpringSecurityWebProjectMarker marker = after.getMarkers()
                                    .findFirst(SpringSecurityWebProjectMarker.class)
                                    .orElseThrow();
                            assertEquals("5.8.16", marker.getSourceVersion());
                        })));
    }

    @Test
    void releaseComparisonIsStrictAndReleaseAware() {
        assertTrue(SpringSecurityWebUpgradeSupport.requiresMigrationTo("5.8.16", "6.0"));
        assertTrue(SpringSecurityWebUpgradeSupport.requiresMigrationTo("5.1.5.RELEASE", "5.7"));
        assertTrue(!SpringSecurityWebUpgradeSupport.requiresMigrationTo("6.2.2", "6.2"));
        assertTrue(!SpringSecurityWebUpgradeSupport.requiresMigrationTo("6.5.10", "6.2"));
    }

    @Test
    void carriesExclusiveMavenPropertyOwner() {
        rewriteRun(
                spec -> spec.recipe(new MarkSelectedSpringSecurityWebProjects()),
                pomXml(UpgradeSpringSecurityWebDependencyTest.project(
                                "<properties><security.web.version>5.7.14</security.web.version></properties>" +
                                "<dependencies>" +
                                UpgradeSpringSecurityWebDependencyTest.dep("${security.web.version}") +
                                "</dependencies>"),
                        source -> source.path("pom.xml").after(actual -> actual)
                                .afterRecipe(after -> assertEquals("5.7.14", after.getMarkers()
                                        .findFirst(SpringSecurityWebProjectMarker.class)
                                        .orElseThrow().getSourceVersion()))));
    }

    @Test
    void carriesGroovyAndKotlinStringCoordinates() {
        rewriteRun(
                spec -> spec.recipe(new MarkSelectedSpringSecurityWebProjects()),
                buildGradle(
                        "dependencies { implementation 'org.springframework.security:spring-security-web:5.8.16' }",
                        source -> source.path("groovy/build.gradle").after(actual -> actual)
                                .afterRecipe(after -> assertEquals("5.8.16", after.getMarkers()
                                        .findFirst(SpringSecurityWebProjectMarker.class)
                                        .orElseThrow().getSourceVersion()))),
                buildGradleKts(
                        "dependencies { implementation(\"org.springframework.security:spring-security-web:6.4.13\") }",
                        source -> source.path("kotlin/build.gradle.kts").after(actual -> actual)
                                .afterRecipe(after -> assertEquals("6.4.13", after.getMarkers()
                                        .findFirst(SpringSecurityWebProjectMarker.class)
                                        .orElseThrow().getSourceVersion()))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "6.5.11", "7.0.0", "7.0.4", "5.8.10",
            "${spring-security.version}", "[5.8,7)"
    })
    void targetConflictOutsideAndUnresolvedVersionsDoNotSelect(String version) {
        rewriteRun(
                spec -> spec.recipe(new MarkSelectedSpringSecurityWebProjects()),
                xml(UpgradeSpringSecurityWebDependencyTest.pom(version),
                        source -> source.path("pom.xml").afterRecipe(after ->
                                assertFalse(after.getMarkers()
                                        .findFirst(SpringSecurityWebProjectMarker.class)
                                        .isPresent()))));
    }

    @Test
    void conflictingSelectedVersionsDoNotSelectTheProject() {
        rewriteRun(
                spec -> spec.recipe(new MarkSelectedSpringSecurityWebProjects()),
                pomXml(UpgradeSpringSecurityWebDependencyTest.project(
                                "<dependencies>" +
                                UpgradeSpringSecurityWebDependencyTest.dep("5.8.16") +
                                UpgradeSpringSecurityWebDependencyTest.dep("6.4.13") +
                                "</dependencies>"),
                        source -> source.path("pom.xml").afterRecipe(after ->
                                assertFalse(after.getMarkers()
                                        .findFirst(SpringSecurityWebProjectMarker.class)
                                        .isPresent()))));
    }

    @Test
    void nearestNestedBuildRootBlocksOuterSelection() {
        rewriteRun(
                spec -> spec.recipe(new MarkSelectedSpringSecurityWebProjects()),
                pomXml(UpgradeSpringSecurityWebDependencyTest.pom("5.8.16"),
                        source -> source.path("pom.xml").after(actual -> actual)),
                java("class Outer {}", source -> source.path("src/main/java/Outer.java")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.getMarkers()
                                        .findFirst(SpringSecurityWebProjectMarker.class)
                                        .isPresent()))),
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>nested</artifactId><version>1</version>
                        </project>
                        """, source -> source.path("nested/pom.xml")),
                java("class Nested {}", source -> source.path("nested/src/main/java/Nested.java")
                        .afterRecipe(after -> assertFalse(after.getMarkers()
                                .findFirst(SpringSecurityWebProjectMarker.class)
                                .isPresent()))));
    }
}
