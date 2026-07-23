package com.huawei.clouds.openrewrite.springweb;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class SpringWebTargetClasspathTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springweb.MigrateDeterministicSpringWeb6Java";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springweb",
                                              "org.openrewrite.java.spring.framework")
                        .build()
                        .activateRecipes(AUTO))
                .parser(targetParser())
                .typeValidationOptions(TypeValidation.all());
    }

    @Test
    void migratedApisAreAttributedAndStableOnSpring6219() {
        rewriteRun(java(
                """
                import org.springframework.context.MessageSource;
                import org.springframework.http.HttpRequest;
                import org.springframework.http.client.ClientHttpResponse;
                import org.springframework.http.client.observation.ClientHttpObservationDocumentation.LowCardinalityKeyNames;
                import org.springframework.validation.ObjectError;
                import org.springframework.web.bind.MethodArgumentNotValidException;
                import org.springframework.web.util.BindErrorUtils;
                import org.springframework.web.util.ForwardedHeaderUtils;
                import org.springframework.web.util.UriComponentsBuilder;

                import java.util.Locale;
                import java.util.Map;

                class TargetApis {
                    Object key() {
                        return LowCardinalityKeyNames.CLIENT_NAME;
                    }
                    int status(ClientHttpResponse response) {
                        return response.getStatusCode().value();
                    }
                    Map<ObjectError, String> errors(MethodArgumentNotValidException exception,
                                                    MessageSource messages, Locale locale) {
                        return BindErrorUtils.resolve(exception.getAllErrors(), messages, locale);
                    }
                    UriComponentsBuilder forwarded(HttpRequest request) {
                        return ForwardedHeaderUtils.adaptFromForwardedHeaders(
                                request.getURI(), request.getHeaders());
                    }
                }
                """));
    }

    @Test
    void nearbyTargetApisAreNotOvermatched() {
        rewriteRun(java(
                """
                import org.springframework.http.client.observation.ClientHttpObservationDocumentation.HighCardinalityKeyNames;
                import org.springframework.web.util.UriComponentsBuilder;

                class TargetNegatives {
                    Object httpUrl() {
                        return HighCardinalityKeyNames.HTTP_URL;
                    }
                    UriComponentsBuilder uri(String value) {
                        return UriComponentsBuilder.fromUriString(value);
                    }
                }
                """));
    }

    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().classpath(
                "spring-web", "spring-context", "spring-core", "spring-beans", "spring-jcl",
                "micrometer-observation", "micrometer-commons");
    }
}
