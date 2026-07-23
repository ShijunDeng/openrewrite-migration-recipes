package com.huawei.clouds.openrewrite.junitplatform;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJUnitPlatform6JavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJUnitPlatform6Java())
                .parser(JavaParser.fromJavaVersion().dependsOn(JUnitPlatformTestApi.sources()));
    }

    @Test
    void replacesRemovedBuilderConstructor() {
        // Reduced from ausbin/circuitsim-grader-template@c3ae77e1db67f24ff370a1df03d33989ab2350d2.
        rewriteRun(java(
                """
                import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                class TesterLauncher {
                    LauncherDiscoveryRequestBuilder request() {
                        return new LauncherDiscoveryRequestBuilder();
                    }
                }
                """,
                """
                import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                class TesterLauncher {
                    LauncherDiscoveryRequestBuilder request() {
                        return LauncherDiscoveryRequestBuilder.request();
                    }
                }
                """));
    }

    @Test
    void replacesFullyQualifiedBuilderConstructorWithoutDuplicatingImports() {
        rewriteRun(java(
                "class Discovery { Object request() { return new org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder(); } }",
                """
                import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;

                class Discovery { Object request() { return LauncherDiscoveryRequestBuilder.request(); } }
                """));
    }

    @Test
    void wrapsStringIdsForMaintainedTestPlanOverload() {
        rewriteRun(java(
                """
                import java.util.Set;
                import org.junit.platform.launcher.TestIdentifier;
                import org.junit.platform.launcher.TestPlan;
                class TreeReader {
                    Set<TestIdentifier> children(TestPlan plan, String uniqueId) {
                        return plan.getChildren(uniqueId);
                    }
                }
                """,
                """
                import java.util.Set;

                import org.junit.platform.engine.UniqueId;
                import org.junit.platform.launcher.TestIdentifier;
                import org.junit.platform.launcher.TestPlan;

                class TreeReader {
                    Set<TestIdentifier> children(TestPlan plan, String uniqueId) {
                        return plan.getChildren(UniqueId.parse(uniqueId));
                    }
                }
                """));
    }

    @Test
    void replacesRemovedEmptyReportEntryConstructor() {
        rewriteRun(java(
                """
                import org.junit.platform.engine.reporting.ReportEntry;
                class Reporter { ReportEntry empty() { return new ReportEntry(); } }
                """,
                """
                import org.junit.platform.engine.reporting.ReportEntry;
                class Reporter { ReportEntry empty() { return ReportEntry.from(java.util.Map.of()); } }
                """));
    }

    @Test
    void handlesMultipleRealLauncherShapesTogether() {
        // Builder factory chaining is used by Atmosphere/atmosphere@b8eb50c4aa3e3c5d22c7c120d9a60b586d10acd1.
        // TestPlan traversal shapes are used by JetBrains/intellij-community@3394a1d40c04cda98c59fc52f6f4d0facdd6bd51.
        rewriteRun(java(
                """
                import org.junit.platform.engine.reporting.ReportEntry;
                import org.junit.platform.launcher.TestPlan;
                import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
                class Integration {
                    Object request() { return new LauncherDiscoveryRequestBuilder(); }
                    Object children(TestPlan plan, String id) { return plan.getChildren(id); }
                    ReportEntry report() { return new ReportEntry(); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String output = after.printAll();
                    org.junit.jupiter.api.Assertions.assertTrue(output.contains("LauncherDiscoveryRequestBuilder.request()"));
                    org.junit.jupiter.api.Assertions.assertTrue(output.contains("getChildren(UniqueId.parse(id))"));
                    org.junit.jupiter.api.Assertions.assertTrue(output.contains("ReportEntry.from(java.util.Map.of())"));
                })));
    }

    @Test
    void targetFormsAndSameNamedBusinessApisAreNoop() {
        rewriteRun(
                java("import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder; " +
                     "class Target { Object x(){ return LauncherDiscoveryRequestBuilder.request(); } }"),
                java("class TestPlan { Object getChildren(String s){return s;} } " +
                     "class Business { Object x(TestPlan p){return p.getChildren(\"id\");} }"),
                java("class ReportEntry {} class BusinessReport { Object x(){return new ReportEntry();} }"));
    }

    @Test
    void generatedAndCacheParentsAreNoop() {
        rewriteRun(
                java("import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder; " +
                     "class Generated { Object x(){return new LauncherDiscoveryRequestBuilder();} }",
                        source -> source.path("generated-code/Generated.java")),
                java("import org.junit.platform.engine.reporting.ReportEntry; " +
                     "class Cached { Object x(){return new ReportEntry();} }",
                        source -> source.path(".gradle/cache/Cached.java")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder; " +
                "class install { Object x(){return new LauncherDiscoveryRequestBuilder();} }",
                "import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder; " +
                "class install { Object x(){return LauncherDiscoveryRequestBuilder.request();} }",
                source -> source.path("install.java")));
    }
}
