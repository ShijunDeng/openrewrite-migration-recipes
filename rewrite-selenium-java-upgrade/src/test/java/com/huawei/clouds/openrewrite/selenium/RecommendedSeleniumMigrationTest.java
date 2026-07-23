package com.huawei.clouds.openrewrite.selenium;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class RecommendedSeleniumMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.selenium").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.selenium.MigrateSeleniumJavaTo4_41_0"))
                .parser(JavaParser.fromJavaVersion().dependsOn(SeleniumTestApi.sources()));
    }

    @Test
    void changesMovedChromeLogLevelType() {
        rewriteRun(java(
                """
                import org.openqa.selenium.chrome.ChromeDriverLogLevel;
                class Logging { ChromeDriverLogLevel level = ChromeDriverLogLevel.INFO; }
                """,
                """
                import org.openqa.selenium.chromium.ChromiumDriverLogLevel;

                class Logging { ChromiumDriverLogLevel level = ChromiumDriverLogLevel.INFO; }
                """));
    }

    @Test
    void movedTypeIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import org.openqa.selenium.chrome.ChromeDriverLogLevel; class Logging { ChromeDriverLogLevel l; }",
                """
                import org.openqa.selenium.chromium.ChromiumDriverLogLevel;

                class Logging { ChromiumDriverLogLevel l; }
                """));
    }

    @Test
    void movedTypeSkipsGeneratedParentButProcessesLeafName() {
        String before = "import org.openqa.selenium.chrome.ChromeDriverLogLevel; class Logging { ChromeDriverLogLevel l; }";
        String after = """
                import org.openqa.selenium.chromium.ChromiumDriverLogLevel;

                class Logging { ChromiumDriverLogLevel l; }
                """;
        rewriteRun(
                java(before.replace("class Logging", "class GeneratedLogging"),
                        source -> source.path("build/generated/GeneratedLogging.java")),
                java(before, after, source -> source.path("install.java")));
    }

    @Test
    void recommendedRecipeUsesOwnedMovedTypeMigration() {
        var recipe = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.selenium").build()
                .activateRecipes("com.huawei.clouds.openrewrite.selenium.MigrateSeleniumJavaTo4_41_0");
        assertEquals(MigrateChromeDriverLogLevel.class, recipe.getRecipeList().get(1).getClass());
    }

    @Test
    void reducedFrameworkiumFixtureMigratesTimeoutAndMarksRemovedEvents() {
        // Reduced from Frameworkium/frameworkium-core@815f590a38a994cfe891fd6d22f9a3187678aae6,
        // src/main/java/com/frameworkium/core/ui/driver/AbstractDriver.java.
        rewriteRun(java("""
                import static java.util.concurrent.TimeUnit.SECONDS;
                import org.openqa.selenium.WebDriver;
                import org.openqa.selenium.support.events.EventFiringWebDriver;
                class DriverSetup {
                    EventFiringWebDriver setup(WebDriver driver) {
                        EventFiringWebDriver eventDriver = new EventFiringWebDriver(driver);
                        eventDriver.register(new Object());
                        eventDriver.manage().timeouts().setScriptTimeout(10, SECONDS);
                        return eventDriver;
                    }
                }
                """, spec -> spec.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("scriptTimeout(Duration.ofMillis(SECONDS.toMillis(10)))"), out);
                    assertTrue(out.contains("EventFiringDecorator"), out);
                })));
    }

    @Test
    void reducedHeyLocalFixtureMarksHeadlessChoiceWithoutGuessing() {
        // Reduced from TGT-SWM/HeyLocal-Crawling-Server@f13da2c3dc319a66bc75cc67aeabb5998e40c73c,
        // src/main/java/kr/pe/heylocal/crawling/config/DriverOptionConfig.java.
        rewriteRun(java("""
                import org.openqa.selenium.chrome.ChromeOptions;
                class DriverOptionConfig {
                    ChromeOptions options() {
                        ChromeOptions options = new ChromeOptions();
                        options.setHeadless(true);
                        return options;
                    }
                }
                """, spec -> spec.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("--headless=new"), out);
                    assertTrue(out.contains("setHeadless(true)"), out);
                })));
    }

    @Test
    void reducedJbangFixtureGetsDeterministicTimeoutRewrite() {
        // Reduced from jbangdev/jbang-examples@69abd9e1bd39efc5fe5884f77351d3916412d509,
        // examples/webdriver.java.
        rewriteRun(java("""
                import java.util.concurrent.TimeUnit;
                import org.openqa.selenium.WebDriver;
                class Example { WebDriver configure(WebDriver driver) {
                    driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
                    return driver;
                } }
                """, spec -> spec.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("implicitlyWait(Duration.ofMillis(TimeUnit.SECONDS.toMillis(10)))"), out);
                    assertFalse(out.contains("workbook-owned literal"), out);
                })));
    }

    @Test
    void upgradesDependencyBeforeSourceRecipes() {
        rewriteRun(
                xml(UpgradeSeleniumDependencyTest.pom("4.8.1"), UpgradeSeleniumDependencyTest.pom("4.41.0"),
                        source -> source.path("pom.xml")),
                java("import org.openqa.selenium.chrome.ChromeDriverLogLevel; class T { ChromeDriverLogLevel l; }",
                        """
                        import org.openqa.selenium.chromium.ChromiumDriverLogLevel;

                        class T { ChromiumDriverLogLevel l; }
                        """));
    }
}
