package com.huawei.clouds.openrewrite.springwebmvc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.framework.MigrateHandlerInterceptor;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringWebMvcSourceMigrationTest implements RewriteTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springwebmvc.MigrateDeterministicSpringWebMvc6Java";
    private static final String RISKS =
            "com.huawei.clouds.openrewrite.springwebmvc.FindSpringWebMvc6SourceAndConfigurationRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(AUTO)).parser(parser()).typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesTypeAttributedServletHierarchyRecursively() {
        rewriteRun(java(
                """
                import javax.servlet.Filter;
                import javax.servlet.http.HttpServletRequest;
                import javax.servlet.http.HttpServletResponse;
                class WebFilter { Filter filter; HttpServletRequest request; HttpServletResponse response; }
                """,
                """
                import jakarta.servlet.Filter;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                class WebFilter { Filter filter; HttpServletRequest request; HttpServletResponse response; }
                """));
    }

    @Test
    void replacesDirectWebMvcConfigurerAdapterWhenNoSuperBehaviorIsUsed() {
        rewriteRun(java(
                """
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
                class WebConfig extends WebMvcConfigurerAdapter { }
                """,
                """
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                class WebConfig implements WebMvcConfigurer { }
                """));
    }

    @Test
    void adapterMigrationMaintainsAttributedClassHierarchy() {
        rewriteRun(spec -> spec.recipe(recipe(AUTO)).parser(parser()).typeValidationOptions(TypeValidation.none()),
                java(
                        "import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter; class Config extends WebMvcConfigurerAdapter {}",
                        "import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;\n\nclass Config implements WebMvcConfigurer {}"));
    }

    @Test
    void replacesDirectHandlerInterceptorAdapterAndPreservesOtherInterfaces() {
        rewriteRun(java(
                """
                import java.io.Serializable;
                import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
                class AuditInterceptor extends HandlerInterceptorAdapter implements Serializable { }
                """,
                """
                import org.springframework.web.servlet.AsyncHandlerInterceptor;

                import java.io.Serializable;

                class AuditInterceptor implements Serializable, AsyncHandlerInterceptor { }
                """, source -> source.afterRecipe(after -> {
                    JavaType.Class type = (JavaType.Class) after.getClasses().get(0).getType();
                    assertTrue(type.getInterfaces().stream().anyMatch(candidate -> TypeUtils.isOfClassType(
                            candidate, "org.springframework.web.servlet.AsyncHandlerInterceptor")));
                })));
    }

    @Test
    void pinnedOfficialHandlerRecipeDemonstratesThePreservedContractGap() {
        rewriteRun(spec -> spec.recipe(new MigrateHandlerInterceptor())
                        .parser(parser()).typeValidationOptions(TypeValidation.none()),
                java("""
                    import java.io.Serializable;
                    import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
                    class AuditInterceptor extends HandlerInterceptorAdapter implements Serializable { }
                    """, source -> source.after(actual -> {
                    assertTrue(actual.contains("implements HandlerInterceptor"), actual);
                    assertFalse(actual.contains("implements Serializable"), actual);
                    assertFalse(actual.contains("AsyncHandlerInterceptor"), actual);
                    return actual;
                })));
    }

    @Test
    void localHandlerGapPreservesAsyncCallbackAndExistingInterface() {
        rewriteRun(java(
                """
                  import java.io.Serializable;
                  import javax.servlet.http.HttpServletRequest;
                  import javax.servlet.http.HttpServletResponse;
                  import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
                  class StreamingInterceptor extends HandlerInterceptorAdapter implements Serializable {
                      @Override
                      public void afterConcurrentHandlingStarted(
                              HttpServletRequest request, HttpServletResponse response) {
                      }
                  }
                  """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String migrated = after.printAll();
                    assertTrue(migrated.contains("implements Serializable, AsyncHandlerInterceptor"), migrated);
                    assertTrue(migrated.contains("import jakarta.servlet.http.HttpServletRequest;"), migrated);
                    assertTrue(migrated.contains("import jakarta.servlet.http.HttpServletResponse;"), migrated);
                })));
    }

    @Test
    void migratesReducedMyBlogWebMvcFixture() {
        // zhyocean/MyBlog@9410e07, src/main/java/com/zhy/config/WebMvcConfig.java.
        rewriteRun(java(
                """
                package com.zhy.config;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
                @Configuration
                public class WebMvcConfig extends WebMvcConfigurerAdapter {
                    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
                        registry.addResourceHandler("/article/**").addResourceLocations("classpath:/static/");
                    }
                }
                """,
                """
                package com.zhy.config;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                @Configuration
                public class WebMvcConfig implements WebMvcConfigurer {
                    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
                        registry.addResourceHandler("/article/**").addResourceLocations("classpath:/static/");
                    }
                }
                """));
    }

    @Test
    void preservesReducedToughProxyFixtureWithSuperBehaviorButMigratesServletTypes() {
        // talkincode/ToughProxy@c40aaac, SessionInterceptor.java: super callbacks require manual handling.
        rewriteRun(java(
                """
                import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
                import javax.servlet.http.HttpServletRequest;
                import javax.servlet.http.HttpServletResponse;
                class SessionInterceptor extends HandlerInterceptorAdapter {
                    void postHandle(HttpServletRequest request, HttpServletResponse response) {
                        super.postHandle(request, response);
                    }
                }
                """,
                """
                import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                class SessionInterceptor extends HandlerInterceptorAdapter {
                    void postHandle(HttpServletRequest request, HttpServletResponse response) {
                        super.postHandle(request, response);
                    }
                }
                """));
    }

    @Test
    void migratesReducedSmallSsmAuthenticationInterceptorFixture() {
        // xenv/S-mall-ssm@3d9e77f, AuthInterceptor.java.
        rewriteRun(java(
                """
                package tmall.interceptor;
                import javax.servlet.http.HttpServletRequest;
                import javax.servlet.http.HttpServletResponse;
                import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
                public class AuthInterceptor extends HandlerInterceptorAdapter {
                    @Override
                    public boolean preHandle(HttpServletRequest request,
                                             HttpServletResponse response,
                                             Object handler) throws Exception {
                        Object user = request.getSession().getAttribute("user");
                        if (user == null) {
                            response.sendRedirect("/login?refer=/");
                            return false;
                        }
                        return true;
                    }
                }
                """, source -> source.after(actual -> {
                    assertTrue(actual.contains("implements AsyncHandlerInterceptor"), actual);
                    assertTrue(actual.contains("import jakarta.servlet.http.HttpServletRequest;"), actual);
                    assertTrue(actual.contains("import jakarta.servlet.http.HttpServletResponse;"), actual);
                    assertTrue(actual.contains("request.getSession().getAttribute(\"user\")"), actual);
                    assertTrue(actual.contains("response.sendRedirect(\"/login?refer=/\")"), actual);
                    return actual;
                })));
    }

    @Test
    void officialRecipeRemovesNoopWebMvcAdapterSuperCall() {
        rewriteRun(java(
                """
                  import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
                  class WebConfig extends WebMvcConfigurerAdapter {
                      void configure() { super.configure(); }
                  }
                  """,
                """
                  import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                  class WebConfig implements WebMvcConfigurer {
                      void configure() { }
                  }
                  """));
    }

    @Test
    void officialRecipeMigratesAnonymousAdapterButPreservesLookalike() {
        rewriteRun(java(
                """
                  import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
                  class WebConfig {
                      Object bean() { return new WebMvcConfigurerAdapter() {}; }
                  }
                  class CustomWebMvcConfigurerAdapter {}
                  class Other extends CustomWebMvcConfigurerAdapter {}
                  """,
                """
                  import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                  class WebConfig {
                      Object bean() { return new WebMvcConfigurer() {}; }
                  }
                  class CustomWebMvcConfigurerAdapter {}
                  class Other extends CustomWebMvcConfigurerAdapter {}
                  """));
    }

    @Test
    void officialMethodArgumentNotValidRecipeUsesBindErrorUtils() {
        rewriteRun(java(
                """
                import org.springframework.context.MessageSource;
                import org.springframework.validation.ObjectError;
                import org.springframework.web.bind.MethodArgumentNotValidException;

                import java.util.List;
                import java.util.Locale;
                import java.util.Map;

                class ValidationErrors {
                    List<String> simple(List<ObjectError> errors) {
                        return MethodArgumentNotValidException.errorsToStringList(errors);
                    }
                    List<String> localized(List<ObjectError> errors,
                                           MessageSource messages, Locale locale) {
                        return MethodArgumentNotValidException.errorsToStringList(
                                errors, messages, locale);
                    }
                    Map<ObjectError, String> resolved(
                            MethodArgumentNotValidException exception,
                            MessageSource messages, Locale locale) {
                        return exception.resolveErrorMessages(messages, locale);
                    }
                }
                """,
                """
                import org.springframework.context.MessageSource;
                import org.springframework.validation.ObjectError;
                import org.springframework.web.bind.MethodArgumentNotValidException;
                import org.springframework.web.util.BindErrorUtils;

                import java.util.List;
                import java.util.Locale;
                import java.util.Map;

                class ValidationErrors {
                    List<String> simple(List<ObjectError> errors) {
                        return BindErrorUtils.resolve(errors).values().stream().toList();
                    }
                    List<String> localized(List<ObjectError> errors,
                                           MessageSource messages, Locale locale) {
                        return BindErrorUtils.resolve(errors, messages, locale).values().stream().toList();
                    }
                    Map<ObjectError, String> resolved(
                            MethodArgumentNotValidException exception,
                            MessageSource messages, Locale locale) {
                        return BindErrorUtils.resolve(exception.getAllErrors(), messages, locale);
                    }
                }
                """));
    }

    @Test
    void skipsGeneratedJavaAndIsIdempotent() {
        rewriteRun(java("import javax.servlet.Filter; class Generated { Filter f; }",
                source -> source.path("target/generated-sources/Generated.java")));
        rewriteRun(spec -> spec.recipe(recipe(AUTO)).parser(parser()).typeValidationOptions(TypeValidation.none())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java("import javax.servlet.Filter; class FilterConfig { Filter f; }",
                        "import jakarta.servlet.Filter; class FilterConfig { Filter f; }"));
    }

    @ParameterizedTest(name = "marks removed type {0}")
    @ValueSource(strings = {
            "org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter",
            "org.springframework.web.servlet.handler.HandlerInterceptorAdapter",
            "org.springframework.web.servlet.resource.GzipResourceResolver",
            "org.springframework.web.servlet.resource.AppCacheManifestTransformer",
            "org.springframework.web.multipart.commons.CommonsMultipartResolver"
    })
    void marksEveryRemovedMvcType(String type) {
        String simple = type.substring(type.lastIndexOf('.') + 1);
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec,
                java("import " + type + "; class Legacy { " + simple + " value; }", source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.REMOVED));
                    return actual;
                })));
    }

    @Test
    void marksCompleteTilesPackage() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec,
                java("import org.springframework.web.servlet.view.tiles3.TilesViewResolver; class Views {}",
                        source -> source.after(actual -> {
                            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.TILES));
                            return actual;
                        })));
    }

    @Test
    void marksRequestMappingOnlyControllerButNotAnnotatedControllers() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec, java("""
                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;
                @RequestMapping("/legacy") class LegacyHandler {}
                @Controller @RequestMapping("/mvc") class MvcHandler {}
                @RestController @RequestMapping("/rest") class RestHandler {}
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.CONTROLLER));
            assertTrue(actual.indexOf(FindSpringWebMvc6SourceRisks.CONTROLLER) < actual.indexOf("LegacyHandler"));
            assertFalse(actual.substring(actual.indexOf("@Controller")).contains(
                    FindSpringWebMvc6SourceRisks.CONTROLLER));
            return actual;
        })));
    }

    @Test
    void marksValidatedControllerAndInterceptorButNotUnrelatedValidatedService() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec, java("""
                import org.springframework.stereotype.Controller;
                import org.springframework.validation.annotation.Validated;
                import org.springframework.web.servlet.HandlerInterceptor;
                @Controller @Validated class MvcController {}
                @Validated class Service {}
                class AuditInterceptor implements HandlerInterceptor {}
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.VALIDATION));
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.INTERCEPTOR));
            return actual;
        })));
    }

    @Test
    void marksTypedPathAndContentNegotiationCallsOnly() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec, java("""
                import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
                import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
                class Config {
                    void configure(PathMatchConfigurer path, ContentNegotiationConfigurer content) {
                        path.setUseTrailingSlashMatch(true);
                        path.setUseSuffixPatternMatch(false);
                        content.favorPathExtension(false);
                    }
                }
                class Other { void setUseTrailingSlashMatch(boolean value) {} }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.PATH));
            assertFalse(actual.contains("/*~~(" + FindSpringWebMvc6SourceRisks.PATH + ")~~>*/void setUseTrailingSlashMatch"));
            return actual;
        })));
    }

    @Test
    void marksRequestDefaultsAnd404Handling() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec, java("""
                import org.springframework.web.bind.annotation.ExceptionHandler;
                import org.springframework.web.bind.annotation.RequestHeader;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.servlet.NoHandlerFoundException;
                class Controller {
                    void search(@RequestParam(defaultValue = "all") String query,
                                @RequestHeader(defaultValue = "x") String header) {}
                    @ExceptionHandler(NoHandlerFoundException.class) void notFound() {}
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.DEFAULT_VALUE));
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.EXCEPTION));
            return actual;
        })));
    }

    @Test
    void marksResourceRouterAndEmitterApis() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec, java("""
                import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
                  import org.springframework.web.servlet.function.support.RouterFunctionMapping;
                  import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
                  class Config {
                    void configure(ResourceHandlerRegistration resources, RouterFunctionMapping routes, SseEmitter emitter) throws Exception {
                      resources.addResourceLocations("classpath:/static");
                      routes.setOrder(3);
                      emitter.send("event");
                    }
                    RouterFunctionMapping defaultRoutes() { return new RouterFunctionMapping(); }
                  }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.RESOURCE));
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.ROUTER));
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.EMITTER));
            return actual;
        })));
    }

    @Test
    void marksResponseEntityExceptionHandlerHttpStatusOverride() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec, java("""
                import org.springframework.http.HttpStatus;
                import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
                class Errors extends ResponseEntityExceptionHandler {
                    Object handleLegacy(Exception ex, HttpStatus status) { return null; }
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.EXCEPTION));
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.STATUS));
            return actual;
        })));
    }

    @Test
    void marksResponseEntityStatusCallsWhoseEnclosingContractNeedsReview() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec, java("""
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                class StatusContract {
                    HttpStatus status(ResponseEntity<String> response) {
                        return response.getStatusCode();
                    }
                }
                """, source -> source.after(actual -> {
            assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.STATUS), actual);
            return actual;
        })));
    }

    @Test
    void sourceMarkersIgnoreSameNamedUnrelatedTypesAndGeneratedFiles() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec,
                java("""
                        class PathMatchConfigurer { void setUseTrailingSlashMatch(boolean value) {} }
                        class Use { void configure(PathMatchConfigurer c) { c.setUseTrailingSlashMatch(true); } }
                        """),
                java("""
                        import org.springframework.web.bind.annotation.ExceptionHandler;
                        class NoHandlerFoundException extends Exception {}
                        class BusinessErrors { @ExceptionHandler(NoHandlerFoundException.class) void handle() {} }
                        """, source -> source.path("BusinessErrors.java")),
                java("import org.springframework.web.servlet.resource.GzipResourceResolver; class Generated {}",
                        source -> source.path("build/generated/Generated.java")));
    }

    @Test
    void marksExactPropertiesAndNestedYamlPaths() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec,
                properties("spring.mvc.pathmatch.matching-strategy=ant_path_matcher\nnormal.key=true\n",
                        source -> source.after(actual -> {
                            assertTrue(actual.contains(FindSpringWebMvc6ConfigurationRisks.CONFIG));
                            return actual;
                        })),
                yaml("""
                        spring:
                          mvc:
                            problemdetails:
                              enabled: true
                        normal: true
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebMvc6ConfigurationRisks.CONFIG));
                    return actual;
                })));
    }

    @Test
    void marksMvcXmlTilesAndJavaxDescriptors() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec,
                xml("""
                        <beans xmlns:mvc="http://www.springframework.org/schema/mvc">
                          <mvc:annotation-driven use-suffix-pattern="true"/>
                          <bean class="org.springframework.web.servlet.view.tiles3.TilesViewResolver"/>
                          <filter-class>javax.servlet.Filter</filter-class>
                        </beans>
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains(FindSpringWebMvc6ConfigurationRisks.XML));
                    assertTrue(actual.contains(FindSpringWebMvc6ConfigurationRisks.TILES));
                    assertTrue(actual.contains(FindSpringWebMvc6ConfigurationRisks.JAKARTA));
                    return actual;
                })));
    }

    @Test
    void configurationMarkersIgnoreLookalikesPomAndGenerated() {
        rewriteRun(SpringWebMvcSourceMigrationTest::riskSpec,
                properties("app.spring.mvc.pathmatch.matching-strategy=safe\n"),
                yaml("springish:\n  mvc:\n    problemdetails:\n      enabled: true\n"),
                xml("<project><properties><spring.mvc.pathmatch.matching-strategy>ant</spring.mvc.pathmatch.matching-strategy></properties></project>",
                        source -> source.path("pom.xml")),
                xml("<application><resources location=\"classpath:/business\"/><interceptors/><tiles/></application>"),
                properties("spring.mvc.pathmatch.matching-strategy=ant_path_matcher\n",
                        source -> source.path("target/classes/application.properties")));
    }

    @Test
    void riskMarkersAreIdempotent() {
        rewriteRun(spec -> {
                    riskSpec(spec);
                    spec.cycles(2).expectedCyclesThatMakeChanges(1);
                },
                properties("spring.mvc.pathmatch.matching-strategy=ant_path_matcher\n",
                        "~~(" + FindSpringWebMvc6ConfigurationRisks.CONFIG + ")~~>spring.mvc.pathmatch.matching-strategy=ant_path_matcher\n"));
    }

    @Test
    void recommendedAggregateRunsUpgradeAutoAndMarkPhasesTogether() {
        rewriteRun(spec -> spec.recipe(recipe(
                                "com.huawei.clouds.openrewrite.springwebmvc.MigrateSpringWebMvcTo6_2_19"))
                        .parser(parser()).typeValidationOptions(TypeValidation.none()),
                pomXml(pom("5.3.23"), pom("6.2.19")),
                java("""
                        import javax.servlet.http.HttpServletRequest;
                        import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
                        import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
                        class WebConfig extends WebMvcConfigurerAdapter {
                            void configure(HttpServletRequest request, PathMatchConfigurer paths) {
                                paths.setUseTrailingSlashMatch(true);
                            }
                        }
                        """, source -> source.after(actual -> {
                    assertTrue(actual.contains("import jakarta.servlet.http.HttpServletRequest;"));
                    assertTrue(actual.contains("implements WebMvcConfigurer"));
                    assertTrue(actual.contains(FindSpringWebMvc6SourceRisks.PATH));
                    return actual;
                })));
    }

    private static void riskSpec(RecipeSpec spec) {
        spec.recipe(recipe(RISKS)).parser(parser()).typeValidationOptions(TypeValidation.none());
    }

    private static org.openrewrite.Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package javax.servlet; public interface Filter {}",
                "package javax.servlet.http; public interface HttpServletRequest {}",
                "package javax.servlet.http; public interface HttpServletResponse {}",
                "package jakarta.servlet; public interface Filter {}",
                "package jakarta.servlet.http; public interface HttpServletRequest {}",
                "package jakarta.servlet.http; public interface HttpServletResponse {}",
                "package org.springframework.web.servlet.config.annotation; public interface WebMvcConfigurer { default void addResourceHandlers(ResourceHandlerRegistry registry) {} }",
                "package org.springframework.web.servlet.config.annotation; public class WebMvcConfigurerAdapter implements WebMvcConfigurer { public void configure() {} }",
                "package org.springframework.web.servlet; public interface HandlerInterceptor {}",
                "package org.springframework.web.servlet; public interface AsyncHandlerInterceptor extends HandlerInterceptor { " +
                "default void afterConcurrentHandlingStarted(javax.servlet.http.HttpServletRequest r, javax.servlet.http.HttpServletResponse s) {} }",
                "package org.springframework.web.servlet.handler; public class HandlerInterceptorAdapter implements org.springframework.web.servlet.AsyncHandlerInterceptor { public void postHandle(javax.servlet.http.HttpServletRequest r, javax.servlet.http.HttpServletResponse s) {} }",
                "package org.springframework.web.servlet.resource; public class GzipResourceResolver {}",
                "package org.springframework.web.servlet.resource; public class AppCacheManifestTransformer {}",
                "package org.springframework.web.multipart.commons; public class CommonsMultipartResolver {}",
                "package org.springframework.web.servlet.view.tiles3; public class TilesViewResolver {}",
                "package org.springframework.stereotype; public @interface Controller {}",
                "package org.springframework.web.bind.annotation; public @interface RestController {}",
                "package org.springframework.web.bind.annotation; public @interface RequestMapping { String[] value() default {}; }",
                "package org.springframework.validation.annotation; public @interface Validated {}",
                "package org.springframework.web.servlet.config.annotation; public class PathMatchConfigurer { public PathMatchConfigurer setUseTrailingSlashMatch(Boolean b){return this;} public PathMatchConfigurer setUseSuffixPatternMatch(Boolean b){return this;} }",
                "package org.springframework.web.servlet.config.annotation; public class ContentNegotiationConfigurer { public ContentNegotiationConfigurer favorPathExtension(boolean b){return this;} }",
                "package org.springframework.web.servlet.config.annotation; public class ResourceHandlerRegistration { public ResourceHandlerRegistration addResourceLocations(String... s){return this;} }",
                "package org.springframework.web.servlet.config.annotation; public class ResourceHandlerRegistry { public ResourceHandlerRegistration addResourceHandler(String... s){return new ResourceHandlerRegistration();} }",
                "package org.springframework.context.annotation; public @interface Configuration {}",
                "package org.springframework.context; public interface MessageSource {}",
                "package org.springframework.validation; public class ObjectError {}",
                "package org.springframework.web.bind; public class MethodArgumentNotValidException extends RuntimeException { " +
                "public static java.util.List<String> errorsToStringList(" +
                "java.util.List<? extends org.springframework.validation.ObjectError> errors) { return null; } " +
                "public static java.util.List<String> errorsToStringList(" +
                "java.util.List<? extends org.springframework.validation.ObjectError> errors, " +
                "org.springframework.context.MessageSource source, java.util.Locale locale) { return null; } " +
                "public java.util.Map<org.springframework.validation.ObjectError, String> resolveErrorMessages(" +
                "org.springframework.context.MessageSource source, java.util.Locale locale) { return null; } " +
                "public java.util.List<org.springframework.validation.ObjectError> getAllErrors() { return null; } }",
                "package org.springframework.web.util; public class BindErrorUtils { " +
                "public static <E> java.util.Map<E, String> resolve(java.util.List<E> errors) { return null; } " +
                "public static <E> java.util.Map<E, String> resolve(java.util.List<E> errors, " +
                "org.springframework.context.MessageSource source, java.util.Locale locale) { return null; } }",
                "package org.springframework.web.servlet.function.support; public class RouterFunctionMapping { public void setOrder(int order){} }",
                "package org.springframework.web.servlet.mvc.method.annotation; public class ResponseBodyEmitter { public void send(Object o) throws Exception{} }",
                "package org.springframework.web.servlet.mvc.method.annotation; public class SseEmitter extends ResponseBodyEmitter {}",
                "package org.springframework.web.servlet; public class NoHandlerFoundException extends Exception {}",
                "package org.springframework.web.bind.annotation; public @interface RequestParam { String defaultValue() default \"\"; }",
                "package org.springframework.web.bind.annotation; public @interface RequestHeader { String defaultValue() default \"\"; }",
                "package org.springframework.web.bind.annotation; public @interface CookieValue { String defaultValue() default \"\"; }",
                "package org.springframework.web.bind.annotation; public @interface ExceptionHandler { Class<?>[] value() default {}; }",
                "package org.springframework.web.servlet.mvc.method.annotation; public abstract class ResponseEntityExceptionHandler {}",
                "package org.springframework.http; public enum HttpStatus { BAD_REQUEST }",
                "package org.springframework.http; public class ResponseEntity<T> { public HttpStatus getStatusCode(){return null;} }"
        );
    }

    private static String pom(String version) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>a</artifactId><version>1</version>" +
               "<dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-webmvc</artifactId>" +
               "<version>" + version + "</version></dependency></dependencies></project>";
    }
}
