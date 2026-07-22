package com.huawei.clouds.openrewrite.fastjson;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeFastjsonTest implements RewriteTest {
    private static final String COMPATIBILITY_RECIPE =
            "com.huawei.clouds.openrewrite.fastjson.UpgradeFastjsonTo2_0_62";
    private static final String NATIVE_API_RECIPE =
            "com.huawei.clouds.openrewrite.fastjson.MigrateFastjson1ToFastjson2Api";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(COMPATIBILITY_RECIPE));
    }

    @Test
    void upgradesCompatibilityArtifactAndPreservesCoordinates() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>fastjson-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.alibaba</groupId>
                              <artifactId>fastjson</artifactId>
                              <version>1.2.83_noneautotype</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>fastjson-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.alibaba</groupId>
                              <artifactId>fastjson</artifactId>
                              <version>2.0.62</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void replacesDependencyWhenNativeApiModeIsSelected() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(NATIVE_API_RECIPE)),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>fastjson-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.alibaba</groupId>
                              <artifactId>fastjson</artifactId>
                              <version>1.2.83</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>fastjson-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>com.alibaba.fastjson2</groupId>
                              <artifactId>fastjson2</artifactId>
                              <version>2.0.62</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void changesPackagesWhenNativeApiModeIsSelected() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(NATIVE_API_RECIPE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package com.alibaba.fastjson;
                                public class JSON {}
                                """,
                                """
                                package com.alibaba.fastjson.annotation;
                                public @interface JSONField {}
                                """
                        )),
                java(
                        """
                        package example;

                        import com.alibaba.fastjson.JSON;
                        import com.alibaba.fastjson.annotation.JSONField;

                        class Payload {
                            @JSONField
                            String value;

                            Class<?> jsonType() {
                                return com.alibaba.fastjson.JSON.class;
                            }
                        }
                        """,
                        """
                        package example;

                        import com.alibaba.fastjson2.JSON;
                        import com.alibaba.fastjson2.annotation.JSONField;

                        class Payload {
                            @JSONField
                            String value;

                            Class<?> jsonType() {
                                return com.alibaba.fastjson2.JSON.class;
                            }
                        }
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = environment();

        for (String recipeName : new String[]{COMPATIBILITY_RECIPE, NATIVE_API_RECIPE}) {
            Recipe recipe = environment.activateRecipes(recipeName);
            assertTrue(environment.listRecipes().stream()
                    .anyMatch(candidate -> recipeName.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        }
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.fastjson")
                .scanYamlResources()
                .build();
    }
}
