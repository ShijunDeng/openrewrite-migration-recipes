package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class JUnitJupiterAggregateProjectGateTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterAggregateTestApi.sources()))
                .recipe(dependencyAndSourceMigration());
    }

    @Test
    void sourceWithoutBuildBoundaryIsNotMigrated() {
        rewriteRun(storeSource("StoreOnly.java"));
    }

    @Test
    void targetOffListFutureDynamicAndRangeProjectsAreNotMigrated() {
        rewriteRun(
                xml(UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("target-version/pom.xml")),
                storeSource("target-version/src/test/java/TargetTest.java"),
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.10.0"), source -> source.path("off-list/pom.xml")),
                storeSource("off-list/src/test/java/OffListTest.java"),
                xml(UpgradeJUnitJupiterDependencyTest.pom("7.0.0"), source -> source.path("future/pom.xml")),
                storeSource("future/src/test/java/FutureTest.java"),
                xml(UpgradeJUnitJupiterDependencyTest.pom("${junit.version}"), source -> source.path("dynamic/pom.xml")),
                storeSource("dynamic/src/test/java/DynamicTest.java"),
                xml(UpgradeJUnitJupiterDependencyTest.pom("[5.8,6)"), source -> source.path("range/pom.xml")),
                storeSource("range/src/test/java/RangeTest.java"));
    }

    @Test
    void mixedMavenVersionsBlockDependencyAndSourceMigration() {
        String mixed = UpgradeJUnitJupiterDependencyTest.project("<dependencies>" +
                UpgradeJUnitJupiterDependencyTest.dep("5.8.2") +
                UpgradeJUnitJupiterDependencyTest.dep("5.9.3") + "</dependencies>");
        rewriteRun(
                xml(mixed, source -> source.path("pom.xml")),
                storeSource("src/test/java/MixedTest.java"));
    }

    @Test
    void exclusiveMavenPropertySelectsButSharedPropertyBlocks() {
        rewriteRun(
                xml(
                        UpgradeJUnitJupiterDependencyTest.project(
                                "<properties><junit.version>5.9.1</junit.version></properties><dependencies>" +
                                UpgradeJUnitJupiterDependencyTest.dep("${junit.version}") + "</dependencies>"),
                        UpgradeJUnitJupiterDependencyTest.project(
                                "<properties><junit.version>6.0.1</junit.version></properties><dependencies>" +
                                UpgradeJUnitJupiterDependencyTest.dep("${junit.version}") + "</dependencies>"),
                        source -> source.path("selected/pom.xml")),
                storeSource(
                        "selected/src/test/java/SelectedPropertyTest.java",
                        """
                          import org.junit.jupiter.api.extension.ExtensionContext;
                          class StoreOnly {
                              Object value(ExtensionContext.Store store) {
                                  return store.computeIfAbsent(String.class);
                              }
                          }
                          """),
                xml(
                        UpgradeJUnitJupiterDependencyTest.project(
                                "<properties><junit.version>5.9.1</junit.version></properties><dependencies>" +
                                UpgradeJUnitJupiterDependencyTest.dep("${junit.version}") +
                                UpgradeJUnitJupiterDependencyTest.rawDep(
                                        "example", "shared", "${junit.version}", "") +
                                "</dependencies>"),
                        source -> source.path("shared/pom.xml")),
                storeSource("shared/src/test/java/SharedPropertyTest.java"));
    }

    @Test
    void offListDeclarationInAuxiliaryGradleScriptBlocksWholeProject() {
        rewriteRun(
                buildGradle("dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:5.9.1' }",
                        source -> source.path("build.gradle")),
                buildGradle("dependencies { testRuntimeOnly 'org.junit.jupiter:junit-jupiter:6.0.1' }",
                        source -> source.path("gradle/testing.gradle")),
                storeSource("src/test/java/MixedGradleTest.java"));
    }

    @Test
    void auxiliaryGradleScriptBelongsToNearestRealBuildRoot() {
        rewriteRun(
                buildGradle("", source -> source.path("build.gradle")),
                buildGradle(
                        "dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:5.9.3' }",
                        "dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:6.0.1' }",
                        source -> source.path("gradle/testing.gradle")),
                storeSource(
                        "src/test/java/AuxiliaryTest.java",
                        """
                          import org.junit.jupiter.api.extension.ExtensionContext;
                          class StoreOnly {
                              Object value(ExtensionContext.Store store) {
                                  return store.computeIfAbsent(String.class);
                              }
                          }
                          """));
    }

    @Test
    void auxiliaryScriptWithoutBuildBoundaryCannotSelectAProject() {
        rewriteRun(
                buildGradle("dependencies { testImplementation 'org.junit.jupiter:junit-jupiter:5.9.3' }",
                        source -> source.path("gradle/testing.gradle")),
                storeSource("src/test/java/UnownedTest.java"));
    }

    @Test
    void dynamicGradleCoordinateBlocksSourceMigration() {
        rewriteRun(
                buildGradle(
                        """
                          def junitVersion = "5.9.1"
                          dependencies {
                              testImplementation "org.junit.jupiter:junit-jupiter:$junitVersion"
                          }
                          """, source -> source.path("build.gradle")),
                storeSource("src/test/java/DynamicGradleTest.java"));
    }

    @Test
    void mixedLiteralAndDynamicGroovyCoordinatesBlockWholeProject() {
        rewriteRun(
                buildGradle(
                        """
                          def junitVersion = "5.10.0"
                          dependencies {
                              testImplementation 'org.junit.jupiter:junit-jupiter:5.9.1'
                              testRuntimeOnly "org.junit.jupiter:junit-jupiter:$junitVersion"
                          }
                          """, source -> source.path("build.gradle")),
                storeSource("src/test/java/MixedDynamicGradleTest.java"));
    }

    @Test
    void kotlinBuildRootIsSelectedButDynamicKotlinCoordinateIsNot() {
        rewriteRun(
                buildGradleKts(
                        "dependencies { testImplementation(\"org.junit.jupiter:junit-jupiter:5.8.2\") }",
                        "dependencies { testImplementation(\"org.junit.jupiter:junit-jupiter:6.0.1\") }",
                        source -> source.path("selected/build.gradle.kts")),
                storeSource(
                        "selected/src/test/java/KotlinSelectedTest.java",
                        """
                          import org.junit.jupiter.api.extension.ExtensionContext;
                          class StoreOnly {
                              Object value(ExtensionContext.Store store) {
                                  return store.computeIfAbsent(String.class);
                              }
                          }
                          """),
                buildGradleKts(
                        """
                          val junitVersion = "5.8.2"
                          dependencies {
                              testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
                          }
                          """, source -> source.path("dynamic/build.gradle.kts")),
                storeSource("dynamic/src/test/java/KotlinDynamicTest.java"));
    }

    @Test
    void mixedLiteralAndDynamicKotlinCoordinatesBlockWholeProject() {
        rewriteRun(
                buildGradleKts(
                        """
                          val junitVersion = "5.10.0"
                          dependencies {
                              testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
                              testRuntimeOnly("org.junit.jupiter:junit-jupiter:$junitVersion")
                          }
                          """, source -> source.path("build.gradle.kts")),
                storeSource("src/test/java/MixedDynamicKotlinTest.java"));
    }

    @Test
    void nearestNestedBuildPreventsParentEligibilityLeakingIntoChild() {
        rewriteRun(
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.9.1"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                storeSource(
                        "src/test/java/ParentTest.java",
                        """
                          import org.junit.jupiter.api.extension.ExtensionContext;
                          class StoreOnly {
                              Object value(ExtensionContext.Store store) {
                                  return store.computeIfAbsent(String.class);
                              }
                          }
                          """),
                xml(UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("child/pom.xml")),
                storeSource("child/src/test/java/ChildTest.java"));
    }

    @Test
    void siblingProjectsRemainIndependent() {
        rewriteRun(
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.8.2"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("selected/pom.xml")),
                storeSource(
                        "selected/src/test/java/SelectedTest.java",
                        """
                          import org.junit.jupiter.api.extension.ExtensionContext;
                          class StoreOnly {
                              Object value(ExtensionContext.Store store) {
                                  return store.computeIfAbsent(String.class);
                              }
                          }
                          """),
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.10.0"), source -> source.path("other/pom.xml")),
                storeSource("other/src/test/java/OtherTest.java"));
    }

    @Test
    void generatedSourcesNeverReceiveProjectMarker() {
        rewriteRun(
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.9.1"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                storeSource("build/generated/GeneratedTest.java"));
    }

    private static Recipe dependencyAndSourceMigration() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitjupiteraggregate")
                .build()
                .activateRecipes(
                        "com.huawei.clouds.openrewrite.junitjupiteraggregate." +
                        "AutoMigrateSelectedJUnitJupiterAggregateTo6_0_1");
    }

    private static org.openrewrite.test.SourceSpecs storeSource(String path) {
        return storeSource(path, null);
    }

    private static org.openrewrite.test.SourceSpecs storeSource(String path, String after) {
        String className = Path.of(path).getFileName().toString().replace(".java", "");
        String before = """
          import org.junit.jupiter.api.extension.ExtensionContext;
          class StoreOnly {
              Object value(ExtensionContext.Store store) {
                  return store.getOrComputeIfAbsent(String.class);
              }
          }
          """.replace("StoreOnly", className);
        return after == null
                ? java(before, source -> source.path(path))
                : java(before, after.replace("StoreOnly", className), source -> source.path(path));
    }
}
