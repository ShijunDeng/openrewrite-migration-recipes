package com.huawei.clouds.openrewrite.bson;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedBsonMigrationTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.bson.MigrateBsonTo5_4_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECOMMENDED))
                .parser(JavaParser.fromJavaVersion().dependsOn(BsonTestApi.legacySources()));
    }

    @Test
    void migratesWholeProjectInProductionOrder() {
        rewriteRun(
                xml(UpgradeBsonDependencyTest.pom("3.12.14"),
                        UpgradeBsonDependencyTest.pom("5.4.0"), source -> source.path("pom.xml")),
                java("""
                        import org.bson.codecs.record.annotations.BsonId;
                        import org.bson.json.*;
                        import org.bson.types.ObjectId;

                        class StoredValue {
                            @BsonId String id;
                            long created(ObjectId value) { return value.getTime(); }
                            String hex(ObjectId value) { return value.toStringMongod(); }
                            JsonWriterSettings json() { return new JsonWriterSettings(JsonMode.SHELL, false); }
                        }
                        """, """
                        import org.bson.codecs.pojo.annotations.BsonId;
                        import org.bson.json.*;
                        import org.bson.types.ObjectId;

                        class StoredValue {
                            @BsonId String id;
                            long created(ObjectId value) { return value.getDate().getTime(); }
                            String hex(ObjectId value) { return value.toHexString(); }
                            JsonWriterSettings json() { return JsonWriterSettings.builder().outputMode(JsonMode.SHELL).indent(false).build(); }
                        }
                        """));
    }

    @Test
    void combinesAutomaticMigrationAndReviewMarkers() {
        rewriteRun(
                java("import org.bson.types.ObjectId; class C { int seconds(ObjectId id){return id.getTimeSecond();} }",
                        "import org.bson.types.ObjectId; class C { int seconds(ObjectId id){return id.getTimestamp();} }"),
                java("import org.bson.codecs.UuidCodec; class StoredUuid { Object codec = new UuidCodec(); }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindBson5SourceRisks.UUID), after.printAll()))));
    }

    @Test
    void targetProjectIsNoop() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(BsonTestApi.targetSources())),
                xml(UpgradeBsonDependencyTest.pom("5.4.0"), source -> source.path("pom.xml")),
                java("import org.bson.types.ObjectId; class C { long time(ObjectId id){return id.getDate().getTime();} }"));
    }

    @Test
    void publicRecipeOrderKeepsAutoBeforeReviewMarkers() {
        Recipe recipe = environment().activateRecipes(RECOMMENDED);
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.bson.UpgradeBsonTo5_4_0",
                "com.huawei.clouds.openrewrite.bson.MigrateBson5Java",
                "com.huawei.clouds.openrewrite.bson.FindBson5BuildRisks",
                "com.huawei.clouds.openrewrite.bson.FindBson5SourceRisks"),
                recipe.getRecipeList().stream().map(Recipe::getName).toList());
    }

    @Test
    void wholeRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeBsonDependencyTest.pom("4.7.2"),
                        UpgradeBsonDependencyTest.pom("5.4.0"), source -> source.path("pom.xml")),
                java("import org.bson.types.ObjectId; class C { long time(ObjectId id){return id.getTime();} }",
                        "import org.bson.types.ObjectId; class C { long time(ObjectId id){return id.getDate().getTime();} }"));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.bson").build();
    }
}
