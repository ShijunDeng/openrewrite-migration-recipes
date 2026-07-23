package com.huawei.clouds.openrewrite.snakeyaml;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class MigrateSnakeYaml2ConstructorsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateSnakeYaml2Constructors())
                .parser(JavaParser.fromJavaVersion().dependsOn(SnakeYamlTestApi.sources()));
    }

    @Test
    void addsLoaderOptionsToSafeConstructor() {
        rewriteRun(java(
                """
                import org.yaml.snakeyaml.constructor.SafeConstructor;
                class Config { Object parser() { return new SafeConstructor(); } }
                """,
                """
                import org.yaml.snakeyaml.LoaderOptions;
                import org.yaml.snakeyaml.constructor.SafeConstructor;

                class Config { Object parser() { return new SafeConstructor(new LoaderOptions()); } }
                """));
    }

    @Test
    void addsDumperOptionsToRepresenter() {
        rewriteRun(java(
                """
                import org.yaml.snakeyaml.representer.Representer;
                class Config { Object representer() { return new Representer(); } }
                """,
                """
                import org.yaml.snakeyaml.DumperOptions;
                import org.yaml.snakeyaml.representer.Representer;

                class Config { Object representer() { return new Representer(new DumperOptions()); } }
                """));
    }

    @Test
    void addsLoaderOptionsToEmptyConstructor() {
        rewriteRun(java(
                "import org.yaml.snakeyaml.constructor.Constructor; class C { Object c(){ return new Constructor(); } }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "new Constructor(new LoaderOptions())");
                    assertContains(after.printAll(), "import org.yaml.snakeyaml.LoaderOptions;");
                })));
    }

    @Test
    void preservesClassRootArgument() {
        rewriteRun(java(
                "import org.yaml.snakeyaml.constructor.Constructor; class C { Object c(){ return new Constructor(C.class); } }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "new Constructor(C.class, new LoaderOptions())"))));
    }

    @Test
    void preservesTypeDescriptionArgument() {
        rewriteRun(java(
                """
                import org.yaml.snakeyaml.TypeDescription;
                import org.yaml.snakeyaml.constructor.Constructor;
                class C { Object c(){ return new Constructor(new TypeDescription(C.class)); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "new Constructor(new TypeDescription(C.class), new LoaderOptions())"))));
    }

    @Test
    void preservesClassNameArgument() {
        rewriteRun(java(
                "import org.yaml.snakeyaml.constructor.Constructor; class C { Object c() throws Exception { return new Constructor(\"java.util.Map\"); } }",
                source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "new Constructor(\"java.util.Map\", new LoaderOptions())"))));
    }

    @Test
    void preservesTypeDescriptionCollectionArguments() {
        rewriteRun(java(
                """
                import java.util.Collections;
                import org.yaml.snakeyaml.TypeDescription;
                import org.yaml.snakeyaml.constructor.Constructor;
                class C { Object c(){ TypeDescription root = new TypeDescription(C.class);
                    return new Constructor(root, Collections.singleton(root)); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(),
                                "new Constructor(root, Collections.singleton(root), new LoaderOptions())"))));
    }

    @Test
    void migratesNestedRealWorldConfigurationPattern() {
        // Reduced from legacy patterns documented by Swagger Parser and Apache Commons Configuration migrations.
        rewriteRun(java(
                """
                import org.yaml.snakeyaml.DumperOptions;
                import org.yaml.snakeyaml.Yaml;
                import org.yaml.snakeyaml.constructor.SafeConstructor;
                import org.yaml.snakeyaml.representer.Representer;
                class YamlFactory { Yaml create() {
                    return new Yaml(new SafeConstructor(), new Representer(), new DumperOptions());
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "new SafeConstructor(new LoaderOptions())");
                    assertContains(out, "new Representer(new DumperOptions())");
                })));
    }

    @Test
    void sameNamedApplicationConstructorsAreNoop() {
        rewriteRun(java("""
                class LoaderOptions { }
                class Constructor { Constructor() { } Constructor(Object o) { } }
                class Use { Object x(){ return new Constructor(); } }
                """, source -> source.afterRecipe(after ->
                        assertFalse(after.printAll().contains("org.yaml.snakeyaml.LoaderOptions"), after.printAll()))));
    }

    @Test
    void generatedAndInstallationParentsAreNoop() {
        rewriteRun(
                java("import org.yaml.snakeyaml.constructor.SafeConstructor; class Generated { Object x(){ return new SafeConstructor(); } }",
                        source -> source.path("generated-code/Generated.java")),
                java("import org.yaml.snakeyaml.representer.Representer; class Installed { Object x(){ return new Representer(); } }",
                        source -> source.path("installation/lib/Installed.java")));
    }

    @Test
    void installLeafIsProcessedAndTwoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import org.yaml.snakeyaml.constructor.SafeConstructor; class install { Object x(){ return new SafeConstructor(); } }",
                source -> source.path("install.java").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "new SafeConstructor(new LoaderOptions())");
                    assertCount(after.printAll(), "new LoaderOptions()", 1);
                })));
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
