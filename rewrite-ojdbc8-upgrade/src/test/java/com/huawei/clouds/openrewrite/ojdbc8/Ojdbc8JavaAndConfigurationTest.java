package com.huawei.clouds.openrewrite.ojdbc8;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class Ojdbc8JavaAndConfigurationTest implements RewriteTest {
    private static final String MIGRATE = "com.huawei.clouds.openrewrite.ojdbc8.MigrateOjdbc8To23_26_1_0_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(UpgradeOjdbc8DependencyTest.environment().activateRecipes(MIGRATE));
    }

    @Test
    void migratesLegacyDriverTypeFromOracleSample() {
        // oracle-samples/oracle-db-examples, fixed commit 1c5a3f18169084be1982a5c16a51ea16f7474b81.
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package oracle.jdbc.driver; public class OracleDriver implements java.sql.Driver { public java.sql.Connection connect(String u, java.util.Properties p) { return null; } public boolean acceptsURL(String u) { return false; } public java.sql.DriverPropertyInfo[] getPropertyInfo(String u, java.util.Properties p) { return null; } public int getMajorVersion() { return 1; } public int getMinorVersion() { return 0; } public boolean jdbcCompliant() { return true; } public java.util.logging.Logger getParentLogger() { return null; } }"
                )),
                java("import oracle.jdbc.driver.OracleDriver; class Server { OracleDriver driver = new OracleDriver(); }",
                     "import oracle.jdbc.OracleDriver;\n\nclass Server { OracleDriver driver = new OracleDriver(); }")
        );
    }

    @Test
    void migratesExactDriverClassStringFromBeamShape() {
        // apache/beam, fixed commit c1e44fb0258582207c38100bf33368d6dc3a33e9.
        rewriteRun(java("class JdbcUtil { String driver = \"oracle.jdbc.driver.OracleDriver\"; }",
                "class JdbcUtil { String driver = \"oracle.jdbc.OracleDriver\"; }"));
    }

    @Test
    void doesNotRewriteSimilarDriverText() {
        rewriteRun(java("class Text { String a = \"use oracle.jdbc.driver.OracleDriver here\"; String b = \"oracle.jdbc.driver.OracleDriverFactory\"; }"));
    }

    @Test
    void renamesDocumentedEquivalentLobMethods() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package oracle.jdbc; public interface OracleBlob { void open(int mode); void close(); boolean isOpen(); }",
                        "package oracle.jdbc; public interface OracleClob { void open(int mode); void close(); boolean isOpen(); }",
                        "package oracle.jdbc; public interface OracleBfile { void open(int mode); void close(); boolean isOpen(); }"
                )),
                java("import oracle.jdbc.*; class Lobs { void use(OracleBlob b, OracleClob c, OracleBfile f) { b.open(1); b.close(); b.isOpen(); c.open(1); c.close(); c.isOpen(); f.open(1); f.close(); f.isOpen(); } }",
                     "import oracle.jdbc.*; class Lobs { void use(OracleBlob b, OracleClob c, OracleBfile f) { b.openLob(1); b.closeLob(); b.isOpenLob(); c.openLob(1); c.closeLob(); c.isOpenLob(); f.openLob(1); f.closeLob(); f.isOpenLob(); } }")
        );
    }

    @Test
    void renamesDocumentedEquivalentStatementCacheMethods() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package oracle.jdbc; public interface OracleConnection { int getStmtCacheSize(); void setStmtCacheSize(int size); }"
                )),
                java("import oracle.jdbc.OracleConnection; class Cache { void use(OracleConnection c) { int n = c.getStmtCacheSize(); c.setStmtCacheSize(n); } }",
                     "import oracle.jdbc.OracleConnection; class Cache { void use(OracleConnection c) { int n = c.getStatementCacheSize(); c.setStatementCacheSize(n); } }")
        );
    }

    @Test
    void sameNamedApplicationMethodIsNotRenamed() {
        rewriteRun(java("class Cache { int getStmtCacheSize() { return 1; } void setStmtCacheSize(int n) {} void use() { setStmtCacheSize(getStmtCacheSize()); } }"));
    }

    @Test
    void marksTwoArgumentStatementCacheDecision() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package oracle.jdbc; public interface OracleConnection { void setStmtCacheSize(int size, boolean clearMetaData); }"
                )),
                java("import oracle.jdbc.OracleConnection; class Cache { void use(OracleConnection c) { c.setStmtCacheSize(10, true); } }",
                        source -> contains(source, "two-argument setStmtCacheSize"))
        );
    }

    @Test
    void marksRemovedOracleRowsetFromDbfitShape() {
        // dbfit/dbfit, fixed commit 096e4757cfe24f20fe45d7c72a333202b9a9880a.
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package oracle.jdbc.rowset; public class OracleCachedRowSet {}"
                )),
                java("import oracle.jdbc.rowset.OracleCachedRowSet; class Normaliser { OracleCachedRowSet rows; }",
                        source -> contains(source, "rowset implementation package"))
        );
    }

    @Test
    void marksRemovedImplicitConnectionCacheManager() {
        // psi-probe/psi-probe, fixed commit e2ad89e252cbcd237611cf89485e7071a2edc6b7.
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package oracle.jdbc.pool; public class OracleConnectionCacheManager {}"
                )),
                java("import oracle.jdbc.pool.OracleConnectionCacheManager; class Pool { OracleConnectionCacheManager manager; }",
                        source -> contains(source, "Implicit Connection Cache"))
        );
    }

    @Test
    void marksOracleSqlConcreteType() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn("package oracle.sql; public class STRUCT {}")),
                java("import oracle.sql.STRUCT; class Value { STRUCT struct; }", source -> contains(source, "oracle.sql concrete types"))
        );
    }

    @Test
    void marksOracleStyleBatching() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package oracle.jdbc; public interface OraclePreparedStatement { void setExecuteBatch(int size); int sendBatch(); }"
                )),
                java("import oracle.jdbc.OraclePreparedStatement; class Batch { void use(OraclePreparedStatement p) { p.setExecuteBatch(10); p.sendBatch(); } }",
                        source -> contains(source, "Oracle-style batching"))
        );
    }

    @Test
    void marksUcpAndWalletTypes() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package oracle.ucp.jdbc; public class PoolDataSourceImpl {}",
                        "package oracle.security.pki; public class OracleWallet {}"
                )),
                java("import oracle.ucp.jdbc.PoolDataSourceImpl; import oracle.security.pki.OracleWallet; class Pools { PoolDataSourceImpl pool; OracleWallet wallet; }",
                        source -> { contains(source, "uses Oracle UCP"); contains(source, "Wallet/PKI APIs"); })
        );
    }

    @Test
    void marksExplicitDriverLoadingButNotUnrelatedClassLoading() {
        rewriteRun(java("class Loader { void load(String n) throws Exception { Class.forName(\"oracle.jdbc.driver.OracleDriver\"); Class.forName(\"java.lang.String\"); Class.forName(n); } }",
                source -> { contains(source, "JDBC 4 service loading"); source.afterRecipe(after -> assertTrue(occurrences(after.printAll(), "JDBC 4 service loading") == 1)); }));
    }

    @Test
    void marksOldAutomaticModuleName() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                text("module app {\n    requires java.sql;\n    requires ojdbc8;\n    requires static transitive ojdbc8;\n}\n",
                        source -> source.path("src/main/java/module-info.java")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("requires java.sql;"));
                                    assertTrue(occurrences(printed, "derived JPMS module name") == 2);
                                })));
    }

    @Test
    void marksSidAndDescriptorAndOciUrls() {
        rewriteRun(java("class Urls { String sid = \"jdbc:oracle:thin:@db:1521:ORCL\"; String descriptor = \"jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=db)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=svc)))\"; String oci = \"jdbc:oracle:oci:@ORCL\"; }",
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("host:port:SID form"));
                    assertTrue(printed.contains("embeds a connect descriptor"));
                    assertTrue(printed.contains("OCI/Type 2 driver"));
                })));
    }

    @Test
    void ordinaryServiceNameUrlIsNotMarked() {
        rewriteRun(java("class Url { String value = \"jdbc:oracle:thin:@//db.example:1521/orders\"; }"));
    }

    @Test
    void updatesExactPropertiesDriverAndMarksConnectionOwners() {
        rewriteRun(properties(
                "spring.datasource.driver-class-name=oracle.jdbc.driver.OracleDriver\nspring.datasource.url=jdbc:oracle:thin:@db:1521:ORCL\noracle.net.tns_admin=/run/tns\nspring.datasource.hikari.data-source-properties.oracle.net.wallet_location=/run/wallet\nucp.fastConnectionFailoverEnabled=true\n",
                source -> source.after(actual -> actual.replace("oracle.jdbc.driver.OracleDriver", "oracle.jdbc.OracleDriver"))
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("Validate this Oracle JDBC URL"));
                            assertTrue(printed.contains("Validate TNS_ADMIN"));
                            assertTrue(printed.contains("Validate Oracle wallet"));
                            assertTrue(printed.contains("Align UCP/ONS"));
                        })));
    }

    @Test
    void updatesExactYamlDriverAndMarksWallet() {
        rewriteRun(yaml("spring:\n  datasource:\n    driver-class-name: oracle.jdbc.driver.OracleDriver\n    wallet: /run/wallet\n",
                source -> source.after(actual -> actual.replace("oracle.jdbc.driver.OracleDriver", "oracle.jdbc.OracleDriver"))
                        .afterRecipe(after -> assertTrue(after.printAll().contains("Validate Oracle wallet")))));
    }

    @Test
    void updatesXmlTagAndAttributeDriverAndMarksTns() {
        rewriteRun(xml("<datasource driver=\"oracle.jdbc.driver.OracleDriver\"><driver>oracle.jdbc.driver.OracleDriver</driver><tns-admin>/run/tns</tns-admin></datasource>",
                source -> source.after(actual -> actual.replace("oracle.jdbc.driver.OracleDriver", "oracle.jdbc.OracleDriver"))
                        .afterRecipe(after -> assertTrue(after.printAll().contains("Validate TNS_ADMIN")))));
    }

    @Test
    void configDoesNotRewriteDriverSubstring() {
        rewriteRun(properties("note=use oracle.jdbc.driver.OracleDriver explicitly\n"), yaml("note: oracle.jdbc.driver.OracleDriverFactory\n"));
    }

    @Test
    void generatedJavaAndConfigurationAreSkipped() {
        rewriteRun(
                java("class Generated { String d = \"oracle.jdbc.driver.OracleDriver\"; }", source -> source.path("target/generated-sources/Generated.java")),
                properties("driver=oracle.jdbc.driver.OracleDriver\n", source -> source.path("install/conf/db.properties")),
                yaml("driver: oracle.jdbc.driver.OracleDriver\n", source -> source.path("build/generated/db.yml"))
        );
    }

    @Test
    void deterministicChangesAndMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("class Db { String driver = \"oracle.jdbc.driver.OracleDriver\"; String url = \"jdbc:oracle:thin:@db:1521:ORCL\"; }",
                        source -> source.after(actual -> actual.replace("oracle.jdbc.driver.OracleDriver", "oracle.jdbc.OracleDriver"))
                                .afterRecipe(after -> {
                                    assertTrue(occurrences(after.printAll(), "host:port:SID form") == 1);
                                    assertFalse(after.printAll().contains("oracle.jdbc.driver.OracleDriver"));
                                }))
        );
    }

    private static void contains(org.openrewrite.test.SourceSpec<?> source, String token) {
        source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains(token), after::printAll));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        for (int i = 0; (i = source.indexOf(token, i)) >= 0; i += token.length()) count++;
        return count;
    }
}
