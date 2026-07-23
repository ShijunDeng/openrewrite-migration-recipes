package com.huawei.clouds.openrewrite.springsecurityweb;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SpringSecurityWebSourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringSecurityWeb6511SourceRisks())
                .parser(SpringSecurityWebTestSupport.parser());
    }

    @Test
    void marksLegacyFilterChainAuthorizationAuthenticationAndFilterOrder() {
        rewriteRun(java("""
                import org.springframework.context.annotation.Configuration;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
                import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

                @Configuration
                class SecurityConfiguration extends WebSecurityConfigurerAdapter {
                    @Override
                    protected void configure(HttpSecurity http) throws Exception {
                        http.authorizeRequests()
                                .antMatchers("/admin/**").hasRole("ADMIN")
                                .anyRequest().authenticated()
                            .and().formLogin()
                            .and().httpBasic()
                            .and().addFilterBefore(new AuditFilter(), UsernamePasswordAuthenticationFilter.class);
                    }
                }
                class AuditFilter extends UsernamePasswordAuthenticationFilter {
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.LEGACY), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.AUTHORIZATION), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.AUTHENTICATION), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.FILTER_ORDER), printed);
        })));
    }

    @Test
    void marksContextCsrfHeadersRememberMeLogoutAndProtocolDsl() {
        rewriteRun(java("""
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                class Boundaries {
                    void configure(HttpSecurity http) throws Exception {
                        http.securityContext().requireExplicitSave(true);
                        http.requestCache();
                        http.sessionManagement().maximumSessions(1);
                        http.csrf().ignoringAntMatchers("/hook");
                        http.headers().frameOptions();
                        http.cors();
                        http.rememberMe().tokenValiditySeconds(60);
                        http.logout().deleteCookies("JSESSIONID");
                        http.oauth2Login();
                        http.oauth2Client();
                        http.oauth2ResourceServer();
                        http.saml2Login();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.CONTEXT_SESSION), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.CSRF), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.HEADERS_CORS), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.REMEMBER_ME), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.LOGOUT), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.OAUTH_SAML), printed);
        })));
    }

    @Test
    void marksSecurityFilterChainMethodSecurityJakartaAndExtensionSpi() {
        rewriteRun(java("""
                import jakarta.servlet.Filter;
                import org.springframework.context.annotation.Bean;
                import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.web.SecurityFilterChain;

                @EnableGlobalMethodSecurity(prePostEnabled = true)
                class SecurityConfiguration {
                    @Bean
                    SecurityFilterChain chain(HttpSecurity http) throws Exception {
                        return http.build();
                    }
                }
                abstract class AuditFilter implements Filter {
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.FILTER_CHAIN), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.METHOD_SECURITY), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.JAKARTA), printed);
            assertTrue(printed.contains(FindSpringSecurityWeb6511SourceRisks.EXTENSION), printed);
        })));
    }

    @Test
    void typeAttributionPreventsSameNamedApplicationMethodsFromBeingMarked() {
        rewriteRun(java("""
                class ApplicationBuilder {
                    ApplicationBuilder csrf() { return this; }
                    ApplicationBuilder headers() { return this; }
                    ApplicationBuilder logout() { return this; }
                    ApplicationBuilder requestMatchers(String value) { return this; }
                    ApplicationBuilder oauth2Login() { return this; }
                }
                class Application {
                    void configure() {
                        new ApplicationBuilder().csrf().headers().logout()
                                .requestMatchers("/x").oauth2Login();
                    }
                }
                """, source -> source.afterRecipe(after ->
                assertFalse(after.printAll().contains("~~("), after.printAll()))));
    }

    @Test
    void generatedJavaIsIgnored() {
        rewriteRun(java("""
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                class GeneratedSecurity {
                    void configure(HttpSecurity http) throws Exception {
                        http.csrf();
                    }
                }
                """, source -> source.path("target/generated/GeneratedSecurity.java").afterRecipe(after ->
                assertFalse(after.printAll().contains("~~("), after.printAll()))));
    }

    @Test
    void markerInsertionIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java("""
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                class SecurityConfiguration {
                    void configure(HttpSecurity http) throws Exception {
                        http.csrf();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertEquals(1, occurrences(after.printAll(),
                        FindSpringSecurityWeb6511SourceRisks.CSRF), after.printAll()))));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
