package com.huawei.clouds.openrewrite.bson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class MigrateJsonWriterSettingsConstructorsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJsonWriterSettingsConstructors())
                .parser(JavaParser.fromJavaVersion().dependsOn(BsonTestApi.legacySources()));
    }

    @ParameterizedTest(name = "removed constructor {0}")
    @MethodSource("constructors")
    void migratesEveryRemovedConstructor(String label, String before, String after) {
        rewriteRun(java("""
                import org.bson.json.*;
                class C { JsonWriterSettings settings() { return %s; } }
                """.formatted(before), """
                import org.bson.json.*;
                class C { JsonWriterSettings settings() { return %s; } }
                """.formatted(after)));
    }

    static Stream<Arguments> constructors() {
        return Stream.of(
                Arguments.of("no arguments", "new JsonWriterSettings()",
                        "JsonWriterSettings.builder().outputMode(JsonMode.STRICT).build()"),
                Arguments.of("mode", "new JsonWriterSettings(JsonMode.SHELL)",
                        "JsonWriterSettings.builder().outputMode(JsonMode.SHELL).build()"),
                Arguments.of("indent flag", "new JsonWriterSettings(true)",
                        "JsonWriterSettings.builder().indent(true).build()"),
                Arguments.of("mode and indent", "new JsonWriterSettings(JsonMode.STRICT, false)",
                        "JsonWriterSettings.builder().outputMode(JsonMode.STRICT).indent(false).build()"),
                Arguments.of("indent characters", "new JsonWriterSettings(JsonMode.EXTENDED, \"  \")",
                        "JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).indent(true).indentCharacters(\"  \").build()"),
                Arguments.of("newline characters", "new JsonWriterSettings(JsonMode.RELAXED, \"  \", \"\\r\\n\")",
                        "JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).indent(true).indentCharacters(\"  \").newLineCharacters(\"\\r\\n\").build()")
        );
    }

    @Test
    void migratesRealOpenCgaModeAndFalsePattern() {
        rewriteRun(java("""
                import org.bson.json.JsonMode;
                import org.bson.json.JsonWriterSettings;
                class VariantMongoDBQueryParser {
                    static final JsonWriterSettings SETTINGS = new JsonWriterSettings(JsonMode.SHELL, false);
                }
                """, """
                import org.bson.json.JsonMode;
                import org.bson.json.JsonWriterSettings;
                class VariantMongoDBQueryParser {
                    static final JsonWriterSettings SETTINGS = JsonWriterSettings.builder().outputMode(JsonMode.SHELL).indent(false).build();
                }
                """));
    }

    @Test
    void preservesArgumentExpressionsAndTheirOrder() {
        rewriteRun(java("""
                import org.bson.json.*;
                class C { String chars(){return " ";} String newline(){return "\\n";}
                    JsonWriterSettings f(JsonMode mode){return new JsonWriterSettings(mode, chars(), newline());}
                }
                """, """
                import org.bson.json.*;
                class C { String chars(){return " ";} String newline(){return "\\n";}
                    JsonWriterSettings f(JsonMode mode){return JsonWriterSettings.builder().outputMode(mode).indent(true).indentCharacters(chars()).newLineCharacters(newline()).build();}
                }
                """));
    }

    @Test
    void unrelatedConstructorIsNoop() {
        rewriteRun(java("class JsonWriterSettings { JsonWriterSettings(boolean b){} } class C { Object s = new JsonWriterSettings(true); }"));
    }

    @Test
    void anonymousSubclassIsNoop() {
        rewriteRun(java("import org.bson.json.JsonWriterSettings; class C { Object s = new JsonWriterSettings() {}; }"));
    }

    @Test
    void generatedSourceIsNoop() {
        rewriteRun(java("import org.bson.json.JsonWriterSettings; class C { Object s = new JsonWriterSettings(); }",
                source -> source.path("target/generated-sources/C.java")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import org.bson.json.JsonWriterSettings; class C { Object s = new JsonWriterSettings(); }",
                "import org.bson.json.JsonMode;\nimport org.bson.json.JsonWriterSettings;\n\n class C { Object s = JsonWriterSettings.builder().outputMode(JsonMode.STRICT).build(); }"));
    }
}
