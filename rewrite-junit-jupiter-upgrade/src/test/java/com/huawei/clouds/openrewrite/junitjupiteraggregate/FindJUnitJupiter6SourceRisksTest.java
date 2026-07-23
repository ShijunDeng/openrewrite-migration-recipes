package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindJUnitJupiter6SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterAggregateTestApi.sources()))
                .recipe(new FindJUnitJupiter6SourceRisks());
    }

    @ParameterizedTest(name = "pre-17 condition {0}")
    @MethodSource("oldConditions")
    void marksEveryPre17Condition(String label, String importName, String annotation) {
        String source = "import org.junit.jupiter.api.condition." + importName + ";\n" +
                        "import org.junit.jupiter.api.condition.JRE;\n" +
                        "class Tests {\n    " + annotation + "\n    void test() {}\n}\n";
        String expected = source.replace(annotation, marker(FindJUnitJupiter6SourceRisks.JRE) + annotation);
        rewriteRun(java(source, expected));
    }

    static Stream<Arguments> oldConditions() {
        return Stream.of(
                Arguments.of("enabled Java 8", "EnabledOnJre", "@EnabledOnJre(JRE.JAVA_8)"),
                Arguments.of("disabled Java 11", "DisabledOnJre", "@DisabledOnJre(JRE.JAVA_11)"),
                Arguments.of("enabled numeric 16", "EnabledOnJre", "@EnabledOnJre(versions = 16)"),
                Arguments.of("disabled numeric array", "DisabledOnJre", "@DisabledOnJre(versions = { 8, 17 })"),
                Arguments.of("enabled range", "EnabledForJreRange", "@EnabledForJreRange(min = JRE.JAVA_9, max = JRE.JAVA_21)"),
                Arguments.of("disabled range", "DisabledForJreRange", "@DisabledForJreRange(min = JRE.JAVA_14)"),
                Arguments.of("enabled numeric range", "EnabledForJreRange", "@EnabledForJreRange(minVersion = 11, maxVersion = 21)"),
                Arguments.of("disabled numeric range", "DisabledForJreRange", "@DisabledForJreRange(maxVersion = 16)")
        );
    }

    @Test
    void Java17And18ConditionsAreNoop() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.condition.EnabledOnJre;
                  import org.junit.jupiter.api.condition.JRE;
                  class Tests {
                      @EnabledOnJre({JRE.JAVA_17, JRE.JAVA_18})
                      void test() {}
                  }
                  """));
    }

    @Test
    void pre17TextInsideReasonIsNotACondition() {
        rewriteRun(java(
                "import org.junit.jupiter.api.condition.*; @EnabledOnJre(value=JRE.JAVA_17, disabledReason=\"ticket 16\") class Tests {}"));
    }

    @Test
    void marksCsvSourceFastCsvReview() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.params.provider.CsvSource;
                  class Tests {
                      @CsvSource(value = {"'foo'INVALID,bar"}, useHeadersInDisplayName = true)
                      void test(String first, String second) {}
                  }
                  """,
                """
                  import org.junit.jupiter.params.provider.CsvSource;
                  class Tests {
                      /*~~(JUnit 6 uses FastCSV: malformed quoting, characters after a closing quote, headers, whitespace/null handling, exception types/messages, and parameterized display names may change. Re-run this data set and review assertions)~~>*/@CsvSource(value = {"'foo'INVALID,bar"}, useHeadersInDisplayName = true)
                      void test(String first, String second) {}
                  }
                  """));
    }

    @Test
    void marksCsvFileLineSeparatorAtAnnotation() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.params.provider.CsvFileSource;
                  class Tests {
                      @CsvFileSource(resources = "/data.csv", lineSeparator = "|")
                      void test(String first) {}
                  }
                  """,
                """
                  import org.junit.jupiter.params.provider.CsvFileSource;
                  class Tests {
                      /*~~(CsvFileSource.lineSeparator was removed in JUnit 6; it now auto-detects CR, LF, or CRLF. Remove the attribute only after confirming the referenced resource uses one of those separators)~~>*/@CsvFileSource(resources = "/data.csv", lineSeparator = "|")
                      void test(String first) {}
                  }
                  """));
    }

    @Test
    void marksCsvFileWithoutRemovedAttributeForParserReview() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.params.provider.CsvFileSource;
                  class Tests {
                      @CsvFileSource(resources = "/data.csv")
                      void test(String first) {}
                  }
                  """,
                """
                  import org.junit.jupiter.params.provider.CsvFileSource;
                  class Tests {
                      /*~~(JUnit 6 uses FastCSV: malformed quoting, characters after a closing quote, headers, whitespace/null handling, exception types/messages, and parameterized display names may change. Re-run this data set and review assertions)~~>*/@CsvFileSource(resources = "/data.csv")
                      void test(String first) {}
                  }
                  """));
    }

    @Test
    void lineSeparatorTextInResourceNameGetsGenericCsvReview() {
        rewriteRun(java(
                "import org.junit.jupiter.params.provider.CsvFileSource; class Tests { " +
                "@CsvFileSource(resources=\"/lineSeparator.csv\") void test(String value) {} }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindJUnitJupiter6SourceRisks.CSV), printed);
                    assertFalse(printed.contains(FindJUnitJupiter6SourceRisks.LINE_SEPARATOR), printed);
                })));
    }

    @Test
    void marksHashDelimiterForJUnit601CommentCharacterDecision() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.params.provider.CsvSource;
                  class Tests {
                      @CsvSource(value = {"first#second"}, delimiter = '#')
                      void test(String first, String second) {}
                  }
                  """,
                """
                  import org.junit.jupiter.params.provider.CsvSource;
                  class Tests {
                      /*~~(JUnit 6.0.1 uses # as the default FastCSV comment character; this CSV source also selects # as its delimiter. Set commentCharacter explicitly to a non-conflicting value and verify comments, headers, and records)~~>*/@CsvSource(value = {"first#second"}, delimiter = '#')
                      void test(String first, String second) {}
                  }
                  """));
    }

    @Test
    void delimiterTextInsideCsvValueIsNotAConfigurationArgument() {
        rewriteRun(java(
                "import org.junit.jupiter.params.provider.CsvSource; class Tests { " +
                "@CsvSource({\"delimiter = '#',value\"}) void test(String first, String second) {} }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindJUnitJupiter6SourceRisks.CSV), printed);
                    assertFalse(printed.contains(FindJUnitJupiter6SourceRisks.COMMENT_CHARACTER), printed);
                })));
    }

    @Test
    void marksNestedOrdering() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.Nested;
                  class Outer {
                      @Nested
                      class First {}
                  }
                  """,
                """
                  import org.junit.jupiter.api.Nested;
                  class Outer {
                      /*~~(JUnit 6 deterministically reorders sibling @Nested classes and inherits @TestMethodOrder into nested classes; review stateful/order-sensitive tests and add explicit orderers where order is contractual)~~>*/@Nested
                      class First {}
                  }
                  """));
    }

    @Test
    void marksNullableExpressionCreator() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object value(ExtensionContext.Store store) { return store.computeIfAbsent("x", key -> null); }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object value(ExtensionContext.Store store) { return /*~~(JUnit 6 computeIfAbsent contracts require a non-null created value and expose JSpecify nullness; this creator can return null, so define an explicit absence strategy before enabling nullness checks)~~>*/store.computeIfAbsent("x", key -> null); }
                  }
                  """));
    }

    @Test
    void marksNullableBlockCreator() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object value(ExtensionContext.Store store) {
                          return store.computeIfAbsent("x", key -> { if (key == null) return new Object(); return null; });
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object value(ExtensionContext.Store store) {
                          return /*~~(JUnit 6 computeIfAbsent contracts require a non-null created value and expose JSpecify nullness; this creator can return null, so define an explicit absence strategy before enabling nullness checks)~~>*/store.computeIfAbsent("x", key -> { if (key == null) return new Object(); return null; });
                      }
                  }
                  """));
    }

    @Test
    void nonNullCreatorIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension { Object value(ExtensionContext.Store store) { return store.computeIfAbsent("x", key -> new Object()); } }
                  """));
    }

    @Test
    void nullTextAndNestedLambdaDoNotMisclassifyNonNullCreator() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Holder { Holder(java.util.function.Supplier<Object> nested) {} }
                  class Extension {
                      Object first(ExtensionContext.Store store) {
                          return store.computeIfAbsent("return null;", key -> new Object());
                      }
                      Object second(ExtensionContext.Store store) {
                          return store.computeIfAbsent("x", key -> new Holder(() -> null));
                      }
                  }
                  """));
    }

    @Test
    void marksNullableTernaryButAcceptsProvablyNonNullTernary() {
        rewriteRun(
                java("import org.junit.jupiter.api.extension.ExtensionContext; class Nullable { Object x(ExtensionContext.Store s, boolean b) { return s.computeIfAbsent(\"x\", k -> b ? new Object() : null); } }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindJUnitJupiter6SourceRisks.NULL_CREATOR), after.printAll()))),
                java("import org.junit.jupiter.api.extension.ExtensionContext; class NonNull { Object x(ExtensionContext.Store s, boolean b) { return s.computeIfAbsent(\"x\", k -> b ? new Object() : \"fallback\"); } }",
                        source -> source.path("NonNull.java")));
    }

    @Test
    void marksCreatorWhoseNullnessCannotBeProven() {
        rewriteRun(java(
                """
                  import java.util.function.Function;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object value(ExtensionContext.Store store, Function<String, Object> factory) {
                          return store.computeIfAbsent("x", factory);
                      }
                  }
                  """,
                """
                  import java.util.function.Function;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class Extension {
                      Object value(ExtensionContext.Store store, Function<String, Object> factory) {
                          return /*~~(JUnit 6 computeIfAbsent requires a non-null created value; this creator's nullness cannot be proven from the call site. Verify its contract and annotate or guard the result before enabling strict nullness checks)~~>*/store.computeIfAbsent("x", factory);
                      }
                  }
                  """));
    }

    @Test
    void marksNamespacedStoreNullSemanticDecisionWithoutAutoMigration() {
        rewriteRun(java(
                """
                  import org.junit.platform.engine.support.store.NamespacedHierarchicalStore;
                  class EngineSupport {
                      String value(NamespacedHierarchicalStore<String> store) {
                          return store.getOrComputeIfAbsent("namespace", "key", key -> key.toString());
                      }
                  }
                  """,
                """
                  import org.junit.platform.engine.support.store.NamespacedHierarchicalStore;
                  class EngineSupport {
                      String value(NamespacedHierarchicalStore<String> store) {
                          return /*~~(NamespacedHierarchicalStore computeIfAbsent is not behaviorally identical: it rejects a null creator result and treats a previously stored null as absent. Review null storage and creator contracts before replacing this call)~~>*/store.getOrComputeIfAbsent("namespace", "key", key -> key.toString());
                      }
                  }
                  """));
    }

    @Test
    void marksCustomStoreImplementation() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  class FakeStore implements ExtensionContext.Store {}
                  """,
                """
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  /*~~(This custom ExtensionContext.Store implementation must implement/verify the JUnit 6 computeIfAbsent family, non-null contracts, ancestor lookup, and AutoCloseable resource lifecycle)~~>*/class FakeStore implements ExtensionContext.Store {}
                  """));
    }

    @Test
    void sameNamedBusinessAnnotationsAreNoop() {
        rewriteRun(java("@interface Nested {} @interface CsvSource {} @Nested @CsvSource class Business {}"));
    }

    @Test
    void generatedSourceIsNoop() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.api.Nested;
                  @Nested class Generated {}
                  """, source -> source.path("target/generated/Generated.java")));
    }

    private static String marker(String message) {
        return "/*~~(" + message + ")~~>*/";
    }
}
