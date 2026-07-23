package com.huawei.clouds.openrewrite.springbootactuator;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.migrate.jakarta.ApplicationPathWildcardNoLongerAccepted;
import org.openrewrite.java.spring.ChangeSpringPropertyKey;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActuatorOfficialRecipeReuseTest {
    private static final String PREFIX =
            "com.huawei.clouds.openrewrite.springbootactuator.";
    private static final String CONFIGURATION =
            PREFIX + "MigrateSpringBootActuatorConfiguration";
    private static final String JAKARTA =
            PREFIX + "MigrateSpringBoot3JakartaPackages";
    private static final String RECOMMENDED =
            PREFIX + "MigrateSpringBootActuatorTo3_5_15";
    private static final Environment ENVIRONMENT =
            Environment.builder().scanRuntimeClasspath().build();

    @Test
    void pinsTheOfficialArtifactsActuallyUsedAndAudited() throws Exception {
        assertArtifact(ChangePackage.class, "rewrite-java-8.87.5.jar",
                "8.88.0-SNAPSHOT", "91e23c2858176877428ddc03e146d2bb023217a8",
                "a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043");
        assertArtifact(ChangeSpringPropertyKey.class, "rewrite-spring-6.35.0.jar",
                "6.35.0", "d28afcb6661ad413539056de0936c5489ff9d8ee",
                "27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b");
        assertArtifact(ApplicationPathWildcardNoLongerAccepted.class,
                "rewrite-migrate-java-3.40.0.jar", "3.40.0",
                "658481254a6ee678f5f162e51d8d49ee01c75877",
                "8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6");
    }

    @Test
    void configurationDirectlyComposesTheExactOfficialSpringLeaves() {
        DeclarativeRecipe recipe = declarative(CONFIGURATION);
        assertEquals(List.of(FindAuthoredSourceFiles.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());

        List<Recipe> children = composition(recipe);
        assertEquals(22, children.size());
        assertTrue(children.subList(0, 21).stream()
                .allMatch(ChangeSpringPropertyKey.class::isInstance));
        assertEquals(
                "org.openrewrite.java.spring.boot3.SpringBootProperties_3_4_EnabledToAccess",
                children.get(21).getName());

        List<String> mappings = children.subList(0, 21).stream()
                .map(ChangeSpringPropertyKey.class::cast)
                .map(change -> change.getOldPropertyKey() + " -> " + change.getNewPropertyKey())
                .toList();
        assertEquals(List.of(
                "management.metrics.export.appoptics -> management.appoptics.metrics.export",
                "management.metrics.export.atlas -> management.atlas.metrics.export",
                "management.metrics.export.datadog -> management.datadog.metrics.export",
                "management.metrics.export.defaults -> management.defaults.metrics.export",
                "management.metrics.export.dynatrace -> management.dynatrace.metrics.export",
                "management.metrics.export.elastic -> management.elastic.metrics.export",
                "management.metrics.export.ganglia -> management.ganglia.metrics.export",
                "management.metrics.export.graphite -> management.graphite.metrics.export",
                "management.metrics.export.humio -> management.humio.metrics.export",
                "management.metrics.export.influx -> management.influx.metrics.export",
                "management.metrics.export.jmx -> management.jmx.metrics.export",
                "management.metrics.export.kairos -> management.kairos.metrics.export",
                "management.metrics.export.newrelic -> management.newrelic.metrics.export",
                "management.metrics.export.prometheus -> management.prometheus.metrics.export",
                "management.metrics.export.signalfx -> management.signalfx.metrics.export",
                "management.metrics.export.simple -> management.simple.metrics.export",
                "management.metrics.export.stackdriver -> management.stackdriver.metrics.export",
                "management.metrics.export.statsd -> management.statsd.metrics.export",
                "management.health.probes.enabled -> management.endpoint.health.probes.enabled",
                "management.endpoints.jmx.unique-names -> spring.jmx.unique-names",
                "micrometer.observations.annotations.enabled -> management.observations.annotations.enabled"),
                mappings);
    }

    @Test
    void jakartaRecipeDirectlyComposesOnlyTheAuditedCoreSubset() {
        DeclarativeRecipe recipe = declarative(JAKARTA);
        assertEquals(List.of(FindAuthoredSourceFiles.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());

        List<Recipe> children = composition(recipe);
        assertEquals(14, children.size());
        assertEquals(List.of(
                        "javax.servlet -> jakarta.servlet",
                        "javax.validation -> jakarta.validation",
                        "javax.persistence -> jakarta.persistence",
                        "javax.inject -> jakarta.inject",
                        "javax.xml.bind -> jakarta.xml.bind",
                        "javax.ws.rs -> jakarta.ws.rs",
                        "javax.mail -> jakarta.mail",
                        "javax.activation -> jakarta.activation"),
                children.subList(0, 8).stream()
                        .map(ChangePackage.class::cast)
                        .peek(change -> assertTrue(Boolean.TRUE.equals(change.getRecursive())))
                        .map(change -> change.getOldPackageName() + " -> " +
                                       change.getNewPackageName())
                        .toList());
        assertEquals(List.of(
                        "javax.annotation.PostConstruct -> jakarta.annotation.PostConstruct",
                        "javax.annotation.PreDestroy -> jakarta.annotation.PreDestroy",
                        "javax.annotation.Resource -> jakarta.annotation.Resource",
                        "javax.annotation.Generated -> jakarta.annotation.Generated",
                        "javax.annotation.Priority -> jakarta.annotation.Priority",
                        "javax.annotation.ManagedBean -> jakarta.annotation.ManagedBean"),
                children.subList(8, 14).stream()
                        .map(ChangeType.class::cast)
                        .peek(change -> assertFalse(Boolean.TRUE.equals(change.getIgnoreDefinition())))
                        .map(change -> change.getOldFullyQualifiedTypeName() + " -> " +
                                       change.getNewFullyQualifiedTypeName())
                        .toList());
    }

    @Test
    void broadOfficialAggregatesAreAvailableButDeliberatelyExcluded() {
        Recipe boot = activate("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5");
        List<Recipe> bootChildren = composition(boot);
        assertTrue(bootChildren.stream().anyMatch(child ->
                "org.openrewrite.java.spring.boot3.SpringBootProperties_3_5"
                        .equals(child.getName())));
        assertTrue(bootChildren.stream().anyMatch(child ->
                "org.openrewrite.java.spring.boot3.UpdatePrometheusPushgateway"
                        .equals(child.getName())));
        UpgradeDependencyVersion wildcard = bootChildren.stream()
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .filter(change -> "org.springframework.boot".equals(change.getGroupId()) &&
                                  "*".equals(change.getArtifactId()))
                .findFirst()
                .orElseThrow();
        assertEquals("3.5.x", wildcard.getNewVersion());

        Recipe javax = activate(
                "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta");
        Set<String> javaxChildren = composition(javax).stream()
                .map(Recipe::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(javaxChildren.contains(
                "org.openrewrite.java.migrate.jakarta.JavaxServletToJakartaServlet"));
        assertTrue(javaxChildren.contains(
                "org.openrewrite.java.migrate.jakarta.JavaxPersistenceToJakartaPersistence"));
        Set<String> servletTree = recipeTree(activate(
                "org.openrewrite.java.migrate.jakarta.JavaxServletToJakartaServlet"))
                .stream().map(Recipe::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(servletTree.contains(
                "org.openrewrite.java.dependencies.ChangeDependency"));
        assertTrue(servletTree.contains(
                "org.openrewrite.java.dependencies.AddDependency"));

        Recipe recommended = activate(RECOMMENDED);
        assertEquals(List.of(
                        PREFIX + "UpgradeSpringBootActuatorTo3_5_15",
                        CONFIGURATION,
                        JAKARTA,
                        PREFIX + "FindSpringBootActuator3_5Risks"),
                composition(recommended).stream().map(Recipe::getName).toList());
        assertEquals(List.of(PREFIX + "UpgradeSelectedActuatorDependency"),
                composition(activate(PREFIX + "UpgradeSpringBootActuatorTo3_5_15"))
                        .stream().map(Recipe::getName).toList());
        assertEquals(List.of(
                        PREFIX + "FindActuatorBuildRisks",
                        PREFIX + "FindActuatorSourceRisks",
                        PREFIX + "FindActuatorConfigurationRisks"),
                composition(activate(PREFIX + "FindSpringBootActuator3_5Risks"))
                        .stream().map(Recipe::getName).toList());

        Set<String> recommendedNames = recipeTree(recommended).stream()
                .map(Recipe::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(java.util.Collections.disjoint(recommendedNames, Set.of(
                "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5",
                "org.openrewrite.java.spring.boot3.SpringBootProperties_3_5",
                "org.openrewrite.java.spring.boot3.UpdatePrometheusPushgateway",
                "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta",
                "org.openrewrite.java.migrate.jakarta.JakartaEE10",
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion",
                "org.openrewrite.maven.UpgradeDependencyVersion",
                "org.openrewrite.gradle.UpgradeDependencyVersion")));
    }

    private static DeclarativeRecipe declarative(String name) {
        return assertInstanceOf(DeclarativeRecipe.class, unwrap(activate(name)));
    }

    private static Recipe activate(String name) {
        return unwrap(ENVIRONMENT.activateRecipes(name));
    }

    private static List<Recipe> composition(Recipe recipe) {
        return unwrap(recipe).getRecipeList().stream()
                .filter(ActuatorOfficialRecipeReuseTest::isCompositionRecipe)
                .map(ActuatorOfficialRecipeReuseTest::unwrap)
                .toList();
    }

    private static List<Recipe> recipeTree(Recipe root) {
        List<Recipe> recipes = new ArrayList<>();
        collect(unwrap(root), recipes);
        return recipes;
    }

    private static void collect(Recipe recipe, List<Recipe> recipes) {
        recipes.add(recipe);
        for (Recipe child : composition(recipe)) {
            collect(child, recipes);
        }
    }

    private static boolean isCompositionRecipe(Recipe recipe) {
        return !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether"
                .equals(recipe.getName());
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

    private static String manifestAttribute(Class<?> type, String name)
            throws Exception {
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
