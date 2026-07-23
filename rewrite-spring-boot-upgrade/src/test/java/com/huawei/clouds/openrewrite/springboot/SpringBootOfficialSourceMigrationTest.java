package com.huawei.clouds.openrewrite.springboot;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot2.MoveAutoConfigurationToImportsFile;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class SpringBootOfficialSourceMigrationTest implements RewriteTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.springboot.";
    private static final String RECIPE =
            PREFIX + "MigrateOfficialSpringBootSource";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe()).parser(parser());
    }

    @Test
    void migratesRealSpringBoot2718ConstructorBindingFixture() {
        // Fixed upstream fixture:
        // spring-projects/spring-boot@0c8b382d42db22b92efcf47000d0ff9ef4971629
        rewriteRun(java(
                """
                /*
                 * Copyright 2012-2019 the original author or authors.
                 *
                 * Licensed under the Apache License, Version 2.0 (the "License");
                 * you may not use this file except in compliance with the License.
                 * You may obtain a copy of the License at
                 *
                 *      https://www.apache.org/licenses/LICENSE-2.0
                 *
                 * Unless required by applicable law or agreed to in writing, software
                 * distributed under the License is distributed on an "AS IS" BASIS,
                 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                 * See the License for the specific language governing permissions and
                 * limitations under the License.
                 */

                package org.springframework.boot.test.autoconfigure.web.client;

                import org.springframework.boot.context.properties.ConfigurationProperties;
                import org.springframework.boot.context.properties.ConstructorBinding;
                import org.springframework.boot.context.properties.bind.DefaultValue;

                /**
                 * Example {@link ConstructorBinding constructor-bound}
                 * {@link ConfigurationProperties @ConfigurationProperties} used to test the use of
                 * configuration properties scan with sliced test.
                 *
                 * @author Stephane Nicoll
                 */
                @ConstructorBinding
                @ConfigurationProperties("example")
                public class ExampleProperties {

                    private final String name;

                    public ExampleProperties(@DefaultValue("test") String name) {
                        this.name = name;
                    }

                    public String getName() {
                        return this.name;
                    }

                }
                """,
                """
                /*
                 * Copyright 2012-2019 the original author or authors.
                 *
                 * Licensed under the Apache License, Version 2.0 (the "License");
                 * you may not use this file except in compliance with the License.
                 * You may obtain a copy of the License at
                 *
                 *      https://www.apache.org/licenses/LICENSE-2.0
                 *
                 * Unless required by applicable law or agreed to in writing, software
                 * distributed under the License is distributed on an "AS IS" BASIS,
                 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                 * See the License for the specific language governing permissions and
                 * limitations under the License.
                 */

                package org.springframework.boot.test.autoconfigure.web.client;

                import org.springframework.boot.context.properties.ConfigurationProperties;
                import org.springframework.boot.context.properties.bind.ConstructorBinding;
                import org.springframework.boot.context.properties.bind.DefaultValue;

                /**
                 * Example {@link ConstructorBinding constructor-bound}
                 * {@link ConfigurationProperties @ConfigurationProperties} used to test the use of
                 * configuration properties scan with sliced test.
                 *
                 * @author Stephane Nicoll
                 */

                @ConfigurationProperties("example")
                public class ExampleProperties {

                    private final String name;

                    public ExampleProperties(@DefaultValue("test") String name) {
                        this.name = name;
                    }

                    public String getName() {
                        return this.name;
                    }

                }
                """,
                source -> source.path(
                        "src/test/java/org/springframework/boot/test/autoconfigure/web/client/ExampleProperties.java")));
    }

    @Test
    void acceptsRealSpringBoot3515TargetFixture() {
        // Fixed upstream fixture:
        // spring-projects/spring-boot@c069bce9fb096f7e146695459d69bf653dece1e6
        rewriteRun(java(
                """
                /*
                 * Copyright 2012-present the original author or authors.
                 *
                 * Licensed under the Apache License, Version 2.0 (the "License");
                 * you may not use this file except in compliance with the License.
                 * You may obtain a copy of the License at
                 *
                 *      https://www.apache.org/licenses/LICENSE-2.0
                 *
                 * Unless required by applicable law or agreed to in writing, software
                 * distributed under the License is distributed on an "AS IS" BASIS,
                 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                 * See the License for the specific language governing permissions and
                 * limitations under the License.
                 */

                package org.springframework.boot.test.autoconfigure.web.client;

                import org.springframework.boot.context.properties.ConfigurationProperties;
                import org.springframework.boot.context.properties.bind.ConstructorBinding;
                import org.springframework.boot.context.properties.bind.DefaultValue;

                /**
                 * Example {@link ConstructorBinding constructor-bound}
                 * {@link ConfigurationProperties @ConfigurationProperties} used to test the use of
                 * configuration properties scan with sliced test.
                 *
                 * @author Stephane Nicoll
                 */
                @ConfigurationProperties("example")
                public class ExampleProperties {

                    private final String name;

                    public ExampleProperties(@DefaultValue("test") String name) {
                        this.name = name;
                    }

                    public String getName() {
                        return this.name;
                    }

                }
                """,
                source -> source.path(
                        "src/test/java/org/springframework/boot/test/autoconfigure/web/client/ExampleProperties.java")));
    }

    @Test
    void movesRealisticSpringFactoriesEntriesToOfficialImportsFile() {
        rewriteRun(
                text(
                        """
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\
                        com.acme.web.WebAutoConfiguration,\\
                        com.acme.data.DataAutoConfiguration
                        """,
                        null,
                        source -> source.path("src/main/resources/META-INF/spring.factories")),
                text(
                        null,
                        """
                        com.acme.data.DataAutoConfiguration
                        com.acme.web.WebAutoConfiguration
                        """,
                        source -> source.path(
                                "src/main/resources/META-INF/spring/" +
                                "org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
        );
    }

    @Test
    void directOfficialMoveAutoConfigurationControl() {
        rewriteRun(spec -> spec.recipe(new MoveAutoConfigurationToImportsFile(false)),
                text(
                        """
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\
                        com.acme.web.WebAutoConfiguration
                        """,
                        null,
                        source -> source.path("src/main/resources/META-INF/spring.factories")),
                text(
                        null,
                        "com.acme.web.WebAutoConfiguration",
                        source -> source.path(
                                "src/main/resources/META-INF/spring/" +
                                "org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
        );
    }

    @Test
    void relocatesBootLauncherReferencesInPlainText() {
        rewriteRun(text(
                """
                Main-Class: org.springframework.boot.loader.JarLauncher
                loader.main=com.acme.Application
                """,
                """
                Main-Class: org.springframework.boot.loader.launch.JarLauncher
                loader.main=com.acme.Application
                """,
                source -> source.path("src/main/resources/META-INF/MANIFEST.MF")));
    }

    @Test
    void migratesExactJakartaSourcePackageButPreservesJavaSeJavax() {
        rewriteRun(
                java(
                        """
                        import javax.servlet.Filter;

                        class WebFilter {
                            Filter delegate;
                        }
                        """,
                        """
                        import jakarta.servlet.Filter;

                        class WebFilter {
                            Filter delegate;
                        }
                        """,
                        source -> source.path("src/main/java/com/acme/WebFilter.java")),
                java(
                        """
                        import javax.sql.DataSource;

                        class Database {
                            DataSource dataSource;
                        }
                        """,
                        source -> source.path("src/main/java/com/acme/Database.java"))
        );
    }

    @Test
    void migratesTaskSchedulerBuilderToSchedulerBuilderNotExecutorBuilder() {
        rewriteRun(java(
                """
                import org.springframework.boot.task.TaskSchedulerBuilder;

                class SchedulingConfiguration {
                    TaskSchedulerBuilder scheduler = new TaskSchedulerBuilder().poolSize(4);
                }
                """,
                """
                import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;

                class SchedulingConfiguration {
                    ThreadPoolTaskSchedulerBuilder scheduler = new ThreadPoolTaskSchedulerBuilder().poolSize(4);
                }
                """));
    }

    @Test
    void reusesOfficialBoot22And23MovedTypes() {
        rewriteRun(spec -> spec.recipe(recipe(PREFIX + "SpringBootSourceVisitors_2_2")),
                java(
                        """
                        import org.springframework.boot.actuate.health.ApplicationHealthIndicator;
                        import org.springframework.boot.test.autoconfigure.web.reactive.WebTestClientBuilderCustomizer;
                        import org.springframework.boot.test.rule.OutputCapture;

                        class Boot22Apis {
                            ApplicationHealthIndicator health;
                            WebTestClientBuilderCustomizer webClient;
                            OutputCapture output;
                        }
                        """,
                        """
                        import org.springframework.boot.actuate.health.PingHealthIndicator;
                        import org.springframework.boot.test.system.OutputCaptureRule;
                        import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer;

                        class Boot22Apis {
                            PingHealthIndicator health;
                            WebTestClientBuilderCustomizer webClient;
                            OutputCaptureRule output;
                        }
                        """));
        rewriteRun(spec -> spec.recipe(recipe(PREFIX + "SpringBootSourceVisitors_2_3")),
                java(
                        """
                        import org.springframework.boot.autoconfigure.elasticsearch.rest.RestClientBuilderCustomizer;

                        class Boot23Apis {
                            RestClientBuilderCustomizer customizer;
                        }
                        """,
                        """
                        import org.springframework.boot.autoconfigure.elasticsearch.RestClientBuilderCustomizer;

                        class Boot23Apis {
                            RestClientBuilderCustomizer customizer;
                        }
                        """));
    }

    @Test
    void reusesBothOfficialBoot24UndertowMethodRenames() {
        rewriteRun(spec -> spec.recipe(recipe(PREFIX + "SpringBootSourceVisitors_2_4")),
                java(
                        """
                        import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;

                        class UndertowConfiguration {
                            void configure(UndertowServletWebServerFactory factory) {
                                if (!factory.isEagerInitFilters()) {
                                    factory.setEagerInitFilters(true);
                                }
                            }
                        }
                        """,
                        """
                        import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;

                        class UndertowConfiguration {
                            void configure(UndertowServletWebServerFactory factory) {
                                if (!factory.isEagerFilterInit()) {
                                    factory.setEagerFilterInit(true);
                                }
                            }
                        }
                        """));
    }

    @Test
    void reusesOfficialBoot25TypeMoves() {
        rewriteRun(spec -> spec.recipe(recipe(PREFIX + "SpringBootSourceVisitors_2_5")),
                java(
                        """
                        import org.springframework.boot.autoconfigure.data.jpa.EntityManagerFactoryDependsOnPostProcessor;
                        import org.springframework.boot.autoconfigure.web.ResourceProperties;

                        class Boot25Apis {
                            EntityManagerFactoryDependsOnPostProcessor ordering;
                            ResourceProperties resources;
                        }
                        """,
                        """
                        import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
                        import org.springframework.boot.autoconfigure.web.WebProperties;

                        class Boot25Apis {
                            EntityManagerFactoryDependsOnPostProcessor ordering;
                            WebProperties.Resources resources;
                        }
                        """));
    }

    @Test
    void reusesOfficialBoot25DatabaseInitializationOrdering() {
        rewriteRun(spec -> spec.recipe(recipe(PREFIX + "SpringBootSourceVisitors_2_5")),
                mavenProject("app",
                        pomXml(bootPom("2.5.1")),
                        java(
                                """
                                import javax.sql.DataSource;

                                class CustomDataSourceInitializer {
                                    void initialize(DataSource dataSource) {
                                    }
                                }
                                """,
                                source -> source.path(
                                        "src/main/java/com/acme/CustomDataSourceInitializer.java")),
                        java(
                                """
                                import org.springframework.context.annotation.Bean;
                                import org.springframework.context.annotation.Configuration;

                                @Configuration
                                class DatabaseConfiguration {
                                    @Bean
                                    CustomDataSourceInitializer initializer() {
                                        return new CustomDataSourceInitializer();
                                    }
                                }
                                """,
                                """
                                import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
                                import org.springframework.context.annotation.Bean;
                                import org.springframework.context.annotation.Configuration;

                                @Configuration
                                class DatabaseConfiguration {
                                    @Bean
                                    @DependsOnDatabaseInitialization
                                    CustomDataSourceInitializer initializer() {
                                        return new CustomDataSourceInitializer();
                                    }
                                }
                                """,
                                source -> source.path(
                                        "src/main/java/com/acme/DatabaseConfiguration.java"))
                ));
    }

    @Test
    void reusesOfficialBoot30BeanAndBoot31RepositoryMigrations() {
        rewriteRun(spec -> spec.recipe(recipe(PREFIX + "SpringBootSourceVisitors_3_0")),
                java(
                        """
                        import org.springframework.context.annotation.Bean;

                        class BeanConfiguration {
                            @Bean
                            void applicationService() {
                            }
                        }
                        """,
                        """
                        import org.springframework.context.annotation.Bean;

                        class BeanConfiguration {
                            @Bean
                            Object applicationService() {
                                return null;
                            }
                        }
                        """));
        rewriteRun(spec -> spec.recipe(recipe(PREFIX + "SpringBootSourceVisitors_3_1")),
                java(
                        """
                        import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurerAdapter;

                        class RepositoryConfiguration extends RepositoryRestConfigurerAdapter {
                        }
                        """,
                        """
                        import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;

                        class RepositoryConfiguration implements RepositoryRestConfigurer {
                        }
                        """));
    }

    @Test
    void selectedReleaseBandsSkipHistoricalJakartaMigrationForBoot34() {
        rewriteRun(spec -> spec.recipe(selectedSourceVisitorsRecipe()),
                xml(bootPom("3.4.12"),
                        source -> source.path("boot34/pom.xml")),
                java(
                        """
                        import javax.servlet.Filter;

                        class Boot34Filter {
                            Filter filter;
                        }
                        """,
                        source -> source.path(
                                "boot34/src/main/java/com/acme/Boot34Filter.java")),
                xml(bootPom("2.7.18"),
                        source -> source.path("boot27/pom.xml")),
                java(
                        """
                        import javax.servlet.Filter;

                        class Boot27Filter {
                            Filter filter;
                        }
                        """,
                        """
                        import jakarta.servlet.Filter;

                        class Boot27Filter {
                            Filter filter;
                        }
                        """,
                        source -> source.path(
                                "boot27/src/main/java/com/acme/Boot27Filter.java"))
        );
    }

    @Test
    void generatedJavaTextAndFactoriesAreStrictlyNoop() {
        rewriteRun(
                java(
                        """
                        import javax.servlet.Filter;

                        class GeneratedFilter {
                            Filter delegate;
                        }
                        """,
                        source -> source.path("target/generated-sources/GeneratedFilter.java")),
                text(
                        "Main-Class: org.springframework.boot.loader.JarLauncher",
                        source -> source.path("build/generated/META-INF/MANIFEST.MF")),
                text(
                        """
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\
                        com.acme.GeneratedAutoConfiguration
                        """,
                        source -> source.path("target/generated/META-INF/spring.factories"))
        );
    }

    @Test
    void sourceMigrationIsTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import javax.servlet.Filter;

                        class WebFilter {
                            Filter delegate;
                        }
                        """,
                        """
                        import jakarta.servlet.Filter;

                        class WebFilter {
                            Filter delegate;
                        }
                        """));
    }

    private static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package org.springframework.boot.context.properties;
                public @interface ConfigurationProperties {
                    String value() default "";
                }
                """,
                """
                package org.springframework.boot.context.properties;
                public @interface ConstructorBinding {
                }
                """,
                """
                package org.springframework.boot.context.properties.bind;
                public @interface ConstructorBinding {
                }
                """,
                """
                package org.springframework.boot.context.properties.bind;
                public @interface DefaultValue {
                    String[] value() default {};
                }
                """,
                """
                package javax.servlet;
                public interface Filter {
                }
                """,
                """
                package jakarta.servlet;
                public interface Filter {
                }
                """,
                """
                package org.springframework.boot.task;
                public class TaskSchedulerBuilder {
                    public TaskSchedulerBuilder poolSize(int size) {
                        return this;
                    }
                }
                """,
                """
                package org.springframework.boot.task;
                public class ThreadPoolTaskSchedulerBuilder {
                    public ThreadPoolTaskSchedulerBuilder poolSize(int size) {
                        return this;
                    }
                }
                """,
                """
                package org.springframework.boot.actuate.health;
                public class ApplicationHealthIndicator {
                }
                """,
                """
                package org.springframework.boot.actuate.health;
                public class PingHealthIndicator {
                }
                """,
                """
                package org.springframework.boot.test.autoconfigure.web.reactive;
                public interface WebTestClientBuilderCustomizer {
                }
                """,
                """
                package org.springframework.boot.test.web.reactive.server;
                public interface WebTestClientBuilderCustomizer {
                }
                """,
                """
                package org.springframework.boot.test.rule;
                public class OutputCapture {
                }
                """,
                """
                package org.springframework.boot.test.system;
                public class OutputCaptureRule {
                }
                """,
                """
                package org.springframework.boot.autoconfigure.elasticsearch.rest;
                public interface RestClientBuilderCustomizer {
                }
                """,
                """
                package org.springframework.boot.autoconfigure.elasticsearch;
                public interface RestClientBuilderCustomizer {
                }
                """,
                """
                package org.springframework.boot.web.embedded.undertow;
                public class UndertowServletWebServerFactory {
                    public boolean isEagerInitFilters() {
                        return false;
                    }
                    public void setEagerInitFilters(boolean eager) {
                    }
                    public boolean isEagerFilterInit() {
                        return false;
                    }
                    public void setEagerFilterInit(boolean eager) {
                    }
                }
                """,
                """
                package org.springframework.boot.autoconfigure.data.jpa;
                public class EntityManagerFactoryDependsOnPostProcessor {
                }
                """,
                """
                package org.springframework.boot.autoconfigure.orm.jpa;
                public class EntityManagerFactoryDependsOnPostProcessor {
                }
                """,
                """
                package org.springframework.boot.autoconfigure.web;
                public class ResourceProperties {
                }
                """,
                """
                package org.springframework.boot.autoconfigure.web;
                public class WebProperties {
                    public static class Resources {
                    }
                }
                """,
                """
                package org.springframework.context.annotation;
                public @interface Bean {
                }
                """,
                """
                package org.springframework.context.annotation;
                public @interface Configuration {
                }
                """,
                """
                package org.springframework.boot.sql.init.dependency;
                public @interface DependsOnDatabaseInitialization {
                }
                """,
                """
                package org.springframework.data.rest.webmvc.config;
                public class RepositoryRestConfigurerAdapter {
                }
                """,
                """
                package org.springframework.data.rest.webmvc.config;
                public interface RepositoryRestConfigurer {
                }
                """);
    }

    private static Recipe recipe() {
        return recipe(RECIPE);
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static Recipe selectedSourceVisitorsRecipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(
                PREFIX + "MarkSelectedSpringBootProjects",
                PREFIX + "MigrateSelectedSpringBootSourceVisitors");
    }

    private static String bootPom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(version);
    }
}
