package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedTomcatCatalinaMigrationTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcatCatalinaTo10_1_56";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE))
                .parser(JavaParser.fromJavaVersion().dependsOn(TomcatCatalinaTestApi.sources()));
    }

    @Test
    void upgradesDependencyAndMigratesApiInSameRun() {
        rewriteRun(
                xml(UpgradeTomcatCatalinaDependencyTest.pom("10.1.40"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("<version>10.1.56</version>"), after::printAll);
                            assertTrue(after.printAll().contains("interim security target"), after::printAll);
                            assertFalse(after.printAll().contains("<version>10.1.40</version>"), after::printAll);
                        })),
                java("import jakarta.servlet.http.HttpSession; class T { Object user(HttpSession s){return s.getValue(\"user\");} }",
                        "import jakarta.servlet.http.HttpSession; class T { Object user(HttpSession s){return s.getAttribute(\"user\");} }")
        );
    }

    @Test
    void removesListenerAttributeBeforeRiskFinder() {
        rewriteRun(xml(
                "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" gcDaemonProtection=\"true\" appContextProtection=\"true\"/></Server>",
                "<Server><Listener className=\"org.apache.catalina.core.JreMemoryLeakPreventionListener\" appContextProtection=\"true\"/></Server>",
                source -> source.path("conf/server.xml").afterRecipe(after ->
                        assertFalse(after.printAll().contains("has no Tomcat 10.1 setter"), after::printAll))));
    }

    @Test
    void unsafeRemovedApiRemainsVisibleAfterSafeOfficialLeaves() {
        rewriteRun(java(
                "import jakarta.servlet.http.*; class T { Object x(HttpSession s,HttpServletResponse r){ r.encodeUrl(\"/\"); return s.getValueNames(); } }",
                "import jakarta.servlet.http.*; class T { Object x(HttpSession s,HttpServletResponse r){ r.encodeURL(\"/\"); return /*~~(Servlet 6 removed this deprecated Servlet 5 API and there is no syntax-only behavior-preserving replacement; choose the replacement from request/session/error-handling semantics and rebuild against Servlet 6)~~>*/s.getValueNames(); } }"));
    }

    @Test
    void fullClusterRestartMarkerSurvivesDependencyUpgrade() {
        rewriteRun(
                xml(UpgradeTomcatCatalinaDependencyTest.pom("10.1.47"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("<version>10.1.56</version>"), after::printAll);
                            assertTrue(after.printAll().contains("interim security target"), after::printAll);
                        })),
                xml("<Server><Cluster><Interceptor className=\"org.apache.catalina.tribes.group.interceptors.EncryptInterceptor\"/></Cluster></Server>",
                        source -> source.path("conf/server.xml").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("stop every cluster node"), after::printAll)))
        );
    }

    @Test
    void realJfinalShapeAndParameterLimitAreHandledTogether() {
        // Java shape reduced from jfinal/jfinal@a0e9e8b99dc793bcf0cd40ca7feba005ba0c5349.
        rewriteRun(
                java("import jakarta.servlet.http.HttpServletRequest; class JsonRequest { boolean x(HttpServletRequest req){return req.isRequestedSessionIdFromUrl();} }",
                        "import jakarta.servlet.http.HttpServletRequest; class JsonRequest { boolean x(HttpServletRequest req){return req.isRequestedSessionIdFromURL();} }"),
                xml("<Server><Service><Connector port=\"8080\"/></Service></Server>", source -> source.path("server.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("reduced Connector maxParameterCount"), after::printAll)))
        );
    }

    @Test
    void recommendedCompositionOrderIsExact() {
        var recipe = environment().activateRecipes(RECIPE);
        assertEquals(List.of(
                        "com.huawei.clouds.openrewrite.tomcatcatalina.UpgradeTomcatCatalinaTo10_1_56",
                        "com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcat9JakartaApiDependencies",
                        "com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcat9JakartaNamespaces",
                        "com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcatCatalina101Java",
                        "com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcatCatalina101Configuration",
                        "com.huawei.clouds.openrewrite.tomcatcatalina.FindTomcatCatalinaBuildRisks",
                        "com.huawei.clouds.openrewrite.tomcatcatalina.FindTomcatCatalinaJavaRisks",
                        "com.huawei.clouds.openrewrite.tomcatcatalina.FindTomcatCatalinaConfigurationRisks",
                        "com.huawei.clouds.openrewrite.tomcatcatalina.FindTomcatCatalinaResourceRisks"),
                recipe.getRecipeList().stream().map(org.openrewrite.Recipe::getName).toList());
    }

    @Test
    void publicUpgradeAndDirectUpgradeHaveParity() {
        var publicUpgrade = environment().activateRecipes(
                "com.huawei.clouds.openrewrite.tomcatcatalina.UpgradeTomcatCatalinaTo10_1_56");
        assertEquals(List.of(FindTomcatCatalinaBranchTransitionRisks.class.getName(),
                        UpgradeSelectedTomcatCatalinaDependency.class.getName()),
                publicUpgrade.getRecipeList().stream().map(org.openrewrite.Recipe::getName).toList());
        assertEquals(SetHolder.SOURCES, UpgradeSelectedTomcatCatalinaDependency.SOURCE_VERSIONS);
        assertEquals("10.1.56", UpgradeSelectedTomcatCatalinaDependency.TARGET);
    }

    @Test
    void tomcat11ConflictIsMarkedButNeverDowngraded() {
        rewriteRun(xml(UpgradeTomcatCatalinaDependencyTest.pom("11.0.21"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    assertTrue(after.printAll().contains(FindTomcatCatalinaBranchTransitionRisks.TARGET_CONFLICT), after::printAll);
                    assertTrue(after.printAll().contains("<version>11.0.21</version>"), after::printAll);
                    assertFalse(after.printAll().contains("<version>10.1.56</version>"), after::printAll);
                })));
    }

    @Test
    void higherPatchIsMarkedButNeverDowngraded() {
        rewriteRun(xml(UpgradeTomcatCatalinaDependencyTest.pom("10.1.57"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    assertTrue(after.printAll().contains(FindTomcatCatalinaBranchTransitionRisks.TARGET_CONFLICT), after::printAll);
                    assertTrue(after.printAll().contains("<version>10.1.57</version>"), after::printAll);
                    assertFalse(after.printAll().contains("<version>10.1.56</version>"), after::printAll);
                })));
    }

    @Test
    void tomcat9AliasAndNamespaceMigrateTogether() {
        rewriteRun(java(
                "import javax.servlet.http.HttpSession; class T { Object value(HttpSession session){return session.getValue(\"user\");} }",
                "import jakarta.servlet.http.HttpSession; class T { Object value(HttpSession session){return session.getAttribute(\"user\");} }"));
    }

    @Test
    void aggregateIsIdempotentAcrossTwoCycles() {
        rewriteRun(specification -> specification.cycles(2).expectedCyclesThatMakeChanges(1),
                java("import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeUrl(\"/x\");} }",
                        "import jakarta.servlet.http.HttpServletResponse; class T { String x(HttpServletResponse r){return r.encodeURL(\"/x\");} }"),
                xml(UpgradeTomcatCatalinaDependencyTest.pom("10.1.40"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("<version>10.1.56</version>"), after::printAll);
                            assertTrue(after.printAll().contains("interim security target"), after::printAll);
                        })));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.tomcatcatalina",
                                      "org.openrewrite.java.migrate.jakarta")
                .build();
    }

    private static final class SetHolder {
        private static final java.util.Set<String> SOURCES = java.util.Set.of(
                "10.1.40", "10.1.47", "10.1.48", "10.1.52",
                "9.0.98", "9.0.105", "9.0.115", "9.0.117");
    }
}
