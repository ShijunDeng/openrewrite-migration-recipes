package com.huawei.clouds.openrewrite.logbackcore;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.dependencies.RemoveDependency;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.logging.logback.ConfigureLoggerLevel;
import org.openrewrite.xml.ChangeTagAttribute;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogbackCoreOfficialRecipeReuseTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.logbackcore.";
    private static final String DETERMINISTIC = PREFIX + "MigrateDeterministicLogbackCore1_5_34";
    private static final String RECOMMENDED = PREFIX + "MigrateLogbackCoreTo1_5_34";
    private static final String OFFICIAL = "org.openrewrite.java.logging.logback.Log4jToLogback";

    @Test
    void pinsAuditedLoggingFrameworkArtifact() throws Exception {
        assertEquals("3.30.0", ConfigureLoggerLevel.class.getPackage().getImplementationVersion());
        assertEquals("c357a7209d721078dc942a777b1d8cc95941f722",
                manifestAttribute(ConfigureLoggerLevel.class, "Full-Change"));
        assertEquals("366a1cd43ee8e0f4378cac52036831df07d74d1648222d0664de2c63f7e26827",
                sha256(ConfigureLoggerLevel.class));
    }

    @Test
    void officialAggregateIsExactlyTheRejectedLog4jFrameworkMigration() {
        List<Recipe> children = effectiveChildren(activate(OFFICIAL));
        assertEquals(List.of(
                        "org.openrewrite.java.logging.slf4j.Log4jToSlf4j",
                        "org.openrewrite.java.logging.logback.Log4jAppenderToLogback",
                        "org.openrewrite.java.logging.logback.Log4jLayoutToLogback",
                        AddDependency.class.getName(),
                        AddDependency.class.getName(),
                        AddDependency.class.getName(),
                        RemoveDependency.class.getName()),
                children.stream().map(Recipe::getName).toList());

        List<AddDependency> additions = children.stream()
                .filter(AddDependency.class::isInstance)
                .map(AddDependency.class::cast)
                .toList();
        assertEquals(List.of(
                        "ch.qos.logback:logback-core",
                        "ch.qos.logback:logback-classic",
                        "org.slf4j:slf4j-api"),
                additions.stream().map(add -> add.getGroupId() + ":" + add.getArtifactId()).toList());
        for (AddDependency addition : additions) {
            assertEquals("latest.release", addition.getVersion());
            assertEquals("org.apache.logging.log4j.*", addition.getOnlyIfUsing());
        }

        RemoveDependency removal = assertInstanceOf(RemoveDependency.class, children.get(6));
        assertEquals("org.apache.logging.log4j", removal.getGroupId());
        assertEquals("log4j-*", removal.getArtifactId());
        assertNull(removal.getUnlessUsing());
    }

    @Test
    void deterministicRecipeDirectlyComposesSevenExactCoreLeaves() {
        List<Recipe> children = effectiveChildren(activate(DETERMINISTIC));
        assertEquals(List.of(
                        ChangeType.class,
                        ChangeType.class,
                        ChangeType.class,
                        ChangeMethodName.class,
                        ChangeMethodName.class,
                        ChangeTagAttribute.class,
                        ChangeTagAttribute.class),
                children.stream().map(Recipe::getClass).toList());

        List<ChangeType> types = children.subList(0, 3).stream()
                .map(ChangeType.class::cast)
                .toList();
        assertEquals(List.of(
                        "ch.qos.logback.core.hook.DelayingShutdownHook->ch.qos.logback.core.hook.DefaultShutdownHook",
                        "ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP->" +
                        "ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy",
                        "ch.qos.logback.core.joran.action.ActionConst->ch.qos.logback.core.joran.JoranConstants"),
                types.stream()
                        .map(type -> type.getOldFullyQualifiedTypeName() + "->" +
                                     type.getNewFullyQualifiedTypeName())
                        .toList());
        assertTrue(types.stream().allMatch(type -> Boolean.TRUE.equals(type.getIgnoreDefinition())));

        List<ChangeMethodName> methods = children.subList(3, 5).stream()
                .map(ChangeMethodName.class::cast)
                .toList();
        assertEquals(List.of(
                        "ch.qos.logback.core.joran.spi.ConfigurationWatchList getMainURL()",
                        "ch.qos.logback.core.joran.spi.ConfigurationWatchList setMainURL(java.net.URL)"),
                methods.stream().map(ChangeMethodName::getMethodPattern).toList());
        assertEquals(List.of("getTopURL", "setTopURL"),
                methods.stream().map(ChangeMethodName::getNewMethodName).toList());
        assertTrue(methods.stream().allMatch(method ->
                Boolean.TRUE.equals(method.getIgnoreDefinition()) &&
                !Boolean.TRUE.equals(method.getMatchOverrides())));

        List<ChangeTagAttribute> xml = children.subList(5, 7).stream()
                .map(ChangeTagAttribute.class::cast)
                .toList();
        assertEquals(List.of(
                        "\\Qch.qos.logback.core.hook.DelayingShutdownHook\\E",
                        "\\Qch.qos.logback.core.rolling.SizeAndTimeBasedFNATP\\E"),
                xml.stream().map(ChangeTagAttribute::getOldValue).toList());
        assertEquals(List.of(
                        "ch.qos.logback.core.hook.DefaultShutdownHook",
                        "ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy"),
                xml.stream().map(ChangeTagAttribute::getNewValue).toList());
        assertTrue(xml.stream().allMatch(change ->
                "//*".equals(change.getElementName()) &&
                "class".equals(change.getAttributeName()) &&
                Boolean.TRUE.equals(change.getRegex())));
    }

    @Test
    void recommendedTreeKeepsStrictUpgradeFirstAndExcludesBroadOfficialMutations() {
        Recipe recommended = activate(RECOMMENDED);
        assertEquals(List.of(
                        PREFIX + "UpgradeLogbackCoreTo1_5_34",
                        DETERMINISTIC,
                        PREFIX + "FindLogbackCore1_5_34BuildRisks",
                        PREFIX + "FindLogbackCore1_5_34SourceRisks",
                        PREFIX + "FindLogbackCore1_5_34ConfigurationRisks"),
                effectiveChildren(recommended).stream().map(Recipe::getName).toList());

        List<Recipe> local = recipeTree(recommended);
        assertTrue(local.stream().noneMatch(recipe -> OFFICIAL.equals(recipe.getName())));
        assertTrue(local.stream().noneMatch(AddDependency.class::isInstance));
        assertTrue(local.stream().noneMatch(RemoveDependency.class::isInstance));
        assertTrue(local.stream().noneMatch(UpgradeDependencyVersion.class::isInstance));
        assertFalse(local.stream().anyMatch(recipe ->
                recipe.getName().startsWith("org.openrewrite.java.logging.logback.Log4j")));
        assertEquals(1, local.stream()
                .filter(UpgradeSelectedLogbackCoreDependency.class::isInstance)
                .count());
    }

    private static Recipe activate(String name) {
        return unwrap(Environment.builder().scanRuntimeClasspath().build().activateRecipes(name));
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .map(LogbackCoreOfficialRecipeReuseTest::unwrap)
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
