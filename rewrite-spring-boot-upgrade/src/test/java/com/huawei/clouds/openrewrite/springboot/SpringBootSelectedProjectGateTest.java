package com.huawei.clouds.openrewrite.springboot;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.config.Environment;
import org.openrewrite.java.spring.SpringConfigFile;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class SpringBootSelectedProjectGateTest implements RewriteTest {
    private static final String PREFIX =
            "com.huawei.clouds.openrewrite.springboot.";

    @Test
    void selectedMavenRootEnablesOfficialConfigurationMigration() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe()),
                xml(pom("2.7.18"), source -> source.path("pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        "server.max-http-request-header-size=16KB",
                        "src/main/resources/application.properties"));
    }

    @Test
    void targetHigherAndUnrelatedProjectsCannotRunAutoConfigurationLeaves() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe()),
                xml(pom("3.5.15"), source -> source.path("target-version/pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        null,
                        "target-version/src/main/resources/application.properties"),
                xml(pom("4.0.0"), source -> source.path("higher-version/pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        null,
                        "higher-version/src/main/resources/application.properties"),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        null,
                        "unrelated/src/main/resources/application.properties"));
    }

    @Test
    void conflictingMavenOwnersBlockEveryAutoLeaf() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe()),
                xml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>2.7.18</version>
                          </parent>
                          <artifactId>app</artifactId>
                          <build><plugins><plugin>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-maven-plugin</artifactId>
                            <version>3.5.15</version>
                          </plugin></plugins></build>
                        </project>
                        """, source -> source.path("pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        null,
                        "src/main/resources/application.properties"));
    }

    @Test
    void nearestNestedBuildRootWins() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe()),
                xml(pom("2.7.18"), source -> source.path("pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        "server.max-http-request-header-size=16KB",
                        "src/main/resources/application.properties"),
                xml(pom("3.5.15"), source -> source.path("module/pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        null,
                        "module/src/main/resources/application.properties"));
    }

    @Test
    void nestedMavenBuildWithoutBootOwnerStopsOuterProjectEligibility() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe()),
                xml(pom("2.7.18"), source -> source.path("pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        "server.max-http-request-header-size=16KB",
                        "src/main/resources/application.properties"),
                xml(plainPom(), source -> source.path("nested/pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        null,
                        "nested/src/main/resources/application.properties"));
    }

    @Test
    void localMavenChildInheritsSelectedParentProjectEligibility() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe()),
                xml(parentPom(), source -> source.path("pom.xml")),
                xml(childPom("../pom.xml", "parent"),
                        source -> source.path("module/pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        "server.max-http-request-header-size=16KB",
                        "module/src/main/resources/application.properties"));
    }

    @Test
    void emptyRelativePathDoesNotClaimAnExternalMavenParent() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe())
                        .expectedCyclesThatMakeChanges(1),
                xml(parentPom(), source -> source.path("pom.xml")),
                xml(childPom("", "parent"),
                        source -> source.path("module/pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        null,
                        "module/src/main/resources/application.properties"));
    }

    @Test
    void mismatchedLocalParentCoordinatesBlockInheritance() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe())
                        .expectedCyclesThatMakeChanges(1),
                xml(parentPom(), source -> source.path("pom.xml")),
                xml(childPom("../pom.xml", "different-parent"),
                        source -> source.path("module/pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        null,
                        "module/src/main/resources/application.properties"));
    }

    @Test
    void missingRelativeParentPomBlocksInheritance() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe())
                        .expectedCyclesThatMakeChanges(1),
                xml(parentPom(), source -> source.path("pom.xml")),
                xml(childPom("../missing.xml", "parent"),
                        source -> source.path("module/pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        null,
                        "module/src/main/resources/application.properties"));
    }

    @Test
    void relativePathToNonPomXmlBlocksInheritance() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe())
                        .expectedCyclesThatMakeChanges(1),
                xml(parentPom(), source -> source.path("pom.xml")),
                xml(parentPom(), source -> source.path("other.xml")),
                xml(childPom("../other.xml", "parent"),
                        source -> source.path("module/pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        null,
                        "module/src/main/resources/application.properties"));
    }

    @Test
    void nestedGradleBuildWithoutBootOwnerStopsOuterProjectEligibility() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                xml(pom("2.7.18"), source -> source.path("pom.xml")),
                text(
                        "Main-Class: org.springframework.boot.loader.JarLauncher",
                        "Main-Class: org.springframework.boot.loader.launch.JarLauncher",
                        source -> source.path("src/main/resources/META-INF/MANIFEST.MF")),
                buildGradle(
                        "plugins { id 'java' }",
                        source -> source.path("nested/build.gradle")),
                text(
                        "Main-Class: org.springframework.boot.loader.JarLauncher",
                        source -> source.path(
                                "nested/src/main/resources/META-INF/MANIFEST.MF")));
    }

    @Test
    void selectedGradlePluginEnablesTheSameProjectGate() {
        rewriteRun(
                spec -> spec.recipe(configurationRecipe()),
                buildGradle(
                        "plugins { id 'org.springframework.boot' version '3.4.12' }",
                        source -> source.path("build.gradle")),
                applicationProperties(
                        "spring.codec.max-in-memory-size=4MB",
                        "spring.http.codecs.max-in-memory-size=4MB",
                        "src/main/resources/application.properties"));
    }

    @Test
    void sourceAndConfigurationRiskMarkersCannotLeakToUnselectedProjects() {
        String key = "spring.main.allow-circular-references=true";
        rewriteRun(
                spec -> spec.recipe(selectedApplicationRisksRecipe()),
                xml(pom("3.4.12"), source -> source.path("selected/pom.xml")),
                properties(
                        key,
                        "~~(" + FindSpringBoot35ConfigurationRisks.CIRCULAR +
                        ")~~>" + key,
                        source -> source.path(
                                "selected/src/main/resources/application.properties")),
                xml(pom("3.5.15"), source -> source.path("target/pom.xml")),
                properties(key, source -> source.path(
                        "target/src/main/resources/application.properties")),
                properties(key, source -> source.path(
                        "unrelated/src/main/resources/application.properties")));
    }

    @Test
    void officialAutoConfigurationScannerCannotGenerateFromHigherProject() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                xml(pom("4.0.0"), source -> source.path("pom.xml")),
                text(
                        """
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\
                        com.acme.web.WebAutoConfiguration
                        """,
                        source -> source.path(
                                "src/main/resources/META-INF/spring.factories")));
    }

    @Test
    void officialAutoConfigurationScannerRunsForSelectedProject() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                xml(pom("2.7.18"), source -> source.path("pom.xml")),
                text(
                        """
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\
                        com.acme.web.WebAutoConfiguration
                        """,
                        null,
                        source -> source.path(
                                "src/main/resources/META-INF/spring.factories")),
                text(
                        null,
                        "com.acme.web.WebAutoConfiguration",
                        source -> source.path(
                                "src/main/resources/META-INF/spring/" +
                                "org.springframework.boot.autoconfigure.AutoConfiguration.imports")));
    }

    @Test
    void recommendedRecipePreservesPreUpgradeEligibilityForEveryAutoBranch() {
        rewriteRun(
                spec -> spec.recipe(recommendedRecipe()),
                xml(
                        pom("2.7.18"),
                        pom("3.5.15"),
                        source -> source.path("pom.xml")),
                applicationProperties(
                        "server.max-http-header-size=16KB",
                        "~~(server.max-http-request-header-size limits request headers only; " +
                        "response header limits require a container-specific customizer)~~>" +
                        "server.max-http-request-header-size=16KB",
                        "src/main/resources/application.properties"),
                text(
                        """
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
                        com.acme.web.WebAutoConfiguration
                        """,
                        null,
                        source -> source.path(
                                "src/main/resources/META-INF/spring.factories")),
                text(
                        null,
                        "com.acme.web.WebAutoConfiguration",
                        source -> source.path(
                                "src/main/resources/META-INF/spring/" +
                                "org.springframework.boot.autoconfigure.AutoConfiguration.imports")));
    }

    private static org.openrewrite.test.SourceSpecs applicationProperties(
            String before, String after, String path) {
        if (after == null) {
            return properties(before, source -> source.path(path)
                    .markers(new SpringConfigFile(Tree.randomId())));
        }
        return properties(before, after, source -> source.path(path)
                .markers(new SpringConfigFile(Tree.randomId())));
    }

    private static Recipe configurationRecipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                PREFIX + "MarkSelectedSpringBootProjects",
                PREFIX + "MigrateSelectedSpringBootConfiguration");
    }

    private static Recipe sourceRecipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                PREFIX + "MarkSelectedSpringBootProjects",
                PREFIX + "MigrateSelectedSpringBootSource");
    }

    private static Recipe recommendedRecipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                PREFIX + "MigrateSpringBootTo3_5_15");
    }

    private static Recipe selectedApplicationRisksRecipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                PREFIX + "MarkSelectedSpringBootProjects",
                PREFIX + "FindSelectedSpringBoot3_5SourceAndConfigurationRisks");
    }

    private static String pom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>app</artifactId>
                  <version>1</version>
                  <dependencies><dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot</artifactId>
                    <version>%s</version>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
    }

    private static String plainPom() {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>nested</artifactId>
                  <version>1</version>
                </project>
                """;
    }

    private static String parentPom() {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <dependencies><dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot</artifactId>
                    <version>2.7.18</version>
                  </dependency></dependencies>
                </project>
                """;
    }

    private static String childPom(String relativePath, String parentArtifact) {
        String relative = relativePath.isEmpty()
                ? "<relativePath/>"
                : "<relativePath>" + relativePath + "</relativePath>";
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>example</groupId>
                    <artifactId>%s</artifactId>
                    <version>1</version>
                    %s
                  </parent>
                  <artifactId>module</artifactId>
                </project>
                """.formatted(parentArtifact, relative);
    }
}
