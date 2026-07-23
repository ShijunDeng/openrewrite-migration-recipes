package com.huawei.clouds.openrewrite.jettyhttp;

import org.eclipse.jetty.http.HttpFields;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.migrate.UpgradeJavaVersion;

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

class JettyHttpOfficialRecipeAuditTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.jettyhttp.";
    private static final String EE9 = "org.openrewrite.java.migrate.jakarta.JettyUpgradeEE9";
    private static final String EE10 = "org.openrewrite.java.migrate.jakarta.JettyUpgradeEE10";
    private static final Environment ENVIRONMENT = JettyHttpTestSupport.environment();

    @Test
    void pinsEveryOfficialArtifactAndTargetJar() throws Exception {
        assertArtifact(UpgradeJavaVersion.class, "rewrite-migrate-java-3.40.0.jar",
                "658481254a6ee678f5f162e51d8d49ee01c75877",
                "8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6");
        assertArtifact(ChangeType.class, "rewrite-java-8.87.5.jar",
                "91e23c2858176877428ddc03e146d2bb023217a8",
                "a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043");
        assertArtifact(ChangeMethodName.class, "rewrite-java-8.87.5.jar",
                "91e23c2858176877428ddc03e146d2bb023217a8",
                "a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043");
        assertArtifact(ChangeDependency.class, "rewrite-java-dependencies-1.59.0.jar",
                "decb8dbb2b5b726f8815efc51c85c34a60268bb0",
                "b5c5ffaa0aea06cbbb8ae110ed138261bce621806c789f14ea0f3fe92cf95550");
        assertEquals("jetty-http-12.0.34.jar", artifact(HttpFields.class).getFileName().toString());
        assertEquals("63890ecc5bb8bf26c4dd0952ef2d0d3dd3f7434b37b21e51f7ea8dfbe96b9dc0",
                sha256(HttpFields.class));
    }

    @Test
    void officialCatalogHasOnlyBroadJettyEeAggregates() {
        List<String> candidates = ENVIRONMENT.listRecipes().stream()
                .map(Recipe::getName)
                .filter(name -> name.startsWith("org.openrewrite."))
                .filter(name -> name.toLowerCase(Locale.ROOT).contains("jetty"))
                .sorted().toList();
        assertEquals(List.of(EE10, EE9), candidates);
    }

    @Test
    void ee9AggregateMutatesTwelveSiblingArtifactsButNeverJettyHttp() {
        List<ChangeDependency> changes = flatten(ENVIRONMENT.activateRecipes(EE9))
                .filter(ChangeDependency.class::isInstance)
                .map(ChangeDependency.class::cast).toList();
        assertEquals(12, changes.size());
        assertTrue(changes.stream().allMatch(change -> "12.0.x".equals(change.getNewVersion())));
        assertTrue(changes.stream().anyMatch(change ->
                "org.eclipse.jetty".equals(change.getOldGroupId()) &&
                "jetty-servlet".equals(change.getOldArtifactId()) &&
                "org.eclipse.jetty.ee9".equals(change.getNewGroupId()) &&
                "jetty-ee9-servlet".equals(change.getNewArtifactId())));
        assertFalse(changes.stream().anyMatch(JettyHttpOfficialRecipeAuditTest::selectsJettyHttp));
    }

    @Test
    void ee10AggregateMutatesEeFamilyAndUnrelatedResourceTypeButNeverJettyHttp() {
        List<ChangeDependency> changes = flatten(ENVIRONMENT.activateRecipes(EE10))
                .filter(ChangeDependency.class::isInstance)
                .map(ChangeDependency.class::cast).toList();
        List<ChangeType> types = flatten(ENVIRONMENT.activateRecipes(EE10))
                .filter(ChangeType.class::isInstance).map(ChangeType.class::cast).toList();
        assertEquals(12, changes.size());
        assertEquals(1, types.size());
        assertEquals("org.eclipse.jetty.util.resource.ResourceCollection",
                types.get(0).getOldFullyQualifiedTypeName());
        assertEquals("org.eclipse.jetty.util.resource.Resource",
                types.get(0).getNewFullyQualifiedTypeName());
        assertTrue(changes.stream().anyMatch(change ->
                "org.eclipse.jetty.ee9".equals(change.getOldGroupId()) &&
                "jetty-ee9-webapp".equals(change.getOldArtifactId()) &&
                "org.eclipse.jetty.ee10".equals(change.getNewGroupId()) &&
                "jetty-ee10-webapp".equals(change.getNewArtifactId())));
        assertFalse(changes.stream().anyMatch(JettyHttpOfficialRecipeAuditTest::selectsJettyHttp));
    }

    @Test
    void acceptedTypeCompositionIsExactlyThreeCoreLeaves() {
        DeclarativeRecipe recipe = assertInstanceOf(DeclarativeRecipe.class,
                ENVIRONMENT.activateRecipes(PREFIX + "MigrateJettyHttp12TypeRelocations"));
        List<Recipe> composition = composition(recipe);
        assertEquals(List.of(ChangeType.class, ChangeType.class, ChangeType.class),
                composition.stream().map(Object::getClass).toList());
        assertEquals(Set.of(
                        "org.eclipse.jetty.http.HttpContent->org.eclipse.jetty.http.content.HttpContent:true",
                        "org.eclipse.jetty.http.ResourceHttpContent->" +
                        "org.eclipse.jetty.http.content.ResourceHttpContent:true",
                        "org.eclipse.jetty.http.PrecompressedHttpContent->" +
                        "org.eclipse.jetty.http.content.PreCompressedHttpContent:true"),
                composition.stream().map(ChangeType.class::cast)
                        .map(change -> change.getOldFullyQualifiedTypeName() + "->" +
                                       change.getNewFullyQualifiedTypeName() + ":" +
                                       change.getIgnoreDefinition())
                        .collect(Collectors.toSet()));
        assertEquals(List.of(FindAuthoredJettyJava.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());
    }

    @Test
    void acceptedContentBufferCompositionIsExactlyTwoCoreLeaves() {
        DeclarativeRecipe recipe = assertInstanceOf(DeclarativeRecipe.class,
                ENVIRONMENT.activateRecipes(PREFIX +
                        "MigrateJettyHttp12ContentBufferAccess"));
        List<Recipe> composition = composition(recipe);
        assertEquals(List.of(ChangeMethodName.class, ChangeMethodName.class),
                composition.stream().map(Object::getClass).toList());
        assertEquals(Set.of(
                        "org.eclipse.jetty.http.HttpContent getDirectBuffer()->getByteBuffer:true:true",
                        "org.eclipse.jetty.http.HttpContent getIndirectBuffer()->getByteBuffer:true:true"),
                composition.stream().map(ChangeMethodName.class::cast)
                        .map(change -> change.getMethodPattern() + "->" +
                                       change.getNewMethodName() + ":" +
                                       change.getMatchOverrides() + ":" +
                                       change.getIgnoreDefinition())
                        .collect(Collectors.toSet()));
        assertEquals(List.of(FindAuthoredJettyJava.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());
    }

    @Test
    void acceptedJavaBaselineUsesTheFixedOfficialLeaf() {
        Recipe baseline = ENVIRONMENT.activateRecipes(PREFIX + "UpgradeJettyHttpBuildToJava17");
        UpgradeJavaVersion official = flatten(baseline)
                .filter(UpgradeJavaVersion.class::isInstance)
                .map(UpgradeJavaVersion.class::cast)
                .findFirst().orElseThrow();
        assertEquals(17, official.getVersion());
        assertEquals(List.of(FindJettyHttpSelectedBuildFiles.class),
                assertInstanceOf(DeclarativeRecipe.class, baseline).getPreconditions().stream()
                        .map(Object::getClass).toList());
    }

    @Test
    void selectedWrappersRequireTheScannedProjectMarker() {
        for (String name : List.of(
                "MigrateSelectedJettyHttp12ContentBufferAccess",
                "MigrateSelectedJettyHttp12TypeRelocations",
                "FindSelectedJettyHttp12SourceRisks",
                "FindSelectedJettyHttp12ConfigurationRisks")) {
            DeclarativeRecipe recipe = assertInstanceOf(DeclarativeRecipe.class,
                    ENVIRONMENT.activateRecipes(PREFIX + name));
            assertEquals(List.of(FindSelectedJettyHttpProjectFiles.class),
                    recipe.getPreconditions().stream().map(Object::getClass).toList(), name);
        }
    }

    @Test
    void recommendedRuntimeTreeExcludesBothBroadAggregates() {
        Recipe recommended = ENVIRONMENT.activateRecipes(PREFIX + "MigrateJettyHttpTo12_0_34");
        Set<String> names = flatten(recommended).map(Recipe::getName).collect(Collectors.toSet());
        assertFalse(names.contains(EE9), names.toString());
        assertFalse(names.contains(EE10), names.toString());
        assertFalse(names.contains("org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta"));
        assertFalse(names.contains("org.openrewrite.java.migrate.jakarta.JakartaEE10"));
        assertTrue(names.contains(UpgradeSelectedJettyHttpDependency.class.getName()));
        assertTrue(names.contains(MarkSelectedJettyHttpProjects.class.getName()));
        assertTrue(names.contains("org.openrewrite.java.migrate.UpgradeJavaVersion"));
        assertTrue(names.contains("org.openrewrite.java.ChangeMethodName"));
        assertTrue(names.contains("org.openrewrite.java.ChangeType"));
        assertFalse(flatten(recommended).anyMatch(ChangeDependency.class::isInstance),
                "The only dependency mutation must remain the strict local whitelist recipe");
    }

    private static boolean selectsJettyHttp(ChangeDependency change) {
        return "org.eclipse.jetty".equals(change.getOldGroupId()) &&
               ("jetty-http".equals(change.getOldArtifactId()) || "*".equals(change.getOldArtifactId()));
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = unwrap(recipe);
        return Stream.concat(Stream.of(unwrapped), unwrapped.getRecipeList().stream()
                .filter(JettyHttpOfficialRecipeAuditTest::isCompositionRecipe)
                .flatMap(JettyHttpOfficialRecipeAuditTest::flatten));
    }

    private static List<Recipe> composition(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .filter(JettyHttpOfficialRecipeAuditTest::isCompositionRecipe)
                .map(JettyHttpOfficialRecipeAuditTest::unwrap).toList();
    }

    private static boolean isCompositionRecipe(Recipe recipe) {
        return !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether".equals(recipe.getName());
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) current = delegating.getDelegate();
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
            if (value == null) throw new IOException("Missing " + name + " in " + jar);
            return value;
        }
    }

    private static String sha256(Class<?> type) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(artifact(type))) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path artifact(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
