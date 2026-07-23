package com.huawei.clouds.openrewrite.elasticsearch;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.testing.testcontainers.ExplicitContainerImage;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticsearchOfficialRecipeReuseTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.elasticsearch.";
    private static final String LEAF =
            "org.openrewrite.java.testing.testcontainers.ExplicitContainerImage";
    private static final String AGGREGATE =
            "org.openrewrite.java.testing.testcontainers.ExplicitContainerImages";
    private static final String TESTCONTAINERS_2 =
            "org.openrewrite.java.testing.testcontainers.Testcontainers2Migration";
    private static final Environment ENVIRONMENT = ElasticsearchTestSupport.environment();

    @Test
    void pinsTheAuditedOfficialArtifactByVersionCommitAndSha256() throws Exception {
        Path jar = artifact(ExplicitContainerImage.class);
        assertEquals("rewrite-testing-frameworks-3.42.0.jar", jar.getFileName().toString());
        assertEquals("2b5d8526dc226ff4794716133b2d0780eb257530",
                manifestAttribute(jar, "Full-Change"));
        assertEquals("77755fabca4585afcc85fec416792d8f663b0e92d1da95c6b28779e985eebff6",
                sha256(jar));
    }

    @Test
    void fixedOfficialAggregateContainsOurExactLeafAndManyUnrelatedContainers() {
        List<ExplicitContainerImage> leaves = flatten(ENVIRONMENT.activateRecipes(AGGREGATE))
                .filter(ExplicitContainerImage.class::isInstance)
                .map(ExplicitContainerImage.class::cast)
                .toList();
        assertTrue(leaves.size() >= 20, "Official aggregate unexpectedly narrowed: " + leaves.size());
        assertTrue(leaves.stream().anyMatch(leaf ->
                "org.testcontainers.elasticsearch.ElasticsearchContainer".equals(field(leaf, "containerClass")) &&
                "docker.elastic.co/elasticsearch/elasticsearch:7.9.2".equals(field(leaf, "image")) &&
                field(leaf, "parseImage") == null));
        assertTrue(leaves.stream().anyMatch(leaf ->
                "org.testcontainers.containers.PostgreSQLContainer".equals(field(leaf, "containerClass"))));
        assertTrue(leaves.stream().anyMatch(leaf ->
                "org.testcontainers.containers.KafkaContainer".equals(field(leaf, "containerClass"))));
    }

    @Test
    void broadTestcontainers2MigrationContainsOutOfScopeDependencyAndTypeChanges() {
        Set<String> names = flatten(ENVIRONMENT.activateRecipes(TESTCONTAINERS_2))
                .map(Recipe::getName).collect(Collectors.toSet());
        assertTrue(names.contains(AGGREGATE), names.toString());
        assertTrue(names.contains(
                "org.openrewrite.java.testing.testcontainers.Testcontainers2Dependencies"), names.toString());
        assertTrue(names.contains("org.openrewrite.java.ChangeType"), names.toString());
    }

    @Test
    void selectedCompositionDirectlyUsesOneOfficialLeafWithFixedParameters() {
        DeclarativeRecipe recipe = assertInstanceOf(DeclarativeRecipe.class,
                ENVIRONMENT.activateRecipes(
                        PREFIX + "MakeSelectedElasticsearchContainerImageExplicit"));
        List<Recipe> composition = composition(recipe);
        assertEquals(1, composition.size());
        ExplicitContainerImage leaf = assertInstanceOf(ExplicitContainerImage.class, composition.get(0));
        assertEquals("org.testcontainers.elasticsearch.ElasticsearchContainer",
                field(leaf, "containerClass"));
        assertEquals("docker.elastic.co/elasticsearch/elasticsearch:7.9.2",
                field(leaf, "image"));
        assertEquals(Boolean.FALSE, field(leaf, "parseImage"));
        assertEquals(List.of(
                        FindSelectedElasticsearchProjectFiles.class,
                        FindAuthoredElasticsearchContainerSources.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());
    }

    @Test
    void publicSourceWrapperEstablishesTheProjectMarkerBeforeTheOfficialLeaf() {
        Recipe wrapper = ENVIRONMENT.activateRecipes(
                PREFIX + "MakeElasticsearchContainerImageExplicit");
        assertEquals(List.of(
                        MarkSelectedElasticsearchProjects.class.getName(),
                        PREFIX + "MakeSelectedElasticsearchContainerImageExplicit"),
                composition(wrapper).stream().map(Recipe::getName).toList());
    }

    @Test
    void recommendedRuntimeTreeIsFixedAndExcludesBothBroadOfficialAggregates() {
        Recipe recommended = ENVIRONMENT.activateRecipes(
                PREFIX + "MigrateElasticsearchTo1_21_4");
        assertEquals(List.of(
                        MarkSelectedElasticsearchProjects.class.getName(),
                        PREFIX + "MakeSelectedElasticsearchContainerImageExplicit",
                        UpgradeSelectedTestcontainersElasticsearchDependency.class.getName(),
                        PREFIX + "FindElasticsearch1_21_4BuildRisks",
                        PREFIX + "FindSelectedElasticsearchContainer1_21_4SourceRisks"),
                composition(recommended).stream().map(Recipe::getName).toList());

        Set<String> names = flatten(recommended)
                .map(Recipe::getName).collect(Collectors.toSet());
        assertTrue(names.contains(LEAF), names.toString());
        assertTrue(names.contains(MarkSelectedElasticsearchProjects.class.getName()),
                names.toString());
        assertTrue(names.contains(UpgradeSelectedTestcontainersElasticsearchDependency.class.getName()),
                names.toString());
        assertTrue(names.contains(FindElasticsearchBuildRisks.class.getName()), names.toString());
        assertTrue(names.contains(FindElasticsearchContainerSourceRisks.class.getName()), names.toString());
        assertFalse(names.contains(AGGREGATE), names.toString());
        assertFalse(names.contains(TESTCONTAINERS_2), names.toString());
        assertFalse(names.contains(
                "org.openrewrite.java.testing.testcontainers.Testcontainers2Dependencies"), names.toString());
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = unwrap(recipe);
        return Stream.concat(Stream.of(unwrapped), unwrapped.getRecipeList().stream()
                .filter(ElasticsearchOfficialRecipeReuseTest::isCompositionRecipe)
                .flatMap(ElasticsearchOfficialRecipeReuseTest::flatten));
    }

    private static List<Recipe> composition(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .filter(ElasticsearchOfficialRecipeReuseTest::isCompositionRecipe)
                .map(ElasticsearchOfficialRecipeReuseTest::unwrap)
                .toList();
    }

    private static boolean isCompositionRecipe(Recipe recipe) {
        return !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether"
                .equals(recipe.getName());
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegate) current = delegate.getDelegate();
        return current;
    }

    private static String manifestAttribute(Path jar, String name) throws Exception {
        try (JarFile artifact = new JarFile(jar.toFile())) {
            String value = artifact.getManifest().getMainAttributes().getValue(name);
            if (value == null) throw new IOException("Missing manifest attribute " + name);
            return value;
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path artifact(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
