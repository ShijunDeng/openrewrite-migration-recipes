package com.huawei.clouds.openrewrite.mybatisspring;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MyBatisSpringJavaRisksTest implements RewriteTest {
    private static final String ALIAS_MESSAGE =
            "@MapperScan value and basePackages are @AliasFor aliases; keep only one after verifying packages";
    private static final String SESSION_MESSAGE =
            "@MapperScan specifies both factory and template references; select one session boundary explicitly";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindMyBatisSpringJavaRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package org.mybatis.spring.annotation;
                        public @interface MapperScan {
                            String[] value() default {};
                            String[] basePackages() default {};
                            String sqlSessionFactoryRef() default "";
                            String sqlSessionTemplateRef() default "";
                        }
                        """,
                        """
                        package org.springframework.batch.core.configuration.annotation;
                        public @interface EnableBatchProcessing {}
                        """,
                        """
                        package org.springframework.beans.factory.support;
                        public interface BeanDefinitionRegistry {}
                        """,
                        """
                        package org.springframework.core.env;
                        public interface Environment {}
                        """,
                        """
                        package org.mybatis.spring;
                        public class MyBatisSystemException extends RuntimeException {
                            public MyBatisSystemException(Throwable cause) { super(cause); }
                            public MyBatisSystemException(String message, Throwable cause) { super(message, cause); }
                        }
                        """,
                        """
                        package org.mybatis.spring;
                        public class SqlSessionFactory {}
                        """,
                        """
                        package org.mybatis.spring;
                        public class SqlSessionTemplate {}
                        """,
                        """
                        package org.mybatis.spring.mapper;
                        public class MapperFactoryBean<T> {}
                        """,
                        """
                        package org.mybatis.spring.mapper;
                        import org.springframework.beans.factory.support.BeanDefinitionRegistry;
                        import org.springframework.core.env.Environment;
                        public class ClassPathMapperScanner {
                            public ClassPathMapperScanner(BeanDefinitionRegistry registry) {}
                            public ClassPathMapperScanner(BeanDefinitionRegistry registry, Environment environment) {}
                            public void setMapperFactoryBean(MapperFactoryBean<?> factoryBean) {}
                            public void setMapperFactoryBeanClass(Class<? extends MapperFactoryBean> factoryBeanClass) {}
                        }
                        """,
                        """
                        package org.mybatis.spring.mapper;
                        import org.mybatis.spring.SqlSessionFactory;
                        import org.mybatis.spring.SqlSessionTemplate;
                        public class MapperScannerConfigurer {
                            public void setSqlSessionFactory(SqlSessionFactory factory) {}
                            public void setSqlSessionTemplate(SqlSessionTemplate template) {}
                            public void setSqlSessionFactoryBeanName(String name) {}
                            public void setSqlSessionTemplateBeanName(String name) {}
                        }
                        """,
                        """
                        package org.mybatis.spring.batch;
                        import java.util.List;
                        public class MyBatisBatchItemWriter<T> {
                            public void write(List<? extends T> items) {}
                        }
                        """,
                        """
                        package javax.persistence;
                        public interface EntityManager {}
                        """,
                        """
                        package javax.validation;
                        public interface Validator {}
                        """,
                        """
                        package javax.servlet;
                        public interface Filter {}
                        """,
                        """
                        package javax.transaction;
                        public interface UserTransaction {}
                        """
                ));
    }

    @Test
    void marksMapperScanAliasConflict() {
        rewriteRun(java(
                """
                import org.mybatis.spring.annotation.MapperScan;

                @MapperScan(value = "example.one", basePackages = "example.two")
                class MapperConfiguration {}
                """,
                """
                import org.mybatis.spring.annotation.MapperScan;

                /*~~(%s)~~>*/@MapperScan(value = "example.one", basePackages = "example.two")
                class MapperConfiguration {}
                """.formatted(ALIAS_MESSAGE)
        ));
    }

    @Test
    void marksMapperScanSessionReferenceConflict() {
        rewriteRun(java(
                """
                import org.mybatis.spring.annotation.MapperScan;

                @MapperScan(basePackages = "example", sqlSessionFactoryRef = "factory", sqlSessionTemplateRef = "template")
                class MapperConfiguration {}
                """,
                """
                import org.mybatis.spring.annotation.MapperScan;

                /*~~(%s)~~>*/@MapperScan(basePackages = "example", sqlSessionFactoryRef = "factory", sqlSessionTemplateRef = "template")
                class MapperConfiguration {}
                """.formatted(SESSION_MESSAGE)
        ));
    }

    @Test
    void marksConstructorsScheduledForRemoval() {
        rewriteRun(java(
                """
                import org.mybatis.spring.MyBatisSystemException;
                import org.mybatis.spring.mapper.ClassPathMapperScanner;
                import org.springframework.beans.factory.support.BeanDefinitionRegistry;

                class Configuration {
                    void configure(BeanDefinitionRegistry registry, Throwable failure) {
                        ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);
                        RuntimeException exception = new MyBatisSystemException(failure);
                    }
                }
                """,
                """
                import org.mybatis.spring.MyBatisSystemException;
                import org.mybatis.spring.mapper.ClassPathMapperScanner;
                import org.springframework.beans.factory.support.BeanDefinitionRegistry;

                class Configuration {
                    void configure(BeanDefinitionRegistry registry, Throwable failure) {
                        ClassPathMapperScanner scanner = /*~~(The one-argument ClassPathMapperScanner constructor is for removal; supply the matching Spring Environment explicitly)~~>*/new ClassPathMapperScanner(registry);
                        RuntimeException exception = /*~~(The one-argument MyBatisSystemException constructor is for removal; preserve the intended message with the message/cause constructor)~~>*/new MyBatisSystemException(failure);
                    }
                }
                """
        ));
    }

    @Test
    void leavesReplacementConstructorsUnmarked() {
        rewriteRun(java(
                """
                import org.mybatis.spring.MyBatisSystemException;
                import org.mybatis.spring.mapper.ClassPathMapperScanner;
                import org.springframework.beans.factory.support.BeanDefinitionRegistry;
                import org.springframework.core.env.Environment;

                class Configuration {
                    void configure(BeanDefinitionRegistry registry, Environment environment, Throwable failure) {
                        ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry, environment);
                        RuntimeException exception = new MyBatisSystemException("query failed", failure);
                    }
                }
                """
        ));
    }

    @Test
    void migratesStableMyBatisSystemExceptionCauseWithoutChangingItsMessage() {
        rewriteRun(
                spec -> spec.recipe(new MigrateMyBatisSpringJava()),
                java(
                        """
                        import org.mybatis.spring.MyBatisSystemException;

                        class ExceptionTranslator {
                            RuntimeException translate(Throwable failure) {
                                return new MyBatisSystemException(failure);
                            }
                        }
                        """,
                        """
                        import org.mybatis.spring.MyBatisSystemException;

                        class ExceptionTranslator {
                            RuntimeException translate(Throwable failure) {
                                return new MyBatisSystemException(failure.getMessage(), failure);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksSideEffectingMyBatisSystemExceptionCauseInsteadOfDuplicatingIt() {
        rewriteRun(
                spec -> spec.recipes(new MigrateMyBatisSpringJava(), new FindMyBatisSpringJavaRisks()),
                java(
                        """
                        import org.mybatis.spring.MyBatisSystemException;

                        class ExceptionTranslator {
                            Throwable nextFailure() { return new IllegalStateException(); }
                            RuntimeException translate() {
                                return new MyBatisSystemException(nextFailure());
                            }
                        }
                        """,
                        """
                        import org.mybatis.spring.MyBatisSystemException;

                        class ExceptionTranslator {
                            Throwable nextFailure() { return new IllegalStateException(); }
                            RuntimeException translate() {
                                return /*~~(The one-argument MyBatisSystemException constructor is for removal; preserve the intended message with the message/cause constructor)~~>*/new MyBatisSystemException(nextFailure());
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksDeprecatedScannerObjectSetters() {
        rewriteRun(java(
                """
                import org.mybatis.spring.SqlSessionFactory;
                import org.mybatis.spring.SqlSessionTemplate;
                import org.mybatis.spring.mapper.MapperScannerConfigurer;

                class Configuration {
                    void configure(MapperScannerConfigurer scanner, SqlSessionFactory factory, SqlSessionTemplate template) {
                        scanner.setSqlSessionFactory(factory);
                        scanner.setSqlSessionTemplate(template);
                    }
                }
                """,
                """
                import org.mybatis.spring.SqlSessionFactory;
                import org.mybatis.spring.SqlSessionTemplate;
                import org.mybatis.spring.mapper.MapperScannerConfigurer;

                class Configuration {
                    void configure(MapperScannerConfigurer scanner, SqlSessionFactory factory, SqlSessionTemplate template) {
                        /*~~(MapperScannerConfigurer object injection is deprecated and can initialize too early; use the corresponding bean-name setter)~~>*/scanner.setSqlSessionFactory(factory);
                        /*~~(MapperScannerConfigurer object injection is deprecated and can initialize too early; use the corresponding bean-name setter)~~>*/scanner.setSqlSessionTemplate(template);
                    }
                }
                """
        ));
    }

    @Test
    void marksMapperFactoryBeanInstanceSetterOnly() {
        rewriteRun(java(
                """
                import org.mybatis.spring.mapper.ClassPathMapperScanner;
                import org.mybatis.spring.mapper.MapperFactoryBean;

                class Configuration {
                    void configure(ClassPathMapperScanner scanner, MapperFactoryBean<?> factoryBean) {
                        scanner.setMapperFactoryBean(factoryBean);
                        scanner.setMapperFactoryBeanClass(MapperFactoryBean.class);
                    }
                }
                """,
                """
                import org.mybatis.spring.mapper.ClassPathMapperScanner;
                import org.mybatis.spring.mapper.MapperFactoryBean;

                class Configuration {
                    void configure(ClassPathMapperScanner scanner, MapperFactoryBean<?> factoryBean) {
                        /*~~(setMapperFactoryBean is for removal; migrate to setMapperFactoryBeanClass after reviewing instance state)~~>*/scanner.setMapperFactoryBean(factoryBean);
                        scanner.setMapperFactoryBeanClass(MapperFactoryBean.class);
                    }
                }
                """
        ));
    }

    @Test
    void marksLegacyBatchWriterListOverride() {
        rewriteRun(java(
                """
                import java.util.List;
                import org.mybatis.spring.batch.MyBatisBatchItemWriter;

                class AuditingWriter extends MyBatisBatchItemWriter<String> {
                    @Override
                    public void write(List<? extends String> items) {
                        super.write(items);
                    }
                }
                """,
                """
                import java.util.List;
                import org.mybatis.spring.batch.MyBatisBatchItemWriter;

                class AuditingWriter extends MyBatisBatchItemWriter<String> {
                    /*~~(Spring Batch changed ItemWriter.write from List to Chunk; migrate this override and review chunk/error semantics)~~>*/@Override
                    public void write(List<? extends String> items) {
                        super.write(items);
                    }
                }
                """
        ));
    }

    @Test
    void marksSpringBatchInfrastructureReview() {
        rewriteRun(java(
                """
                import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;

                @EnableBatchProcessing
                class BatchConfiguration {}
                """,
                """
                import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;

                /*~~(Spring Batch 6 changes repository defaults and restart metadata; review data source, transaction manager, and failed executions)~~>*/@EnableBatchProcessing
                class BatchConfiguration {}
                """
        ));
    }

    @Test
    void marksJavaxEeTypesButNotJavaSeOrXaTypes() {
        rewriteRun(java(
                """
                import javax.persistence.EntityManager;
                import javax.sql.DataSource;
                import javax.transaction.UserTransaction;
                import javax.transaction.xa.XAResource;

                class LegacyPlatformTypes {
                    EntityManager entityManager;
                    UserTransaction transaction;
                    DataSource dataSource;
                    XAResource xaResource;
                }
                """,
                """
                import javax.persistence.EntityManager;
                import javax.sql.DataSource;
                import javax.transaction.UserTransaction;
                import javax.transaction.xa.XAResource;

                class LegacyPlatformTypes {
                    /*~~(Spring Framework 7 uses Jakarta APIs; migrate this javax EE type and dependency with the platform)~~>*/EntityManager entityManager;
                    /*~~(Spring Framework 7 uses Jakarta APIs; migrate this javax EE type and dependency with the platform)~~>*/UserTransaction transaction;
                    DataSource dataSource;
                    XAResource xaResource;
                }
                """
        ));
    }

    @Test
    void leavesCompatibleMapperScanAndBeanNameSettersUnmarked() {
        rewriteRun(java(
                """
                import org.mybatis.spring.annotation.MapperScan;
                import org.mybatis.spring.mapper.MapperScannerConfigurer;

                @MapperScan(basePackages = "example", sqlSessionFactoryRef = "factory")
                class Configuration {
                    void configure(MapperScannerConfigurer scanner) {
                        scanner.setSqlSessionFactoryBeanName("factory");
                        scanner.setSqlSessionTemplateBeanName("template");
                    }
                }
                """
        ));
    }
}
