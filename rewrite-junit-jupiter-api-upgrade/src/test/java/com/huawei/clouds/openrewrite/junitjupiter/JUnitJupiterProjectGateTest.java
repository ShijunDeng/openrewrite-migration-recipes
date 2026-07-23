package com.huawei.clouds.openrewrite.junitjupiter;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;

class JUnitJupiterProjectGateTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterTestApi.sources()))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath(
                                "com.huawei.clouds.openrewrite.junitjupiter",
                                "org.openrewrite.java",
                                "org.openrewrite.java.dependencies",
                                "org.openrewrite.java.testing.junit6")
                        .build()
                        .activateRecipes(
                                "com.huawei.clouds.openrewrite.junitjupiter.UpgradeJUnitJupiterApiTo6_0_1",
                                "com.huawei.clouds.openrewrite.junitjupiter.MigrateSelectedJUnitJupiter6Java",
                                "com.huawei.clouds.openrewrite.junitjupiter.FindSelectedJUnitJupiter6SourceAndConfigurationRisks"));
    }

    @Test
    void projectWithoutTargetDependencyDoesNotAuthorizeSourceOrConfiguration() {
        rewriteRun(
                java(oldStoreSource("NoBuildExtension")),
                properties("junit.jupiter.tempdir.scope=per_context\n",
                        source -> source.path("src/test/resources/junit-platform.properties")));
    }

    @Test
    void targetAndOffListVersionsDoNotAuthorizeSourceOrConfiguration() {
        rewriteRun(
                xml(UpgradeJUnitJupiterApiDependencyTest.pom("6.0.1"),
                        source -> source.path("target-version/pom.xml")),
                java(oldStoreSource("TargetExtension"),
                        source -> source.path("target-version/src/test/java/TargetExtension.java")),
                properties("junit.jupiter.tempdir.scope=per_context\n",
                        source -> source.path("target-version/src/test/resources/junit-platform.properties")),
                xml(UpgradeJUnitJupiterApiDependencyTest.pom("5.10.0"),
                        source -> source.path("off-list/pom.xml")),
                java(oldStoreSource("OffListExtension"),
                        source -> source.path("off-list/src/test/java/OffListExtension.java")),
                properties("junit.jupiter.tempdir.scope=per_context\n",
                        source -> source.path("off-list/src/test/resources/junit-platform.properties")));
    }

    @Test
    void mixedWorkbookVersionsAreAConflictAndRemainByteStable() {
        String mixed = UpgradeJUnitJupiterApiDependencyTest.project(
                "<dependencies>" +
                UpgradeJUnitJupiterApiDependencyTest.dep("5.7.1") +
                UpgradeJUnitJupiterApiDependencyTest.dep("5.8.2") +
                "</dependencies>");
        rewriteRun(
                xml(mixed, source -> source.path("pom.xml")),
                java(oldStoreSource("MixedExtension")),
                properties("junit.jupiter.tempdir.scope=per_context\n",
                        source -> source.path("src/test/resources/junit-platform.properties")));
    }

    @Test
    void auxiliaryGradleScriptIsNotAnIndependentProjectBoundary() {
        rewriteRun(
                buildGradle(
                        "dependencies { testImplementation 'org.junit.jupiter:junit-jupiter-api:5.9.3' }",
                        source -> source.path("gradle/junit-conventions.gradle")),
                java(oldStoreSource("ConventionExtension"),
                        source -> source.path("gradle/src/test/java/ConventionExtension.java")));
    }

    @Test
    void mixedGroovyDynamicCoordinateBlocksTheEntireProject() {
        rewriteRun(
                buildGradle("""
                  def junitVersion = '5.10.0'
                  dependencies {
                      testImplementation 'org.junit.jupiter:junit-jupiter-api:5.7.1'
                      implementation "org.junit.jupiter:junit-jupiter-api:$junitVersion"
                  }
                  """),
                java(oldStoreSource("DynamicGroovyExtension"),
                        source -> source.path("src/test/java/DynamicGroovyExtension.java")));
    }

    @Test
    void mixedKotlinDynamicCoordinateBlocksTheEntireProject() {
        rewriteRun(
                buildGradleKts("""
                  val junitVersion = "5.10.0"
                  dependencies {
                      testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.1")
                      implementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
                  }
                  """),
                java(oldStoreSource("DynamicKotlinExtension"),
                        source -> source.path("src/test/java/DynamicKotlinExtension.java")));
    }

    @Test
    void nestedBuildWithoutTheDependencyShadowsASelectedParentRoot() {
        rewriteRun(
                xml(UpgradeJUnitJupiterApiDependencyTest.pom("5.7.1"),
                        UpgradeJUnitJupiterApiDependencyTest.pom("6.0.1"),
                        source -> source.path("pom.xml")),
                java(oldStoreSource("ParentExtension"), newStoreSource("ParentExtension"),
                        source -> source.path("src/test/java/ParentExtension.java")),
                xml(UpgradeJUnitJupiterApiDependencyTest.project(""),
                        source -> source.path("child/pom.xml")),
                java(oldStoreSource("ChildExtension"),
                        source -> source.path("child/src/test/java/ChildExtension.java")));
    }

    private static String oldStoreSource(String className) {
        return """
          import org.junit.jupiter.api.extension.ExtensionContext;
          class %s {
              Object value(ExtensionContext.Store store) {
                  return store.getOrComputeIfAbsent(String.class);
              }
          }
          """.formatted(className);
    }

    private static String newStoreSource(String className) {
        return """
          import org.junit.jupiter.api.extension.ExtensionContext;
          class %s {
              Object value(ExtensionContext.Store store) {
                  return store.computeIfAbsent(String.class);
              }
          }
          """.formatted(className);
    }
}
