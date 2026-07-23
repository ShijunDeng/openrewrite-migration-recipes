package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedJUnitJupiterAggregateMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterAggregateTestApi.sources()))
                .recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitjupiteraggregate")
                        .build()
                        .activateRecipes("com.huawei.clouds.openrewrite.junitjupiteraggregate.MigrateJUnitJupiterTo6_0_1"));
    }

    @Test
    void recommendedRecipeTreeIsDiscoveredAndValid() {
        var recipe = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitjupiteraggregate")
                .build()
                .activateRecipes(
                        "com.huawei.clouds.openrewrite.junitjupiteraggregate.MigrateJUnitJupiterTo6_0_1");
        assertTrue(recipe.validate().isValid(), recipe.validate().toString());
    }

    @Test
    void upgradesDependencyAndMigratesOpenJmlOrdererShapeTogether() {
        rewriteRun(
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.9.1"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
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
                pomXml(UpgradeJUnitJupiterDependencyTest.pom("5.8.2"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
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
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.9.1"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
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
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.9.1"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
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
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.9.3"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                properties(
                        "junit.jupiter.tempdir.scope=per_context\n",
                        "~~(JUnit 6 removed junit.jupiter.tempdir.scope; choose the supported TempDir lifecycle explicitly and verify cleanup, sharing, and parallel-test isolation before deleting this key)~~>junit.jupiter.tempdir.scope=per_context\n",
                        source -> source.path("src/test/resources/junit-platform.properties")));
    }

    @Test
    void configurationIsOnlyMarkedInsideASelectedProject() {
        rewriteRun(
                xml(UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                properties("junit.jupiter.tempdir.scope=per_context\n",
                        source -> source.path("src/test/resources/junit-platform.properties")));
    }

    @Test
    void migratesRemovedPlatformUtilityTypeAndMethodTogether() {
        rewriteRun(
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.8.2"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                java(
                """
                  import org.junit.platform.commons.util.BlacklistedExceptions;
                  class FailureSupport {
                      void rethrow(Throwable failure) { BlacklistedExceptions.rethrowIfBlacklisted(failure); }
                  }
                  """,
                """
                  import org.junit.platform.commons.util.UnrecoverableExceptions;

                  class FailureSupport {
                      void rethrow(Throwable failure) { UnrecoverableExceptions.rethrowIfUnrecoverable(failure); }
                  }
                  """));
    }

    @Test
    void combinesAggregatePlatformAutomaticMigrations() {
        rewriteRun(
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.9.3"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                java(
                """
                  import java.util.Map;
                  import org.junit.platform.engine.ConfigurationParameters;
                  import org.junit.platform.engine.discovery.MethodSelector;
                  import org.junit.platform.engine.reporting.ReportEntry;
                  import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                  class PlatformSupport {
                      int size(ConfigurationParameters p) { return p.size(); }
                      String parameters(MethodSelector selector) { return selector.getMethodParameterTypes(); }
                      Object request() { return new LauncherDiscoveryRequestBuilder(); }
                      ReportEntry report() { return new ReportEntry(); }
                  }
                  """,
                """
                  import java.util.Map;
                  import org.junit.platform.engine.ConfigurationParameters;
                  import org.junit.platform.engine.discovery.MethodSelector;
                  import org.junit.platform.engine.reporting.ReportEntry;
                  import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                  class PlatformSupport {
                      int size(ConfigurationParameters p) { return p.keySet().size(); }
                      String parameters(MethodSelector selector) { return selector.getParameterTypeNames(); }
                      Object request() { return LauncherDiscoveryRequestBuilder.request(); }
                      ReportEntry report() { return ReportEntry.from(Map.of()); }
                  }
                  """));
    }

    @Test
    void upgradesAggregateAndMarksConsoleLauncherDecision() {
        rewriteRun(
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.9.1"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                java(
                        """
                          import org.junit.platform.console.ConsoleLauncher;
                          class ConsoleSupport { void run() { ConsoleLauncher.main("-c", "example.Tests"); } }
                          """,
                        """
                          import org.junit.platform.console.ConsoleLauncher;
                          class ConsoleSupport { void run() { /*~~(JUnit 6 requires a ConsoleLauncher subcommand and conventional option spelling; add execute/discover/engines as intended and replace --h/-help before relying on this invocation)~~>*/ConsoleLauncher.main("-c", "example.Tests"); } }
                          """));
    }

    @Test
    void platformAutomaticMigrationsAreIdempotentInRecommendedRecipe() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.9.1"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
                java(
                """
                  import org.junit.platform.commons.util.PreconditionViolationException;
                  import org.junit.platform.engine.ConfigurationParameters;
                  class PlatformSupport {
                      int size(ConfigurationParameters p) { return p.size(); }
                      RuntimeException failure() { return new PreconditionViolationException("bad"); }
                  }
                  """,
                """
                  import org.junit.platform.commons.PreconditionViolationException;
                  import org.junit.platform.engine.ConfigurationParameters;

                  class PlatformSupport {
                      int size(ConfigurationParameters p) { return p.keySet().size(); }
                      RuntimeException failure() { return new PreconditionViolationException("bad"); }
                  }
                  """));
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossBuildAndSourceChanges() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.8.2"),
                        UpgradeJUnitJupiterDependencyTest.pom("6.0.1"), source -> source.path("pom.xml")),
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
                xml(UpgradeJUnitJupiterDependencyTest.pom("5.9.1"),
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
