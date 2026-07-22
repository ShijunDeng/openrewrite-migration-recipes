package com.huawei.clouds.openrewrite.jcloverslf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;

class JclOverSlf4jRecommendedRecipeTest implements RewriteTest {
    private static final String MIGRATION =
            "com.huawei.clouds.openrewrite.jcloverslf4j.MigrateJclOverSlf4jTo2_0_17";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(JclOverSlf4jDependencyTest.environment().activateRecipes(MIGRATION))
                .typeValidationOptions(TypeValidation.none())
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        "package org.apache.commons.logging; public interface Log {}",
                        "package org.apache.commons.logging; public class LogFactory { public static Log getLog(Class<?> c){return null;} public static LogFactory getFactory(){return null;} }"));
    }

    @Test
    void migratesFamilyOwnedMavenPropertyAndProvider() {
        String before = "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version>" +
                        "<properties><slf4j.version>1.7.36</slf4j.version><maven.compiler.release>8</maven.compiler.release></properties>" +
                        "<dependencies>" + dep("jcl-over-slf4j", "${slf4j.version}") +
                        dep("slf4j-api", "${slf4j.version}") + dep("slf4j-simple", "${slf4j.version}") +
                        "</dependencies></project>";
        String after = before.replace("<slf4j.version>1.7.36</slf4j.version>",
                "<slf4j.version>2.0.17</slf4j.version>");
        rewriteRun(pomXml(before, after));
    }

    @Test
    void migratesPinnedAuctionGatewayGradleFixtureAndMarksMissingProvider() {
        // sba-indoles/auction-apiGateway-server @ ced191a76d65ad83231735892c2c737211148588
        // build.gradle declares slf4j-api 2.0.0, jcl-over-slf4j 1.7.30, and jul-to-slf4j 1.7.30.
        rewriteRun(spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradle("plugins { id 'java' }\ndependencies {\n" +
                        "    implementation 'org.slf4j:slf4j-api:2.0.0'\n" +
                        "    implementation 'org.slf4j:jcl-over-slf4j:1.7.30'\n" +
                        "    implementation 'org.slf4j:jul-to-slf4j:1.7.30'\n}\n",
                        source -> source.path("build.gradle").after(actual -> actual).afterRecipe(unit -> {
                            String printed = unit.printAll();
                            assertTrue(printed.contains("org.slf4j:jcl-over-slf4j:2.0.17"));
                            assertTrue(printed.contains("org.slf4j:jul-to-slf4j:2.0.17"));
                            assertTrue(printed.contains("ServiceLoader<SLF4JServiceProvider>"));
                        })));
    }

    @Test
    void marksOfficialTargetJclDiscoveryContractAtPinnedCommit() {
        // qos-ch/slf4j v_2.0.17 peeled commit c233ea1932228a7fc580823289f896e97ba8a74d
        // jcl-over-slf4j LogFactory.java preserves these constants but documents that discovery is unused.
        rewriteRun(java("""
                class BridgeFactoryContract {
                    static final String FACTORY_PROPERTY = "org.apache.commons.logging.LogFactory";
                    static final String FACTORY_PROPERTIES = "commons-logging.properties";
                    static final String SERVICE_ID = "META-INF/services/org.apache.commons.logging.LogFactory";
                }
                """, source -> source.path("src/main/java/example/BridgeFactoryContract.java")
                .after(actual -> actual).afterRecipe(unit ->
                        assertTrue(unit.printAll().contains("custom-factory discovery are ignored")))));
    }

    @Test
    void marksPinnedCommonsLoggingDiscoveryFixture() {
        // apache/commons-logging 1.2 @ bd26f32b9a24e1c5176da719c95203bba09e401c
        // LogFactory.java performs system-property, service, properties-file, and class-loader discovery.
        rewriteRun(java("""
                class CommonsLoggingDiscovery {
                    static final String FACTORY_PROPERTY = "org.apache.commons.logging.LogFactory";
                    static final String FACTORY_PROPERTIES = "commons-logging.properties";
                    static final String DIAGNOSTICS = "org.apache.commons.logging.diagnostics.dest";
                }
                """, source -> source.path("src/main/java/example/CommonsLoggingDiscovery.java")
                .after(actual -> actual).afterRecipe(unit ->
                        assertTrue(unit.printAll().contains("TCCL, diagnostics")))));
    }

    @Test
    void marksLoopAndProviderServiceTogether() {
        rewriteRun(
                pomXml("<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>loop</artifactId><version>1</version><dependencies>" +
                        dep("jcl-over-slf4j", "1.7.32") + dep("slf4j-jcl", "1.7.36") +
                        "</dependencies></project>", source -> source.after(actual -> actual)
                        .afterRecipe(document -> assertTrue(document.printAll().contains("StackOverflowError")))),
                text("example.CustomProvider\n",
                        source -> source.path("src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider")
                                .after(actual -> actual).afterRecipe(file ->
                                        assertTrue(file.printAll().contains("exactly one implementation")))));
    }

    @Test
    void modernTargetAndPublicJclApiAreNoOp() {
        rewriteRun(
                pomXml("<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>modern</artifactId><version>1</version><properties><maven.compiler.release>17</maven.compiler.release></properties><dependencies>" +
                        dep("jcl-over-slf4j", "2.0.17") + dep("slf4j-simple", "2.0.17") +
                        "</dependencies></project>"),
                java("""
                        import org.apache.commons.logging.Log;
                        import org.apache.commons.logging.LogFactory;
                        class Modern {
                            Log log = LogFactory.getLog(Modern.class);
                        }
                        """));
    }

    @Test
    void followsPinnedOpenRewriteMavenGradleShapesAndIsDiscoverableAndIdempotent() {
        // openrewrite/rewrite @ d4ac42ebd579b96bf9aa19ad04a8f545175f7abc
        // rewrite-maven RemoveDependencyTest and rewrite-gradle ChangeDependencyTest fix root/profile/DM and Gradle call shapes.
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml("<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>idempotent</artifactId><version>1</version>" +
                        "<properties><slf4j.version>1.7.30</slf4j.version><java.version>8</java.version></properties><dependencies>" +
                        dep("jcl-over-slf4j", "${slf4j.version}") + dep("slf4j-api", "${slf4j.version}") +
                        dep("slf4j-simple", "${slf4j.version}") + "</dependencies></project>",
                        source -> source.after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("<slf4j.version>2.0.17</slf4j.version>")))));
        Recipe recipe = JclOverSlf4jDependencyTest.environment().activateRecipes(MIGRATION);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(JclOverSlf4jDependencyTest.environment().listRecipes().stream()
                .anyMatch(candidate -> MIGRATION.equals(candidate.getName())));
    }

    private static String dep(String artifact, String version) {
        return "<dependency><groupId>org.slf4j</groupId><artifactId>" + artifact + "</artifactId><version>" + version + "</version></dependency>";
    }
}
