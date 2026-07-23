package com.huawei.clouds.openrewrite.zookeeper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class FindZooKeeper386BuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindZooKeeper386BuildRisks());
    }

    @ParameterizedTest(name = "workbook conflict {0}")
    @ValueSource(strings = {"3.9.3", "3.9.4"})
    void marksWorkbookConflictsInAllBuildDialectsWithoutChangingThem(String version) {
        rewriteRun(
                xml(UpgradeZooKeeperDependencyTest.pom(version), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> assertConflict(after.printAll(), version))),
                buildGradle("dependencies { implementation 'org.apache.zookeeper:zookeeper:" + version + "' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertConflict(after.printAll(), version))),
                buildGradleKts("dependencies { implementation(\"org.apache.zookeeper:zookeeper:" + version + "\") }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertConflict(after.printAll(), version))));
    }

    @ParameterizedTest(name = "future target conflict {0}")
    @ValueSource(strings = {"3.8.7", "3.9.0", "3.10.0", "4.0.0"})
    void marksEveryFixedHigherVersionAsNoDowngrade(String version) {
        rewriteRun(xml(UpgradeZooKeeperDependencyTest.pom(version), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> assertConflict(after.printAll(), version))));
    }

    @ParameterizedTest(name = "approved or target {0}")
    @ValueSource(strings = {"3.4.14", "3.6.0", "3.7.1", "3.8.3", "3.8.4", "3.8.6"})
    void approvedAndTargetVersionsDoNotGetPrimaryVersionMarkers(String version) {
        rewriteRun(xml(UpgradeZooKeeperDependencyTest.pom(version), source -> source.path("pom.xml")));
    }

    @ParameterizedTest(name = "outside source {0}")
    @ValueSource(strings = {
            "3.4.13", "3.5.6-hw-ei-302002", "3.5.10", "3.6.3-hw-ei-312002",
            "3.6.4", "3.7.2", "3.8.0", "3.8.5"
    })
    void marksOutsideFixedSources(String version) {
        rewriteRun(xml(UpgradeZooKeeperDependencyTest.pom(version), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindZooKeeper386BuildRisks.OUTSIDE),
                                after.printAll()))));
    }

    @ParameterizedTest(name = "unresolved source {0}")
    @ValueSource(strings = {"${external.version}", "[3.4,3.9)", "3.8.+", "latest.release"})
    void marksUnresolvedOwners(String version) {
        rewriteRun(xml(UpgradeZooKeeperDependencyTest.pom(version), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindZooKeeper386BuildRisks.OWNER),
                                after.printAll()))));
    }

    @Test
    void resolvesRootAndProfilePropertiesBeforeClassifyingConflict() {
        String pom = UpgradeZooKeeperDependencyTest.project(
                "<properties><zk>3.9.4</zk></properties><dependencies>" +
                UpgradeZooKeeperDependencyTest.dep("${zk}") + "</dependencies>" +
                "<profiles><profile><id>it</id><properties><zk>3.9.3</zk></properties><dependencies>" +
                UpgradeZooKeeperDependencyTest.dep("${zk}") + "</dependencies></profile></profiles>");
        rewriteRun(xml(pom, source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("3.9.4 is newer"), printed);
                    assertTrue(printed.contains("3.9.3 is newer"), printed);
                    assertEquals(2, occurrences(printed, FindZooKeeper386BuildRisks.NO_DOWNGRADE_PREFIX));
                })));
    }

    @Test
    void marksVariantsAndKeepsCoordinates() {
        rewriteRun(
                xml(UpgradeZooKeeperDependencyTest.project("<dependencies>" +
                        UpgradeZooKeeperDependencyTest.dep("3.7.1", "<classifier>tests</classifier>") +
                        UpgradeZooKeeperDependencyTest.dep("3.8.3", "<type>zip</type>") +
                        "</dependencies>"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertEquals(2, occurrences(printed, FindZooKeeper386BuildRisks.VARIANT));
                            assertTrue(printed.contains("3.7.1"), printed);
                            assertTrue(printed.contains("3.8.3"), printed);
                        })),
                buildGradle("dependencies { implementation 'org.apache.zookeeper:zookeeper:3.7.1:tests' }",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindZooKeeper386BuildRisks.VARIANT),
                                        after.printAll()))));
    }

    @Test
    void marksJuteSkewAndPrimaryExclusions() {
        String pom = UpgradeZooKeeperDependencyTest.project("<dependencies>" +
                UpgradeZooKeeperDependencyTest.dep("3.8.6",
                        "<exclusions><exclusion><groupId>org.apache.zookeeper</groupId>" +
                        "<artifactId>zookeeper-jute</artifactId></exclusion></exclusions>") +
                "<dependency><groupId>org.apache.zookeeper</groupId><artifactId>zookeeper-jute</artifactId>" +
                "<version>3.7.1</version></dependency></dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertEquals(2, occurrences(after.printAll(), FindZooKeeper386BuildRisks.JUTE)))));
    }

    @Test
    void marksLoggingBindingsAndTransportOverrides() {
        String pom = UpgradeZooKeeperDependencyTest.project("<dependencies>" +
                UpgradeZooKeeperDependencyTest.dep("3.8.6") +
                dependency("org.slf4j", "slf4j-api", "1.7.36") +
                dependency("ch.qos.logback", "logback-classic", "1.2.13") +
                dependency("org.apache.logging.log4j", "log4j-slf4j-impl", "2.20.0") +
                dependency("io.netty", "netty-handler", "4.1.100.Final") +
                dependency("org.eclipse.jetty", "jetty-server", "9.4.54.v20240208") +
                "</dependencies>");
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertEquals(3, occurrences(printed, FindZooKeeper386BuildRisks.LOGGING));
            assertEquals(2, occurrences(printed, FindZooKeeper386BuildRisks.TRANSPORT));
        })));
    }

    @Test
    void marksGroovyAndKotlinGraphRisks() {
        rewriteRun(
                buildGradle("""
                        dependencies {
                            implementation 'org.apache.zookeeper:zookeeper:3.8.6'
                            implementation 'org.apache.zookeeper:zookeeper-jute:3.7.1'
                            runtimeOnly 'org.slf4j:slf4j-simple:1.7.36'
                            implementation 'io.netty:netty-handler:4.1.100.Final'
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindZooKeeper386BuildRisks.JUTE), printed);
                            assertTrue(printed.contains(FindZooKeeper386BuildRisks.LOGGING), printed);
                            assertTrue(printed.contains(FindZooKeeper386BuildRisks.TRANSPORT), printed);
                        })),
                buildGradleKts("""
                        dependencies {
                            implementation("org.apache.zookeeper:zookeeper:3.8.6")
                            implementation("org.apache.zookeeper:zookeeper-jute:3.7.1")
                            runtimeOnly("org.slf4j:slf4j-simple:1.7.36")
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindZooKeeper386BuildRisks.JUTE), printed);
                            assertTrue(printed.contains(FindZooKeeper386BuildRisks.LOGGING), printed);
                        })));
    }

    @Test
    void marksMavenShadeRelocation() {
        String plugin = "<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId>" +
                        "<artifactId>maven-shade-plugin</artifactId><configuration><relocations><relocation>" +
                        "<pattern>org.apache.zookeeper</pattern><shadedPattern>shadow.zookeeper</shadedPattern>" +
                        "</relocation></relocations></configuration></plugin></plugins></build>";
        rewriteRun(xml(UpgradeZooKeeperDependencyTest.project(plugin), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindZooKeeper386BuildRisks.PACKAGING),
                                after.printAll()))));
    }

    @Test
    void generatedBuildFilesAreIgnored() {
        rewriteRun(
                xml(UpgradeZooKeeperDependencyTest.pom("3.9.4"), source -> source.path("target/pom.xml")),
                buildGradle("dependencies { implementation 'org.apache.zookeeper:zookeeper:3.9.3' }",
                        source -> source.path("generated/build.gradle")));
    }

    @Test
    void twoCyclesDoNotDuplicateMarkers() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeZooKeeperDependencyTest.pom("3.9.4"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(),
                                        FindZooKeeper386BuildRisks.NO_DOWNGRADE_PREFIX)))));
    }

    private static String dependency(String group, String artifact, String version) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + artifact +
               "</artifactId><version>" + version + "</version></dependency>";
    }

    private static void assertConflict(String printed, String version) {
        assertTrue(printed.contains(version), printed);
        assertTrue(printed.contains(FindZooKeeper386BuildRisks.NO_DOWNGRADE_PREFIX), printed);
        assertFalse(printed.contains(">3.8.6<"), printed);
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
