package com.huawei.clouds.openrewrite.springsecuritycore;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.xml.ChangeTagAttribute;
import org.openrewrite.xml.ChangeTagName;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringSecurityCoreOfficialRecipeAuditTest {
    private static final String DETERMINISTIC =
            "com.huawei.clouds.openrewrite.springsecuritycore.MigrateDeterministicSpringSecurityCore6";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.springsecuritycore.MigrateSpringSecurityCoreTo6_5_11";
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.springsecuritycore.UpgradeSpringSecurityCoreTo6_5_11";
    private static final String OFFICIAL_65 =
            "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_5";
    private static final Environment ENVIRONMENT = SpringSecurityCoreTestSupport.environment();

    @Test
    void pinsTheAuditedRewriteSpringArtifact() throws Exception {
        Class<?> official = Class.forName(
                "org.openrewrite.java.spring.security5.UpdatePbkdf2PasswordEncoder");
        assertEquals("6.35.0", official.getPackage().getImplementationVersion());
        assertEquals("d28afcb6661ad413539056de0936c5489ff9d8ee",
                manifestAttribute(official, "Full-Change"));
    }

    @Test
    void official65AggregateIsExactlyTheRejectedBroadDependencySelection() {
        List<Recipe> children = effectiveChildren(ENVIRONMENT.activateRecipes(OFFICIAL_65));
        assertEquals(2, children.size());
        UpgradeDependencyVersion family = assertInstanceOf(UpgradeDependencyVersion.class, children.get(0));
        assertUpgrade(family, "*", "6.5.x");
        UpgradeDependencyVersion authorizationServer =
                assertInstanceOf(UpgradeDependencyVersion.class, children.get(1));
        assertUpgrade(authorizationServer, "spring-security-oauth2-authorization-server", "1.5.x");
    }

    @Test
    void deterministicRecipeDirectlyComposesOnlyAuditedCoreLeavesWithExactOptions() {
        DeclarativeRecipe recipe = assertInstanceOf(
                DeclarativeRecipe.class, ENVIRONMENT.activateRecipes(DETERMINISTIC));
        assertEquals(List.of(FindAuthoredSourceFiles.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());
        List<Recipe> children = effectiveChildren(recipe);
        assertEquals(List.of(
                        "org.openrewrite.java.spring.security5.UpdatePbkdf2PasswordEncoder",
                        "org.openrewrite.java.spring.security5.UpdateSCryptPasswordEncoder",
                        "org.openrewrite.java.spring.security5.UpdateArgon2PasswordEncoder",
                        "org.openrewrite.java.spring.security5.ReplaceGlobalMethodSecurityWithMethodSecurity",
                        "com.huawei.clouds.openrewrite.springsecuritycore." +
                        "MigrateSpringSecurityMethodSecurityXml",
                        "org.openrewrite.java.spring.security6.UpdateEnableReactiveMethodSecurity"),
                children.stream().map(Recipe::getName).toList());

        DeclarativeRecipe xml = assertInstanceOf(DeclarativeRecipe.class, children.get(4));
        List<Recipe> xmlChildren = effectiveChildren(xml);
        assertEquals(List.of(
                        "com.huawei.clouds.openrewrite.springsecuritycore." +
                        "MigrateOfficialUnprefixedSpringSecurityMethodSecurityXml",
                        MigratePrefixedSpringSecurityMethodSecurityXml.class.getName()),
                xmlChildren.stream().map(Recipe::getName).toList());

        DeclarativeRecipe officialGate =
                assertInstanceOf(DeclarativeRecipe.class, xmlChildren.get(0));
        assertEquals(List.of(FindOfficialSpringSecurityMethodSecurityXmlFiles.class),
                officialGate.getPreconditions().stream().map(Object::getClass).toList());
        Recipe officialXml = effectiveChildren(officialGate).get(0);
        assertEquals(
                "org.openrewrite.java.spring.security5." +
                "ReplaceGlobalMethodSecurityWithMethodSecurityXml",
                officialXml.getName());
        List<Recipe> officialLeaves = effectiveChildren(officialXml);
        assertEquals(2, officialLeaves.size());
        ChangeTagName name = assertInstanceOf(ChangeTagName.class, officialLeaves.get(0));
        assertEquals("global-method-security", name.getElementName());
        assertEquals("method-security", name.getNewName());
        ChangeTagAttribute attribute =
                assertInstanceOf(ChangeTagAttribute.class, officialLeaves.get(1));
        assertEquals("method-security", attribute.getElementName());
        assertEquals("pre-post-enabled", attribute.getAttributeName());
        assertNull(attribute.getNewValue());
        assertEquals("true", attribute.getOldValue());
        assertNull(attribute.getRegex());
    }

    @Test
    void standaloneUpgradeAlwaysScansBeforeItsMarkerGatedDependencyVisitor() {
        DeclarativeRecipe recipe = assertInstanceOf(
                DeclarativeRecipe.class, ENVIRONMENT.activateRecipes(UPGRADE));
        assertEquals(List.of(
                        MarkSelectedSpringSecurityCoreProjects.class.getName(),
                        UpgradeSelectedSpringSecurityCoreDependency.class.getName()),
                effectiveChildren(recipe).stream().map(Recipe::getName).toList());
    }

    @Test
    void recommendedRuntimeTreeRejectsBroadAggregatesSelectorsAndUnrelatedLeaves() {
        List<Recipe> tree = recipeTree(ENVIRONMENT.activateRecipes(RECOMMENDED));
        Set<String> names = tree.stream().map(Recipe::getName).collect(Collectors.toSet());
        Set<String> excluded = Set.of(
                "org.openrewrite.java.spring.security5.UpgradeSpringSecurity_5_7",
                "org.openrewrite.java.spring.security5.UpgradeSpringSecurity_5_8",
                "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_0",
                "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_1",
                "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_2",
                "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_3",
                "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_4",
                OFFICIAL_65,
                "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_2",
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion");
        assertTrue(java.util.Collections.disjoint(names, excluded), names.toString());
        assertFalse(tree.stream().anyMatch(UpgradeDependencyVersion.class::isInstance), names.toString());
        assertTrue(names.contains(UpgradeSelectedSpringSecurityCoreDependency.class.getName()), names.toString());
        assertTrue(names.contains(
                "org.openrewrite.java.spring.security5.UpdatePbkdf2PasswordEncoder"), names.toString());
        assertTrue(names.contains(
                "org.openrewrite.java.spring.security5.search.FindEncryptorsQueryableTextUses"), names.toString());
        assertFalse(names.contains(
                "org.openrewrite.java.spring.security5.WebSecurityConfigurerAdapter"), names.toString());
        assertFalse(names.contains(
                "org.openrewrite.java.spring.security6.oauth2.client.OAuth2LoginLambdaDsl"), names.toString());
        assertTrue(names.contains(MarkSelectedSpringSecurityCoreProjects.class.getName()), names.toString());
        assertEquals(1, tree.stream()
                .filter(MarkSelectedSpringSecurityCoreProjects.class::isInstance).count());
        assertTrue(names.contains(
                FindSpringSecurityCore6511DowngradeConflicts.class.getName()), names.toString());
        assertTrue(names.contains(
                "org.openrewrite.java.spring.security5." +
                "ReplaceGlobalMethodSecurityWithMethodSecurityXml"), names.toString());

        DeclarativeRecipe selectedMigration = assertInstanceOf(
                DeclarativeRecipe.class,
                tree.stream().filter(candidate -> candidate.getName().endsWith(
                        "MigrateSelectedDeterministicSpringSecurityCore6")).findFirst().orElseThrow());
        assertEquals(List.of(FindSelectedSpringSecurityCoreProjectFiles.class),
                selectedMigration.getPreconditions().stream().map(Object::getClass).toList());
        DeclarativeRecipe selectedBuild = assertInstanceOf(
                DeclarativeRecipe.class,
                tree.stream().filter(candidate -> candidate.getName().endsWith(
                        "FindSelectedSpringSecurityCore6_5_11BuildRisks")).findFirst().orElseThrow());
        assertEquals(List.of(FindSelectedSpringSecurityCoreProjectFiles.class),
                selectedBuild.getPreconditions().stream().map(Object::getClass).toList());
        DeclarativeRecipe selectedSource = assertInstanceOf(
                DeclarativeRecipe.class,
                tree.stream().filter(candidate -> candidate.getName().endsWith(
                        "FindSelectedSpringSecurityCore6_5_11SourceRisks")).findFirst().orElseThrow());
        assertEquals(List.of(FindSelectedSpringSecurityCoreProjectFiles.class),
                selectedSource.getPreconditions().stream().map(Object::getClass).toList());
    }

    private static void assertUpgrade(UpgradeDependencyVersion recipe, String artifact, String version) {
        assertEquals("org.springframework.security", recipe.getGroupId());
        assertEquals(artifact, recipe.getArtifactId());
        assertEquals(version, recipe.getNewVersion());
        assertEquals(Boolean.FALSE, recipe.getOverrideManagedVersion());
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        return unwrap(recipe).getRecipeList().stream()
                .map(SpringSecurityCoreOfficialRecipeAuditTest::unwrap)
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
        for (Recipe child : effectiveChildren(recipe)) collect(child, recipes);
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) current = delegating.getDelegate();
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
