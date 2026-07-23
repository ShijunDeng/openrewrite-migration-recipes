package com.huawei.clouds.openrewrite.springsecuritycore;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class SpringSecurityCoreOfficialMigrationTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springsecuritycore.MigrateDeterministicSpringSecurityCore6";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(SpringSecurityCoreTestSupport.environment().activateRecipes(RECIPE))
                .parser(SpringSecurityCoreTestSupport.parser());
    }

    @Test
    void reusesOfficialPbkdf2DefaultMigration() {
        rewriteRun(java("""
                import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
                class Passwords {
                    Pbkdf2PasswordEncoder encoder() {
                        return new Pbkdf2PasswordEncoder();
                    }
                }
                """, """
                import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
                class Passwords {
                    Pbkdf2PasswordEncoder encoder() {
                        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
                    }
                }
                """));
    }

    @Test
    void reusesOfficialSCryptAndArgon2DefaultMigrations() {
        rewriteRun(
                java("""
                        import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
                        class SCryptPasswords {
                            SCryptPasswordEncoder encoder() {
                                return new SCryptPasswordEncoder();
                            }
                        }
                        """, """
                        import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
                        class SCryptPasswords {
                            SCryptPasswordEncoder encoder() {
                                return SCryptPasswordEncoder.defaultsForSpringSecurity_v4_1();
                            }
                        }
                        """),
                java("""
                        import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
                        class ArgonPasswords {
                            Argon2PasswordEncoder encoder() {
                                return new Argon2PasswordEncoder();
                            }
                        }
                        """, """
                        import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
                        class ArgonPasswords {
                            Argon2PasswordEncoder encoder() {
                                return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_2();
                            }
                        }
                        """));
    }

    @Test
    void reusesOfficialMethodSecurityJavaLeaf() {
        rewriteRun(java("""
                import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
                @EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
                class MethodSecurityConfiguration {
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("EnableMethodSecurity"), printed);
            assertFalse(printed.contains("EnableGlobalMethodSecurity"), printed);
            assertFalse(printed.contains("prePostEnabled = true"), printed);
            assertTrue(printed.contains("securedEnabled = true"), printed);
        })));
    }

    @Test
    void reusesOfficialXmlRecipeForVerifiedDefaultSecurityNamespace() {
        rewriteRun(xml("""
                <global-method-security
                    xmlns="http://www.springframework.org/schema/security"
                    pre-post-enabled="true"
                    secured-enabled="true"/>
                """, """
                <method-security
                    xmlns="http://www.springframework.org/schema/security"
                    secured-enabled="true"/>
                """, source -> source.path("method-security.xml")));
    }

    @Test
    void prefixedGapSupportsAnyAliasButNeverAForeignNamespace() {
        rewriteRun(
                xml("""
                        <beans xmlns="http://www.springframework.org/schema/beans"
                               xmlns:ss="http://www.springframework.org/schema/security">
                          <ss:global-method-security pre-post-enabled="true"/>
                        </beans>
                        """, """
                        <beans xmlns="http://www.springframework.org/schema/beans"
                               xmlns:ss="http://www.springframework.org/schema/security">
                          <ss:method-security/>
                        </beans>
                        """, source -> source.path("safe.xml")),
                xml("""
                        <foreign:global-method-security
                            xmlns:foreign="https://example.org/not-spring-security"
                            pre-post-enabled="true"/>
                        """, source -> source.path("foreign.xml")),
                xml("""
                        <global-method-security
                            xmlns="https://example.org/not-spring-security"
                            pre-post-enabled="true"/>
                        """, source -> source.path("foreign-default.xml")));
    }

    @Test
    void officialXmlRecipeIsSkippedWhenItsUnprefixedMatchSetMixesNamespaces() {
        rewriteRun(xml("""
                <root xmlns="http://www.springframework.org/schema/security">
                  <global-method-security pre-post-enabled="true"/>
                  <foreign xmlns="https://example.org/foreign">
                    <method-security pre-post-enabled="true"/>
                  </foreign>
                </root>
                """, source -> source.path("mixed.xml")));
    }

    @Test
    void reusesOfficialReactiveMethodSecurityDefaultMigration() {
        rewriteRun(java("""
                import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
                @EnableReactiveMethodSecurity(useAuthorizationManager = true)
                class ReactiveMethodSecurityConfiguration {
                }
                """, """
                import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
                @EnableReactiveMethodSecurity
                class ReactiveMethodSecurityConfiguration {
                }
                """));
    }

    @Test
    void officialLeavesNeverTouchGeneratedSources() {
        rewriteRun(
                java("""
                        import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
                        class GeneratedPasswords {
                            Pbkdf2PasswordEncoder encoder() {
                                return new Pbkdf2PasswordEncoder();
                            }
                        }
                        """, source -> source.path("target/generated-sources/GeneratedPasswords.java")),
                xml("""
                        <beans>
                          <global-method-security pre-post-enabled="true"/>
                        </beans>
                        """, source -> source.path("build/generated/security-context.xml")));
    }

    @Test
    void officialCompositionIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
                        class Passwords {
                            Pbkdf2PasswordEncoder encoder() {
                                return new Pbkdf2PasswordEncoder();
                            }
                        }
                        """, source -> source.after(actual -> actual)));
    }
}
