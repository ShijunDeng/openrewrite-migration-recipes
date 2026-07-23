package com.huawei.clouds.openrewrite.zookeeper;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class ZooKeeperRecommendedRecipeTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.zookeeper.MigrateZooKeeperTo3_8_6";

    @Test
    void descriptorComposesOfficialAndCustomCapabilitiesInAuditedOrder() {
        Recipe recipe = recipe();
        List<String> names = recipe.getRecipeList().stream().map(Recipe::getName).toList();
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.zookeeper.UpgradeSelectedZooKeeperDependency",
                "com.huawei.clouds.openrewrite.zookeeper.FindZooKeeper386BuildRisks",
                "com.huawei.clouds.openrewrite.zookeeper.MigrateDeterministicZooKeeperApis",
                "com.huawei.clouds.openrewrite.zookeeper.MigrateDeterministicZooKeeperConfiguration",
                "com.huawei.clouds.openrewrite.zookeeper.FindZooKeeper386SourceRisks",
                "com.huawei.clouds.openrewrite.zookeeper.FindZooKeeperConfigurationRisks"), names);

        List<String> javaChildren = recipe.getRecipeList().get(2).getRecipeList().stream()
                .map(Recipe::getName).toList();
        assertTrue(javaChildren.contains("org.openrewrite.java.ChangeMethodName"), javaChildren.toString());
        assertTrue(javaChildren.contains("org.openrewrite.java.ChangeType"), javaChildren.toString());

        List<String> configChildren = recipe.getRecipeList().get(3).getRecipeList().stream()
                .map(Recipe::getName).toList();
        assertTrue(configChildren.contains("org.openrewrite.properties.ChangePropertyValue"),
                configChildren.toString());
    }

    @Test
    void dependencyUpgradeRunsBeforeBuildAudit() {
        rewriteRun(spec -> spec.recipe(recipe()),
                xml(UpgradeZooKeeperDependencyTest.pom("3.7.1"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>3.8.6</version>"), printed);
                            assertFalse(printed.contains(FindZooKeeper386BuildRisks.OUTSIDE), printed);
                            assertFalse(printed.contains(FindZooKeeper386BuildRisks.NO_DOWNGRADE_PREFIX), printed);
                        })));
    }

    @Test
    void threeNineWorkbookInputStaysUnchangedAndGetsExactConflictMarker() {
        rewriteRun(spec -> spec.recipe(recipe()),
                xml(UpgradeZooKeeperDependencyTest.pom("3.9.4"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>3.9.4</version>"), printed);
                            assertTrue(printed.contains(FindZooKeeper386BuildRisks.NO_DOWNGRADE_PREFIX), printed);
                            assertFalse(printed.contains("<version>3.8.6</version>"), printed);
                        })));
    }

    @Test
    void officialApiRenameAndPersistenceAuditRunTogether() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                java("""
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class StorageAdmin {
                            Object path(FileTxnSnapLog log) {
                                return log.getDataDir();
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("log.getDataLogDir()"), printed);
                    assertTrue(printed.contains(FindZooKeeper386SourceRisks.PERSISTENCE), printed);
                })));
    }

    @Test
    void officialTypeRenameAndAuditReviewRunTogether() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                java("""
                        import org.apache.zookeeper.audit.Log4jAuditLogger;
                        class AuditProvider {
                            Log4jAuditLogger logger = new Log4jAuditLogger();
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("Slf4jAuditLogger"), printed);
                    assertFalse(printed.contains("Log4jAuditLogger"), printed);
                    assertTrue(printed.contains(FindZooKeeper386SourceRisks.AUDIT), printed);
                })));
    }

    @Test
    void exactPropertiesAutoMigrationIsFollowedByAuditMarker() {
        rewriteRun(spec -> spec.recipe(recipe()),
                properties("zookeeper.audit.impl.class=org.apache.zookeeper.audit.Log4jAuditLogger",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("org.apache.zookeeper.audit.Slf4jAuditLogger"), printed);
                            assertTrue(printed.contains(FindZooKeeperConfigurationRisks.AUDIT), printed);
                        })));
    }

    @Test
    void yamlAutoMigrationAndOperationalDataReviewRunTogether() {
        rewriteRun(spec -> spec.recipe(recipe()),
                yaml("""
                        zookeeper:
                          audit:
                            impl:
                              class: org.apache.zookeeper.audit.Log4jAuditLogger
                          snapshot:
                            trust:
                              empty: false
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("org.apache.zookeeper.audit.Slf4jAuditLogger"), printed);
                    assertTrue(printed.contains(FindZooKeeperConfigurationRisks.AUDIT), printed);
                    assertTrue(printed.contains(FindZooKeeperConfigurationRisks.DATA), printed);
                })));
    }

    @Test
    void officialAndCustomAutosIgnoreGeneratedFiles() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                xml(UpgradeZooKeeperDependencyTest.pom("3.7.1"),
                        source -> source.path("target/generated/pom.xml")),
                java("""
                        import org.apache.zookeeper.audit.Log4jAuditLogger;
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class Generated {
                            Object p(FileTxnSnapLog log) { return log.getDataDir(); }
                            Log4jAuditLogger logger = new Log4jAuditLogger();
                        }
                        """, source -> source.path("build/generated/sources/Generated.java")),
                properties("zookeeper.audit.impl.class=org.apache.zookeeper.audit.Log4jAuditLogger",
                        source -> source.path("target/classes/zoo.cfg")));
    }

    @Test
    void fullCompositionIsIdempotent() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeZooKeeperDependencyTest.pom("3.8.4"),
                        UpgradeZooKeeperDependencyTest.pom("3.8.6"),
                        source -> source.path("pom.xml")),
                java("""
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class Storage { Object p(FileTxnSnapLog log) { return log.getDataDir(); } }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(1, occurrences(printed, "getDataLogDir()"));
                    assertFalse(printed.contains("getDataDir()"), printed);
                })),
                properties(
                        "zookeeper.audit.impl.class=org.apache.zookeeper.audit.Log4jAuditLogger",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(),
                                        "org.apache.zookeeper.audit.Slf4jAuditLogger")))));
    }

    @Test
    void realZooKeeperAndHBasePatternsCanBeProcessedInOneRun() {
        // apache/zookeeper @ b8eb6a301beceae92a60e8be1a8d716a1109c82f
        // apache/hbase @ 872616e4b45bf2994a63092b272987187bf3e161
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                xml(UpgradeZooKeeperDependencyTest.pom("3.4.14"),
                        source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("3.8.6"), after.printAll()))),
                java("""
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
                        class EmbeddedAdmin {
                            Object logDir(FileTxnSnapLog log) { return log.getDataDir(); }
                            void parse(QuorumPeerConfig config, java.util.Properties properties) throws Exception {
                                config.parseProperties(properties);
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("getDataLogDir"), printed);
                    assertTrue(printed.contains(FindZooKeeper386SourceRisks.PERSISTENCE), printed);
                    assertTrue(printed.contains(FindZooKeeper386SourceRisks.RECONFIG), printed);
                })));
    }

    private static Recipe recipe() {
        return Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.zookeeper", "org.openrewrite.java",
                "org.openrewrite.properties").build().activateRecipes(RECIPE);
    }

    private static JavaParser.Builder<?, ?> legacyParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package org.apache.zookeeper.server.persistence;
                public class FileTxnSnapLog {
                    public java.io.File getDataDir() { return null; }
                    public java.io.File getDataLogDir() { return null; }
                }
                """,
                """
                package org.apache.zookeeper.audit;
                public class Log4jAuditLogger { public Log4jAuditLogger() {} }
                """,
                """
                package org.apache.zookeeper.audit;
                public class Slf4jAuditLogger { public Slf4jAuditLogger() {} }
                """,
                """
                package org.apache.zookeeper.server.quorum;
                public class QuorumPeerConfig {
                    public void parseProperties(java.util.Properties properties) {}
                }
                """);
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
