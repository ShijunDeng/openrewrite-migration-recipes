package com.huawei.clouds.openrewrite.selenium;

final class SeleniumTestApi {
    private SeleniumTestApi() { }

    static String[] sources() {
        return new String[]{
                """
                package org.openqa.selenium;
                public interface WebDriver {
                    Options manage();
                    interface Options { Timeouts timeouts(); }
                    interface Timeouts {
                        Timeouts implicitlyWait(long value, java.util.concurrent.TimeUnit unit);
                        Timeouts implicitlyWait(java.time.Duration value);
                        Timeouts setScriptTimeout(long value, java.util.concurrent.TimeUnit unit);
                        Timeouts scriptTimeout(java.time.Duration value);
                        Timeouts pageLoadTimeout(long value, java.util.concurrent.TimeUnit unit);
                        Timeouts pageLoadTimeout(java.time.Duration value);
                    }
                }
                """,
                "package org.openqa.selenium; public interface WebElement { String getAttribute(String name); }",
                "package org.openqa.selenium; public interface Capabilities { }",
                "package org.openqa.selenium; public class DesiredCapabilities implements Capabilities { public DesiredCapabilities(){} public void setCapability(String n,Object v){} }",
                "package org.openqa.selenium.remote; public class RemoteWebDriver implements org.openqa.selenium.WebDriver { public RemoteWebDriver(java.net.URL u, org.openqa.selenium.Capabilities c){} public Options manage(){return null;} }",
                "package org.openqa.selenium.chrome; public enum ChromeDriverLogLevel { ALL, DEBUG, INFO, WARNING, SEVERE, OFF }",
                "package org.openqa.selenium.chromium; public enum ChromiumDriverLogLevel { ALL, DEBUG, INFO, WARNING, SEVERE, OFF }",
                "package org.openqa.selenium.chrome; public class ChromeOptions { public ChromeOptions setHeadless(boolean h){return this;} }",
                "package org.openqa.selenium.firefox; public class FirefoxBinary { public FirefoxBinary(){} }",
                "package org.openqa.selenium.support.events; public class EventFiringWebDriver implements org.openqa.selenium.WebDriver { public EventFiringWebDriver(org.openqa.selenium.WebDriver d){} public EventFiringWebDriver register(Object l){return this;} public Options manage(){return null;} }",
                "package org.openqa.selenium.support.events; public abstract class AbstractWebDriverEventListener { }",
                "package org.openqa.selenium.support.events; public interface WebDriverEventListener { }",
                "package org.openqa.selenium.support.ui; public class Select { public void selectByVisibleText(String s){} public void selectByValue(String s){} public void selectByIndex(int i){} public void deselectAll(){} }",
                "package org.openqa.selenium.devtools.v110.network; public class Network { }",
                "package org.openqa.selenium.devtools; public class DevTools { public void send(Object x){} public void addListener(Object x,Object y){} }"
        };
    }
}
