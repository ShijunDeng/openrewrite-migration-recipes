package com.huawei.clouds.openrewrite.junrar;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.dependencies.FindDependency;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.search.FindMethods;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialJunrarRecipeAuditTest {
    private static final String INVENTORY =
            "com.huawei.clouds.openrewrite.junrar.InventoryJunrarExtractionEntrypoints";
    private static final String SELECTED_INVENTORY =
            "com.huawei.clouds.openrewrite.junrar.InventorySelectedJunrarExtractionEntrypoints";
    private static final String SELECTED_SOURCE_RISKS =
            "com.huawei.clouds.openrewrite.junrar.FindSelectedJunrar7_5_10SourceRisks";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.junrar.MigrateJunrarTo7_5_10";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath(
                    "com.huawei.clouds.openrewrite.junrar",
                    "org.openrewrite",
                    "org.openrewrite.java",
                    "org.openrewrite.java.dependencies")
            .build();

    @Test
    void pinsAuditedCoreAndJavaDependenciesArtifacts() throws Exception {
        assertEquals("8.87.5", Recipe.class.getPackage().getImplementationVersion());
        assertEquals("b3008cc4a1f0c43f562da16e5933a2a56d9bc568",
                manifestAttribute(Recipe.class, "Full-Change"));
        assertEquals("1.59.0", ChangeDependency.class.getPackage().getImplementationVersion());
        assertEquals("decb8dbb2b5b726f8815efc51c85c34a60268bb0",
                manifestAttribute(ChangeDependency.class, "Full-Change"));
    }

    @Test
    void fixedOfficialCatalogHasNoDedicatedJunrarRecipe() {
        Set<String> official = ENVIRONMENT.listRecipes().stream()
                .map(Recipe::getName)
                .filter(name -> !name.startsWith("com.huawei.clouds.openrewrite.junrar."))
                .filter(name -> name.toLowerCase(Locale.ROOT).contains("junrar"))
                .collect(Collectors.toSet());
        assertTrue(official.isEmpty(), official.toString());
    }

    @Test
    void inventoryDirectlyComposesEightExactOfficialFindMethodsLeaves() {
        List<Recipe> leaves = effectiveChildren(activate(INVENTORY));
        assertEquals(8, leaves.size());
        assertTrue(leaves.stream().allMatch(FindMethods.class::isInstance));

        List<String> patterns = leaves.stream().map(FindMethods.class::cast)
                .map(FindMethods::getMethodPattern).toList();
        assertEquals(List.of(
                "com.github.junrar.Junrar extract(..)",
                "com.github.junrar.Archive extractFile(..)",
                "com.github.junrar.Archive getInputStream(..)",
                "com.github.junrar.rarfile.FileHeader getFileName()",
                "com.github.junrar.rarfile.FileHeader getFileNameW()",
                "com.github.junrar.rarfile.FileHeader getFileNameString()",
                "com.github.junrar.rarfile.FileHeader getFileNameByteArray()",
                "com.github.junrar.volume.InputStreamVolume getLength()"), patterns);
        for (Recipe leaf : leaves) {
            assertFalse(Boolean.TRUE.equals(((FindMethods) leaf).getMatchOverrides()));
        }
    }

    @Test
    void recommendedTreeRejectsBroadDependencySelectors() {
        List<Recipe> tree = recipeTree(activate(RECOMMENDED));
        assertFalse(tree.stream().anyMatch(ChangeDependency.class::isInstance));
        assertFalse(tree.stream().anyMatch(UpgradeDependencyVersion.class::isInstance));
        assertFalse(tree.stream().anyMatch(FindDependency.class::isInstance));
        assertEquals(2, tree.stream()
                .filter(MarkSelectedJunrarProjects.class::isInstance).count());
        assertEquals(1, tree.stream()
                .filter(UpgradeSelectedJunrarDependency.class::isInstance).count());
        assertEquals(8, tree.stream().filter(FindMethods.class::isInstance).count());
        assertEquals(1, tree.stream().filter(FindJunrar7510BuildRisks.class::isInstance).count());
        assertEquals(1, tree.stream().filter(FindJunrar7510SourceRisks.class::isInstance).count());
    }

    @Test
    void recommendedTopLevelOrderIsStable() {
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.junrar.UpgradeJunrarTo7_5_10",
                SELECTED_INVENTORY,
                "com.huawei.clouds.openrewrite.junrar.FindJunrar7_5_10BuildRisks",
                SELECTED_SOURCE_RISKS),
                effectiveChildren(activate(RECOMMENDED)).stream().map(Recipe::getName).toList());
    }

    @Test
    void everyRecommendedSourceBranchHasTheScannedProjectPrecondition() {
        for (String name : List.of(SELECTED_INVENTORY, SELECTED_SOURCE_RISKS)) {
            DeclarativeRecipe selected = assertInstanceOf(
                    DeclarativeRecipe.class, activate(name));
            assertEquals(List.of(FindSelectedJunrarProjectFiles.class),
                    selected.getPreconditions().stream()
                            .map(OfficialJunrarRecipeAuditTest::unwrap)
                            .map(Object::getClass)
                            .toList());
        }
    }

    @Test
    void allPublishedRecipesAreDiscoverable() {
        for (String name : List.of(
                "com.huawei.clouds.openrewrite.junrar.UpgradeJunrarTo7_5_10",
                INVENTORY,
                SELECTED_INVENTORY,
                "com.huawei.clouds.openrewrite.junrar.FindJunrar7_5_10BuildRisks",
                "com.huawei.clouds.openrewrite.junrar.FindJunrar7_5_10SourceRisks",
                SELECTED_SOURCE_RISKS,
                RECOMMENDED)) {
            assertEquals(name, ENVIRONMENT.activateRecipes(name).getName());
        }
    }

    private static Recipe activate(String name) {
        return unwrap(ENVIRONMENT.activateRecipes(name));
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .map(OfficialJunrarRecipeAuditTest::unwrap)
                .filter(child -> !child.getClass().getName().endsWith("PreconditionBellwether"))
                .toList();
    }

    private static List<Recipe> recipeTree(Recipe root) {
        List<Recipe> result = new ArrayList<>();
        collect(unwrap(root), result);
        return result;
    }

    private static void collect(Recipe recipe, List<Recipe> result) {
        result.add(recipe);
        for (Recipe child : effectiveChildren(recipe)) collect(child, result);
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) {
            current = delegating.getDelegate();
        }
        return current;
    }

    private static String manifestAttribute(Class<?> type, String name) throws Exception {
        Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarFile artifact = new JarFile(jar.toFile())) {
            String value = artifact.getManifest().getMainAttributes().getValue(name);
            if (value == null) throw new IOException("Missing " + name + " in " + jar);
            return value;
        }
    }
}
