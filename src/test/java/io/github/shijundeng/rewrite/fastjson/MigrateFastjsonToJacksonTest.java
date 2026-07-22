package io.github.shijundeng.rewrite.fastjson;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainJava;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateFastjsonToJacksonTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateFastjsonToJackson())
                .parser(JavaParser.fromJavaVersion().classpath("fastjson", "jackson-databind"));
    }

    @Test
    void migratesSourceAndGeneratesFacade() {
        rewriteRun(
                java(
                        """
                        package example;

                        import com.alibaba.fastjson.JSON;
                        import com.alibaba.fastjson.JSONObject;

                        class Example {
                            JSONObject read(String json) {
                                JSONObject object = JSON.parseObject(json);
                                object.put("migrated", true);
                                return object;
                            }
                        }
                        """,
                        """
                        package example;

                        import com.fasterxml.jackson.databind.node.ObjectNode;
                        import io.github.shijundeng.fastjson.JacksonJson;

                        class Example {
                            ObjectNode read(String json) {
                                ObjectNode object = JacksonJson.readObject(json);
                                JacksonJson.put(object, "migrated", true);
                                return object;
                            }
                        }
                        """,
                        spec -> spec.path("src/main/java/example/Example.java")
                ),
                java(
                        null,
                        JacksonJsonSupport.HELPER_SOURCE,
                        spec -> spec.path("src/main/java/" + JacksonJsonSupport.HELPER_RELATIVE_PATH)
                )
        );
    }

    @Test
    void replacesMavenDependencyWhenMigrationIsComplete() {
        rewriteRun(
                mavenProject(
                        "app",
                        pomXml(
                                """
                                <project>
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>example</groupId>
                                  <artifactId>app</artifactId>
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
                                  <artifactId>app</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>com.fasterxml.jackson.core</groupId>
                                      <artifactId>jackson-databind</artifactId>
                                      <version>2.22.1</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """
                        ),
                        srcMainJava(
                                java(
                                        """
                                        package example;

                                        import com.alibaba.fastjson.JSON;

                                        class JsonService {
                                            String write(Object value) {
                                                return JSON.toJSONString(value);
                                            }
                                        }
                                        """,
                                        """
                                        package example;

                                        import io.github.shijundeng.fastjson.JacksonJson;

                                        class JsonService {
                                            String write(Object value) {
                                                return JacksonJson.toJson(value);
                                            }
                                        }
                                        """
                                ),
                                java(
                                        null,
                                        JacksonJsonSupport.HELPER_SOURCE,
                                        spec -> spec.path(JacksonJsonSupport.HELPER_RELATIVE_PATH)
                                )
                        )
                )
        );
    }

    @Test
    void retainsFastjsonWhenUnsupportedUsageRemains() {
        rewriteRun(
                mavenProject(
                        "legacy-app",
                        pomXml(
                                """
                                <project>
                                  <modelVersion>4.0.0</modelVersion>
                                  <groupId>example</groupId>
                                  <artifactId>legacy-app</artifactId>
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
                                  <artifactId>legacy-app</artifactId>
                                  <version>1.0.0</version>
                                  <dependencies>
                                    <dependency>
                                      <groupId>com.alibaba</groupId>
                                      <artifactId>fastjson</artifactId>
                                      <version>1.2.83</version>
                                    </dependency>
                                    <dependency>
                                      <groupId>com.fasterxml.jackson.core</groupId>
                                      <artifactId>jackson-databind</artifactId>
                                      <version>2.22.1</version>
                                    </dependency>
                                  </dependencies>
                                </project>
                                """
                        ),
                        srcMainJava(
                                java(
                                        """
                                        package example;

                                        import com.alibaba.fastjson.JSON;
                                        import com.alibaba.fastjson.serializer.SerializerFeature;

                                        class LegacyJsonService {
                                            String write(Object value) {
                                                return JSON.toJSONString(value, SerializerFeature.PrettyFormat);
                                            }
                                        }
                                        """
                                )
                        )
                )
        );
    }
}
