package com.huawei.clouds.openrewrite.jasypt;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeJasyptSpringBootStarterTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.jasypt.UpgradeJasyptSpringBootStarterTo4_0_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesDirectMavenDependencyFromOfficialDocumentation() {
        // Adapted from the official starter dependency example:
        // https://github.com/ulisesbocchio/jasypt-spring-boot/blob/jasypt-spring-boot-parent-4.0.3/README.md
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>encrypted-properties-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>2.1.1</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>encrypted-properties-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>4.0.3</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void upgradesMavenVersionPropertyFromPig() {
        // Adapted from pig-mesh/pig at f4e5a3a4:
        // https://github.com/pig-mesh/pig/blob/f4e5a3a4b902dc00c192b878d7587cec93698803/pom.xml
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.pig4cloud</groupId>
                          <artifactId>pig</artifactId>
                          <version>3.9.0</version>
                          <properties>
                            <jasypt.version>3.0.5</jasypt.version>
                          </properties>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>${jasypt.version}</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.pig4cloud</groupId>
                          <artifactId>pig</artifactId>
                          <version>3.9.0</version>
                          <properties>
                            <jasypt.version>4.0.3</jasypt.version>
                          </properties>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>${jasypt.version}</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void upgradesManagedMavenDependencyFromOpenSpg() {
        // Adapted from OpenSPG/openspg at ceeb3ef5:
        // https://github.com/OpenSPG/openspg/blob/ceeb3ef549df79ca4c4878e7ff452c73584991f3/pom.xml
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.antgroup.openspg</groupId>
                          <artifactId>openspg</artifactId>
                          <version>1.0.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>com.github.ulisesbocchio</groupId>
                                <artifactId>jasypt-spring-boot-starter</artifactId>
                                <version>3.0.4</version>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>com.antgroup.openspg</groupId>
                          <artifactId>openspg</artifactId>
                          <version>1.0.0</version>
                          <dependencyManagement>
                            <dependencies>
                              <dependency>
                                <groupId>com.github.ulisesbocchio</groupId>
                                <artifactId>jasypt-spring-boot-starter</artifactId>
                                <version>4.0.3</version>
                              </dependency>
                            </dependencies>
                          </dependencyManagement>
                        </project>
                        """
                )
        );
    }

    @Test
    void upgradesGradleStringNotationFromSgExam() {
        // Adapted from wells2333/sg-exam at 4a7215ac:
        // https://github.com/wells2333/sg-exam/blob/4a7215ace7f56555bc683e4a4c0188f86986fd9f/sg-common/build.gradle
        rewriteRun(
                buildGradle(
                        """
                        plugins {
                            id 'java'
                        }

                        repositories {
                            mavenCentral()
                        }

                        dependencies {
                            implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:1.18'
                        }
                        """,
                        """
                        plugins {
                            id 'java'
                        }

                        repositories {
                            mavenCentral()
                        }

                        dependencies {
                            implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:4.0.3'
                        }
                        """
                )
        );
    }

    @Test
    void upgradesParenthesizedGradleNotationFromCxFlow() {
        // Adapted from checkmarx-ltd/cx-flow at 00b24fa4:
        // https://github.com/checkmarx-ltd/cx-flow/blob/00b24fa410257d154403778f48758d2b474f8977/build.gradle
        rewriteRun(
                buildGradle(
                        """
                        plugins {
                            id 'java'
                        }

                        repositories {
                            mavenCentral()
                        }

                        dependencies {
                            implementation ('com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5')
                        }
                        """,
                        """
                        plugins {
                            id 'java'
                        }

                        repositories {
                            mavenCentral()
                        }

                        dependencies {
                            implementation ('com.github.ulisesbocchio:jasypt-spring-boot-starter:4.0.3')
                        }
                        """
                )
        );
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(
                buildGradle(
                        """
                        plugins {
                            id 'java'
                        }

                        repositories {
                            mavenCentral()
                        }

                        dependencies {
                            implementation group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: '2.1.2'
                        }
                        """,
                        """
                        plugins {
                            id 'java'
                        }

                        repositories {
                            mavenCentral()
                        }

                        dependencies {
                            implementation group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: '4.0.3'
                        }
                        """
                )
        );
    }

    @Test
    void leavesNonStarterArtifactUntouched() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>jasypt-library-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot</artifactId>
                              <version>3.0.5</version>
                            </dependency>
                            <dependency>
                              <groupId>org.jasypt</groupId>
                              <artifactId>jasypt</artifactId>
                              <version>1.9.3</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void leavesMavenPluginUntouched() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>jasypt-plugin-app</artifactId>
                          <version>1.0.0</version>
                          <build>
                            <plugins>
                              <plugin>
                                <groupId>com.github.ulisesbocchio</groupId>
                                <artifactId>jasypt-maven-plugin</artifactId>
                                <version>3.0.5</version>
                              </plugin>
                            </plugins>
                          </build>
                        </project>
                        """
                )
        );
    }

    @Test
    void leavesCurrentVersionUntouched() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>current-jasypt-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>4.0.3</version>
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
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jasypt")
                .scanYamlResources()
                .build();
    }
}
