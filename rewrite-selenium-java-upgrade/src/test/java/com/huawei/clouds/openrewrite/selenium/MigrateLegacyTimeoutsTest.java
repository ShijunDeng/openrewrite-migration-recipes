package com.huawei.clouds.openrewrite.selenium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class MigrateLegacyTimeoutsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateLegacyTimeouts())
                .parser(JavaParser.fromJavaVersion().dependsOn(SeleniumTestApi.sources()));
    }

    @ParameterizedTest(name = "removed timeout {0}")
    @MethodSource("timeouts")
    void migratesRemovedOverload(String oldName, String newName, String unit) {
        rewriteRun(java(
                """
                import java.util.concurrent.TimeUnit;
                import org.openqa.selenium.WebDriver;
                class Browser {
                    void configure(WebDriver driver, long amount) {
                        driver.manage().timeouts().%s(amount, TimeUnit.%s);
                    }
                }
                """.formatted(oldName, unit),
                """
                import java.time.Duration;
                import java.util.concurrent.TimeUnit;
                import org.openqa.selenium.WebDriver;

                class Browser {
                    void configure(WebDriver driver, long amount) {
                        driver.manage().timeouts().%s(Duration.ofMillis(TimeUnit.%s.toMillis(amount)));
                    }
                }
                """.formatted(newName, unit)));
    }

    static Stream<Arguments> timeouts() {
        return Stream.of(
                Arguments.of("implicitlyWait", "implicitlyWait", "SECONDS"),
                Arguments.of("implicitlyWait", "implicitlyWait", "MILLISECONDS"),
                Arguments.of("implicitlyWait", "implicitlyWait", "MINUTES"),
                Arguments.of("setScriptTimeout", "scriptTimeout", "SECONDS"),
                Arguments.of("setScriptTimeout", "scriptTimeout", "NANOSECONDS"),
                Arguments.of("pageLoadTimeout", "pageLoadTimeout", "HOURS"),
                Arguments.of("pageLoadTimeout", "pageLoadTimeout", "DAYS"),
                Arguments.of("pageLoadTimeout", "pageLoadTimeout", "MICROSECONDS")
        );
    }

    @Test
    void supportsStaticImportedEnumConstant() {
        rewriteRun(java(
                """
                import static java.util.concurrent.TimeUnit.SECONDS;
                import org.openqa.selenium.WebDriver;
                class Browser { void configure(WebDriver d) { d.manage().timeouts().implicitlyWait(10, SECONDS); } }
                """,
                """
                import static java.util.concurrent.TimeUnit.SECONDS;
                import org.openqa.selenium.WebDriver;

                import java.time.Duration;

                class Browser { void configure(WebDriver d) {
                    d.manage().timeouts().implicitlyWait(Duration.ofMillis(SECONDS.toMillis(10))); } }
                """));
    }

    @Test
    void dynamicUnitIsNotReordered() {
        rewriteRun(java("""
                import java.util.concurrent.TimeUnit;
                import org.openqa.selenium.WebDriver;
                class Browser { void configure(WebDriver d, long n, TimeUnit unit) { d.manage().timeouts().implicitlyWait(n, unit); } }
                """));
    }

    @Test
    void sameNamedApplicationMethodIsNoop() {
        rewriteRun(java("""
                import java.util.concurrent.TimeUnit;
                class Timeouts { Timeouts implicitlyWait(long n, TimeUnit unit) { return this; } }
                class Browser { void configure(Timeouts t) { t.implicitlyWait(10, TimeUnit.SECONDS); } }
                """));
    }

    @Test
    void durationOverloadsAreNoop() {
        rewriteRun(java("""
                import java.time.Duration;
                import org.openqa.selenium.WebDriver;
                class Browser { void configure(WebDriver d) { d.manage().timeouts().implicitlyWait(Duration.ofSeconds(10)); } }
                """));
    }

    @Test
    void generatedParentIsNoopAndLeafInstallIsMigratedIdempotently() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import java.util.concurrent.TimeUnit;
                        import org.openqa.selenium.WebDriver;
                        class Generated { void x(WebDriver d) { d.manage().timeouts().implicitlyWait(1, TimeUnit.SECONDS); } }
                        """, source -> source.path("generated-code/Generated.java")),
                java("""
                        import java.util.concurrent.TimeUnit;
                        import org.openqa.selenium.WebDriver;
                        class install { void x(WebDriver d) { d.manage().timeouts().pageLoadTimeout(1, TimeUnit.SECONDS); } }
                        """,
                        """
                        import java.time.Duration;
                        import java.util.concurrent.TimeUnit;
                        import org.openqa.selenium.WebDriver;

                        class install { void x(WebDriver d) {
                            d.manage().timeouts().pageLoadTimeout(Duration.ofMillis(TimeUnit.SECONDS.toMillis(1))); } }
                        """, source -> source.path("install.java")));
    }
}
