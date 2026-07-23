package com.huawei.clouds.openrewrite.zookeeper;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class MigrateDeterministicZooKeeperApisTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.zookeeper.MigrateDeterministicZooKeeperApis";

    @Test
    void recipeTreeContainsTheTwoOfficialTypeAwareBuildingBlocks() {
        Recipe recipe = recipe();
        List<String> names = recipe.getRecipeList().stream().map(Recipe::getName).toList();
        assertTrue(names.contains("org.openrewrite.java.ChangeMethodName"), names.toString());
        assertTrue(names.contains("org.openrewrite.java.ChangeType"), names.toString());
    }

    @Test
    void renamesFileTxnSnapLogDataDirectoryAccessor() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                java("""
                        import java.io.File;
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class SnapshotAdmin {
                            File transactionDirectory(FileTxnSnapLog snapLog) {
                                return snapLog.getDataDir();
                            }
                        }
                        """, """
                        import java.io.File;
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class SnapshotAdmin {
                            File transactionDirectory(FileTxnSnapLog snapLog) {
                                return snapLog.getDataLogDir();
                            }
                        }
                        """));
    }

    @Test
    void renamesMethodReferenceWithAttribution() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                java("""
                        import java.io.File;
                        import java.util.function.Function;
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class Paths {
                            Function<FileTxnSnapLog, File> data = FileTxnSnapLog::getDataDir;
                        }
                        """, """
                        import java.io.File;
                        import java.util.function.Function;
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class Paths {
                            Function<FileTxnSnapLog, File> data = FileTxnSnapLog::getDataLogDir;
                        }
                        """));
    }

    @Test
    void changesAuditLoggerTypeInImportsConstructionAndDeclarations() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                java("""
                        import org.apache.zookeeper.audit.Log4jAuditLogger;
                        class AuditConfiguration {
                            Log4jAuditLogger logger() {
                                return new Log4jAuditLogger();
                            }
                        }
                        """, """
                        import org.apache.zookeeper.audit.Slf4jAuditLogger;

                        class AuditConfiguration {
                            Slf4jAuditLogger logger() {
                                return new Slf4jAuditLogger();
                            }
                        }
                        """));
    }

    @Test
    void leavesUnattributedLookalikeMethodsAndTypesUntouched() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                java("""
                        class FileTxnSnapLog {
                            java.io.File getDataDir() { return null; }
                        }
                        class Log4jAuditLogger {}
                        class App {
                            Object path(FileTxnSnapLog log) { return log.getDataDir(); }
                            Log4jAuditLogger logger = new Log4jAuditLogger();
                        }
                        """));
    }

    @Test
    void definitionsAreNotRenamedWhenZooKeeperSourcesAreInTheInputSet() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(JavaParser.fromJavaVersion()),
                java("""
                        package org.apache.zookeeper.server.persistence;
                        public class FileTxnSnapLog {
                            public java.io.File getDataDir() { return null; }
                            public java.io.File getDataLogDir() { return null; }
                        }
                        """, source -> source.path(
                        "src/main/java/org/apache/zookeeper/server/persistence/FileTxnSnapLog.java")));
    }

    @Test
    void officialRecipesIgnoreGeneratedInputs() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                java("""
                        import org.apache.zookeeper.audit.Log4jAuditLogger;
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class GeneratedAdapter {
                            Object p(FileTxnSnapLog log) { return log.getDataDir(); }
                            Log4jAuditLogger logger = new Log4jAuditLogger();
                        }
                        """, source -> source.path("target/generated-sources/GeneratedAdapter.java")));
    }

    @Test
    void realZooKeeper4730PurgeTxnLogFixtureUsesOfficialMethodRename() {
        // Fixed source evidence: apache/zookeeper @ b8eb6a301beceae92a60e8be1a8d716a1109c82f
        // (ZOOKEEPER-4730, Apache-2.0). PurgeTxnLog used the accessor for transaction logs.
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                java("""
                        /*
                         * Licensed to the Apache Software Foundation (ASF) under one
                         * or more contributor license agreements.
                         */
                        import java.io.File;
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class PurgeTxnLog {
                            static File[] transactionLogs(FileTxnSnapLog txnLog) {
                                return txnLog.getDataDir().listFiles();
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains("txnLog.getDataLogDir().listFiles()"),
                                after.printAll()))));
    }

    @Test
    void realApacheDorisGetDataDirLookalikeIsUntouched() {
        // apache/doris @ 8a1bf788e290dc5832c4bd432a34d0e8b4c42906, Apache-2.0.
        // StorageMediaMigrationTask owns an unrelated business accessor with the same method name.
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser()),
                java("""
                        /*
                         * Licensed to the Apache Software Foundation (ASF) under one
                         * or more contributor license agreements.
                         */
                        package org.apache.doris.task;
                        class StorageMediaMigrationTask {
                            private String dataDir;
                            public String getDataDir() {
                                return dataDir;
                            }
                            public void setDataDir(String dataDir) {
                                this.dataDir = dataDir;
                            }
                        }
                        """));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.recipe(recipe()).parser(legacyParser())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.apache.zookeeper.audit.Log4jAuditLogger;
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class Adapter {
                            Object p(FileTxnSnapLog log) { return log.getDataDir(); }
                            Log4jAuditLogger logger = new Log4jAuditLogger();
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("getDataLogDir()"), printed);
                    assertTrue(printed.contains("Slf4jAuditLogger"), printed);
                })));
    }

    private static Recipe recipe() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.zookeeper", "org.openrewrite.java")
                .build().activateRecipes(RECIPE);
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
                public class Log4jAuditLogger {
                    public Log4jAuditLogger() {}
                }
                """,
                """
                package org.apache.zookeeper.audit;
                public class Slf4jAuditLogger {
                    public Slf4jAuditLogger() {}
                }
                """);
    }
}
