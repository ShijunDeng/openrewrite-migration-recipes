package com.huawei.clouds.openrewrite.fastjson;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateFastjsonApiTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateFastjsonApi())
                .parser(JavaParser.fromJavaVersion().classpath("fastjson", "jackson-databind"));
    }

    @Test
    void migratesStaticAndContainerApis() {
        rewriteRun(
                java(
                        """
                        package example;

                        import com.alibaba.fastjson.JSON;
                        import com.alibaba.fastjson.JSONArray;
                        import com.alibaba.fastjson.JSONObject;

                        import java.util.List;

                        class Example {
                            String write(User user) {
                                return JSON.toJSONString(user);
                            }

                            User read(String json) {
                                return JSON.parseObject(json, User.class);
                            }

                            List<User> readList(String json) {
                                return JSON.parseArray(json, User.class);
                            }

                            String name(JSONObject object) {
                                return object.getString("name");
                            }

                            User first(JSONArray array) {
                                return array.getObject(0, User.class);
                            }

                            static class User {
                                public String name;
                            }
                        }
                        """,
                        """
                        package example;

                        import com.alibaba.fastjson.JSONArray;
                        import com.alibaba.fastjson.JSONObject;
                        import com.huawei.clouds.openrewrite.fastjson.JacksonJson;

                        import java.util.List;

                        class Example {
                            String write(User user) {
                                return JacksonJson.toJson(user);
                            }

                            User read(String json) {
                                return JacksonJson.fromJson(json, User.class);
                            }

                            List<User> readList(String json) {
                                return JacksonJson.fromJsonList(json, User.class);
                            }

                            String name(JSONObject object) {
                                return JacksonJson.getString(object, "name");
                            }

                            User first(JSONArray array) {
                                return JacksonJson.getObject(array, 0, User.class);
                            }

                            static class User {
                                public String name;
                            }
                        }
                        """
                )
        );
    }

    @Test
    void migratesTreeCreationAndMutation() {
        rewriteRun(
                java(
                        """
                        package example;

                        import com.alibaba.fastjson.JSONArray;
                        import com.alibaba.fastjson.JSONObject;

                        class Trees {
                            JSONObject object() {
                                JSONObject object = new JSONObject();
                                object.put("enabled", true);
                                return object;
                            }

                            JSONArray array() {
                                JSONArray array = new JSONArray();
                                array.add("value");
                                return array;
                            }
                        }
                        """,
                        """
                        package example;

                        import com.alibaba.fastjson.JSONArray;
                        import com.alibaba.fastjson.JSONObject;
                        import com.huawei.clouds.openrewrite.fastjson.JacksonJson;

                        class Trees {
                            JSONObject object() {
                                JSONObject object = JacksonJson.emptyObject();
                                JacksonJson.put(object, "enabled", true);
                                return object;
                            }

                            JSONArray array() {
                                JSONArray array = JacksonJson.emptyArray();
                                JacksonJson.add(array, "value");
                                return array;
                            }
                        }
                        """
                )
        );
    }
}
