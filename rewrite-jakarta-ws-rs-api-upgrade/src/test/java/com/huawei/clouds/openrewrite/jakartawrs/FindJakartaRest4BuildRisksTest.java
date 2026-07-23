package com.huawei.clouds.openrewrite.jakartawrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.xml.Assertions.xml;

class FindJakartaRest4BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) { spec.recipe(new FindJakartaRest4BuildRisks()); }

    @ParameterizedTest(name = "unresolved API {0}")
    @ValueSource(strings = {"2.1.6", "3.0.0", "3.1.0", "3.1.1", "${external}", "[3,5)", ""})
    void marksUnresolvedPrimaryVersions(String version) {
        String dependency = version.isEmpty()
                ? "<dependency><groupId>jakarta.ws.rs</groupId><artifactId>jakarta.ws.rs-api</artifactId></dependency>"
                : UpgradeJakartaWsRsApiDependencyTest.dep(version);
        rewriteRun(xml(UpgradeJakartaWsRsApiDependencyTest.project("<dependencies>" + dependency + "</dependencies>"),
                spec -> spec.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("actual property/BOM/catalog"), after::printAll))));
    }

    @Test
    void resolvedTargetLiteralAndPropertyAreNoop() {
        rewriteRun(
                xml(UpgradeJakartaWsRsApiDependencyTest.pom("4.0.0"), spec -> spec.path("pom.xml")),
                xml(UpgradeJakartaWsRsApiDependencyTest.project("<properties><r>4.0.0</r></properties><dependencies>" +
                        UpgradeJakartaWsRsApiDependencyTest.dep("${r}") + "</dependencies>"), spec -> spec.path("nested/pom.xml")));
    }

    @Test
    void duplicateTargetPropertyIsMarkedAsAmbiguousOwnership() {
        rewriteRun(xml(UpgradeJakartaWsRsApiDependencyTest.project(
                "<properties><r>4.0.0</r><r>4.0.0</r></properties><dependencies>" +
                UpgradeJakartaWsRsApiDependencyTest.dep("${r}") + "</dependencies>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("actual property/BOM/catalog"), after::printAll))));
    }

    @Test
    void profilePrimaryDoesNotLeakIntoSiblingBuildPolicy() {
        String jersey = UpgradeJakartaWsRsApiDependencyTest.rawDep(
                "org.glassfish.jersey.core", "jersey-server", "3.1.9", "");
        rewriteRun(xml(UpgradeJakartaWsRsApiDependencyTest.project("""
                <profiles>
                  <profile><id>rest</id><dependencies>%s</dependencies></profile>
                  <profile><id>sibling</id><properties><java.version>11</java.version></properties><dependencies>%s</dependencies></profile>
                </profiles>
                """.formatted(UpgradeJakartaWsRsApiDependencyTest.dep("4.0.0"), jersey)),
                source -> source.path("pom.xml").afterRecipe(after -> {
                    String out = after.printAll();
                    assertFalse(out.contains("requires Java SE 17"), out);
                    assertFalse(out.contains("certified implementation"), out);
                })));
    }

    @Test
    void rootJavaPolicyIsVisibleToProfilePrimary() {
        rewriteRun(xml(UpgradeJakartaWsRsApiDependencyTest.project(
                "<properties><java.version>11</java.version></properties><profiles><profile><id>rest</id><dependencies>" +
                UpgradeJakartaWsRsApiDependencyTest.dep("4.0.0") + "</dependencies></profile></profiles>"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("requires Java SE 17"), after::printAll))));
    }

    @ParameterizedTest(name = "Java baseline {0}")
    @ValueSource(strings = {"1.8", "8", "11", "12", "13", "14", "15", "16"})
    void marksMavenJavaBelow17(String level) {
        rewriteRun(xml(UpgradeJakartaWsRsApiDependencyTest.project(
                "<properties><maven.compiler.release>" + level + "</maven.compiler.release></properties><dependencies>" +
                UpgradeJakartaWsRsApiDependencyTest.dep("4.0.0") + "</dependencies>"),
                spec -> spec.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("requires Java SE 17"), after::printAll))));
    }

    @ParameterizedTest(name = "valid Java {0}")
    @ValueSource(strings = {"17", "18", "21", "22"})
    void acceptsMavenJava17OrNewer(String level) {
        rewriteRun(xml(UpgradeJakartaWsRsApiDependencyTest.project(
                "<properties><maven.compiler.release>" + level + "</maven.compiler.release></properties><dependencies>" +
                UpgradeJakartaWsRsApiDependencyTest.dep("4.0.0") + "</dependencies>"), spec -> spec.path("pom.xml")));
    }

    @ParameterizedTest(name = "incompatible runtime {0}")
    @ValueSource(strings = {"org.glassfish.jersey.core:jersey-common:2.41", "org.glassfish.jersey.core:jersey-server:3.1.9", "org.jboss.resteasy:resteasy-core:4.7.9.Final", "org.jboss.resteasy:resteasy-client:6.2.12.Final"})
    void marksIncompatibleRuntime(String coordinate) {
        String[] p = coordinate.split(":");
        rewriteRun(xml(UpgradeJakartaWsRsApiDependencyTest.project("<dependencies>" +
                UpgradeJakartaWsRsApiDependencyTest.dep("4.0.0") +
                UpgradeJakartaWsRsApiDependencyTest.rawDep(p[0], p[1], p[2], "") + "</dependencies>"),
                spec -> spec.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("certified implementation"), after::printAll))));
    }

    @Test
    void acceptsJersey4AndResteasy7() {
        rewriteRun(xml(UpgradeJakartaWsRsApiDependencyTest.project("<dependencies>" +
                UpgradeJakartaWsRsApiDependencyTest.dep("4.0.0") +
                UpgradeJakartaWsRsApiDependencyTest.rawDep("org.glassfish.jersey.core", "jersey-common", "4.0.0", "") +
                UpgradeJakartaWsRsApiDependencyTest.rawDep("org.jboss.resteasy", "resteasy-core", "7.0.0", "") +
                "</dependencies>"), spec -> spec.path("pom.xml")));
    }

    @Test
    void marksGradleVariableVariantRuntimeAndToolchain() {
        rewriteRun(
                buildGradle("def r='3.1.0'; dependencies { implementation \"jakarta.ws.rs:jakarta.ws.rs-api:$r\" }",
                        spec -> spec.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("property/BOM/catalog"), after::printAll))),
                buildGradle("dependencies { implementation 'jakarta.ws.rs:jakarta.ws.rs-api:3.1.0:tests' }",
                        spec -> spec.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("classifier/non-jar"), after::printAll))),
                buildGradle("dependencies { implementation 'jakarta.ws.rs:jakarta.ws.rs-api:4.0.0'; runtimeOnly 'org.glassfish.jersey.core:jersey-common:3.1.9' }",
                        spec -> spec.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("certified implementation"), after::printAll))),
                buildGradle("java { toolchain { languageVersion = JavaLanguageVersion.of(11) } }; dependencies { implementation 'jakarta.ws.rs:jakarta.ws.rs-api:4.0.0' }",
                        spec -> spec.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("requires Java SE 17"), after::printAll))));
    }

    @Test
    void emptyAndNestedGradleOwnerDoNotActivateBaseline() {
        rewriteRun(
                buildGradle(""),
                buildGradle("java { toolchain { languageVersion = JavaLanguageVersion.of(11) } }; subprojects { dependencies { implementation 'jakarta.ws.rs:jakarta.ws.rs-api:4.0.0' } }"));
    }

    @Test
    void rootGradlePrimaryDoesNotOwnNestedProjectJavaPolicy() {
        rewriteRun(buildGradle("""
                dependencies { implementation 'jakarta.ws.rs:jakarta.ws.rs-api:4.0.0' }
                project(':legacy') { sourceCompatibility = '11' }
                custom { java { toolchain { languageVersion = JavaLanguageVersion.of(8) } } }
                """, source -> source.afterRecipe(after ->
                assertFalse(after.printAll().contains("requires Java SE 17"), after.printAll()))));
    }
}
