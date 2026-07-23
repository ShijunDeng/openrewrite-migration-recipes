package com.huawei.clouds.openrewrite.springsecuritycore;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SpringSecurityCoreSourceRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springsecuritycore.FindSpringSecurityCore6_5_11SourceRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(SpringSecurityCoreTestSupport.environment().activateRecipes(RECIPE))
                .parser(SpringSecurityCoreTestSupport.parser());
    }

    @Test
    void marksPasswordAndRemovedQueryableEncryptionAtExactCalls() {
        rewriteRun(java("""
                import org.springframework.security.crypto.encrypt.Encryptors;
                import org.springframework.security.crypto.password.PasswordEncoder;
                class PasswordBoundary {
                    String encode(PasswordEncoder encoder, String raw) {
                        Encryptors.queryableText("password", "salt");
                        return encoder.encode(raw);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringSecurityCore6511SourceRisks.PASSWORD), printed);
            assertTrue(printed.contains("/*~~>*/Encryptors.queryableText"), printed);
        })));
    }

    @Test
    void marksAuthenticationProviderAndUserDetailsExtensions() {
        rewriteRun(java("""
                import org.springframework.security.authentication.AuthenticationProvider;
                import org.springframework.security.core.Authentication;
                import org.springframework.security.core.AuthenticationException;
                class CustomProvider implements AuthenticationProvider {
                    public Authentication authenticate(Authentication value) throws AuthenticationException {
                        return value;
                    }
                    public boolean supports(Class<?> type) {
                        return true;
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(
                        FindSpringSecurityCore6511SourceRisks.AUTHENTICATION), after.printAll()))));
    }

    @Test
    void marksMethodAuthorizationAndContextPropagationBoundaries() {
        rewriteRun(java("""
                import org.springframework.security.access.prepost.PreAuthorize;
                import org.springframework.security.core.context.SecurityContextHolder;
                class SecuredService {
                    @PreAuthorize("#owner == authentication.name")
                    String read(String owner) {
                        return SecurityContextHolder.getContext().getAuthentication().getName();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringSecurityCore6511SourceRisks.AUTHORIZATION), printed);
            assertTrue(printed.contains(FindSpringSecurityCore6511SourceRisks.CONTEXT), printed);
        })));
    }

    @Test
    void ignoresBusinessNamesStringsAndGeneratedSources() {
        rewriteRun(
                java("""
                        class AuthenticationProvider {
                            String note = "SecurityContextHolder Pbkdf2PasswordEncoder";
                            String authenticate(String value) {
                                return value;
                            }
                        }
                        """, source -> source.afterRecipe(after -> {
                    String printed = after.printAll();
                    assertFalse(printed.contains(FindSpringSecurityCore6511SourceRisks.AUTHENTICATION), printed);
                    assertFalse(printed.contains(FindSpringSecurityCore6511SourceRisks.CONTEXT), printed);
                    assertFalse(printed.contains(FindSpringSecurityCore6511SourceRisks.PASSWORD), printed);
                })),
                java("""
                        import org.springframework.security.access.prepost.PreAuthorize;
                        class GeneratedService {
                            @PreAuthorize("authenticated")
                            void run() {
                            }
                        }
                        """, source -> source.path("target/generated/GeneratedService.java")));
    }

    @Test
    void marksLegacyAccessDecisionExtensionAndStabilizesInTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import java.util.Collection;
                        import org.springframework.security.access.AccessDecisionVoter;
                        import org.springframework.security.access.ConfigAttribute;
                        import org.springframework.security.core.Authentication;
                        class LegacyVoter implements AccessDecisionVoter<Object> {
                            public boolean supports(ConfigAttribute attribute) { return true; }
                            public boolean supports(Class<?> type) { return true; }
                            public int vote(Authentication authentication, Object object,
                                    Collection<ConfigAttribute> attributes) { return ACCESS_ABSTAIN; }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(
                                FindSpringSecurityCore6511SourceRisks.LEGACY), after.printAll()))));
    }
}
