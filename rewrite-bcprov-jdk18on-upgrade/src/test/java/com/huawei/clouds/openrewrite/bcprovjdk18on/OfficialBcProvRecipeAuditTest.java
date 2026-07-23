package com.huawei.clouds.openrewrite.bcprovjdk18on;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.ReplaceConstantWithAnotherConstant;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.migrate.UpgradeJavaVersion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialBcProvRecipeAuditTest {
    private static final String DETERMINISTIC =
            "com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateDeterministicBcProv1_84Java";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateBcProvJdk18onTo1_84";
    private static final String OFFICIAL =
            "org.openrewrite.java.migrate.BounceCastleFromJdk15OntoJdk18On";

    @Test
    void pinsAuditedOpenRewriteArtifacts() throws Exception {
        assertEquals("3.40.0", UpgradeJavaVersion.class.getPackage().getImplementationVersion());
        assertEquals("658481254a6ee678f5f162e51d8d49ee01c75877",
                manifestAttribute(UpgradeJavaVersion.class, "Full-Change"));
    }

    @Test
    void officialBouncyCastleAggregateIsExactlyTheRejectedBroadCoordinateMigration() {
        Recipe official = activate(OFFICIAL);
        List<Recipe> leaves = effectiveChildren(official);
        List<String> oldArtifacts = List.of(
                "bcprov-jdk15on", "bcutil-jdk15on", "bcpkix-jdk15on", "bcmail-jdk15on",
                "bcjmail-jdk15on", "bcpg-jdk15on", "bctls-jdk15on",
                "bcprov-jdk15to18", "bcutil-jdk15to18", "bcpkix-jdk15to18", "bcmail-jdk15to18",
                "bcjmail-jdk15to18", "bcpg-jdk15to18", "bctls-jdk15to18");

        assertEquals(oldArtifacts.size(), leaves.size());
        for (int i = 0; i < oldArtifacts.size(); i++) {
            ChangeDependency change = assertInstanceOf(ChangeDependency.class, leaves.get(i));
            String oldArtifact = oldArtifacts.get(i);
            assertEquals("org.bouncycastle", change.getOldGroupId());
            assertEquals(oldArtifact, change.getOldArtifactId());
            assertNull(change.getNewGroupId());
            assertEquals(oldArtifact.replace("-jdk15to18", "-jdk18on")
                            .replace("-jdk15on", "-jdk18on"),
                    change.getNewArtifactId());
            assertEquals("latest.release", change.getNewVersion());
        }
    }

    @Test
    void localRecipeDirectlyComposesOnlyTheAuditedCoreLeavesAndTheEcGapRecipe() {
        Recipe deterministic = activate(DETERMINISTIC);
        assertEquals(List.of(
                        "com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateAttributedBikePackage",
                        "com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateAttributedPicnicPackage",
                        "com.huawei.clouds.openrewrite.bcprovjdk18on.MigrateAttributedRainbowPackage",
                        ChangeType.class.getName(),
                        ChangeMethodName.class.getName(),
                        ReplaceConstantWithAnotherConstant.class.getName(),
                        MigrateBcProv184Java.class.getName()),
                effectiveChildren(deterministic).stream().map(Recipe::getName).toList());

        List<Recipe> tree = recipeTree(deterministic);
        List<ChangePackage> packages = tree.stream()
                .filter(ChangePackage.class::isInstance)
                .map(ChangePackage.class::cast)
                .toList();
        assertEquals(List.of(
                        "org.bouncycastle.pqc.crypto.bike->org.bouncycastle.pqc.legacy.bike",
                        "org.bouncycastle.pqc.crypto.picnic->org.bouncycastle.pqc.legacy.picnic",
                        "org.bouncycastle.pqc.crypto.rainbow->org.bouncycastle.pqc.legacy.rainbow"),
                packages.stream()
                        .map(change -> change.getOldPackageName() + "->" + change.getNewPackageName())
                        .toList());
        assertTrue(packages.stream().allMatch(change -> Boolean.TRUE.equals(change.getRecursive())));

        ChangeType type = only(tree, ChangeType.class);
        assertEquals("org.bouncycastle.pqc.jcajce.provider.util.WrapUtil",
                type.getOldFullyQualifiedTypeName());
        assertEquals("org.bouncycastle.jcajce.provider.asymmetric.util.WrapUtil",
                type.getNewFullyQualifiedTypeName());
        assertTrue(Boolean.TRUE.equals(type.getIgnoreDefinition()));

        ChangeMethodName method = only(tree, ChangeMethodName.class);
        assertEquals("org.bouncycastle.util.Pack longToBigEndian(long, byte[], int, int)",
                method.getMethodPattern());
        assertEquals("longToBigEndian_Low", method.getNewMethodName());
        assertFalse(Boolean.TRUE.equals(method.getMatchOverrides()));
        assertTrue(Boolean.TRUE.equals(method.getIgnoreDefinition()));

        ReplaceConstantWithAnotherConstant constant =
                only(tree, ReplaceConstantWithAnotherConstant.class);
        assertEquals("org.bouncycastle.crypto.hpke.HPKE.kem_P384_SHA348",
                constant.getExistingFullyQualifiedConstantName());
        assertEquals("org.bouncycastle.crypto.hpke.HPKE.kem_P384_SHA384",
                constant.getFullyQualifiedConstantName());
        assertEquals(1, tree.stream().filter(MigrateBcProv184Java.class::isInstance).count());
    }

    @Test
    void recommendedTreeExcludesTheBroadOfficialAggregateAndGenericDependencyChanges() {
        List<Recipe> local = recipeTree(activate(RECOMMENDED));
        assertTrue(local.stream().noneMatch(recipe -> OFFICIAL.equals(recipe.getName())));
        assertTrue(local.stream().noneMatch(ChangeDependency.class::isInstance));
        assertEquals(1, local.stream()
                .filter(UpgradeSelectedBcProvDependency.class::isInstance)
                .count());
    }

    private static Recipe activate(String name) {
        return unwrap(Environment.builder().scanRuntimeClasspath().build().activateRecipes(name));
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .map(OfficialBcProvRecipeAuditTest::unwrap)
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

    private static <T extends Recipe> T only(List<Recipe> tree, Class<T> type) {
        List<T> matches = tree.stream().filter(type::isInstance).map(type::cast).toList();
        assertEquals(1, matches.size(), "Expected one " + type.getName());
        return matches.get(0);
    }

    private static String manifestAttribute(Class<?> type, String name) throws Exception {
        Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarFile artifact = new JarFile(jar.toFile())) {
            String value = artifact.getManifest().getMainAttributes().getValue(name);
            if (value == null) {
                throw new IOException("Missing manifest attribute " + name + " in " + jar);
            }
            return value;
        }
    }
}
