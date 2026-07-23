package com.huawei.clouds.openrewrite.appium;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindAppium9SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindAppium9SourceRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(AppiumTestApi.sources()));
    }

    @Test
    void marksDesiredCapabilitiesAtDriverConstructionOnly() {
        // Reduced from lgxqf/UICrawler@ab96bcf67e6a5c3ac52148a4dcd54f3870c51f60.
        rewriteRun(java(
                """
                import io.appium.java_client.MobileElement;
                import io.appium.java_client.android.AndroidDriver;
                import java.net.URL;
                import org.openqa.selenium.remote.DesiredCapabilities;
                class DriverFactory {
                    AndroidDriver<MobileElement> create(URL url) {
                        DesiredCapabilities caps = new DesiredCapabilities();
                        return new AndroidDriver<>(url, caps);
                    }
                    DesiredCapabilities seleniumOnly() { return new DesiredCapabilities(); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindAppium9SourceRisks.CAPABILITIES, 1))));
    }

    @Test
    void marksLegacyCapabilityConstants() {
        rewriteRun(java(
                """
                import io.appium.java_client.remote.AndroidMobileCapabilityType;
                import io.appium.java_client.remote.MobileCapabilityType;
                import io.appium.java_client.remote.AutomationName;
                class Caps { Object[] values = { MobileCapabilityType.PLATFORM_NAME,
                    AndroidMobileCapabilityType.APP_ACTIVITY, AutomationName.APPIUM }; }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindAppium9SourceRisks.CAPABILITIES))));
    }

    @Test
    void marksRemovedMobileElementOnlyMethods() {
        rewriteRun(java(
                """
                import io.appium.java_client.MobileElement;
                import io.appium.java_client.android.AndroidElement;
                class Page {
                    Object center(MobileElement e) { return e.getCenter(); }
                    void replace(AndroidElement e) { e.replaceValue("new"); }
                    void standard(MobileElement e) { e.sendKeys("ok"); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertCount(after.printAll(), FindAppium9SourceRisks.ELEMENT, 2);
                    assertContains(after.printAll(), "e.sendKeys(\"ok\")");
                })));
    }

    @Test
    void marksRealOpenTestTouchActionPattern() {
        // Reduced from mcdcorp/opentest@60ade6ac6a8dce228b6796f78b4d870b15b124f0.
        rewriteRun(java(
                """
                import io.appium.java_client.AppiumDriver;
                import io.appium.java_client.MobileElement;
                import io.appium.java_client.TouchAction;
                class AppiumTestAction {
                    TouchAction actions(AppiumDriver<MobileElement> driver) {
                        return new TouchAction(driver).press(new Object()).moveTo(new Object());
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindAppium9SourceRisks.TOUCH))));
    }

    @Test
    void marksRemovedApplicationLifecycleMethods() {
        rewriteRun(java(
                """
                import io.appium.java_client.AppiumDriver;
                import io.appium.java_client.MobileElement;
                class Lifecycle { void reset(AppiumDriver<MobileElement> d) { d.closeApp(); d.resetApp(); d.launchApp(); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindAppium9SourceRisks.APP_MANAGEMENT, 3))));
    }

    @Test
    void marksRemovedAndroidStartActivity() {
        rewriteRun(java(
                """
                import io.appium.java_client.MobileElement;
                import io.appium.java_client.android.AndroidDriver;
                class Activity { void start(AndroidDriver<MobileElement> d) { d.startActivity(new Object()); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindAppium9SourceRisks.ACTIVITY))));
    }

    @Test
    void marksWindowsLocatorApis() {
        rewriteRun(java(
                """
                import io.appium.java_client.AppiumDriver;
                import io.appium.java_client.MobileBy;
                import io.appium.java_client.MobileElement;
                class WindowsPage { Object one(AppiumDriver<MobileElement> d) {
                    d.findElementByWindowsUIAutomation("name:Save"); return MobileBy.windowsAutomation("name:Save"); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindAppium9SourceRisks.WINDOWS, 2))));
    }

    @Test
    void marksNumericTimeUnitApi() {
        rewriteRun(java(
                """
                import io.appium.java_client.service.local.AppiumServiceBuilder;
                import java.util.concurrent.TimeUnit;
                class Service { Object configure() { return new AppiumServiceBuilder().withStartUpTimeOut(30, TimeUnit.SECONDS); } }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindAppium9SourceRisks.TIME))));
    }

    @Test
    void marksDriverAndLocalServiceWdHubRouting() {
        rewriteRun(java(
                """
                import io.appium.java_client.AppiumDriver;
                import io.appium.java_client.MobileElement;
                import io.appium.java_client.service.local.AppiumServiceBuilder;
                import io.appium.java_client.service.local.flags.GeneralServerFlag;
                import java.net.URL;
                import org.openqa.selenium.Capabilities;
                class Routing {
                    AppiumDriver<MobileElement> remote(Capabilities caps) throws Exception {
                        return new AppiumDriver<>(new URL("http://grid:4723/wd/hub"), caps);
                    }
                    Object local() { return new AppiumServiceBuilder().withArgument(GeneralServerFlag.BASEPATH, "/wd/hub/"); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindAppium9SourceRisks.BASE_PATH, 2))));
    }

    @Test
    void sameNamedBusinessApisAndTargetFormsAreNoop() {
        rewriteRun(java(
                """
                class Driver { void resetApp(){} void startActivity(Object o){} }
                class TouchAction { TouchAction(Object d){} }
                class Use { void x(Driver d) { d.resetApp(); d.startActivity(this); new TouchAction(d); } }
                """, source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void generatedInstallAndCacheParentsAreNoop() {
        rewriteRun(
                java("import io.appium.java_client.TouchAction; class Generated { TouchAction x(){return null;} }",
                        source -> source.path("generated-code/Generated.java")),
                java("import io.appium.java_client.TouchAction; class Installed { TouchAction x(){return null;} }",
                        source -> source.path("installation/lib/Installed.java")),
                java("import io.appium.java_client.TouchAction; class Cached { TouchAction x(){return null;} }",
                        source -> source.path(".m2/cache/Cached.java")));
    }

    @Test
    void installLeafIsMarkedAndMarkersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import io.appium.java_client.TouchAction; class install { TouchAction x(){return null;} }",
                source -> source.path("install.java").after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindAppium9SourceRisks.TOUCH, 1))));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~("), actual);
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
