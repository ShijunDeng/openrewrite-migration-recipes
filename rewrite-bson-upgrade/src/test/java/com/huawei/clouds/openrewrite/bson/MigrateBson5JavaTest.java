package com.huawei.clouds.openrewrite.bson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;

class MigrateBson5JavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateBson5Java())
                .parser(JavaParser.fromJavaVersion().dependsOn(BsonTestApi.legacySources()));
    }

    @ParameterizedTest(name = "ObjectId migration {0}")
    @MethodSource("objectIdMethods")
    void migratesRemovedObjectIdMethods(String label, String before, String after) {
        rewriteRun(java("import org.bson.types.ObjectId; class C { " + before + " }",
                "import org.bson.types.ObjectId; class C { " + after + " }"));
    }

    static Stream<Arguments> objectIdMethods() {
        return Stream.of(
                Arguments.of("seconds", "int f(ObjectId id){ return id.getTimeSecond(); }",
                        "int f(ObjectId id){ return id.getTimestamp(); }"),
                Arguments.of("hex", "String f(ObjectId id){ return id.toStringMongod(); }",
                        "String f(ObjectId id){ return id.toHexString(); }"),
                Arguments.of("milliseconds", "long f(ObjectId id){ return id.getTime(); }",
                        "long f(ObjectId id){ return id.getDate().getTime(); }")
        );
    }

    @Test
    void preservesComplexObjectIdReceiverEvaluationCount() {
        rewriteRun(java(
                "import org.bson.types.ObjectId; class C { ObjectId next(){return null;} long f(){return next().getTime();} }",
                "import org.bson.types.ObjectId; class C { ObjectId next(){return null;} long f(){return next().getDate().getTime();} }"));
    }

    @ParameterizedTest(name = "record annotation {0}")
    @MethodSource("recordAnnotations")
    void migratesRemovedRecordAnnotationPackage(String simpleName, String suffix) {
        rewriteRun(java(
                "import org.bson.codecs.record.annotations." + simpleName + "; " +
                "class RecordValue { " + suffix + " String id; }",
                "import org.bson.codecs.pojo.annotations." + simpleName + ";\n\n" +
                "class RecordValue { " + suffix + " String id; }"));
    }

    static Stream<Arguments> recordAnnotations() {
        return Stream.of(
                Arguments.of("BsonId", "@BsonId"),
                Arguments.of("BsonProperty", "@BsonProperty(\"identifier\")"),
                Arguments.of("BsonRepresentation", "@BsonRepresentation(7)")
        );
    }

    @Test
    void migratesFullyQualifiedRecordAnnotation() {
        rewriteRun(java(
                "class C { @org.bson.codecs.record.annotations.BsonId String id; }",
                "import org.bson.codecs.pojo.annotations.BsonId;\n\nclass C { @BsonId String id; }"));
    }

    @Test
    void sameNamedApplicationMethodsAreNoop() {
        rewriteRun(java("class Own { long getTime(){return 1;} int getTimeSecond(){return 1;} " +
                        "String toStringMongod(){return \"x\";} void f(){getTime();getTimeSecond();toStringMongod();} }"));
    }

    @Test
    void targetApisAreNoop() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(BsonTestApi.targetSources())), java(
                "import org.bson.types.ObjectId; class C { long f(ObjectId id){return id.getDate().getTime();} " +
                "int s(ObjectId id){return id.getTimestamp();} String h(ObjectId id){return id.toHexString();} }"));
    }

    @Test
    void generatedSourcesAreNoop() {
        rewriteRun(java("import org.bson.types.ObjectId; class C { long f(ObjectId id){return id.getTime();} }",
                source -> source.path("build/generated/sources/C.java")));
    }

    @Test
    void compositionHasAllDeterministicSteps() {
        assertEquals(7, new MigrateBson5Java().getRecipeList().size());
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import org.bson.types.ObjectId; class C { long f(ObjectId id){return id.getTime();} }",
                "import org.bson.types.ObjectId; class C { long f(ObjectId id){return id.getDate().getTime();} }"));
    }
}
