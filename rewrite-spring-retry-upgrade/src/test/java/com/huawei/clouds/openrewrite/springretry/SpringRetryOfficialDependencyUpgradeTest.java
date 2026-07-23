package com.huawei.clouds.openrewrite.springretry;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringRetryOfficialDependencyUpgradeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(official());
    }

    @Test
    void usesTheFixedOfficialRecipeArtifact() {
        String location = UpgradeDependencyVersion.class.getProtectionDomain()
                .getCodeSource().getLocation().toString();
        assertTrue(location.contains("/rewrite-java-dependencies/1.59.0/"), location);
    }

    @Test
    void officialRecipeUpgradesAResolvedMavenLiteral() {
        rewriteRun(pomXml(
                SpringRetryTestSupport.pom("1.3.4"),
                SpringRetryTestSupport.pom("2.0.13")));
    }

    @Test
    void officialRecipeUpgradesAResolvedMavenProperty() {
        String before = SpringRetryTestSupport.project(
                "<properties><retry.version>1.3.4</retry.version></properties><dependencies>" +
                SpringRetryTestSupport.dependency("${retry.version}", "") + "</dependencies>");
        String after = before.replace(
                "<retry.version>1.3.4</retry.version>",
                "<retry.version>2.0.13</retry.version>");
        rewriteRun(pomXml(before, after));
    }

    @Test
    void officialRecipeUpgradesGradleStringAndMapDeclarations() {
        rewriteRun(
                buildGradle(
                        "dependencies { implementation 'org.springframework.retry:spring-retry:1.3.4' }",
                        "dependencies { implementation 'org.springframework.retry:spring-retry:2.0.13' }"),
                buildGradle(
                        "dependencies { runtimeOnly group: 'org.springframework.retry', name: 'spring-retry', version: '1.3.4' }",
                        "dependencies { runtimeOnly group: 'org.springframework.retry', name: 'spring-retry', version: '2.0.13' }",
                        source -> source.path("map/build.gradle")));
    }

    @Test
    void officialMavenDelegateNeedsResolutionMarkersSoTheStrictFallbackRemainsNecessary() {
        rewriteRun(xml(SpringRetryTestSupport.pom("1.3.4")));
    }

    private static UpgradeDependencyVersion official() {
        return new UpgradeDependencyVersion(
                SpringRetrySupport.GROUP,
                SpringRetrySupport.ARTIFACT,
                SpringRetrySupport.TARGET,
                null,
                false,
                null);
    }
}
