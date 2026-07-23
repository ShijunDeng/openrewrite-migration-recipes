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
    void recommendedRecipeComposesProjectGateAndOfficialComponents() {
        Recipe recipe = environment().activateRecipes(RECIPE);
        assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                () -> recipe.validateAll().toString());
        List<String> direct = recipe.getRecipeList().stream().map(Recipe::getName).toList();
        assertEquals(List.of(
                "com.huawei.clouds.openrewrite.springsecurityweb.MarkSelectedSpringSecurityWebProjects",
                "com.huawei.clouds.openrewrite.springsecurityweb.MigrateSelectedDeterministicSpringSecurityWeb6",
                "com.huawei.clouds.openrewrite.springsecurityweb.FindSelectedSpringSecurityWeb6_5_11SourceRisks",
                "com.huawei.clouds.openrewrite.springsecurityweb.FindSelectedSpringSecurityWeb6_5_11ConfigurationRisks",
                "com.huawei.clouds.openrewrite.springsecurityweb.UpgradeSelectedSpringSecurityWebBuildToJava17",
                "com.huawei.clouds.openrewrite.springsecurityweb.UpgradeSpringSecurityWebTo6_5_11",
                "com.huawei.clouds.openrewrite.springsecurityweb.FindSpringSecurityWeb6_5_11BuildRisks"
        ), direct);
        List<String> tree = treeNames(recipe);
        assertTrue(tree.contains("org.openrewrite.java.migrate.UpgradeJavaVersion"), tree.toString());
        assertTrue(tree.contains("org.openrewrite.java.spring.security5.WebSecurityConfigurerAdapter"), tree.toString());
        assertTrue(tree.contains("org.openrewrite.java.spring.security5.AuthorizeHttpRequests"), tree.toString());
        assertTrue(tree.contains("org.openrewrite.java.spring.security6.UseSha256InRememberMe"), tree.toString());
        assertTrue(tree.contains(
                "org.openrewrite.java.spring.security5.search.FindEncryptorsQueryableTextUses"), tree.toString());
        assertFalse(tree.contains("org.openrewrite.java.dependencies.UpgradeDependencyVersion"), tree.toString());
        assertFalse(tree.stream().anyMatch(name -> name.contains("UpgradeSpringSecurity_")), tree.toString());
        assertFalse(tree.contains("org.openrewrite.java.migrate.UpgradeToJava17"), tree.toString());
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
                    assertTrue(printed.contains("<maven.compiler.release>17</maven.compiler.release>"), printed);
                    assertFalse(printed.contains(FindSpringSecurityWeb6511BuildRisks.JAVA_BASELINE), printed);
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
                })),
                java("""
                        import javax.servlet.Filter;
                        import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
                        @EnableGlobalMethodSecurity(prePostEnabled = true)
                        abstract class SevenXProjectSource implements Filter {
                        }
                        """),
                properties("spring.security.csrf.enabled=false\n",
                        source -> source.path("application.properties")));
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(UpgradeSpringSecurityWebDependencyTest.pom("6.5.10"),
                        source -> source.path("pom.xml").after(actual -> actual)),
                properties("spring.security.csrf.enabled=false\n",
                        source -> source.path("application.properties").after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(),
                                        FindSpringSecurityWeb6511ConfigurationRisks.CSRF)))));
    }

    @Test
    void unrelatedProjectIsCompletelyNoop() {
        rewriteRun(
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>unrelated</artifactId><version>1</version>
                          <properties><maven.compiler.release>11</maven.compiler.release></properties>
                        </project>
                        """),
                java("""
                        import javax.servlet.Filter;
                        import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
                        @EnableGlobalMethodSecurity(prePostEnabled = true)
                        abstract class UnrelatedSecurity implements Filter {
                        }
                        """),
                properties("spring.security.csrf.enabled=false\n",
                        source -> source.path("application.properties")));
    }

    @Test
    void sixTwoSourceDoesNotReplayFiveXOrJakartaMigration() {
        rewriteRun(
                pomXml(UpgradeSpringSecurityWebDependencyTest.pom("6.2.8"),
                        source -> source.path("pom.xml").after(actual -> actual)),
                java("""
                        import javax.servlet.Filter;
                        import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
                        @EnableGlobalMethodSecurity(prePostEnabled = true)
                        abstract class LegacyCodeInSixTwoBuild implements Filter {
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("import javax.servlet.Filter;"), printed);
                    assertFalse(printed.contains("import jakarta.servlet.Filter;"), printed);
                    assertTrue(printed.contains(
                            "import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;"),
                            printed);
                    assertFalse(printed.contains(
                            "import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;"),
                            printed);
                })));
    }

    @Test
    void officialJavaBaselineRecipeNeverDowngradesJava21() {
        rewriteRun(pomXml(UpgradeSpringSecurityWebDependencyTest.project("""
                        <properties><maven.compiler.release>21</maven.compiler.release></properties>
                        <dependencies>%s</dependencies>
                        """.formatted(UpgradeSpringSecurityWebDependencyTest.dep("6.5.10"))),
                source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(
                            "<maven.compiler.release>21</maven.compiler.release>"), printed);
                    assertTrue(printed.contains("<version>6.5.11</version>"), printed);
                    assertFalse(printed.contains(
                            "<maven.compiler.release>17</maven.compiler.release>"), printed);
                })));
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
