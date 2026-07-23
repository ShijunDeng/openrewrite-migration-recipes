package com.huawei.clouds.openrewrite.tomcatcatalina;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.DeleteMethodArgument;
import org.openrewrite.java.ReorderMethodArguments;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.migrate.jakarta.UpdateGetRealPath;
import org.openrewrite.java.search.DoesNotUseType;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.maven.AddDependency;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomcatCatalinaOfficialRecipeReuseTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.tomcatcatalina.";
    private static final String JAKARTA_EE_10 =
            "org.openrewrite.java.migrate.jakarta.JakartaEE10";
    private static final String JAKARTA_10_APIS =
            "org.openrewrite.java.migrate.jakarta.MigrationToJakarta10Apis";
    private static final String SERVLET_10_REMOVALS =
            "org.openrewrite.java.migrate.jakarta.RemovalsServletJakarta10";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath("com.huawei.clouds.openrewrite.tomcatcatalina",
                                  "org.openrewrite.java.migrate.jakarta",
                                  "org.openrewrite.java.spring")
            .build();

    @Test
    void pinsEveryAuditedOfficialArtifact() throws Exception {
        assertArtifact(ChangePackage.class, "rewrite-java-8.87.5.jar",
                "91e23c2858176877428ddc03e146d2bb023217a8",
                "a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043");
        assertArtifact(AddDependency.class, "rewrite-maven-8.87.5.jar",
                "b3008cc4a1f0c43f562da16e5933a2a56d9bc568",
                "d7d4a38376a87e9de2a27f43dfe522abba6a8e20ca3429587ec349fcde23db4c");
        assertArtifact(UpdateGetRealPath.class, "rewrite-migrate-java-3.40.0.jar",
                "658481254a6ee678f5f162e51d8d49ee01c75877",
                "8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6");
        assertArtifact(UpgradeDependencyVersion.class,
                "rewrite-java-dependencies-1.59.0.jar",
                "decb8dbb2b5b726f8815efc51c85c34a60268bb0",
                "b5c5ffaa0aea06cbbb8ae110ed138261bce621806c789f14ea0f3fe92cf95550");
        assertArtifact(AddSpringProperty.class, "rewrite-spring-6.35.0.jar",
                "d28afcb6661ad413539056de0936c5489ff9d8ee",
                "27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b");
    }

    @Test
    void fixedOfficialCatalogHasNoStandaloneTomcatCatalinaMigration() {
        List<String> candidates = ENVIRONMENT.listRecipes().stream()
                .map(Recipe::getName)
                .filter(name -> name.startsWith("org.openrewrite."))
                .filter(name -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.contains("tomcat") || lower.contains("catalina");
                })
                .sorted()
                .toList();
        assertTrue(candidates.isEmpty(), candidates.toString());
    }

    @Test
    void broadJakartaAggregateAndPlatformSelectorAreProvenOutOfScope() {
        Set<String> aggregateTree = flatten(ENVIRONMENT.activateRecipes(JAKARTA_EE_10))
                .map(Recipe::getName)
                .collect(Collectors.toSet());
        assertTrue(aggregateTree.contains(JAKARTA_10_APIS));
        assertTrue(aggregateTree.contains(
                "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta"));
        assertTrue(aggregateTree.contains(
                "org.openrewrite.java.migrate.jakarta.MigratePluginsForJakarta10"));
        assertTrue(aggregateTree.contains(
                "org.openrewrite.java.migrate.jakarta.JettyUpgradeEE10"));
        assertTrue(aggregateTree.contains(
                "org.openrewrite.java.migrate.jakarta.Faces3xMigrationToFaces4x"));

        List<UpgradeDependencyVersion> selectors =
                flatten(ENVIRONMENT.activateRecipes(JAKARTA_10_APIS))
                        .filter(UpgradeDependencyVersion.class::isInstance)
                        .map(UpgradeDependencyVersion.class::cast)
                        .toList();
        assertTrue(selectors.size() >= 20, "Unexpectedly narrow Jakarta platform tree");
        assertTrue(selectors.stream().anyMatch(selector ->
                "jakarta.servlet".equals(selector.getGroupId()) &&
                "jakarta.servlet-api".equals(selector.getArtifactId()) &&
                "6.0.x".equals(selector.getNewVersion())));
        assertTrue(selectors.stream().anyMatch(selector ->
                "jakarta.persistence".equals(selector.getGroupId()) &&
                "jakarta.persistence-api".equals(selector.getArtifactId()) &&
                "3.1.x".equals(selector.getNewVersion())));
        assertTrue(selectors.stream().anyMatch(selector ->
                "jakarta.jms".equals(selector.getGroupId()) &&
                "jakarta.jms-api".equals(selector.getArtifactId()) &&
                "3.1.x".equals(selector.getNewVersion())));
    }

    @Test
    void apiDependencyCompositionUsesOnlyOfficialExactVersionLeaves() {
        DeclarativeRecipe recipe = recipe("MigrateTomcat9JakartaApiDependencies");
        List<Recipe> composition = composition(recipe);

        assertEquals(List.of(
                        ChangeDependency.class,
                        UpgradeDependencyVersion.class,
                        ChangeDependency.class,
                        UpgradeDependencyVersion.class),
                composition.stream().map(Object::getClass).toList());
        assertEquals(List.of(IsTomcatNonGeneratedSource.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());

        Set<String> changes = composition.stream()
                .filter(ChangeDependency.class::isInstance)
                .map(ChangeDependency.class::cast)
                .map(change -> change.getOldGroupId() + ":" + change.getOldArtifactId() + "->" +
                               change.getNewGroupId() + ":" + change.getNewArtifactId() + ":" +
                               change.getNewVersion())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "javax.servlet:javax.servlet-api->jakarta.servlet:jakarta.servlet-api:6.0.0",
                "javax.el:javax.el-api->jakarta.el:jakarta.el-api:5.0.1"), changes);

        Set<String> upgrades = composition.stream()
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .map(upgrade -> upgrade.getGroupId() + ":" + upgrade.getArtifactId() + ":" +
                                upgrade.getNewVersion())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "jakarta.servlet:jakarta.servlet-api:6.0.0",
                "jakarta.el:jakarta.el-api:5.0.1"), upgrades);
    }

    @Test
    void namespaceCompositionUsesOnlyCoreChangePackageAndRemovedTypeGuards() {
        DeclarativeRecipe recipe = recipe("MigrateTomcat9JakartaNamespaces");
        List<Recipe> composition = composition(recipe);

        assertEquals(Set.of(
                        "javax.servlet->jakarta.servlet:true",
                        "javax.el->jakarta.el:true"),
                composition.stream()
                        .map(ChangePackage.class::cast)
                        .map(change -> change.getOldPackageName() + "->" +
                                       change.getNewPackageName() + ":" +
                                       change.getRecursive())
                        .collect(Collectors.toSet()));
        assertEquals(List.of(IsTomcatNonGeneratedSource.class,
                        DoesNotUseType.class, DoesNotUseType.class, DoesNotUseType.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());
        assertEquals(Set.of(
                        "javax.servlet.SingleThreadModel",
                        "javax.servlet.http.HttpSessionContext",
                        "javax.servlet.http.HttpUtils"),
                guardedTypes(recipe));
        recipe.getPreconditions().stream()
                .filter(DoesNotUseType.class::isInstance)
                .map(DoesNotUseType.class::cast)
                .forEach(guard -> assertEquals(Boolean.TRUE, guard.getIncludeImplicit()));
    }

    @Test
    void officialServletAggregateContainsReturnTypeUnsafeRename() {
        Set<String> officialMethodRenames = flatten(
                ENVIRONMENT.activateRecipes(SERVLET_10_REMOVALS))
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .map(TomcatCatalinaOfficialRecipeReuseTest::methodRename)
                .collect(Collectors.toSet());

        assertTrue(officialMethodRenames.contains(
                "jakarta.servlet.http.HttpSession getValueNames()->getAttributeNames"));
    }

    @Test
    void servletCompositionReusesSafeOfficialLeavesAndRejectsUnsafeAggregate() {
        DeclarativeRecipe recipe = recipe("MigrateTomcatCatalina101Java");
        List<Recipe> composition = composition(recipe);

        assertEquals(List.of(
                        ChangeMethodName.class, ChangeMethodName.class,
                        ChangeMethodName.class, ChangeMethodName.class,
                        ChangeMethodName.class, ChangeMethodName.class,
                        ChangeMethodName.class, ChangeMethodName.class,
                        ChangeMethodName.class,
                        DeleteMethodArgument.class, DeleteMethodArgument.class,
                        ReorderMethodArguments.class, UpdateGetRealPath.class,
                        DeleteMethodArgument.class, DeleteMethodArgument.class,
                        ReorderMethodArguments.class,
                        DeclarativeRecipe.class, DeclarativeRecipe.class),
                composition.stream().map(Object::getClass).toList());
        assertEquals(List.of(
                        "org.openrewrite.java.migrate.jakarta.RemovedIsParmetersProvidedMethod",
                        "org.openrewrite.java.migrate.jakarta.ServletCookieBehaviorChangeRFC6265"),
                composition.subList(16, 18).stream().map(Recipe::getName).toList());
        assertEquals(IsTomcatNonGeneratedSource.class,
                recipe.getPreconditions().get(0).getClass());
        assertEquals(Set.of(
                        "javax.servlet.SingleThreadModel",
                        "javax.servlet.http.HttpSessionContext",
                        "javax.servlet.http.HttpUtils",
                        "jakarta.servlet.SingleThreadModel",
                        "jakarta.servlet.http.HttpSessionContext",
                        "jakarta.servlet.http.HttpUtils"),
                guardedTypes(recipe));

        Set<String> officialMethodRenames = flatten(
                ENVIRONMENT.activateRecipes(SERVLET_10_REMOVALS))
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .map(TomcatCatalinaOfficialRecipeReuseTest::methodRename)
                .collect(Collectors.toSet());
        Set<String> expectedSafeMethodRenames =
                new java.util.HashSet<>(officialMethodRenames);
        assertTrue(expectedSafeMethodRenames.remove(
                "jakarta.servlet.http.HttpSession getValueNames()->getAttributeNames"));
        Set<String> adoptedMethodRenames = composition.stream()
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .map(TomcatCatalinaOfficialRecipeReuseTest::methodRename)
                .collect(Collectors.toSet());
        assertEquals(expectedSafeMethodRenames, adoptedMethodRenames);

        Set<String> officialArgumentDeletions = flatten(
                ENVIRONMENT.activateRecipes(SERVLET_10_REMOVALS))
                .filter(DeleteMethodArgument.class::isInstance)
                .map(DeleteMethodArgument.class::cast)
                .map(TomcatCatalinaOfficialRecipeReuseTest::argumentDeletion)
                .collect(Collectors.toSet());
        assertEquals(officialArgumentDeletions, composition.stream()
                .filter(DeleteMethodArgument.class::isInstance)
                .map(DeleteMethodArgument.class::cast)
                .map(TomcatCatalinaOfficialRecipeReuseTest::argumentDeletion)
                .collect(Collectors.toSet()));

        Set<String> officialReorders = flatten(
                ENVIRONMENT.activateRecipes(SERVLET_10_REMOVALS))
                .filter(ReorderMethodArguments.class::isInstance)
                .map(ReorderMethodArguments.class::cast)
                .map(TomcatCatalinaOfficialRecipeReuseTest::argumentReorder)
                .collect(Collectors.toSet());
        assertEquals(officialReorders, composition.stream()
                .filter(ReorderMethodArguments.class::isInstance)
                .map(ReorderMethodArguments.class::cast)
                .map(TomcatCatalinaOfficialRecipeReuseTest::argumentReorder)
                .collect(Collectors.toSet()));

        Set<String> activated = flatten(recipe).map(Recipe::getName)
                .collect(Collectors.toSet());
        assertFalse(activated.contains(SERVLET_10_REMOVALS));
        assertTrue(activated.contains("org.openrewrite.java.ChangeMethodName"));
        assertTrue(activated.contains("org.openrewrite.java.DeleteMethodArgument"));
        assertTrue(activated.contains("org.openrewrite.java.ReorderMethodArguments"));
        assertTrue(activated.contains("org.openrewrite.java.RemoveMethodInvocations"));
        assertTrue(activated.contains(
                "org.openrewrite.java.migrate.jakarta.UpdateGetRealPath"));
    }

    @Test
    void recommendedRuntimeTreeExcludesBroadAggregatesAndTomcatVersionSelectors() {
        Recipe recommended = ENVIRONMENT.activateRecipes(
                PREFIX + "MigrateTomcatCatalinaTo10_1_56");
        Set<String> activated = flatten(recommended).map(Recipe::getName)
                .collect(Collectors.toSet());

        Set<String> excluded = Set.of(
                JAKARTA_EE_10,
                JAKARTA_10_APIS,
                "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta",
                "org.openrewrite.java.migrate.jakarta.JavaxServletToJakartaServlet",
                "org.openrewrite.java.migrate.jakarta.JavaxElToJakartaEl",
                "org.openrewrite.java.migrate.jakarta.MigratePluginsForJakarta10",
                "org.openrewrite.java.migrate.jakarta.JettyUpgradeEE10",
                "org.openrewrite.java.migrate.jakarta.Faces3xMigrationToFaces4x");
        assertTrue(java.util.Collections.disjoint(activated, excluded),
                activated.toString());

        List<UpgradeDependencyVersion> selectors = flatten(recommended)
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .toList();
        assertEquals(Set.of(
                        "jakarta.servlet:jakarta.servlet-api:6.0.0",
                        "jakarta.el:jakarta.el-api:5.0.1"),
                selectors.stream()
                        .map(selector -> selector.getGroupId() + ":" +
                                         selector.getArtifactId() + ":" +
                                         selector.getNewVersion())
                        .collect(Collectors.toSet()));
        assertFalse(selectors.stream().anyMatch(selector ->
                "org.apache.tomcat".equals(selector.getGroupId()) ||
                "*".equals(selector.getArtifactId()) ||
                selector.getNewVersion().contains("x")));
        assertTrue(activated.contains(
                UpgradeSelectedTomcatCatalinaDependency.class.getName()));
    }

    private static Set<String> guardedTypes(DeclarativeRecipe recipe) {
        return recipe.getPreconditions().stream()
                .filter(DoesNotUseType.class::isInstance)
                .map(DoesNotUseType.class::cast)
                .map(DoesNotUseType::getFullyQualifiedTypeName)
                .collect(Collectors.toSet());
    }

    private static String methodRename(ChangeMethodName recipe) {
        return recipe.getMethodPattern() + "->" + recipe.getNewMethodName();
    }

    private static String argumentDeletion(DeleteMethodArgument recipe) {
        return recipe.getMethodPattern() + "#" + recipe.getArgumentIndex();
    }

    private static String argumentReorder(ReorderMethodArguments recipe) {
        return recipe.getMethodPattern() + ":" +
               java.util.Arrays.toString(recipe.getOldParameterNames()) + "->" +
               java.util.Arrays.toString(recipe.getNewParameterNames()) + ":" +
               recipe.getMatchOverrides();
    }

    private static DeclarativeRecipe recipe(String shortName) {
        return assertInstanceOf(DeclarativeRecipe.class,
                ENVIRONMENT.activateRecipes(PREFIX + shortName));
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = unwrap(recipe);
        return Stream.concat(Stream.of(unwrapped), unwrapped.getRecipeList().stream()
                .filter(TomcatCatalinaOfficialRecipeReuseTest::isCompositionRecipe)
                .flatMap(TomcatCatalinaOfficialRecipeReuseTest::flatten));
    }

    private static List<Recipe> composition(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .filter(TomcatCatalinaOfficialRecipeReuseTest::isCompositionRecipe)
                .map(TomcatCatalinaOfficialRecipeReuseTest::unwrap)
                .toList();
    }

    private static boolean isCompositionRecipe(Recipe recipe) {
        return !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether"
                .equals(recipe.getName());
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegate) {
            current = delegate.getDelegate();
        }
        return current;
    }

    private static void assertArtifact(Class<?> type, String fileName,
                                       String commit, String hash) throws Exception {
        assertEquals(fileName, artifact(type).getFileName().toString());
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
