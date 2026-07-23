package com.huawei.clouds.openrewrite.springsecurityweb;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

class SpringSecurityWebRecommendedRecipeTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springsecurityweb.MigrateSpringSecurityWebTo6_5_11";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE))
                .parser(SpringSecurityWebTestSupport.parser());
    }

    @Test
    void recommendedRecipeComposesAllFivePublicCapabilitiesAndOfficialComponents() {
        Recipe recipe = environment().activateRecipes(RECIPE);
        List<String> direct = recipe.getRecipeList().stream().map(Recipe::getName).toList();
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.springsecurityweb.UpgradeSpringSecurityWebTo6_5_11",
                "com.huawei.clouds.openrewrite.springsecurityweb.MigrateDeterministicSpringSecurityWeb6",
                "com.huawei.clouds.openrewrite.springsecurityweb.FindSpringSecurityWeb6_5_11BuildRisks",
                "com.huawei.clouds.openrewrite.springsecurityweb.FindSpringSecurityWeb6_5_11SourceRisks",
                "com.huawei.clouds.openrewrite.springsecurityweb.FindSpringSecurityWeb6_5_11ConfigurationRisks"
        ), direct);
        List<String> tree = treeNames(recipe);
        assertTrue(tree.contains("org.openrewrite.java.spring.security5.WebSecurityConfigurerAdapter"), tree.toString());
        assertTrue(tree.contains("org.openrewrite.java.spring.security5.AuthorizeHttpRequests"), tree.toString());
        assertTrue(tree.contains("org.openrewrite.java.spring.security6.UseSha256InRememberMe"), tree.toString());
        assertTrue(tree.contains(
                "org.openrewrite.java.spring.security5.search.FindEncryptorsQueryableTextUses"), tree.toString());
        assertFalse(tree.contains("org.openrewrite.java.dependencies.UpgradeDependencyVersion"), tree.toString());
        assertFalse(tree.stream().anyMatch(name -> name.contains("UpgradeSpringSecurity_")), tree.toString());
    }

    @Test
    void recommendedRecipePerformsDependencyJakartaApiAndConfigurationWorkTogether() {
        rewriteRun(
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>security</artifactId><version>1</version>
                          <properties>
                            <maven.compiler.release>11</maven.compiler.release>
                            <maven.compiler.parameters>false</maven.compiler.parameters>
                          </properties>
                          <dependencies>
                            <dependency>
                              <groupId>org.springframework.security</groupId>
                              <artifactId>spring-security-web</artifactId>
                              <version>5.8.16</version>
                            </dependency>
                            <dependency>
                              <groupId>javax.servlet</groupId>
                              <artifactId>javax.servlet-api</artifactId>
                              <version>4.0.1</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>6.5.11</version>"), printed);
                    assertTrue(printed.contains("<groupId>jakarta.servlet</groupId>"), printed);
                    assertTrue(printed.contains("<artifactId>jakarta.servlet-api</artifactId>"), printed);
                    assertTrue(printed.contains("<version>6.0.0</version>"), printed);
                    assertTrue(printed.contains(FindSpringSecurityWeb6511BuildRisks.JAVA_BASELINE), printed);
                    assertTrue(printed.contains(FindSpringSecurityWeb6511BuildRisks.PARAMETERS), printed);
                })),
                java("""
                        import javax.servlet.Filter;
                        import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
                        @EnableGlobalMethodSecurity(prePostEnabled = true)
                        abstract class SecurityConfiguration implements Filter {
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("import jakarta.servlet.Filter;"), printed);
                    assertTrue(printed.contains("EnableMethodSecurity"), printed);
                    assertFalse(printed.contains("EnableGlobalMethodSecurity"), printed);
                    assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.METHOD_SECURITY), printed);
                    assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.JAKARTA), printed);
                })),
                properties("""
                        spring.security.filter.dispatcher-types=REQUEST,ASYNC,ERROR
                        spring.security.csrf.enabled=false
                        """, source -> source.path("application.properties")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains(
                                    FindSpringSecurityWeb6511ConfigurationRisks.FILTER_CHAIN), after.printAll());
                            assertTrue(after.printAll().contains(
                                    FindSpringSecurityWeb6511ConfigurationRisks.CSRF), after.printAll());
                        })));
    }

    @Test
    void recommendedRecipeNeverDowngradesWorkbookSevenXAndMarksExactConflict() {
        rewriteRun(pomXml(UpgradeSpringSecurityWebDependencyTest.pom("7.0.4"),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>7.0.4</version>"), printed);
                    assertFalse(printed.contains("<version>6.5.11</version>"), printed);
                    assertEquals(1, occurrences(printed, SpringSecurityWebUpgradeSupport.TARGET_CONFLICT), printed);
                })));
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties("spring.security.csrf.enabled=false\n",
                        source -> source.path("application.properties").after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(),
                                        FindSpringSecurityWeb6511ConfigurationRisks.CSRF)))));
    }

    private static List<String> treeNames(Recipe root) {
        List<String> names = new ArrayList<>();
        collect(root, names);
        return names;
    }

    private static void collect(Recipe recipe, List<String> names) {
        for (Recipe child : recipe.getRecipeList()) {
            names.add(child.getName());
            collect(child, names);
        }
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springsecurityweb",
                                      "org.openrewrite.java.spring")
                .build();
    }
}
