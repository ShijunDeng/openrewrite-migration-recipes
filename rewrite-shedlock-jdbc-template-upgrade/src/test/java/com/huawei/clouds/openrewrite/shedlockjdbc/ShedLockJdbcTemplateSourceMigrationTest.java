package com.huawei.clouds.openrewrite.shedlockjdbc;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class ShedLockJdbcTemplateSourceMigrationTest implements RewriteTest {
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.shedlockjdbc.MigrateShedLockJdbcTemplateTo7_2_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(shedLockParser()).typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesOfficialDatabaseProductPackageMove() {
        // ShedLock 7.2.1 tag, fixed commit f79462aa33d864ca7e877dc9494dbb9b7ab05518.
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        package example;
                        import net.javacrumbs.shedlock.provider.jdbctemplate.DatabaseProduct;
                        class Config { DatabaseProduct product = DatabaseProduct.POSTGRES_SQL; }
                        """,
                        """
                        package example;

                        import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;

                        class Config { DatabaseProduct product = DatabaseProduct.POSTGRES_SQL; }
                        """
                )
        );
    }

    @Test
    void migratesExactUtcTimeZoneToForceUtcTimeZone() {
        rewriteRun(
                sourceSpec(),
                java(
                        """
                        import java.util.TimeZone;
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        class Config { void configure(JdbcTemplateLockProvider.Configuration.Builder b) { b.withTimeZone(TimeZone.getTimeZone("UTC")); } }
                        """,
                        """
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        class Config { void configure(JdbcTemplateLockProvider.Configuration.Builder b) { b.forceUtcTimeZone(); } }
                        """
                )
        );
    }

    @Test
    void utcMigrationIsIdempotent() {
        rewriteRun(
                spec -> {
                    sourceSpec().accept(spec);
                    spec.cycles(2).expectedCyclesThatMakeChanges(1);
                },
                java(
                        """
                        import java.util.TimeZone;
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        class Config { void configure(JdbcTemplateLockProvider.Configuration.Builder b) { b.withTimeZone(TimeZone.getTimeZone("UTC")); } }
                        """,
                        """
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        class Config { void configure(JdbcTemplateLockProvider.Configuration.Builder b) { b.forceUtcTimeZone(); } }
                        """
                )
        );
    }

    @Test
    void preservesNonUtcDynamicAndUntypedLookalikeCalls() {
        rewriteRun(
                sourceSpec(),
                java(
                        """
                        import java.util.TimeZone;
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        class Config {
                            void configure(JdbcTemplateLockProvider.Configuration.Builder b, TimeZone zone) {
                                b.withTimeZone(TimeZone.getTimeZone("UTC-08"));
                                b.withTimeZone(zone);
                                new Lookalike().withTimeZone(TimeZone.getTimeZone("UTC"));
                            }
                            static class Lookalike { Lookalike withTimeZone(TimeZone zone) { return this; } }
                        }
                        """
                )
        );
    }

    @Test
    void marksRieckpilRealUsingDbTimeConfiguration() {
        // rieckpil/blog-tutorials, fixed commit cc20cab53eeb73c404b9bcc4a22b169571f4b403.
        rewriteRun(
                riskSpec(),
                java(
                        """
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        class ShedLockConfig { void configure(JdbcTemplateLockProvider.Configuration.Builder b) { b.usingDbTime(); } }
                        """,
                        """
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        class ShedLockConfig { void configure(JdbcTemplateLockProvider.Configuration.Builder b) { /*~~(usingDbTime is database-specific and strongly preferred for clock consistency; verify the selected dialect, privileges and DB UTC behavior)~~>*/b.usingDbTime(); } }
                        """
                )
        );
    }

    @Test
    void marksChaosbladeRealTimezoneTransactionAndSchemaConfiguration() {
        // chaosblade-io/chaosblade-box, fixed commit 7a446db75fee6124d78e9033658af916fdf1224f.
        rewriteRun(
                riskSpec(),
                java(
                        """
                        import java.util.TimeZone;
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        class ServerConfiguration { void configure(JdbcTemplateLockProvider.Configuration.Builder b) {
                            b.withTransactionManager(new Object());
                            b.withTimeZone(TimeZone.getTimeZone("UTC-08"));
                            b.withTableName("t_chaos_distribute_lock");
                        } }
                        """,
                        """
                        import java.util.TimeZone;
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        class ServerConfiguration { void configure(JdbcTemplateLockProvider.Configuration.Builder b) {
                            /*~~(Retest ShedLock REQUIRES_NEW transaction boundaries, configured isolation, pool capacity and rollback/deadlock behavior)~~>*/b.withTransactionManager(new Object());
                            /*~~(Non-literal or non-UTC withTimeZone requires a deliberate timezone review; forceUtcTimeZone is the 7.2.1 replacement for UTC)~~>*/b.withTimeZone(TimeZone.getTimeZone("UTC-08"));
                            /*~~(Custom ShedLock table/columns/case/database product detected; validate identifier quoting, schema migration and timestamp precision on the target database)~~>*/b.withTableName("t_chaos_distribute_lock");
                        } }
                        """,
                        source -> source.afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("REQUIRES_NEW transaction boundaries"));
                            assertTrue(printed.contains("deliberate timezone review"));
                            assertTrue(printed.contains("Custom ShedLock table/columns"));
                        })
                )
        );
    }

    @Test
    void marksIsolationAndDatabaseProductDecisions() {
        rewriteRun(
                riskSpec(),
                java(
                        """
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        import net.javacrumbs.shedlock.provider.jdbctemplate.DatabaseProduct;
                        class Config { void configure(JdbcTemplateLockProvider.Configuration.Builder b) {
                            b.withIsolationLevel(8); b.withDatabaseProduct(DatabaseProduct.POSTGRES_SQL);
                        } }
                        """,
                        """
                        import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
                        import net.javacrumbs.shedlock.provider.jdbctemplate.DatabaseProduct;
                        class Config { void configure(JdbcTemplateLockProvider.Configuration.Builder b) {
                            /*~~(Retest ShedLock REQUIRES_NEW transaction boundaries, configured isolation, pool capacity and rollback/deadlock behavior)~~>*/b.withIsolationLevel(8); /*~~(Custom ShedLock table/columns/case/database product detected; validate identifier quoting, schema migration and timestamp precision on the target database)~~>*/b.withDatabaseProduct(DatabaseProduct.POSTGRES_SQL);
                        } }
                        """,
                        source -> source.afterRecipe(after -> {
                            assertTrue(after.printAll().contains("configured isolation"));
                            assertTrue(after.printAll().contains("timestamp precision"));
                        })
                )
        );
    }

    @Test
    void marksLockExceptionBoundary() {
        rewriteRun(
                riskSpec(),
                java(
                        """
                        import net.javacrumbs.shedlock.core.LockProvider;
                        class Runner { void run(LockProvider provider) { provider.lock("job"); } }
                        """,
                        """
                        import net.javacrumbs.shedlock.core.LockProvider;
                        class Runner { void run(LockProvider provider) { /*~~(ShedLock 7 providers throw LockException on unexpected failures; distinguish provider errors from an unavailable lock and review retry/catch behavior)~~>*/provider.lock("job"); } }
                        """
                )
        );
    }

    @Test
    void marksSchedulerLockAndProviderSelectionSemantics() {
        rewriteRun(
                riskSpec(),
                java(
                        """
                        import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                        import net.javacrumbs.shedlock.spring.annotation.LockProviderToUse;
                        class Jobs { @SchedulerLock(name = "billing") @LockProviderToUse("primary") void bill() {} }
                        """,
                        """
                        import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                        import net.javacrumbs.shedlock.spring.annotation.LockProviderToUse;
                        class Jobs { /*~~(Verify lock name/durations and Spring proxy semantics; final/non-public/self-invoked methods and reactive scheduling require integration tests)~~>*/@SchedulerLock(name = "billing") /*~~(Verify the named LockProvider exists and is selected for every scheduled path; missing selection fails when multiple providers are present)~~>*/@LockProviderToUse("primary") void bill() {} }
                        """,
                        source -> source.afterRecipe(after -> {
                            assertTrue(after.printAll().contains("lock name/durations"));
                            assertTrue(after.printAll().contains("named LockProvider exists"));
                        })
                )
        );
    }

    @Test
    void marksMultipleLockProviderDefinitions() {
        rewriteRun(
                riskSpec(),
                java(
                        """
                        import net.javacrumbs.shedlock.core.LockProvider;
                        class Providers {
                            LockProvider primary() { return null; }
                            LockProvider secondary() { return null; }
                        }
                        """,
                        """
                        import net.javacrumbs.shedlock.core.LockProvider;
                        class Providers {
                            /*~~(Multiple LockProvider definitions detected; bind scheduled methods/classes explicitly with @LockProviderToUse and test provider selection)~~>*/LockProvider primary() { return null; }
                            /*~~(Multiple LockProvider definitions detected; bind scheduled methods/classes explicitly with @LockProviderToUse and test provider selection)~~>*/LockProvider secondary() { return null; }
                        }
                        """,
                        source -> source.afterRecipe(after ->
                                assertTrue(after.printAll().contains("Multiple LockProvider definitions detected")))
                )
        );
    }

    @Test
    void marksLegacyJakartaNamespace() {
        rewriteRun(
                riskSpec(),
                java(
                        """
                        import javax.annotation.PostConstruct;
                        class Config { @PostConstruct void init() {} }
                        """,
                        """
                        /*~~(ShedLock 7 requires Java 17 and Spring 6.2/7; verify the Jakarta namespace and framework baseline before compiling)~~>*/import javax.annotation.PostConstruct;
                        class Config { @PostConstruct void init() {} }
                        """,
                        source -> source.afterRecipe(after ->
                                assertTrue(after.printAll().contains("Jakarta namespace")))
                )
        );
    }

    @Test
    void marksJavaAndSpringBootBaselines() {
        rewriteRun(
                spec -> spec.recipe(new FindShedLockJdbcTemplate7MigrationRisks()),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion>
                          <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.2.5</version></parent>
                          <groupId>example</groupId><artifactId>legacy</artifactId><version>1</version><properties><java.version>11</java.version></properties>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion>
                          <!--~~(ShedLock 7 supports Spring Boot 3.4/3.5/4.x and Spring Framework 6.2/7; upgrade the framework baseline deliberately)~~>--><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.2.5</version></parent>
                          <groupId>example</groupId><artifactId>legacy</artifactId><version>1</version><properties><!--~~(ShedLock 7 requires Java 17 or newer)~~>--><java.version>11</java.version></properties>
                        </project>
                        """,
                        source -> source.path("pom.xml").afterRecipe(after -> {
                            assertTrue(after.printAll().contains("Java 17 or newer"));
                            assertTrue(after.printAll().contains("Spring Boot 3.4/3.5/4.x"));
                        })
                )
        );
    }

    @Test
    void marksGradleJavaToolchainBaselines() {
        rewriteRun(
                spec -> spec.recipe(new FindShedLockJdbcTemplate7MigrationRisks()),
                buildGradle(
                        "sourceCompatibility = JavaVersion.VERSION_11\njava { toolchain { languageVersion = JavaLanguageVersion.of(16) } }",
                        "/*~~(ShedLock 7 requires a Java 17+ Gradle toolchain and runtime)~~>*/sourceCompatibility = JavaVersion.VERSION_11\njava { toolchain { languageVersion = /*~~(ShedLock 7 requires a Java 17+ Gradle toolchain and runtime)~~>*/JavaLanguageVersion.of(16) } }"
                )
        );
    }

    @Test
    void marksDirectSpringFrameworkBaseline() {
        rewriteRun(
                spec -> spec.recipe(new FindShedLockJdbcTemplate7MigrationRisks()),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>spring</artifactId><version>1</version><dependencies>
                          <dependency><groupId>org.springframework</groupId><artifactId>spring-jdbc</artifactId><version>6.1.21</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>spring</artifactId><version>1</version><dependencies>
                          <!--~~(ShedLock 7 requires the Spring Framework 6.2/7 line; align Spring JDBC, transactions, context and Boot management together)~~>--><dependency><groupId>org.springframework</groupId><artifactId>spring-jdbc</artifactId><version>6.1.21</version></dependency>
                        </dependencies></project>
                        """,
                        source -> source.path("pom.xml")
                )
        );
    }

    @Test
    void marksUnalignedProviderFamily() {
        rewriteRun(
                spec -> spec.recipe(new FindShedLockJdbcTemplate7MigrationRisks()),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>mixed</artifactId><version>1</version><dependencies>
                          <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>4.44.0</version></dependency>
                          <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>7.2.1</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>mixed</artifactId><version>1</version><dependencies>
                          <!--~~(Align ShedLock core, Spring integration, BOM and all providers to one compatible 7.2.1 line; shared properties and external management are not changed automatically)~~>--><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>4.44.0</version></dependency>
                          <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>7.2.1</version></dependency>
                        </dependencies></project>
                        """,
                        source -> source.path("pom.xml").afterRecipe(after ->
                                assertTrue(after.printAll().contains("Align ShedLock core")))
                )
        );
    }

    @Test
    void marksUnsupportedSpringXmlConfiguration() {
        rewriteRun(
                spec -> spec.recipe(new FindShedLockJdbcTemplate7MigrationRisks()),
                xml(
                        """
                        <beans><bean id="lockProvider" class="net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider"/></beans>
                        """,
                        """
                        <beans><!--~~(ShedLock Spring XML configuration has been unsupported since 3.x; replace it with Java configuration before adopting 7.2.1)~~>--><bean id="lockProvider" class="net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider"/></beans>
                        """,
                        source -> source.path("src/main/resources/shedlock-context.xml")
                )
        );
    }

    @Test
    void marksSchemaAndTimezoneConfigurationResources() {
        rewriteRun(
                spec -> spec.recipe(new FindShedLockJdbcTemplate7MigrationRisks()),
                text(
                        "CREATE TABLE shedlock (name VARCHAR(64) PRIMARY KEY, lock_until TIMESTAMP, locked_at TIMESTAMP, locked_by VARCHAR(255));",
                        "~~(ShedLock schema detected; validate primary key width, timestamp precision/timezone, table/column names and privileges against the target database)~~>CREATE TABLE shedlock (name VARCHAR(64) PRIMARY KEY, lock_until TIMESTAMP, locked_at TIMESTAMP, locked_by VARCHAR(255));",
                        source -> source.path("src/main/resources/db/migration/V1__shedlock.sql")
                ),
                yaml(
                        "shedlock:\n  table: app_lock\n  timezone: UTC\n",
                        "~~(Verify ShedLock table/timezone configuration matches usingDbTime/forceUtcTimeZone and the deployed schema)~~>shedlock:\n  ~~(Verify ShedLock table/timezone configuration matches usingDbTime/forceUtcTimeZone and the deployed schema)~~>table: app_lock\n  ~~(Verify ShedLock table/timezone configuration matches usingDbTime/forceUtcTimeZone and the deployed schema)~~>timezone: UTC\n",
                        source -> source.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void ignoresUnrelatedSqlAndConfiguration() {
        rewriteRun(
                spec -> spec.recipe(new FindShedLockJdbcTemplate7MigrationRisks()),
                text("CREATE TABLE customer (id BIGINT PRIMARY KEY);", source -> source.path("src/main/resources/db/migration/V1__customer.sql")),
                yaml("spring:\n  datasource:\n    url: jdbc:h2:mem:test\n", source -> source.path("src/main/resources/application.yml"))
        );
    }

    private static Consumer<RecipeSpec> sourceSpec() {
        return spec -> spec.recipe(new MigrateShedLockJdbcTemplate7Source())
                .parser(shedLockParser()).typeValidationOptions(TypeValidation.none());
    }

    private static Consumer<RecipeSpec> riskSpec() {
        return spec -> spec.recipe(new FindShedLockJdbcTemplate7MigrationRisks())
                .parser(shedLockParser()).typeValidationOptions(TypeValidation.none());
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.shedlockjdbc")
                .scanYamlResources().build();
    }

    private static JavaParser.Builder<?, ?> shedLockParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package net.javacrumbs.shedlock.provider.jdbctemplate; public enum DatabaseProduct { POSTGRES_SQL, MYSQL, UNKNOWN }",
                "package net.javacrumbs.shedlock.provider.sql; public enum DatabaseProduct { POSTGRES_SQL, MYSQL, UNKNOWN }",
                """
                package net.javacrumbs.shedlock.provider.jdbctemplate;
                import java.util.TimeZone;
                public class JdbcTemplateLockProvider {
                    public JdbcTemplateLockProvider(Configuration c) {}
                    public static class Configuration {
                        public static Builder builder() { return new Builder(); }
                        public static class Builder {
                            public Builder withJdbcTemplate(Object v) { return this; }
                            public Builder withTransactionManager(Object v) { return this; }
                            public Builder withTableName(String v) { return this; }
                            public Builder withTimeZone(TimeZone v) { return this; }
                            public Builder forceUtcTimeZone() { return this; }
                            public Builder withColumnNames(Object v) { return this; }
                            public Builder withDbUpperCase(boolean v) { return this; }
                            public Builder withDatabaseProduct(DatabaseProduct v) { return this; }
                            public Builder usingDbTime() { return this; }
                            public Builder withIsolationLevel(int v) { return this; }
                            public Configuration build() { return new Configuration(); }
                        }
                    }
                }
                """,
                "package net.javacrumbs.shedlock.core; public interface LockProvider { Object lock(String name); }",
                "package net.javacrumbs.shedlock.spring.annotation; public @interface SchedulerLock { String name(); String lockAtMostFor() default \"\"; String lockAtLeastFor() default \"\"; }",
                "package net.javacrumbs.shedlock.spring.annotation; public @interface EnableSchedulerLock { String defaultLockAtMostFor() default \"\"; }",
                "package net.javacrumbs.shedlock.spring.annotation; public @interface LockProviderToUse { String value(); }",
                "package javax.annotation; public @interface PostConstruct {}"
        );
    }
}
