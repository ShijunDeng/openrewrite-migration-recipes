package com.huawei.clouds.openrewrite.mybatisspring;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class UpgradeMyBatisSpringTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.mybatisspring.UpgradeMyBatisSpringDependencyTo4_0_0";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.mybatisspring.MigrateMyBatisSpringTo4_0_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE))
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package org.springframework.batch.item;
                        public interface ItemWriter<T> {}
                        """,
                        """
                        package org.springframework.batch.item.database;
                        public abstract class AbstractPagingItemReader<T> {}
                        """,
                        """
                        package org.springframework.batch.poller;
                        public interface Poller<T> {}
                        """,
                        """
                        package org.springframework.batch.repeat;
                        public enum RepeatStatus { CONTINUABLE, FINISHED }
                        """,
                        """
                        package org.springframework.batch.support.transaction;
                        public class ResourcelessTransactionManager {}
                        """,
                        """
                        package org.mybatis.spring.annotation;
                        public @interface MapperScan {
                            String[] value() default {};
                            String[] basePackages() default {};
                            String sqlSessionFactoryRef() default "";
                        }
                        """,
                        """
                        package org.mybatis.spring.batch;
                        public class MyBatisBatchItemWriter<T> {}
                        """,
                        """
                        package org.mybatis.spring.batch.builder;
                        public class MyBatisBatchItemWriterBuilder<T> {
                            public MyBatisBatchItemWriterBuilder<T> statementId(String statementId) { return this; }
                        }
                        """
                ));
    }

    @Test
    void upgradesAbel533ManagedPropertyFrom131() {
        // Reduced from abel533/mapper-boot-starter at 5210a16c:
        // https://github.com/abel533/mapper-boot-starter/blob/5210a16cad675b09f70b8d26198e1d9532b0585f/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>tk.mybatis</groupId><artifactId>mapper-spring-boot</artifactId><version>1</version>
                  <properties><mybatis-spring.version>1.3.1</mybatis-spring.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId>
                    <version>${mybatis-spring.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>tk.mybatis</groupId><artifactId>mapper-spring-boot</artifactId><version>1</version>
                  <properties><mybatis-spring.version>4.0.0</mybatis-spring.version></properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId>
                    <version>${mybatis-spring.version}</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesDirectMavenDependencyFrom204() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>direct</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>2.0.4</version></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>direct</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>4.0.0</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesHotswapAgentProvidedPropertyFrom207() {
        // Reduced from HotswapProjects/HotswapAgent at 7fc5e6d6:
        // https://github.com/HotswapProjects/HotswapAgent/blob/7fc5e6d6bf311ebf447d839b8b8c32dbd8c26bc2/plugin/hotswap-agent-mybatis-plugin/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>org.hotswapagent</groupId><artifactId>mybatis-plugin</artifactId><version>1</version>
                  <properties><org.mybatis-spring.version>2.0.7</org.mybatis-spring.version></properties>
                  <dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>${org.mybatis-spring.version}</version><scope>provided</scope></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>org.hotswapagent</groupId><artifactId>mybatis-plugin</artifactId><version>1</version>
                  <properties><org.mybatis-spring.version>4.0.0</org.mybatis-spring.version></properties>
                  <dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>${org.mybatis-spring.version}</version><scope>provided</scope></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesDependencyManagementFrom210() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>2.1.0</version></dependency></dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>4.0.0</version></dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesRuntimeDependencyFrom211() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>runtime</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>2.1.1</version><scope>runtime</scope></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>runtime</artifactId><version>1</version>
                  <dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>4.0.0</version><scope>runtime</scope></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesSpringDataRelationalStylePropertyFrom302() {
        // Reduced from spring-projects/spring-data-relational at 5e41e741:
        // https://github.com/spring-projects/spring-data-relational/blob/5e41e7419f432d7d1660f5666542cbb527fd3d8d/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>org.springframework.data</groupId><artifactId>spring-data-relational</artifactId><version>1</version>
                  <properties><mybatis-spring.version>3.0.2</mybatis-spring.version></properties>
                  <dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>${mybatis-spring.version}</version><scope>test</scope></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>org.springframework.data</groupId><artifactId>spring-data-relational</artifactId><version>1</version>
                  <properties><mybatis-spring.version>4.0.0</mybatis-spring.version></properties>
                  <dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>${mybatis-spring.version}</version><scope>test</scope></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesEasybestGradleApiDependency() {
        // Reduced from easybest/spring-data-mybatis at 773d8494:
        // https://github.com/easybest/spring-data-mybatis/blob/773d8494ea4a8b66c785e4eaaa0c0eb35e5c4199/main/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    api('org.mybatis:mybatis:3.5.11')
                    api('org.mybatis:mybatis-spring:2.0.7')
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    api('org.mybatis:mybatis:3.5.11')
                    api('org.mybatis:mybatis-spring:4.0.0')
                }
                """
        ));
    }

    @Test
    void upgradesDoubleQuotedGradleDependencyFrom301() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation("org.mybatis:mybatis-spring:3.0.1") }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation("org.mybatis:mybatis-spring:4.0.0") }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation group: 'org.mybatis', name: 'mybatis-spring', version: '3.0.2' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation group: 'org.mybatis', name: 'mybatis-spring', version: '4.0.0' }
                """
        ));
    }

    @Test
    void leavesKotlinGradleDependencyUntouchedWithoutSemanticModel() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { implementation("org.mybatis:mybatis-spring:2.1.1") }
                """
        ));
    }

    @Test
    void leavesTargetAndNewerVersionsUntouched() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>target</artifactId><version>1</version><dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>4.0.0</version></dependency></dependencies></project>
                        """),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>newer-minor</artifactId><version>1</version><dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>4.1.0</version></dependency></dependencies></project>
                        """, spec -> spec.path("newer-minor-pom.xml"))
        );
    }

    @Test
    void leavesVersionsOutsideTheSpreadsheetMatrixUntouched() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>old-unlisted</artifactId><version>1</version><dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>3.0.0</version></dependency></dependencies></project>
                        """),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>nearby-patch</artifactId><version>1</version><dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>2.0.6</version></dependency></dependencies></project>
                        """, spec -> spec.path("nearby-patch-pom.xml"))
        );
    }

    @Test
    void isolatesAPropertySharedWithAnotherDependency() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>shared-property</artifactId><version>1</version>
                  <properties><shared.version>2.0.7</shared.version></properties>
                  <dependencies>
                    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>${shared.version}</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${shared.version}</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>shared-property</artifactId><version>1</version>
                  <properties><shared.version>2.0.7</shared.version></properties>
                  <dependencies>
                    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>4.0.0</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${shared.version}</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void isolatesAPropertyEmbeddedInUnrelatedProjectMetadata() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>embedded-shared-property</artifactId><version>1</version>
                  <name>release-${shared.version}</name>
                  <properties><shared.version>2.0.7</shared.version></properties>
                  <dependencies>
                    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>${shared.version}</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>embedded-shared-property</artifactId><version>1</version>
                  <name>release-${shared.version}</name>
                  <properties><shared.version>2.0.7</shared.version></properties>
                  <dependencies>
                    <dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId><version>4.0.0</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void doesNotOverrideAnExternallyManagedVersion() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>externally-managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot</artifactId>
                    <version>2.2.2</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>org.mybatis</groupId><artifactId>mybatis-spring</artifactId></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void leavesCompanionAndSimilarArtifactsUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>companions</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.mybatis</groupId><artifactId>mybatis</artifactId><version>3.5.19</version></dependency>
                  <dependency><groupId>org.mybatis.spring.boot</groupId><artifactId>mybatis-spring-boot-starter</artifactId><version>3.0.4</version></dependency>
                  <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-spring</artifactId><version>3.5.12</version></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void leavesUnresolvedGradleInterpolationUntouched() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                ext { mybatisSpringVersion = providers.gradleProperty('mybatisSpringVersion') }
                dependencies { implementation "org.mybatis:mybatis-spring:${mybatisSpringVersion}" }
                """
        ));
    }

    @Test
    void preservesOfficialMyBatisSpringNamespaceConfiguration() {
        // Reduced from mybatis/spring's official 1.3.1 namespace sample:
        // https://github.com/mybatis/spring/blob/9cb7b928b6a2b4626b1a0769327f8698be97d318/src/test/java/org/mybatis/spring/sample/config/applicationContext-namespace.xml
        rewriteRun(xml(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:mybatis="http://mybatis.org/schema/mybatis-spring"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
                                           http://mybatis.org/schema/mybatis-spring http://mybatis.org/schema/mybatis-spring.xsd">
                  <mybatis:scan base-package="org.mybatis.spring.sample.mapper"/>
                </beans>
                """
        ));
    }

    @Test
    void preservesMapperXmlStatements() {
        rewriteRun(xml(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
                <mapper namespace="example.UserMapper">
                  <select id="findById" parameterType="long" resultType="example.User">
                    select id, display_name from users where id = #{id}
                  </select>
                </mapper>
                """
        ));
    }

    @Test
    void dependencyOnlyRecipePreservesMapperScanSource() {
        rewriteRun(java(
                """
                import org.mybatis.spring.annotation.MapperScan;

                @MapperScan(basePackages = "example.mapper", sqlSessionFactoryRef = "ordersSqlSessionFactory")
                class MyBatisConfiguration {}
                """
        ));
    }

    @Test
    void migrationRecipeMovesSpringBatchItemPackages() {
        // Shape reduced from eGovFramework/egovframe-runtime's MyBatis batch writer:
        // https://github.com/eGovFramework/egovframe-runtime/blob/77f3fa781bb19754ea4186c88554df4d67e03d07/Batch/org.egovframe.rte.bat.core/src/main/java/org/egovframe/rte/bat/core/item/database/EgovMyBatisBatchItemWriter.java
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import org.springframework.batch.item.ItemWriter;
                        import org.springframework.batch.item.database.AbstractPagingItemReader;

                        abstract class MyBatisPagingReader<T> extends AbstractPagingItemReader<T> {}
                        interface MyBatisWriter<T> extends ItemWriter<T> {}
                        """,
                        """
                        import org.springframework.batch.infrastructure.item.ItemWriter;
                        import org.springframework.batch.infrastructure.item.database.AbstractPagingItemReader;

                        abstract class MyBatisPagingReader<T> extends AbstractPagingItemReader<T> {}
                        interface MyBatisWriter<T> extends ItemWriter<T> {}
                        """
                )
        );
    }

    @Test
    void migrationRecipeMovesRepeatSupportAndPollerPackages() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import org.springframework.batch.poller.Poller;
                        import org.springframework.batch.repeat.RepeatStatus;
                        import org.springframework.batch.support.transaction.ResourcelessTransactionManager;

                        class BatchInfrastructure {
                            Poller<String> poller;
                            RepeatStatus status = RepeatStatus.FINISHED;
                            ResourcelessTransactionManager transactionManager;
                        }
                        """,
                        """
                        import org.springframework.batch.infrastructure.poller.Poller;
                        import org.springframework.batch.infrastructure.repeat.RepeatStatus;
                        import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;

                        class BatchInfrastructure {
                            Poller<String> poller;
                            RepeatStatus status = RepeatStatus.FINISHED;
                            ResourcelessTransactionManager transactionManager;
                        }
                        """
                )
        );
    }

    @Test
    void migrationRecipePreservesMyBatisBatchBuilderApi() {
        // Reduced from Macchinetta's real MyBatis batch configuration at 67547867:
        // https://github.com/Macchinetta/macchinetta-batch-functionaltest/blob/675478671ee45ee1f3df8dcca8a73cb887c85f04/macchinetta-batch-functionaltest-app/src/main/java/jp/co/ntt/fw/macchinetta/batch/functionaltest/jobs/ch05/dbaccess/DBAccessByItemWriterConfig.java
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import org.mybatis.spring.annotation.MapperScan;
                        import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;

                        @MapperScan(basePackages = "example.repository", sqlSessionFactoryRef = "jobSqlSessionFactory")
                        class BatchConfiguration {
                            MyBatisBatchItemWriterBuilder<String> writer() {
                                return new MyBatisBatchItemWriterBuilder<String>().statementId("example.Repository.insert");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = environment();
        Recipe dependencyRecipe = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe migrationRecipe = environment.activateRecipes(MIGRATION_RECIPE);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> DEPENDENCY_RECIPE.equals(candidate.getName())));
        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> MIGRATION_RECIPE.equals(candidate.getName())));
        assertTrue(dependencyRecipe.validate().isValid(), () -> dependencyRecipe.validate().failures().toString());
        assertTrue(migrationRecipe.validate().isValid(), () -> migrationRecipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.mybatisspring")
                .scanYamlResources()
                .build();
    }
}
