package com.huawei.clouds.openrewrite.springwebmvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class SpringWebMvcServletOfficialMigrationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springwebmvc.MigrateDeterministicSpringWebMvc6Java";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcTo6_2_19";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(AUTO))
                .parser(JavaParser.fromJavaVersion()
                        .classpath("javax.servlet-api", "jakarta.servlet-api"))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesNamespaceAndSafeRemovedServletCallsTogether() {
        rewriteRun(java(
                """
                import javax.servlet.http.HttpServletRequest;
                import javax.servlet.http.HttpServletResponse;
                import javax.servlet.http.HttpSession;

                class LegacyServletCalls {
                    Object invoke(HttpServletRequest request, HttpServletResponse response,
                                  HttpSession session) {
                        boolean fromUrl = request.isRequestedSessionIdFromUrl();
                        String encoded = response.encodeUrl("/");
                        String redirect = response.encodeRedirectUrl("/login");
                        response.setStatus(202, "accepted");
                        session.putValue("key", encoded);
                        Object value = session.getValue("key");
                        session.removeValue("key");
                        return fromUrl ? value : redirect;
                    }
                }
                """,
                """
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import jakarta.servlet.http.HttpSession;

                class LegacyServletCalls {
                    Object invoke(HttpServletRequest request, HttpServletResponse response,
                                  HttpSession session) {
                        boolean fromUrl = request.isRequestedSessionIdFromURL();
                        String encoded = response.encodeURL("/");
                        String redirect = response.encodeRedirectURL("/login");
                        response.setStatus(202);
                        session.setAttribute("key", encoded);
                        Object value = session.getAttribute("key");
                        session.removeAttribute("key");
                        return fromUrl ? value : redirect;
                    }
                }
                """));
    }

    @Test
    void reusesOfficialServletContextAndUnavailableExceptionLeaves() {
        rewriteRun(java(
                """
                import javax.servlet.Servlet;
                import javax.servlet.ServletContext;
                import javax.servlet.ServletRequest;
                import javax.servlet.UnavailableException;

                class LegacyContext {
                    String path(ServletRequest request) {
                        return request.getRealPath("/");
                    }
                    void log(ServletContext context, Exception failure) {
                        context.log(failure, "failed");
                    }
                    UnavailableException unavailable(Servlet servlet) {
                        return new UnavailableException(30, servlet, "offline");
                    }
                }
                """,
                """
                import jakarta.servlet.Servlet;
                import jakarta.servlet.ServletContext;
                import jakarta.servlet.ServletRequest;
                import jakarta.servlet.UnavailableException;

                class LegacyContext {
                    String path(ServletRequest request) {
                        return request.getServletContext().getRealPath("/");
                    }
                    void log(ServletContext context, Exception failure) {
                        context.log("failed", failure);
                    }
                    UnavailableException unavailable(Servlet servlet) {
                        return new UnavailableException("offline", 30);
                    }
                }
                """));
    }

    @Test
    void officialCookieRecipeRemovesServlet6NoOpSetters() {
        rewriteRun(java(
                """
                import javax.servlet.http.Cookie;
                class CookiePolicy {
                    void legacy(Cookie cookie) {
                        cookie.setComment("legacy");
                        cookie.setVersion(1);
                    }
                }
                """,
                """
                import jakarta.servlet.http.Cookie;
                class CookiePolicy {
                    void legacy(Cookie cookie) {
                    }
                }
                """));
    }

    @Test
    void cookieGettersUsedAsValuesRemainAndReceivePreciseMarkers() {
        rewriteRun(spec -> spec.recipe(recipe(RECOMMENDED))
                        .parser(JavaParser.fromJavaVersion()
                                .classpath("javax.servlet-api", "jakarta.servlet-api"))
                        .typeValidationOptions(TypeValidation.none()),
                xml(pom("5.3.23"), pom("6.2.19"),
                        source -> source.path("pom.xml")),
                java("""
                    import javax.servlet.http.Cookie;
                    class CookiePolicy {
                        String comment(Cookie cookie) {
                            return cookie.getComment();
                        }
                        int version(Cookie cookie) {
                            return cookie.getVersion();
                        }
                    }
                    """, source -> source.after(actual -> {
                    assertTrue(actual.contains("import jakarta.servlet.http.Cookie;"), actual);
                    assertTrue(actual.contains("cookie.getComment()"), actual);
                    assertTrue(actual.contains("cookie.getVersion()"), actual);
                    assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.SERVLET_COOKIE), actual);
                    return actual;
                })));
    }

    @Test
    void unsafeGetValueNamesIsNeverRenamedByAutoRecipe() {
        rewriteRun(java(
                """
                import javax.servlet.http.HttpSession;
                class Sessions {
                    String[] names(HttpSession session) {
                        return session.getValueNames();
                    }
                }
                """,
                """
                import jakarta.servlet.http.HttpSession;
                class Sessions {
                    String[] names(HttpSession session) {
                        return session.getValueNames();
                    }
                }
                """));
    }

    @Test
    void recommendedRecipeLeavesUnsafeGetValueNamesWithPreciseMarker() {
        rewriteRun(spec -> spec.recipe(recipe(RECOMMENDED))
                        .parser(JavaParser.fromJavaVersion()
                                .classpath("javax.servlet-api", "jakarta.servlet-api"))
                        .typeValidationOptions(TypeValidation.none()),
                xml(pom("5.3.23"), pom("6.2.19"),
                        source -> source.path("pom.xml")),
                java("""
                    import javax.servlet.http.HttpSession;
                    class Sessions {
                        String[] names(HttpSession session) {
                            return session.getValueNames();
                        }
                    }
                    """, source -> source.after(actual -> {
                    assertTrue(actual.contains("import jakarta.servlet.http.HttpSession;"), actual);
                    assertTrue(actual.contains("session.getValueNames()"), actual);
                    assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.SERVLET_RETURN_TYPE), actual);
                    return actual;
                })));
    }

    @Test
    void removedServletTypeBlocksNamespaceFabricationAndGetsMarker() {
        rewriteRun(spec -> spec.recipe(recipe(RECOMMENDED))
                        .parser(JavaParser.fromJavaVersion()
                                .classpath("javax.servlet-api", "jakarta.servlet-api"))
                        .typeValidationOptions(TypeValidation.none()),
                xml(pom("5.3.23"), pom("6.2.19"),
                        source -> source.path("pom.xml")),
                java("""
                    import javax.servlet.SingleThreadModel;
                    class LegacyServlet implements SingleThreadModel { }
                    """, source -> source.after(actual -> {
                    assertTrue(actual.contains("import javax.servlet.SingleThreadModel;"), actual);
                    assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.SERVLET_REMOVED), actual);
                    return actual;
                })));
    }

    @Test
    void safeServletLeavesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                    import javax.servlet.http.HttpServletResponse;
                    class Redirects {
                        String redirect(HttpServletResponse response) {
                            return response.encodeRedirectUrl("/");
                        }
                    }
                    """, """
                    import jakarta.servlet.http.HttpServletResponse;
                    class Redirects {
                        String redirect(HttpServletResponse response) {
                            return response.encodeRedirectURL("/");
                        }
                    }
                    """));
    }

    private static org.openrewrite.Recipe recipe(String name) {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springwebmvc",
                                      "org.openrewrite.java.spring.framework",
                                      "org.openrewrite.java.migrate.jakarta")
                .build()
                .activateRecipes(name);
    }

    private static String pom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>web</artifactId>
                  <version>1</version>
                  <dependencies><dependency>
                    <groupId>org.springframework</groupId>
                    <artifactId>spring-webmvc</artifactId>
                    <version>%s</version>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
    }
}
