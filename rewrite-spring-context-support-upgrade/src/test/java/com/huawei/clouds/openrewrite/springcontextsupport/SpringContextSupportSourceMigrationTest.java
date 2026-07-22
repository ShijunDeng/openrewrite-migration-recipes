package com.huawei.clouds.openrewrite.springcontextsupport;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
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

class SpringContextSupportSourceMigrationTest implements RewriteTest {
    private static final String NAMESPACE =
            "com.huawei.clouds.openrewrite.springcontextsupport.MigrateDeterministicSpring6JakartaNamespaces";
    private static final String RISKS =
            "com.huawei.clouds.openrewrite.springcontextsupport.FindSpringContextSupport6SourceAndConfigRisks";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.springcontextsupport.MigrateSpringContextSupportTo6_2_19";

    private static final String EHCACHE =
            "Spring 6 removed org.springframework.cache.ehcache and Ehcache 2 integration; choose Ehcache 3 through JCache or its native API and migrate configuration deliberately";
    private static final String PARAMETER =
            "LocalVariableTableParameterNameDiscoverer was removed in Spring 6.1; use standard reflection and compile Java/Groovy/Kotlin with parameter metadata";
    private static final String REMOVED =
            "Spring 6 removed legacy remoting or EJB integration; replace it with a supported protocol or direct JNDI design and add integration tests";
    private static final String ASYNC =
            "Spring 6 enforces @Async return types; use void, Future/CompletableFuture, or redesign the asynchronous boundary";
    private static final String BEAN =
            "Spring 6.2 rejects invalid @Configuration methods: @Bean must not return void or also declare @Autowired";
    private static final String MAIL =
            "JavaMail integration detected; verify Jakarta Mail/Activation provider versions, Session properties, MIME encoding, attachments and transport failures";
    private static final String QUARTZ =
            "Quartz integration detected; verify Quartz/Spring alignment, JobFactory injection, transaction/data-source ownership, lifecycle and shutdown semantics";
    private static final String CACHE =
            "Caffeine reactive cache mode detected; Spring 6.1 caches produced Mono/Flux values asynchronously, so verify provider support and existing Publisher caching semantics";
    private static final String SPEL =
            "Spring 6.2.19 limits SpEL evaluation to 10,000 operations by default; size and secure trusted expressions and configure maxOperations only when justified";
    private static final String PROPERTY =
            "Spring compatibility switch or escaped placeholder detected; verify the Spring 6.1/6.2 parameter, cache, locking, placeholder and SpEL behavior before retaining it";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(NAMESPACE)).parser(apiParser()).typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesMailActivationInjectAndExactAnnotationTypes() {
        rewriteRun(java(
                """
                package example;
                import javax.activation.DataSource;
                import javax.annotation.PostConstruct;
                import javax.annotation.PreDestroy;
                import javax.annotation.Resource;
                import javax.inject.Inject;
                import javax.mail.Message;
                class MailConfiguration {
                    @Inject DataSource dataSource;
                    @Resource Message message;
                    @PostConstruct void start() {}
                    @PreDestroy void stop() {}
                }
                """,
                """
                package example;
                import jakarta.activation.DataSource;
                import jakarta.annotation.PostConstruct;
                import jakarta.annotation.PreDestroy;
                import jakarta.annotation.Resource;
                import jakarta.inject.Inject;
                import jakarta.mail.Message;

                class MailConfiguration {
                    @Inject DataSource dataSource;
                    @Resource Message message;
                    @PostConstruct void start() {}
                    @PreDestroy void stop() {}
                }
                """
        ));
    }

    @Test
    void preservesJavaxCacheAndAnnotationConcurrent() {
        rewriteRun(java(
                """
                package example;
                import javax.annotation.concurrent.ThreadSafe;
                import javax.cache.Cache;
                @ThreadSafe class CacheConfiguration { Cache<String, String> cache; }
                """
        ));
    }

    @Test
    void migratesOnlyStructuredConfigurationValues() {
        rewriteRun(
                properties(
                        "handler=javax.mail.internet.MimeMessage\ncache=javax.cache.Cache\nannotation=javax.annotation.PostConstruct\n",
                        "handler=jakarta.mail.internet.MimeMessage\ncache=javax.cache.Cache\nannotation=jakarta.annotation.PostConstruct\n"),
                yaml(
                        "mailType: javax.mail.internet.MimeMessage\ncacheType: javax.cache.Cache\nactivation: javax.activation.DataSource\n",
                        "mailType: jakarta.mail.internet.MimeMessage\ncacheType: javax.cache.Cache\nactivation: jakarta.activation.DataSource\n"),
                xml(
                        "<beans mail=\"javax.mail.Message\"><bean class=\"javax.inject.Provider\">javax.annotation.Resource</bean><cache>javax.cache.Cache</cache></beans>",
                        "<beans mail=\"jakarta.mail.Message\"><bean class=\"jakarta.inject.Provider\">jakarta.annotation.Resource</bean><cache>javax.cache.Cache</cache></beans>")
        );
    }

    @Test
    void doesNotRewritePomCoordinatesOrGeneratedSources() {
        rewriteRun(
                xml("""
                       <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>javax</artifactId><version>1</version><dependencies>
                         <dependency><groupId>javax.mail</groupId><artifactId>mail</artifactId><version>1.6.2</version></dependency>
                       </dependencies></project>
                       """, source -> source.path("pom.xml")),
                properties("handler=javax.mail.Message\n", source -> source.path("build/generated/application.properties")),
                java("import javax.mail.Message; class Generated { Message value; }",
                        source -> source.path("target/generated-sources/Generated.java"))
        );
    }

    @Test
    void namespaceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(recipe(NAMESPACE)).parser(apiParser())
                        .typeValidationOptions(TypeValidation.none()).cycles(2).expectedCyclesThatMakeChanges(1),
                java("import javax.mail.Message; class Mail { Message value; }",
                        "import jakarta.mail.Message; class Mail { Message value; }"));
    }

    @Test
    void marksOfficialRemovedSpringApisPrecisely() {
        rewriteRun(SpringContextSupportSourceMigrationTest::riskSpec, java(
                """
                package example;
                import org.springframework.cache.ehcache.EhCacheCacheManager;
                import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
                import org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean;
                class Legacy {}
                """,
                """
                package example;
                /*~~(%s)~~>*/import org.springframework.cache.ehcache.EhCacheCacheManager;
                /*~~(%s)~~>*/import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
                /*~~(%s)~~>*/import org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean;
                class Legacy {}
                """.formatted(EHCACHE, PARAMETER, REMOVED)
        ));
    }

    @Test
    void marksInvalidAsyncAndBeanMethodsButNotValidForms() {
        rewriteRun(SpringContextSupportSourceMigrationTest::riskSpec, java(
                """
                package example;
                import java.util.concurrent.CompletableFuture;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.context.annotation.Bean;
                import org.springframework.scheduling.annotation.Async;
                class Configuration {
                    @Async String invalidAsync() { return "x"; }
                    @Async void validVoid() {}
                    @Async CompletableFuture<String> validFuture() { return null; }
                    @Bean void invalidBean() {}
                    @Bean @Autowired String invalidAutowiredBean() { return "x"; }
                    @Bean String validBean() { return "x"; }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(ASYNC));
                    assertTrue(printed.contains(BEAN));
                    assertTrue(printed.contains("@Async void validVoid()"));
                    assertFalse(printed.contains("*/@Async void validVoid()"));
                    assertFalse(printed.contains("*/@Bean String validBean()"));
                })
        ));
    }

    @Test
    void marksTypedMailQuartzCaffeineAndSpelUsage() {
        rewriteRun(SpringContextSupportSourceMigrationTest::riskSpec, java(
                """
                package example;
                import org.springframework.cache.caffeine.CaffeineCacheManager;
                import org.springframework.expression.spel.standard.SpelExpressionParser;
                import org.springframework.mail.javamail.JavaMailSenderImpl;
                import org.springframework.scheduling.quartz.SchedulerFactoryBean;
                class Integrations {
                    void configure(JavaMailSenderImpl mail, SchedulerFactoryBean scheduler, CaffeineCacheManager cache) {
                        mail.setProtocol("smtp");
                        scheduler.setWaitForJobsToCompleteOnShutdown(true);
                        cache.setAsyncCacheMode(true);
                        new SpelExpressionParser();
                    }
                }
                """,
                source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(MAIL));
                    assertTrue(printed.contains(QUARTZ));
                    assertTrue(printed.contains(CACHE));
                    assertTrue(printed.contains(SPEL));
                })
        ));
    }

    @Test
    void ignoresSameNamedMethodsOnUnrelatedTypes() {
        rewriteRun(SpringContextSupportSourceMigrationTest::riskSpec, java(
                """
                package example;
                class Custom {
                    void setProtocol(String value) {}
                    void setAsyncCacheMode(boolean value) {}
                    void setWaitForJobsToCompleteOnShutdown(boolean value) {}
                }
                class Use { void configure(Custom custom) { custom.setProtocol("smtp"); custom.setAsyncCacheMode(true); } }
                """
        ));
    }

    @Test
    void marksExactCompatibilityConfigurationAcrossFormats() {
        rewriteRun(SpringContextSupportSourceMigrationTest::riskSpec,
                properties(
                        "spring.cache.reactivestreams.ignore=true\nnormal.key=true\n",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains(PROPERTY)))),
                yaml(
                        "spring.expression.maxOperations: 20000\nnormal: value\n",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains(PROPERTY)))),
                xml(
                        "<beans><value>org.springframework.mail.javamail.JavaMailSenderImpl</value><safe>example.Mail</safe></beans>",
                        source -> source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains(MAIL))))
        );
    }

    @Test
    void skipsRiskMarkersInGeneratedAndPomFiles() {
        rewriteRun(SpringContextSupportSourceMigrationTest::riskSpec,
                properties("spring.locking.strict=true\n", source -> source.path("generated/application.properties")),
                pomXml("""
                       <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>safe</artifactId><version>1</version>
                         <properties><handler>org.springframework.mail.javamail.JavaMailSenderImpl</handler></properties>
                       </project>
                       """),
                java("import org.springframework.cache.ehcache.EhCacheCacheManager; class Generated {}",
                        source -> source.path("build/generated/Generated.java"))
        );
    }

    @Test
    void riskMarkersAreIdempotent() {
        rewriteRun(spec -> {
                    riskSpec(spec);
                    spec.cycles(2).expectedCyclesThatMakeChanges(1);
                },
                java(
                        "import org.springframework.core.LocalVariableTableParameterNameDiscoverer; class Legacy {}",
                        "/*~~(%s)~~>*/import org.springframework.core.LocalVariableTableParameterNameDiscoverer; class Legacy {}"
                                .formatted(PARAMETER)));
    }

    @Test
    void recommendedRecipeUpgradesDependencyMigratesJakartaAndRetainsManualWarning() {
        rewriteRun(spec -> spec.recipe(recipe(RECOMMENDED)).parser(apiParser()).typeValidationOptions(TypeValidation.none()),
                pomXml(
                        pom("5.3.29"),
                        pom("6.2.19")),
                java(
                        """
                        import javax.mail.Message;
                        import org.springframework.cache.ehcache.EhCacheCacheManager;
                        class Application { Message message; }
                        """,
                        """
                        import jakarta.mail.Message;
                        /*~~(%s)~~>*/import org.springframework.cache.ehcache.EhCacheCacheManager;
                        class Application { Message message; }
                        """.formatted(EHCACHE))
        );
    }

    private static void riskSpec(RecipeSpec spec) {
        spec.recipe(recipe(RISKS)).parser(apiParser()).typeValidationOptions(TypeValidation.none());
    }

    private static org.openrewrite.Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static JavaParser.Builder<?, ?> apiParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package javax.mail; public class Message {}",
                "package jakarta.mail; public class Message {}",
                "package javax.activation; public interface DataSource {}",
                "package jakarta.activation; public interface DataSource {}",
                "package javax.inject; public @interface Inject {}",
                "package jakarta.inject; public @interface Inject {}",
                "package javax.annotation; public @interface PostConstruct {}",
                "package javax.annotation; public @interface PreDestroy {}",
                "package javax.annotation; public @interface Resource {}",
                "package jakarta.annotation; public @interface PostConstruct {}",
                "package jakarta.annotation; public @interface PreDestroy {}",
                "package jakarta.annotation; public @interface Resource {}",
                "package javax.annotation.concurrent; public @interface ThreadSafe {}",
                "package javax.cache; public interface Cache<K,V> {}",
                "package org.springframework.scheduling.annotation; public @interface Async {}",
                "package org.springframework.context.annotation; public @interface Bean {}",
                "package org.springframework.beans.factory.annotation; public @interface Autowired {}",
                "package org.springframework.mail.javamail; public class JavaMailSenderImpl { public void setProtocol(String value) {} }",
                "package org.springframework.scheduling.quartz; public class SchedulerFactoryBean { public void setWaitForJobsToCompleteOnShutdown(boolean value) {} }",
                "package org.springframework.cache.caffeine; public class CaffeineCacheManager { public void setAsyncCacheMode(boolean value) {} }",
                "package org.springframework.expression.spel.standard; public class SpelExpressionParser { public SpelExpressionParser() {} }"
        );
    }

    private static String pom(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies><dependency>
                 <groupId>org.springframework</groupId><artifactId>spring-context-support</artifactId><version>%s</version>
               </dependency></dependencies></project>
               """.formatted(version);
    }
}
