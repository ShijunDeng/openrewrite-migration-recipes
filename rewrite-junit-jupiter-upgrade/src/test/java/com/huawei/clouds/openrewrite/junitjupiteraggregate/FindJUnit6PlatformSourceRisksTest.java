package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindJUnit6PlatformSourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterAggregateTestApi.sources()))
                .recipe(new FindJUnit6PlatformSourceRisks());
    }

    @ParameterizedTest(name = "removed type {0}")
    @MethodSource("removedTypes")
    void marksRemovedPlatformTypeImports(String type, String message) {
        String simple = type.substring(type.lastIndexOf('.') + 1);
        rewriteRun(java("import " + type + ";\nclass UsesRemoved { " + simple + " value; }\n",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> removedTypes() {
        return Stream.of(
                Arguments.of("org.junit.platform.engine.support.filter.ClasspathScanningSupport",
                        "JUnit 6 removed ClasspathScanningSupport"),
                Arguments.of("org.junit.platform.engine.support.hierarchical.SingleTestExecutor",
                        "JUnit 6 removed SingleTestExecutor"),
                Arguments.of("org.junit.platform.launcher.listeners.LegacyReportingUtils",
                        "JUnit 6 removed launcher.listeners.LegacyReportingUtils"),
                Arguments.of("org.junit.platform.suite.api.UseTechnicalNames",
                        "JUnit 6 removed @UseTechnicalNames"));
    }

    @Test
    void marksRemovedTestPlanMutation() {
        rewriteRun(java(
                """
                  import org.junit.platform.launcher.TestIdentifier;
                  import org.junit.platform.launcher.TestPlan;
                  class PlanSupport { void add(TestPlan plan, TestIdentifier id) { plan.add(id); } }
                  """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJUnit6PlatformSourceRisks.TEST_PLAN))));
    }

    @Test
    void marksRemovedEngineTestKitExecuteOverload() {
        rewriteRun(java(
                """
                  import org.junit.platform.testkit.engine.EngineTestKit;
                  import org.junit.platform.engine.EngineDiscoveryRequest;
                  class KitSupport { Object run(EngineDiscoveryRequest request) { return EngineTestKit.execute("engine", request); } }
                  """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJUnit6PlatformSourceRisks.ENGINE_TEST_KIT))));
    }

    @Test
    void maintainedEngineTestKitLauncherRequestOverloadIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.platform.testkit.engine.EngineTestKit;
                  import org.junit.platform.launcher.LauncherDiscoveryRequest;
                  class KitSupport { Object run(LauncherDiscoveryRequest request) { return EngineTestKit.execute("engine", request); } }
                  """, source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksReportEntryConstructorReferenceButNotDirectFactory() {
        rewriteRun(java(
                """
                  import java.util.function.Supplier;
                  import org.junit.platform.engine.reporting.ReportEntry;
                  class Reporter { Supplier<ReportEntry> factory = ReportEntry::new; }
                  """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJUnit6PlatformSourceRisks.REPORT_REFERENCE))),
                java(
                """
                  import java.util.Map;
                  import org.junit.platform.engine.reporting.ReportEntry;
                  class MaintainedReporter { ReportEntry entry = ReportEntry.from(Map.of()); }
                  """, source -> source.path("Maintained.java").afterRecipe(after ->
                        assertNoMarker(after.printAll()))));
    }

    @Test
    void marksRemovedTempDirConstants() {
        rewriteRun(
                java(
                        """
                          import org.junit.jupiter.api.io.TempDir;
                          class Config { String key = TempDir.SCOPE_PROPERTY_NAME; }
                          """, source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnit6PlatformSourceRisks.TEMPDIR_CONSTANT))),
                java(
                        """
                          import static org.junit.jupiter.engine.Constants.TEMP_DIR_SCOPE_PROPERTY_NAME;
                          class EngineConfig { String key = TEMP_DIR_SCOPE_PROPERTY_NAME; }
                          """, source -> source.path("EngineConfig.java").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnit6PlatformSourceRisks.TEMPDIR_CONSTANT))));
    }

    @Test
    void marksQuarkiverseConsoleCallWithoutSubcommand() {
        rewriteRun(java(
                """
                  import org.junit.platform.console.ConsoleLauncher;
                  class CucumberQuarkusTest {
                      void run(String testClass) { ConsoleLauncher.main("-c", testClass); }
                  }
                  """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindJUnit6PlatformSourceRisks.CONSOLE_SUBCOMMAND))));
    }

    @Test
    void marksDynamicAndLegacyConsoleArguments() {
        rewriteRun(
                java(
                        """
                          import org.junit.platform.console.ConsoleLauncher;
                          class DynamicConsole { void run(String[] args) { ConsoleLauncher.main(args); } }
                          """, source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnit6PlatformSourceRisks.CONSOLE_SUBCOMMAND))),
                java(
                        """
                          import org.junit.platform.console.ConsoleLauncher;
                          class LegacyHelp { void run() { ConsoleLauncher.main("execute", "--h"); } }
                          """, source -> source.path("LegacyHelp.java").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnit6PlatformSourceRisks.CONSOLE_SUBCOMMAND))));
    }

    @Test
    void maintainedConsoleSubcommandIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.platform.console.ConsoleLauncher;
                  class Console { void run() { ConsoleLauncher.main("execute", "--select-class", "example.Tests"); } }
                  """, source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksRemovedReflectionUtilsMethods() {
        rewriteRun(
                java(
                        """
                          import java.lang.reflect.Field;
                          import org.junit.platform.commons.util.ReflectionUtils;
                          class FieldReader { Object read(Field field, Object target) { return ReflectionUtils.readFieldValue(field, target); } }
                          """, source -> source.after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnit6PlatformSourceRisks.REFLECTION_UTILS))),
                java(
                        """
                          import org.junit.platform.commons.util.ReflectionUtils;
                          class MethodReader { Object read() { return ReflectionUtils.getMethod(String.class, "length"); } }
                          """, source -> source.path("MethodReader.java").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), FindJUnit6PlatformSourceRisks.REFLECTION_UTILS))));
    }

    @Test
    void sameNamedBusinessTypesAndFieldsAreNoop() {
        rewriteRun(java(
                "class TempDir { static final String SCOPE_PROPERTY_NAME = \"x\"; } class Business { String key = TempDir.SCOPE_PROPERTY_NAME; }",
                source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void generatedSourceIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.platform.console.ConsoleLauncher;
                  class Generated { void run() { ConsoleLauncher.main("-c", "example.Tests"); } }
                  """, source -> source.path("target/generated/Generated.java").afterRecipe(after ->
                        assertNoMarker(after.printAll()))));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), actual);
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~("), actual);
    }
}
