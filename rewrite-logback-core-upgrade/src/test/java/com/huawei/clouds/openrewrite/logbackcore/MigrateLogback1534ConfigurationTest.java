package com.huawei.clouds.openrewrite.logbackcore;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class MigrateLogback1534ConfigurationTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.logbackcore.MigrateDeterministicLogbackCore1_5_34";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe());
    }

    @Test
    void migratesApacheShenyuShutdownHookFixture() {
        rewriteRun(xml(
                """
                <configuration>
                    <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook">
                        <delay>1000</delay>
                    </shutdownHook>
                </configuration>
                """,
                """
                <configuration>
                    <shutdownHook class="ch.qos.logback.core.hook.DefaultShutdownHook">
                        <delay>1000</delay>
                    </shutdownHook>
                </configuration>
                """,
                source -> source.path("src/main/resources/logback.xml")));
    }

    @Test
    void migratesTwitterAlgorithmShutdownHookFixture() {
        rewriteRun(xml(
                """
                <configuration scan="true">
                    <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>
                </configuration>
                """,
                """
                <configuration scan="true">
                    <shutdownHook class="ch.qos.logback.core.hook.DefaultShutdownHook"/>
                </configuration>
                """,
                source -> source.path("logback-service.xml")));
    }

    @Test
    void migratesOpenTsdbLegacyTriggeringPolicyFixture() {
        rewriteRun(xml(
                """
                <configuration>
                    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                                <maxFileSize>100MB</maxFileSize>
                            </timeBasedFileNamingAndTriggeringPolicy>
                        </rollingPolicy>
                    </appender>
                </configuration>
                """,
                """
                <configuration>
                    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy">
                                <maxFileSize>100MB</maxFileSize>
                            </timeBasedFileNamingAndTriggeringPolicy>
                        </rollingPolicy>
                    </appender>
                </configuration>
                """,
                source -> source.path("fat-jar/file-logback.xml")));
    }

    @Test
    void migratesBothNamesAndPreservesSurroundingConfiguration() {
        rewriteRun(xml(
                """
                <configuration debug="false">
                    <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"><delay>250</delay></shutdownHook>
                    <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                        <maxFileSize>7MB</maxFileSize>
                    </timeBasedFileNamingAndTriggeringPolicy>
                </configuration>
                """,
                """
                <configuration debug="false">
                    <shutdownHook class="ch.qos.logback.core.hook.DefaultShutdownHook"><delay>250</delay></shutdownHook>
                    <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy">
                        <maxFileSize>7MB</maxFileSize>
                    </timeBasedFileNamingAndTriggeringPolicy>
                </configuration>
                """,
                source -> source.path("logback-test.xml")));
    }

    @Test
    void detectsCustomNamedLogbackConfigurationByRootAndClass() {
        rewriteRun(xml(
                """
                <configuration>
                    <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>
                </configuration>
                """,
                """
                <configuration>
                    <shutdownHook class="ch.qos.logback.core.hook.DefaultShutdownHook"/>
                </configuration>
                """,
                source -> source.path("observability.xml")));
    }

    @Test
    void leavesUnrelatedXmlAndNonClassAttributesUntouched() {
        rewriteRun(
                xml("""
                    <root>
                        <entry class="ch.qos.logback.core.hook.DelayingShutdownHook"/>
                    </root>
                    """, source -> source.path("application.xml")),
                xml("""
                    <configuration>
                        <property name="class" value="ch.qos.logback.core.hook.DelayingShutdownHook"/>
                    </configuration>
                    """, source -> source.path("logback.xml")));
    }

    @Test
    void leavesAlreadyMigratedAndLookalikeNamesUntouched() {
        rewriteRun(xml("""
                <configuration>
                    <shutdownHook class="ch.qos.logback.core.hook.DefaultShutdownHook"/>
                    <component class="example.ch.qos.logback.core.hook.DelayingShutdownHook"/>
                    <component class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATPFactory"/>
                </configuration>
                """, source -> source.path("logback.xml")));
    }

    @Test
    void skipsGeneratedConfigurationAndIsIdempotent() {
        rewriteRun(xml("""
                <configuration>
                    <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>
                </configuration>
                """, source -> source.path("build/resources/logback.xml")));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(
                        """
                        <configuration>
                            <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>
                        </configuration>
                        """,
                        """
                        <configuration>
                            <shutdownHook class="ch.qos.logback.core.hook.DefaultShutdownHook"/>
                        </configuration>
                        """,
                        source -> source.path("logback.xml")));
    }

    private static Recipe recipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE);
    }
}
