package com.huawei.clouds.openrewrite.graalvmjs;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class GraalVmJsOptionsAndRisksTest implements RewriteTest {
    private static final String MIGRATE = "com.huawei.clouds.openrewrite.graalvmjs.MigrateGraalVmJsTo24_2_1";

    @Test
    void migratesContextDisableEvalTrueByInvertingMeaning() {
        rewriteRun(spec -> spec.recipe(new MigrateDeprecatedGraalVmJsOptions()).parser(polyglotParser()),
                java("""
                        import org.graalvm.polyglot.Context;
                        class App { Context.Builder configure() { return Context.newBuilder("js").option("js.disable-eval", "true"); } }
                        """, """
                        import org.graalvm.polyglot.Context;
                        class App { Context.Builder configure() { return Context.newBuilder("js").option("js.allow-eval", "false"); } }
                        """));
    }

    @Test
    void migratesContextDisableEvalFalseByInvertingMeaning() {
        rewriteRun(spec -> spec.recipe(new MigrateDeprecatedGraalVmJsOptions()).parser(polyglotParser()),
                java("""
                        import org.graalvm.polyglot.Context;
                        class App { Context.Builder configure() { return Context.newBuilder().option("js.disable-eval", "false"); } }
                        """, """
                        import org.graalvm.polyglot.Context;
                        class App { Context.Builder configure() { return Context.newBuilder().option("js.allow-eval", "true"); } }
                        """));
    }

    @Test
    void migratesSystemPropertyDisableEval() {
        rewriteRun(spec -> spec.recipe(new MigrateDeprecatedGraalVmJsOptions()),
                java("""
                        class App { void configure() { System.setProperty("polyglot.js.disable-eval", "true"); } }
                        """, """
                        class App { void configure() { System.setProperty("polyglot.js.allow-eval", "false"); } }
                        """));
    }

    @Test
    void leavesVariableOptionValueAndUnrelatedBuilderAlone() {
        rewriteRun(spec -> spec.recipe(new MigrateDeprecatedGraalVmJsOptions()).parser(polyglotParser()),
                java("""
                        import org.graalvm.polyglot.Context;
                        class App {
                            void configure(Context.Builder builder, String value) {
                                builder.option("js.disable-eval", value);
                                new Other().option("js.disable-eval", "true");
                            }
                            static class Other { Other option(String key, String value) { return this; } }
                        }
                        """));
    }

    @Test
    void optionMigrationSkipsGeneratedParentsButKeepsInstallLeaf() {
        rewriteRun(spec -> spec.recipe(new MigrateDeprecatedGraalVmJsOptions()),
                java("class Generated { void x() { System.setProperty(\"polyglot.js.disable-eval\", \"true\"); } }",
                        source -> source.path("generated-src/Generated.java")),
                java("class Install { void x() { System.setProperty(\"polyglot.js.disable-eval\", \"true\"); } }",
                        "class Install { void x() { System.setProperty(\"polyglot.js.allow-eval\", \"false\"); } }",
                        source -> source.path("install.java")));
    }

    @Test
    void optionMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeprecatedGraalVmJsOptions()).cycles(2)
                        .expectedCyclesThatMakeChanges(1),
                java("class App { void x() { System.setProperty(\"polyglot.js.disable-eval\", \"false\"); } }",
                        "class App { void x() { System.setProperty(\"polyglot.js.allow-eval\", \"true\"); } }"));
    }

    @Test
    void marksRealScriptEngineLookupFixture() {
        rewriteRun(spec -> spec.recipe(new FindGraalVmJs24JavaRisks()),
                java("""
                        import javax.script.ScriptEngine;
                        import javax.script.ScriptEngineManager;
                        class Scripts { ScriptEngine engine() { return new ScriptEngineManager().getEngineByName("graal.js"); } }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "JSR-223 discovery needs"))));
    }

    @Test
    void marksContextCreationForEcmaDefaultChange() {
        rewriteRun(spec -> spec.recipe(new FindGraalVmJs24JavaRisks()).parser(polyglotParser()),
                java("""
                        import org.graalvm.polyglot.Context;
                        class Scripts { Context context() { return Context.create("js"); } }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "defaults to ECMAScript 2024"))));
    }

    @Test
    void marksExactImportAssertionsOptionLiteral() {
        rewriteRun(spec -> spec.recipe(new FindGraalVmJs24JavaRisks()).parser(polyglotParser()),
                java("""
                        import org.graalvm.polyglot.Context;
                        class Scripts { Context.Builder context() { return Context.newBuilder().option("js.import-assertions", "true"); } }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "js.import-assertions was replaced");
                            assertContains(printed, "defaults to ECMAScript 2024");
                        })));
    }

    @Test
    void marksSecurityCapabilityButNotExplicitFalse() {
        rewriteRun(spec -> spec.recipe(new FindGraalVmJs24JavaRisks()).parser(polyglotParser()),
                java("""
                        import org.graalvm.polyglot.Context;
                        class Scripts { Context.Builder risky() { return Context.newBuilder().allowAllAccess(true); } }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "Revalidate this Polyglot capability"))),
                java("""
                        import org.graalvm.polyglot.Context;
                        class Safe { Context.Builder safe(Context.Builder builder) { return builder.allowAllAccess(false); } }
                        """));
    }

    @Test
    void marksDirectScriptEngineImport() {
        rewriteRun(spec -> spec.recipe(new FindGraalVmJs24JavaRisks()).parser(polyglotParser()),
                java("""
                        import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
                        class Scripts { GraalJSScriptEngine engine; }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "no longer shipped by the runtime"))));
    }

    @Test
    void javaRiskFinderSkipsGeneratedButKeepsInstallLeaf() {
        rewriteRun(spec -> spec.recipe(new FindGraalVmJs24JavaRisks()),
                java("class Generated { void x() { System.setProperty(\"x\", \"y\"); } }",
                        source -> source.path("generated-test/Generated.java")),
                java("""
                        import javax.script.ScriptEngineManager;
                        class Install { Object x() { return new ScriptEngineManager().getEngineByName("js"); } }
                        """, source -> source.path("install.java").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "JSR-223 discovery needs"))));
    }

    @Test
    void marksMavenJavaBaselineAndProtectedVersion() {
        rewriteRun(spec -> spec.recipe(new FindGraalVmJs24BuildRisks()),
                xml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                          <properties><maven.compiler.release>11</maven.compiler.release></properties>
                          <dependencies><dependency><groupId>org.graalvm.js</groupId><artifactId>js</artifactId></dependency></dependencies>
                        </project>
                        """, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "Java 17+");
                            assertContains(printed, "Versionless GraalJS");
                        })));
    }

    @Test
    void marksCompilerPluginLevelAndTruffleCoupling() {
        rewriteRun(spec -> spec.recipe(new FindGraalVmJs24BuildRisks()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                          <build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><configuration><release>11</release></configuration></plugin></plugins></build>
                          <dependencies><dependency><groupId>org.graalvm.truffle</groupId><artifactId>truffle-api</artifactId><version>24.2.1</version></dependency></dependencies>
                        </project>
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "maven-compiler-plugin");
                            assertContains(printed, "not the supported Polyglot embedding surface");
                        })));
    }

    @Test
    void marksGradleMissingPolyglotButNotWhenPresent() {
        rewriteRun(spec -> spec.recipe(new FindGraalVmJs24BuildRisks()),
                buildGradle("""
                        dependencies { implementation('org.graalvm.polyglot:js:24.2.1') }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "Add org.graalvm.polyglot:polyglot:24.2.1"))),
                buildGradle("""
                        dependencies {
                            implementation('org.graalvm.polyglot:js:24.2.1')
                            implementation('org.graalvm.polyglot:polyglot:24.2.1')
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "selects Oracle/GFTC");
                            assertFalse(printed.contains("Add org.graalvm.polyglot:polyglot:24.2.1"));
                        })),
                buildGradle("""
                        dependencies {
                            implementation('org.graalvm.polyglot:js:24.2.1')
                            implementation('org.graalvm.polyglot:polyglot:24.1.0')
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "Align the explicit Polyglot API dependency to 24.2.1");
                            assertContains(printed, "Add org.graalvm.polyglot:polyglot:24.2.1");
                        })),
                buildGradle("""
                        dependencies {
                            implementation('org.graalvm.polyglot:js:24.2.1')
                            testImplementation('org.graalvm.polyglot:polyglot:24.2.1')
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "Add org.graalvm.polyglot:polyglot:24.2.1");
                        })));
    }

    @Test
    void marksNestedGradleOwnerAndJava11Baseline() {
        rewriteRun(spec -> spec.recipe(new FindGraalVmJs24BuildRisks()),
                buildGradle("""
                        sourceCompatibility = JavaVersion.VERSION_11
                        subprojects { dependencies { implementation('org.graalvm.js:js:22.3.1') } }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "Java 17+");
                            assertContains(printed, "Nested/selected/variant Gradle declaration");
                        })));
    }

    @Test
    void recommendedRecipeIsDiscoverableValidAndPerformsAutoBeforeMark() {
        Environment environment = environment();
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATE.equals(recipe.getName())));
        var migrate = environment.activateRecipes(MIGRATE);
        assertTrue(migrate.validateAll().stream().allMatch(validation -> validation.isValid()));
        assertEquals("com.huawei.clouds.openrewrite.graalvmjs.UpgradeGraalVmJsTo24_2_1",
                migrate.getRecipeList().get(0).getName());
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(MIGRATE)),
                buildGradle("dependencies { implementation('org.graalvm.js:js:22.3.0') }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "org.graalvm.polyglot:js:24.2.1");
                            assertContains(printed, "Add org.graalvm.polyglot:polyglot:24.2.1");
                        })));
    }

    @Test
    void migratesRealMcJsScriptsFixedCommitFixture() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                buildGradle("""
                        dependencies {
                            implementation 'org.graalvm.js:js:22.3.0'
                            implementation 'org.graalvm.sdk:graal-sdk:22.3.0'
                            implementation 'org.graalvm.truffle:truffle-api:22.3.0'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "org.graalvm.polyglot:js:24.2.1");
                            assertContains(printed, "org.graalvm.polyglot:polyglot:24.2.1");
                            assertContains(printed, "Direct Truffle API use");
                        })));
    }

    @Test
    void migratesRealXpandaFixedCommitFixture() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                buildGradle("""
                        dependencies {
                            implementation 'org.graalvm.js:js-scriptengine:22.3.1'
                            implementation 'org.graalvm.js:js:22.3.1'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "org.graalvm.js:js-scriptengine:24.2.1");
                            assertContains(printed, "org.graalvm.polyglot:js:24.2.1");
                            assertContains(printed, "Add org.graalvm.polyglot:polyglot:24.2.1");
                        })));
    }

    @Test
    void migratesRealQlarrFixedCommitFixture() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                buildGradle("""
                        dependencies {
                            implementation "org.graalvm.js:js:22.3.1"
                            implementation "org.graalvm.js:js-scriptengine:22.3.1"
                            implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1"
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "org.graalvm.polyglot:js:24.2.1");
                            assertContains(printed, "org.graalvm.js:js-scriptengine:24.2.1");
                            assertContains(printed, "kotlinx-coroutines-core:1.8.1");
                        })));
    }

    @Test
    void lowLevelPublicRecipeContainsNoCompatibilityMarkers() {
        Environment environment = environment();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.graalvmjs.UpgradeGraalVmJsTo24_2_1")),
                buildGradle("dependencies { implementation('org.graalvm.js:js:22.3.1') }",
                        "dependencies { implementation('org.graalvm.js:js:24.2.1') }",
                        source -> source.afterRecipe(after ->
                                assertFalse(after.printAll().contains("SearchResult")))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath().build();
    }

    private static JavaParser.Builder<?, ?> polyglotParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package org.graalvm.polyglot;
                public final class Context {
                    public static Context create(String... languages) { return null; }
                    public static Builder newBuilder(String... languages) { return null; }
                    public static final class Builder {
                        public Builder option(String key, String value) { return this; }
                        public Builder allowAllAccess(boolean value) { return this; }
                        public Builder allowHostAccess(Object value) { return this; }
                        public Builder allowIO(boolean value) { return this; }
                        public Builder allowCreateThread(boolean value) { return this; }
                        public Builder allowNativeAccess(boolean value) { return this; }
                        public Builder allowExperimentalOptions(boolean value) { return this; }
                        public Builder allowPolyglotAccess(Object value) { return this; }
                    }
                }
                """,
                """
                package com.oracle.truffle.js.scriptengine;
                public class GraalJSScriptEngine { public static GraalJSScriptEngine create() { return null; } }
                """);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected to find '" + expected + "' in:\n" + actual);
    }
}
