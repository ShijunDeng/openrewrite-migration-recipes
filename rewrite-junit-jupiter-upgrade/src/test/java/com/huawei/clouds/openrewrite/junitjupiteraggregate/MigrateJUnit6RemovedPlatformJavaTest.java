package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJUnit6RemovedPlatformJavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterAggregateTestApi.sources()))
                .recipe(new MigrateJUnit6RemovedPlatformJava());
    }

    @Test
    void migratesConfigurationSizeFromWireShape() {
        rewriteRun(java(
                """
                  import org.junit.platform.engine.ConfigurationParameters;
                  class EngineContext {
                      boolean hasConfiguration(ConfigurationParameters parameters) { return parameters.size() > 0; }
                  }
                  """,
                """
                  import org.junit.platform.engine.ConfigurationParameters;
                  class EngineContext {
                      boolean hasConfiguration(ConfigurationParameters parameters) { return parameters.keySet().size() > 0; }
                  }
                  """));
    }

    @Test
    void migratesLauncherBuilderConstructorFromQuarkusShape() {
        rewriteRun(java(
                """
                  import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                  class LauncherFactory { Object request() { return new LauncherDiscoveryRequestBuilder(); } }
                  """,
                """
                  import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                  class LauncherFactory { Object request() { return LauncherDiscoveryRequestBuilder.request(); } }
                  """));
    }

    @Test
    void migratesEmptyReportEntryToFactory() {
        rewriteRun(java(
                """
                  import java.util.Map;
                  import org.junit.platform.engine.reporting.ReportEntry;
                  class Reporter { ReportEntry empty() { return new ReportEntry(); } }
                  """,
                """
                  import java.util.Map;
                  import org.junit.platform.engine.reporting.ReportEntry;
                  class Reporter { ReportEntry empty() { return ReportEntry.from(Map.of()); } }
                  """));
    }

    @Test
    void migratesReflectionSupportLoadClassWithoutChangingOptionalContract() {
        rewriteRun(java(
                """
                  import java.util.Optional;
                  import org.junit.platform.commons.support.ReflectionSupport;
                  class Loader { Optional<Class<?>> load(String name) { return ReflectionSupport.loadClass(name); } }
                  """,
                """
                  import java.util.Optional;
                  import org.junit.platform.commons.support.ReflectionSupport;
                  class Loader { Optional<Class<?>> load(String name) { return ReflectionSupport.tryToLoadClass(name).toOptional(); } }
                  """));
    }

    @Test
    void wrapsTestPlanStringIdsWithUniqueIdParse() {
        rewriteRun(java(
                """
                  import org.junit.platform.launcher.TestPlan;
                  class PlanSupport {
                      Object children(TestPlan plan, String id) { return plan.getChildren(id); }
                      Object identifier(TestPlan plan, String id) { return plan.getTestIdentifier(id); }
                  }
                  """,
                """
                  import org.junit.platform.engine.UniqueId;
                  import org.junit.platform.launcher.TestPlan;

                  class PlanSupport {
                      Object children(TestPlan plan, String id) { return plan.getChildren(UniqueId.parse(id)); }
                      Object identifier(TestPlan plan, String id) { return plan.getTestIdentifier(UniqueId.parse(id)); }
                  }
                  """));
    }

    @Test
    void maintainedMethodsAreNoop() {
        rewriteRun(java(
                """
                  import java.util.Map;
                  import org.junit.platform.engine.ConfigurationParameters;
                  import org.junit.platform.engine.discovery.MethodSelector;
                  import org.junit.platform.engine.reporting.ReportEntry;
                  import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                  class Maintained {
                      int size(ConfigurationParameters p) { return p.keySet().size(); }
                      String names(MethodSelector s) { return s.getParameterTypeNames(); }
                      Object request() { return LauncherDiscoveryRequestBuilder.request(); }
                      ReportEntry report() { return ReportEntry.from(Map.of()); }
                  }
                  """));
    }

    @Test
    void sameNamedBusinessApisAreNoop() {
        rewriteRun(java(
                "class ConfigurationParameters { int size(){return 0;} } class LauncherDiscoveryRequestBuilder {} class Business { Object x(){return new LauncherDiscoveryRequestBuilder();} }"));
    }

    @Test
    void generatedSourceIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.platform.engine.ConfigurationParameters;
                  class Generated { int size(ConfigurationParameters p) { return p.size(); } }
                  """, source -> source.path("build/generated/Generated.java")));
    }

    @Test
    void allPlatformChangesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                  import java.util.Map;
                  import org.junit.platform.engine.ConfigurationParameters;
                  import org.junit.platform.engine.reporting.ReportEntry;
                  import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                  class PlatformSupport {
                      int size(ConfigurationParameters p) { return p.size(); }
                      Object request() { return new LauncherDiscoveryRequestBuilder(); }
                      ReportEntry report() { return new ReportEntry(); }
                  }
                  """,
                """
                  import java.util.Map;
                  import org.junit.platform.engine.ConfigurationParameters;
                  import org.junit.platform.engine.reporting.ReportEntry;
                  import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                  class PlatformSupport {
                      int size(ConfigurationParameters p) { return p.keySet().size(); }
                      Object request() { return LauncherDiscoveryRequestBuilder.request(); }
                      ReportEntry report() { return ReportEntry.from(Map.of()); }
                  }
                  """));
    }
}
