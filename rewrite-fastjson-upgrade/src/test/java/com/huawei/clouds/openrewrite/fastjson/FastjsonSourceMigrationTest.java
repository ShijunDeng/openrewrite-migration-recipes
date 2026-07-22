package com.huawei.clouds.openrewrite.fastjson;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FastjsonSourceMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateFastjsonCompatibilitySource())
                .parser(JavaParser.fromJavaVersion().classpath("fastjson"));
    }

    @Test
    void migratesParserConfigDeserializerPutFromAlibabaOtter() {
        // Reduced from alibaba/otter at 7544d0515e832b188736cc6d882d5a7da0558a55:
        // https://github.com/alibaba/otter/blob/7544d0515e832b188736cc6d882d5a7da0558a55/shared/common/src/main/java/com/alibaba/otter/shared/common/utils/JsonUtils.java#L62-L72
        rewriteRun(java(
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
                class JsonUtils {
                    void register(ObjectDeserializer reader) {
                        ParserConfig.getGlobalInstance().getDeserializers().put(String.class, reader);
                    }
                }
                """,
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
                class JsonUtils {
                    void register(ObjectDeserializer reader) {
                        ParserConfig.getGlobalInstance().putDeserializer(String.class, reader);
                    }
                }
                """
        ));
    }

    @Test
    void migratesParserConfigDeserializerGet() {
        rewriteRun(java(
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                class Registry {
                    Object lookup() {
                        return ParserConfig.getGlobalInstance().getDeserializers().get(String.class);
                    }
                }
                """,
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                class Registry {
                    Object lookup() {
                        return ParserConfig.getGlobalInstance().getDeserializer(String.class);
                    }
                }
                """
        ));
    }

    @Test
    void migratesWriteJsonStringToAndReordersArguments() {
        // Call shape occurs in alipay/SoloPi at 35a4a3e3fe02deeb89df35c82dc3ba03a33f4f13:
        // https://github.com/alipay/SoloPi/blob/35a4a3e3fe02deeb89df35c82dc3ba03a33f4f13/src/app/src/main/java/com/alipay/hulu/activity/CaseReplayResultActivity.java
        rewriteRun(java(
                """
                import com.alibaba.fastjson.JSON;
                import com.alibaba.fastjson.serializer.SerializerFeature;
                import java.io.Writer;
                class Output {
                    void write(Object value, Writer writer) {
                        JSON.writeJSONStringTo(value, writer, SerializerFeature.PrettyFormat);
                    }
                }
                """,
                """
                import com.alibaba.fastjson.JSON;
                import com.alibaba.fastjson.serializer.SerializerFeature;
                import java.io.Writer;
                class Output {
                    void write(Object value, Writer writer) {
                        JSON.writeJSONString(writer, value, SerializerFeature.PrettyFormat);
                    }
                }
                """
        ));
    }

    @Test
    void leavesOrdinaryMapPutAndGetUntouched() {
        rewriteRun(java("""
                import java.util.HashMap;
                import java.util.Map;
                class OrdinaryMap {
                    final Map<Class<?>, Object> values = new HashMap<>();
                    void put(Object value) { values.put(String.class, value); }
                    Object get() { return values.get(String.class); }
                }
                """));
    }

    @Test
    void leavesAlreadyMigratedParserConfigApisUntouched() {
        rewriteRun(java("""
                import com.alibaba.fastjson.parser.ParserConfig;
                import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
                class Registry {
                    void register(ObjectDeserializer reader) {
                        ParserConfig.getGlobalInstance().putDeserializer(String.class, reader);
                    }
                }
                """));
    }

    @Test
    void leavesConsumedDeserializerPutAndGeneratedSourceUntouched() {
        rewriteRun(
                java("""
                        import com.alibaba.fastjson.parser.ParserConfig;
                        import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
                        class Registry {
                            ObjectDeserializer replace(ObjectDeserializer reader) {
                                return ParserConfig.getGlobalInstance().getDeserializers().put(String.class, reader);
                            }
                        }
                        """, source -> source.path("src/main/java/example/Registry.java")),
                java("""
                        import com.alibaba.fastjson.parser.ParserConfig;
                        import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
                        class GeneratedRegistry {
                            void register(ObjectDeserializer reader) {
                                ParserConfig.getGlobalInstance().getDeserializers().put(String.class, reader);
                            }
                        }
                        """, source -> source.path("target/generated-sources/GeneratedRegistry.java"))
        );
    }

    @Test
    void nativeRecipeMovesOnlyOneToOneCoreAndAnnotationTypes() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder()
                                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.fastjson")
                                .scanYamlResources().build()
                                .activateRecipes("com.huawei.clouds.openrewrite.fastjson.MigrateFastjson1ToFastjson2Api"))
                        .parser(JavaParser.fromJavaVersion().classpath("fastjson", "fastjson2")),
                java(
                        """
                        import com.alibaba.fastjson.JSON;
                        import com.alibaba.fastjson.JSONObject;
                        import com.alibaba.fastjson.TypeReference;
                        import com.alibaba.fastjson.annotation.JSONField;
                        import java.util.Map;
                        class Payload {
                            @JSONField(name = "id") String id;
                            String write(JSONObject value) { return JSON.toJSONString(value); }
                            TypeReference<Map<String, String>> type() { return new TypeReference<>() {}; }
                        }
                        """,
                        """
                        import com.alibaba.fastjson2.JSON;
                        import com.alibaba.fastjson2.JSONObject;
                        import com.alibaba.fastjson2.TypeReference;
                        import com.alibaba.fastjson2.annotation.JSONField;

                        import java.util.Map;

                        class Payload {
                            @JSONField(name = "id") String id;
                            String write(JSONObject value) { return JSON.toJSONString(value); }
                            TypeReference<Map<String, String>> type() { return new TypeReference<>() {}; }
                        }
                        """
                )
        );
    }
}
