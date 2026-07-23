package com.huawei.clouds.openrewrite.junitplatform;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindJUnitPlatform6SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJUnitPlatform6SourceRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(JUnitPlatformTestApi.sources()));
    }

    @Test
    void marksOnlyRemovedTestPlanMutation() {
        rewriteRun(java(
                """
                import org.junit.platform.launcher.TestIdentifier;
                import org.junit.platform.launcher.TestPlan;
                class ToolAdapter { void register(TestPlan plan, TestIdentifier id) { plan.add(id); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindJUnitPlatform6SourceRisks.TEST_PLAN_ADD)))));
    }

    @Test
    void overloadsAndBusinessMethodsAreNoop() {
        rewriteRun(
                java("import org.junit.platform.launcher.*; class Use { Object x(TestPlan p, TestIdentifier id){ return p.getChildren(id); } }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                java("class BusinessUse { static class BusinessPlan { void add(Object id){} } " +
                     "void x(BusinessPlan p){p.add(null);} }",
                        source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void generatedFilesAreNoopAndMarkersAreIdempotent() {
        rewriteRun(java("import org.junit.platform.launcher.*; class Generated { void x(TestPlan p, TestIdentifier i){p.add(i);} }",
                source -> source.path("target/generated/Generated.java").afterRecipe(after -> assertNoMarker(after.printAll()))));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import org.junit.platform.launcher.*; class Tool { void x(TestPlan p, TestIdentifier i){p.add(i);} }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindJUnitPlatform6SourceRisks.TEST_PLAN_ADD, 1))));
    }

    private static void assertNoMarker(String output) {
        assertFalse(output.contains("/*~~("), output);
    }

    private static void assertCount(String output, String value, int expected) {
        int count = 0;
        for (int at = 0; (at = output.indexOf(value, at)) >= 0; at += value.length()) count++;
        assertTrue(count == expected, output);
    }
}
