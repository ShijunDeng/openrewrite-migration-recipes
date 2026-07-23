package com.huawei.clouds.openrewrite.selenium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.xml.Assertions.xml;

class FindSeleniumBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) { spec.recipe(new FindSeleniumBuildRisks()); }

    @ParameterizedTest(name = "unsupported version {0}")
    @ValueSource(strings = {"4.8.1", "4.8.2", "4.40.0", "${external.version}", "[4,5)", ""})
    void marksUnresolvedMavenVersion(String version) {
        String dependency = version.isEmpty()
                ? "<dependency><groupId>org.seleniumhq.selenium</groupId><artifactId>selenium-java</artifactId></dependency>"
                : UpgradeSeleniumDependencyTest.dep(version);
        rewriteRun(xml(UpgradeSeleniumDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                spec -> spec.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("workbook-owned literal 4.8.1"), after::printAll))));
    }

    @Test
    void resolvedTargetPropertyIsNotMarked() {
        rewriteRun(xml(UpgradeSeleniumDependencyTest.project(
                "<properties><s>4.41.0</s></properties><dependencies>" +
                UpgradeSeleniumDependencyTest.dep("${s}") + "</dependencies>"), spec -> spec.path("pom.xml")));
    }

    @Test
    void duplicateTargetPropertyIsMarkedAsAmbiguousOwnership() {
        rewriteRun(xml(UpgradeSeleniumDependencyTest.project(
                "<properties><s>4.41.0</s><s>4.41.0</s></properties><dependencies>" +
                UpgradeSeleniumDependencyTest.dep("${s}") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("BOM/property/catalog/platform owner"), after::printAll))));
    }

    @Test
    void marksVariantAndJava8ButNotJava11() {
        rewriteRun(xml(UpgradeSeleniumDependencyTest.project(
                "<properties><maven.compiler.release>8</maven.compiler.release><maven.compiler.source>11</maven.compiler.source></properties>" +
                "<dependencies>" + UpgradeSeleniumDependencyTest.rawDep("org.seleniumhq.selenium", "selenium-java", "4.41.0", "<classifier>tests</classifier>") + "</dependencies>"),
                spec -> spec.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("artifact topology"), out);
                    assertTrue(out.contains("requires Java 11"), out);
                })));
    }

    @Test
    void profileDependencyDoesNotMarkSiblingProfileJavaBaseline() {
        rewriteRun(xml(UpgradeSeleniumDependencyTest.project("""
                <profiles>
                  <profile><id>selenium</id><dependencies>%s</dependencies></profile>
                  <profile><id>sibling</id><properties><java.version>8</java.version></properties></profile>
                </profiles>
                """.formatted(UpgradeSeleniumDependencyTest.dep("4.41.0"))), source -> source.path("pom.xml")));
    }

    @Test
    void rootJavaBaselineIsVisibleToProfileDependency() {
        rewriteRun(xml(UpgradeSeleniumDependencyTest.project("""
                <properties><java.version>8</java.version></properties>
                <profiles><profile><id>selenium</id><dependencies>%s</dependencies></profile></profiles>
                """.formatted(UpgradeSeleniumDependencyTest.dep("4.41.0"))),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("requires Java 11"), after::printAll))));
    }

    @Test
    void marksGradleVariableAndVariantButNotTarget() {
        rewriteRun(
                buildGradle("def seleniumVersion='4.8.1'; dependencies { implementation \"org.seleniumhq.selenium:selenium-java:$seleniumVersion\" }",
                        spec -> spec.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("BOM/property/catalog"), after::printAll))),
                buildGradle("dependencies { implementation 'org.seleniumhq.selenium:selenium-java:4.8.1:tests' }",
                        spec -> spec.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("artifact topology"), after::printAll))),
                buildGradle("dependencies { implementation 'org.seleniumhq.selenium:selenium-java:4.41.0' }"));
    }
}
