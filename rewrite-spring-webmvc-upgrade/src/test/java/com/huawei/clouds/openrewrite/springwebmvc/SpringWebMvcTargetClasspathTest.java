package com.huawei.clouds.openrewrite.springwebmvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class SpringWebMvcTargetClasspathTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springwebmvc.MigrateDeterministicSpringWebMvc6Java";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springwebmvc",
                                              "org.openrewrite.java.spring.framework",
                                              "org.openrewrite.java.migrate.jakarta")
                        .build()
                        .activateRecipes(AUTO))
                .parser(JavaParser.fromJavaVersion().classpath(
                        "spring-webmvc", "spring-web", "spring-context", "spring-aop",
                        "spring-beans", "spring-core", "spring-expression", "spring-jcl",
                        "jakarta.servlet-api", "micrometer-observation", "micrometer-commons"))
                .typeValidationOptions(TypeValidation.all());
    }

    @Test
    void migratedMvcAndServletApisAreAttributedOnSpring6219() {
        rewriteRun(java(
                """
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import jakarta.servlet.http.HttpSession;
                import org.springframework.http.HttpStatusCode;
                import org.springframework.http.MediaType;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.servlet.AsyncHandlerInterceptor;
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                class TargetApis implements WebMvcConfigurer, AsyncHandlerInterceptor {
                    HttpStatusCode status(ResponseEntity<String> response) {
                        return response.getStatusCode();
                    }
                    Object servlet(HttpServletRequest request,
                                   HttpServletResponse response,
                                   HttpSession session) {
                        response.setStatus(204);
                        session.setAttribute("type", MediaType.APPLICATION_JSON);
                        return session.getAttribute("type");
                    }
                }
                """));
    }

    @Test
    void targetPathPatternAndExceptionApisRemainUnchanged() {
        rewriteRun(java(
                """
                import org.springframework.http.HttpStatusCode;
                import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
                import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

                class TargetConfig {
                    void configure(PathMatchConfigurer paths) {
                        paths.setPatternParser(null);
                    }
                }
                abstract class TargetErrors extends ResponseEntityExceptionHandler {
                    HttpStatusCode status;
                }
                """));
    }
}
