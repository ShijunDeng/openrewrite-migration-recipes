package com.huawei.clouds.openrewrite.logbackcore;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateLogback1534JavaTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.logbackcore.MigrateDeterministicLogbackCore1_5_34";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe())
                .parser(JavaParser.fromJavaVersion().classpath("logback-core"));
    }

    @Test
    void renamesDelayingShutdownHookTypeConstructorAndImport() {
        rewriteRun(java(
                """
                import ch.qos.logback.core.hook.DelayingShutdownHook;

                class LoggingLifecycle {
                    DelayingShutdownHook hook = new DelayingShutdownHook();
                }
                """,
                """
                import ch.qos.logback.core.hook.DefaultShutdownHook;

                class LoggingLifecycle {
                    DefaultShutdownHook hook = new DefaultShutdownHook();
                }
                """));
    }

    @Test
    void renamesLegacySizeAndTimePolicyIncludingGenericType() {
        rewriteRun(java(
                """
                import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;

                class Rolling {
                    SizeAndTimeBasedFNATP<Object> policy = new SizeAndTimeBasedFNATP<>();
                }
                """,
                """
                import ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy;

                class Rolling {
                    SizeAndTimeBasedFileNamingAndTriggeringPolicy<Object> policy = new SizeAndTimeBasedFileNamingAndTriggeringPolicy<>();
                }
                """));
    }

    @Test
    void renamesActionConstantsOwner() {
        rewriteRun(java(
                """
                import ch.qos.logback.core.joran.action.ActionConst;

                class Rule {
                    String tag() {
                        return ActionConst.APPENDER_TAG;
                    }
                }
                """,
                """
                import ch.qos.logback.core.joran.JoranConstants;

                class Rule {
                    String tag() {
                        return JoranConstants.APPENDER_TAG;
                    }
                }
                """));
    }

    @Test
    void renamesConfigurationWatchListAccessors() {
        rewriteRun(java(
                """
                import ch.qos.logback.core.joran.spi.ConfigurationWatchList;
                import java.net.URL;

                class Watcher {
                    URL read(ConfigurationWatchList watchList) {
                        return watchList.getMainURL();
                    }

                    void write(ConfigurationWatchList watchList, URL url) {
                        watchList.setMainURL(url);
                    }
                }
                """,
                """
                import ch.qos.logback.core.joran.spi.ConfigurationWatchList;
                import java.net.URL;

                class Watcher {
                    URL read(ConfigurationWatchList watchList) {
                        return watchList.getTopURL();
                    }

                    void write(ConfigurationWatchList watchList, URL url) {
                        watchList.setTopURL(url);
                    }
                }
                """));
    }

    @Test
    void migratesRealGeoToolsConfigurationLookupShape() {
        // Fixed fixture:
        // https://github.com/aaime/geotools-dependabot-test/blob/d052a1f853152af8112ef5929cfb47c001134ef7/modules/library/metadata/src/main/java/org/geotools/util/logging/LogbackLoggerFactory.java#L88-L97
        rewriteRun(java(
                """
                import ch.qos.logback.core.joran.spi.ConfigurationWatchList;

                class LogbackLoggerFactory {
                    public String lookupConfiguration(ConfigurationWatchList configurationWatchList) {
                        return configurationWatchList.getMainURL().toString();
                    }
                }
                """,
                """
                import ch.qos.logback.core.joran.spi.ConfigurationWatchList;

                class LogbackLoggerFactory {
                    public String lookupConfiguration(ConfigurationWatchList configurationWatchList) {
                        return configurationWatchList.getTopURL().toString();
                    }
                }
                """));
    }

    @Test
    void migratesRealScyllaShutdownHookShape() {
        // Fixed fixture:
        // https://github.com/scylladb/scylla-tools-java/blob/4f1353ba4cbf1bee35dd5109730e3db02359f2e4/src/java/org/apache/cassandra/utils/logging/LogbackLoggingSupport.java#L85-L91
        rewriteRun(java(
                """
                import ch.qos.logback.core.Context;
                import ch.qos.logback.core.hook.DelayingShutdownHook;

                class LogbackLoggingSupport {
                    public void onShutdown(Context context) {
                        DelayingShutdownHook logbackHook = new DelayingShutdownHook();
                        logbackHook.setContext(context);
                        logbackHook.run();
                    }
                }
                """,
                """
                import ch.qos.logback.core.Context;
                import ch.qos.logback.core.hook.DefaultShutdownHook;

                class LogbackLoggingSupport {
                    public void onShutdown(Context context) {
                        DefaultShutdownHook logbackHook = new DefaultShutdownHook();
                        logbackHook.setContext(context);
                        logbackHook.run();
                    }
                }
                """));
    }

    @Test
    void migratesStaticImportedJoranConstant() {
        rewriteRun(java(
                """
                import static ch.qos.logback.core.joran.action.ActionConst.APPENDER_TAG;

                class Rule {
                    String tag() {
                        return APPENDER_TAG;
                    }
                }
                """,
                """
                import static ch.qos.logback.core.joran.JoranConstants.APPENDER_TAG;

                class Rule {
                    String tag() {
                        return APPENDER_TAG;
                    }
                }
                """));
    }

    @Test
    void migratesConfigurationWatchListMethodReference() {
        rewriteRun(java(
                """
                import ch.qos.logback.core.joran.spi.ConfigurationWatchList;
                import java.net.URL;
                import java.util.function.Function;

                class Watcher {
                    Function<ConfigurationWatchList, URL> lookup = ConfigurationWatchList::getMainURL;
                }
                """,
                """
                import ch.qos.logback.core.joran.spi.ConfigurationWatchList;
                import java.net.URL;
                import java.util.function.Function;

                class Watcher {
                    Function<ConfigurationWatchList, URL> lookup = ConfigurationWatchList::getTopURL;
                }
                """));
    }

    @Test
    void composesAllDeterministicJavaMigrations() {
        rewriteRun(java(
                """
                import ch.qos.logback.core.hook.DelayingShutdownHook;
                import ch.qos.logback.core.joran.action.ActionConst;
                import ch.qos.logback.core.joran.spi.ConfigurationWatchList;
                import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;

                class LogbackExtension {
                    DelayingShutdownHook hook = new DelayingShutdownHook();
                    SizeAndTimeBasedFNATP<Object> policy = new SizeAndTimeBasedFNATP<>();

                    String inspect(ConfigurationWatchList list) {
                        return ActionConst.VALUE_ATTR + list.getMainURL();
                    }
                }
                """,
                """
                import ch.qos.logback.core.hook.DefaultShutdownHook;
                import ch.qos.logback.core.joran.JoranConstants;
                import ch.qos.logback.core.joran.spi.ConfigurationWatchList;
                import ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy;

                class LogbackExtension {
                    DefaultShutdownHook hook = new DefaultShutdownHook();
                    SizeAndTimeBasedFileNamingAndTriggeringPolicy<Object> policy = new SizeAndTimeBasedFileNamingAndTriggeringPolicy<>();

                    String inspect(ConfigurationWatchList list) {
                        return JoranConstants.VALUE_ATTR + list.getTopURL();
                    }
                }
                """));
    }

    @Test
    void ignoresSameSimpleNamesWithoutLogbackAttribution() {
        rewriteRun(java("""
                class DelayingShutdownHook {}
                class SizeAndTimeBasedFNATP<T> {}
                class ActionConst {
                    static final String APPENDER_TAG = "appender";
                }
                class ConfigurationWatchList {
                    java.net.URL getMainURL() {
                        return null;
                    }
                    void setMainURL(java.net.URL ignored) {}
                }

                class BusinessCode {
                    DelayingShutdownHook hook = new DelayingShutdownHook();
                    SizeAndTimeBasedFNATP<String> policy = new SizeAndTimeBasedFNATP<>();
                    String tag = ActionConst.APPENDER_TAG;
                    java.net.URL read(ConfigurationWatchList list) {
                        list.setMainURL(null);
                        return list.getMainURL();
                    }
                }
                """));
    }

    @Test
    void skipsGeneratedSource() {
        rewriteRun(java(
                """
                import ch.qos.logback.core.hook.DelayingShutdownHook;
                class Generated {
                    DelayingShutdownHook hook = new DelayingShutdownHook();
                }
                """,
                source -> source.path("build/generated/sources/Generated.java")));
    }

    @Test
    void migrationIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import ch.qos.logback.core.hook.DelayingShutdownHook;
                        class App {
                            DelayingShutdownHook hook = new DelayingShutdownHook();
                        }
                        """,
                        """
                        import ch.qos.logback.core.hook.DefaultShutdownHook;

                        class App {
                            DefaultShutdownHook hook = new DefaultShutdownHook();
                        }
                        """));
    }

    private static Recipe recipe() {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(RECIPE);
    }
}
