package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateTomcat9JakartaApiDependenciesTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.tomcatcatalina.MigrateTomcat9JakartaApiDependencies";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.tomcatcatalina")
                .build()
                .activateRecipes(RECIPE));
    }

    @Test
    void changesJavaxServletAndElDependenciesToExactTomcat101Apis() {
        rewriteRun(pomXml(
                pom(dependency("javax.servlet", "javax.servlet-api", "4.0.1") +
                    dependency("javax.el", "javax.el-api", "3.0.1-b06")),
                pom(dependency("jakarta.servlet", "jakarta.servlet-api", "6.0.0") +
                    dependency("jakarta.el", "jakarta.el-api", "5.0.1"))));
    }

    @Test
    void upgradesExistingJakartaApisToExactTomcat101Baselines() {
        rewriteRun(pomXml(
                pom(dependency("jakarta.servlet", "jakarta.servlet-api", "5.0.0") +
                    dependency("jakarta.el", "jakarta.el-api", "4.0.0")),
                pom(dependency("jakarta.servlet", "jakarta.servlet-api", "6.0.0") +
                    dependency("jakarta.el", "jakarta.el-api", "5.0.1"))));
    }

    @Test
    void neverDowngradesNewerJakartaApiLines() {
        rewriteRun(pomXml(
                pom(dependency("jakarta.servlet", "jakarta.servlet-api", "6.1.0") +
                    dependency("jakarta.el", "jakarta.el-api", "6.0.0"))));
    }

    @Test
    void changesGradleGroovyDependencies() {
        rewriteRun(
                specification -> specification.beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                          plugins { id 'java-library' }
                          repositories { mavenCentral() }
                          dependencies {
                              compileOnly 'javax.servlet:javax.servlet-api:4.0.1'
                              implementation 'javax.el:javax.el-api:3.0.1-b06'
                          }
                          """,
                        """
                          plugins { id 'java-library' }
                          repositories { mavenCentral() }
                          dependencies {
                              compileOnly 'jakarta.servlet:jakarta.servlet-api:6.0.0'
                              implementation 'jakarta.el:jakarta.el-api:5.0.1'
                          }
                          """));
    }

    @Test
    void upgradesGradleKotlinDependencies() {
        rewriteRun(
                specification -> specification.beforeRecipe(withToolingApi()),
                buildGradleKts(
                        """
                          plugins { `java-library` }
                          repositories { mavenCentral() }
                          dependencies {
                              compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
                              implementation("jakarta.el:jakarta.el-api:4.0.0")
                          }
                          """,
                        """
                          plugins { `java-library` }
                          repositories { mavenCentral() }
                          dependencies {
                              compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")
                              implementation("jakarta.el:jakarta.el-api:5.0.1")
                          }
                          """));
    }

    @Test
    void generatedBuildFilesAreNoop() {
        rewriteRun(pomXml(
                pom(dependency("javax.servlet", "javax.servlet-api", "4.0.1")),
                source -> source.path("target/generated/pom.xml")));
    }

    @Test
    void exactTargetsAreIdempotent() {
        rewriteRun(specification -> specification.cycles(2).expectedCyclesThatMakeChanges(0),
                pomXml(pom(dependency("jakarta.servlet", "jakarta.servlet-api", "6.0.0") +
                              dependency("jakarta.el", "jakarta.el-api", "5.0.1"))));
    }

    private static String pom(String dependencies) {
        return """
          <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>example</groupId>
              <artifactId>app</artifactId>
              <version>1</version>
              <dependencies>
          """ + dependencies + """
              </dependencies>
          </project>
          """;
    }

    private static String dependency(String group, String artifact, String version) {
        return """
                  <dependency>
                      <groupId>%s</groupId>
                      <artifactId>%s</artifactId>
                      <version>%s</version>
                  </dependency>
          """.formatted(group, artifact, version);
    }
}
