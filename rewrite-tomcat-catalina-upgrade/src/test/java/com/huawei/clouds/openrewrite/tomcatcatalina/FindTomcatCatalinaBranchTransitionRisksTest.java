package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FindTomcatCatalinaBranchTransitionRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindTomcatCatalinaBranchTransitionRisks());
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.0.98", "9.0.105", "9.0.117"})
    void marksTomcat9NamespaceTransition(String version) {
        rewriteRun(xml(UpgradeTomcatCatalinaDependencyTest.pom(version), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("Java EE javax.* to Jakarta EE jakarta.*"), after::printAll))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.1.57", "11.0.18", "11.0.21"})
    void marksAndPreservesEveryHigherTargetConflict(String version) {
        rewriteRun(xml(UpgradeTomcatCatalinaDependencyTest.pom(version), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindTomcatCatalinaBranchTransitionRisks.TARGET_CONFLICT), after::printAll))));
    }

    @Test
    void resolvesOwnedRootAndProfileProperties() {
        rewriteRun(xml(UpgradeTomcatCatalinaDependencyTest.project(
                        "<properties><rootTomcat>9.0.105</rootTomcat></properties><dependencies>" +
                        UpgradeTomcatCatalinaDependencyTest.dep("${rootTomcat}") + "</dependencies>" +
                        "<profiles><profile><id>it</id><properties><profileTomcat>11.0.18</profileTomcat></properties><dependencies>" +
                        UpgradeTomcatCatalinaDependencyTest.dep("${profileTomcat}") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    assertTrue(after.printAll().contains("Java EE javax.* to Jakarta EE jakarta.*"), after::printAll);
                    assertTrue(after.printAll().contains(FindTomcatCatalinaBranchTransitionRisks.TARGET_CONFLICT), after::printAll);
                })));
    }

    @Test
    void marksRootGradleGroovyKotlinAndMapCoordinates() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.apache.tomcat:tomcat-catalina:9.0.98' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(
                                after.printAll().contains("Java EE javax.* to Jakarta EE jakarta.*"), after::printAll))),
                buildGradleKts("dependencies { implementation(\"org.apache.tomcat:tomcat-catalina:11.0.21\") }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(
                                after.printAll().contains(FindTomcatCatalinaBranchTransitionRisks.TARGET_CONFLICT), after::printAll))),
                buildGradle("dependencies { implementation group: 'org.apache.tomcat', name: 'tomcat-catalina', version: '9.0.115' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(
                                after.printAll().contains("Java EE javax.* to Jakarta EE jakarta.*"), after::printAll)))
        );
    }

    @Test
    void targetPatchOtherCoordinatesDynamicNestedAndGeneratedAreUnmarked() {
        rewriteRun(
                xml(UpgradeTomcatCatalinaDependencyTest.pom("10.1.54"), source -> source.path("pom.xml")),
                xml(UpgradeTomcatCatalinaDependencyTest.project("<dependencies>" +
                        UpgradeTomcatCatalinaDependencyTest.rawDep("example", "tomcat-catalina", "9.0.54", "") +
                        "</dependencies>"), source -> source.path("pom.xml")),
                xml(UpgradeTomcatCatalinaDependencyTest.pom("${tomcat.version}"), source -> source.path("pom.xml")),
                buildGradle("subprojects { dependencies { implementation 'org.apache.tomcat:tomcat-catalina:9.0.54' } }"),
                xml(UpgradeTomcatCatalinaDependencyTest.pom("9.0.54"), source -> source.path("target/pom.xml"))
        );
    }

    @Test
    void publicUpgradeKeepsBranchMarkerAfterVersionRewrite() {
        var recipe = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.tomcatcatalina")
                .build().activateRecipes("com.huawei.clouds.openrewrite.tomcatcatalina.UpgradeTomcatCatalinaTo10_1_56");
        rewriteRun(specification -> specification.recipe(recipe),
                xml(UpgradeTomcatCatalinaDependencyTest.pom("9.0.105"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("Java EE javax.* to Jakarta EE jakarta.*"), after::printAll);
                            assertTrue(after.printAll().contains("<version>10.1.56</version>"), after::printAll);
                            assertFalse(after.printAll().contains("<version>9.0.105</version>"), after::printAll);
                        })));
    }
}
