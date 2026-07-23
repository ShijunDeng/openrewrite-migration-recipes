package com.huawei.clouds.openrewrite.snakeyaml;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SnakeYaml25SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSnakeYaml25SourceRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(SnakeYamlTestApi.sources()));
    }

    @Test
    void marksDefaultYamlSecurityChangeAtConstructor() {
        rewriteRun(java(
                "import org.yaml.snakeyaml.Yaml; class F { Object yaml(){ return new Yaml(); } }",
                "import org.yaml.snakeyaml.Yaml; class F { Object yaml(){ return /*~~(%s)~~>*/new Yaml(); } }"
                        .formatted(FindSnakeYaml25SourceRisks.DEFAULT_YAML)));
    }

    @Test
    void marksLoadAndLoadAsAtExactInvocations() {
        rewriteRun(java(
                """
                import org.yaml.snakeyaml.Yaml;
                class F { Object load(Yaml yaml, String text) { yaml.load(text); return yaml.loadAs(text, F.class); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindSnakeYaml25SourceRisks.LOAD, 2))));
    }

    @Test
    void marksDumpAndRepresentAtExactInvocations() {
        rewriteRun(java(
                """
                import org.yaml.snakeyaml.Yaml;
                class F { Object dump(Yaml yaml, Object value) { yaml.dump(value); return yaml.represent(value); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindSnakeYaml25SourceRisks.DUMP, 2))));
    }

    @Test
    void marksLoaderLimitsAndOptionsConstruction() {
        // Reduced from Swagger Parser and Jenkins Pipeline Utility Steps fixed revisions documented in README.md.
        rewriteRun(java(
                """
                import org.yaml.snakeyaml.LoaderOptions;
                class Limits { LoaderOptions options() { LoaderOptions o = new LoaderOptions();
                    o.setMaxAliasesForCollections(25); o.setAllowRecursiveKeys(false); o.setAllowDuplicateKeys(false); return o; } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindSnakeYaml25SourceRisks.SECURITY_OPTIONS, 4))));
    }

    @Test
    void marksTypedConstructorTagPolicy() {
        rewriteRun(java(
                "import org.yaml.snakeyaml.constructor.Constructor; class Config { Object c(){ return new Constructor(Config.class); } }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSnakeYaml25SourceRisks.TAG_POLICY))));
    }

    @Test
    void marksSafeConstructorLoadBoundary() {
        rewriteRun(java(
                "import org.yaml.snakeyaml.constructor.SafeConstructor; class Config { Object c(){ return new SafeConstructor(); } }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSnakeYaml25SourceRisks.LOAD))));
    }

    @Test
    void marksRepresenterDumpBoundary() {
        rewriteRun(java(
                "import org.yaml.snakeyaml.representer.Representer; class Config { Object c(){ return new Representer(); } }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSnakeYaml25SourceRisks.DUMP))));
    }

    @Test
    void marksSchemaAndPropertyCustomization() {
        rewriteRun(java(
                """
                import org.yaml.snakeyaml.TypeDescription;
                import org.yaml.snakeyaml.constructor.Constructor;
                class Schema { Object c(){ Constructor c = new Constructor();
                    c.addTypeDescription(new TypeDescription(Schema.class));
                    c.getPropertyUtils().setSkipMissingProperties(true); return c; } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindSnakeYaml25SourceRisks.SCHEMA, 2))));
    }

    @Test
    void marksRealSwaggerStyleCustomSafeConstructorExtension() {
        // Reduced from swagger-api/swagger-parser@fcbd9ed88d6eeaaf6d51c177288b156ce67e9760.
        rewriteRun(java(
                """
                import org.yaml.snakeyaml.constructor.SafeConstructor;
                class CustomSnakeYamlConstructor extends SafeConstructor {
                    CustomSnakeYamlConstructor() { super(); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSnakeYaml25SourceRisks.EXTENSION))));
    }

    @Test
    void marksCustomRepresenterExtensionAtExtendsType() {
        rewriteRun(java(
                """
                import org.yaml.snakeyaml.representer.Representer;
                class CustomRepresenter extends Representer { CustomRepresenter() { super(); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindSnakeYaml25SourceRisks.EXTENSION))));
    }

    @Test
    void sameNamedApplicationApisAreNoop() {
        rewriteRun(java("""
                class Yaml { Object load(String s){ return s; } String dump(Object o){ return ""; } }
                class LoaderOptions { void setAllowDuplicateKeys(boolean b) { } }
                class Use { Object x(Yaml y) { return y.load("x"); } }
                """, source -> source.afterRecipe(after -> {
                    String out = after.printAll();
                    assertFalse(out.contains("/*~~("), out);
                })));
    }

    @Test
    void generatedInstallAndCachesAreNoop() {
        rewriteRun(
                java("import org.yaml.snakeyaml.Yaml; class Generated { Object x(){ return new Yaml(); } }",
                        source -> source.path("generated-code/Generated.java")),
                java("import org.yaml.snakeyaml.Yaml; class Installed { Object x(){ return new Yaml(); } }",
                        source -> source.path("installation/lib/Installed.java")),
                java("import org.yaml.snakeyaml.Yaml; class Cached { Object x(){ return new Yaml(); } }",
                        source -> source.path(".m2/cache/Cached.java")));
    }

    @Test
    void installLeafIsMarkedAndMarkersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import org.yaml.snakeyaml.Yaml; class install { Object x(){ return new Yaml(); } }",
                source -> source.path("install.java").after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindSnakeYaml25SourceRisks.DEFAULT_YAML, 1))));
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
