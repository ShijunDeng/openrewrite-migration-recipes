package com.huawei.clouds.openrewrite.springsecurityweb;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class SpringSecurityOfficialMigrationTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springsecurityweb.MigrateDeterministicSpringSecurityWeb6";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE))
                .parser(SpringSecurityWebTestSupport.parser());
    }

    @Test
    void reusesApplicableOfficialComponentsWithoutBroadVersionAggregates() {
        List<String> names = treeNames(environment().activateRecipes(RECIPE));
        assertTrue(names.contains("org.openrewrite.java.dependencies.ChangeDependency"), names.toString());
        assertTrue(names.contains("org.openrewrite.java.ChangePackage"), names.toString());
        assertTrue(names.contains("org.openrewrite.java.spring.security5.WebSecurityConfigurerAdapter"),
                names.toString());
        assertTrue(names.contains("org.openrewrite.java.spring.boot2.HttpSecurityLambdaDsl"), names.toString());
        assertTrue(names.contains("org.openrewrite.java.spring.security5.AuthorizeHttpRequests"), names.toString());
        assertTrue(names.contains("org.openrewrite.java.spring.security5.UseNewRequestMatchers"), names.toString());
        assertTrue(names.contains(
                "org.openrewrite.java.spring.security5.ReplaceGlobalMethodSecurityWithMethodSecurity"),
                names.toString());
        assertTrue(names.contains(
                "org.openrewrite.java.spring.security5.ReplaceGlobalMethodSecurityWithMethodSecurityXml"),
                names.toString());
        assertTrue(names.contains("org.openrewrite.java.spring.security6.UseSha256InRememberMe"), names.toString());
        assertTrue(names.contains(
                "org.openrewrite.java.spring.security6.RequireExplicitSavingOfSecurityContextRepository"),
                names.toString());
        assertTrue(names.contains("org.openrewrite.java.spring.security6.UpdateRequestCache"), names.toString());
        assertTrue(names.contains(
                "org.openrewrite.java.spring.security6.oauth2.client.OAuth2LoginLambdaDsl"), names.toString());
        assertTrue(names.contains("org.openrewrite.java.spring.security6.ApplyToWithLambdaDsl"), names.toString());
        assertFalse(names.contains("org.openrewrite.java.dependencies.UpgradeDependencyVersion"), names.toString());
        assertFalse(names.stream().anyMatch(name -> name.matches(
                "org\\.openrewrite\\.java\\.spring\\.security[56]\\.UpgradeSpringSecurity_.*")),
                names.toString());
        assertFalse(names.contains("org.openrewrite.java.migrate.jakarta.JavaxServletToJakartaServlet"),
                names.toString());
    }

    @Test
    void migratesJavaxServletDependencyAndSourceToJakartaServletSix() {
        rewriteRun(
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>security</artifactId><version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>javax.servlet</groupId>
                              <artifactId>javax.servlet-api</artifactId>
                              <version>4.0.1</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """, """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>security</artifactId><version>1</version>
                          <dependencies>
                            <dependency>
                              <groupId>jakarta.servlet</groupId>
                              <artifactId>jakarta.servlet-api</artifactId>
                              <version>6.0.0</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("pom.xml")),
                java("""
                        import javax.servlet.Filter;
                        abstract class AuditFilter implements Filter {
                        }
                        """, """
                        import jakarta.servlet.Filter;
                        abstract class AuditFilter implements Filter {
                        }
                        """));
    }

    @Test
    void migratesWebSecurityConfigurerAdapterAndLegacyAuthorizationDsl() {
        rewriteRun(java("""
                import org.springframework.context.annotation.Configuration;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

                @Configuration
                class SecurityConfiguration extends WebSecurityConfigurerAdapter {
                    @Override
                    protected void configure(HttpSecurity http) throws Exception {
                        http.authorizeRequests()
                                .antMatchers("/", "/public/**").permitAll()
                                .anyRequest().authenticated()
                            .and().formLogin()
                            .and().httpBasic();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertFalse(printed.contains("WebSecurityConfigurerAdapter"), printed);
            assertFalse(printed.contains("authorizeRequests"), printed);
            assertFalse(printed.contains("antMatchers"), printed);
            assertTrue(printed.contains("SecurityFilterChain"), printed);
            assertTrue(printed.contains("@Bean"), printed);
            assertTrue(printed.contains("authorizeHttpRequests"), printed);
            assertTrue(printed.contains("requestMatchers"), printed);
            assertTrue(printed.contains("http.build()"), printed);
        })));
    }

    @Test
    void migratesMethodSecurityJavaAndXmlUsingOfficialRecipes() {
        rewriteRun(
                java("""
                        import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
                        @EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
                        class MethodSecurityConfiguration {
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertFalse(printed.contains("EnableGlobalMethodSecurity"), printed);
                    assertTrue(printed.contains("EnableMethodSecurity"), printed);
                    assertFalse(printed.contains("prePostEnabled = true"), printed);
                    assertTrue(printed.contains("securedEnabled = true"), printed);
                })),
                xml("""
                        <beans xmlns:security="http://www.springframework.org/schema/security">
                          <security:global-method-security pre-post-enabled="true" secured-enabled="true"/>
                          <security:websocket-message-broker use-authorization-manager="true"/>
                        </beans>
                        """, """
                        <beans xmlns:security="http://www.springframework.org/schema/security">
                          <security:method-security secured-enabled="true"/>
                          <security:websocket-message-broker/>
                        </beans>
                        """, source -> source.path("security-context.xml")));
    }

    @Test
    void removesExplicitDefaultSha256RememberMeConfiguration() {
        rewriteRun(java("""
                import org.springframework.security.core.userdetails.UserDetailsService;
                import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
                import static org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm.SHA256;

                class RememberMeConfiguration {
                    TokenBasedRememberMeServices service(UserDetailsService users) {
                        TokenBasedRememberMeServices service =
                                new TokenBasedRememberMeServices("key", users, SHA256);
                        service.setMatchingAlgorithm(SHA256);
                        return service;
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("new TokenBasedRememberMeServices(\"key\", users)"), printed);
            assertFalse(printed.contains("setMatchingAlgorithm"), printed);
        })));
    }

    @Test
    void officialCompositionIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java("""
                import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
                @EnableGlobalMethodSecurity(prePostEnabled = true)
                class SecurityConfiguration {
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains("EnableMethodSecurity"), after.printAll()))));
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

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springsecurityweb",
                                      "org.openrewrite.java.spring")
                .build();
    }
}
