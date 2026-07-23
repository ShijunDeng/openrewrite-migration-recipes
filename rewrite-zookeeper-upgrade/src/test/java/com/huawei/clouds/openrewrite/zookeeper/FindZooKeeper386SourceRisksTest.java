package com.huawei.clouds.openrewrite.zookeeper;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindZooKeeper386SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindZooKeeper386SourceRisks()).parser(targetParser());
    }

    @Test
    void marksClientSessionLifecycleCalls() {
        rewriteRun(java("""
                import org.apache.zookeeper.ZooKeeper;
                class Client {
                    long session(ZooKeeper zk) throws Exception {
                        long id = zk.getSessionId();
                        zk.close();
                        return id;
                    }
                }
                """, assertMarked(FindZooKeeper386SourceRisks.CLIENT_SESSION)));
    }

    @Test
    void marksPersistentWatchBoundary() {
        rewriteRun(java("""
                import org.apache.zookeeper.AddWatchMode;
                import org.apache.zookeeper.Watcher;
                import org.apache.zookeeper.ZooKeeper;
                class Watches {
                    void register(ZooKeeper zk, Watcher watcher) throws Exception {
                        zk.addWatch("/orders", watcher, AddWatchMode.PERSISTENT_RECURSIVE);
                    }
                }
                """, assertMarked(FindZooKeeper386SourceRisks.WATCH)));
    }

    @Test
    void marksAclAndAuthenticationChoices() {
        rewriteRun(java("""
                import java.nio.charset.StandardCharsets;
                import org.apache.zookeeper.ZooDefs;
                import org.apache.zookeeper.ZooKeeper;
                class Auth {
                    void configure(ZooKeeper zk) {
                        zk.addAuthInfo("digest", "user:secret".getBytes(StandardCharsets.UTF_8));
                        Object acl = ZooDefs.Ids.OPEN_ACL_UNSAFE;
                    }
                }
                """, assertMarked(FindZooKeeper386SourceRisks.ACL_AUTH)));
    }

    @Test
    void marksMultiTransactionBoundary() {
        rewriteRun(java("""
                import org.apache.zookeeper.ZooKeeper;
                class Transactions {
                    Object run(ZooKeeper zk) throws Exception {
                        return zk.transaction().check("/orders", -1).commit();
                    }
                }
                """, assertMarked(FindZooKeeper386SourceRisks.MULTI)));
    }

    @Test
    void marksDynamicReconfiguration() {
        rewriteRun(java("""
                import java.util.List;
                import org.apache.zookeeper.admin.ZooKeeperAdmin;
                class Reconfigure {
                    void run(ZooKeeperAdmin zk) throws Exception {
                        zk.reconfigure(List.of("server.4=host:2888:3888:participant;2181"),
                            null, null, -1, null);
                    }
                }
                """, assertMarked(FindZooKeeper386SourceRisks.RECONFIG)));
    }

    @Test
    void marksPersistenceAndTargetAccessor() {
        rewriteRun(java("""
                import java.io.File;
                import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                class Storage {
                    File logDirectory(FileTxnSnapLog log) {
                        return log.getDataLogDir();
                    }
                }
                """, assertMarked(FindZooKeeper386SourceRisks.PERSISTENCE)));
    }

    @Test
    void marksTlsAndSaslInternals() {
        rewriteRun(java("""
                import org.apache.zookeeper.common.X509Util;
                import org.apache.zookeeper.common.ZKConfig;
                class Security {
                    boolean fips(ZKConfig config) {
                        return X509Util.getFipsMode(config);
                    }
                }
                """, assertMarked(FindZooKeeper386SourceRisks.TLS_SASL)));
    }

    @Test
    void marksAuditLoggerUse() {
        rewriteRun(java("""
                import org.apache.zookeeper.audit.Slf4jAuditLogger;
                class Audit {
                    Object logger() {
                        return new Slf4jAuditLogger();
                    }
                }
                """, assertMarked(FindZooKeeper386SourceRisks.AUDIT)));
    }

    @Test
    void marksDirectJuteProtocolUse() {
        rewriteRun(java("""
                import org.apache.zookeeper.data.Stat;
                class Protocol {
                    long zxid(Stat stat) {
                        return stat.getCzxid();
                    }
                }
                """, assertMarked(FindZooKeeper386SourceRisks.JUTE)));
    }

    @Test
    void marksRemovedSaslClientApiUsingLegacyAttribution() {
        rewriteRun(spec -> spec.parser(legacyParser()),
                java("""
                        import org.apache.zookeeper.client.ZooKeeperSaslClient;
                        class LegacySecurity {
                            void stop(ZooKeeperSaslClient client) {
                                client.shutdown();
                            }
                        }
                        """, assertMarked(FindZooKeeper386SourceRisks.TLS_SASL)));
    }

    @Test
    void marksRemovedServerConnectionReturnTypeBoundary() {
        rewriteRun(spec -> spec.parser(legacyParser()),
                java("""
                        import org.apache.zookeeper.server.ServerCnxn;
                        class Embedded {
                            void send(ServerCnxn cnxn) {
                                cnxn.sendResponse();
                            }
                        }
                        """, assertMarked(FindZooKeeper386SourceRisks.SERVER_INTERNAL)));
    }

    @Test
    void realHBaseEmbeddedQuorumFixtureGetsServerAndReconfigurationMarks() {
        // apache/hbase @ 872616e4b45bf2994a63092b272987187bf3e161, Apache-2.0.
        rewriteRun(java("""
                /*
                 * Licensed to the Apache Software Foundation (ASF) under one
                 * or more contributor license agreements.
                 */
                import java.util.Properties;
                import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
                import org.apache.zookeeper.server.quorum.QuorumPeerMain;
                class HQuorumPeer {
                    void run(Properties properties) throws Exception {
                        QuorumPeerConfig config = new QuorumPeerConfig();
                        config.parseProperties(properties);
                        new QuorumPeerMain().runFromConfig(config);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindZooKeeper386SourceRisks.SERVER_INTERNAL), printed);
                    assertTrue(printed.contains(FindZooKeeper386SourceRisks.RECONFIG), printed);
                })));
    }

    @Test
    void realNiFiEmbeddedTlsServerFixtureGetsPersistenceServerAndTlsMarks() {
        // apache/nifi @ c7c745e8c8dcc7518b9d03720c85879cf29281be, Apache-2.0.
        rewriteRun(java("""
                /*
                 * Licensed to the Apache Software Foundation (ASF) under one
                 * or more contributor license agreements.
                 */
                import java.io.File;
                import org.apache.zookeeper.common.X509Util;
                import org.apache.zookeeper.server.ServerCnxnFactory;
                import org.apache.zookeeper.server.ZooKeeperServer;
                import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                class ZooKeeperStateServer {
                    void start(File log, File data, X509Util x509) throws Exception {
                        FileTxnSnapLog transactionLog = new FileTxnSnapLog(log, data);
                        ZooKeeperServer embedded = new ZooKeeperServer();
                        embedded.setTxnLogFactory(transactionLog);
                        ServerCnxnFactory.createFactory().startup(embedded);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindZooKeeper386SourceRisks.PERSISTENCE), printed);
                    assertTrue(printed.contains(FindZooKeeper386SourceRisks.SERVER_INTERNAL), printed);
                    assertTrue(printed.contains(FindZooKeeper386SourceRisks.TLS_SASL), printed);
                })));
    }

    @Test
    void realPinotLocalServerFixtureGetsServerLifecycleMark() {
        // apache/pinot @ 86535a9b6b3ec5e959bff9fb2d8cc14f59fc68aa, Apache-2.0.
        rewriteRun(java("""
                /*
                 * Licensed to the Apache Software Foundation (ASF) under one
                 * or more contributor license agreements.
                 */
                import org.apache.zookeeper.server.ZooKeeperServerMain;
                import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
                class PublicZooKeeperServerMain extends ZooKeeperServerMain {
                    @Override
                    public void initializeAndRun(String[] args) throws Exception {
                        super.initializeAndRun(args);
                    }
                }
                """, assertMarked(FindZooKeeper386SourceRisks.SERVER_INTERNAL)));
    }

    @Test
    void generatedSourceIsIgnored() {
        rewriteRun(java("""
                import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                class Generated { Object p(FileTxnSnapLog log) { return log.getDataLogDir(); } }
                """, source -> source.path("target/generated-sources/Generated.java")));
    }

    @Test
    void ordinaryApplicationSourceIsNotMarked() {
        rewriteRun(java("""
                import java.util.List;
                class Ordinary {
                    int size(List<String> values) { return values.size(); }
                }
                """));
    }

    @Test
    void markersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
                        class Storage { Object p(FileTxnSnapLog log) { return log.getDataLogDir(); } }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertEquals(occurrences(after.printAll(), FindZooKeeper386SourceRisks.PERSISTENCE),
                                occurrences(after.printAll(), FindZooKeeper386SourceRisks.PERSISTENCE)))));
    }

    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().classpath("zookeeper", "zookeeper-jute");
    }

    private static JavaParser.Builder<?, ?> legacyParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package org.apache.zookeeper.client;
                public class ZooKeeperSaslClient { public void shutdown() {} }
                """,
                """
                package org.apache.zookeeper.server;
                public class ServerCnxn { public void sendResponse() {} }
                """);
    }

    private static java.util.function.Consumer<org.openrewrite.test.SourceSpec<org.openrewrite.java.tree.J.CompilationUnit>>
    assertMarked(String marker) {
        return source -> source.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(marker), after.printAll()));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
