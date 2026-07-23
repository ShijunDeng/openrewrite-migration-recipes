package com.huawei.clouds.openrewrite.junitjupiter;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedJUnitJupiterMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterTestApi.sources()))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitjupiter")
                        .build()
                        .activateRecipes("com.huawei.clouds.openrewrite.junitjupiter.MigrateJUnitJupiterApiTo6_0_1"));
    }

    @Test
    void upgradesDependencyAndMigratesOpenJmlOrdererShapeTogether() {
        rewriteRun(
                pomXml(UpgradeJUnitJupiterApiDependencyTest.pom("5.7.1"),
                        UpgradeJUnitJupiterApiDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                java(
                        """
                          import org.junit.jupiter.api.TestMethodOrder;
                          @TestMethodOrder(org.junit.jupiter.api.MethodOrderer.Alphanumeric.class)
                          class AllTests {}
                          """,
                        """
                          import org.junit.jupiter.api.TestMethodOrder;
                          @TestMethodOrder(org.junit.jupiter.api.MethodOrderer.MethodName.class)
                          class AllTests {}
                          """));
    }

    @Test
    void upgradesDependencyAndMigratesApacheHiveStoreShapeTogether() {
        rewriteRun(
                xml(UpgradeJUnitJupiterApiDependencyTest.pom("5.8.2"),
                        UpgradeJUnitJupiterApiDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                java(
                        """
                          import java.util.ArrayList;
                          import java.util.List;
                          import org.junit.jupiter.api.extension.ExtensionContext;
                          class DoNothingTCPServerExtension {
                              Object server(ExtensionContext context) {
                                  return context.getStore(ExtensionContext.Namespace.GLOBAL)
                                          .getOrComputeIfAbsent(context.getUniqueId(), id -> new ArrayList<String>());
                              }
                          }
                          """,
                        """
                          import java.util.ArrayList;
                          import java.util.List;
                          import org.junit.jupiter.api.extension.ExtensionContext;
                          class DoNothingTCPServerExtension {
                              Object server(ExtensionContext context) {
                                  return context.getStore(ExtensionContext.Namespace.GLOBAL)
                                          .computeIfAbsent(context.getUniqueId(), id -> new ArrayList<String>());
                              }
                          }
                          """));
    }

    @Test
    void adaptsOldDynamicInterceptorWithoutDiscardingItsBody() {
        rewriteRun(
                xml(UpgradeJUnitJupiterApiDependencyTest.pom("5.9.3"),
                        UpgradeJUnitJupiterApiDependencyTest.pom("6.0.1"),
                        source -> source.path("pom.xml")),
                java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  import org.junit.jupiter.api.extension.InvocationInterceptor;
                  class TimingInterceptor implements InvocationInterceptor {
                      @Override
                      public void interceptDynamicTest(Invocation<Void> invocation, ExtensionContext context) throws Throwable {
                          invocation.proceed();
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  import org.junit.jupiter.api.extension.InvocationInterceptor;

                  class TimingInterceptor implements InvocationInterceptor {
                      @Override
                      public void interceptDynamicTest(Invocation<Void> invocation, DynamicTestInvocationContext invocationContext, ExtensionContext context) throws Throwable {
                          invocation.proceed();
                      }
                  }
                  """));
    }

    @Test
    void combinesAutomaticMigrationWithSourceReviewMarkers() {
        rewriteRun(
                xml(UpgradeJUnitJupiterApiDependencyTest.pom("5.7.1"),
                        UpgradeJUnitJupiterApiDependencyTest.pom("6.0.1"),
                        source -> source.path("pom.xml")),
                java(
                """
                  import org.junit.jupiter.api.MethodOrderer;
                  import org.junit.jupiter.api.Nested;
                  import org.junit.jupiter.api.TestMethodOrder;
                  @TestMethodOrder(MethodOrderer.Alphanumeric.class)
                  class Outer {
                      @Nested class First {}
                  }
                  """,
                """
                  import org.junit.jupiter.api.MethodOrderer;
                  import org.junit.jupiter.api.Nested;
                  import org.junit.jupiter.api.TestMethodOrder;
                  @TestMethodOrder(MethodOrderer.MethodName.class)
                  class Outer {
                      /*~~(JUnit 6 deterministically reorders sibling @Nested classes and inherits @TestMethodOrder into nested classes; review stateful/order-sensitive tests and add explicit orderers where order is contractual)~~>*/@Nested class First {}
                  }
                  """));
    }

    @Test
    void combinesDependencyMigrationWithConfigurationReviewMarker() {
        rewriteRun(
                xml(UpgradeJUnitJupiterApiDependencyTest.pom("5.9.3"),
                        UpgradeJUnitJupiterApiDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                properties(
                        "junit.jupiter.tempdir.scope=per_context\n",
                        "~~(JUnit 6 removed junit.jupiter.tempdir.scope; choose the supported TempDir lifecycle explicitly and verify cleanup, sharing, and parallel-test isolation before deleting this key)~~>junit.jupiter.tempdir.scope=per_context\n",
                        source -> source.path("src/test/resources/junit-platform.properties")));
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossBuildAndSourceChanges() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeJUnitJupiterApiDependencyTest.pom("5.8.2"),
                        UpgradeJUnitJupiterApiDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                java(
                        """
                          import org.junit.jupiter.api.MethodOrderer;
                          import org.junit.jupiter.api.TestMethodOrder;
                          import org.junit.jupiter.api.extension.ExtensionContext;
                          @TestMethodOrder(MethodOrderer.Alphanumeric.class)
                          class Extension {
                              Object value(ExtensionContext.Store store) {
                                  return store.getOrComputeIfAbsent(String.class);
                              }
                          }
                          """,
                        """
                          import org.junit.jupiter.api.MethodOrderer;
                          import org.junit.jupiter.api.TestMethodOrder;
                          import org.junit.jupiter.api.extension.ExtensionContext;
                          @TestMethodOrder(MethodOrderer.MethodName.class)
                          class Extension {
                              Object value(ExtensionContext.Store store) {
                                  return store.computeIfAbsent(String.class);
                              }
                          }
                          """));
    }

    @Test
    void generatedBuildAndSourceRemainUntouched() {
        rewriteRun(
                xml(UpgradeJUnitJupiterApiDependencyTest.pom("5.7.1"),
                        source -> source.path("target/generated/pom.xml")),
                java(
                        """
                          import org.junit.jupiter.api.MethodOrderer;
                          import org.junit.jupiter.api.TestMethodOrder;
                          @TestMethodOrder(MethodOrderer.Alphanumeric.class)
                          class GeneratedTests {}
                          """, source -> source.path("build/generated/GeneratedTests.java")));
    }
}
