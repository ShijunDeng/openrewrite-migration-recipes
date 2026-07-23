package com.huawei.clouds.openrewrite.trinojdbc;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class TrinoJdbcJavaRisksTest implements RewriteTest {
    @Override public void defaults(RecipeSpec spec) {
        spec.recipe(new FindTrinoJdbcJavaRisks()).parser(JavaParser.fromJavaVersion().dependsOn(TrinoJdbcTestApi.sources()));
    }

    @Test void marksRemovedDriverUriFactoryWithoutInventingReplacement() {
        rewriteRun(java("""
                import io.trino.jdbc.TrinoDriverUri;
                import java.util.Properties;
                class Uri { Object parse(String value) { return TrinoDriverUri.create(value, new Properties()); } }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(1, count(out, "/*~~("), out); assertTrue(out.contains("no longer public"), out);
        })));
    }

    @Test void marksUrlSecurityAndPropertyReview() {
        rewriteRun(java("class Url { String value = \"jdbc:trino://host:8443/hive?SSL=true&explicitPrepare=false\"; }",
                s -> s.after(a -> a).afterRecipe(after -> {
                    String out = after.printAll(); assertEquals(1, count(out, "/*~~("), out); assertTrue(out.contains("hostnameInCertificate"), out);
                })));
    }

    @Test void marksPreparedStatementBoundary() {
        rewriteRun(java("""
                import io.trino.jdbc.TrinoConnection;
                class Prepared { void run(TrinoConnection c) { c.prepareStatement("SELECT ?"); } }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(1, count(out, "/*~~("), out); assertTrue(out.contains("EXECUTE IMMEDIATE"), out);
        })));
    }

    @Test void marksMetadataNullCatalogCallsOnlyInTrinoSource() {
        rewriteRun(
                java("""
                        import io.trino.jdbc.TrinoConnection;
                        import java.sql.DatabaseMetaData;
                        class Metadata { void scan(DatabaseMetaData m) throws Exception { m.getSchemas(null, "%"); m.getTables(null, null, "%", null); } }
                        """, s -> s.after(a -> a).afterRecipe(after -> {
                            String out = after.printAll(); assertEquals(2, count(out, "/*~~("), out); assertTrue(out.contains("assumeNullCatalogMeansCurrent"), out);
                        })),
                java("import java.sql.DatabaseMetaData; class Other { void scan(DatabaseMetaData m) throws Exception { m.getSchemas(); } }"));
    }

    @Test void marksResultConversionsInTrinoSource() {
        rewriteRun(java("""
                import io.trino.jdbc.TrinoConnection;
                import java.sql.ResultSet;
                class Rows { void read(ResultSet r) throws Exception { r.getObject(1); r.getTimestamp(2); r.getBigDecimal(3); } }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(3, count(out, "/*~~("), out); assertTrue(out.contains("LocalDateTime/timezone"), out);
        })));
    }

    @Test void marksSessionContextAndPoolResetBoundaries() {
        rewriteRun(java("""
                import io.trino.jdbc.TrinoConnection;
                import java.util.Locale;
                class Session { void configure(TrinoConnection c) { c.setCatalog("hive"); c.setSchema("default"); c.setTimeZoneId("UTC"); c.setLocale(Locale.US); c.setSessionProperty("x", "y"); c.setClientInfo("ApplicationName", "app"); } }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(6, count(out, "/*~~("), out); assertTrue(out.contains("pooled-connection reuse"), out);
        })));
    }

    @Test void marksCancellationAndLifecycleCalls() {
        rewriteRun(java("""
                import io.trino.jdbc.TrinoStatement;
                class Stop { void stop(TrinoStatement s) { s.cancel(); s.partialCancel(); s.close(); } }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(3, count(out, "/*~~("), out); assertTrue(out.contains("OpenTelemetry"), out);
        })));
    }

    @Test void marksQueryProgressAndStatisticsConsumers() {
        rewriteRun(java("""
                import io.trino.jdbc.QueryStats;
                class Metrics { long read(QueryStats s) { String id = s.getQueryId(); long bytes = s.getProcessedBytes(); return bytes + s.getWallTimeMillis(); } }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(3, count(out, "/*~~("), out); assertTrue(out.contains("callback threading"), out);
        })));
    }

    @Test void marksInternalImportsAndConcreteSubclasses() {
        rewriteRun(java("""
                import io.trino.jdbc.$internal.Secret;
                import io.trino.jdbc.TrinoStatement;
                class CustomStatement extends TrinoStatement { Secret secret; }
                """, s -> s.after(a -> a).afterRecipe(after -> {
            String out = after.printAll(); assertEquals(2, count(out, "/*~~("), out); assertTrue(out.contains("not a public API"), out); assertTrue(out.contains("Custom subclass"), out);
        })));
    }

    @Test void sameNamedApplicationApisAndUnsignalledJdbcStayClean() {
        rewriteRun(java("""
                class LocalUri { static Object create(String x, java.util.Properties p) { return null; } }
                class LocalConnection { void prepareStatement(String s) {} void setCatalog(String s) {} }
                class App { void x(LocalConnection c) { c.prepareStatement("x"); c.setCatalog("x"); LocalUri.create("x", new java.util.Properties()); } }
                """),
                java("import java.sql.ResultSet; class Generic { void x(ResultSet r) throws Exception { r.getTimestamp(1); } }"));
    }

    @Test void generatedParentIsNoopAndLeafIsIdempotentlyMarked() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("class G { String u = \"jdbc:trino://h\"; }", s -> s.path("build/generated/G.java")),
                java("class install { String u = \"jdbc:trino://h\"; }", s -> s.path("install.java").after(a -> a).afterRecipe(after -> assertEquals(1, count(after.printAll(), "/*~~("), after.printAll()))));
    }

    private static int count(String text, String needle) {
        int n = 0; for (int i = 0; (i = text.indexOf(needle, i)) >= 0; i += needle.length()) n++; return n;
    }
}
