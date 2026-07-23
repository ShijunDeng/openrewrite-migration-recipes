package com.huawei.clouds.openrewrite.appium;

final class AppiumTestApi {
    private AppiumTestApi() {
    }

    static String[] sources() {
        return new String[]{
                "package org.openqa.selenium; public interface Capabilities { }",
                """
                package org.openqa.selenium;
                public abstract class By {
                    public static By id(String s){return null;} public static By linkText(String s){return null;}
                    public static By partialLinkText(String s){return null;} public static By tagName(String s){return null;}
                    public static By className(String s){return null;} public static By cssSelector(String s){return null;}
                    public static By xpath(String s){return null;}
                }
                """,
                """
                package org.openqa.selenium;
                public interface WebElement {
                    void sendKeys(CharSequence... keys); WebElement findElement(By by);
                    java.util.List<WebElement> findElements(By by);
                }
                """,
                "package org.openqa.selenium.remote; public class DesiredCapabilities implements org.openqa.selenium.Capabilities { public DesiredCapabilities(){} }",
                "package org.openqa.selenium.interactions; public class PointerInput { }",
                """
                package io.appium.java_client;
                public abstract class MobileBy extends org.openqa.selenium.By {
                    public static org.openqa.selenium.By AndroidUIAutomator(String s){return null;}
                    public static org.openqa.selenium.By AccessibilityId(String s){return null;}
                    public static org.openqa.selenium.By iOSClassChain(String s){return null;}
                    public static org.openqa.selenium.By androidDataMatcher(String s){return null;}
                    public static org.openqa.selenium.By androidViewMatcher(String s){return null;}
                    public static org.openqa.selenium.By iOSNsPredicateString(String s){return null;}
                    public static org.openqa.selenium.By windowsAutomation(String s){return null;}
                    public static org.openqa.selenium.By AndroidViewTag(String s){return null;}
                    public static org.openqa.selenium.By image(String s){return null;}
                    public static org.openqa.selenium.By custom(String s){return null;}
                }
                """,
                """
                package io.appium.java_client;
                public abstract class AppiumBy extends org.openqa.selenium.By {
                    public static org.openqa.selenium.By accessibilityId(String s){return null;}
                    public static org.openqa.selenium.By androidDataMatcher(String s){return null;}
                    public static org.openqa.selenium.By androidUIAutomator(String s){return null;}
                    public static org.openqa.selenium.By androidViewMatcher(String s){return null;}
                    public static org.openqa.selenium.By androidViewTag(String s){return null;}
                    public static org.openqa.selenium.By className(String s){return null;}
                    public static org.openqa.selenium.By id(String s){return null;}
                    public static org.openqa.selenium.By name(String s){return null;}
                    public static org.openqa.selenium.By custom(String s){return null;}
                    public static org.openqa.selenium.By image(String s){return null;}
                    public static org.openqa.selenium.By iOSClassChain(String s){return null;}
                    public static org.openqa.selenium.By iOSNsPredicateString(String s){return null;}
                }
                """,
                """
                package io.appium.java_client;
                public class MobileElement implements org.openqa.selenium.WebElement {
                    public void setValue(String value){} public Object getCenter(){return null;}
                    public void sendKeys(CharSequence... keys){} public org.openqa.selenium.WebElement findElement(org.openqa.selenium.By by){return null;}
                    public java.util.List<org.openqa.selenium.WebElement> findElements(org.openqa.selenium.By by){return null;}
                }
                """,
                """
                package io.appium.java_client.android;
                public class AndroidElement extends io.appium.java_client.MobileElement { public void replaceValue(String value){} }
                """,
                "package io.appium.java_client.ios; public class IOSElement extends io.appium.java_client.MobileElement { }",
                """
                package io.appium.java_client;
                public class AppiumDriver<T extends org.openqa.selenium.WebElement> {
                    public AppiumDriver(java.net.URL url, org.openqa.selenium.Capabilities caps) { }
                    public T findElement(org.openqa.selenium.By by){return null;}
                    public java.util.List<T> findElements(org.openqa.selenium.By by){return null;}
                    public T findElementByAccessibilityId(String s){return null;}
                    public java.util.List<T> findElementsByAccessibilityId(String s){return null;}
                    public T findElementById(String s){return null;} public java.util.List<T> findElementsById(String s){return null;}
                    public T findElementByXPath(String s){return null;} public java.util.List<T> findElementsByXPath(String s){return null;}
                    public T findElementByAndroidUIAutomator(String s){return null;}
                    public T findElementByWindowsUIAutomation(String s){return null;}
                    public void launchApp(){} public void resetApp(){} public void closeApp(){}
                }
                """,
                """
                package io.appium.java_client.android;
                public class AndroidDriver<T extends org.openqa.selenium.WebElement> extends io.appium.java_client.AppiumDriver<T> {
                    public AndroidDriver(java.net.URL url, org.openqa.selenium.Capabilities caps){super(url,caps);}
                    public void startActivity(Object activity) { }
                }
                """,
                """
                package io.appium.java_client.ios;
                public class IOSDriver<T extends org.openqa.selenium.WebElement> extends io.appium.java_client.AppiumDriver<T> {
                    public IOSDriver(java.net.URL url, org.openqa.selenium.Capabilities caps){super(url,caps);}
                }
                """,
                "package io.appium.java_client; public class TouchAction { public TouchAction(AppiumDriver<?> d){} public TouchAction press(Object o){return this;} public TouchAction moveTo(Object o){return this;} public void perform(){} }",
                "package io.appium.java_client; public class MultiTouchAction { public MultiTouchAction(AppiumDriver<?> d){} }",
                "package io.appium.java_client.android; public class AndroidTouchAction extends io.appium.java_client.TouchAction { public AndroidTouchAction(io.appium.java_client.AppiumDriver<?> d){super(d);} }",
                "package io.appium.java_client.ios; public class IOSTouchAction extends io.appium.java_client.TouchAction { public IOSTouchAction(io.appium.java_client.AppiumDriver<?> d){super(d);} }",
                "package io.appium.java_client.remote; public interface MobileCapabilityType { String PLATFORM_NAME=\"platformName\"; }",
                "package io.appium.java_client.remote; public interface AndroidMobileCapabilityType { String APP_ACTIVITY=\"appActivity\"; }",
                "package io.appium.java_client.remote; public interface IOSMobileCapabilityType { String BUNDLE_ID=\"bundleId\"; }",
                "package io.appium.java_client.remote; public class MobileOptions { }",
                "package io.appium.java_client.remote; public interface YouiEngineCapabilityType { }",
                "package io.appium.java_client.remote; public interface AutomationName { String APPIUM=\"Appium\"; }",
                "package io.appium.java_client.pagefactory; public class WindowsBy { }",
                "package io.appium.java_client.pagefactory.bys.builder; public class ByAll { }",
                "package io.appium.java_client.service.local.flags; public enum GeneralServerFlag { BASEPATH, PRE_LAUNCH }",
                """
                package io.appium.java_client.service.local;
                public class AppiumServiceBuilder {
                    public AppiumServiceBuilder withStartUpTimeOut(long value, java.util.concurrent.TimeUnit unit){return this;}
                    public AppiumServiceBuilder withArgument(io.appium.java_client.service.local.flags.GeneralServerFlag flag, String value){return this;}
                }
                """
        };
    }
}
