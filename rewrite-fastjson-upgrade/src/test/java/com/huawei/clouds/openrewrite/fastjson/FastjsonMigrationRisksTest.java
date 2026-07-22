package com.huawei.clouds.openrewrite.fastjson;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;

class FastjsonMigrationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindFastjson2MigrationRisks())
                .parser(JavaParser.fromJavaVersion().classpath("fastjson"));
    }

    @Test
    void marksAutoTypeEnablementPrecisely() {
        rewriteRun(java(
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                class SecurityConfig {
                    void configure(ParserConfig config) {
                        config.setAutoTypeSupport(true);
                    }
                }
                """,
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                class SecurityConfig {
                    void configure(ParserConfig config) {
                        /*~~(Fastjson2 removed the old AutoType whitelist model; use a narrow AutoTypeBeforeHandler and security tests)~~>*/config.setAutoTypeSupport(true);
                    }
                }
                """
        ));
    }

    @Test
    void marksAutoTypeAcceptFromAlibabaOtter() {
        // Reduced from alibaba/otter at 7544d0515e832b188736cc6d882d5a7da0558a55:
        // https://github.com/alibaba/otter/blob/7544d0515e832b188736cc6d882d5a7da0558a55/shared/common/src/main/java/com/alibaba/otter/shared/common/utils/JsonUtils.java#L70-L72
        rewriteRun(java(
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                class SecurityConfig {
                    void configure(ParserConfig config) {
                        config.addAccept("com.alibaba.otter.");
                    }
                }
                """,
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                class SecurityConfig {
                    void configure(ParserConfig config) {
                        /*~~(Migrate this rule to JSONFactory.getDefaultObjectReaderProvider() and review its security boundary)~~>*/config.addAccept("com.alibaba.otter.");
                    }
                }
                """
        ));
    }

    @Test
    void marksSafeModeFromJFinal() {
        // Reduced from jfinal/jfinal at a0e9e8b99dc793bcf0cd40ca7feba005ba0c5349:
        // https://github.com/jfinal/jfinal/blob/a0e9e8b99dc793bcf0cd40ca7feba005ba0c5349/src/main/java/com/jfinal/json/FastJson.java#L34-L41
        rewriteRun(java(
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                class SecurityConfig {
                    void configure(ParserConfig config) {
                        config.setSafeMode(true);
                    }
                }
                """,
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                class SecurityConfig {
                    void configure(ParserConfig config) {
                        /*~~(Fastjson2 safe mode is process-wide and cannot be toggled incompatibly at runtime)~~>*/config.setSafeMode(true);
                    }
                }
                """
        ));
    }

    @Test
    void marksOppositeReferenceDetectionSemanticsFromJvmSerializers() {
        // Reduced from eishay/jvm-serializers at 3f7e4bc94e40f2c8c89acd3d2e51454867cec596:
        // https://github.com/eishay/jvm-serializers/blob/3f7e4bc94e40f2c8c89acd3d2e51454867cec596/tpc/src/serializers/json/FastJSONDatabind.java#L50-L70
        rewriteRun(java(
                """
                import com.alibaba.fastjson.serializer.SerializerFeature;
                class Features {
                    SerializerFeature reference() {
                        return SerializerFeature.DisableCircularReferenceDetect;
                    }
                }
                """,
                """
                import com.alibaba.fastjson.serializer.SerializerFeature;
                class Features {
                    SerializerFeature reference() {
                        return /*~~(Fastjson2 disables reference detection by default; native JSONWriter.Feature.ReferenceDetection has the opposite meaning)~~>*/SerializerFeature.DisableCircularReferenceDetect;
                    }
                }
                """
        ));
    }

    @Test
    void marksIsoDateFeature() {
        rewriteRun(java(
                """
                import com.alibaba.fastjson.serializer.SerializerFeature;
                class Features {
                    SerializerFeature date() { return SerializerFeature.UseISO8601DateFormat; }
                }
                """,
                """
                import com.alibaba.fastjson.serializer.SerializerFeature;
                class Features {
                    SerializerFeature date() { return /*~~(Fastjson2 has no equivalent writer feature; choose an explicit format such as iso8601)~~>*/SerializerFeature.UseISO8601DateFormat; }
                }
                """
        ));
    }

    @Test
    void marksAutoTypeParserFeature() {
        rewriteRun(java(
                """
                import com.alibaba.fastjson.parser.Feature;
                class Features {
                    Feature autoType() { return Feature.SupportAutoType; }
                }
                """,
                """
                import com.alibaba.fastjson.parser.Feature;
                class Features {
                    Feature autoType() { return /*~~(Do not mechanically enable Fastjson2 SupportAutoType; choose a narrow AutoTypeBeforeHandler allow-list)~~>*/Feature.SupportAutoType; }
                }
                """
        ));
    }

    @Test
    void marksDateFormattingCallFromJFinal() {
        // Reduced from jfinal/jfinal at a0e9e8b99dc793bcf0cd40ca7feba005ba0c5349:
        // https://github.com/jfinal/jfinal/blob/a0e9e8b99dc793bcf0cd40ca7feba005ba0c5349/src/main/java/com/jfinal/json/FastJson.java#L49-L70
        rewriteRun(java(
                """
                import com.alibaba.fastjson.JSON;
                class JsonOutput {
                    String write(Object value) {
                        return JSON.toJSONStringWithDateFormat(value, "yyyy-MM-dd");
                    }
                }
                """,
                """
                import com.alibaba.fastjson.JSON;
                class JsonOutput {
                    String write(Object value) {
                        return /*~~(Date and time-zone defaults changed; verify the explicit format against production payloads)~~>*/JSON.toJSONStringWithDateFormat(value, "yyyy-MM-dd");
                    }
                }
                """
        ));
    }

    @Test
    void marksDeserializerMapWhenReturnValueCannotBePreserved() {
        rewriteRun(java(
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
                class Registry {
                    ObjectDeserializer replace(ObjectDeserializer reader) {
                        return ParserConfig.getGlobalInstance().getDeserializers().put(String.class, reader);
                    }
                }
                """,
                """
                import com.alibaba.fastjson.parser.ParserConfig;
                import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
                class Registry {
                    ObjectDeserializer replace(ObjectDeserializer reader) {
                        return /*~~(The mutable deserializer map is absent from Fastjson 2 compatibility mode; use putDeserializer/getDeserializer when return semantics are equivalent, or redesign code that exposes or consumes the map)~~>*//*~~(Global reader/writer provider mutation affects the whole process; review initialization order and isolation)~~>*/ParserConfig.getGlobalInstance().getDeserializers().put(String.class, reader);
                    }
                }
                """
        ));
    }

    @Test
    void marksCustomJsonFieldExtensionHookButNotSimpleNameMapping() {
        rewriteRun(
                java(
                        """
                        import com.alibaba.fastjson.annotation.JSONField;
                        import com.alibaba.fastjson.serializer.ToStringSerializer;
                        class Payload {
                            @JSONField(serializeUsing = ToStringSerializer.class)
                            Object custom;
                        }
                        """,
                        """
                        import com.alibaba.fastjson.annotation.JSONField;
                        import com.alibaba.fastjson.serializer.ToStringSerializer;
                        class Payload {
                            /*~~(Fastjson2 annotation extension hooks and formatting defaults differ; verify this option explicitly)~~>*/@JSONField(serializeUsing = ToStringSerializer.class)
                            Object custom;
                        }
                        """
                ),
                java("""
                        import com.alibaba.fastjson.annotation.JSONField;
                        class StablePayload {
                            @JSONField(name = "value")
                            Object value;
                        }
                        """, source -> source.path("StablePayload.java"))
        );
    }

    @Test
    void ignoresSameNamedBusinessMethodsAndEnums() {
        rewriteRun(java("""
                class BusinessConfig {
                    enum Feature { SupportAutoType, SortField }
                    void addAccept(String value) {}
                    void setAutoTypeSupport(boolean enabled) {}
                    void configure() {
                        addAccept("example");
                        setAutoTypeSupport(true);
                        Feature feature = Feature.SupportAutoType;
                    }
                }
                """));
    }

    @Test
    void marksOnlyExactLegacySecurityProperties() {
        rewriteRun(
                spec -> spec.recipe(new FindFastjson2PropertiesRisks()),
                properties(
                        """
                        fastjson.parser.autoTypeSupport=true
                        fastjson.parser.autoTypeAccept=com.example.safe.
                        fastjson.parser.deny=com.example.bad.
                        fastjson.parser.deny.internal=java.lang.
                        fastjson.parser.safeMode=true
                        application.fastjson.parser.safeMode=true
                        """,
                        """
                        ~~(Fastjson2 does not preserve the old AutoType switch; use a narrow AutoTypeBeforeHandler allow-list)~~>fastjson.parser.autoTypeSupport=true
                        ~~(Fastjson2 removed the old whitelist model; review and migrate every accepted package prefix)~~>fastjson.parser.autoTypeAccept=com.example.safe.
                        ~~(Fastjson2 uses a different AutoType security model; verify this deny rule rather than assuming it remains authoritative)~~>fastjson.parser.deny=com.example.bad.
                        ~~(This Fastjson 1.x internal deny property is not consumed by the 2.0.62 compatibility ParserConfig)~~>fastjson.parser.deny.internal=java.lang.
                        ~~(Fastjson2 safe mode is initialized process-wide; verify the deployment startup value and inability to toggle it)~~>fastjson.parser.safeMode=true
                        application.fastjson.parser.safeMode=true
                        """
                )
        );
    }

    @Test
    void ignoresGeneratedSecurityProperties() {
        rewriteRun(
                spec -> spec.recipe(new FindFastjson2PropertiesRisks()),
                properties("fastjson.parser.safeMode=true\n",
                        source -> source.path("build/resources/main/application.properties"))
        );
    }
}
