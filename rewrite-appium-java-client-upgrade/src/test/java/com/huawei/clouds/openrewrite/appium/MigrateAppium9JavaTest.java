package com.huawei.clouds.openrewrite.appium;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class MigrateAppium9JavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateAppium9Java())
                .parser(JavaParser.fromJavaVersion().dependsOn(AppiumTestApi.sources()));
    }

    @Test
    void migratesMobileByFactories() {
        rewriteRun(java(
                """
                import io.appium.java_client.MobileBy;
                import org.openqa.selenium.By;
                class Locators {
                    By a = MobileBy.AccessibilityId("login");
                    By b = MobileBy.AndroidUIAutomator("new UiSelector().text(\\\"OK\\\")");
                    By c = MobileBy.AndroidViewTag("submit");
                    By d = MobileBy.iOSClassChain("**/XCUIElementTypeButton");
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "import io.appium.java_client.AppiumBy;");
                    assertContains(out, "AppiumBy.accessibilityId(\"login\")");
                    assertContains(out, "AppiumBy.androidUIAutomator(");
                    assertContains(out, "AppiumBy.androidViewTag(\"submit\")");
                    assertContains(out, "AppiumBy.iOSClassChain(");
                    assertFalse(out.contains("MobileBy"), out);
                })));
    }

    @Test
    void migratesSelectedDriverFindShortcuts() {
        rewriteRun(java(
                """
                import io.appium.java_client.AppiumDriver;
                import io.appium.java_client.MobileElement;
                import java.util.List;
                class Search {
                    MobileElement one(AppiumDriver<MobileElement> d) { return d.findElementByAccessibilityId("login"); }
                    List<MobileElement> many(AppiumDriver<MobileElement> d) { return d.findElementsByXPath("//button"); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "WebElement one(AppiumDriver d)");
                    assertContains(out, "d.findElement(AppiumBy.accessibilityId(\"login\"))");
                    assertContains(out, "List<WebElement> many(AppiumDriver d)");
                    assertContains(out, "d.findElements(AppiumBy.xpath(\"//button\"))");
                })));
    }

    @Test
    void removesDriverGenericsAndConstructorDiamonds() {
        // Reduced from PerfectoCode/Samples@8339178c854f62fd39644ef5d95e7037742409e1.
        rewriteRun(java(
                """
                import io.appium.java_client.MobileElement;
                import io.appium.java_client.android.AndroidDriver;
                import java.net.URL;
                import org.openqa.selenium.Capabilities;
                class DriverFactory {
                    AndroidDriver<MobileElement> create(URL url, Capabilities caps) {
                        return new AndroidDriver<>(url, caps);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "AndroidDriver create(URL url, Capabilities caps)");
                    assertContains(out, "new AndroidDriver(url, caps)");
                    assertFalse(out.contains("MobileElement"), out);
                })));
    }

    @Test
    void migratesAllRemovedElementTypesAndSetValue() {
        rewriteRun(java(
                """
                import io.appium.java_client.MobileElement;
                import io.appium.java_client.android.AndroidElement;
                import io.appium.java_client.ios.IOSElement;
                class Page {
                    MobileElement mobile; AndroidElement android; IOSElement ios;
                    void type(MobileElement element) { element.setValue("hello"); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "WebElement mobile");
                    assertContains(out, "WebElement android");
                    assertContains(out, "WebElement ios");
                    assertContains(out, "element.sendKeys(\"hello\")");
                    assertFalse(out.contains("MobileElement") || out.contains("AndroidElement") || out.contains("IOSElement"), out);
                })));
    }

    @Test
    void migratesRealOpenTestLocatorPattern() {
        // Reduced from mcdcorp/opentest@60ade6ac6a8dce228b6796f78b4d870b15b124f0.
        rewriteRun(java(
                """
                import io.appium.java_client.MobileBy;
                import org.openqa.selenium.By;
                class AppiumTestAction { By accessibility(String value) { return MobileBy.AccessibilityId(value); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "return AppiumBy.accessibilityId(value)");
                })));
    }

    @Test
    void targetFormsAndSameNamedBusinessApisAreNoop() {
        rewriteRun(
                java("import io.appium.java_client.AppiumBy; class Target { Object x(){ return AppiumBy.accessibilityId(\"x\"); } }"),
                java("class MobileBy { static Object AccessibilityId(String s){return s;} } class Use { Object x(){return MobileBy.AccessibilityId(\"x\");} }"));
    }

    @Test
    void unsupportedWindowsShortcutIsNotGuessedByAuto() {
        rewriteRun(java(
                """
                import io.appium.java_client.AppiumDriver;
                import io.appium.java_client.MobileElement;
                class WindowsSearch {
                    MobileElement x(AppiumDriver<MobileElement> d) { return d.findElementByWindowsUIAutomation("name:Save"); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "WebElement x(AppiumDriver d)");
                    assertContains(out, "d.findElementByWindowsUIAutomation(\"name:Save\")");
                })));
    }

    @Test
    void generatedInstallAndCacheParentsAreNoop() {
        rewriteRun(
                java("import io.appium.java_client.MobileBy; class Generated { Object x(){return MobileBy.AccessibilityId(\"x\");} }",
                        source -> source.path("generated-code/Generated.java")),
                java("import io.appium.java_client.MobileBy; class Installed { Object x(){return MobileBy.AccessibilityId(\"x\");} }",
                        source -> source.path("installation/lib/Installed.java")),
                java("import io.appium.java_client.MobileBy; class Cached { Object x(){return MobileBy.AccessibilityId(\"x\");} }",
                        source -> source.path(".gradle/cache/Cached.java")));
    }

    @Test
    void installLeafIsProcessedAndAutoIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import io.appium.java_client.MobileBy; class install { Object x(){return MobileBy.AccessibilityId(\"x\");} }",
                source -> source.path("install.java").after(actual -> actual).afterRecipe(after -> {
                    assertCount(after.printAll(), "AppiumBy.accessibilityId", 1);
                    assertFalse(after.printAll().contains("MobileBy"), after.printAll());
                })));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
