package com.huawei.clouds.openrewrite.zookeeper;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.apache.commons.codec.ApacheBase64ToJavaBase64;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.properties.ChangePropertyValue;
import org.openrewrite.yaml.ChangeValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialZooKeeperRecipeAuditTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.zookeeper.";
    private static final String APIS = PREFIX + "MigrateDeterministicZooKeeperApis";
    private static final String CONFIGURATION = PREFIX + "MigrateDeterministicZooKeeperConfiguration";
    private static final String RECOMMENDED = PREFIX + "MigrateZooKeeperTo3_8_6";

    @Test
    void pinsEveryAuditedOfficialArtifact() throws Exception {
        assertArtifact(ChangeMethodName.class, "rewrite-java-8.87.5.jar", "8.88.0-SNAPSHOT",
                "91e23c2858176877428ddc03e146d2bb023217a8",
                "a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043");
        assertArtifact(ChangePropertyValue.class, "rewrite-properties-8.87.5.jar", "8.87.5",
                "b3008cc4a1f0c43f562da16e5933a2a56d9bc568",
                "887bc2c9802e8824e47103fcd9cef614161c9cbef1b0b66adfe996c999f3f656");
        assertArtifact(ChangeValue.class, "rewrite-yaml-8.87.5.jar", "8.87.5",
                "b3008cc4a1f0c43f562da16e5933a2a56d9bc568",
                "ee7b98ffc6360ee2407516de0a7a5dc6595e462a9d2f33e462afe6fe0298354e");
        assertArtifact(UpgradeDependencyVersion.class,
                "rewrite-java-dependencies-1.59.0.jar", "1.59.0",
                "decb8dbb2b5b726f8815efc51c85c34a60268bb0",
                "b5c5ffaa0aea06cbbb8ae110ed138261bce621806c789f14ea0f3fe92cf95550");
        assertArtifact(ApacheBase64ToJavaBase64.class, "rewrite-apache-2.28.0.jar", "2.28.0",
                "b0424eb13da62085a34a7e84a3987ac78227b70b",
                "1841723a57e3dad3a47777a311275f1d18fed8e197c99aa3526503e7c8a06d17");
    }

    @Test
    void pinnedApacheCatalogContainsNoZooKeeperRecipe() {
        List<String> apacheRecipes = Environment.builder().scanRuntimeClasspath().build().listRecipes().stream()
                .map(Recipe::getName)
                .filter(name -> name.startsWith("org.openrewrite.apache."))
                .toList();

        assertTrue(apacheRecipes.contains(ApacheBase64ToJavaBase64.class.getName()), apacheRecipes.toString());
        assertFalse(apacheRecipes.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.contains("zookeeper")), apacheRecipes.toString());
    }

    @Test
    void deterministicRecipesDirectlyComposeExactCoreLeavesAndOnlyTheYamlGap() {
        List<Recipe> apiLeaves = effectiveChildren(activate(APIS));
        assertEquals(List.of(ChangeMethodName.class, ChangeType.class),
                apiLeaves.stream().map(Recipe::getClass).toList());

        ChangeMethodName method = assertInstanceOf(ChangeMethodName.class, apiLeaves.get(0));
        assertEquals("org.apache.zookeeper.server.persistence.FileTxnSnapLog getDataDir()",
                method.getMethodPattern());
        assertEquals("getDataLogDir", method.getNewMethodName());
        assertFalse(Boolean.TRUE.equals(method.getMatchOverrides()));
        assertTrue(Boolean.TRUE.equals(method.getIgnoreDefinition()));

        ChangeType type = assertInstanceOf(ChangeType.class, apiLeaves.get(1));
        assertEquals("org.apache.zookeeper.audit.Log4jAuditLogger",
                type.getOldFullyQualifiedTypeName());
        assertEquals("org.apache.zookeeper.audit.Slf4jAuditLogger",
                type.getNewFullyQualifiedTypeName());
        assertTrue(Boolean.TRUE.equals(type.getIgnoreDefinition()));

        List<Recipe> configurationLeaves = effectiveChildren(activate(CONFIGURATION));
        assertEquals(List.of(ChangePropertyValue.class, MigrateZooKeeperYamlAuditLogger.class),
                configurationLeaves.stream().map(Recipe::getClass).toList());

        ChangePropertyValue property = assertInstanceOf(ChangePropertyValue.class,
                configurationLeaves.get(0));
        assertEquals("zookeeper.audit.impl.class", property.getPropertyKey());
        assertEquals("org.apache.zookeeper.audit.Log4jAuditLogger", property.getOldValue());
        assertEquals("org.apache.zookeeper.audit.Slf4jAuditLogger", property.getNewValue());
        assertFalse(Boolean.TRUE.equals(property.getRegex()));
        assertFalse(Boolean.TRUE.equals(property.getRelaxedBinding()));
    }

    @Test
    void recommendedRuntimeTreeExcludesBroadAggregatesAndVersionSelectors() {
        Recipe recommended = activate(RECOMMENDED);
        assertEquals(List.of(
                        PREFIX + "UpgradeSelectedZooKeeperDependency",
                        PREFIX + "FindZooKeeper386BuildRisks",
                        APIS,
                        CONFIGURATION,
                        PREFIX + "FindZooKeeper386SourceRisks",
                        PREFIX + "FindZooKeeperConfigurationRisks"),
                effectiveChildren(recommended).stream().map(Recipe::getName).toList());

        List<Recipe> tree = recipeTree(recommended);
        assertEquals(1, tree.stream()
                .filter(UpgradeSelectedZooKeeperDependency.class::isInstance)
                .count());
        assertTrue(tree.stream().noneMatch(UpgradeDependencyVersion.class::isInstance));
        assertTrue(tree.stream().noneMatch(recipe ->
                recipe.getName().equals("org.openrewrite.maven.UpgradeDependencyVersion") ||
                recipe.getName().equals("org.openrewrite.gradle.UpgradeDependencyVersion")));
        assertTrue(tree.stream().noneMatch(recipe ->
                recipe.getName().startsWith("org.openrewrite.apache.") ||
                recipe.getName().startsWith("org.openrewrite.java.migrate.")));
        assertTrue(tree.stream().noneMatch(recipe -> {
            String name = recipe.getName().toLowerCase(Locale.ROOT);
            return name.contains("zookeeper") && !recipe.getName().startsWith(PREFIX);
        }));
    }

    private static Recipe activate(String name) {
        return unwrap(Environment.builder().scanRuntimeClasspath().build().activateRecipes(name));
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .map(OfficialZooKeeperRecipeAuditTest::unwrap)
                .filter(child -> !child.getClass().getName().endsWith("PreconditionBellwether"))
                .toList();
    }

    private static List<Recipe> recipeTree(Recipe root) {
        List<Recipe> recipes = new ArrayList<>();
        collect(unwrap(root), recipes);
        return recipes;
    }

    private static void collect(Recipe recipe, List<Recipe> recipes) {
        recipes.add(recipe);
        for (Recipe child : effectiveChildren(recipe)) {
            collect(child, recipes);
        }
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe unwrapped = recipe;
        while (unwrapped instanceof Recipe.DelegatingRecipe delegating) {
            unwrapped = delegating.getDelegate();
        }
        return unwrapped;
    }

    private static void assertArtifact(Class<?> type, String fileName, String implementationVersion,
                                       String commit, String hash) throws Exception {
        assertEquals(fileName, artifact(type).getFileName().toString());
        assertEquals(implementationVersion, type.getPackage().getImplementationVersion());
        assertEquals(commit, manifestAttribute(type, "Full-Change"));
        assertEquals(hash, sha256(type));
    }

    private static String manifestAttribute(Class<?> type, String name) throws Exception {
        Path jar = artifact(type);
        try (JarFile artifact = new JarFile(jar.toFile())) {
            String value = artifact.getManifest().getMainAttributes().getValue(name);
            if (value == null) {
                throw new IOException("Missing manifest attribute " + name + " in " + jar);
            }
            return value;
        }
    }

    private static String sha256(Class<?> type) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(artifact(type))) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path artifact(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
