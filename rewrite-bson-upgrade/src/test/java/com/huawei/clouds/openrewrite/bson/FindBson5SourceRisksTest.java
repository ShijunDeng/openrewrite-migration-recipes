package com.huawei.clouds.openrewrite.bson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindBson5SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindBson5SourceRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(BsonTestApi.legacySources()));
    }

    @Test
    void marksNoArgumentUuidCodecDefaultChange() {
        marked("import org.bson.codecs.UuidCodec; class C { Object c = new UuidCodec(); }",
                FindBson5SourceRisks.UUID);
    }

    @ParameterizedTest(name = "explicit UUID representation {0}")
    @ValueSource(strings = {"JAVA_LEGACY", "STANDARD", "UNSPECIFIED", "C_SHARP_LEGACY", "PYTHON_LEGACY"})
    void explicitUuidCodecRepresentationIsStable(String representation) {
        clean("import org.bson.*; import org.bson.codecs.UuidCodec; class C { Object c = " +
              "new UuidCodec(UuidRepresentation." + representation + "); }");
    }

    @ParameterizedTest(name = "relaxed JSON {0}")
    @ValueSource(strings = {"Document", "BsonDocument", "RawBsonDocument"})
    void marksNoArgumentToJsonBehaviorChange(String type) {
        marked("import org.bson." + type + "; class C { String f(" + type +
               " value){ return value.toJson(); } }", FindBson5SourceRisks.JSON);
    }

    @Test
    void explicitJsonWriterSettingsIsNotMarked() {
        clean("import org.bson.Document; import org.bson.json.JsonWriterSettings; " +
              "class C { String f(Document d, JsonWriterSettings s){ return d.toJson(s); } }");
    }

    @Test
    void marksObjectIdWriteSerializationBoundary() {
        marked("""
                import java.io.*;
                import org.bson.types.ObjectId;
                class Cache { void save(ObjectOutputStream out, ObjectId id) throws Exception { out.writeObject(id); } }
                """, FindBson5SourceRisks.OBJECT_ID_SERIALIZATION);
    }

    @Test
    void marksObjectIdReadSerializationBoundary() {
        marked("""
                import java.io.*;
                import org.bson.types.ObjectId;
                class Cache { ObjectId load(ObjectInputStream in) throws Exception { return (ObjectId) in.readObject(); } }
                """, FindBson5SourceRisks.OBJECT_ID_SERIALIZATION);
    }

    @ParameterizedTest(name = "legacy ObjectId method {0}")
    @ValueSource(strings = {"createFromLegacyFormat", "getCurrentCounter", "getMachineIdentifier",
            "getProcessIdentifier", "getCounter"})
    void marksLegacyObjectIdComponentMethods(String method) {
        String invocation = method.equals("createFromLegacyFormat") ? "ObjectId." + method + "(1,2,3)" :
                method.equals("getCurrentCounter") ? "ObjectId." + method + "()" : "id." + method + "()";
        marked("import org.bson.types.ObjectId; class C { ObjectId id; Object f(){ return " +
               invocation + "; } }", FindBson5SourceRisks.OBJECT_ID_COMPONENTS);
    }

    @Test
    void marksLegacyObjectIdComponentConstructor() {
        marked("import org.bson.types.ObjectId; class C { Object id = new ObjectId(1, 2, (short) 3, 4); }",
                FindBson5SourceRisks.OBJECT_ID_COMPONENTS);
    }

    @ParameterizedTest(name = "Decimal128 numeric dispatch {0}")
    @ValueSource(strings = {"isNumber", "asNumber"})
    void marksDecimal128NumericDispatch(String method) {
        marked("import org.bson.BsonDecimal128; class C { Object f(BsonDecimal128 d){ return d." +
               method + "(); } }", FindBson5SourceRisks.DECIMAL);
    }

    @ParameterizedTest(name = "removed codec {0}")
    @MethodSource("removedCodecs")
    void marksRemovedPublicCodecs(String type, String expression) {
        marked("import org.bson.codecs." + type + "; class C { Object codec = " + expression + "; }",
                FindBson5SourceRisks.REMOVED_CODECS);
    }

    static Stream<Arguments> removedCodecs() {
        return Stream.of(
                Arguments.of("MapCodec", "new MapCodec()"),
                Arguments.of("IterableCodec", "new IterableCodec(null, null)")
        );
    }

    @Test
    void marksParameterizableCodecImplementations() {
        marked("""
                import org.bson.codecs.*;
                class GenericCodec implements Parameterizable {
                    public Codec<?> parameterize(Object type, Object registry) { return null; }
                }
                """, FindBson5SourceRisks.PARAMETERIZABLE);
    }

    @ParameterizedTest(name = "reader state {0}")
    @ValueSource(strings = {"mark", "reset"})
    void marksRemovedReaderStateMethods(String method) {
        marked("import org.bson.BsonReader; class C { void f(BsonReader reader){ reader." +
               method + "(); } }", FindBson5SourceRisks.READER_MARK);
    }

    @Test
    void getMarkWithOwnedResetIsNotMarked() {
        clean("import org.bson.*; class C { void f(BsonReader reader){ BsonReaderMark mark = reader.getMark(); mark.reset(); } }");
    }

    @Test
    void marksRemovedWriterFlush() {
        marked("import org.bson.BsonWriter; class C { void f(BsonWriter writer){ writer.flush(); } }",
                FindBson5SourceRisks.WRITER_FLUSH);
    }

    @ParameterizedTest(name = "legacy public API {0}")
    @MethodSource("legacyApis")
    void marksLegacyUtilityApis(String label, String source) {
        marked(source, FindBson5SourceRisks.LEGACY_API);
    }

    static Stream<Arguments> legacyApis() {
        return Stream.of(
                Arguments.of("BSON encode", "import org.bson.BSON; class C { byte[] f(){ return BSON.encode(new Object()); } }"),
                Arguments.of("BSON hook", "import org.bson.*; class C { void f(){ BSON.addEncodingHook(String.class, v -> v); } }"),
                Arguments.of("Bits", "import org.bson.io.Bits; class C { int f(byte[] b){ return Bits.readInt(b); } }"),
                Arguments.of("util package", "import org.bson.util.CopyOnWriteMap; class C { Object m = new CopyOnWriteMap<>(); }")
        );
    }

    @ParameterizedTest(name = "ordinary ObjectId API {0}")
    @ValueSource(strings = {"getTimeSecond", "getTime", "toStringMongod"})
    void deterministicObjectIdApisAreHandledByAutoNotMarked(String method) {
        clean("import org.bson.types.ObjectId; class C { Object f(ObjectId id){ return id." + method + "(); } }");
    }

    @Test
    void unrelatedSameNamedApplicationApisAreNoop() {
        clean("class Own { void mark(){} void reset(){} void flush(){} String toJson(){return \"{}\";} " +
              "boolean isNumber(){return false;} Object asNumber(){return null;} }");
    }

    @Test
    void generatedSourcesAreNoop() {
        rewriteRun(java("import org.bson.codecs.UuidCodec; class C { Object c = new UuidCodec(); }",
                source -> source.path("target/generated-sources/C.java")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("import org.bson.codecs.UuidCodec; class C { Object c = new UuidCodec(); }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertCount(after.printAll(), FindBson5SourceRisks.UUID, 1))));
    }

    private void marked(String source, String message) {
        rewriteRun(java(source, spec -> spec.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), message))));
    }

    private void clean(String source) {
        rewriteRun(java(source, spec -> spec.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~("), actual);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
