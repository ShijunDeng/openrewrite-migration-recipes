package com.huawei.clouds.openrewrite.junitjupiteraggregate;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.testing.junit6.MigrateMethodOrdererAlphanumeric;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class OfficialJUnitJupiter6LeavesTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.junitjupiteraggregate.MigrateOfficialJUnitJupiter6Leaves";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion().dependsOn(JUnitJupiterAggregateTestApi.sources()))
                .recipe(environment().activateRecipes(RECIPE));
    }

    @Test
    void officialRecipeTreeIsDiscoveredValidAndExcludesBroadAggregate() {
        Recipe recipe = environment().activateRecipes(RECIPE);
        assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                () -> recipe.validateAll().toString());
        List<Recipe> recipes = flatten(recipe).toList();
        Recipe orderer = recipes.stream().filter(candidate ->
                "org.openrewrite.java.testing.junit6.MigrateMethodOrdererAlphanumeric"
                        .equals(candidate.getName())).findFirst().orElseThrow();
        assertEquals(MigrateMethodOrdererAlphanumeric.class, orderer.getClass());
        assertEquals("3.42.1", orderer.getClass().getPackage().getImplementationVersion());
        assertTrue(orderer.getClass().getProtectionDomain().getCodeSource()
                        .getLocation().toString().contains("rewrite-testing-frameworks-3.42.1.jar"),
                "The official JUnit leaf must come from the pinned binary artifact");

        List<String> forbidden = List.of(
                "JUnit5to6Migration",
                "RemoveInterceptDynamicTest",
                "MinimumJreConditions",
                "RemoveCsvFileSourceLineSeparator",
                "RemoveObsoleteJunitPlatformProperties",
                "JUnitPioneer",
                "UpgradeDependencyVersion",
                "RemoveDependency",
                "DeleteMethodArgument");
        assertFalse(recipes.stream().map(Recipe::getName)
                .anyMatch(name -> forbidden.stream().anyMatch(name::contains)));
    }

    @Test
    void reusesOfficialStoreAndOrdererLeavesIncludingStaticImport() {
        rewriteRun(java(
                """
                  import static org.junit.jupiter.api.MethodOrderer.Alphanumeric;
                  import org.junit.jupiter.api.TestMethodOrder;
                  import org.junit.jupiter.api.extension.ExtensionContext;

                  @TestMethodOrder(Alphanumeric.class)
                  class Extension {
                      Object value(ExtensionContext.Store store) {
                          return store.getOrComputeIfAbsent(String.class);
                      }
                  }
                  """,
                """
                  import static org.junit.jupiter.api.MethodOrderer.MethodName;
                  import org.junit.jupiter.api.TestMethodOrder;
                  import org.junit.jupiter.api.extension.ExtensionContext;

                  @TestMethodOrder(MethodName.class)
                  class Extension {
                      Object value(ExtensionContext.Store store) {
                          return store.computeIfAbsent(String.class);
                      }
                  }
                  """, source -> source.afterRecipe(after -> {
                    boolean[] found = {false};
                    new JavaIsoVisitor<boolean[]>() {
                        @Override
                        public J.Identifier visitIdentifier(J.Identifier identifier, boolean[] p) {
                            J.Identifier visited = super.visitIdentifier(identifier, p);
                            if ("MethodName".equals(visited.getSimpleName()) &&
                                TypeUtils.isOfClassType(visited.getType(),
                                        "org.junit.jupiter.api.MethodOrderer$MethodName")) {
                                p[0] = true;
                            }
                            return visited;
                        }
                    }.visitNonNull(after, found);
                    assertTrue(found[0], "The official rename must retain JUnit 6 nested-type metadata");
                })));
    }

    @Test
    void reusesOfficialParameterizedConstantLeaves() {
        rewriteRun(java(
                """
                  import static org.junit.jupiter.params.ParameterizedTest.INDEX_PLACEHOLDER;
                  import org.junit.jupiter.params.ParameterizedTest;
                  class Names {
                      String index = INDEX_PLACEHOLDER;
                      String display = ParameterizedTest.DISPLAY_NAME_PLACEHOLDER;
                  }
                  """,
                """
                  import org.junit.jupiter.params.ParameterizedInvocationConstants;

                  import static org.junit.jupiter.params.ParameterizedInvocationConstants.INDEX_PLACEHOLDER;

                  class Names {
                      String index = INDEX_PLACEHOLDER;
                      String display = ParameterizedInvocationConstants.DISPLAY_NAME_PLACEHOLDER;
                  }
                  """));
    }

    @Test
    void reusesOfficialJUnit514TypeAndMethodLeaves() {
        rewriteRun(java(
                """
                  import java.util.Set;
                  import org.junit.jupiter.api.extension.MediaType;
                  import org.junit.jupiter.params.support.ParameterInfo;
                  import org.junit.platform.commons.support.Resource;
                  import org.junit.platform.engine.discovery.ClasspathResourceSelector;
                  import org.junit.platform.engine.discovery.DiscoverySelectors;
                  import org.junit.platform.engine.reporting.OutputDirectoryProvider;
                  class PlatformTypes {
                      MediaType media;
                      ParameterInfo parameter;
                      Resource resource;
                      OutputDirectoryProvider output;
                      Object select(Set<String> names) { return DiscoverySelectors.selectClasspathResource(names); }
                      Object resources(ClasspathResourceSelector selector) { return selector.getClasspathResources(); }
                  }
                  """,
                """
                  import java.util.Set;

                  import org.junit.jupiter.api.MediaType;
                  import org.junit.jupiter.params.ParameterInfo;
                  import org.junit.platform.commons.io.Resource;
                  import org.junit.platform.engine.OutputDirectoryCreator;
                  import org.junit.platform.engine.discovery.ClasspathResourceSelector;
                  import org.junit.platform.engine.discovery.DiscoverySelectors;

                  class PlatformTypes {
                      MediaType media;
                      ParameterInfo parameter;
                      Resource resource;
                      OutputDirectoryCreator output;
                      Object select(Set<String> names) { return DiscoverySelectors.selectClasspathResourceByName(names); }
                      Object resources(ClasspathResourceSelector selector) { return selector.getResources(); }
                  }
                  """));
    }

    @Test
    void reusesOfficialJUnit6ConstantsExecutionsAndPlatformLeavesWithTypeMetadata() {
        rewriteRun(java(
                """
                  import org.junit.jupiter.engine.Constants;
                  import org.junit.platform.commons.util.BlacklistedExceptions;
                  import org.junit.platform.commons.util.PreconditionViolationException;
                  import org.junit.platform.engine.discovery.MethodSelector;
                  import org.junit.platform.testkit.engine.Executions;
                  class PlatformSupport {
                      String constant = Constants.DEFAULT_DISPLAY_NAME_GENERATOR_PROPERTY_NAME;
                      Object finished(Executions executions) { return executions.started(); }
                      String names(MethodSelector selector) { return selector.getMethodParameterTypes(); }
                      RuntimeException error() { return new PreconditionViolationException("bad"); }
                      void rethrow(Throwable failure) { BlacklistedExceptions.rethrowIfBlacklisted(failure); }
                  }
                  """,
                """
                  import org.junit.jupiter.api.Constants;
                  import org.junit.platform.commons.PreconditionViolationException;
                  import org.junit.platform.commons.util.UnrecoverableExceptions;
                  import org.junit.platform.engine.discovery.MethodSelector;
                  import org.junit.platform.testkit.engine.Executions;

                  class PlatformSupport {
                      String constant = Constants.DEFAULT_DISPLAY_NAME_GENERATOR_PROPERTY_NAME;
                      Object finished(Executions executions) { return executions.finished(); }
                      String names(MethodSelector selector) { return selector.getParameterTypeNames(); }
                      RuntimeException error() { return new PreconditionViolationException("bad"); }
                      void rethrow(Throwable failure) { UnrecoverableExceptions.rethrowIfUnrecoverable(failure); }
                  }
                  """, source -> source.afterRecipe(after -> {
                    boolean[] found = {false};
                    new JavaIsoVisitor<boolean[]>() {
                        @Override
                        public J.Identifier visitIdentifier(J.Identifier identifier, boolean[] p) {
                            J.Identifier visited = super.visitIdentifier(identifier, p);
                            if ("UnrecoverableExceptions".equals(visited.getSimpleName()) &&
                                TypeUtils.isOfClassType(visited.getType(),
                                        "org.junit.platform.commons.util.UnrecoverableExceptions")) {
                                p[0] = true;
                            }
                            return visited;
                        }
                    }.visitNonNull(after, found);
                    assertTrue(found[0], "The changed utility must retain target type metadata");
                })));
    }

    @Test
    void safeOfficialLeavesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                  import org.junit.jupiter.api.MethodOrderer;
                  import org.junit.jupiter.api.TestMethodOrder;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  @TestMethodOrder(MethodOrderer.Alphanumeric.class)
                  class Extension {
                      Object value(ExtensionContext.Store store) {
                          return store.getOrComputeIfAbsent(String.class);
                      }
                  }
                  """,
                """
                  import org.junit.jupiter.api.MethodOrderer;
                  import org.junit.jupiter.api.TestMethodOrder;
                  import org.junit.jupiter.api.extension.ExtensionContext;
                  @TestMethodOrder(MethodOrderer.MethodName.class)
                  class Extension {
                      Object value(ExtensionContext.Store store) {
                          return store.computeIfAbsent(String.class);
                      }
                  }
                  """));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.junitjupiteraggregate")
                .build();
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        return Stream.concat(Stream.of(recipe), recipe.getRecipeList().stream()
                .flatMap(OfficialJUnitJupiter6LeavesTest::flatten));
    }
}
