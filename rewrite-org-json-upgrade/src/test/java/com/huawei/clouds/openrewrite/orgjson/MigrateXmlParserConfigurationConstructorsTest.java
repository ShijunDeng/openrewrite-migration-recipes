package com.huawei.clouds.openrewrite.orgjson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class MigrateXmlParserConfigurationConstructorsTest implements RewriteTest {
    @Override public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateXmlParserConfigurationConstructors()).parser(JavaParser.fromJavaVersion().classpath("json"));
    }

    @ParameterizedTest @MethodSource("constructors")
    void migratesDeprecatedConstructor(String arguments, String chain) {
        rewriteRun(java("""
                import org.json.XMLParserConfiguration;
                class Config { XMLParserConfiguration config(boolean keep, String cdata, boolean nil) {
                    return new XMLParserConfiguration(%s);
                } }
                """.formatted(arguments), """
                import org.json.XMLParserConfiguration;
                class Config { XMLParserConfiguration config(boolean keep, String cdata, boolean nil) {
                    return new XMLParserConfiguration()%s;
                } }
                """.formatted(chain)));
    }

    static Stream<Arguments> constructors() { return Stream.of(
            Arguments.of("keep", ".withKeepStrings(keep)"),
            Arguments.of("cdata", ".withcDataTagName(cdata)"),
            Arguments.of("keep, cdata", ".withKeepStrings(keep).withcDataTagName(cdata)"),
            Arguments.of("keep, cdata, nil", ".withKeepStrings(keep).withcDataTagName(cdata).withConvertNilAttributeToNull(nil)"),
            Arguments.of("true", ".withKeepStrings(true)"),
            Arguments.of("\"text\"", ".withcDataTagName(\"text\")"),
            Arguments.of("false, null, true", ".withKeepStrings(false).withcDataTagName(null).withConvertNilAttributeToNull(true)")); }

    @Test void fullyQualifiedUseAddsImportAndIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "class A { Object x() { return new org.json.XMLParserConfiguration(true); } }",
                """
                import org.json.XMLParserConfiguration;

                class A { Object x() { return new XMLParserConfiguration().withKeepStrings(true); } }
                """));
    }

    @Test void defaultModernAndSameNamedTypesStayClean() {
        rewriteRun(java("""
                import org.json.XMLParserConfiguration;
                class Local { Local(boolean b) {} }
                class A { Object a = new XMLParserConfiguration(); Object b = new XMLParserConfiguration().withKeepStrings(true); Object c = new Local(true); }
                """));
    }

    @Test void generatedParentNoopAndLeafInstallMigrates() {
        rewriteRun(java("import org.json.XMLParserConfiguration; class G { Object x = new XMLParserConfiguration(true); }", s -> s.path("generated/G.java")),
                java("import org.json.XMLParserConfiguration; class install { Object x = new XMLParserConfiguration(true); }",
                        "import org.json.XMLParserConfiguration; class install { Object x = new XMLParserConfiguration().withKeepStrings(true); }", s -> s.path("install.java")));
    }
}
