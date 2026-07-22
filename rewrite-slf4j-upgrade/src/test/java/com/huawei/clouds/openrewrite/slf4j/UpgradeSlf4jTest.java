package com.huawei.clouds.openrewrite.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeSlf4jTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.slf4j.UpgradeSlf4jApiTo2_0_17";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesDirectMavenDependency() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>logging-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>org.slf4j</groupId>
                              <artifactId>slf4j-api</artifactId>
                              <version>1.7.25</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>logging-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>org.slf4j</groupId>
                              <artifactId>slf4j-api</artifactId>
                              <version>2.0.17</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void upgradesManagedMavenDependencyWithoutChangingProvider() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>managed-logging-app</artifactId>
                          <version>1.0.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>org.slf4j</groupId>
                                <artifactId>slf4j-api</artifactId>
                                <version>2.0.6</version>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>ch.qos.logback</groupId>
                              <artifactId>logback-classic</artifactId>
                              <version>1.4.14</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>managed-logging-app</artifactId>
                          <version>1.0.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>org.slf4j</groupId>
                                <artifactId>slf4j-api</artifactId>
                                <version>2.0.17</version>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                          <dependencies>
                            <dependency>
                              <groupId>ch.qos.logback</groupId>
                              <artifactId>logback-classic</artifactId>
                              <version>1.4.14</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.slf4j")
                .scanYamlResources()
                .build();
    }
}
