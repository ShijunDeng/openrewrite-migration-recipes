package com.huawei.clouds.openrewrite.trinojdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class MigrateTrinoJdbc418ApisTest implements RewriteTest {
    @Override public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateTrinoJdbc418Apis()).parser(JavaParser.fromJavaVersion().dependsOn(TrinoJdbcTestApi.sources()));
    }

    @Test void renamesAttributedTrinoConnectionMethodAndPrimitiveResult() {
        rewriteRun(java("""
                import io.trino.jdbc.TrinoConnection;
                class Mode { boolean explicit(TrinoConnection connection) { return connection.isUseLegacyPreparedStatements(); } }
                """, """
                import io.trino.jdbc.TrinoConnection;
                class Mode { boolean explicit(TrinoConnection connection) { return connection.useExplicitPrepare(); } }
                """));
    }

    @Test void renamesChainedCastButNotSameNamedApplicationMethod() {
        rewriteRun(
                java("""
                        import io.trino.jdbc.TrinoConnection;
                        class Mode { Boolean explicit(Object connection) { return ((TrinoConnection) connection).isUseLegacyPreparedStatements(); } }
                        """, """
                        import io.trino.jdbc.TrinoConnection;
                        class Mode { Boolean explicit(Object connection) { return ((TrinoConnection) connection).useExplicitPrepare(); } }
                        """),
                java("""
                        class LocalConnection { Boolean isUseLegacyPreparedStatements() { return true; } }
                        class Local { boolean x(LocalConnection connection) { return connection.isUseLegacyPreparedStatements(); } }
                        """));
    }

    @ParameterizedTest(name = "URL query position {0}") @MethodSource("urlRenames")
    void renamesExactUrlQueryKey(String label, String before, String after) {
        rewriteRun(java("class Url { String value = \"" + before + "\"; }",
                "class Url { String value = \"" + after + "\"; }"));
    }

    static Stream<Arguments> urlRenames() {
        return Stream.of(
                Arguments.of("first false", "jdbc:trino://host:8443/hive/default?legacyPreparedStatements=false", "jdbc:trino://host:8443/hive/default?explicitPrepare=false"),
                Arguments.of("first true", "jdbc:trino://host/hive?legacyPreparedStatements=true&SSL=true", "jdbc:trino://host/hive?explicitPrepare=true&SSL=true"),
                Arguments.of("middle", "jdbc:trino://host?user=test&legacyPreparedStatements=false&timezone=UTC", "jdbc:trino://host?user=test&explicitPrepare=false&timezone=UTC"),
                Arguments.of("last", "jdbc:trino://host?SSL=true&legacyPreparedStatements=true", "jdbc:trino://host?SSL=true&explicitPrepare=true"),
                Arguments.of("repeated", "jdbc:trino://host?legacyPreparedStatements=true&legacyPreparedStatements=false", "jdbc:trino://host?explicitPrepare=true&explicitPrepare=false"),
                Arguments.of("fragment preserved", "jdbc:trino://host?legacyPreparedStatements=false#legacyPreparedStatements=true", "jdbc:trino://host?explicitPrepare=false#legacyPreparedStatements=true"),
                Arguments.of("empty value", "jdbc:trino://host?legacyPreparedStatements=", "jdbc:trino://host?explicitPrepare="),
                Arguments.of("encoded value", "jdbc:trino://host?legacyPreparedStatements=%66alse", "jdbc:trino://host?explicitPrepare=%66alse")
        );
    }

    @ParameterizedTest(name = "URL no-op {0}") @MethodSource("urlNoops")
    void preservesUnrelatedOrAmbiguousStrings(String label, String value) {
        rewriteRun(java("class Url { String value = \"" + value + "\"; }"));
    }

    static Stream<Arguments> urlNoops() {
        return Stream.of(
                Arguments.of("already modern", "jdbc:trino://host?explicitPrepare=false"),
                Arguments.of("nearby key", "jdbc:trino://host?legacyPreparedStatementsExtra=false"),
                Arguments.of("case differs", "jdbc:trino://host?LegacyPreparedStatements=false"),
                Arguments.of("fragment only", "jdbc:trino://host/path#x&legacyPreparedStatements=false"),
                Arguments.of("not query", "jdbc:trino://host/path/legacyPreparedStatements=false"),
                Arguments.of("other JDBC", "jdbc:postgresql://host/db?legacyPreparedStatements=false"),
                Arguments.of("ordinary text", "legacyPreparedStatements=false"),
                Arguments.of("no query", "jdbc:trino://localhost:3306/test")
        );
    }

    @Test void migratesApacheFlinkPreparedStatementFixtureShape() {
        // Extracted from apache/flink-connector-jdbc@7c0710d98c013347776e5162aa8f827d53f4917b,
        // TrinoPreparedStatementTest.java; the historical property is added to exercise this migration.
        rewriteRun(java("""
                class TrinoPreparedStatementFixture {
                    String url() { return "jdbc:trino://localhost:3306/test?legacyPreparedStatements=false"; }
                }
                """, """
                class TrinoPreparedStatementFixture {
                    String url() { return "jdbc:trino://localhost:3306/test?explicitPrepare=false"; }
                }
                """));
    }

    @Test void generatedParentIsNoopAndLeafInstallIsMigratedIdempotently() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("class Generated { String u = \"jdbc:trino://h?legacyPreparedStatements=false\"; }", s -> s.path("target/generated/Generated.java")),
                java("class install { String u = \"jdbc:trino://h?legacyPreparedStatements=false\"; }",
                        "class install { String u = \"jdbc:trino://h?explicitPrepare=false\"; }", s -> s.path("install.java")));
    }

    @Test void helperDoesNotModifyFragmentOrPath() {
        org.junit.jupiter.api.Assertions.assertEquals(
                "jdbc:trino://h/legacyPreparedStatements=false#x&legacyPreparedStatements=false",
                MigrateTrinoJdbc418Apis.migrateUrl("jdbc:trino://h/legacyPreparedStatements=false#x&legacyPreparedStatements=false"));
    }
}
