package com.huawei.clouds.openrewrite.springwebmvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodInvocationReturnType;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.DeleteMethodArgument;
import org.openrewrite.java.ReorderMethodArguments;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.migrate.UpgradeJavaVersion;
import org.openrewrite.java.migrate.jakarta.UpdateGetRealPath;
import org.openrewrite.java.search.DoesNotUseType;
import org.openrewrite.java.spring.framework.MigrateUtf8MediaTypes;
import org.openrewrite.java.spring.framework.MigrateWebMvcConfigurerAdapter;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
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

class SpringWebMvcOfficialRecipeReuseTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.springwebmvc.";
    private static final String AUTO = PREFIX + "MigrateDeterministicSpringWebMvc6Java";
    private static final String RECOMMENDED = PREFIX + "MigrateSpringWebMvcTo6_2_19";
    private static final String NAMESPACE = PREFIX + "MigrateSpringWebMvcServletNamespaces";
    private static final String SERVLET = PREFIX + "MigrateSpringWebMvcServlet6Apis";
    private static final String FRAMEWORK_62 =
            "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_2";
    private static final String JAKARTA_EE_10 =
            "org.openrewrite.java.migrate.jakarta.JakartaEE10";
    private static final String JAVAX_SERVLET =
            "org.openrewrite.java.migrate.jakarta.JavaxServletToJakartaServlet";
    private static final String SERVLET_REMOVALS =
            "org.openrewrite.java.migrate.jakarta.RemovalsServletJakarta10";
    private static final String JAVA_17 =
            "org.openrewrite.java.migrate.UpgradeToJava17";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springwebmvc",
                                  "org.openrewrite.java.spring.framework",
                                  "org.openrewrite.java.migrate.jakarta",
                                  "org.openrewrite.java.migrate")
            .build();

    @Test
    void pinsEveryAuditedRuntimeArtifactAndTarget() throws Exception {
        assertArtifact(Recipe.class, "rewrite-core-8.87.5.jar",
                "b3008cc4a1f0c43f562da16e5933a2a56d9bc568",
                "a7ff59eebc8072353ec5c3aee3e2033bc69a844b3c9ce2e9be8d4adaec10cbf8");
        assertArtifact(ChangePackage.class, "rewrite-java-8.87.5.jar",
                "91e23c2858176877428ddc03e146d2bb023217a8",
                "a378253fe0c0865ab39d1743e468fe3d2557d7760e0a6897de294ca18ea90043");
        assertArtifact(MigrateWebMvcConfigurerAdapter.class,
                "rewrite-spring-6.35.0.jar",
                "d28afcb6661ad413539056de0936c5489ff9d8ee",
                "27df444210c8bfee7e9d0f04d6d6f7986d2bee36bcd472d8307912613e93e98b");
        assertArtifact(UpdateGetRealPath.class,
                "rewrite-migrate-java-3.40.0.jar",
                "658481254a6ee678f5f162e51d8d49ee01c75877",
                "8c00217ff2cf4dc9c139a1eff49ed1403fe20e010e42295f5aeb1dd9a5872dc6");
        assertArtifact(UpgradeDependencyVersion.class,
                "rewrite-java-dependencies-1.59.0.jar",
                "decb8dbb2b5b726f8815efc51c85c34a60268bb0",
                "b5c5ffaa0aea06cbbb8ae110ed138261bce621806c789f14ea0f3fe92cf95550");
        assertFileHash(DispatcherServlet.class, "spring-webmvc-6.2.19.jar",
                "134f42320cedd31f54f683d2ca9936a4e015c011fb1882a31fa7213e2d8c7e94");
    }

    @Test
    void namespaceUsesTheOfficialServletLeafButGuardsRemovedTypes() {
        DeclarativeRecipe recipe = declarative(NAMESPACE);
        List<Recipe> composition = composition(recipe);
        assertEquals(List.of(ChangePackage.class),
                composition.stream().map(Object::getClass).toList());

        ChangePackage change = (ChangePackage) composition.get(0);
        assertEquals("javax.servlet", change.getOldPackageName());
        assertEquals("jakarta.servlet", change.getNewPackageName());
        assertEquals(Boolean.TRUE, change.getRecursive());

        assertEquals(IsSpringWebMvcNonGeneratedSource.class,
                recipe.getPreconditions().get(0).getClass());
        assertEquals(Set.of(
                        "javax.servlet.SingleThreadModel",
                        "javax.servlet.http.HttpSessionContext",
                        "javax.servlet.http.HttpUtils"),
                recipe.getPreconditions().stream()
                        .filter(DoesNotUseType.class::isInstance)
                        .map(DoesNotUseType.class::cast)
                        .peek(guard -> assertEquals(Boolean.TRUE, guard.getIncludeImplicit()))
                        .map(DoesNotUseType::getFullyQualifiedTypeName)
                        .collect(Collectors.toSet()));
    }

    @Test
    void officialJavaxServletAggregateProvesWhyOnlyItsNamespaceLeafIsAdopted() {
        List<Recipe> official = composition(
                ENVIRONMENT.activateRecipes(JAVAX_SERVLET));
        assertTrue(official.stream().anyMatch(ChangePackage.class::isInstance));
        assertTrue(official.stream().anyMatch(ChangeDependency.class::isInstance));
        assertTrue(official.stream().anyMatch(UpgradeDependencyVersion.class::isInstance));
        assertTrue(official.stream().anyMatch(AddDependency.class::isInstance));

        ChangePackage officialPackage = official.stream()
                .filter(ChangePackage.class::isInstance)
                .map(ChangePackage.class::cast)
                .findFirst().orElseThrow();
        ChangePackage adoptedPackage = composition(declarative(NAMESPACE)).stream()
                .map(ChangePackage.class::cast)
                .findFirst().orElseThrow();
        assertEquals(officialPackage.getOldPackageName(), adoptedPackage.getOldPackageName());
        assertEquals(officialPackage.getNewPackageName(), adoptedPackage.getNewPackageName());
        assertEquals(officialPackage.getRecursive(), adoptedPackage.getRecursive());
    }

    @Test
    void officialServletAggregateContainsTheReturnTypeUnsafeRename() {
        Set<String> officialRenames = flatten(
                ENVIRONMENT.activateRecipes(SERVLET_REMOVALS))
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .map(SpringWebMvcOfficialRecipeReuseTest::methodRename)
                .collect(Collectors.toSet());
        assertTrue(officialRenames.contains(
                "jakarta.servlet.http.HttpSession getValueNames()->getAttributeNames"));
    }

    @Test
    void servletCompositionReusesEverySafeOfficialLeafAndRejectsUnsafeRename() {
        DeclarativeRecipe recipe = declarative(SERVLET);
        List<Recipe> adopted = composition(recipe);

        Set<String> officialRenames = flatten(
                ENVIRONMENT.activateRecipes(SERVLET_REMOVALS))
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .map(SpringWebMvcOfficialRecipeReuseTest::methodRename)
                .collect(Collectors.toSet());
        Set<String> expectedSafeRenames = new HashSet<>(officialRenames);
        assertTrue(expectedSafeRenames.remove(
                "jakarta.servlet.http.HttpSession getValueNames()->getAttributeNames"));
        assertEquals(expectedSafeRenames, adopted.stream()
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .map(SpringWebMvcOfficialRecipeReuseTest::methodRename)
                .collect(Collectors.toSet()));

        assertEquals(flatten(ENVIRONMENT.activateRecipes(SERVLET_REMOVALS))
                        .filter(DeleteMethodArgument.class::isInstance)
                        .map(DeleteMethodArgument.class::cast)
                        .map(SpringWebMvcOfficialRecipeReuseTest::argumentDeletion)
                        .collect(Collectors.toSet()),
                adopted.stream()
                        .filter(DeleteMethodArgument.class::isInstance)
                        .map(DeleteMethodArgument.class::cast)
                        .map(SpringWebMvcOfficialRecipeReuseTest::argumentDeletion)
                        .collect(Collectors.toSet()));
        assertEquals(flatten(ENVIRONMENT.activateRecipes(SERVLET_REMOVALS))
                        .filter(ReorderMethodArguments.class::isInstance)
                        .map(ReorderMethodArguments.class::cast)
                        .map(SpringWebMvcOfficialRecipeReuseTest::argumentReorder)
                        .collect(Collectors.toSet()),
                adopted.stream()
                        .filter(ReorderMethodArguments.class::isInstance)
                        .map(ReorderMethodArguments.class::cast)
                        .map(SpringWebMvcOfficialRecipeReuseTest::argumentReorder)
                        .collect(Collectors.toSet()));
        assertEquals(1, adopted.stream().filter(UpdateGetRealPath.class::isInstance).count());
        assertTrue(adopted.stream().anyMatch(recipeNode ->
                "org.openrewrite.java.migrate.jakarta.ServletCookieBehaviorChangeRFC6265"
                        .equals(recipeNode.getName())));
        assertFalse(flatten(recipe).map(Recipe::getName)
                .anyMatch(SERVLET_REMOVALS::equals));
    }

    @Test
    void deterministicCompositionUsesOnlyAuditedOfficialLeavesAndOneLocalGap() {
        DeclarativeRecipe recipe = declarative(AUTO);
        List<Recipe> composition = composition(recipe);

        assertEquals(List.of(
                        NAMESPACE,
                        SERVLET,
                        MigrateUtf8MediaTypes.class.getName(),
                        "org.openrewrite.java.spring.framework.MigrateMethodArgumentNotValidExceptionErrorMethod",
                        MigrateWebMvcConfigurerAdapter.class.getName(),
                        MigrateHandlerInterceptorAdapterPreservingAsyncContract.class.getName(),
                        "org.openrewrite.java.spring.framework.MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode",
                        ChangeMethodInvocationReturnType.class.getName(),
                        "org.openrewrite.java.spring.framework.MigrateResponseStatusException"),
                composition.stream().map(Recipe::getName).toList());
        assertEquals(List.of(IsSpringWebMvcNonGeneratedSource.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());

        ChangeMethodInvocationReturnType status = composition.stream()
                .filter(ChangeMethodInvocationReturnType.class::isInstance)
                .map(ChangeMethodInvocationReturnType.class::cast)
                .findFirst().orElseThrow();
        assertEquals("org.springframework.http.ResponseEntity getStatusCode()",
                status.getMethodPattern());
        assertEquals("org.springframework.http.HttpStatusCode",
                status.getNewReturnType());
    }

    @Test
    void fixedFrameworkAggregateContainsAcceptedLeavesAndRejectedCrossModuleWork() {
        Set<String> tree = flatten(ENVIRONMENT.activateRecipes(FRAMEWORK_62))
                .map(Recipe::getName)
                .collect(Collectors.toSet());
        assertTrue(tree.contains(MigrateUtf8MediaTypes.class.getName()));
        assertTrue(tree.contains(
                "org.openrewrite.java.spring.framework.MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode"));
        assertTrue(tree.contains(
                "org.openrewrite.java.spring.framework.MigrateResponseStatusException"));
        assertTrue(tree.contains(
                "org.openrewrite.java.spring.framework.MigrateHandlerInterceptor"));
        assertTrue(tree.contains(
                "org.openrewrite.java.spring.kafka.UpgradeSpringKafka_3_0"));
        assertTrue(flatten(ENVIRONMENT.activateRecipes(FRAMEWORK_62))
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .anyMatch(selector -> "org.springframework".equals(selector.getGroupId()) &&
                                      "*".equals(selector.getArtifactId())));
    }

    @Test
    void broadJava17AndJakartaTreesAreAuditedButNeverActivated() {
        Set<String> javaTree = flatten(ENVIRONMENT.activateRecipes(JAVA_17))
                .map(Recipe::getName).collect(Collectors.toSet());
        assertTrue(javaTree.contains(UpgradeJavaVersion.class.getName()));
        assertTrue(javaTree.contains("org.openrewrite.java.migrate.Java8toJava11"));

        Set<String> jakartaTree = flatten(ENVIRONMENT.activateRecipes(JAKARTA_EE_10))
                .map(Recipe::getName).collect(Collectors.toSet());
        assertTrue(jakartaTree.contains(
                "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta"));
        assertTrue(jakartaTree.contains(
                "org.openrewrite.java.migrate.jakarta.MigrationToJakarta10Apis"));
        assertTrue(jakartaTree.contains(
                "org.openrewrite.java.migrate.jakarta.MigratePluginsForJakarta10"));
        assertTrue(jakartaTree.contains(
                "org.openrewrite.java.migrate.jakarta.JettyUpgradeEE10"));
    }

    @Test
    void recommendedRuntimeTreeRejectsEveryBroadOrUnsafeAggregate() {
        Set<String> activated = flatten(
                ENVIRONMENT.activateRecipes(RECOMMENDED))
                .map(Recipe::getName)
                .collect(Collectors.toSet());

        Set<String> rejected = Set.of(
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_5_3",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_0",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_1",
                FRAMEWORK_62,
                JAKARTA_EE_10,
                "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta",
                JAVAX_SERVLET,
                SERVLET_REMOVALS,
                JAVA_17,
                UpgradeJavaVersion.class.getName(),
                "org.openrewrite.java.spring.framework.MigrateHandlerInterceptor",
                "org.openrewrite.java.spring.framework.MigrateSpringAssert",
                "org.openrewrite.java.spring.framework.MigrateUriComponentsBuilderMethods",
                "org.openrewrite.java.spring.boot3.MaintainTrailingSlashURLMappings",
                "org.openrewrite.java.spring.boot3.AddRouteTrailingSlash");
        assertTrue(java.util.Collections.disjoint(activated, rejected), activated.toString());
        assertFalse(flatten(ENVIRONMENT.activateRecipes(RECOMMENDED))
                .anyMatch(UpgradeDependencyVersion.class::isInstance));
        assertTrue(activated.contains(MigrateUtf8MediaTypes.class.getName()));
        assertTrue(activated.contains(
                "org.openrewrite.java.spring.framework.MigrateMethodArgumentNotValidExceptionErrorMethod"));
        assertTrue(activated.contains(UpdateGetRealPath.class.getName()));
        assertTrue(activated.contains(ChangeMethodInvocationReturnType.class.getName()));

        Set<String> adoptedRenames = flatten(
                ENVIRONMENT.activateRecipes(RECOMMENDED))
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .map(SpringWebMvcOfficialRecipeReuseTest::methodRename)
                .collect(Collectors.toSet());
        assertFalse(adoptedRenames.contains(
                "jakarta.servlet.http.HttpSession getValueNames()->getAttributeNames"));
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

    private static DeclarativeRecipe declarative(String name) {
        return assertInstanceOf(DeclarativeRecipe.class,
                ENVIRONMENT.activateRecipes(name));
    }

    private static List<Recipe> composition(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .filter(SpringWebMvcOfficialRecipeReuseTest::isCompositionRecipe)
                .map(SpringWebMvcOfficialRecipeReuseTest::unwrap)
                .toList();
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = unwrap(recipe);
        return Stream.concat(Stream.of(unwrapped), unwrapped.getRecipeList().stream()
                .filter(SpringWebMvcOfficialRecipeReuseTest::isCompositionRecipe)
                .flatMap(SpringWebMvcOfficialRecipeReuseTest::flatten));
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

    private static void assertFileHash(Class<?> type, String fileName,
                                       String hash) throws Exception {
        assertEquals(fileName, artifact(type).getFileName().toString());
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
