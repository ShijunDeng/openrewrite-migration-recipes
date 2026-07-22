package com.huawei.clouds.openrewrite.jcloverslf4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class JclOverSlf4jBuildRiskTest implements RewriteTest {
    @Test
    void marksMissingSlf4j2Provider() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom(dependencies(dep("org.slf4j", "jcl-over-slf4j", "2.0.17"))),
                        source -> source.after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("ServiceLoader<SLF4JServiceProvider>")))));
    }

    @Test
    void acceptsExactlyOneMatchingFirstPartyProvider() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom(dependencies(
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        dep("org.slf4j", "slf4j-simple", "2.0.17")))));
    }

    @Test
    void acceptsSlf4j2CompatibleLogbackProviderVersion() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom(dependencies(
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        dep("ch.qos.logback", "logback-classic", "1.4.14")))));
    }

    @Test
    void marksMismatchedFirstPartyApiAndProvider() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom(dependencies(
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        dep("org.slf4j", "slf4j-api", "2.0.16") +
                        dep("org.slf4j", "slf4j-simple", "2.0.16"))),
                        source -> source.after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("not the workbook target 2.0.17"));
                            assertTrue(printed.contains("not the workbook target 2.0.17; align it"));
                        })));
    }

    @Test
    void acceptsLocallyManagedTargetCoreWithoutFalseOwnershipMarker() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom("<dependencyManagement><dependencies>" +
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        "</dependencies></dependencyManagement><dependencies>" +
                        "<dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId></dependency>" +
                        dep("org.slf4j", "slf4j-simple", "2.0.17") + "</dependencies>")));
    }

    @Test
    void rootManagementAppliesToProfileConsumer() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom("<dependencyManagement><dependencies>" +
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        "</dependencies></dependencyManagement><profiles><profile><id>logging</id><dependencies>" +
                        "<dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId></dependency>" +
                        dep("org.slf4j", "slf4j-simple", "2.0.17") +
                        "</dependencies></profile></profiles>")));
    }

    @Test
    void profileManagementDoesNotLeakToRootConsumer() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom("<dependencyManagement><dependencies>" +
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.16") +
                        "</dependencies></dependencyManagement><dependencies>" +
                        "<dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId></dependency>" +
                        dep("org.slf4j", "slf4j-simple", "2.0.17") + "</dependencies>" +
                        "<profiles><profile><id>managed</id><dependencyManagement><dependencies>" +
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        "</dependencies></dependencyManagement></profile></profiles>"),
                        source -> source.after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("owning BOM/property/catalog")))));
    }

    @Test
    void providerCountsDoNotLeakAcrossProfiles() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom("<profiles>" +
                        "<profile><id>simple</id><dependencies>" +
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        dep("org.slf4j", "slf4j-simple", "2.0.17") + "</dependencies></profile>" +
                        "<profile><id>nop</id><dependencies>" +
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        dep("org.slf4j", "slf4j-nop", "2.0.17") + "</dependencies></profile>" +
                        "</profiles>")));
    }

    @Test
    void marksJclDelegationLoopAtBothCoordinates() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom(dependencies(
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        dep("org.slf4j", "slf4j-jcl", "1.7.36"))),
                        source -> source.after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("StackOverflowError"));
                            assertTrue(printed.contains("slf4j-jcl"));
                        })));
    }

    @ParameterizedTest
    @ValueSource(strings = {"commons-logging:commons-logging:1.2", "org.springframework:spring-jcl:5.3.31"})
    void marksDuplicateJclImplementations(String coordinate) {
        String[] parts = coordinate.split(":");
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom(dependencies(
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        dep(parts[0], parts[1], parts[2]) +
                        dep("org.slf4j", "slf4j-simple", "2.0.17"))),
                        source -> source.after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("duplicate API classes")))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"org.slf4j:slf4j-simple:1.7.35", "ch.qos.logback:logback-classic:1.2.13",
            "org.apache.logging.log4j:log4j-slf4j-impl:2.20.0"})
    void marksOldStaticBinderProviders(String coordinate) {
        String[] parts = coordinate.split(":");
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom(dependencies(
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        dep(parts[0], parts[1], parts[2]))),
                        source -> source.after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("ignored by slf4j-api 2.0")))));
    }

    @Test
    void marksMultipleProviders() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom(dependencies(
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        dep("org.slf4j", "slf4j-simple", "2.0.17") +
                        dep("org.slf4j", "slf4j-nop", "2.0.17"))),
                        source -> source.after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("Multiple SLF4J providers")))));
    }

    @Test
    void marksSlf4jBomAndManagedCoreOwnership() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom("<dependencyManagement><dependencies>" +
                        "<dependency><groupId>org.slf4j</groupId><artifactId>slf4j-bom</artifactId><version>2.0.16</version><type>pom</type><scope>import</scope></dependency>" +
                        "</dependencies></dependencyManagement><dependencies>" +
                        "<dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId></dependency>" +
                        dep("org.slf4j", "slf4j-simple", "2.0.16") + "</dependencies>"),
                        source -> source.after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("owning BOM/property/catalog"));
                            assertTrue(printed.contains("controls API, bridge, and provider"));
                        })));
    }

    @Test
    void marksMavenVariants() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom(dependencies(
                        "<dependency><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId><version>2.0.17</version><classifier>tests</classifier></dependency>")),
                        source -> source.after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("variant-specific")))));
    }

    @Test
    void marksMavenPropertyAndCompilerPluginJava7Baselines() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom("<properties><java.version>1.7</java.version></properties>" +
                        dependencies(dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                                     dep("org.slf4j", "slf4j-simple", "2.0.17")) +
                        "<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><configuration><release>7</release></configuration></plugin></plugins></build>"),
                        source -> source.after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("requires Java 8")))));
    }

    @Test
    void marksDynamicGradleCoreAndOldProvider() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks())
                        .beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradle("plugins { id 'java' }\ndef bridgeVersion = '2.0.17'\ndependencies {\n" +
                        "    implementation \"org.slf4j:jcl-over-slf4j:$bridgeVersion\"\n" +
                        "    runtimeOnly 'org.slf4j:slf4j-simple:1.7.35'\n}\n",
                        source -> source.after(actual -> actual).afterRecipe(unit -> {
                            String printed = unit.printAll();
                            assertTrue(printed.contains("owning BOM/property/catalog"));
                            assertTrue(printed.contains("ignored by slf4j-api 2.0"));
                        })));
    }

    @Test
    void marksGradleVariantAndMultipleProviders() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                buildGradle("plugins { id 'java' }\ndependencies {\n" +
                        "    implementation group: 'org.slf4j', name: 'jcl-over-slf4j', version: '2.0.17', classifier: 'tests'\n" +
                        "    runtimeOnly 'org.slf4j:slf4j-simple:2.0.17'\n" +
                        "    runtimeOnly 'org.slf4j:slf4j-nop:2.0.17'\n}\n",
                        source -> source.after(actual -> actual).afterRecipe(unit -> {
                            String printed = unit.printAll();
                            assertTrue(printed.contains("variant-specific"));
                            assertTrue(printed.contains("Multiple SLF4J providers"));
                        })));
    }

    @Test
    void marksGradleJava7Baseline() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                buildGradle("plugins { id 'java' }\nsourceCompatibility = '1.7'\ndependencies {\n" +
                        "    implementation 'org.slf4j:jcl-over-slf4j:2.0.17'\n" +
                        "    runtimeOnly 'org.slf4j:slf4j-simple:2.0.17'\n}\n",
                        source -> source.after(actual -> actual).afterRecipe(unit ->
                                assertTrue(unit.printAll().contains("requires Java 8")))));
    }

    @Test
    void marksGradleKotlinLoop() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks())
                        .beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradleKts("plugins { java }\ndependencies {\n" +
                        "    implementation(\"org.slf4j:jcl-over-slf4j:2.0.17\")\n" +
                        "    runtimeOnly(\"org.slf4j:slf4j-jcl:1.7.36\")\n}\n",
                        source -> source.after(actual -> actual).afterRecipe(unit ->
                                assertTrue(unit.printAll().contains("StackOverflowError")))));
    }

    @Test
    void ignoresDependencyManagementOnlyProviderTopologyAndGeneratedBuilds() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks()),
                pomXml(pom("<dependencyManagement><dependencies>" +
                        dep("org.slf4j", "jcl-over-slf4j", "2.0.17") +
                        dep("org.slf4j", "slf4j-jcl", "1.7.36") +
                        "</dependencies></dependencyManagement>")),
                buildGradle("plugins { id 'java' }\ndependencies { implementation 'org.slf4j:jcl-over-slf4j:2.0.17' }\n",
                        source -> source.path("generated/build.gradle")));
    }

    @Test
    void searchMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindJclOverSlf4jBuildRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom(dependencies(dep("org.slf4j", "jcl-over-slf4j", "2.0.17"))),
                        source -> source.after(actual -> actual)));
    }

    private static String pom(String body) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" + body + "</project>";
    }

    private static String dependencies(String body) {
        return "<dependencies>" + body + "</dependencies>";
    }

    private static String dep(String group, String artifact, String version) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact + "</artifactId><version>" + version + "</version></dependency>";
    }
}
