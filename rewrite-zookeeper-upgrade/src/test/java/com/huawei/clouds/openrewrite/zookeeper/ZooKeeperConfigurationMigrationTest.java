package com.huawei.clouds.openrewrite.zookeeper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class ZooKeeperConfigurationMigrationTest implements RewriteTest {
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.zookeeper.MigrateDeterministicZooKeeperConfiguration";

    @Test
    void descriptorReusesOfficialPropertiesRecipeAndCustomYamlGap() {
        Recipe recipe = migrationRecipe();
        List<String> names = recipe.getRecipeList().stream().map(Recipe::getName).toList();
        assertTrue(names.contains("org.openrewrite.properties.ChangePropertyValue"), names.toString());
        assertTrue(names.contains(
                "com.huawei.clouds.openrewrite.zookeeper.MigrateZooKeeperYamlAuditLogger"), names.toString());
    }

    @Test
    void officialRecipeMigratesExactPropertiesValue() {
        rewriteRun(spec -> spec.recipe(migrationRecipe()),
                properties(
                        "zookeeper.audit.impl.class=org.apache.zookeeper.audit.Log4jAuditLogger",
                        "zookeeper.audit.impl.class=org.apache.zookeeper.audit.Slf4jAuditLogger"));
    }

    @Test
    void customGapMigratesDottedAndNestedYamlValues() {
        rewriteRun(spec -> spec.recipe(migrationRecipe()),
                yaml(
                        "zookeeper.audit.impl.class: org.apache.zookeeper.audit.Log4jAuditLogger",
                        "zookeeper.audit.impl.class: org.apache.zookeeper.audit.Slf4jAuditLogger"),
                yaml("""
                        zookeeper:
                          audit:
                            impl:
                              class: org.apache.zookeeper.audit.Log4jAuditLogger
                        """, """
                        zookeeper:
                          audit:
                            impl:
                              class: org.apache.zookeeper.audit.Slf4jAuditLogger
                        """));
    }

    @Test
    void unrelatedKeysAndNonExactValuesAreUntouched() {
        rewriteRun(spec -> spec.recipe(migrationRecipe()),
                properties("""
                        other.audit.impl.class=org.apache.zookeeper.audit.Log4jAuditLogger
                        zookeeper.audit.impl.class=${AUDIT_LOGGER}
                        zookeeper.audit.enable=true
                        """),
                yaml("""
                        other.audit.impl.class: org.apache.zookeeper.audit.Log4jAuditLogger
                        zookeeper.audit.impl.class: ${AUDIT_LOGGER}
                        """));
    }

    @Test
    void generatedConfigurationsAreIgnored() {
        rewriteRun(spec -> spec.recipe(migrationRecipe()),
                properties("zookeeper.audit.impl.class=org.apache.zookeeper.audit.Log4jAuditLogger",
                        source -> source.path("target/classes/zoo.cfg")),
                yaml("zookeeper.audit.impl.class: org.apache.zookeeper.audit.Log4jAuditLogger",
                        source -> source.path("build/resources/main/application.yml")));
    }

    @Test
    void exactConfigurationMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(migrationRecipe()).cycles(2).expectedCyclesThatMakeChanges(1),
                properties(
                        "zookeeper.audit.impl.class=org.apache.zookeeper.audit.Log4jAuditLogger",
                        "zookeeper.audit.impl.class=org.apache.zookeeper.audit.Slf4jAuditLogger"),
                yaml(
                        "zookeeper.audit.impl.class: org.apache.zookeeper.audit.Log4jAuditLogger",
                        "zookeeper.audit.impl.class: org.apache.zookeeper.audit.Slf4jAuditLogger"));
    }

    @ParameterizedTest(name = "structured risk {0}")
    @MethodSource("riskKeys")
    void marksEveryStructuredPropertiesRisk(String key, String expectedMessage) {
        rewriteRun(spec -> spec.recipe(new FindZooKeeperConfigurationRisks()),
                properties(key + "=value", source -> source.path("zoo.cfg")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(expectedMessage), after.printAll()))));
    }

    static Stream<Arguments> riskKeys() {
        return Stream.of(
                Arguments.of("dataDir", FindZooKeeperConfigurationRisks.DATA),
                Arguments.of("dataLogDir", FindZooKeeperConfigurationRisks.DATA),
                Arguments.of("snapshot.trust.empty", FindZooKeeperConfigurationRisks.DATA),
                Arguments.of("autopurge.snapRetainCount", FindZooKeeperConfigurationRisks.DATA),
                Arguments.of("autopurge.purgeInterval", FindZooKeeperConfigurationRisks.DATA),
                Arguments.of("server.1", FindZooKeeperConfigurationRisks.QUORUM),
                Arguments.of("dynamicConfigFile", FindZooKeeperConfigurationRisks.QUORUM),
                Arguments.of("reconfigEnabled", FindZooKeeperConfigurationRisks.QUORUM),
                Arguments.of("standaloneEnabled", FindZooKeeperConfigurationRisks.QUORUM),
                Arguments.of("clientPort", FindZooKeeperConfigurationRisks.NETWORK),
                Arguments.of("secureClientPort", FindZooKeeperConfigurationRisks.NETWORK),
                Arguments.of("4lw.commands.whitelist", FindZooKeeperConfigurationRisks.NETWORK),
                Arguments.of("admin.enableServer", FindZooKeeperConfigurationRisks.NETWORK),
                Arguments.of("ssl.keyStore.location", FindZooKeeperConfigurationRisks.SECURITY),
                Arguments.of("quorum.auth.enableSasl", FindZooKeeperConfigurationRisks.SECURITY),
                Arguments.of("zookeeper.fips-mode", FindZooKeeperConfigurationRisks.SECURITY),
                Arguments.of("zookeeper.audit.enable", FindZooKeeperConfigurationRisks.AUDIT),
                Arguments.of("zookeeper.audit.impl.class", FindZooKeeperConfigurationRisks.AUDIT));
    }

    @Test
    void marksNestedYamlPathsByStructure() {
        rewriteRun(spec -> spec.recipe(new FindZooKeeperConfigurationRisks()),
                yaml("""
                        zookeeper:
                          snapshot:
                            trust:
                              empty: false
                          audit:
                            enable: true
                          ssl:
                            keyStore:
                              location: /run/secret/zk.p12
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindZooKeeperConfigurationRisks.DATA), printed);
                    assertTrue(printed.contains(FindZooKeeperConfigurationRisks.AUDIT), printed);
                    assertTrue(printed.contains(FindZooKeeperConfigurationRisks.SECURITY), printed);
                })));
    }

    @Test
    void safeUnrelatedConfigurationIsNotMarked() {
        rewriteRun(spec -> spec.recipe(new FindZooKeeperConfigurationRisks()),
                properties("application.name=orders\nfeature.enabled=true"),
                yaml("application:\n  name: orders\nfeature:\n  enabled: true"));
    }

    @Test
    void configurationMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindZooKeeperConfigurationRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                properties("dataDir=/var/lib/zookeeper", source -> source.after(actual -> actual).afterRecipe(after ->
                        assertEquals(1, occurrences(after.printAll(), FindZooKeeperConfigurationRisks.DATA)))));
    }

    private static Recipe migrationRecipe() {
        return Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.zookeeper", "org.openrewrite.properties").build()
                .activateRecipes(MIGRATE);
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
