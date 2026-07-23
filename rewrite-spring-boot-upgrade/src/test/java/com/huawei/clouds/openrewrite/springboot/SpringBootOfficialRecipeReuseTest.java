package com.huawei.clouds.openrewrite.springboot;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.spring.ChangeSpringPropertyKey;
import org.openrewrite.java.spring.boot2.MoveAutoConfigurationToImportsFile;
import org.openrewrite.java.spring.boot3.RemoveConstructorBindingAnnotation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringBootOfficialRecipeReuseTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.springboot.";
    private static final String CONFIGURATION = PREFIX + "MigrateOfficialSpringBootConfiguration";
    private static final String SOURCE = PREFIX + "MigrateOfficialSpringBootSource";
    private static final String SOURCE_VISITORS =
            PREFIX + "MigrateOfficialSpringBootSourceVisitors";
    private static final String SELECTED_CONFIGURATION =
            PREFIX + "MigrateSelectedSpringBootConfiguration";
    private static final String SELECTED_SOURCE =
            PREFIX + "MigrateSelectedSpringBootSource";
    private static final String SELECTED_SOURCE_VISITORS =
            PREFIX + "MigrateSelectedSpringBootSourceVisitors";
    private static final String SELECTED_RISKS =
            PREFIX + "FindSelectedSpringBoot3_5Risks";
    private static final String RECOMMENDED = PREFIX + "MigrateSpringBootTo3_5_15";
    private static final List<String> CONFIGURATION_RELEASES = List.of(
            "2_2", "2_3", "2_4", "2_5", "2_6", "2_7",
            "3_0", "3_1", "3_2", "3_3", "3_4", "3_5");
    private static final List<String> SOURCE_RELEASES = List.of(
            "2_2", "2_3", "2_4", "2_5", "2_7",
            "3_0", "3_1", "3_2", "3_4");
    private static final Environment ENVIRONMENT =
            Environment.builder().scanRuntimeClasspath().build();

    @Test
    void pinsTheOfficialArtifactsUsedByTheRuntimeTree() throws Exception {
        assertArtifact(RemoveConstructorBindingAnnotation.class,
                "rewrite-spring-6.35.0.jar", "6.35.0",
                "d28afcb6661ad413539056de0936c5489ff9d8ee",
                "27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b");
        assertArtifact(ChangePackage.class,
                "rewrite-java-8.87.5.jar", "8.88.0-SNAPSHOT",
                "91e23c2858176877428ddc03e146d2bb023217a8",
                "a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043");
    }

    @Test
    void pinsTheTargetSpringBootJarAndPom() throws Exception {
        Path jar = artifact(org.springframework.boot.SpringApplication.class);
        assertEquals("spring-boot-3.5.15.jar", jar.getFileName().toString());
        assertEquals("3.5.15",
                org.springframework.boot.SpringApplication.class.getPackage()
                        .getImplementationVersion());
        assertEquals(
                "1d3ea175f61f492d95cbca457d6cc9cf1b696b550422c1b424d50dfa58f7da15",
                sha256(jar));

        Path pom = jar.resolveSibling("spring-boot-3.5.15.pom");
        assertTrue(Files.isRegularFile(pom));
        assertEquals(
                "9040aaafea6765582ec52256b53240673d2cec23ca5d4b92a9abed86cce7375a",
                sha256(pom));
    }

    @Test
    void directlyComposesEveryOfficialBootPropertyGeneration() {
        DeclarativeRecipe recipe = declarative(CONFIGURATION);
        assertEquals(List.of(FindAuthoredSpringBootSourceFiles.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());
        assertEquals(CONFIGURATION_RELEASES.stream()
                        .map(release -> PREFIX + "SpringBootConfiguration_" + release)
                        .toList(),
                composition(recipe).stream().map(Recipe::getName).toList());

        assertEquals(List.of("org.openrewrite.java.spring.boot2.SpringBootProperties_2_2"),
                directNames(PREFIX + "SpringBootConfiguration_2_2"));
        assertEquals(List.of(PREFIX + "SpringBoot23PropertiesWithout35Conflict"),
                directNames(PREFIX + "SpringBootConfiguration_2_3"));
        assertEquals(List.of("org.openrewrite.java.spring.boot2.SpringBootProperties_2_4"),
                directNames(PREFIX + "SpringBootConfiguration_2_4"));
        assertEquals(List.of(
                        "org.openrewrite.java.spring.boot2.MergeBootstrapYamlWithApplicationYaml"),
                directNames(PREFIX + "MergeBootstrapIntoApplicationExplicitly"));
        assertEquals(List.of(
                        "org.openrewrite.java.spring.boot2.MigrateDatabaseCredentials",
                        "org.openrewrite.java.spring.boot2.SpringBootProperties_2_5"),
                directNames(PREFIX + "SpringBootConfiguration_2_5"));
        assertEquals(List.of("org.openrewrite.java.spring.boot2.SpringBootProperties_2_6"),
                directNames(PREFIX + "SpringBootConfiguration_2_6"));
        assertEquals(List.of(
                        "org.openrewrite.java.spring.boot2.SpringBootProperties_2_7",
                        "org.openrewrite.java.spring.boot2.SamlRelyingPartyPropertyApplicationPropertiesMove",
                        "org.openrewrite.yaml.ChangeKey",
                        "org.openrewrite.java.spring.ChangeSpringPropertyValue"),
                directNames(PREFIX + "SpringBootConfiguration_2_7"));
        assertEquals(List.of(
                        "org.openrewrite.java.spring.boot3.MigrateMaxHttpHeaderSize",
                        "org.openrewrite.java.spring.boot3.SpringBootProperties_3_0",
                        "org.openrewrite.java.spring.boot3.ActuatorEndpointSanitization"),
                directNames(PREFIX + "SpringBootConfiguration_3_0"));
        for (String release : List.of("3_1", "3_2", "3_3", "3_4", "3_5")) {
            assertEquals(List.of("org.openrewrite.java.spring.boot3.SpringBootProperties_" + release),
                    directNames(PREFIX + "SpringBootConfiguration_" + release));
        }
    }

    @Test
    void boot23AdapterReusesEveryOfficialLeafExceptThe35ReversedKey() {
        List<Recipe> official = composition(activate(
                SpringBoot23PropertiesWithout35Conflict.OFFICIAL_RECIPE));
        List<Recipe> adapted = new SpringBoot23PropertiesWithout35Conflict()
                .getRecipeList().stream().map(SpringBootOfficialRecipeReuseTest::unwrap).toList();

        assertEquals(official.size() - 1, adapted.size());
        assertTrue(adapted.stream().allMatch(recipe -> official.stream()
                .anyMatch(candidate -> unwrap(candidate).equals(recipe))));
        assertFalse(adapted.stream().filter(ChangeSpringPropertyKey.class::isInstance)
                .map(ChangeSpringPropertyKey.class::cast)
                .anyMatch(change ->
                        SpringBoot23PropertiesWithout35Conflict.REVERSED_OLD_KEY
                                .equals(change.getOldPropertyKey()) &&
                        SpringBoot23PropertiesWithout35Conflict.REVERSED_NEW_KEY
                                .equals(change.getNewPropertyKey())));
    }

    @Test
    void sourceTreeUsesOfficialBootAndCoreLeavesWithoutLocalWrappers() {
        DeclarativeRecipe root = declarative(SOURCE);
        assertEquals(List.of(
                        SOURCE_VISITORS,
                        PREFIX + "MoveAuthoredAutoConfigurationToImportsFile"),
                composition(root).stream().map(Recipe::getName).toList());
        MoveAuthoredAutoConfigurationToImportsFile scanning =
                assertInstanceOf(MoveAuthoredAutoConfigurationToImportsFile.class,
                        composition(root).get(1));
        MoveAutoConfigurationToImportsFile officialScanner =
                assertInstanceOf(MoveAutoConfigurationToImportsFile.class,
                        scanning.officialDelegate());
        assertFalse(Boolean.TRUE.equals(officialScanner.getPreserveFactoriesFile()));

        DeclarativeRecipe recipe = declarative(SOURCE_VISITORS);
        assertEquals(List.of(FindAuthoredSpringBootSourceFiles.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());

        assertEquals(SOURCE_RELEASES.stream()
                        .map(release -> PREFIX + "SpringBootSourceVisitors_" + release)
                        .toList(),
                composition(recipe).stream().map(Recipe::getName).toList());
        List<Recipe> children = SOURCE_RELEASES.stream()
                .flatMap(release -> composition(
                        activate(PREFIX + "SpringBootSourceVisitors_" + release)).stream())
                .toList();
        assertEquals(53, children.size());
        assertEquals(List.of(
                        "org.openrewrite.java.spring.boot2.MigrateApplicationHealthIndicatorToPingHealthIndicator",
                        "org.openrewrite.java.spring.boot2.MigrateWebTestClientBuilderCustomizerPackageName",
                        "org.openrewrite.java.spring.boot2.MigrateConfigurationPropertiesBindingPostProcessorValidatorBeanName",
                        "org.openrewrite.java.spring.boot2.MigrateDiskSpaceHealthIndicatorConstructor",
                        "org.openrewrite.java.spring.boot2.SpringBootMavenPluginMigrateAgentToAgents",
                        "org.openrewrite.java.ChangeType",
                        "org.openrewrite.java.spring.boot2.MigrateRestClientBuilderCustomizerPackageName",
                        "org.openrewrite.java.spring.boot2.MigrateErrorPropertiesIncludeStackTraceConstants",
                        "org.openrewrite.java.spring.boot2.GetErrorAttributes",
                        "org.openrewrite.java.spring.boot2.MigrateUndertowServletWebServerFactoryIsEagerInitFilters",
                        "org.openrewrite.java.spring.boot2.MigrateUndertowServletWebServerFactorySetEagerInitFilters",
                        "org.openrewrite.java.spring.boot2.MigrateLoggingSystemPropertyConstants",
                        "org.openrewrite.java.spring.boot2.MigrateHsqlEmbeddedDatabaseConnection"),
                children.subList(0, 13).stream().map(Recipe::getName).toList());
        assertTrue(children.stream().anyMatch(child ->
                "org.openrewrite.java.spring.boot2.DatabaseComponentAndBeanInitializationOrdering"
                        .equals(child.getName())));
        assertTrue(children.stream().anyMatch(child ->
                "org.openrewrite.java.spring.framework.BeanMethodReturnNull"
                        .equals(child.getName())));
        assertTrue(children.stream().anyMatch(child ->
                "org.openrewrite.java.spring.data.MigrateRepositoryRestConfigurerAdapter"
                        .equals(child.getName())));
        assertTrue(children.stream().anyMatch(RemoveConstructorBindingAnnotation.class::isInstance));
        assertTrue(children.stream().filter(ChangeType.class::isInstance)
                .map(ChangeType.class::cast)
                .anyMatch(change ->
                        "org.springframework.boot.context.properties.ConstructorBinding"
                                .equals(change.getOldFullyQualifiedTypeName()) &&
                        "org.springframework.boot.context.properties.bind.ConstructorBinding"
                                .equals(change.getNewFullyQualifiedTypeName())));
        assertFalse(children.stream().anyMatch(child -> Set.of(
                        "org.openrewrite.java.spring.boot3.UpdatePrometheusPushgateway",
                        "org.openrewrite.java.spring.boot3.ChangeCassandraGroupId")
                .contains(child.getName())));
        assertEquals(List.of("org.openrewrite.java.spring.boot3.ChangeCassandraGroupId"),
                directNames(PREFIX + "ChangeCassandraDriverCoordinatesExplicitly"));
        assertEquals(List.of("org.openrewrite.java.spring.boot3.UpdatePrometheusPushgateway"),
                directNames(PREFIX + "UpdatePrometheusPushgatewayExplicitly"));
        ChangeType schedulerBuilder = children.stream()
                .filter(ChangeType.class::isInstance)
                .map(ChangeType.class::cast)
                .filter(change ->
                        "org.springframework.boot.task.TaskSchedulerBuilder"
                                .equals(change.getOldFullyQualifiedTypeName()))
                .findFirst().orElseThrow();
        assertEquals("org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder",
                schedulerBuilder.getNewFullyQualifiedTypeName());
        assertFalse(children.stream()
                .filter(ChangeType.class::isInstance)
                .map(ChangeType.class::cast)
                .anyMatch(change ->
                        "org.springframework.boot.task.TaskSchedulerBuilder"
                                .equals(change.getOldFullyQualifiedTypeName()) &&
                        "org.springframework.boot.task.ThreadPoolTaskExecutorBuilder"
                                .equals(change.getNewFullyQualifiedTypeName())));

        List<ChangePackage> packages = children.stream()
                .filter(ChangePackage.class::isInstance).map(ChangePackage.class::cast).toList();
        assertEquals(8, packages.size());
        assertEquals(List.of(
                        "javax.servlet -> jakarta.servlet",
                        "javax.validation -> jakarta.validation",
                        "javax.persistence -> jakarta.persistence",
                        "javax.inject -> jakarta.inject",
                        "javax.xml.bind -> jakarta.xml.bind",
                        "javax.ws.rs -> jakarta.ws.rs",
                        "javax.mail -> jakarta.mail",
                        "javax.activation -> jakarta.activation"),
                packages.stream()
                        .peek(change -> assertTrue(Boolean.TRUE.equals(change.getRecursive())))
                        .map(change -> change.getOldPackageName() + " -> " +
                                       change.getNewPackageName()).toList());
        List<ChangeType> annotationTypes = children.stream()
                .filter(ChangeType.class::isInstance).map(ChangeType.class::cast)
                .filter(change -> change.getOldFullyQualifiedTypeName().startsWith("javax.annotation."))
                .toList();
        assertEquals(6, annotationTypes.size());
        assertTrue(annotationTypes.stream()
                .allMatch(change -> !Boolean.TRUE.equals(change.getIgnoreDefinition())));
    }

    @Test
    void broadAggregateIsAuditedButExcludedFromTheRecommendedTree() {
        Recipe broad = activate("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5");
        List<Recipe> broadChildren = composition(broad);
        UpgradeDependencyVersion wildcard = broadChildren.stream()
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .filter(change -> SpringBootSupport.GROUP.equals(change.getGroupId()) &&
                                  "*".equals(change.getArtifactId()))
                .findFirst().orElseThrow();
        assertEquals("3.5.x", wildcard.getNewVersion());
        assertTrue(broadChildren.stream().anyMatch(child ->
                "org.openrewrite.java.spring.cloud2025.UpgradeSpringCloud_2025".equals(child.getName())));
        assertTrue(broadChildren.stream().anyMatch(child ->
                "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_5".equals(child.getName())));

        Recipe recommended = activate(RECOMMENDED);
        assertEquals(List.of(
                        PREFIX + "MarkSelectedSpringBootProjects",
                        PREFIX + "UpgradeSpringBootTo3_5_15",
                        SELECTED_CONFIGURATION,
                        SELECTED_SOURCE,
                        SELECTED_RISKS),
                composition(recommended).stream().map(Recipe::getName).toList());
        Set<String> names = recipeTree(recommended).stream()
                .map(Recipe::getName).collect(Collectors.toSet());
        assertTrue(java.util.Collections.disjoint(names, Set.of(
                "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5",
                "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4",
                "org.openrewrite.java.spring.cloud2025.UpgradeSpringCloud_2025",
                "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_5",
                "org.openrewrite.java.migrate.UpgradeToJava17",
                "org.openrewrite.java.spring.boot2.MergeBootstrapYamlWithApplicationYaml",
                "org.openrewrite.java.spring.boot3.UpdatePrometheusPushgateway",
                "org.openrewrite.java.spring.boot3.ChangeCassandraGroupId",
                "org.openrewrite.java.dependencies.ChangeDependency",
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion",
                "org.openrewrite.maven.UpgradeParentVersion",
                "org.openrewrite.maven.UpgradePluginVersion",
                "org.openrewrite.gradle.plugins.UpgradePluginVersion")));
        assertFalse(names.stream().anyMatch(name ->
                name.startsWith("com.huawei.clouds.openrewrite.springboot.Apply") ||
                name.startsWith("com.huawei.clouds.openrewrite.springboot.MigrateBoot")));
    }

    @Test
    void recommendedTreeGatesEveryOfficialAutoAndRiskBranchByScannedProject() {
        DeclarativeRecipe configuration = declarative(SELECTED_CONFIGURATION);
        assertTrue(configuration.getPreconditions().isEmpty());
        assertEquals(CONFIGURATION_RELEASES.stream()
                        .map(release -> PREFIX +
                                "MigrateSelectedSpringBootConfiguration_" + release)
                        .toList(),
                composition(configuration).stream().map(Recipe::getName).toList());
        assertReleaseGates("MigrateSelectedSpringBootConfiguration_",
                "SpringBootConfiguration_", CONFIGURATION_RELEASES);

        DeclarativeRecipe visitors = declarative(SELECTED_SOURCE_VISITORS);
        assertTrue(visitors.getPreconditions().isEmpty());
        assertEquals(SOURCE_RELEASES.stream()
                        .map(release -> PREFIX +
                                "MigrateSelectedSpringBootSourceVisitors_" + release)
                        .toList(),
                composition(visitors).stream().map(Recipe::getName).toList());
        assertReleaseGates("MigrateSelectedSpringBootSourceVisitors_",
                "SpringBootSourceVisitors_", SOURCE_RELEASES);

        DeclarativeRecipe source = declarative(SELECTED_SOURCE);
        assertEquals(List.of(
                        SELECTED_SOURCE_VISITORS,
                        PREFIX + "MoveSelectedAutoConfigurationToImportsFile"),
                composition(source).stream().map(Recipe::getName).toList());
        MoveSelectedAutoConfigurationToImportsFile scanner =
                assertInstanceOf(MoveSelectedAutoConfigurationToImportsFile.class,
                        composition(source).get(1));
        assertInstanceOf(MoveAutoConfigurationToImportsFile.class,
                scanner.officialDelegate());

        DeclarativeRecipe risks = declarative(SELECTED_RISKS);
        assertEquals(List.of(
                        PREFIX + "FindSpringBoot35BuildRisks",
                        PREFIX + "FindSelectedSpringBoot3_5SourceAndConfigurationRisks"),
                composition(risks).stream().map(Recipe::getName).toList());
        DeclarativeRecipe applicationRisks = declarative(
                PREFIX + "FindSelectedSpringBoot3_5SourceAndConfigurationRisks");
        assertEquals(List.of(FindSelectedSpringBootSourceFiles.class),
                applicationRisks.getPreconditions().stream()
                        .map(SpringBootOfficialRecipeReuseTest::unwrap)
                        .map(Object::getClass).toList());
    }

    private static void assertReleaseGates(String selectedPrefix, String childPrefix,
                                           List<String> releases) {
        for (String release : releases) {
            DeclarativeRecipe selected = declarative(PREFIX + selectedPrefix + release);
            assertEquals(1, selected.getPreconditions().size());
            FindSelectedSpringBootMigrationSourceFiles gate =
                    assertInstanceOf(FindSelectedSpringBootMigrationSourceFiles.class,
                            unwrap(selected.getPreconditions().get(0)));
            assertEquals(release.replace('_', '.'), gate.getTargetRelease());
            assertEquals(List.of(PREFIX + childPrefix + release),
                    composition(selected).stream().map(Recipe::getName).toList());
        }
    }

    private static List<String> directNames(String name) {
        return composition(activate(name)).stream().map(Recipe::getName).toList();
    }

    private static DeclarativeRecipe declarative(String name) {
        return assertInstanceOf(DeclarativeRecipe.class, unwrap(activate(name)));
    }

    private static Recipe activate(String name) {
        return unwrap(ENVIRONMENT.activateRecipes(name));
    }

    private static List<Recipe> composition(Recipe recipe) {
        return unwrap(recipe).getRecipeList().stream()
                .filter(SpringBootOfficialRecipeReuseTest::isCompositionRecipe)
                .map(SpringBootOfficialRecipeReuseTest::unwrap)
                .toList();
    }

    private static List<Recipe> recipeTree(Recipe root) {
        List<Recipe> recipes = new ArrayList<>();
        collect(unwrap(root), recipes);
        return recipes;
    }

    private static void collect(Recipe recipe, List<Recipe> recipes) {
        recipes.add(recipe);
        for (Recipe child : composition(recipe)) collect(child, recipes);
    }

    private static boolean isCompositionRecipe(Recipe recipe) {
        return !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether".equals(recipe.getName());
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) {
            current = delegating.getDelegate();
        }
        return current;
    }

    private static void assertArtifact(Class<?> type, String fileName,
                                       String implementationVersion, String commit,
                                       String hash) throws Exception {
        assertEquals(fileName, artifact(type).getFileName().toString());
        assertEquals(implementationVersion, type.getPackage().getImplementationVersion());
        assertEquals(commit, manifestAttribute(type, "Full-Change"));
        assertEquals(hash, sha256(type));
    }

    private static String manifestAttribute(Class<?> type, String name) throws Exception {
        try (JarFile artifact = new JarFile(artifact(type).toFile())) {
            String value = artifact.getManifest().getMainAttributes().getValue(name);
            if (value == null) throw new IOException("Missing manifest attribute " + name);
            return value;
        }
    }

    private static Path artifact(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static String sha256(Class<?> type) throws Exception {
        return sha256(artifact(type));
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
